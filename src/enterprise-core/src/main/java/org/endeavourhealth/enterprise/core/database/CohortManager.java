package org.endeavourhealth.enterprise.core.database;

import org.endeavourhealth.enterprise.core.database.models.QueryMeta;
import org.endeavourhealth.enterprise.core.database.models.data.*;
import org.endeavourhealth.enterprise.core.json.JsonOrganisation;
import org.endeavourhealth.enterprise.core.json.JsonCohortRun;
import org.endeavourhealth.enterprise.core.querydocument.models.*;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

public class CohortManager {

	public static void saveCohortPatients(CohortPatientsEntity result) throws Exception {
		EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();

		entityManager.getTransaction().begin();

		entityManager.merge(result);

		entityManager.getTransaction().commit();

		entityManager.close();
	}

	public static void saveCohortResult(CohortResultEntity result) throws Exception {
		EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();

		entityManager.getTransaction().begin();

		entityManager.merge(result);

		entityManager.getTransaction().commit();

		entityManager.close();
	}

	public static Timestamp convertToDate(String date) {
		Timestamp timeStamp = null;

		try {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			Date parsedDate = dateFormat.parse(date);
			timeStamp = new java.sql.Timestamp(parsedDate.getTime());
		} catch (Exception e) {

		}


		return timeStamp;

	}

	public static void runCohort(LibraryItem libraryItem, JsonCohortRun cohortRun, String userUuid) throws Exception {
		Timestamp runDate = new Timestamp(System.currentTimeMillis());
		runCohort(libraryItem, cohortRun, userUuid, runDate);
	}

	public static void runCohort(LibraryItem libraryItem, JsonCohortRun cohortRun, String userUuid, Timestamp runDate) throws Exception {

		List<QueryResult> queryResults = new ArrayList<>();

		executeRules(libraryItem, cohortRun, queryResults);

		calculateAndStoreResults(libraryItem, cohortRun, userUuid, runDate, queryResults);
	}

	private static void calculateAndStoreResults(LibraryItem libraryItem, JsonCohortRun cohortRun, String userUuid, Timestamp runDate, List<QueryResult> queryResults) throws Exception {
		// Calculate and store the results for each organisation in the cohort
		List<JsonOrganisation> organisations = cohortRun.getOrganisation();

		for (JsonOrganisation organisationInCohort : organisations) {
			runCohortForOrganisation(libraryItem, cohortRun, userUuid, runDate, queryResults, organisationInCohort);
		} // next organisation in cohort
	}

	private static void runCohortForOrganisation(LibraryItem libraryItem, JsonCohortRun cohortRun, String userUuid, Timestamp runDate, List<QueryResult> queryResults, JsonOrganisation organisationInCohort) throws Exception {
		// calculate cohort denominator count
		String where = "";

		if (cohortRun.getPopulation().equals("0")) // currently registered
			where = "select distinct p " +
					"from PatientEntity p JOIN EpisodeOfCareEntity e on e.patientId = p.id " +
					"where p.dateOfDeath IS NULL and p.organizationId = :organizationId " +
					"and e.registrationTypeId = 2 " +
					"and e.dateRegistered <= :baseline " +
					"and (e.dateRegisteredEnd > :baseline or e.dateRegisteredEnd IS NULL)";
		else if (cohortRun.getPopulation().equals("1")) // all patients
			where = "select distinct p " +
					"from PatientEntity p JOIN EpisodeOfCareEntity e on e.patientId = p.id " +
					"where p.organizationId = :organizationId " +
					"and e.dateRegistered <= :baseline " +
					"and (e.dateRegisteredEnd > :baseline or e.dateRegisteredEnd IS NULL)";

		EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();
		Timestamp baselineDate = convertToDate(cohortRun.getBaselineDate());

		List<PatientEntity> patients = entityManager.
				createQuery(where, PatientEntity.class)
				.setParameter("organizationId", Long.parseLong(organisationInCohort.getId()))
				.setParameter("baseline", baselineDate)
				.getResultList();

		// For each organisation - get the denominator list of patients (needed for FAILED rule conditions)
		List<Long> denominatorPatients = new ArrayList<>();
		for (PatientEntity patientEntity : patients) {
			denominatorPatients.add(patientEntity.getId());
		}

		entityManager.close();

		// for each organisation, calculate the final list of patients from the query rules
		Integer ruleId = libraryItem.getQuery().getStartingRules().getRuleId().get(0);

		Integer i = 0;

		List<Long> finalPatients = loopThroughRules(queryResults, organisationInCohort, denominatorPatients, ruleId, i);

		saveIdentifiedPatientsIntoCohortPatientTable(cohortRun, runDate, organisationInCohort, finalPatients);
		saveQueryCountsToCohortSummaryTable(cohortRun, userUuid, runDate, organisationInCohort, baselineDate, denominatorPatients, finalPatients);
	}

