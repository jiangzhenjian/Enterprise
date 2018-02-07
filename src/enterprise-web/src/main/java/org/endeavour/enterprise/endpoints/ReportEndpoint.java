package org.endeavour.enterprise.endpoints;

import org.endeavourhealth.common.security.SecurityUtils;
import org.endeavourhealth.core.database.rdbms.ehr.RdbmsResourceDal;
import org.endeavourhealth.core.database.rdbms.subscriberTransform.RdbmsPseudoIdDal;
import org.endeavourhealth.enterprise.core.Resources;
import org.endeavourhealth.enterprise.core.database.ReportManager;
import org.endeavourhealth.enterprise.core.database.models.ItemEntity;
import org.endeavourhealth.enterprise.core.database.models.data.CohortPatientsEntity;
import org.endeavourhealth.enterprise.core.database.models.data.CohortResultEntity;
import org.endeavourhealth.enterprise.core.database.models.data.ReportResultEntity;
import org.endeavourhealth.enterprise.core.json.JsonReportRun;
import org.endeavourhealth.enterprise.core.querydocument.QueryDocumentSerializer;
import org.endeavourhealth.enterprise.core.querydocument.models.LibraryItem;
import org.endeavourhealth.enterprise.core.querydocument.models.Query;
import org.hl7.fhir.instance.model.Resource;
import org.hl7.fhir.instance.model.ResourceType;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/report")
public final class ReportEndpoint extends AbstractItemEndpoint {

	private static final Logger LOG = LoggerFactory.getLogger(ReportEndpoint.class);

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/run")
	public Response run(@Context SecurityContext sc, JsonReportRun report) throws Exception {
		super.setLogbackMarkers(sc);

		String userUuid = SecurityUtils.getCurrentUserId(sc).toString();

		ItemEntity item = ItemEntity.retrieveLatestForUUid(report.getReportItemUuid());
		String xml = item.getXmlContent();
		LibraryItem libraryItem = QueryDocumentSerializer.readLibraryItemFromXml(xml);

		item = ItemEntity.retrieveLatestForUUid(report.getBaselineCohortId());
		xml = item.getXmlContent();
		LibraryItem cohortItem = QueryDocumentSerializer.readLibraryItemFromXml(xml);

		Timestamp runDate = null;
		if (report.getScheduled())
			runDate = new ReportManager().runLater(userUuid, report, libraryItem, cohortItem.getName());
		else {
			runDate = new Timestamp(System.currentTimeMillis());
			new ReportManager().runNow(userUuid, report, libraryItem, runDate, cohortItem.getName());
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(runDate.getTime())
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getResults")
	public Response getResults(@Context SecurityContext sc, @QueryParam("reportItemUuid") String reportItemUuid, @QueryParam("runDate") String runDate) throws Exception {
		super.setLogbackMarkers(sc);

		List<ReportResultEntity[]> results = ReportResultEntity.getReportResults(reportItemUuid, runDate);

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getAllResults")
	public Response getAllResults(@Context SecurityContext sc, @QueryParam("reportItemUuid") String reportItemUuid, @QueryParam("runDate") String runDate) throws Exception {
		super.setLogbackMarkers(sc);

		List<ReportResultEntity[]> results = ReportResultEntity.getAllReportResults(reportItemUuid);

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/reverseLookupPatient")
	public Response reverseLookupPatient(@Context SecurityContext sc, @QueryParam("psuedoIds") List<String> pseudoIds,
										 @QueryParam("serviceUUID") String serviceUUID,
										 @QueryParam("subscriberConfigName") String subscriberConfigName) throws Exception {
		super.setLogbackMarkers(sc);

		String configName = "ceg_enterprise";

		if (subscriberConfigName != null)
			configName = subscriberConfigName;

		RdbmsPseudoIdDal pseudo = new RdbmsPseudoIdDal(configName);

		List<String> patientIdList = pseudo.findPatientIdsFromPseudoIds(pseudoIds);

		List<Resource> resources = new ArrayList<>();

		for (String patient : patientIdList) {
			RdbmsResourceDal resourceDal = new RdbmsResourceDal();
			Resource resource = resourceDal.getCurrentVersionAsResource(UUID.fromString(serviceUUID), ResourceType.Patient, patient);
			resources.add(resource);
		}


		clearLogbackMarkers();

		return Response
				.ok()
				.entity(resources)
				.build();
	}



}
