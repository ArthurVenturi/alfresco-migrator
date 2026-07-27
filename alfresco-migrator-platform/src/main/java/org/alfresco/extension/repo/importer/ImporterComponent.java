package org.alfresco.extension.repo.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.extension.repo.importer.view.NodeContext;
import org.alfresco.extension.service.cmr.view.ImportPackageHandler;
import org.alfresco.extension.service.cmr.view.ImporterBinding;
import org.alfresco.extension.service.cmr.view.ImporterBinding.UUID_BINDING;
import org.alfresco.extension.service.cmr.view.ImporterContentCache;
import org.alfresco.extension.service.cmr.view.ImporterException;
import org.alfresco.extension.service.cmr.view.ImporterProgress;
import org.alfresco.extension.service.cmr.view.ImporterService;
import org.alfresco.extension.service.cmr.view.Location;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.filefolder.HiddenAspect;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.usage.ContentUsageImpl;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.repo.version.common.VersionUtil;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.ChildAssociationDefinition;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.InvalidClassException;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.XPathException;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.search.QueryParameterDefinition;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AccessPermission;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.OwnableService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.extensions.surf.util.ParameterCheck;
import org.springframework.util.StringUtils;
import org.xml.sax.ContentHandler;

public class ImporterComponent implements ImporterService {

    private static final Log logger = LogFactory.getLog(ImporterComponent.class);
    public static final String ACTION_MODEL_1_0_URI = "http://www.alfresco.org/model/action/1.0";
    private Parser viewParser;
    private NamespaceService namespaceService;
    private DictionaryService dictionaryService;
    private BehaviourFilter behaviourFilter;
    private NodeService nodeService;
    private SearchService searchService;
    private ContentService contentService;
    private RuleService ruleService;
    private PermissionService permissionService;
    private AuthorityService authorityService;
    private OwnableService ownableService;
    private VersionService versionService;
    private HiddenAspect hiddenAspect;
    private ContentUsageImpl contentUsageImpl;
    protected NodeService dbNodeService;

    public ImporterComponent() {
    }

    public void setViewParser(Parser viewParser) {
        this.viewParser = viewParser;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setBehaviourFilter(BehaviourFilter behaviourFilter) {
        this.behaviourFilter = behaviourFilter;
    }

    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void setOwnableService(OwnableService ownableService) {
        this.ownableService = ownableService;
    }

    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }

    public void setDbNodeService(NodeService nodeService) {
        this.dbNodeService = nodeService;
    }

    public void setHiddenAspect(HiddenAspect hiddenAspect) {
        this.hiddenAspect = hiddenAspect;
    }

    public void setContentUsageImpl(ContentUsageImpl contentUsageImpl) {
        this.contentUsageImpl = contentUsageImpl;
    }

    @Override
    public void importView(Reader viewReader, Location location, ImporterBinding binding, ImporterProgress progress) {
        NodeRef nodeRef = this.getNodeRef(location, binding);
        this.parserImport(nodeRef, location, viewReader, new DefaultStreamHandler(), binding, progress);
    }

    @Override
    public void importView(ImportPackageHandler importHandler, Location location, ImporterBinding binding,
            ImporterProgress progress) throws ImporterException {
        importHandler.startImport();
        Reader dataFileReader = importHandler.getDataStream();
        NodeRef nodeRef = this.getNodeRef(location, binding);
        this.parserImport(nodeRef, location, dataFileReader, importHandler, binding, progress);
        importHandler.endImport();
    }

    private NodeRef getNodeRef(Location location, ImporterBinding binding) {
        ParameterCheck.mandatory("Location", location);
        NodeRef nodeRef = location.getNodeRef();
        if (nodeRef == null) {
            nodeRef = nodeService.getRootNode(location.getStoreRef());
        }

        String path = location.getPath();
        if (path != null && path.length() > 0) {
            path = this.bindPlaceHolder(path, binding);
            path = this.createValidPath(path);
            List<NodeRef> nodeRefs = this.searchService.selectNodes(nodeRef, path, (QueryParameterDefinition[]) null,
                    this.namespaceService, false);
            if (nodeRefs.isEmpty()) {
                throw new ImporterException("Path " + path + " within node " + String.valueOf(nodeRef)
                        + " does not exist - the path must resolve to a valid location");
            }

            if (nodeRefs.size() > 1) {
                throw new ImporterException("Path " + path + " within node " + String.valueOf(nodeRef)
                        + " found too many locations - the path must resolve to one location");
            }

            nodeRef = (NodeRef) nodeRefs.get(0);
        }

        return nodeRef;
    }

    private String bindPlaceHolder(String value, ImporterBinding binding) {
        if (binding != null) {
            for (int iStartBinding = value.indexOf("${"); iStartBinding != -1; iStartBinding = value.indexOf("${")) {
                int iEndBinding = value.indexOf("}", iStartBinding + "${".length());
                if (iEndBinding == -1) {
                    throw new ImporterException("Cannot find end marker } within value " + value);
                }

                String key = value.substring(iStartBinding + "${".length(), iEndBinding);
                String keyValue = binding.getValue(key);
                if (keyValue == null) {
                    logger.warn("No binding value for placeholder (will default to empty string): " + value);
                }

                value = StringUtils.replace(value, "${" + key + "}", keyValue == null ? "" : keyValue);
            }
        }

        return value;
    }

    private String createValidPath(String path) {
        StringBuffer validPath = new StringBuffer(path.length());
        String[] segments = StringUtils.delimitedListToStringArray(path, "/");

        for (int i = 0; i < segments.length; ++i) {
            if (segments[i] != null && segments[i].length() > 0) {
                int colonIndex = segments[i].indexOf(58);
                if (colonIndex == -1) {
                    validPath.append(segments[i]);
                } else {
                    String[] qnameComponents = QName.splitPrefixedQName(segments[i]);
                    String localName = qnameComponents[1];
                    if (localName == null || localName.length() == 0) {
                        throw new IllegalArgumentException("Local name cannot be null or empty.");
                    }

                    localName = localName.replace("@", "_x0040_");
                    QName segmentQName = QName.createQName(qnameComponents[0], localName, this.namespaceService);
                    validPath.append(segmentQName.toPrefixString());
                }
            }

            if (i < segments.length - 1) {
                validPath.append("/");
            }
        }

        return validPath.toString();
    }

    public void parserImport(NodeRef nodeRef, Location location, Reader viewReader, ImportPackageHandler streamHandler,
            ImporterBinding binding, ImporterProgress progress) {
        ParameterCheck.mandatory("Node Reference", nodeRef);
        ParameterCheck.mandatory("View Reader", viewReader);
        ParameterCheck.mandatory("Stream Handler", streamHandler);
        Importer nodeImporter = new NodeImporter(nodeRef, location, binding, streamHandler, progress);

        try {
            nodeImporter.start();
            this.viewParser.parse(viewReader, nodeImporter);
            nodeImporter.end();
        } catch (RuntimeException e) {
            nodeImporter.error(e);
            throw e;
        }
    }

    public ContentHandler handlerImport(NodeRef nodeRef, Location location, ImportContentHandler handler,
            ImporterBinding binding, ImporterProgress progress) {
        ParameterCheck.mandatory("Node Reference", nodeRef);
        DefaultContentHandler defaultHandler = new DefaultContentHandler(handler);
        ImportPackageHandler streamHandler = new ContentHandlerStreamHandler(defaultHandler);
        Importer nodeImporter = new NodeImporter(nodeRef, location, binding, streamHandler, progress);
        defaultHandler.setImporter(nodeImporter);
        return defaultHandler;
    }

    private class NodeImporter implements Importer {

        private NodeRef rootRef;
        private QName rootAssocType;
        private Location location;
        private ImporterBinding binding;
        private ImporterProgress progress;
        private ImportPackageHandler streamHandler;
        private NodeImporterStrategy importStrategy;
        private UpdateExistingNodeImporterStrategy updateStrategy;
        private QName[] excludedClasses;
        private List<ImportedNodeRef> nodeRefs = new ArrayList<>();

        private NodeImporter(NodeRef rootRef, Location location, ImporterBinding binding,
                ImportPackageHandler streamHandler, ImporterProgress progress) {
            this.rootRef = rootRef;
            this.rootAssocType = location.getChildAssocType();
            this.location = location;
            this.binding = binding;
            this.progress = progress;
            this.streamHandler = streamHandler;
            this.importStrategy = this.createNodeImporterStrategy(binding == null ? null : binding.getUUIDBinding());
            this.updateStrategy = new UpdateExistingNodeImporterStrategy();
            if (binding != null && binding.getExcludedClasses() != null) {
                this.excludedClasses = binding.getExcludedClasses();
            } else {
                this.excludedClasses = new QName[] { ContentModel.ASPECT_REFERENCEABLE };
            }

        }

