package org.endeavourhealth.enterprise.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.endeavourhealth.enterprise.core.database.models.ItemEntity;

/**
 * JSON object used to manipulate folders, such as creating, moving and renaming
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JsonFolder implements Comparable {

    public static final int FOLDER_TYPE_LIBRARY = 1;
    public static final int FOLDER_TYPE_REPORTS = 2;

    private String uuid = null;
    private String folderName = null;
    private Integer folderType = null;
    private String parentFolderUuid = null;
    private Boolean hasChildren = null;
    private Integer contentCount = null;

    public JsonFolder() {
    }

    public JsonFolder(ItemEntity item, int contentCount, boolean hasChildren) {
        this.uuid = item.getItemUuid();
        this.folderName = item.getTitle();

        this.hasChildren = hasChildren;

        if (contentCount > 0) {
            this.contentCount = new Integer(contentCount);
        }
    }
    /*public JsonFolder(DbFolder folder, int count)
    {
        this.uuid = folder.getPrimaryUuid();
        this.folderName = folder.getTitle();
        this.folderType = new Integer(folder.getFolderType());
        this.parentFolderUuid = folder.getParentFolderUuid();
        this.hasChildren = folder.getHasChildren();
        if (count > -1)
        {
            contentCount = new Integer(count);
        }
    }*/

    /**
     * gets/sets
     */
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public Integer getFolderType() {
        return folderType;
    }

    public void setFolderType(Integer folderType) {
        this.folderType = folderType;
    }

    public String getParentFolderUuid() {
        return parentFolderUuid;
    }

    public void setParentFolderUuid(String parentUuid) {
        this.parentFolderUuid = parentUuid;
    }

    public Integer getContentCount() {
        return contentCount;
    }

    public void setContentCount(Integer contentCount) {
        this.contentCount = contentCount;
    }

    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    @Override
    public int compareTo(Object o) {
        JsonFolder other = (JsonFolder)o;
        return folderName.compareToIgnoreCase(other.folderName);
    }
}
