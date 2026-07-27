/**
 * ImportTool tool component.
 *
 * @namespace Alfresco
 * @class Alfresco.ImportTool
 */
(function()
{
  /** YUI Library aliases **/
  var Dom = YAHOO.util.Dom,
      Event = YAHOO.util.Event;

  /** Alfresco Slingshot aliases **/
  var $hasEventInterest = Alfresco.util.hasEventInterest;

  /**
   * ImportTool constructor.
   *
   * @param {String} htmlId The HTML id of the parent element
   * @return {Alfresco.ImportTool} The new ImportTool instance
   * @constructor
   */
  Alfresco.ImportTool = function(htmlId)
  {
    this.name = "Alfresco.ImportTool";
    this.extensions = "*.acp;*.zip";

    Alfresco.ImportTool.superclass.constructor.call(this, htmlId);

    /* Register this component */
    Alfresco.util.ComponentManager.register(this);

    /* Load YUI Components */
    Alfresco.util.YUILoaderHelper.require(["button", "container", "json", "history"], this.onComponentsLoaded, this);

    /* Define panel handlers */
    var parent = this;

    /* Options Panel Handler */
    OptionsPanelHandler = function OptionsPanelHandler_constructor()
    {
      OptionsPanelHandler.superclass.constructor.call(this, "options");
    };

    YAHOO.extend(OptionsPanelHandler, Alfresco.ConsolePanelHandler,
    {
      onLoad: function onLoad()
      {
        parent.widgets.importButton = Alfresco.util.createYUIButton(parent, "import-button", null, {
          type: "submit"
        });

        parent.widgets.selectDestinationButton = Alfresco.util.createYUIButton(parent, "selectDestination-button", this.onSelectDestinationClick);
        parent.widgets.uploadArchive = Alfresco.util.createYUIButton(parent, "upload-button", this.onUpload);
        parent.widgets.destinationPathField = Dom.get(parent.id + "-destination-path-field");
        parent.widgets.destinationNodeField = Dom.get(parent.id + "-destination-node");
        parent.widgets.archivePathField = Dom.get(parent.id + "-archive-path-field");
        parent.widgets.archiveNode = Dom.get(parent.id + "-archive-node");
        parent.widgets.archiveQueue = [];
        parent.widgets.archiveDetails = {};
        parent.widgets.runInBackgroundImport = false;

        YAHOO.Bubbling.on("folderSelected", this.onDestinationSelected, this);
        parent.widgets.uploadArchive.set("disabled", true);

        var htmlForm = Dom.get(parent.id + "-options-form");
        htmlForm.setAttribute("action", Alfresco.constants.PROXY_URI + "slingshot/import/create-action");

        parent.form = new Alfresco.forms.Form(parent.id + "-options-form");
        parent.form.addValidation(parent.id + "-archive-node", Alfresco.forms.validation.mandatory, null, "change");
        parent.form.addValidation(parent.id + "-destination-node", Alfresco.forms.validation.length, {
           min: 60,
           max: 60
        }, "change");

        parent.form.doBeforeAjaxRequest = {
          fn: this.doBeforeAjaxRequest,
          scope: this
        };

        parent.form.setSubmitElements([parent.widgets.importButton]);
        parent.form.setSubmitAsJSON(true);
        parent.form.setShowSubmitStateDynamically(true);
        parent.form.setAJAXSubmit(true, {
          successCallback: {
            fn: this.onSuccess,
            scope: this
          },
          failureCallback: {
            fn: this.onFailure,
            scope: this
          }
        });
        parent.form.init();
      },

      doBeforeAjaxRequest: function OptionsPanel_doBeforeAjaxRequest(p_config)
      {
        parent.widgets.importButton.set("disabled", true);

        parent.widgets.archiveQueue = (parent.widgets.archiveNode.value || "").split(",");
        for (var i = parent.widgets.archiveQueue.length - 1; i >= 0; i--) {
          parent.widgets.archiveQueue[i] = parent.widgets.archiveQueue[i].replace(/^\s+|\s+$/g, "");
          if (parent.widgets.archiveQueue[i].length === 0) {
            parent.widgets.archiveQueue.splice(i, 1);
          }
        }

        if (parent.widgets.archiveQueue.length === 0) {
          parent.widgets.importButton.set("disabled", false);
          return false;
        }

        parent.widgets.runInBackgroundImport = !!p_config.dataObj["run-in-background"];


        if (!parent.widgets.runInBackgroundImport) {
          this.widgets.feedbackMessage = Alfresco.util.PopupManager.displayMessage({
            text: parent.msg("message.importing"),
            spanClass: "wait",
            displayTime: 0
          });
        } else {
          this.widgets.feedbackMessage = null;
        }

        this.importNextArchive();
        return false;
      },

      importNextArchive: function OptionsPanel_importNextArchive()
      {
        var archiveNodeRef = parent.widgets.archiveQueue.shift();

        if (!archiveNodeRef) {
          this.onSuccess();
          return;
        }

        var postBody = {
          "archive-node": archiveNodeRef,
          "destination-node": parent.widgets.destinationNodeField.value
        };

        if (parent.widgets.runInBackgroundImport) {
          postBody["run-in-background"] = true;
        }

        YAHOO.util.Connect.setDefaultPostHeader(false)
        this.applyCSRFHeaders();
        YAHOO.util.Connect.initHeader("Content-Type", "application/json; charset=UTF-8", true);
        YAHOO.util.Connect.asyncRequest("POST", Alfresco.constants.PROXY_URI + "slingshot/import/create-action", {
          scope: this,
          success: function(response)
          {
            this.importNextArchive();
          },
          failure: function(response)
          {
            this.onFailure(response);
          }
        }, YAHOO.lang.JSON.stringify(postBody));
      },

      getCookieValue: function OptionsPanel_getCookieValue(name)
      {
        var cookieString = document.cookie || "",
            escapedName = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
            match = cookieString.match(new RegExp("(?:^|; )" + escapedName + "=([^;]*)"));

        return match ? decodeURIComponent(match[1]) : null;
      },

      resolveCSRFToken: function OptionsPanel_resolveCSRFToken()
      {
        if (Alfresco.util && typeof Alfresco.util.getCSRFToken === "function") {
          var utilToken = Alfresco.util.getCSRFToken();
          if (utilToken) {
            return utilToken;
          }
        }

        return this.getCookieValue("Alfresco-CSRFToken") ||
               this.getCookieValue("CSRF-TOKEN") ||
               this.getCookieValue("XSRF-TOKEN");
      },

      applyCSRFHeaders: function OptionsPanel_applyCSRFHeaders()
      {
        var csrfToken = this.resolveCSRFToken();

        if (!csrfToken) {
          return;
        }

        YAHOO.util.Connect.initHeader("Alfresco-CSRFToken", csrfToken, true);
        YAHOO.util.Connect.initHeader("X-Alfresco-CSRFToken", csrfToken, true);
      },

      onSuccess: function OptionsPanel_onSuccess(response)
      {
        if (this.widgets.feedbackMessage) {
          this.widgets.feedbackMessage.destroy();

          Alfresco.util.PopupManager.displayPrompt({
            title: parent.msg("message.success.title"),
            text: parent.msg("message.success")
          });
        }

        parent.widgets.archiveNode.value = "";
        parent.widgets.destinationNodeField.value = "";
        parent.widgets.destinationPathField.innerHTML = "";
        parent.widgets.archivePathField.innerHTML = "";
        parent.widgets.archiveQueue = [];
        parent.widgets.archiveDetails = {};
        parent.widgets.runInBackgroundImport = false;
        parent.widgets.uploadArchive.set("disabled", true);
        parent.form.updateSubmitElements();
      },

      onFailure: function OptionsPanel_onFailure(response)
      {
        var errorText = parent.msg("message.unknown-error"),
            parsedError = null;

        if (this.widgets.feedbackMessage) {
          this.widgets.feedbackMessage.destroy();
        }

        if (response && response.responseText) {
          try {
            var jsonResponse = YAHOO.lang.JSON.parse(response.responseText);
            parsedError = jsonResponse;
            if (jsonResponse && jsonResponse.status && jsonResponse.status.message) {
              errorText = jsonResponse.status.message;
            }
          } catch (e) {
            // Keep default message when response payload is not JSON.
          }
        }

        parent.widgets.archiveQueue = [];
        parent.widgets.runInBackgroundImport = false;

        parent.form.updateSubmitElements();

        Alfresco.util.PopupManager.displayPrompt({
          title: parent.msg("message.unknown-error.title"),
          text: errorText
        });
      },

      onUpload: function OptionsPanel_onUpload(e, p_obj)
      {
        if (!this.fileUpload) {
           this.fileUpload = Alfresco.getFileUploadInstance();
        }

        var uploadConfig = {
          mode: this.fileUpload.MODE_MULTI_UPLOAD,
          destination: parent.widgets.destinationNodeField.value,
          filter: [{
            description: parent.msg("message.upload.description"),
            extensions: parent.extensions
          }],
          onFileUploadComplete: {
            fn: this.onFileUploadComplete,
            scope: this
          },
          onFileUploadFailure: {
            fn: this.onFileUploadFailure,
            scope: this
          }
        };
        this.fileUpload.show(uploadConfig);
        Event.preventDefault(e);
      },

      onSelectDestinationClick: function OptionsPanel_onSelectDestinationClick(e, p_obj)
      {
        if (!this.widgets.destinationDialog) {
          this.widgets.destinationDialog = new Alfresco.module.DoclibGlobalFolder(this.id + "-selectDestination");
          var allowedViewModes = [ Alfresco.module.DoclibGlobalFolder.VIEW_MODE_REPOSITORY ];

          this.widgets.destinationDialog.setOptions({
            allowedViewModes: allowedViewModes,
            siteId: this.options.siteId,
            containerId: this.options.containerId,
            title: this.msg("title.destinationDialog"),
            nodeRef: "alfresco://company/home"
          });
        }

        var pathNodeRef = this.widgets.destinationNodeField.value;
        this.widgets.destinationDialog.setOptions({
          pathNodeRef: pathNodeRef ? new Alfresco.util.NodeRef(pathNodeRef) : null
        });

        this.widgets.destinationDialog.showDialog();
      },

      onDestinationSelected: function OptionsPanel_onDestinationSelected(layer, args)
      {
        if ($hasEventInterest(parent.widgets.destinationDialog, args)) {
          var obj = args[1];
          if (obj !== null) {
            parent.widgets.destinationNodeField.value = obj.selectedFolder.nodeRef;
            parent.widgets.destinationPathField.innerHTML = obj.selectedFolder.path;
            parent.widgets.uploadArchive.set("disabled", false);
          }
        }

        parent.form.updateSubmitElements();
      }
    });
    new OptionsPanelHandler();

    return this;
  };

  YAHOO.extend(Alfresco.ImportTool, Alfresco.ConsoleTool,
  {
    onFileUploadComplete: function onFileUploadComplete(complete)
    {
      var successful = complete.successful || [],
          success = successful.length,
          nodeRefs = this.widgets.archiveNode.value ? this.widgets.archiveNode.value.split(",") : [],
          filePaths = [],
          currentText = this.widgets.archivePathField.textContent || "",
          basePath = this.widgets.destinationPathField.innerHTML;

      if (success > 0) {
        for (var i = 0; i < success; i++) {
          var uploaded = successful[i],
              fileName = uploaded.fileName || "",
              extensionMatch = fileName.match(/\.([^\.]+)$/),
              extension = extensionMatch ? extensionMatch[1].toLowerCase() : "";

          nodeRefs.push(uploaded.nodeRef);
          filePaths.push(basePath + "/" + uploaded.fileName);
          this.widgets.archiveDetails[uploaded.nodeRef] = {
            fileName: fileName,
            extension: extension
          };
        }

        this.widgets.archiveNode.value = nodeRefs.join(",");
        this.widgets.archivePathField.textContent = currentText ? currentText + ", " + filePaths.join(", ") : filePaths.join(", ");

        this.form.updateSubmitElements();
      }
    },

    onFileUploadFailure: function onFileUploadFailure(error)
    {
      console.log("File upload failed with error:", error);
      throw error;
    }
  });
})();