        private NodeImporterStrategy createNodeImporterStrategy(UUID_BINDING uuidBinding) {
            if (uuidBinding == null) {
                return new CreateNewNodeImporterStrategy(true);
            } else if (uuidBinding.equals(UUID_BINDING.CREATE_NEW)) {
                return new CreateNewNodeImporterStrategy(true);
            } else if (uuidBinding.equals(UUID_BINDING.CREATE_NEW_WITH_UUID)) {
                return new CreateNewNodeImporterStrategy(false);
            } else if (uuidBinding.equals(UUID_BINDING.REMOVE_EXISTING)) {
                return new RemoveExistingNodeImporterStrategy();
            } else if (uuidBinding.equals(UUID_BINDING.REPLACE_EXISTING)) {
                return new ReplaceExistingNodeImporterStrategy();
            } else if (uuidBinding.equals(UUID_BINDING.UPDATE_EXISTING)) {
                return new UpdateExistingNodeImporterStrategy();
            } else {
                return (NodeImporterStrategy) (uuidBinding.equals(UUID_BINDING.THROW_ON_COLLISION)
                        ? new ThrowOnCollisionNodeImporterStrategy()
                        : new CreateNewNodeImporterStrategy(true));
            }
        }

        public NodeRef getRootRef() {
            return this.rootRef;
        }

        public QName getRootAssocType() {
            return this.rootAssocType;
        }

        public Location getLocation() {
            return this.location;
        }

        public void start() {
            this.reportStarted();
        }

        public void importMetaData(Map<QName, String> properties) {
            String complexPath = (String) properties
                    .get(QName.createQName(NamespaceService.REPOSITORY_VIEW_1_0_URI, "exportOf"));

            for (String path : complexPath.split(",")) {
                if (path != null && path.equals("/")) {
                    NodeRef storeRootRef = nodeService.getRootNode(this.rootRef.getStoreRef());
                    if (!storeRootRef.equals(this.rootRef)) {
                        throw new ImporterException("A complete repository package cannot be imported here");
                    }
                }
            }

        }

        public NodeRef importNode(ImportNode context) {
            NodeRef nodeRef;
            if (context.isReference()) {
                nodeRef = this.linkNode(context);
            } else {
                nodeRef = this.importStrategy.importNode(context);
            }

            for (QName aspect : context.getNodeAspects()) {
                if (!nodeService.hasAspect(nodeRef, aspect)) {
                    nodeService.addAspect(nodeRef, aspect, (Map) null);
                    this.reportAspectAdded(nodeRef, aspect);
                }
            }

            hiddenAspect.checkHidden(nodeRef, false, false);

            for (Map.Entry<QName, Serializable> property : context.getProperties().entrySet()) {
                DataTypeDefinition valueDataType = context.getPropertyDataType((QName) property.getKey());
                if (valueDataType != null && valueDataType.getName().equals(DataTypeDefinition.CONTENT)) {
                    Object objVal = property.getValue();
                    if (objVal instanceof String) {
                        this.importContent(nodeRef, (QName) property.getKey(), (String) objVal);
                    } else if (objVal instanceof Collection) {
                        for (String value : (Collection<String>) objVal) {
                            this.importContent(nodeRef, (QName) property.getKey(), value);
                        }
                    }
                }
            }

            return nodeRef;
        }

        @Override
        public void applyVersionHistory(ImportNode context, NodeRef nodeRef) {
            // Version replay is only applicable to versionable nodes parsed with explicit
            // version entries.
            if (context != null && nodeRef != null) {
                if (context.getNodeAspects().contains(ContentModel.ASPECT_VERSIONABLE)) {
                    List<NodeContext.VersionImportData> versionEntries = null;
                    if (context instanceof NodeContext) {
                        versionEntries = ((NodeContext) context).getVersionHistoryEntries();
                    }

                    if (versionEntries != null && !versionEntries.isEmpty()) {
                        this.generateVersioningForVersionableNode(nodeRef, versionEntries);
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("No version entries parsed for versionable node " + String.valueOf(nodeRef)
                                    + "; skipping version-history replay.");
                        }

                    }
                }
            }
        }

        private void generateVersioningForVersionableNode(NodeRef nodeRef,
                List<NodeContext.VersionImportData> versionEntries) {
            // Normalize labels so creation runs oldest->newest when labels are numerically
            // comparable.
            List<NodeContext.VersionImportData> orderedEntries = this.normalizeEntriesForCreation(versionEntries);
            VersionHistory existingVersionHistory = versionService.getVersionHistory(nodeRef);
            if (existingVersionHistory != null) {
                if (this.importStrategy instanceof UpdateExistingNodeImporterStrategy) {
                    // UPDATE_EXISTING keeps node identity and attempts to complement existing
                    // history.
                    int backfilledVersions = this.backfillMissingImportedVersions(nodeRef, existingVersionHistory,
                            orderedEntries);
                    VersionHistory refreshedHistory = versionService.getVersionHistory(nodeRef);
                    this.replayImportedSnapshotsOnExistingHistory(nodeRef,
                            refreshedHistory == null ? existingVersionHistory : refreshedHistory, orderedEntries);
                    this.alignCurrentVersionLabelToLatestImported(nodeRef, orderedEntries);
                    this.ensureCurrentVersionLabelExists(nodeRef);
                    if (backfilledVersions > 0 && logger.isInfoEnabled()) {
                        logger.info("Backfilled missing imported version labels on existing node history. node="
                                + String.valueOf(nodeRef) + ", backfilledVersions=" + backfilledVersions);
                    }

                } else {
                    // Non-update strategies still reconcile imported snapshots with the current
                    // history.
                    int backfilledVersions = this.backfillMissingImportedVersions(nodeRef, existingVersionHistory,
                            orderedEntries);
                    VersionHistory refreshedHistory = versionService.getVersionHistory(nodeRef);
                    this.replayImportedSnapshotsOnExistingHistory(nodeRef,
                            refreshedHistory == null ? existingVersionHistory : refreshedHistory, orderedEntries);
                    this.alignCurrentVersionLabelToLatestImported(nodeRef, orderedEntries);
                    this.ensureCurrentVersionLabelExists(nodeRef);
                    if (logger.isInfoEnabled()) {
                        logger.info("Existing version history detected for node " + String.valueOf(nodeRef)
                                + " while using " + this.importStrategy.getClass().getSimpleName()
                                + ". Applied replay on existing history with backfilledVersions=" + backfilledVersions);
                    }

                }
            } else if (orderedEntries.isEmpty()) {
                // Preserve Alfresco expectation that a versionable node has an initial version.
                this.ensureCurrentVersionLabelExists(nodeRef);
                versionService.createVersion(nodeRef, (Map) null);
                this.ensureCurrentVersionLabelExists(nodeRef);
            } else {
                this.validateVersionReplayOrdering(nodeRef,
                        ((NodeContext.VersionImportData) orderedEntries.get(0)).getVersionLabel(), orderedEntries);
                int replayIndex = 0;

                for (NodeContext.VersionImportData versionEntry : orderedEntries) {
                    ++replayIndex;

                    try {
                        this.ensureCurrentVersionLabelExists(nodeRef);
                        Version createdVersion = versionService.createVersion(nodeRef,
                                this.buildImportedVersionProperties(versionEntry));
                        NodeRef frozenNodeRef = VersionUtil.convertNodeRef(createdVersion.getFrozenStateNodeRef());
                        this.applyImportedVersionLabel(frozenNodeRef, versionEntry.getVersionLabel());
                        this.applyFrozenSnapshot(frozenNodeRef, versionEntry);
                        this.applyVersionAuditMetadata(frozenNodeRef, versionEntry);
                    } catch (Exception e) {
                        String debugContext = this.buildVersionReplayDebugContext(nodeRef,
                                ((NodeContext.VersionImportData) orderedEntries.get(0)).getVersionLabel(),
                                orderedEntries, replayIndex, versionEntry);
                        throw new ImporterException("Failed to recreate historical version. " + debugContext
                                + ", cause=" + e.getClass().getName() + ": " + e.getMessage(), e);
                    }
                }

                this.alignCurrentVersionLabelToLatestImported(nodeRef, orderedEntries);
                this.ensureCurrentVersionLabelExists(nodeRef);
            }
        }

