package org.alfresco.extension.repo.importer;

import java.util.Date;

public interface ImportVersion {

    /**
     * @return the version created date
     */
    Date getVersionCreated();

    /**
     * @return the version label
     */
    String getVersionLabel();

    /**
     * @return the version description
     */
    String getVersionDescription();

    /**
     * @return the version author
     */
    String getVersionAuthor();
}
