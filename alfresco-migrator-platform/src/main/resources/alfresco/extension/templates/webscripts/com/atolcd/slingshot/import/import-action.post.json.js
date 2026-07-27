/**
 * Import Archive Upload method
 *
 * @method POST
 * @param filedata {file}
 */

function main() {
  try {
    var archiveNodes = [];
    if (json.has("archive-node")) {
      var archiveNodeValue = String(json.get("archive-node"));
      var parsedArchiveNodes = archiveNodeValue.split(",");
      for (var n = 0; n < parsedArchiveNodes.length; n++) {
        var parsedNode = String(parsedArchiveNodes[n]).replace(/^\s+|\s+$/g, "");
        if (parsedNode.length > 0) {
          archiveNodes.push(parsedNode);
        }
      }
    }

    if (archiveNodes.length === 0) {
      status.code = 400;
      status.message = "No valid archive-node values found in request.";
      status.redirect = true;
      return;
    }

    var destinationSpace = null;
    if (json.has("destination-node")) {
      var destinationNodeValue = json.get("destination-node");
      destinationSpace = search.findNode(destinationNodeValue);
    }

    // Execute action for every uploaded archive node, asynchronously or not.
    for (var i = 0; i < archiveNodes.length; i++) {
      var archiveNode = search.findNode(archiveNodes[i]);

      // create import action
      var importer = actions.create("migratorImporter");
      importer.parameters.encoding = "UTF-8";
      importer.parameters.destination = destinationSpace;

      if (archiveNode !== null) {
        if (json.has("run-in-background")) {
          importer.executeAsynchronously(archiveNode);
        } else {
          importer.execute(archiveNode);
        }
      }
    }

    status.code = 200;
  } catch (e) {
    status.code = 500;
    status.message = "Unexpected error occured during content extraction.";

    if (e.message && e.message.indexOf("org.alfresco.service.cmr.usage.ContentQuotaException") == 0) {
      status.code = 413;
      status.message = e.message;
    }
    status.redirect = true;
    return;
  }
}

main();
