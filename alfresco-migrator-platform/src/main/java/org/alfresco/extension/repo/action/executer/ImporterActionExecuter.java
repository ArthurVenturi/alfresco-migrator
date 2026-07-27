/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2022 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.extension.repo.action.executer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ApplicationModel;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.extension.repo.importer.ACPImportPackageHandler;
import org.alfresco.extension.service.cmr.view.ImporterBinding;
import org.alfresco.extension.service.cmr.view.ImporterContentCache;
import org.alfresco.extension.service.cmr.view.ImporterException;
import org.alfresco.extension.service.cmr.view.ImporterService;
import org.alfresco.extension.service.cmr.view.Location;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.InputStreamStatistics;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Importer action executor
 * 
 * @author gavinc
 */
public class ImporterActionExecuter extends ActionExecuterAbstractBase {
    private static final Log logger = LogFactory.getLog(ImporterActionExecuter.class);

    public static final String NAME = "migratorImporter";
    public static final String PARAM_ENCODING = "encoding";
    public static final String PARAM_DESTINATION_FOLDER = "destination";
    public static final String PARAM_UUID_BINDING = "uuid-binding";
    // Reuses exporter action naming so Share/rules can pass one flag for both directions.
    public static final String PARAM_INCLUDE_VERSION_HISTORY = "include-versions";
    // Binding key consumed by ImporterComponent to enable/disable version replay.
    private static final String IMPORT_VERSION_HISTORY_BINDING_KEY = "import-version-history";
    public static final String ARCHIVE_CONTAINS_SUSPICIOUS_PATHS_ERROR = "Archive contains suspicious paths. Please review it's contents and make sure it doesn't contain entries with absolute paths or paths containing references to the parent folder (i.e. \"..\")";

    private static final int BUFFER_SIZE = 16384;
    private static final String TEMP_FILE_PREFIX = "alf";
    private static final String TEMP_FILE_SUFFIX_ACP = ".acp";
    private static final String ACP_FILE_EXTENSION = ".acp";

    private long ratioThreshold;
    private long uncompressedBytesLimit = -1L;
    private boolean highByteZip = false;
    private String defaultUuidBinding = ImporterBinding.UUID_BINDING.CREATE_NEW.name();
    private boolean includeVersionHistory = true;

    /**
     * The importer service
     */
    private ImporterService importerService;

    /**
     * The node service
     */
    private NodeService nodeService;

    /**
     * The content service
     */
    private ContentService contentService;

    /**
     * The file folder service
     */
    private FileFolderService fileFolderService;

    /**
     * Sets the ImporterService to use
     * 
     * @param importerService The ImporterService
     */
    public void setImporterService(ImporterService importerService) {
        this.importerService = importerService;
    }

    /**
     * Sets the NodeService to use
     * 
     * @param nodeService The NodeService
     */
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    /**
     * Sets the ContentService to use
     * 
     * @param contentService The ContentService
     */
    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * Sets the FileFolderService to use
     * 
     * @param fileFolderService The FileFolderService
     */
    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    /**
     * @return the highByteZip encoding switch
     */
    public boolean isHighByteZip() {
        return this.highByteZip;
    }

    /**
     * @param highByteZip the encoding switch for high-byte ZIP filenames to set
     */
    public void setHighByteZip(boolean highByteZip) {
        this.highByteZip = highByteZip;
    }

    /**
     * @param ratioThreshold the compression ratio threshold for Zip bomb detection
     */
    public void setRatioThreshold(long ratioThreshold) {
        this.ratioThreshold = ratioThreshold;
    }

    /**
     * This method sets a value for the uncompressed bytes limit. If the string does
     * not {@link Long#parseLong(String) parse} to a
     * java long.
     *
     * @param limit a String representing a valid Java long.
     */
    public void setUncompressedBytesLimit(String limit) {
        // A string parameter is used here in order to not to require end users to
        // provide a value for the limit in a property
        // file. This results in the empty string being injected to this method.
        long longLimit = -1L;
        try {
            longLimit = Long.parseLong(limit);
        } catch (NumberFormatException ignored) {
            // Intentionally empty
        }
        this.uncompressedBytesLimit = longLimit;
    }

    /**
     * @param defaultUuidBinding default UUID binding strategy used when action
     *                           parameter {@link #PARAM_UUID_BINDING} is absent
     */
    public void setDefaultUuidBinding(String defaultUuidBinding) {
        this.defaultUuidBinding = defaultUuidBinding;
    }