        private void replayImportedSnapshotsOnExistingHistory(NodeRef nodeRef, VersionHistory existingVersionHistory,
                List<NodeContext.VersionImportData> importedVersionEntries) {
            // Match by label and reapply imported frozen-state data to existing historical
            // entries.
            if (existingVersionHistory != null && importedVersionEntries != null && !importedVersionEntries.isEmpty()) {
                Map<String, Version> existingByLabel = new HashMap<>();

                for (Version existingVersion : existingVersionHistory.getAllVersions()) {
                    if (existingVersion != null && existingVersion.getVersionLabel() != null
                            && !existingByLabel.containsKey(existingVersion.getVersionLabel())) {
                        existingByLabel.put(existingVersion.getVersionLabel(), existingVersion);
                    }
                }

                int appliedSnapshots = 0;

                for (NodeContext.VersionImportData importedEntry : importedVersionEntries) {
                    if (importedEntry != null && importedEntry.getVersionLabel() != null) {
                        Version existingVersion = (Version) existingByLabel.get(importedEntry.getVersionLabel());
                        if (existingVersion != null) {
                            NodeRef existingFrozenNodeRef = VersionUtil
                                    .convertNodeRef(existingVersion.getFrozenStateNodeRef());
                            this.applyFrozenSnapshot(existingFrozenNodeRef, importedEntry);
                            this.applyVersionAuditMetadata(existingFrozenNodeRef, importedEntry);
                            ++appliedSnapshots;
                        }
                    }
                }

                logger.info("Replayed imported version snapshots on existing history. node=" + String.valueOf(nodeRef)
                        + ", appliedSnapshots=" + appliedSnapshots + ", importedEntries="
                        + importedVersionEntries.size());
            }
        }

        private int backfillMissingImportedVersions(NodeRef nodeRef, VersionHistory existingVersionHistory,
                List<NodeContext.VersionImportData> importedVersionEntries) {
            // Create only labels that are absent in target history so imported timeline can
            // be replayed.
            if (importedVersionEntries != null && !importedVersionEntries.isEmpty()) {
                Set<String> existingLabels = new HashSet<>();
                if (existingVersionHistory != null && existingVersionHistory.getAllVersions() != null) {
                    for (Version existingVersion : existingVersionHistory.getAllVersions()) {
                        if (existingVersion != null && existingVersion.getVersionLabel() != null) {
                            existingLabels.add(existingVersion.getVersionLabel());
                        }
                    }
                }

                List<String> missingImportedLabels = new ArrayList<>();

                for (NodeContext.VersionImportData importedEntry : importedVersionEntries) {
                    if (importedEntry != null && importedEntry.getVersionLabel() != null
                            && !existingLabels.contains(importedEntry.getVersionLabel())) {
                        missingImportedLabels.add(importedEntry.getVersionLabel());
                    }
                }

                if (missingImportedLabels.isEmpty()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "Version history already exists and is compatible for node " + String.valueOf(nodeRef)
                                        + ". Imported labels are already present: " + String.valueOf(existingLabels));
                    }

                    return 0;
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info(
                                "Detected missing imported version labels on existing node history; backfilling. node="
                                        + String.valueOf(nodeRef) + ", missingImportedLabels="
                                        + String.valueOf(missingImportedLabels) + ", existingLabels="
                                        + String.valueOf(existingLabels));
                    }

                    int createdVersions = 0;

                    for (NodeContext.VersionImportData importedEntry : importedVersionEntries) {
                        if (importedEntry != null && importedEntry.getVersionLabel() != null
                                && !existingLabels.contains(importedEntry.getVersionLabel())) {
                            try {
                                this.ensureCurrentVersionLabelExists(nodeRef);
                                Version historicalVersion = versionService.createVersion(nodeRef,
                                        this.buildImportedVersionProperties(importedEntry));
                                NodeRef historicalFrozenNodeRef = VersionUtil
                                        .convertNodeRef(historicalVersion.getFrozenStateNodeRef());
                                this.applyImportedVersionLabel(historicalFrozenNodeRef,
                                        importedEntry.getVersionLabel());
                                this.applyFrozenSnapshot(historicalFrozenNodeRef, importedEntry);
                                this.applyVersionAuditMetadata(historicalFrozenNodeRef, importedEntry);
                                existingLabels.add(importedEntry.getVersionLabel());
                                ++createdVersions;
                            } catch (Exception e) {
                                throw new ImporterException("Failed to backfill missing imported version label '"
                                        + importedEntry.getVersionLabel() + "' on node " + String.valueOf(nodeRef)
                                        + ". cause=" + e.getClass().getName() + ": " + e.getMessage(), e);
                            }
                        }
                    }

