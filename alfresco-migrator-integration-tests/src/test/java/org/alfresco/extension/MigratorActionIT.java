package org.alfresco.extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.extension.repo.action.executer.ExporterActionExecuter;
import org.alfresco.extension.repo.action.executer.ImporterActionExecuter;
import org.alfresco.model.ContentModel;
import org.alfresco.rad.test.AbstractAlfrescoIT;
import org.alfresco.rad.test.AlfrescoTestRunner;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.nodelocator.CompanyHomeNodeLocator;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(value = AlfrescoTestRunner.class)
public class MigratorActionIT extends AbstractAlfrescoIT {

    /**
     * Test the export and import of a simple folder structure with a single
     * text file using the Exporter and Importer Action Executors.
     *
     * @throws Exception if any error occurs during the test
     */
    @Test
    public void testMigratorWorkflow() throws Exception {
        String fileName = "sample.txt";
        String fileContent = "Hello Alfresco";
        String folderName = "migrator";
        String exportFolderName = folderName + "-export";
        String importFolderName = folderName + "-import";
        String packageName = "migrator-test-export";

        HashMap<QName, Serializable> fileProperties = new HashMap<>();
        fileProperties.put(ContentModel.PROP_NAME, fileName);

        NodeRef workspaceFolder = createNode(folderName, ContentModel.TYPE_FOLDER,
                createFolderProperties(folderName));
        NodeRef fileNodeRef = createNode(fileName, ContentModel.TYPE_CONTENT, fileProperties, workspaceFolder);
        addFileContent(fileNodeRef, fileContent);

        NodeRef exportDestination = createNode(exportFolderName, ContentModel.TYPE_FOLDER,
                createFolderProperties(exportFolderName), workspaceFolder);
        removeExistingChildByName(exportDestination, packageName + ".acp");

        Action exportAction = createExportAction(exportDestination, packageName);
        getServiceRegistry().getActionService().executeAction(exportAction, workspaceFolder);

        NodeRef exportedPackage = findChildByName(exportDestination, packageName + ".acp");
        assertNotNull("Export package node should exist", exportedPackage);

        ContentReader packageReader = getServiceRegistry().getContentService().getReader(exportedPackage, ContentModel.PROP_CONTENT);
        assertNotNull("Export package content reader should exist", packageReader);
        assertEquals("Export package should be ACP", MimetypeMap.MIMETYPE_ACP, packageReader.getMimetype());

        NodeRef importDestination = createNode(importFolderName, ContentModel.TYPE_FOLDER,
                createFolderProperties(importFolderName), workspaceFolder);
        removeExistingChildByName(importDestination, folderName);
        Action importAction = createImportAction(importDestination);
        getServiceRegistry().getActionService().executeAction(importAction, exportedPackage);

        NodeRef importedFolder = findChildByName(importDestination, folderName);
        assertNotNull("Imported root folder should exist", importedFolder);

        NodeRef importedFile = findChildByName(importedFolder, fileName);
        assertNotNull("Imported content file should exist", importedFile);

        String importedContent = readTextContent(importedFile);
        assertEquals(fileContent, importedContent);
    }

    /**
     * Helper function to create the export action.
     *
     * @param destinationFolder the destination folder for the exported package
     * @param packageName the name of the exported package
     * @return the created export action
     */
    private Action createExportAction(NodeRef destinationFolder, String packageName) {
        Action action = getServiceRegistry().getActionService().createAction(ExporterActionExecuter.NAME);
        action.setParameterValue(ExporterActionExecuter.PARAM_PACKAGE_NAME, packageName);
        action.setParameterValue(ExporterActionExecuter.PARAM_DESTINATION_FOLDER, destinationFolder);
        action.setParameterValue(ExporterActionExecuter.PARAM_STORE, "workspace://SpacesStore");
        action.setParameterValue(ExporterActionExecuter.PARAM_ENCODING, "UTF-8");
        action.setParameterValue(ExporterActionExecuter.PARAM_INCLUDE_CHILDREN, Boolean.TRUE);
        action.setParameterValue(ExporterActionExecuter.PARAM_INCLUDE_SELF, Boolean.TRUE);
        return action;
    }

    /**
     * Helper function to create the import action.
     *
     * @param destinationFolder the destination folder for the imported content
     * @return the created import action
     */
    private Action createImportAction(NodeRef destinationFolder) {
        Action action = getServiceRegistry().getActionService().createAction(ImporterActionExecuter.NAME);
        action.setParameterValue(ImporterActionExecuter.PARAM_DESTINATION_FOLDER, destinationFolder);
        action.setParameterValue(ImporterActionExecuter.PARAM_ENCODING, "UTF-8");
        action.setParameterValue(ImporterActionExecuter.PARAM_UUID_BINDING, "CREATE_NEW");
        action.setParameterValue(ImporterActionExecuter.PARAM_INCLUDE_VERSION_HISTORY, Boolean.FALSE);
        return action;
    }