	private static void saveQueryCountsToCohortSummaryTable(JsonCohortRun cohortRun, String userUuid, Timestamp runDate, JsonOrganisation organisationInCohort, Timestamp baselineDate, List<Long> denominatorPatients, List<Long> finalPatients) throws Exception {
		// save the query counts to the cohort summary table
		CohortResultEntity cohortResult = new CohortResultEntity();
		cohortResult.setEndUserUuid(userUuid);
		cohortResult.setBaselineDate(baselineDate);
		cohortResult.setRunDate(runDate);
		cohortResult.setOrganisationId(Long.parseLong(organisationInCohort.getId()));
		cohortResult.setQueryItemUuid(cohortRun.getQueryItemUuid());
		cohortResult.setPopulationTypeId(Byte.parseByte(cohortRun.getPopulation()));
		cohortResult.setEnumeratorCount(finalPatients.size());
		cohortResult.setDenominatorCount(denominatorPatients.size());

		// save the counts to the query cohort summary table
		saveCohortResult(cohortResult);
	}

	private static void saveIdentifiedPatientsIntoCohortPatientTable(JsonCohortRun cohortRun, Timestamp runDate, JsonOrganisation organisationInCohort, List<Long> finalPatients) throws Exception {
		// save each patient identified into the query cohort patient table
		for (Long patient : finalPatients) {
			CohortPatientsEntity cohortPatientsEntity = new CohortPatientsEntity();
			cohortPatientsEntity.setRunDate(runDate);
			cohortPatientsEntity.setQueryItemUuid(cohortRun.getQueryItemUuid());
			cohortPatientsEntity.setOrganisationId(Long.parseLong(organisationInCohort.getId()));
			Long patientId = patient.longValue();
			cohortPatientsEntity.setPatientId(patientId);

			saveCohortPatients(cohortPatientsEntity);
		}
	}

	private static List<Long> loopThroughRules(List<QueryResult> queryResults, JsonOrganisation organisationInCohort, List<Long> denominatorPatients, Integer ruleId, Integer i) throws Exception {
		List<Long> finalPatients = new ArrayList<>();

		while (true) { // loop through all the rules
			i++;

			RuleAction rulePassAction = getRuleAction(true, ruleId, queryResults, Long.parseLong(organisationInCohort.getId()));
			RuleAction ruleFailAction = getRuleAction(false, ruleId, queryResults, Long.parseLong(organisationInCohort.getId()));

			List<Long> patients1 = getRulePatients(ruleId, queryResults, Long.parseLong(organisationInCohort.getId()));  // get patients in rule

			if (i == 1 && ruleFailIncludeGoto(ruleFailAction)) {

				List<Long> denominatorPatients1 = new ArrayList<Long>(denominatorPatients);
				denominatorPatients1.removeAll(patients1); // fail action so calculate patients from denominator who have not met the rule's conditions
				patients1 = new ArrayList<Long>(denominatorPatients1);

			} else if (i > 1 && (rulePassIncludeGoto(rulePassAction) || ruleFailIncludeGoto(ruleFailAction))) {

				if (ruleFailIncludeGoto(ruleFailAction))
					finalPatients.removeAll(patients1); // fail action so remove patients not matching criteria
				else
					finalPatients.retainAll(patients1); // narrow down to patients common in both lists

				if (finalPatients.isEmpty())
					break;
			}

			if (rulePassFailGoto(rulePassAction, ruleFailAction)) { // Rule passes and moves to next rule

				ruleId = getNextRuleIds(rulePassAction, ruleFailAction).get(0);

				List<Long> patients2 = getRulePatients(ruleId, queryResults, Long.parseLong(organisationInCohort.getId()));  // get patients in rule

				rulePassAction = getRuleAction(true, ruleId, queryResults, Long.parseLong(organisationInCohort.getId()));
				ruleFailAction = getRuleAction(false, ruleId, queryResults, Long.parseLong(organisationInCohort.getId()));

				if (finalPatients.isEmpty())
					finalPatients = new ArrayList<Long>(patients1); // first rule

				if (ruleFailIncludeGoto(ruleFailAction))
					finalPatients.removeAll(patients2); // fail action so remove patients not matching criteria
				else
					finalPatients.retainAll(patients2); // narrow down to patients common in both lists

				if (finalPatients.isEmpty())
					break;

				if (rulePassFailGoto(rulePassAction, ruleFailAction))  // Rule passes and moves to next rule
					ruleId = getNextRuleIds(rulePassAction, ruleFailAction).get(0);

				else if (rulePassFailInclude(rulePassAction, ruleFailAction)) // Rule includes patients, so break out the loop
					break;

			} else if (rulePassFailInclude(rulePassAction, ruleFailAction)) { // Rule includes patients, so break out the loop

				if (finalPatients.isEmpty()) // Only one rule in Query
					finalPatients = new ArrayList<Long>(patients1);

				break;
			}

		}

		return finalPatients;
	}

