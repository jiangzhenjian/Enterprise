package org.endeavour.enterprise.entity.json;

import org.endeavour.enterprise.entity.database.DbOrganisation;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by Drew on 18/02/2016.
 */
public final class JsonOrganisation implements Serializable
{

    private UUID organisationUuid = null;
    private String name = null;
    private String nationanId = null;

    public JsonOrganisation()
    {}
    public JsonOrganisation(DbOrganisation org)
    {
        this.organisationUuid = org.getPrimaryUuid();
        this.name = org.getName();
        this.nationanId = org.getNationalId();
    }

    /**
     * gets/sets
     */
    public UUID getOrganisationUuid() {
        return organisationUuid;
    }

    public void setOrganisationUuid(UUID organisationUuid) {
        this.organisationUuid = organisationUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNationanId() {
        return nationanId;
    }

    public void setNationanId(String nationanId) {
        this.nationanId = nationanId;
    }
}