    /**
     * @param includeVersionHistory default behavior when action parameter
     *                                     {@link #PARAM_INCLUDE_VERSION_HISTORY} is absent
     */
    public void setIncludeVersionHistory(boolean includeVersionHistory) {
        this.includeVersionHistory = includeVersionHistory;
    }

    /**
     * @see org.alfresco.repo.action.executer.ActionExecuter#execute(Action,
     *      NodeRef)
     */
    public void executeImpl(Action ruleAction, NodeRef actionedUponNodeRef) {
        if (this.nodeService.exists(actionedUponNodeRef) == true) {
            // The node being passed in should be an Alfresco content package
            ContentReader reader = this.contentService.getReader(actionedUponNodeRef, ContentModel.PROP_CONTENT);
            if (reader != null) {
                NodeRef importDest = (NodeRef) ruleAction.getParameterValue(PARAM_DESTINATION_FOLDER);
                boolean acpLikePackage = isAcpLikePackage(actionedUponNodeRef);
                if (MimetypeMap.MIMETYPE_ACP.equals(reader.getMimetype()) || acpLikePackage) {
                    if (!MimetypeMap.MIMETYPE_ACP.equals(reader.getMimetype())) {
                        logger.debug("ACP package detected by filename; overriding mimetype '" + reader.getMimetype()
                                + "' and using ACP import flow. packageNode=" + actionedUponNodeRef
                                + ", destination=" + importDest);
                    }
                    importAcpPackage(ruleAction, actionedUponNodeRef, importDest, reader);
                } else if (MimetypeMap.MIMETYPE_ZIP.equals(reader.getMimetype())) {
                    // perform an import of a standard ZIP file
                    ZipFile zipFile = null;
                    File tempFile = null;
                    try {
                        logger.debug("Starting ZIP import action. packageNode=" + actionedUponNodeRef + ", destination="
                                + importDest);

                        tempFile = TempFileProvider.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX_ACP);
                        reader.getContent(tempFile);
                        // NOTE: This encoding allows us to workaround bug:
                        // http://bugs.sun.com/bugdatabase/view_bug.do;:WuuT?bug_id=4820807
                        // We also try to use the extra encoding information if present
                        String encoding = (String) ruleAction.getParameterValue(PARAM_ENCODING);
                        if (encoding == null) {
                            encoding = "UTF-8";
                        } else {
                            if (encoding.equalsIgnoreCase("default")) {
                                encoding = null;
                            }
                        }
                        zipFile = new ZipFile(tempFile, encoding, false);
                        // build a temp dir name based on the ID of the noderef we are importing
                        // also use the long life temp folder as large ZIP files can take a while
                        File alfTempDir = TempFileProvider.getLongLifeTempDir("import");
                        File tempDir = new File(
                                alfTempDir.getPath() + File.separatorChar + actionedUponNodeRef.getId());
                        try {
                            // TODO: improve this code to directly pipe the zip stream output into the repo
                            // objects -
                            // to remove the need to expand to the filesystem first?
                            extractFile(zipFile, tempDir.getPath(),
                                    new ZipBombProtection(ratioThreshold, uncompressedBytesLimit));
                            importDirectory(tempDir.getPath(), importDest);
                            logger.debug("ZIP import action completed successfully. packageNode=" + actionedUponNodeRef
                                    + ", destination=" + importDest);
                        } finally {
                            deleteDir(tempDir);
                        }
                    } catch (IOException ioErr) {
                        logger.error("ZIP import action failed. packageNode=" + actionedUponNodeRef + ", destination="
                                + importDest, ioErr);
                        throw new AlfrescoRuntimeException("Failed to import ZIP file.", ioErr);
                    } finally {
                        // now the import is done, delete the temporary file
                        if (tempFile != null) {
                            tempFile.delete();
                        }
                        if (zipFile != null) {
                            try {
                                zipFile.close();
                            } catch (IOException e) {
                                throw new AlfrescoRuntimeException("Failed to close zip package.", e);
                            }
                        }
                    }
                } else {
                    logger.warn("Unsupported package mimetype '" + reader.getMimetype()
                            + "'. Import action skipped. packageNode=" + actionedUponNodeRef + ", destination="
                            + importDest);
                }
            }
        }
    }

    private void importAcpPackage(Action ruleAction, NodeRef actionedUponNodeRef, NodeRef importDest,
            ContentReader reader) {
        logger.debug("Starting ACP import action. packageNode=" + actionedUponNodeRef + ", destination=" + importDest);

        File zipFile = null;
        try {
            zipFile = TempFileProvider.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX_ACP);
            reader.getContent(zipFile);

            ACPImportPackageHandler importHandler = new ACPImportPackageHandler(zipFile,
                    (String) ruleAction.getParameterValue(PARAM_ENCODING));

            ImporterBinding binding = buildImporterBinding(ruleAction);
            try {
                this.importerService.importView(importHandler, new Location(importDest), binding, null);
            } catch (RuntimeException ex) {
                if (shouldRetryWithReplaceExisting(binding, ex)) {
                    logger.warn("Detected historical backfill conflict with UPDATE_EXISTING. Retrying ACP import with REPLACE_EXISTING. packageNode="
                            + actionedUponNodeRef + ", destination=" + importDest, ex);
                    ImporterBinding replaceBinding = createImporterBinding(
                            ImporterBinding.UUID_BINDING.REPLACE_EXISTING,
                            bindingIncludesVersionHistory(binding));
                    this.importerService.importView(importHandler, new Location(importDest), replaceBinding, null);
                } else {
                    throw ex;
                }
            }

            logger.debug("ACP import action completed successfully. packageNode=" + actionedUponNodeRef
                    + ", destination=" + importDest);
        } finally {
            if (zipFile != null) {
                zipFile.delete();
            }
        }
    }

    private boolean isAcpLikePackage(NodeRef actionedUponNodeRef) {
        Serializable nameValue = nodeService.getProperty(actionedUponNodeRef, ContentModel.PROP_NAME);
        if (!(nameValue instanceof String)) {
            return false;
        }

        String fileName = ((String) nameValue).trim();
        return fileName.toLowerCase().endsWith(ACP_FILE_EXTENSION);
    }

    /**
     * Recursively import a directory structure into the specified root node
     * 
     * @param dir  The directory of files and folders to import
     * @param root The root node to import into
     */
    private void importDirectory(String dir, NodeRef root) {
        File topdir = new File(dir);
        for (File file : topdir.listFiles()) {
            try {
                if (file.isFile()) {
                    String fileName = file.getName();

                    // create content node based on the file name
                    FileInfo fileInfo = this.fileFolderService.create(root, fileName, ContentModel.TYPE_CONTENT);
                    NodeRef fileRef = fileInfo.getNodeRef();

                    // add titled aspect for the read/edit properties screens
                    Map<QName, Serializable> titledProps = new HashMap<QName, Serializable>(1, 1.0f);
                    titledProps.put(ContentModel.PROP_TITLE, fileName);
                    this.nodeService.addAspect(fileRef, ContentModel.ASPECT_TITLED, titledProps);

                    // push the content of the file into the node
                    InputStream contentStream = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE);
                    ContentWriter writer = this.contentService.getWriter(fileRef, ContentModel.PROP_CONTENT, true);
                    writer.guessMimetype(fileName);
                    writer.putContent(contentStream);
                } else {
                    String folderName = file.getName();

                    // create a folder based on the folder name
                    FileInfo folderInfo = this.fileFolderService.create(root, folderName, ContentModel.TYPE_FOLDER);
                    NodeRef folderRef = folderInfo.getNodeRef();

                    // add titled aspect
                    Map<QName, Serializable> titledProps = new HashMap<QName, Serializable>(1, 1.0f);
                    this.nodeService.addAspect(folderRef, ContentModel.ASPECT_TITLED, titledProps);

                    // add the uifacets aspect for the read/edit properties screens
                    this.nodeService.addAspect(folderRef, ApplicationModel.ASPECT_UIFACETS, null);

                    importDirectory(file.getPath(), folderRef);
                }
            } catch (FileNotFoundException e) {
                // TODO: add failed file info to status message?
                throw new AlfrescoRuntimeException("Failed to process ZIP file.", e);
            } catch (FileExistsException e) {
                // TODO: add failed file info to status message?
                throw new AlfrescoRuntimeException("Failed to process ZIP file.", e);
            }
        }
    }

    /**
     * @see org.alfresco.repo.action.ParameterizedItemAbstractBase#addParameterDefinitions(List)
     */
    protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_FOLDER, DataTypeDefinition.NODE_REF,
                true, getParamDisplayLabel(PARAM_DESTINATION_FOLDER)));
        paramList.add(new ParameterDefinitionImpl(PARAM_ENCODING, DataTypeDefinition.TEXT,
                false, getParamDisplayLabel(PARAM_ENCODING)));
        paramList.add(new ParameterDefinitionImpl(PARAM_UUID_BINDING, DataTypeDefinition.TEXT,
                false, getParamDisplayLabel(PARAM_UUID_BINDING)));
        paramList.add(new ParameterDefinitionImpl(PARAM_INCLUDE_VERSION_HISTORY, DataTypeDefinition.BOOLEAN,
            false, getParamDisplayLabel(PARAM_INCLUDE_VERSION_HISTORY)));
    }

    private ImporterBinding buildImporterBinding(Action ruleAction) {
        String requestedBinding = (String) ruleAction.getParameterValue(PARAM_UUID_BINDING);
        ImporterBinding.UUID_BINDING uuidBinding = resolveUuidBinding(
                StringUtils.isBlank(requestedBinding) ? defaultUuidBinding : requestedBinding);
        boolean includeVersionHistory = resolveIncludeVersionHistory(ruleAction);

        return createImporterBinding(uuidBinding, includeVersionHistory);
    }

    private ImporterBinding createImporterBinding(ImporterBinding.UUID_BINDING uuidBinding,
            boolean includeVersionHistory) {
        // Keep importer behavior controlled by one explicit binding value.
        return new ImporterBinding() {
            @Override
            public UUID_BINDING getUUIDBinding() {
                return uuidBinding;
            }

            @Override
            public boolean allowReferenceWithinTransaction() {
                return false;
            }

            @Override
            public String getValue(String key) {
                if (IMPORT_VERSION_HISTORY_BINDING_KEY.equals(key)) {
                    return Boolean.toString(includeVersionHistory);
                }
                return null;
            }

            @Override
            public QName[] getExcludedClasses() {
                return null;
            }

            @Override
            public ImporterContentCache getImportConentCache() {
                return null;
            }
        };
    }

    private boolean resolveIncludeVersionHistory(Action ruleAction) {
        // Action parameter wins; otherwise fallback to the bean default for backward compatibility.
        Boolean includeHistory = (Boolean) ruleAction.getParameterValue(PARAM_INCLUDE_VERSION_HISTORY);
        if (includeHistory != null) {
            return includeHistory.booleanValue();
        }
        return includeVersionHistory;
    }

    private boolean shouldRetryWithReplaceExisting(ImporterBinding binding, RuntimeException ex) {
        if (binding == null || binding.getUUIDBinding() != ImporterBinding.UUID_BINDING.UPDATE_EXISTING) {
            return false;
        }

        Throwable current = ex;
        while (current != null) {
            if (current instanceof ImporterException) {
                String message = current.getMessage();
                if (message != null
                        && message.contains("Historical backfill on update-existing import is not supported safely")) {
                    return true;
                }
            }
            current = current.getCause();
        }

        return false;
    }

    private boolean bindingIncludesVersionHistory(ImporterBinding binding) {
        if (binding == null) {
            return includeVersionHistory;
        }

        String value = binding.getValue(IMPORT_VERSION_HISTORY_BINDING_KEY);
        if (StringUtils.isBlank(value)) {
            return includeVersionHistory;
        }

        return Boolean.parseBoolean(value);
    }

    private ImporterBinding.UUID_BINDING resolveUuidBinding(String bindingName) {
        if (StringUtils.isBlank(bindingName)) {
            return ImporterBinding.UUID_BINDING.CREATE_NEW;
        }

        String normalized = bindingName.trim().toUpperCase().replace('-', '_');
        try {
            return ImporterBinding.UUID_BINDING.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid UUID binding strategy '" + bindingName + "'. Falling back to "
                    + defaultUuidBinding + ".");
            String normalizedDefault = StringUtils.isBlank(defaultUuidBinding)
                    ? ""
                    : defaultUuidBinding.trim().toUpperCase().replace('-', '_');
            try {
                return ImporterBinding.UUID_BINDING.valueOf(normalizedDefault);
            } catch (IllegalArgumentException ignored) {
                return ImporterBinding.UUID_BINDING.CREATE_NEW;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private ZipFile createZipFile(File tempFile, String encoding) throws IOException {
        String effectiveEncoding = encoding;
        if (effectiveEncoding == null || effectiveEncoding.equalsIgnoreCase("default")) {
            effectiveEncoding = "UTF-8";
        }

        return new ZipFile(tempFile, effectiveEncoding, false);
    }

    /**
     * Extract the file and folder structure of a ZIP file into the specified
     * directory
     *
     * @param archive    The ZIP archive to extract
     * @param extractDir The directory to extract into
     */
    public static void extractFile(ZipFile archive, String extractDir) {
        extractFile(archive, extractDir, ExtractionProgressTracker.NONE);
    }

    /**
     * Extract the file and folder structure of a ZIP file into the specified
     * directory using a progress tracker
     *
     * @param archive    The ZIP archive to extract
     * @param extractDir The directory to extract into
     * @param tracker    The extraction progress tracker to check against during the
     *                   extraction process
     */
    public static void extractFile(ZipFile archive, String extractDir, ExtractionProgressTracker tracker) {
        String fileName;
        String destFileName;
        byte[] buffer = new byte[BUFFER_SIZE];
        extractDir = extractDir + File.separator;
        try {
            long totalCompressedBytesCount = 0;
            long totalUncompressedBytesCount = 0;
            tracker.reportProgress(0, 0);
            for (Enumeration<ZipArchiveEntry> e = archive.getEntries(); e.hasMoreElements();) {
                ZipArchiveEntry entry = e.nextElement();
                if (!entry.isDirectory()) {
                    fileName = StringUtils.stripAccents(entry.getName()).replaceAll("\\?", "_");
                    fileName = fileName.replace('/', File.separatorChar);

                    if (fileName.startsWith("/") || fileName.indexOf(":" + File.separator) == 1
                            || fileName.contains(".." + File.separator)) {
                        throw new AlfrescoRuntimeException(ARCHIVE_CONTAINS_SUSPICIOUS_PATHS_ERROR);
                    }

                    destFileName = extractDir + fileName;
                    File destFile = new File(destFileName);
                    String parent = destFile.getParent();
                    if (parent != null) {
                        File parentFile = new File(parent);
                        if (!parentFile.exists())
                            parentFile.mkdirs();
                    }

                    try (InputStream zis = archive.getInputStream(entry);
                            InputStream in = new BufferedInputStream(zis, BUFFER_SIZE);
                            OutputStream out = new BufferedOutputStream(new FileOutputStream(destFileName),
                                    BUFFER_SIZE)) {
                        final InputStreamStatistics entryStats = (InputStreamStatistics) zis;
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            tracker.reportProgress(totalCompressedBytesCount + entryStats.getCompressedCount(),
                                    totalUncompressedBytesCount + entryStats.getUncompressedCount());
                            out.write(buffer, 0, count);
                        }
                        totalCompressedBytesCount += entryStats.getCompressedCount();
                        totalUncompressedBytesCount += entryStats.getUncompressedCount();
                    }
                } else {
                    File newdir = new File(extractDir + entry.getName());
                    newdir.mkdirs();
                }
            }
        } catch (ZipException e) {
            throw new AlfrescoRuntimeException("Failed to process ZIP file.", e);
        } catch (FileNotFoundException e) {
            throw new AlfrescoRuntimeException("Failed to process ZIP file.", e);
        } catch (IOException e) {
            throw new AlfrescoRuntimeException("Failed to process ZIP file.", e);
        }
    }

    /**
     * Recursively delete a dir of files and directories
     * 
     * @param dir directory to delete
     */
    public static void deleteDir(File dir) {
        if (dir != null) {
            File elenco = new File(dir.getPath());

            // listFiles can return null if the path is invalid i.e. already been deleted,
            // therefore check for null before using in loop
            File[] files = elenco.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile())
                        file.delete();
                    else
                        deleteDir(file);
                }
            }

            // delete provided directory
            dir.delete();
        }
    }

    private static class ZipBombProtection implements ExtractionProgressTracker {
        private final long ratioThreshold;
        private final long uncompressedBytesLimit;

        private ZipBombProtection(long ratioThreshold, long uncompressedBytesLimit) {
            this.ratioThreshold = ratioThreshold;
            this.uncompressedBytesLimit = uncompressedBytesLimit;
        }

        @Override
        public void reportProgress(long compressedBytesCount, long uncompressedBytesCount) {
            if (compressedBytesCount <= 0 || uncompressedBytesCount <= 0) {
                return;
            }

            long ratio = uncompressedBytesCount / compressedBytesCount;

            if (ratio > ratioThreshold) {
                throw new AlfrescoRuntimeException("Unexpected compression ratio detected (" + ratio
                        + "%). Possible zip bomb attack. Breaking the extraction process.");
            }

            if (uncompressedBytesLimit > 0 && uncompressedBytesCount > uncompressedBytesLimit) {
                throw new AlfrescoRuntimeException("Uncompressed bytes limit exceeded (" + uncompressedBytesCount
                        + "). Possible zip bomb attack. Breaking the extraction process.");
            }
        }
    }

    private interface ExtractionProgressTracker {
        void reportProgress(long compressedBytesCount, long uncompressedBytesCount);

        ExtractionProgressTracker NONE = new ExtractionProgressTracker() {
            @Override
            public void reportProgress(long compressedBytesCount, long uncompressedBytesCount) {
                // intentionally do nothing
            }
        };
    }
}