	private static void executeRules(LibraryItem libraryItem, JsonCohortRun cohortRun, List<QueryResult> queryResults) {
		for (Rule rule : libraryItem.getQuery().getRule()) { // execute each rule
			List<Filter> filters = rule.getTest().getFilter();

			QueryMeta q = new QueryMeta();

			buildFilters(cohortRun, filters, q);

			// Run the rule SQL for each organisation in the report

			runRuleForOrganisations(cohortRun, queryResults, rule, q);
		} // next Rule in Query
	}

	private static void runRuleForOrganisations(JsonCohortRun cohortRun, List<QueryResult> queryResults, Rule rule, QueryMeta q) {
		List<JsonOrganisation> organisations = cohortRun.getOrganisation();

		for (JsonOrganisation organisationInCohort : organisations) {
			Timestamp baselineDate = convertToDate(cohortRun.getBaselineDate());
			String patientWhere = "";

			if (cohortRun.getPopulation().equals("0")) { // currently registered
				patientWhere = "select distinct p " +
						"from PatientEntity p JOIN EpisodeOfCareEntity e on e.patientId = p.id " +
						"JOIN " + q.dataTable + " d on d." + q.patientJoinField + " = p.id " +
						"where p.dateOfDeath IS NULL and p.organizationId = :organizationId " +
						"and e.registrationTypeId = 2 " +
						"and e.dateRegistered <= :baseline " +
						"and (e.dateRegisteredEnd > :baseline or e.dateRegisteredEnd IS NULL) " + q.sqlWhere;
			} else if (cohortRun.getPopulation().equals("1")) { // all patients
				patientWhere = "select distinct p " +
						"from PatientEntity p JOIN EpisodeOfCareEntity e on e.patientId = p.id " +
						"JOIN " + q.dataTable + " d on d." + q.patientJoinField + " = p.id " +
						"where p.organizationId = :organizationId " +
						"and e.dateRegistered <= :baseline " +
						"and (e.dateRegisteredEnd > :baseline or e.dateRegisteredEnd IS NULL) " + q.sqlWhere;
			}

			EntityManager entityManager = PersistenceManager.INSTANCE.getEmEnterpriseData();

			List<PatientEntity> patients = entityManager.
					createQuery(patientWhere, PatientEntity.class)
					.setParameter("organizationId", Long.parseLong(organisationInCohort.getId()))
					.setParameter("baseline", baselineDate)
					.getResultList();

			// For each organisation - add the rule's identified list of patients to the overall Query Result list
			QueryResult queryResult = new QueryResult();
			queryResult.setOrganisationId(Long.parseLong(organisationInCohort.getId()));
			queryResult.setRuleId(rule.getId());
			queryResult.setOnPass(rule.getOnPass());
			queryResult.setOnFail(rule.getOnFail());
			List<Long> queryPatients = new ArrayList<>();
			for (PatientEntity patientEntity : patients) {
				queryPatients.add(patientEntity.getId());
			}
			queryResult.setPatients(queryPatients);
			queryResults.add(queryResult);

			entityManager.close();
		} // next organisation in cohort
	}

