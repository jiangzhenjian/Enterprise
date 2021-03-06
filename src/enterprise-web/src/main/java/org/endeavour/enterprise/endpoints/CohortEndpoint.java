package org.endeavour.enterprise.endpoints;

import org.endeavourhealth.common.security.SecurityUtils;
import org.endeavourhealth.enterprise.core.database.CohortManager;
import org.endeavourhealth.enterprise.core.database.models.ItemEntity;
import org.endeavourhealth.enterprise.core.database.models.data.*;
import org.endeavourhealth.enterprise.core.json.*;
import org.endeavourhealth.enterprise.core.querydocument.QueryDocumentSerializer;
import org.endeavourhealth.enterprise.core.querydocument.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.*;

@Path("/cohort")
public final class CohortEndpoint extends AbstractItemEndpoint {

	private static final Logger LOG = LoggerFactory.getLogger(CohortEndpoint.class);

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getOrganisations")
	public Response getOrganisations(@Context SecurityContext sc) throws Exception {
		super.setLogbackMarkers(sc);

		List<Object[]> organisations = OrganizationEntity.getOrganisations();

		Set<String> orgList = SecurityUtils.getUserAllowedOrganisationIdsFromSecurityContext(sc);

		List<JsonOrganisation> results = new ArrayList<>();

		for (Object[] orgEntity : organisations) {
			String id = orgEntity[0].toString();
			String name = orgEntity[1].toString();
			String odsCode = orgEntity[2]==null?"":orgEntity[2].toString();

			JsonOrganisation org = new JsonOrganisation();
			org.setId(id);
			org.setName(name);
			org.setOdsCode(odsCode);

			results.add(org);
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getMsoaCodes")
	public Response getMsoaCodes(@Context SecurityContext sc) throws Exception {
		super.setLogbackMarkers(sc);

		List<Object[]> msoaCodes = MsoaLookupEntity.getMsoaCodes();

		List<JsonMsoa> results = new ArrayList<>();

		for (Object[] msoaEntity : msoaCodes) {
			String code = msoaEntity[0].toString();
			if (msoaEntity[1] == null)
				continue;
			String name = msoaEntity[1].toString();

			JsonMsoa msoa = new JsonMsoa();
			msoa.setMsoaCode(code);
			msoa.setMsoaName(name);

			results.add(msoa);
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getLsoaCodes")
	public Response getLsoaCodes(@Context SecurityContext sc) throws Exception {
		super.setLogbackMarkers(sc);

		List<Object[]> lsoaCodes = LsoaLookupEntity.getLsoaCodes();

		List<JsonLsoa> results = new ArrayList<>();

		for (Object[] lsoaEntity : lsoaCodes) {
			String code = lsoaEntity[0].toString();
			String name = lsoaEntity[1].toString();

			JsonLsoa lsoa = new JsonLsoa();
			lsoa.setLsoaCode(code);
			lsoa.setLsoaName(name);

			results.add(lsoa);
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getRegions")
	public Response getRegions(@Context SecurityContext sc) throws Exception {
		super.setLogbackMarkers(sc);

		List<Object[]> regions = RegionEntity.getRegions();

		List<JsonRegion> results = new ArrayList<>();

		for (Object[] regionEntity : regions) {
			String uuid = regionEntity[0].toString();
			String name = regionEntity[1].toString();

			JsonRegion region = new JsonRegion();
			region.setUuid(uuid);
			region.setName(name);

			results.add(region);
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getOrgsForRegion")
	public Response getOrgsForRegion(@Context SecurityContext sc, @QueryParam("uuid") String uuid) throws Exception {
		super.setLogbackMarkers(sc);

		List<Object[]> orgsForRegion = RegionEntity.getOrgsForRegion(uuid);

		List<JsonOrganisation> results = new ArrayList<>();

		for (Object[] regionEntity : orgsForRegion) {
			String id = regionEntity[0].toString();
			String name = regionEntity[1].toString();
			String odsCode = regionEntity[2].toString();

			JsonOrganisation orgForRegion = new JsonOrganisation();
			orgForRegion.setId(id);
			orgForRegion.setName(name);
			orgForRegion.setOdsCode(odsCode);

			results.add(orgForRegion);
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getOrgsForParentOdsCode")
	public Response getOrgsForParentOdsCode(@Context SecurityContext sc, @QueryParam("odsCode") String odsCode) throws Exception {
		super.setLogbackMarkers(sc);

		List<Object[]> ParentOdsCode = RegionEntity.getOrgsForParentOdsCode(odsCode);

		List<JsonOrganisation> results = new ArrayList<>();

		for (Object[] regionEntity : ParentOdsCode) {
			String id = regionEntity[0].toString();
			String name = regionEntity[1].toString();
			String odsCode1 = regionEntity[2].toString();

			JsonOrganisation orgForRegion = new JsonOrganisation();
			orgForRegion.setId(id);
			orgForRegion.setName(name);
			orgForRegion.setOdsCode(odsCode1);

			results.add(orgForRegion);
		}

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getResults")
	public Response getResults(@Context SecurityContext sc, @QueryParam("queryItemUuid") String queryItemUuid, @QueryParam("runDate") String runDate) throws Exception {
		super.setLogbackMarkers(sc);

		List<CohortResultEntity[]> results = CohortResultEntity.getCohortResults(queryItemUuid, runDate);

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
	public Response getAllResults(@Context SecurityContext sc, @QueryParam("queryItemUuid") String queryItemUuid, @QueryParam("runDate") String runDate) throws Exception {
		super.setLogbackMarkers(sc);

		List<CohortResultEntity[]> results = CohortResultEntity.getAllCohortResults(queryItemUuid);

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getPatients")
	public Response getPatients(@Context SecurityContext sc, @QueryParam("queryItemUuid") String queryItemUuid, @QueryParam("runDate") String runDate, @QueryParam("organisationId") Long organisationId) throws Exception {

		List<PatientEntity> results = null;

		LOG.trace("Called cohort/getPatients", "");

		try {
			super.setLogbackMarkers(sc);

			results = CohortPatientsEntity.getCohortPatients(queryItemUuid, runDate, organisationId);

			clearLogbackMarkers();

		} catch (Exception e) {
			LOG.trace("cohort/getPatients", e.getMessage());
		}

		return Response
				.ok()
				.entity(results)
				.build();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/run")
	public Response run(@Context SecurityContext sc, JsonCohortRun cohortRun) throws Exception {
		super.setLogbackMarkers(sc);

		String userUuid = SecurityUtils.getCurrentUserId(sc).toString();

		ItemEntity item = ItemEntity.retrieveLatestForUUid(cohortRun.getQueryItemUuid());
		String xml = item.getXmlContent();
		LibraryItem libraryItem = QueryDocumentSerializer.readLibraryItemFromXml(xml);

		CohortManager.runCohort(libraryItem, cohortRun, userUuid);

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(null)
				.build();
	}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("organisations")
    public Response getReportOrganisations(@Context SecurityContext sc, @QueryParam("itemId") String libraryItemUuid) throws Exception {
        super.setLogbackMarkers(sc);

        List<OrganizationEntity> orgs = CohortResultEntity.getCohortReportOrganisations(libraryItemUuid);

        clearLogbackMarkers();

        return Response
            .ok()
            .entity(orgs)
            .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("graphData")
    public Response getGraphData(@Context SecurityContext sc, @QueryParam("itemId") String libraryItemUuid, @QueryParam("orgId") List<Long> orgIds) throws Exception {
        super.setLogbackMarkers(sc);

        List results = CohortResultEntity.getReportData(libraryItemUuid, orgIds);

        clearLogbackMarkers();

        return Response
            .ok()
            .entity(results)
            .build();
	}

	@GET
	@Produces(MediaType.TEXT_PLAIN )
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/getFrailty")
	public Response getFrailty(@Context SecurityContext sc, @QueryParam("pseudoId") String pseudoId) throws Exception {
		super.setLogbackMarkers(sc);

		LOG.debug("Frailty API called for pseudo ID " + pseudoId);
		String frailtyCategory = CohortResultEntity.getFrailty(pseudoId);
		LOG.debug("Frailty Calculated as " + frailtyCategory);

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(frailtyCategory)
				.build();
	}
}