                    return createdVersions;
                }
            } else {
                return 0;
            }
        }

        private void applyImportedVersionLabel(NodeRef frozenNodeRef, String importedVersionLabel) {
            if (frozenNodeRef != null && StringUtils.hasText(importedVersionLabel)) {
                dbNodeService.setProperty(frozenNodeRef, ContentModel.PROP_VERSION_LABEL, importedVersionLabel);
                dbNodeService.setProperty(frozenNodeRef, Version2Model.PROP_QNAME_VERSION_LABEL, importedVersionLabel);
            }
        }

        private void applyVersionAuditMetadata(NodeRef frozenNodeRef, NodeContext.VersionImportData versionEntry) {
            if (frozenNodeRef != null && versionEntry != null && versionEntry.getVersionProperties() != null) {
                Map<String, String> props = versionEntry.getVersionProperties();
                String creator = this.firstNonBlank((String) props.get("versionProp.frozenCreator"),
                        (String) props.get("creator"), (String) props.get("versionProp.creator"));
                if (StringUtils.hasText(creator)) {
                    dbNodeService.setProperty(frozenNodeRef, ContentModel.PROP_CREATOR, creator);
                }

                String created = this.firstNonBlank((String) props.get("versionProp.frozenCreated"),
                        (String) props.get("createdDate"), (String) props.get("versionProp.created"));
                Date createdDate = this.parseDateSafely(created);
                if (createdDate != null) {
                    dbNodeService.setProperty(frozenNodeRef, ContentModel.PROP_CREATED, createdDate);
                }

                String modifier = this.firstNonBlank((String) props.get("versionProp.frozenModifier"),
                        (String) props.get("frozenModifier"), (String) props.get("versionProp.modifier"));
                if (StringUtils.hasText(modifier)) {
                    dbNodeService.setProperty(frozenNodeRef, ContentModel.PROP_MODIFIER, modifier);
                }

                String modified = this.firstNonBlank((String) props.get("versionProp.frozenModified"),
                        (String) props.get("frozenModifiedDate"), (String) props.get("versionProp.modified"));
                Date modifiedDate = this.parseDateSafely(modified);
                if (modifiedDate != null) {
                    dbNodeService.setProperty(frozenNodeRef, ContentModel.PROP_MODIFIED, modifiedDate);
                }

            }
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            } else {
                for (String value : values) {
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }

                return null;
            }
        }

        private Date parseDateSafely(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            } else {
                try {
                    return (Date) DefaultTypeConverter.INSTANCE.convert(Date.class, value);
                } catch (Exception var3) {
                    return null;
                }
            }
        }

        private void alignCurrentVersionLabelToLatestImported(NodeRef nodeRef,
                List<NodeContext.VersionImportData> orderedEntries) {
            if (nodeRef != null && orderedEntries != null && !orderedEntries.isEmpty()) {
                NodeContext.VersionImportData latestEntry = (NodeContext.VersionImportData) orderedEntries
                        .get(orderedEntries.size() - 1);
                if (latestEntry != null && StringUtils.hasText(latestEntry.getVersionLabel())) {
                    dbNodeService.setProperty(nodeRef, ContentModel.PROP_VERSION_LABEL, latestEntry.getVersionLabel());
                }
            }
        }

        private List<NodeContext.VersionImportData> normalizeEntriesForCreation(
                List<NodeContext.VersionImportData> versionEntries) {
            List<NodeContext.VersionImportData> ordered = new ArrayList<>();
            if (versionEntries != null && !versionEntries.isEmpty()) {
                for (NodeContext.VersionImportData entry : versionEntries) {
                    if (entry != null && StringUtils.hasText(entry.getVersionLabel())) {
                        ordered.add(entry);
                    }
                }

                if (ordered.size() < 2) {
                    return ordered;
                } else {
                    Boolean ascending = null;

                    for (int i = 1; i < ordered.size(); ++i) {
                        Integer comparison = this.compareNumericVersionLabels(
                                ((NodeContext.VersionImportData) ordered.get(i - 1)).getVersionLabel(),
                                ((NodeContext.VersionImportData) ordered.get(i)).getVersionLabel());
                        if (comparison != null && comparison != 0) {
                            boolean currentAscending = comparison < 0;
                            if (ascending == null) {
                                ascending = currentAscending;
                            } else if (ascending != currentAscending) {
                                return ordered;
                            }
                        }
                    }

                    if (ascending != null && !ascending) {
                        Collections.reverse(ordered);
                    }

                    return ordered;
                }
            } else {
                return ordered;
            }
        }

        private void ensureCurrentVersionLabelExists(NodeRef nodeRef) {
            // Some repositories may hold stale current-label values after prior
            // updates/imports.
            VersionHistory history = versionService.getVersionHistory(nodeRef);
            if (history != null && history.getAllVersions() != null && !history.getAllVersions().isEmpty()) {
                Set<String> existingLabels = new HashSet<>();
                String fallbackLabel = null;

                for (Version existingVersion : history.getAllVersions()) {
                    if (existingVersion != null && existingVersion.getVersionLabel() != null) {
                        String label = existingVersion.getVersionLabel();
                        if (fallbackLabel == null) {
                            fallbackLabel = label;
                        }

                        existingLabels.add(label);
                    }
                }

                if (fallbackLabel != null) {
                    String currentLabel = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_VERSION_LABEL);
                    if (!StringUtils.hasText(currentLabel) || !existingLabels.contains(currentLabel)) {
                        dbNodeService.setProperty(nodeRef, ContentModel.PROP_VERSION_LABEL, fallbackLabel);
                        logger.warn("Adjusted inconsistent current version label before historical replay. node="
                                + String.valueOf(nodeRef) + ", previousLabel=" + currentLabel + ", fallbackLabel="
                                + fallbackLabel + ", existingLabels=" + String.valueOf(existingLabels));
                    }

                }
            }
        }

        private void validateVersionReplayOrdering(NodeRef nodeRef, String initialLabel,
                List<NodeContext.VersionImportData> versionEntries) {
            // Guard against mixed numeric ordering that would create historical versions
            // out of sequence.
            if (versionEntries != null && versionEntries.size() >= 2) {
                List<String> labels = new ArrayList<>();

                for (NodeContext.VersionImportData entry : versionEntries) {
                    if (entry != null && entry.getVersionLabel() != null) {
                        labels.add(entry.getVersionLabel());
                    }
                }

                if (labels.size() >= 2) {
                    Boolean ascending = null;
                    boolean allComparable = true;

                    for (int i = 1; i < labels.size(); ++i) {
                        Integer comparison = this.compareNumericVersionLabels((String) labels.get(i - 1),
                                (String) labels.get(i));
                        if (comparison == null) {
                            allComparable = false;
                            break;
                        }

                        if (comparison != 0) {
                            boolean currentAscending = comparison < 0;
                            if (ascending == null) {
                                ascending = currentAscending;
                            } else if (ascending != currentAscending) {
                                throw new ImporterException("Version ordering validation failed for node "
                                        + String.valueOf(nodeRef) + ": mixed numeric order in XML. labels="
                                        + String.valueOf(labels) + ", initialLabel=" + initialLabel);
                            }
                        }
                    }

                    if (!allComparable) {
                        logger.info("Version ordering validation for node " + String.valueOf(nodeRef)
                                + ": non-numeric labels detected, replay will follow XML order as provided. labels="
                                + String.valueOf(labels) + ", initialLabel=" + initialLabel);
                    } else {
                        String order = ascending != null && !ascending ? "newest->oldest" : "oldest->newest";
                        logger.info("Version ordering validation for node " + String.valueOf(nodeRef)
                                + ": detected XML order=" + order + ", labels=" + String.valueOf(labels)
                                + ", initialLabel=" + initialLabel);
                    }
                }
            }
        }

        private Integer compareNumericVersionLabels(String left, String right) {
            int[] leftParts = this.parseNumericVersionParts(left);
            int[] rightParts = this.parseNumericVersionParts(right);
            if (leftParts != null && rightParts != null) {
                int maxLength = Math.max(leftParts.length, rightParts.length);

                for (int i = 0; i < maxLength; ++i) {
                    int leftValue = i < leftParts.length ? leftParts[i] : 0;
                    int rightValue = i < rightParts.length ? rightParts[i] : 0;
                    if (leftValue != rightValue) {
                        return leftValue < rightValue ? -1 : 1;
                    }
                }

                return 0;
            } else {
                return null;
            }
        }

        private int[] parseNumericVersionParts(String label) {
            if (label == null) {
                return null;
            } else {
                String trimmed = label.trim();
                if (trimmed.length() == 0) {
                    return null;
                } else {
                    String[] rawParts = trimmed.split("\\.");
                    int[] parts = new int[rawParts.length];

                    for (int i = 0; i < rawParts.length; ++i) {
                        String part = rawParts[i];
                        if (part.length() == 0) {
                            return null;
                        }

                        for (int c = 0; c < part.length(); ++c) {
                            if (!Character.isDigit(part.charAt(c))) {
                                return null;
                            }
                        }

                        try {
                            parts[i] = Integer.parseInt(part);
                        } catch (NumberFormatException var8) {
                            return null;
                        }
                    }

                    return parts;
                }
            }
        }

        private String buildVersionReplayDebugContext(NodeRef nodeRef, String initialLabel,
                List<NodeContext.VersionImportData> versionEntries, int replayIndex,
                NodeContext.VersionImportData currentVersionEntry) {
            List<String> labels = new ArrayList<>();
            if (versionEntries != null) {
                for (NodeContext.VersionImportData versionEntry : versionEntries) {
                    if (versionEntry != null) {
                        labels.add(versionEntry.getVersionLabel());
                    }
                }
            }

            String currentLabel = currentVersionEntry == null ? null : currentVersionEntry.getVersionLabel();
            int propertyCount = currentVersionEntry != null && currentVersionEntry.getVersionProperties() != null
                    ? currentVersionEntry.getVersionProperties().size()
                    : 0;
            int contentCount = currentVersionEntry != null
                    && currentVersionEntry.getVersionContentByPropertyQName() != null
                            ? currentVersionEntry.getVersionContentByPropertyQName().size()
                            : 0;
            return "node=" + String.valueOf(nodeRef) + ", replayIndex=" + replayIndex + ", currentLabel=" + currentLabel
                    + ", initialLabel=" + initialLabel + ", propertiesInCurrentEntry=" + propertyCount
                    + ", contentEntriesInCurrentEntry=" + contentCount + ", xmlLabels=" + String.valueOf(labels);
        }

        private Map<String, Serializable> buildImportedVersionProperties(NodeContext.VersionImportData versionEntry) {
            Map<String, Serializable> importedVersionProperties = new HashMap<>();
            if (versionEntry != null && versionEntry.getVersionProperties() != null) {
                for (Map.Entry<String, String> importedProperty : versionEntry.getVersionProperties().entrySet()) {
                    String key = (String) importedProperty.getKey();
                    String value = (String) importedProperty.getValue();
                    if (key != null) {
                        if (key.equals("description") && value != null && value.trim().length() > 0) {
                            importedVersionProperties.put("description", value);
                        } else if (key.equals("creator") && StringUtils.hasText(value)) {
                            importedVersionProperties.put("creator", value);
                        } else if (key.equals("createdDate") && StringUtils.hasText(value)) {
                            Date parsedDate = this.parseDateSafely(value);
                            if (parsedDate != null) {
                                importedVersionProperties.put("createdDate", parsedDate);
                            }
                        } else if (key.equals("versionType") && StringUtils.hasText(value)) {
                            // createVersion expects VersionType enum, not raw text.
                            VersionType parsedVersionType = this.parseVersionTypeSafely(value);
                            if (parsedVersionType != null) {
                                importedVersionProperties.put("versionType", parsedVersionType);
                            }
                        }
                    }
                }

                return importedVersionProperties;
            } else {
                return importedVersionProperties;
            }
        }

        private VersionType parseVersionTypeSafely(String value) {
            // Map known values only; unknown values are ignored to keep import resilient.
            if (!StringUtils.hasText(value)) {
                return null;
            } else if ("MAJOR".equalsIgnoreCase(value)) {
                return VersionType.MAJOR;
            } else if ("MINOR".equalsIgnoreCase(value)) {
                return VersionType.MINOR;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Ignoring unsupported versionType value during import: " + value);
                }

                return null;
            }
        }

        private void applyFrozenSnapshot(NodeRef frozenNodeRef, NodeContext.VersionImportData versionEntry) {
            // Keep auditable behavior disabled while replaying frozen snapshot writes,
            // then restore explicit audit values afterwards via applyVersionAuditMetadata.
            boolean auditableDisabled = false;

            try {
                behaviourFilter.disableBehaviour(frozenNodeRef, ContentModel.ASPECT_AUDITABLE);
                auditableDisabled = true;
                this.applyFrozenProperties(frozenNodeRef, versionEntry);
                this.applyFrozenContent(frozenNodeRef, versionEntry);
            } finally {
                if (auditableDisabled) {
                    behaviourFilter.enableBehaviour(frozenNodeRef, ContentModel.ASPECT_AUDITABLE);
                }
            }
        }

        private void applyFrozenProperties(NodeRef frozenNodeRef, NodeContext.VersionImportData versionEntry) {
            if (versionEntry != null && versionEntry.getVersionProperties() != null) {
                for (Map.Entry<String, String> entry : versionEntry.getVersionProperties().entrySet()) {
                    String key = (String) entry.getKey();
                    if (key != null && key.startsWith("frozenProp.")) {
                        String qnameAsPrefix = key.substring("frozenProp.".length());
                        QName propertyQName = QName.resolveToQName(namespaceService, qnameAsPrefix);
                        if (propertyQName != null) {
                            if (!this.shouldRestoreFrozenProperty(propertyQName)) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Skipping immutable/non-importable frozen property '" + qnameAsPrefix
                                            + "' on " + String.valueOf(frozenNodeRef));
                                }
                            } else {
                                try {
                                    String rawValue = (String) entry.getValue();
                                    if (this.isMandatoryPropertyWithBlankValue(propertyQName, rawValue)) {
                                        if (logger.isWarnEnabled()) {
                                            logger.warn("Skipping blank mandatory frozen property '" + qnameAsPrefix
                                                    + "' on " + String.valueOf(frozenNodeRef)
                                                    + " to avoid invalid historical state.");
                                        }
                                    } else {
                                        dbNodeService.setProperty(frozenNodeRef, propertyQName,
                                                this.convertPropertyValue(propertyQName, rawValue));
                                    }
                                } catch (Exception e) {
                                    throw new ImporterException("Failed to restore frozen property '" + qnameAsPrefix
                                            + "' on " + String.valueOf(frozenNodeRef), e);
                                }
                            }
                        }
                    }
                }

            }
        }

        private boolean isMandatoryPropertyWithBlankValue(QName propertyQName, String rawValue) {
            if (rawValue != null && rawValue.trim().length() > 0) {
                return false;
            }

            PropertyDefinition propertyDefinition = dictionaryService.getProperty(propertyQName);
            if (propertyDefinition != null && propertyDefinition.isMandatory()) {
                return true;
            }

            return ACTION_MODEL_1_0_URI.equals(propertyQName.getNamespaceURI())
                    && "script-ref".equals(propertyQName.getLocalName());
        }

        private Serializable convertPropertyValue(QName propertyQName, String value) {
            PropertyDefinition propertyDefinition = dictionaryService.getProperty(propertyQName);
            if (propertyDefinition != null && propertyDefinition.getDataType() != null && value != null) {
                return (Serializable) (DataTypeDefinition.CONTENT.equals(propertyDefinition.getDataType().getName())
                        ? value
                        : (Serializable) DefaultTypeConverter.INSTANCE.convert(propertyDefinition.getDataType(),
                                value));
            } else {
                return value;
            }
        }

        private void applyFrozenContent(NodeRef frozenNodeRef, NodeContext.VersionImportData versionEntry) {
            if (versionEntry != null && versionEntry.getVersionContentByPropertyQName() != null) {
                for (Map.Entry<String, String> contentEntry : versionEntry.getVersionContentByPropertyQName()
                        .entrySet()) {
                    String propertyQNameAsPrefix = (String) contentEntry.getKey();
                    String contentDescriptor = (String) contentEntry.getValue();
                    if (propertyQNameAsPrefix != null && contentDescriptor != null && contentDescriptor.length() != 0) {
                        QName propertyQName = QName.resolveToQName(namespaceService, propertyQNameAsPrefix);
                        if (propertyQName != null) {
                            if (!this.shouldRestoreFrozenProperty(propertyQName)) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Skipping immutable/non-importable frozen content property '"
                                            + propertyQNameAsPrefix + "' on " + String.valueOf(frozenNodeRef));
                                }
                            } else {
                                try {
                                    this.importContent(frozenNodeRef, propertyQName, contentDescriptor);
                                } catch (Exception e) {
                                    throw new ImporterException("Failed to restore frozen content '"
                                            + propertyQNameAsPrefix + "' on " + String.valueOf(frozenNodeRef), e);
                                }
                            }
                        }
                    }
                }

            }
        }

        private boolean shouldRestoreFrozenProperty(QName propertyQName) {
            if (propertyQName == null) {
                return false;
            } else {
                return !this.isNeverRestorableFrozenProperty(propertyQName);
            }
        }

        private boolean isNeverRestorableFrozenProperty(QName propertyQName) {
            if (!NamespaceService.SYSTEM_MODEL_1_0_URI.equals(propertyQName.getNamespaceURI())) {
                return false;
            } else {
                String localName = propertyQName.getLocalName();
                return "node-uuid".equals(localName) || "node-dbid".equals(localName)
                        || "store-protocol".equals(localName) || "store-identifier".equals(localName)
                        || "cascadeTx".equals(localName) || "cascadeCRC".equals(localName);
            }
        }

        private NodeRef linkNode(ImportNode context) {
            ImportParent parentContext = context.getParentContext();
            NodeRef parentRef = parentContext.getParentRef();
            String uuid = context.getUUID();
            if (uuid != null && uuid.length() != 0) {
                NodeRef referencedRef = new NodeRef(this.rootRef.getStoreRef(), uuid);
                if (!parentRef.equals(this.getRootRef())) {
                    QName assocType = this.getAssocType(context);
                    AssociationDefinition assocDef = dictionaryService.getAssociation(assocType);
                    if (assocDef.isChild()) {
                        QName childQName = this.getChildName(context);
                        if (childQName == null) {
                            String name = (String) nodeService.getProperty(referencedRef, ContentModel.PROP_NAME);
                            if (name == null || name.length() == 0) {
                                throw new ImporterException("Cannot determine node reference child name");
                            }

                            String localName = QName.createValidLocalName(name);
                            childQName = QName.createQName(assocType.getNamespaceURI(), localName);
                        }

                        nodeService.addChild(parentRef, referencedRef, assocType, childQName);
                        this.reportNodeLinked(referencedRef, parentRef, assocType, childQName);
                    } else {
                        nodeService.createAssociation(parentRef, referencedRef, assocType);
                        this.reportNodeLinked(parentRef, referencedRef, assocType, (QName) null);
                    }
                }

                this.updateStrategy.importNode(context);
                return referencedRef;
            } else {
                throw new ImporterException("Node reference does not specify a reference to follow.");
            }
        }

        private void importContent(NodeRef nodeRef, QName propertyName, String importContentData) {
            ImporterContentCache contentCache = this.binding == null ? null : this.binding.getImportConentCache();
            importContentData = bindPlaceHolder(importContentData, this.binding);
            if (importContentData != null && importContentData.length() > 0) {
                DataTypeDefinition dataTypeDef = dictionaryService.getDataType(DataTypeDefinition.CONTENT);
                ContentData contentData = (ContentData) DefaultTypeConverter.INSTANCE.convert(dataTypeDef,
                        importContentData);
                String contentUrl = contentData.getContentUrl();
                if (contentUrl != null && contentUrl.length() > 0) {
                    Map<QName, Serializable> propsBefore = null;
                    if (contentUsageImpl != null && contentUsageImpl.getEnabled()) {
                        propsBefore = nodeService.getProperties(nodeRef);
                    }

                    if (contentCache != null) {
                        ContentData cachedContentData = contentCache.getContent(this.streamHandler, contentData);
                        nodeService.setProperty(nodeRef, propertyName, cachedContentData);
                    } else {
                        InputStream contentStream = this.streamHandler.importStream(contentUrl);
                        ContentWriter writer = contentService.getWriter(nodeRef, propertyName, true);
                        writer.setEncoding(contentData.getEncoding());
                        writer.setMimetype(contentData.getMimetype());
                        writer.putContent(contentStream);
                    }

                    if (contentUsageImpl != null && contentUsageImpl.getEnabled()) {
                        Map<QName, Serializable> propsAfter = nodeService.getProperties(nodeRef);
                        contentUsageImpl.onUpdateProperties(nodeRef, propsBefore, propsAfter);
                    }

                    this.reportContentCreated(nodeRef, contentUrl);
                }
            }

        }

        public void childrenImported(NodeRef nodeRef) {
            behaviourFilter.enableBehaviour(nodeRef);
            ruleService.enableRules(nodeRef);
        }

        public NodeRef resolvePath(String path) {
            NodeRef referencedRef = null;
            if (path != null && path.length() > 0) {
                referencedRef = this.resolveImportedNodeRef(this.rootRef, path);
            }

            return referencedRef;
        }

        public boolean isExcludedClass(QName className) {
            for (QName excludedClass : this.excludedClasses) {
                if (excludedClass.equals(className)) {
                    return true;
                }
            }

            return false;
        }

        public void end() {
            for (ImportedNodeRef importedRef : this.nodeRefs) {
                Serializable refProperty = null;
                if (importedRef.value != null) {
                    if (!(importedRef.value instanceof Collection)) {
                        refProperty = this.resolveImportedNodeRef(importedRef.context.getNodeRef(),
                                (String) importedRef.value);
                    } else {
                        Collection<String> unresolvedRefs = (Collection<String>) importedRef.value;
                        List<NodeRef> resolvedRefs = new ArrayList<>(unresolvedRefs.size());

                        for (String unresolvedRef : unresolvedRefs) {
                            if (unresolvedRef != null) {
                                NodeRef nodeRef = this.resolveImportedNodeRef(importedRef.context.getNodeRef(),
                                        unresolvedRef);
                                if (nodeRef != null) {
                                    resolvedRefs.add(nodeRef);
                                }
                            }
                        }

                        refProperty = (Serializable) resolvedRefs;
                    }
                }

                Set<QName> nodeTypeAndAspects = this.getNodeTypeAndAspects(importedRef.context);

                try {
                    for (QName typeOrAspect : nodeTypeAndAspects) {
                        behaviourFilter.disableBehaviour(importedRef.context.getNodeRef(), typeOrAspect);
                    }

                    nodeService.setProperty(importedRef.context.getNodeRef(), importedRef.property, refProperty);
                    if (this.progress != null) {
                        this.progress.propertySet(importedRef.context.getNodeRef(), importedRef.property, refProperty);
                    }
                } finally {
                    for (QName typeOrAspect : nodeTypeAndAspects) {
                        behaviourFilter.enableBehaviour(importedRef.context.getNodeRef(), typeOrAspect);
                    }

                }
            }

            this.reportCompleted();
        }

        public void error(Throwable e) {
            behaviourFilter.enableBehaviour();
            this.reportError(e);
        }

        private QName getChildName(ImportNode context) {
            QName assocType = this.getAssocType(context);
            QName childQName = null;
            String childName = context.getChildName();
            if (childName != null) {
                childName = bindPlaceHolder(childName, this.binding);
                if (ContentModel.TYPE_PERSON.equals(context.getTypeDefinition().getName())
                        && assocType.equals(ContentModel.ASSOC_CHILDREN)) {
                    childName = childName.toLowerCase();
                }

                String[] qnameComponents = QName.splitPrefixedQName(childName);
                childQName = QName.createQName(qnameComponents[0], QName.createValidLocalName(qnameComponents[1]),
                        namespaceService);
            } else {
                Map<QName, Serializable> typeProperties = context.getProperties();
                Serializable nameValue = (Serializable) typeProperties.get(ContentModel.PROP_NAME);
                if (nameValue != null && !String.class.isAssignableFrom(nameValue.getClass())) {
                    throw new ImporterException("Unable to use childName property: "
                            + String.valueOf(ContentModel.PROP_NAME) + " is not a string");
                }

                String name = (String) nameValue;
                if (name != null && name.length() > 0) {
                    name = bindPlaceHolder(name, this.binding);
                    String localName = QName.createValidLocalName(name);
                    childQName = QName.createQName(assocType.getNamespaceURI(), localName);
                }
            }

            return childQName;
        }

        private QName getAssocType(ImportNode context) {
            QName assocType = context.getParentContext().getAssocType();
            if (assocType != null) {
                return assocType;
            } else {
                List<QName> nodeTypes = new ArrayList<>();
                nodeTypes.add(context.getTypeDefinition().getName());

                for (QName aspect : context.getNodeAspects()) {
                    nodeTypes.add(aspect);
                }

                Map<QName, QName> targetTypes = new HashMap<>();
                QName parentType = nodeService.getType(context.getParentContext().getParentRef());
                ClassDefinition classDef = dictionaryService.getClass(parentType);
                Map<QName, ChildAssociationDefinition> childAssocDefs = classDef.getChildAssociations();

                for (ChildAssociationDefinition childAssocDef : childAssocDefs.values()) {
                    targetTypes.put(childAssocDef.getTargetClass().getName(), childAssocDef.getName());
                }

                for (QName parentAspect : nodeService.getAspects(context.getParentContext().getParentRef())) {
                    classDef = dictionaryService.getClass(parentAspect);
                    if (classDef == null) {
                        throw new InvalidClassException(
                                "Failed import for context '" + String.valueOf(context.getParentContext())
                                        + "'.  Unknown aspect: " + String.valueOf(parentAspect),
                                parentAspect);
                    }

                    childAssocDefs = classDef.getChildAssociations();

                    for (ChildAssociationDefinition childAssocDef : childAssocDefs.values()) {
                        targetTypes.put(childAssocDef.getTargetClass().getName(), childAssocDef.getName());
                    }
                }

                QName closestAssocType = null;
                int closestHit = 1;

                for (QName nodeType : nodeTypes) {
                    for (QName targetType : targetTypes.keySet()) {
                        QName testType = nodeType;

                        ClassDefinition testTypeDef;
                        for (int howClose = 1; testType != null; testType = testTypeDef == null ? null
                                : testTypeDef.getParentName()) {
                            --howClose;
                            if (targetType.equals(testType) && howClose < closestHit) {
                                closestAssocType = (QName) targetTypes.get(targetType);
                                closestHit = howClose;
                                break;
                            }

                            testTypeDef = dictionaryService.getClass(testType);
                        }
                    }
                }

                return closestAssocType;
            }
        }

        private Set<QName> getNodeTypeAndAspects(ImportNode context) {
            Set<QName> classNames = new HashSet<>();
            TypeDefinition typeDef = context.getTypeDefinition();
            classNames.add(typeDef.getName());
            classNames.addAll(context.getNodeAspects());
            return classNames;
        }

        private Map<QName, Serializable> bindProperties(ImportNode context) {
            Map<QName, Serializable> properties = context.getProperties();
            Map<QName, Serializable> boundProperties = new HashMap<>(properties.size());

            for (QName property : properties.keySet()) {
                if (!this.isImporterManagedVersionProperty(property)) {
                    DataTypeDefinition valueDataType = context.getPropertyDataType(property);
                    if (valueDataType == null || !valueDataType.getName().equals(DataTypeDefinition.CONTENT)) {
                        Serializable value = properties.get(property);
                        if (!(value instanceof Collection)) {
                            value = this.bindValue(context, property, valueDataType, value);
                        } else {
                            List<Serializable> boundCollection = new ArrayList<>();

                            for (Serializable collectionValue : (Collection<Serializable>) value) {
                                Serializable objValue = this.bindValue(context, property, valueDataType,
                                        collectionValue);
                                boundCollection.add(objValue);
                            }

                            value = (Serializable) boundCollection;
                        }

                        if (valueDataType == null || !valueDataType.getName().equals(DataTypeDefinition.NODE_REF)
                                && !valueDataType.getName().equals(DataTypeDefinition.CATEGORY)) {
                            boundProperties.put(property, value);
                        } else {
                            ImportedNodeRef importedRef = new ImportedNodeRef(context, property, value);
                            this.nodeRefs.add(importedRef);
                        }
                    }
                }
            }

            return boundProperties;
        }

        private boolean isImporterManagedVersionProperty(QName property) {
            if (property == null) {
                return false;
            } else if (ContentModel.PROP_VERSION_LABEL.equals(property)) {
                return true;
            } else if (!NamespaceService.SYSTEM_MODEL_1_0_URI.equals(property.getNamespaceURI())) {
                return false;
            } else {
                String localName = property.getLocalName();
                return "versionHistoryMetadata".equals(localName) || "versionHistoryContent".equals(localName);
            }
        }

        private List<AccessPermission> bindPermissions(List<AccessPermission> permissions) {
            List<AccessPermission> boundPermissions = new ArrayList<>(permissions.size());

            for (AccessPermission permission : permissions) {
                AccessPermission ace = new NodeContext.ACE(permission.getAccessStatus(),
                        bindPlaceHolder(permission.getAuthority(), this.binding), permission.getPermission());
                boundPermissions.add(ace);
            }

            return boundPermissions;
        }

        private Serializable bindValue(ImportNode context, QName property, DataTypeDefinition valueType,
                Serializable value) {
            Serializable objValue = null;
            if (value != null && valueType != null) {
                if (value instanceof String) {
                    value = bindPlaceHolder(value.toString(), this.binding);
                }

                if (!valueType.getName().equals(DataTypeDefinition.NODE_REF)
                        && !valueType.getName().equals(DataTypeDefinition.CATEGORY)) {
                    objValue = (Serializable) DefaultTypeConverter.INSTANCE.convert(valueType, value);
                } else {
                    objValue = value;
                }
            }

            return objValue;
        }

        private NodeRef resolveImportedNodeRef(NodeRef sourceNodeRef, String importedRef) {
            NodeRef nodeRef = null;
            importedRef = bindPlaceHolder(importedRef, this.binding);
            if (importedRef.equals("/")) {
                nodeRef = sourceNodeRef;
            } else if (importedRef.startsWith("/")) {
                String path = createValidPath(importedRef);
                List<NodeRef> nodeRefs = searchService.selectNodes(sourceNodeRef, path,
                        (QueryParameterDefinition[]) null, namespaceService, false);
                if (nodeRefs.size() > 0) {
                    nodeRef = (NodeRef) nodeRefs.get(0);
                }
            } else if (NodeRef.isNodeRef(importedRef)) {
                nodeRef = new NodeRef(importedRef);
            } else {
                try {
                    String path = createValidPath(importedRef);
                    List<NodeRef> nodeRefs = searchService.selectNodes(sourceNodeRef, path,
                            (QueryParameterDefinition[]) null, namespaceService, false);
                    if (nodeRefs.size() > 0) {
                        nodeRef = (NodeRef) nodeRefs.get(0);
                    }
                } catch (XPathException var6) {
                    nodeRef = new NodeRef(importedRef);
                } catch (AlfrescoRuntimeException var7) {
                }
            }

            return nodeRef;
        }

        private void reportStarted() {
            if (this.progress != null) {
                this.progress.started();
            }

        }

        private void reportCompleted() {
            if (this.progress != null) {
                this.progress.completed();
            }

        }

        private void reportError(Throwable e) {
            if (this.progress != null) {
                this.progress.error(e);
            }

        }

        private void reportNodeCreated(ChildAssociationRef childAssocRef) {
            if (this.progress != null) {
                this.progress.nodeCreated(childAssocRef.getChildRef(), childAssocRef.getParentRef(),
                        childAssocRef.getTypeQName(), childAssocRef.getQName());
            }

        }

        private void reportNodeLinked(NodeRef childRef, NodeRef parentRef, QName assocType, QName childName) {
            if (this.progress != null) {
                this.progress.nodeLinked(childRef, parentRef, assocType, childName);
            }

        }

        private void reportContentCreated(NodeRef nodeRef, String sourceUrl) {
            if (this.progress != null) {
                this.progress.contentCreated(nodeRef, sourceUrl);
            }

        }

        private void reportAspectAdded(NodeRef nodeRef, QName aspect) {
            if (this.progress != null) {
                this.progress.aspectAdded(nodeRef, aspect);
            }

        }

        private void reportPropertySet(NodeRef nodeRef, Map<QName, Serializable> properties) {
            if (this.progress != null && properties != null) {
                for (QName property : properties.keySet()) {
                    this.progress.propertySet(nodeRef, property, (Serializable) properties.get(property));
                }
            }

        }

        private void reportPermissionSet(NodeRef nodeRef, List<AccessPermission> permissions) {
            if (this.progress != null && permissions != null) {
                for (AccessPermission permission : permissions) {
                    this.progress.permissionSet(nodeRef, permission);
                }
            }

        }

        private class CreateNewNodeImporterStrategy implements NodeImporterStrategy {
            private boolean assignNewUUID;

            public CreateNewNodeImporterStrategy(boolean assignNewUUID) {
                this.assignNewUUID = assignNewUUID;
            }

            public NodeRef importNode(ImportNode node) {
                TypeDefinition nodeType = node.getTypeDefinition();
                NodeRef parentRef = node.getParentContext().getParentRef();
                QName assocType = getAssocType(node);
                QName childQName = getChildName(node);
                if (childQName == null) {
                    throw new ImporterException(
                            "Cannot determine child name of node (type: " + String.valueOf(nodeType.getName()) + ")");
                } else {
                    Set<QName> nodeTypeAndAspects = getNodeTypeAndAspects(node);
                    for (QName typeOrAspect : nodeTypeAndAspects) {
                        behaviourFilter.disableBehaviour(typeOrAspect);
                    }
                    Map<QName, Serializable> initialProperties = bindProperties(node);
                    if (!this.assignNewUUID && node.getUUID() != null) {
                        initialProperties.put(ContentModel.PROP_NODE_UUID, node.getUUID());
                    }
                    ChildAssociationRef assocRef = nodeService.createNode(parentRef, assocType, childQName,
                            nodeType.getName(), initialProperties);
                    NodeRef nodeRef = assocRef.getChildRef();
                    if (!AuthenticationUtil.isRunAsUserTheSystemUser() && !authorityService.hasAdminAuthority()) {
                        ownableService.takeOwnership(nodeRef);
                    }
                    List<AccessPermission> permissions = null;
                    AccessStatus writePermission = permissionService.hasPermission(nodeRef, "ChangePermissions");
                    if (AuthenticationUtil.isRunAsUserTheSystemUser() || writePermission.equals(AccessStatus.ALLOWED)) {
                        permissions = bindPermissions(node.getAccessControlEntries());
                        for (AccessPermission permission : permissions) {
                            permissionService.setPermission(nodeRef, permission.getAuthority(),
                                    permission.getPermission(),
                                    permission.getAccessStatus().equals(AccessStatus.ALLOWED));
                        }
                        boolean inheritPermissions = node.getInheritPermissions();
                        if (!inheritPermissions) {
                            permissionService.setInheritParentPermissions(nodeRef, false);
                        }
                    }
                    for (QName typeOrAspect : nodeTypeAndAspects) {
                        behaviourFilter.enableBehaviour(typeOrAspect);
                    }
                    behaviourFilter.disableBehaviour(nodeRef);
                    ruleService.disableRules(nodeRef);
                    reportNodeCreated(assocRef);
                    reportPropertySet(nodeRef, initialProperties);
                    reportPermissionSet(nodeRef, permissions);
                    return nodeRef;
                }
            }
        }

        // NOTE: workaround the site folder issue when importing, find a solution so it
        // does without nedding a shortcut. (Archived)
        private class CreateNewNodeImporterSiteStrategy implements NodeImporterStrategy {

            private final boolean assignNewUUID;

            public CreateNewNodeImporterSiteStrategy(boolean assignNewUUID) {
                this.assignNewUUID = assignNewUUID;
            }

            public NodeRef importNode(ImportNode node) {
                TypeDefinition nodeType = node.getTypeDefinition();
                NodeRef parentRef = node.getParentContext().getParentRef();
                QName assocType = getAssocType(node);
                QName childQName = getChildName(node);

                if (childQName == null) {
                    throw new ImporterException(
                            "Cannot determine child name of node (type: " + String.valueOf(nodeType.getName()) + ")");
                } else {
                    Set<QName> nodeTypeAndAspects = getNodeTypeAndAspects(node);

                    for (QName typeOrAspect : nodeTypeAndAspects) {
                        behaviourFilter.disableBehaviour(typeOrAspect);
                    }

                    Map<QName, Serializable> initialProperties = bindProperties(node);
                    if (!this.assignNewUUID && node.getUUID() != null) {
                        initialProperties.put(ContentModel.PROP_NODE_UUID, node.getUUID());
                    }

                    // --- 1. MASK SITE AS FOLDER & STRIP PROPERTIES (REFACTORED) ---
                    QName actualTypeToCreate = nodeType.getName();

                    final boolean isSiteMorph = dictionaryService.isSubClass(actualTypeToCreate, SiteModel.TYPE_SITE);

                    if (isSiteMorph) {
                        actualTypeToCreate = ContentModel.TYPE_FOLDER;

                        java.util.Iterator<QName> propIter = initialProperties.keySet().iterator();
                        while (propIter.hasNext()) {
                            QName propName = propIter.next();
                            if (SiteModel.SITE_MODEL_URL.equals(propName.getNamespaceURI())) {
                                propIter.remove();
                            }
                        }
                        initialProperties.remove(ContentModel.PROP_TAGSCOPE_CACHE);
                    }

                    // Create the Node
                    ChildAssociationRef assocRef = nodeService.createNode(parentRef, assocType, childQName,
                            actualTypeToCreate, initialProperties);
                    final NodeRef nodeRef = assocRef.getChildRef();

                    // --- 2. FORCE OWNERSHIP (Adds cm:ownable aspect) ---
                    if (!AuthenticationUtil.isRunAsUserTheSystemUser() && !authorityService.hasAdminAuthority()) {
                        ownableService.takeOwnership(nodeRef);
                    } else if (isSiteMorph) {
                        ownableService.setOwner(nodeRef, AuthenticationUtil.getSystemUserName());
                    }

                    // --- 3. FIX PERMISSIONS ---
                    List<AccessPermission> permissions = null;
                    AccessStatus writePermission = permissionService.hasPermission(nodeRef, "ChangePermissions");
                    if (AuthenticationUtil.isRunAsUserTheSystemUser() || writePermission.equals(AccessStatus.ALLOWED)) {
                        permissions = bindPermissions(node.getAccessControlEntries());

                        if (!isSiteMorph) {
                            for (AccessPermission permission : permissions) {
                                permissionService.setPermission(nodeRef, permission.getAuthority(),
                                        permission.getPermission(),
                                        permission.getAccessStatus().equals(AccessStatus.ALLOWED));
                            }
                        }

                        boolean inheritPermissions = node.getInheritPermissions();
                        if (isSiteMorph) {
                            inheritPermissions = true; // FORCE INHERITANCE
                        }
                        if (!inheritPermissions) {
                            permissionService.setInheritParentPermissions(nodeRef, false);
                        }
                    }

                    for (QName typeOrAspect : nodeTypeAndAspects) {
                        behaviourFilter.enableBehaviour(typeOrAspect);
                    }

                    // --- 4. STRIP LINGERING ASPECTS POST-IMPORT (REFACTORED) ---
                    if (isSiteMorph) {
                        AlfrescoTransactionSupport.bindListener(
                                new TransactionListenerAdapter() {
                                    @Override
                                    public void beforeCommit(boolean readOnly) {

                                        // 1. Remove Tagscope
                                        if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TAGSCOPE)) {
                                            nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TAGSCOPE);
                                        }

                                        // 2. Remove sys:undeletable and sys:unmovable
                                        QName ASPECT_UNDELETABLE = QName
                                                .createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, "undeletable");
                                        QName ASPECT_UNMOVABLE = QName
                                                .createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, "unmovable");

                                        if (nodeService.hasAspect(nodeRef, ASPECT_UNDELETABLE)) {
                                            nodeService.removeAspect(nodeRef, ASPECT_UNDELETABLE);
                                        }
                                        if (nodeService.hasAspect(nodeRef, ASPECT_UNMOVABLE)) {
                                            nodeService.removeAspect(nodeRef, ASPECT_UNMOVABLE);
                                        }

                                        // 3. Remove any remaining Site (st:*) aspects
                                        Set<QName> currentAspects = nodeService
                                                .getAspects(nodeRef);
                                        for (QName aspect : currentAspects) {
                                            if (SiteModel.SITE_MODEL_URL.equals(aspect.getNamespaceURI())) {
                                                nodeService.removeAspect(nodeRef, aspect);
                                            }
                                        }
                                    }
                                });
                    }

                    behaviourFilter.disableBehaviour(nodeRef);
                    ruleService.disableRules(nodeRef);
                    reportNodeCreated(assocRef);
                    reportPropertySet(nodeRef, initialProperties);
                    reportPermissionSet(nodeRef, permissions);
                    return nodeRef;
                }
            }
        }

        private class RemoveExistingNodeImporterStrategy implements NodeImporterStrategy {

            private final NodeImporterStrategy createNewStrategy = new CreateNewNodeImporterStrategy(false);

            private RemoveExistingNodeImporterStrategy() {
            }

            public NodeRef importNode(ImportNode node) {
                String uuid = node.getUUID();
                if (uuid != null && uuid.length() > 0) {
                    NodeRef existingNodeRef = new NodeRef(rootRef.getStoreRef(), uuid);
                    if (nodeService.exists(existingNodeRef)) {
                        ChildAssociationRef childAssocRef = nodeService.getPrimaryParent(existingNodeRef);
                        nodeService.removeChild(childAssocRef.getParentRef(), childAssocRef.getChildRef());
                    }
                }

                return this.createNewStrategy.importNode(node);
            }
        }

        private class ReplaceExistingNodeImporterStrategy implements NodeImporterStrategy {

            private final NodeImporterStrategy createNewStrategy = new CreateNewNodeImporterStrategy(false);

            private ReplaceExistingNodeImporterStrategy() {
            }

            public NodeRef importNode(ImportNode node) {
                String uuid = node.getUUID();
                if (uuid != null && uuid.length() > 0) {
                    NodeRef existingNodeRef = new NodeRef(rootRef.getStoreRef(), uuid);
                    if (nodeService.exists(existingNodeRef)) {
                        ChildAssociationRef childAssocRef = nodeService.getPrimaryParent(existingNodeRef);
                        nodeService.removeChild(childAssocRef.getParentRef(), childAssocRef.getChildRef());
                        node.getParentContext().setParentRef(childAssocRef.getParentRef());
                        node.getParentContext().setAssocType(childAssocRef.getTypeQName());
                    }
                }

                return this.createNewStrategy.importNode(node);
            }
        }

        private class ThrowOnCollisionNodeImporterStrategy implements NodeImporterStrategy {

            private final NodeImporterStrategy createNewStrategy = new CreateNewNodeImporterStrategy(false);

            private ThrowOnCollisionNodeImporterStrategy() {
            }

            public NodeRef importNode(ImportNode node) {
                String uuid = node.getUUID();
                if (uuid != null && uuid.length() > 0) {
                    NodeRef existingNodeRef = new NodeRef(rootRef.getStoreRef(), uuid);
                    if (nodeService.exists(existingNodeRef)) {
                        throw new InvalidNodeRefException("Node " + String.valueOf(existingNodeRef) + " already exists",
                                existingNodeRef);
                    }
                }

                return this.createNewStrategy.importNode(node);
            }
        }

        private class UpdateExistingNodeImporterStrategy implements NodeImporterStrategy {

            private final NodeImporterStrategy createNewStrategy = new CreateNewNodeImporterStrategy(false);

            private UpdateExistingNodeImporterStrategy() {
            }

            public NodeRef importNode(ImportNode node) {
                String uuid = node.getUUID();
                NodeRef existingNodeRef = null;
                if (uuid == null && location.getPath() != null) {
                    NodeRef parentNodeRef = node.getParentContext().getParentRef();
                    String importPath = location.getPath();
                    String path = importPath + "/" + QName
                            .createQName(node.getTypeDefinition().getName().getNamespaceURI(), node.getChildName())
                            .toPrefixString();
                    List<NodeRef> nodeRefs = searchService.selectNodes(parentNodeRef, path,
                            (QueryParameterDefinition[]) null, namespaceService, false);
                    if (!nodeRefs.isEmpty()) {
                        existingNodeRef = (NodeRef) nodeRefs.get(0);
                    }
                }

                if (uuid != null && uuid.length() > 0 || existingNodeRef != null) {
                    if (existingNodeRef == null) {
                        existingNodeRef = new NodeRef(rootRef.getStoreRef(), uuid);
                    }

                    if (nodeService.exists(existingNodeRef)) {
                        Map<QName, Serializable> updateProperties = bindProperties(node);
                        if (updateProperties != null && updateProperties.size() > 0 && logger.isDebugEnabled()) {
                            logger.debug("Preserving existing node properties during update for "
                                    + String.valueOf(existingNodeRef)
                                    + "; imported metadata will not overwrite repository state.");
                        }

                        List<AccessPermission> permissions = null;
                        AccessStatus writePermission = permissionService.hasPermission(existingNodeRef,
                                "ChangePermissions");
                        if (AuthenticationUtil.isRunAsUserTheSystemUser()
                                || writePermission.equals(AccessStatus.ALLOWED)) {
                            boolean inheritPermissions = node.getInheritPermissions();
                            if (!inheritPermissions) {
                                permissionService.setInheritParentPermissions(existingNodeRef, false);
                            }

                            permissions = bindPermissions(node.getAccessControlEntries());

                            for (AccessPermission permission : permissions) {
                                permissionService.setPermission(existingNodeRef, permission.getAuthority(),
                                        permission.getPermission(),
                                        permission.getAccessStatus().equals(AccessStatus.ALLOWED));
                            }
                        }

                        if (logger.isDebugEnabled()) {
                            logger.debug("Updating existing node " + String.valueOf(existingNodeRef) + " at "
                                    + String.valueOf(nodeService.getPath(existingNodeRef)) + " for " + node.toString());
                        }

                        reportPropertySet(existingNodeRef, updateProperties);
                        reportPermissionSet(existingNodeRef, permissions);
                        return existingNodeRef;
                    }
                }

                return this.createNewStrategy.importNode(node);
            }
        }
    }

    private static class ImportedNodeRef {

        private ImportNode context;
        private QName property;
        private Serializable value;

        private ImportedNodeRef(ImportNode context, QName property, Serializable value) {
            this.context = context;
            this.property = property;
            this.value = value;
        }
    }

    private static class DefaultStreamHandler implements ImportPackageHandler {

        private DefaultStreamHandler() {
        }

        public void startImport() {
        }

        public InputStream importStream(String content) {
            ResourceLoader loader = new DefaultResourceLoader();
            Resource resource = loader.getResource(content);
            if (!resource.exists()) {
                throw new ImporterException("Content URL " + content + " does not exist.");
            } else {
                try {
                    return resource.getInputStream();
                } catch (IOException var5) {
                    throw new ImporterException("Failed to retrieve input stream for content URL " + content);
                }
            }
        }

        public Reader getDataStream() {
            return null;
        }

        public void endImport() {
        }
    }

    private static class ContentHandlerStreamHandler implements ImportPackageHandler {

        private ImportContentHandler handler;

        private ContentHandlerStreamHandler(ImportContentHandler handler) {
            this.handler = handler;
        }

        public void startImport() {
        }

        public InputStream importStream(String content) {
            return this.handler.importStream(content);
        }

        public Reader getDataStream() {
            return null;
        }

        public void endImport() {
        }
    }

    public interface NodeImporterStrategy {

        NodeRef importNode(ImportNode node);
    }
}