	private static void buildFilters(JsonCohortRun cohortRun, List<Filter> filters, QueryMeta q) {
		for (Filter filter : filters) { // build the SQL for each filter
			String field = filter.getField();

			if (field.equals("CONCEPT")) {
				buildConceptFilter(q, filter);
			} else if (field.equals("EFFECTIVE_DATE")) {
				buildEffectiveDateFilter(cohortRun, q, filter);
			} else if (field.equals("OBSERVATION_PROBLEM")) {
				q.sqlWhere += " and d.isProblem = '1'";
			} else if (field.equals("MEDICATION_STATUS")) {
				q.sqlWhere += " and d.isActive = '1'";
			} else if (field.equals("MEDICATION_TYPE")) {
				buildMedicationTypeFilter(q, filter);
			}

		} // next Filter
	}

	private static void buildMedicationTypeFilter(QueryMeta q, Filter filter) {
		for (String value : filter.getValueSet().getValue()) {
			if (value.equals("ACUTE"))
				q.sqlWhere += " or d.medicationStatementAuthorisationTypeId = '0'";
			else if (value.equals("REPEAT"))
				q.sqlWhere += " or d.medicationStatementAuthorisationTypeId = '1'";
			else if (value.equals("REPEAT_DISPENSING"))
				q.sqlWhere += " or d.medicationStatementAuthorisationTypeId = '2'";
			else if (value.equals("AUTOMATIC"))
				q.sqlWhere += " or d.medicationStatementAuthorisationTypeId = '3'";
		}
		q.sqlWhere = q.sqlWhere.replaceFirst("or d.medicationStatementAuthorisationTypeId", "and (d.medicationStatementAuthorisationTypeId");
		q.sqlWhere += ")";
	}

	private static void buildEffectiveDateFilter(JsonCohortRun cohortRun, QueryMeta q, Filter filter) {
		if (filter.getValueFrom() != null) {
			String dateFrom = filter.getValueFrom().getConstant();
			if (filter.getValueFrom().getRelativeUnit() != null) {
				dateFrom = "-" + dateFrom;
				String relativeUnit = filter.getValueFrom().getRelativeUnit().value();
				Timestamp baselineDate = convertToDate(cohortRun.getBaselineDate());
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				Calendar calDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
				dateFormat.setCalendar(calDate);
				calDate.setTime(baselineDate);

				adjustCalendar(dateFrom, relativeUnit, calDate);
				dateFrom = dateFormat.format(calDate.getTime());
			}
			q.sqlWhere += " and d.clinicalEffectiveDate >= '" + dateFrom + "'";
		} else if (filter.getValueTo() != null) {
			String dateTo = filter.getValueTo().getConstant();
			if (filter.getValueTo().getRelativeUnit() != null) {
				dateTo = "-" + dateTo;
				String relativeUnit = filter.getValueTo().getRelativeUnit().value();
				Timestamp baselineDate = convertToDate(cohortRun.getBaselineDate());
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				Calendar calDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
				dateFormat.setCalendar(calDate);
				calDate.setTime(baselineDate);

				adjustCalendar(dateTo, relativeUnit, calDate);
				dateTo = dateFormat.format(calDate.getTime());
			}
			q.sqlWhere += " and d.clinicalEffectiveDate <= '" + dateTo + "'";
		}
	}

	private static void adjustCalendar(String dateFrom, String relativeUnit, Calendar calDate) {
		switch (relativeUnit) {
			case "day":
				calDate.add(Calendar.DATE, Integer.parseInt(dateFrom));
				break;
			case "week":
				calDate.add(Calendar.DATE, Integer.parseInt(dateFrom) * 7);
				break;
			case "month":
				calDate.add(Calendar.MONTH, Integer.parseInt(dateFrom));
				break;
			case "year":
				calDate.add(Calendar.YEAR, Integer.parseInt(dateFrom));
				break;
		}
	}

