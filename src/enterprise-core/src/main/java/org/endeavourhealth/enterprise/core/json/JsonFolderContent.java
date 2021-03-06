package org.endeavourhealth.enterprise.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.endeavourhealth.enterprise.core.database.models.ActiveItemEntity;
import org.endeavourhealth.enterprise.core.database.models.AuditEntity;
import org.endeavourhealth.enterprise.core.database.models.ItemEntity;
import org.endeavourhealth.enterprise.core.database.models.data.CohortResultEntity;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JsonFolderContent implements Comparable {
    private String uuid = null;
    private Integer type = null;
    private String typeDesc = null;
    private String name = null;
    private String description = null;
    private Date lastModified = null;
    private Date lastRun = null; //only applicable when showing reports
    private Boolean isScheduled = null; //only applicable when showing reports
    private Boolean isRunning = null;

    public JsonFolderContent() {

    }

    public JsonFolderContent(ActiveItemEntity activeItem, ItemEntity item, AuditEntity audit, CohortResultEntity cohort) {
        this(item, audit, cohort);
        setTypeEnum(activeItem.getItemTypeId());
    }
    public JsonFolderContent(ItemEntity item, AuditEntity audit, CohortResultEntity cohort) {
        this.uuid = item.getItemUuid();
        this.name = item.getTitle();
        this.description = item.getDescription();
        this.isRunning = false;

        if (audit != null) {
            this.lastModified = new Date(audit.getTimeStamp().getTime());
        }

        if (cohort != null) {
            this.lastRun = new Date(cohort.getRunDate().getTime());
            if (this.lastRun.after(new Date()))
                this.isRunning = true;
        }
    }

    public void setTypeEnum(Short t) {
        setType(t.intValue());
        setTypeDesc(t.toString());
    }


    /**
     * gets/sets
     */
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getTypeDesc() {
        return typeDesc;
    }

    public void setTypeDesc(String typeDesc) {
        this.typeDesc = typeDesc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Date getLastRun() {
        return lastRun;
    }

    public void setLastRun(Date lastRun) {
        this.lastRun = lastRun;
    }

    public Boolean getIsScheduled() {
        return isScheduled;
    }

    public void setIsScheduled(Boolean scheduled) {
        isScheduled = scheduled;
    }

    public Boolean getIsRunning() {
        return isRunning;
    }

    public void setIsRunning(Boolean isRunning) {
        this.isRunning = isRunning;
    }

    @Override
    public int compareTo(Object o) {
        JsonFolderContent other = (JsonFolderContent)o;
        return name.compareToIgnoreCase(other.name);
    }
}