    /**
     * Read text content for passed in file Node Reference
     *
     * @param nodeRef the node reference for a file containing text
     * @return the text content
     */
    private String readTextContent(NodeRef nodeRef) {
        ContentReader reader = getServiceRegistry().getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
        try (InputStream is = reader.getContentInputStream()) {
            return IOUtils.toString(is, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find a direct child node by name under a given parent node.
     *
     * @param parent the parent node reference
     * @param name the name of the child node to find
     * @return the NodeRef of the found child node, or null if not found
     */
    private NodeRef findChildByName(NodeRef parent, String name) {
        if (parent == null || name == null) {
            return null;
        }

        for (ChildAssociationRef parentChildAssocRef : getServiceRegistry().getNodeService().getChildAssocs(parent)) {
            NodeRef childNodeRef = parentChildAssocRef.getChildRef();
            String childName = (String) getServiceRegistry().getNodeService().getProperty(childNodeRef, ContentModel.PROP_NAME);
            if (name.equals(childName)) {
                return childNodeRef;
            }
        }
        return null;
    }

    /**
     * Remove an existing direct child node by name under a given parent node.
     *
     * @param parent the parent node reference
     * @param name the name of the child node to remove
     */
    private void removeExistingChildByName(NodeRef parent, String name) {
        NodeRef existingNode = findChildByName(parent, name);
        if (existingNode == null) {
            return;
        }

        ChildAssociationRef parentAssoc = getServiceRegistry().getNodeService().getPrimaryParent(existingNode);
        if (parentAssoc != null) {
            getServiceRegistry().getNodeService().removeChild(parentAssoc.getParentRef(), parentAssoc.getChildRef());
        }
    }

    /**
     * Create a new node, such as a file or a folder, with passed in type and
     * properties
     *
     * @param name the name of the file or folder
     * @param type the content model type
     * @param properties the properties from the content model
     * @return the Node Reference for the newly created node
     */
    private NodeRef createNode(String name, QName type, Map<QName, Serializable> properties) {
        return createNode(name, type, properties, getCompanyHomeNodeRef());
    }

    /**
     * Create a new node, such as a file or a folder, with passed in type and
     * properties, under the specified parent folder node reference
     *
     * @param name the name of the file or folder
     * @param type the content model type
     * @param properties the properties from the content model
     * @param parentFolderNodeRef the node reference for the parent folder
     * @return the Node Reference for the newly created node
     */
    private NodeRef createNode(String name, QName type, Map<QName, Serializable> properties, NodeRef parentFolderNodeRef) {
        removeExistingChildByName(parentFolderNodeRef, name);

        QName associationType = ContentModel.ASSOC_CONTAINS;
        QName associationQName = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI,
                QName.createValidLocalName(name));
        Map<QName, Serializable> nodeProperties = new HashMap<>(properties);
        nodeProperties.put(ContentModel.PROP_NAME, name);
        ChildAssociationRef parentChildAssocRef = getServiceRegistry().getNodeService().createNode(
                parentFolderNodeRef, associationType, associationQName, type, nodeProperties);

        return parentChildAssocRef.getChildRef();
    }

    /**
     * Helper method to create a map of properties for a folder node 
     *
     * @param folderName the name of the folder
     * @return a map of properties for the folder node
     */
    private Map<QName, Serializable> createFolderProperties(String folderName) {
        HashMap<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, folderName);
        return properties;
    }

    /**
     * Add some text content to a file node
     *
     * @param nodeRef the node reference for the file that should have some text
     * content added to it
     * @param fileContent the text content
     */
    private void addFileContent(NodeRef nodeRef, String fileContent) {
        boolean updateContentPropertyAutomatically = true;
        ContentWriter writer = getServiceRegistry().getContentService().getWriter(nodeRef, ContentModel.PROP_CONTENT,
                updateContentPropertyAutomatically);
        writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
        writer.setEncoding("UTF-8");
        writer.putContent(fileContent);
    }

    /**
     * Get the node reference for the /Company Home top folder in Alfresco. Use
     * the standard node locator service.
     *
     * @return the node reference for /Company Home
     */
    private NodeRef getCompanyHomeNodeRef() {
        return getServiceRegistry().getNodeLocatorService().getNode(CompanyHomeNodeLocator.NAME, null, null);
    }
}