	private static void buildConceptFilter(QueryMeta q, Filter filter) {
		List<CodeSetValue> codeSetValues = filter.getCodeSet().getCodeSetValue();
		Integer c = 0;
		for (CodeSetValue codeSetValue : codeSetValues) {
			c++;
			String code = codeSetValue.getCode();
			String term = codeSetValue.getTerm();
			String parentType = codeSetValue.getParentType();
			String baseType = codeSetValue.getBaseType();
			String valueFrom = codeSetValue.getValueFrom();
			String valueTo = codeSetValue.getValueTo();
			Boolean includeChildren = codeSetValue.isIncludeChildren();

			buildConceptTypeFilter(q, c, code, term, parentType, baseType, valueFrom, valueTo);
		}

		if (q.dataTable.equals("AllergyIntoleranceEntity") ||
				q.dataTable.equals("ReferralRequestEntity") ||
				q.dataTable.equals("EncounterEntity")) {
			q.sqlWhere = q.sqlWhere.replaceFirst("or d.snomedConceptId", "and (d.snomedConceptId");
			q.sqlWhere += ")";
		} else if (q.dataTable.equals("ObservationEntity")) {
			q.sqlWhere = "and (" + q.sqlWhere + ")";
		} else if (q.dataTable.equals("MedicationStatementEntity") ||
				q.dataTable.equals("MedicationOrderEntity") ||
				q.dataTable.equals("ReferralRequestEntity")) {
			q.sqlWhere = q.sqlWhere.replaceFirst("or d.dmdId", "and (d.dmdId");
			q.sqlWhere += ")";
		}
	}

	private static void buildConceptTypeFilter(QueryMeta q, Integer c, String code, String term, String parentType, String baseType, String valueFrom, String valueTo) {
		switch (baseType) {
			case "Patient":
				buildConceptPatientFilter(q, term, parentType, valueFrom, valueTo);
				break;
			case "Observation":
				buildConceptObservationFilter(q, c, code, valueFrom, valueTo);
				break;
			case "Medication Statement":
				q.patientJoinField = "patientId";
				q.dataTable = "MedicationStatementEntity";
				q.sqlWhere += " or d.dmdId = '" + code + "'";
				break;
			case "Medication Order":
				q.patientJoinField = "patientId";
				q.dataTable = "MedicationOrderEntity";
				q.sqlWhere += " or d.dmdId = '" + code + "'";
				break;
			case "Allergy":
				q.patientJoinField = "patientId";
				q.dataTable = "AllergyIntoleranceEntity";
				q.sqlWhere += " or d.snomedConceptId = '" + code + "'";
				break;
			case "Referral":
				q.patientJoinField = "patientId";
				q.dataTable = "ReferralRequestEntity";
				q.sqlWhere += " or d.snomedConceptId = '" + code + "'";
				break;
			case "Encounter":
				q.patientJoinField = "patientId";
				q.dataTable = "EncounterEntity";
				q.sqlWhere += " or d.snomedConceptId = '" + code + "'";
				break;
		}
	}

	private static void buildConceptObservationFilter(QueryMeta q, Integer c, String code, String valueFrom, String valueTo) {
		q.patientJoinField = "patientId";
		q.dataTable = "ObservationEntity";
		String pref = " or";
		if (c == 1)
			pref = "";

		if (valueFrom.equals("") && valueTo.equals(""))
			q.sqlWhere += pref + " d.snomedConceptId = '" + code + "'";
		if (!valueFrom.equals("") && valueTo.equals(""))
			q.sqlWhere += pref + " (d.snomedConceptId = '" + code + "' and d.value >= '" + valueFrom + "')";
		if (valueFrom.equals("") && !valueTo.equals(""))
			q.sqlWhere += pref + " (d.snomedConceptId = '" + code + "' and d.value <= '" + valueTo + "')";
		if (!valueFrom.equals("") && !valueTo.equals(""))
			q.sqlWhere += pref + " (d.snomedConceptId = '" + code + "' and d.value >= '" + valueFrom + "' and d.value <= '" + valueTo + "')";
		return;
	}

