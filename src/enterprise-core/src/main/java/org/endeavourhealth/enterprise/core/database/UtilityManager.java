package org.endeavourhealth.enterprise.core.database;

import org.endeavourhealth.enterprise.core.json.*;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class UtilityManager {

    private boolean includeOrganisationQuery = false;
    private String orgJoin = " join enterprise_admin.incidence_prevalence_organisation_list o " +
            " on o.organisation_id = p.organization_id ";
    private String whereClauses = "";

    public boolean runPrevIncReport(JsonPrevInc options) throws Exception {

        cleanUpDatabase();
        initialiseReportResultTable(options);
        generateWhereClausesFromOptions(options);
        createTemporaryTables();
        populateOrganisationTable(options);
        populatePatientTable();
        populateClinicalData(options.getCodeSet());
        updatePersonData();
        runInitialPopulationQuery();
        //runIncidenceQueries();
        //runPrevalenceQueries();
        //runPopulationQueries();

        return true;
    }

    private void initialiseReportResultTable(JsonPrevInc options) throws Exception {
        List<String> initialiseScripts = new ArrayList<>();

        initialiseScripts.add("delete from enterprise_admin.incidence_prevalence_result;");

        Date currentDate = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(currentDate);

        Date beginning;
        Date end;

        Integer number = Integer.parseInt(options.getTimePeriodNo());

        String insert = "insert into enterprise_admin.incidence_prevalence_result (query_id, query_title, min_date, max_date)\n" +
                "values ('70134d14-8402-11e7-a9c9-0a0027000012', '%s', '%s', '%s')";

        int precision = Calendar.DAY_OF_YEAR;
        int substractionPrecision = Calendar.YEAR;

        if (options.getTimePeriod().equals("MONTHS")) {
            precision = Calendar.DAY_OF_MONTH;
            substractionPrecision = Calendar.MONTH;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        for (Integer i = 0; i < number; i++) {
            // get the first day of the month/year
            c.set(precision, c.getActualMinimum(precision));
            beginning = c.getTime();
            // get the last day of the month/year
            c.set(precision, c.getActualMaximum(precision));
            end = c.getTime();

            initialiseScripts.add(String.format(insert, options.getTitle(), dateFormat.format(beginning).toString(),dateFormat.format(end).toString()));

            c.add(substractionPrecision, -1);
        }

        for (String script : initialiseScripts) {
            System.out.println(script);
            runScript(script);
        }

    }

    private void cleanUpDatabase() throws Exception {

        includeOrganisationQuery = false;
        whereClauses = "";

        List<String> deleteScripts = new ArrayList<>();

        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_raw_data;");
        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_patient_list;");
        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_organisation_list;");
        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_population_list;");

        for (String script : deleteScripts) {
            runScript(script);
        }
    }

    private void createTemporaryTables() throws Exception {

        List<String> tempTableScripts = new ArrayList<>();

        tempTableScripts.add("create table enterprise_admin.incidence_prevalence_raw_data (\n" +
                "\tperson_id bigint(20) not null primary key,\n" +
                "    patient_gender_id smallint(6) null,\n" +
                "    age_years int(11) null,\n" +
                "    postcode_prefix varchar(20) null,\n" +
                "    clinical_effective_date date null,\n" +
                "    clinical_effective_year int(4) null,\n" +
                "    clinical_effective_month int(4) null,\n" +
                "    lsoa_code varchar(50) null,\n" +
                "    msoa_code varchar(50) null,\n" +
                "    ethnic_code char(1) null,\n" +
                "    organisation_id bigint(20) null,\n" +
                "    date_of_death_year int(6) null,\n" +
                "    date_of_death_month int(6) null,\n" +
                "    \n" +
                "    index ix_incidence_prevalence_raw_data_clinical_effective_date (clinical_effective_date),    \n" +
                "    index ix_incidence_prevalence_raw_data_patient_gender_id (patient_gender_id),    \n" +
                "    index ix_incidence_prevalence_raw_data_postcode_prefix (postcode_prefix),\n" +
                "    index ix_incidence_prevalence_raw_data_age_years (age_years)      \n" +
                "    \n" +
                ");");

        tempTableScripts.add("create table enterprise_admin.incidence_prevalence_patient_list (\n" +
                "\tpatient_id bigint(20) not null primary key\n" +
                ");");

        tempTableScripts.add("create table enterprise_admin.incidence_prevalence_organisation_list (\n" +
                "\torganisation_id bigint(20) not null primary key\n" +
                ");");

        tempTableScripts.add("create table enterprise_admin.incidence_prevalence_population_list (\n" +
                "\tepisode_id bigint(20) not null,\n" +
                "\tperson_id bigint(20) not null,\n" +
                "    date_registered date null,\n" +
                "    date_registered_year int(4) null,\n" +
                "    date_registered_month int(4) null,\n" +
                "    date_registered_end date null,\n" +
                "    date_registered_end_year int(4) null,\n" +
                "    date_registered_end_month int(4) null,\n" +
                "    patient_gender_id smallint(6) null,\n" +
                "    age_years int(11) null,\n" +
                "    organisation_id bigint(20) null,\n" +
                "    date_of_death date null,\n" +
                "    date_of_death_year int(4) null,\n" +
                "    date_of_death_month int(4) null,\n" +
                "     \n" +
                "\tprimary key (episode_id, person_id),\n" +
                "    index ix_bigdata_results_date_registered_year (date_registered_year),  \n" +
                "    index ix_bigdata_results_date_registered_month (date_registered_month), \n" +
                "    index ix_bigdata_results_patient_gender_id (patient_gender_id),    \n" +
                "    index ix_bigdata_results_date_registered_end_year (date_registered_end_year),\n" +
                "    index ix_bigdata_results_date_registered_end_month (date_registered_end_month),\n" +
                "    index ix_bigdata_results_date_of_death_year (date_of_death_year) ,\n" +
                "    index ix_bigdata_results_date_of_death_month (date_of_death_month)       \n" +
                "    \n" +
                ");");

        for (String script : tempTableScripts) {
            runScript(script);
        }
    }

    private String createStandardOrganisationInsert(List<JsonOrganisation> organisations) throws Exception {
        String orgList = "";
        for (JsonOrganisation org : organisations) {
            orgList += "(" + org.getId() + "),";
        }
        orgList =  orgList.substring(0, orgList.length() - 1);

        String query = String.format("insert into enterprise_admin.incidence_prevalence_organisation_list (organisation_id)" +
                        " values %s",
                orgList);


        return query;
    }

    private void populateOrganisationTable(JsonPrevInc options) throws Exception {

        List<String> orgScripts = new ArrayList<>();

        if (options.getOrganisation() != null && options.getOrganisation().size() > 0) {
            includeOrganisationQuery = true;
            orgScripts.add(createStandardOrganisationInsert(options.getOrganisation()));
        }

        for (String script : orgScripts) {
            runScript(script);
        }

    }

    private void generateWhereClausesFromOptions(JsonPrevInc options) throws Exception {

        List<String> whereClauseList = new ArrayList<>();


        if (options.getLsoaCode() != null && options.getLsoaCode().size() > 0) {
            String lsoaCodes = "";
            for (JsonLsoa lsoa : options.getLsoaCode()) {
                lsoaCodes += "'" + lsoa.getLsoaCode() + "',";
            }
            lsoaCodes =  lsoaCodes.substring(0, lsoaCodes.length() - 1);
            whereClauseList.add(" p.lsoa_code in (" + lsoaCodes + ")");
        }

        if (options.getMsoaCode() != null && options.getMsoaCode().size() > 0) {
            String msoaCodes = "";
            for (JsonMsoa msoa : options.getMsoaCode()) {
                msoaCodes += "'" + msoa.getMsoaCode() + "',";
            }
            msoaCodes =  msoaCodes.substring(0, msoaCodes.length() - 1);
            whereClauseList.add(" p.msoa_code in (" + msoaCodes + ")");
        }

        if (options.getEthnicity() != null && options.getEthnicity().size() > 0) {
            String ethnicityCodes = "";
            for (String ethnicity : options.getEthnicity()) {
                ethnicityCodes += "'" + ethnicity + "',";
            }
            ethnicityCodes =  ethnicityCodes.substring(0, ethnicityCodes.length() - 1);
            whereClauseList.add(" p.ethnic_code in (" + ethnicityCodes + ")");
        }

        if (options.getPostCodePrefix() != null && !options.getPostCodePrefix().equals("")) {

            whereClauseList.add(" p.postcode_prefix = '" + options.getPostCodePrefix() + "'");
        }

        if (options.getAgeFrom() != null && !options.getAgeFrom().equals("")) {
            whereClauseList.add(" p.age_years >= " + options.getAgeFrom());
        }

        if (options.getAgeTo() != null && !options.getAgeTo().equals("")) {
            whereClauseList.add(" p.age_years <= " + options.getAgeTo());
        }

        if (options.getSex() != null && !options.getSex().equals("-1")) {
            whereClauseList.add(" p.patient_gender_id = " + options.getSex());
        }

        String allWhereClauses = "";
        String prefix = " where ";
        for (String where : whereClauseList) {
            allWhereClauses += prefix + where;
            prefix = " and ";
        }

        whereClauses = allWhereClauses;
    }

    private void populatePatientTable() throws Exception {

        List<String> populateScripts = new ArrayList<>();

        populateScripts.add(String.format("insert into enterprise_admin.incidence_prevalence_patient_list (patient_id)\n" +
                "select \n" +
                "\tid \n" +
                "from enterprise_data_pseudonymised.patient p" +
                "%s" +
                "%s", includeOrganisationQuery ? orgJoin : "", whereClauses));

        for (String script : populateScripts) {
            System.out.println(script);
            runScript(script);
        }

    }

    private void populateClinicalData(String codeSetId) throws Exception {

        List<String> clinicalScripts = new ArrayList<>();

        clinicalScripts.add(String.format("insert into enterprise_admin.incidence_prevalence_raw_data (person_id, clinical_effective_date)\n" +
                "SELECT\n" +
                "\tDISTINCT\n" +
                "    d.person_id, \n" +
                "    min(IFNULL(d.clinical_effective_date, '1000-01-01')) as clinical_effective_date\n" +
                "FROM enterprise_admin.incidence_prevalence_patient_list p \n" +
                "JOIN enterprise_data_pseudonymised.observation d \n" +
                "\tON p.patient_id = d.patient_id\n" +
                "JOIN enterprise_admin.CodeSet c\n" +
                "    ON c.SnomedConceptId = d.snomed_concept_id\n" +
                "WHERE \n" +
                "    c.ItemUuid = '%s'\n" +
                "group by d.person_id;", codeSetId));

        for (String script : clinicalScripts) {
            runScript(script);
        }
    }

    private void updatePersonData() throws Exception {

        List<String> personScripts = new ArrayList<>();

        personScripts.add("update enterprise_admin.incidence_prevalence_raw_data r\n" +
                "inner join enterprise_data_pseudonymised.patient p on p.person_id = r.person_id\n" +
                "set r.patient_gender_id = p.patient_gender_id,\n" +
                "\tr.age_years = p.age_years,\n" +
                "    r.postcode_prefix = p.postcode_prefix,\n" +
                "    r.clinical_effective_year = YEAR(r.clinical_effective_date),\n" +
                "    r.clinical_effective_month = MONTH(r.clinical_effective_date),\n" +
                "    r.lsoa_code = p.lsoa_code,\n" +
                "    r.msoa_code = p.msoa_code,\n" +
                "    r.ethnic_code =  p.ethnic_code,\n" +
                "    r.organisation_id = p.organization_id,\n" +
                "    r.date_of_death_year = year(ifnull(p.date_of_death, '9999-01-01')),\n" +
                "    r.date_of_death_month = month(ifnull(p.date_of_death, '9999-01-01'));");

        for (String script : personScripts) {
            runScript(script);
        }

    }

    private void runIncidenceQueries() throws Exception {

        List<String> incidenceScripts = new ArrayList<>();

        String incidenceQuery = "update enterprise_admin.incidence_prevalence_result res, \n" +
                "(select \n" +
                "\tr.min_date,\n" +
                "    r.max_date,\n" +
                "    count(DISTINCT p.person_id) total\n" +
                "from enterprise_admin.incidence_prevalence_result r\n" +
                "inner join enterprise_admin.incidence_prevalence_data d \n" +
                "\ton d.clinical_effective_date >= r.min_date and d.clinical_effective_date <= r.max_date \n" +
                "inner join enterprise_data_pseudonymised.patient p \n" +
                "\ton p.person_id = d.person_id\n" +
                "    %s\n" +
                "where r.query_id = '70134d14-8402-11e7-a9c9-0a0027000012'\n" +
                "group by r.min_date, r.max_date) gru\n" +
                "set res.%s = gru.total\n" +
                "where res.min_date = gru.min_date\n" +
                "and res.query_id = '70134d14-8402-11e7-a9c9-0a0027000012';";

        // Male
        incidenceScripts.add(String.format(incidenceQuery, " and p.patient_gender_id = 0", "incidence_male"));
        // Female
        incidenceScripts.add(String.format(incidenceQuery, " and p.patient_gender_id = 1", "incidence_female"));
        // Other
        incidenceScripts.add(String.format(incidenceQuery, " and p.patient_gender_id not in (0, 1)", "incidence_other"));

        for (String script : incidenceScripts) {
            runScript(script);
        }
    }

    private void runPrevalenceQueries() throws Exception {

        List<String> prevalenceScripts = new ArrayList<>();

        String incidenceQuery = "update enterprise_admin.incidence_prevalence_result res, \n" +
                "(select \n" +
                "\tr.min_date,\n" +
                "    r.max_date,\n" +
                "    COUNT(DISTINCT p.person_id) total \n" +
                "from enterprise_admin.incidence_prevalence_result r\n" +
                "inner join enterprise_admin.incidence_prevalence_data d \n" +
                "\ton d.clinical_effective_date <= r.max_date\n" +
                "inner join enterprise_data_pseudonymised.patient p \n" +
                "\ton p.person_id = d.person_id\n" +
                "\t%s\n" +
                "    AND (p.date_of_death IS NULL OR p.date_of_death > r.max_date)\n" +
                "    where r.query_id = '70134d14-8402-11e7-a9c9-0a0027000012'\n" +
                "group by r.min_date, r.max_date) gru\n" +
                "set res.%s = gru.total\n" +
                "where res.min_date = gru.min_date\n" +
                "and res.query_id = '70134d14-8402-11e7-a9c9-0a0027000012';";

        // Male
        prevalenceScripts.add(String.format(incidenceQuery, " and p.patient_gender_id = 0", "prevalence_male"));
        // Female
        prevalenceScripts.add(String.format(incidenceQuery, " and p.patient_gender_id = 1", "prevalence_female"));
        // Other
        prevalenceScripts.add(String.format(incidenceQuery, " and p.patient_gender_id not in (0, 1)", "prevalence_other"));

        for (String script : prevalenceScripts) {
            runScript(script);
        }
    }

    private void runInitialPopulationQuery() throws Exception {

        List<String> populationScripts = new ArrayList<>();

        populationScripts.add(String.format("insert into enterprise_admin.incidence_prevalence_population_list\n" +
                "select \n" +
                "\te.id,\n" +
                "\te.person_id,\n" +
                "    e.date_registered,\n" +
                "    YEAR(e.date_registered),\n" +
                "    MONTH(e.date_registered),\n" +
                "    IFNULL(e.date_registered_end, '9999-12-31'),\n" +
                "    YEAR(IFNULL(e.date_registered_end, '9999-12-31')),\n" +
                "    MONTH(IFNULL(e.date_registered_end, '9999-12-31')),\n" +
                "    p.patient_gender_id,\n" +
                "    p.age_years,\n" +
                "    p.organization_id,\n" +
                "    IFNULL(p.date_of_death, '9999-12-31'),\n" +
                "    YEAR(IFNULL(p.date_of_death, '9999-12-31')),\n" +
                "    MONTH(IFNULL(p.date_of_death, '9999-12-31'))    \n" +
                "from enterprise_data_pseudonymised.episode_of_care e\n" +
                "join enterprise_data_pseudonymised.patient p \n" +
                "\ton p.id = e.patient_id and e.organization_id = p.organization_id and e.person_id = p.person_id" +
                "%s" +
                "%s", includeOrganisationQuery ? orgJoin : "", whereClauses));

        for (String script : populationScripts) {
            runScript(script);
            System.out.println(script);
        }
    }

    private void runScript(String script) throws Exception {

        EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();

        entityManager.getTransaction().begin();

        Query q = entityManager.createNativeQuery(script);

        int resultCount = q.executeUpdate();

        System.out.println(resultCount + " rows affected");
        entityManager.getTransaction().commit();

        entityManager.close();

    }

    public List getIncPrevResults(JsonPrevIncGraph params) {
        EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();

        System.out.printf("Breakdown : %s\n", params.breakdown);
        System.out.printf("Gender : %s\n", params.gender);
        System.out.printf("Ethnicity : %s\n", params.ethnicity);
        System.out.printf("Postcode : %s\n", params.postcode);
        System.out.printf("LSOA : %s\n", params.lsoa);
        System.out.printf("MSOA : %s\n", params.msoa);
        System.out.printf("Age x10 : %s\n", params.agex10);

        String select = "clinical_effective_year, count(*)";
        String from = "enterprise_admin.incidence_prevalence_raw_data";
        String where = "";
        String group = "clinical_effective_year";
        String order = "clinical_effective_year";

        if (params.breakdown != null && !params.breakdown.isEmpty()) {
            select += ", " + params.breakdown;
            group += ", " + params.breakdown;
            order = params.breakdown + ", " + order;
        }

        Query q = entityManager.createNativeQuery(
            "SELECT " + select +
                " FROM " + from +
                " GROUP BY " + group +
                " ORDER BY " + order);

        List resultList = q.getResultList();

        System.out.println(resultList.size() + " rows affected");

        entityManager.close();

        return resultList;
    }

    public List getDistinctValuesForGraphing(String columnName) throws Exception {
        EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();

        Query q = entityManager.createNativeQuery("SELECT DISTINCT " +
                columnName +
                " FROM enterprise_admin.incidence_prevalence_raw_data ORDER BY " +
                columnName +
                " ASC");

        List resultList = q.getResultList();

        entityManager.close();

        return resultList;
    }
}
