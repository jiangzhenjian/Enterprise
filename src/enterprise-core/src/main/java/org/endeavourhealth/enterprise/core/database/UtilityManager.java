package org.endeavourhealth.enterprise.core.database;

import org.endeavourhealth.enterprise.core.json.JsonPrevInc;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class UtilityManager {

    public boolean runDiabetesReport(JsonPrevInc options) throws Exception {

        cleanUpDatabase();
        initialiseReportResultTable(options);
        createPopulationTable();
        createRawDataTable(options.getCodeSet());
        createDataTable();
        removeDuplicates();
        runIncidenceQueries();
        runPrevalenceQueries();
        runPopulationQueries();

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

        String insert = "insert into enterprise_admin.incidence_prevalence_result (query_id, min_date, max_date)\n" +
                "values ('70134d14-8402-11e7-a9c9-0a0027000012', '%s', '%s')";

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

            initialiseScripts.add(String.format(insert, dateFormat.format(beginning).toString(),dateFormat.format(end).toString()));

            c.add(substractionPrecision, -1);
        }

        for (String script : initialiseScripts) {
            System.out.println(script);
            runScript(script);
        }

    }

    private void cleanUpDatabase() throws Exception {

        List<String> deleteScripts = new ArrayList<>();

        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_population;");
        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_data_raw;");
        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_data;");
        deleteScripts.add("drop table if exists enterprise_admin.incidence_prevalence_data_duplicates;");

        for (String script : deleteScripts) {
            runScript(script);
        }

    }

    private void createPopulationTable() throws Exception {

        List<String> populationTableScripts = new ArrayList<>();

        populationTableScripts.add("CREATE TABLE enterprise_admin.incidence_prevalence_population\n" +
                "SELECT\n" +
                "\tp.person_id, \n" +
                "    p.patient_gender_id,\n" +
                "    p.date_of_death, \n" +
                "    e.registration_type_id,  \n" +
                "    e.date_registered, \n" +
                "    e.date_registered_end,\n" +
                "    p.organization_id\n" +
                "from enterprise_data_pseudonymised.patient p \n" +
                "JOIN enterprise_data_pseudonymised.episode_of_care e \n" +
                "\tON e.person_id = p.person_id and e.organization_id = p.organization_id;");

        populationTableScripts.add("CREATE INDEX ix_incidence_prevalence_population\n" +
                "ON enterprise_admin.incidence_prevalence_population (person_id);");

        populationTableScripts.add("CREATE INDEX ix2_incidence_prevalence_population\n" +
                "ON enterprise_admin.incidence_prevalence_population (patient_gender_id, date_registered, date_registered_end, date_of_death);");


        for (String script : populationTableScripts) {
            runScript(script);
        }

    }

    private void createRawDataTable(String codeSetUuid) throws Exception {

        List<String> rawTableScripts = new ArrayList<>();

        rawTableScripts.add(String.format("CREATE TABLE enterprise_admin.incidence_prevalence_data_raw\n" +
                "SELECT\n" +
                "\tp.person_id, \n" +
                "    p.patient_gender_id,\n" +
                "    p.date_of_death, \n" +
                "    p.registration_type_id,  \n" +
                "    p.date_registered, \n" +
                "    p.date_registered_end, \n" +
                "    d.snomed_concept_id,\n" +
                "    d.clinical_effective_date,\n" +
                "    d.id\n" +
                "FROM enterprise_admin.incidence_prevalence_population p \n" +
                "JOIN enterprise_data_pseudonymised.observation d \n" +
                "\tON d.person_id = p.person_id and d.organization_id = p.organization_id\n" +
                "JOIN enterprise_admin.CodeSet c \n" +
                "\tON c.SnomedConceptId = d.snomed_concept_id\n" +
                "WHERE \n" +
                "\tc.ItemUuid = '%s'",
        codeSetUuid));



        rawTableScripts.add("CREATE INDEX ix_incidence_prevalence_data_raw\n" +
                "ON enterprise_admin.incidence_prevalence_data_raw (person_id, clinical_effective_date);");

        for (String script : rawTableScripts) {
            runScript(script);
        }
    }

    private void createDataTable() throws Exception {

        List<String> dataTableScripts = new ArrayList<>();

        dataTableScripts.add("CREATE TABLE enterprise_admin.incidence_prevalence_data\n" +
                "SELECT DISTINCT\n" +
                "\tperson_id, \n" +
                "    patient_gender_id,\n" +
                "    date_of_death, \n" +
                "    registration_type_id,  \n" +
                "    date_registered, \n" +
                "    date_registered_end, \n" +
                "    snomed_concept_id,\n" +
                "    clinical_effective_date,\n" +
                "    id\n" +
                "FROM enterprise_admin.incidence_prevalence_data_raw earliest\n" +
                "WHERE\n" +
                "\tNOT EXISTS (\n" +
                "\t\tSELECT 1 \n" +
                "        FROM enterprise_admin.incidence_prevalence_data_raw later\n" +
                "        WHERE \n" +
                "\t\t\tlater.person_id = earliest.person_id\n" +
                "            AND ((later.clinical_effective_date < earliest.clinical_effective_date)\n" +
                "\t\t\t\tOR (later.clinical_effective_date IS NULL AND earliest.clinical_effective_date IS NOT NULL))\n" +
                "    );");

        //No need for the raw table now.
        dataTableScripts.add("DROP TABLE enterprise_admin.incidence_prevalence_data_raw;");

        dataTableScripts.add("CREATE INDEX ix_incidence_prevalence_data\n" +
                "ON enterprise_admin.incidence_prevalence_data (clinical_effective_date, patient_gender_id);");

        for (String script : dataTableScripts) {
            runScript(script);
        }
    }

    private void removeDuplicates() throws Exception {

        List<String> duplicateRemovalScripts = new ArrayList<>();

        duplicateRemovalScripts.add("CREATE TABLE enterprise_admin.incidence_prevalence_data_duplicates \n" +
                "SELECT\n" +
                "\tperson_id, MIN(id) as id_to_keep\n" +
                "FROM enterprise_admin.incidence_prevalence_data\n" +
                "GROUP BY person_id\n" +
                "HAVING COUNT(1) > 1;");


        duplicateRemovalScripts.add("DELETE r FROM enterprise_admin.incidence_prevalence_data r \n" +
                "inner JOIN enterprise_admin.incidence_prevalence_data_duplicates  duplicates\n" +
                "ON \n" +
                "\tduplicates.person_id = r.person_id\n" +
                "    AND duplicates.id_to_keep != r.id;");

        duplicateRemovalScripts.add("DROP TABLE enterprise_admin.incidence_prevalence_data_duplicates;");

        for (String script : duplicateRemovalScripts) {
            runScript(script);
        }
    }

    private void runIncidenceQueries() throws Exception {

        List<String> incidenceScripts = new ArrayList<>();

        String incidenceQuery = "update enterprise_admin.incidence_prevalence_result res, \n" +
                "(select \n" +
                "\tr.min_date,\n" +
                "    r.max_date,\n" +
                "    count(d.clinical_effective_date) total\n" +
                "from enterprise_admin.incidence_prevalence_result r\n" +
                "left outer join enterprise_admin.incidence_prevalence_data d on d.clinical_effective_date >= r.min_date and d.clinical_effective_date <= r.max_date %s\n" +
                "where r.query_id = '70134d14-8402-11e7-a9c9-0a0027000012'\n" +
                "group by r.min_date, r.max_date) gru\n" +
                "set res.%s = gru.total\n" +
                "where res.min_date = gru.min_date\n" +
                "and res.query_id = '70134d14-8402-11e7-a9c9-0a0027000012';";

        // Male
        incidenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id = 0", "incidence_male"));
        // Female
        incidenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id = 1", "incidence_female"));
        // Other
        incidenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id not in (0, 1)", "incidence_other"));
        // total
        incidenceScripts.add(String.format(incidenceQuery, " ", "incidence_total"));

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
                "    COUNT(DISTINCT d.person_id) total -- DL changed to count distinct persons\n" +
                "from enterprise_admin.incidence_prevalence_result r\n" +
                "left outer join enterprise_admin.incidence_prevalence_data d \n" +
                "\ton d.clinical_effective_date <= r.max_date \n" +
                "\t%s\n" +
                "    AND (d.date_of_death IS NULL OR d.date_of_death > r.max_date) -- DL factor in date of death into this query\n" +
                "where r.query_id = '70134d14-8402-11e7-a9c9-0a0027000012'\n" +
                "group by r.min_date, r.max_date) gru\n" +
                "set res.%s = gru.total\n" +
                "where res.min_date = gru.min_date\n" +
                "and res.query_id = '70134d14-8402-11e7-a9c9-0a0027000012';";

        // Male
        prevalenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id = 0", "prevalence_male"));
        // Female
        prevalenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id = 1", "prevalence_female"));
        // Other
        prevalenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id not in (0, 1)", "prevalence_other"));
        // total
        prevalenceScripts.add(String.format(incidenceQuery, " ", "prevalence_total"));

        for (String script : prevalenceScripts) {
            runScript(script);
        }
    }

    private void runPopulationQueries() throws Exception {

        List<String> prevalenceScripts = new ArrayList<>();

        String incidenceQuery = "update enterprise_admin.incidence_prevalence_result res, \n" +
                "(select \n" +
                "\tr.min_date,\n" +
                "    r.max_date,\n" +
                "    COUNT(DISTINCT d.person_id) total -- DL changed to count distinct persons\n" +
                "from enterprise_admin.incidence_prevalence_result r\n" +
                "left outer join enterprise_admin.incidence_prevalence_population d -- DL changed to use population table, not diabetes population\n" +
                "\ton d.date_registered <= r.max_date \n" +
                "    and (d.date_registered_end is null or d.date_registered_end >= r.min_date) \n" +
                "    %s\n" +
                "    AND (d.date_of_death IS NULL OR d.date_of_death > r.max_date) -- DL factor in date of death into this query\n" +
                "where r.query_id = '70134d14-8402-11e7-a9c9-0a0027000012'\n" +
                "group by r.min_date, r.max_date) gru\n" +
                "set res.%s = gru.total\n" +
                "where res.min_date = gru.min_date\n" +
                "and res.query_id = '70134d14-8402-11e7-a9c9-0a0027000012';";

        // Male
        prevalenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id = 0", "population_male"));
        // Female
        prevalenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id = 1", "population_female"));
        // Other
        prevalenceScripts.add(String.format(incidenceQuery, " and d.patient_gender_id not in (0, 1)", "population_other"));
        // total
        prevalenceScripts.add(String.format(incidenceQuery, " ", "population_total"));

        for (String script : prevalenceScripts) {
            runScript(script);
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
}