	private static void buildConceptPatientFilter(QueryMeta q, String term, String parentType, String valueFrom, String valueTo) {
		q.patientJoinField = "id";
		q.dataTable = "PatientEntity";
		if ((parentType.equals("Sex") && term.equals("Male")) ||
				(term.equals("Sex") && valueFrom.equals("Male"))) {
			q.sqlWhere += " and d.patientGenderId = '0'";
		} else if ((parentType.equals("Sex") && term.equals("Female")) ||
				(term.equals("Sex") && valueFrom.equals("Female"))) {
			q.sqlWhere += " and d.patientGenderId = '1'";
		} else if (term.equals("Post Code Prefix")) {
			q.sqlWhere += " and d.postcodePrefix like '" + valueFrom + "%'";
		} else if (term.equals("Age Years")) {
			if (!valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.ageYears between '" + valueFrom + "' and '" + valueTo + "'";
			else if (!valueFrom.equals("") && valueTo.equals(""))
				q.sqlWhere += " and d.ageYears >= '" + valueFrom + "'";
			else if (valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.ageYears <= '" + valueTo + "'";

		} else if (term.equals("Age Months")) {
			if (!valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.ageMonths between '" + valueFrom + "' and '" + valueTo + "'";
			else if (!valueFrom.equals("") && valueTo.equals(""))
				q.sqlWhere += " and d.ageMonths >= '" + valueFrom + "'";
			else if (valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.ageMonths <= '" + valueTo + "'";
		} else if (term.equals("Age Weeks")) {
			if (!valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.ageWeeks between '" + valueFrom + "' and '" + valueTo + "'";
			else if (!valueFrom.equals("") && valueTo.equals(""))
				q.sqlWhere += " and d.ageWeeks >= '" + valueFrom + "'";
			else if (valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.ageWeeks <= '" + valueTo + "'";
		} else if (term.equals("LSOA Code")) {
			q.sqlWhere += " and d.lsoaCode like '" + valueFrom + "%'";
		} else if (term.equals("MSOA Code")) {
			q.sqlWhere += " and d.msoaCode like '" + valueFrom + "%'";
		} else if (term.equals("Date of Death")) {
			if (!valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.dateOfDeath between '" + valueFrom + "' and '" + valueTo + "'";
			else if (!valueFrom.equals("") && valueTo.equals(""))
				q.sqlWhere += " and d.dateOfDeath >= '" + valueFrom + "'";
			else if (valueFrom.equals("") && !valueTo.equals(""))
				q.sqlWhere += " and d.dateOfDeath <= '" + valueTo + "'";
		}
	}

	public static RuleAction getRuleAction(Boolean pass, Integer ruleId, List<QueryResult> queryResults, Long organisationId) throws Exception {

		RuleAction ruleAction = null;

		for (QueryResult qr : queryResults) {
			if (qr.getOrganisationId() == organisationId && qr.getRuleId() == ruleId) {
				if (pass)
					ruleAction = qr.getOnPass();
				else
					ruleAction = qr.getOnFail();
				break;
			}
		}

		return ruleAction;
	}

	public static List<Long> getRulePatients(Integer ruleId, List<QueryResult> queryResults, Long organisationId) throws Exception {

		List<Long> patients = null;

		for (QueryResult qr : queryResults) {
			if (qr.getOrganisationId() == organisationId && qr.getRuleId() == ruleId) {
				patients = qr.getPatients();  // get patients in rule
				break;
			}
		}

		return patients;
	}

	public static List<Integer> getNextRuleIds(RuleAction rulePassAction, RuleAction ruleFailAction) throws Exception {

		List<Integer> nextRuleIds = null;
		if (rulePassAction.getAction().equals(RuleActionOperator.GOTO_RULES))
			nextRuleIds = rulePassAction.getRuleId();
		else if (ruleFailAction.getAction().equals(RuleActionOperator.GOTO_RULES))
			nextRuleIds = ruleFailAction.getRuleId();

		return nextRuleIds;

	}

	public static Boolean ruleFailIncludeGoto(RuleAction ruleFailAction) throws Exception {

		Boolean result = false;

		if (ruleFailAction.getAction().equals(RuleActionOperator.INCLUDE) ||
				ruleFailAction.getAction().equals(RuleActionOperator.GOTO_RULES))
			result = true;

		return result;
	}

	public static Boolean rulePassIncludeGoto(RuleAction rulePassAction) throws Exception {

		Boolean result = false;

		if (rulePassAction.getAction().equals(RuleActionOperator.INCLUDE) ||
				rulePassAction.getAction().equals(RuleActionOperator.GOTO_RULES))
			result = true;

		return result;
	}

	public static Boolean rulePassFailGoto(RuleAction rulePassAction, RuleAction ruleFailAction) throws Exception {

		Boolean result = false;

		if (rulePassAction.getAction().equals(RuleActionOperator.GOTO_RULES) ||
				ruleFailAction.getAction().equals(RuleActionOperator.GOTO_RULES))
			result = true;

		return result;
	}

	public static Boolean rulePassFailInclude(RuleAction rulePassAction, RuleAction ruleFailAction) throws Exception {

		Boolean result = false;

		if (rulePassAction.getAction().equals(RuleActionOperator.INCLUDE) ||
				ruleFailAction.getAction().equals(RuleActionOperator.INCLUDE))
			result = true;

		return result;
	}

}