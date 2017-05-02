package org.endeavourhealth.enterprise.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JsonCohortRun {

    private List<JsonOrganisation> organisation = new ArrayList<>();
    private String population = null;
    private String baselineDate = null;
    private String queryItemUuid = null;

    public JsonCohortRun() {
    }

    /**
     * gets/sets
     */
    public List<JsonOrganisation> getOrganisation() {
        return organisation;
    }

    public void setOrganisation(List<JsonOrganisation> organisation) {
        this.organisation = organisation;
    }

    public String getPopulation() {
        return population;
    }

    public void setPopulation(String population) {
        this.population = population;
    }

    public String getBaselineDate() {
        return baselineDate;
    }

    public void setBaselineDate(String baselineDate) {
        this.baselineDate = baselineDate;
    }

    public String getQueryItemUuid() {
        return queryItemUuid;
    }

    public void setQueryItemUuid(String queryItemUuid) {
        this.queryItemUuid = queryItemUuid;
    }


}