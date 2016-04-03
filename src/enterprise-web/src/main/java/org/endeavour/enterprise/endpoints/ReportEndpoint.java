package org.endeavour.enterprise.endpoints;

import org.endeavour.enterprise.json.JsonDeleteResponse;
import org.endeavour.enterprise.json.JsonQueryResult;
import org.endeavour.enterprise.json.JsonReportRequest;
import org.endeavour.enterprise.json.JsonReportResult;
import org.endeavourhealth.enterprise.core.DefinitionItemType;
import org.endeavourhealth.enterprise.core.database.administration.DbEndUser;
import org.endeavourhealth.enterprise.core.database.definition.DbActiveItem;
import org.endeavourhealth.enterprise.core.database.definition.DbItem;
import org.endeavourhealth.enterprise.core.database.execution.*;
import org.endeavourhealth.enterprise.core.querydocument.QueryDocumentSerializer;
import org.endeavourhealth.enterprise.core.querydocument.models.QueryDocument;
import org.endeavourhealth.enterprise.core.querydocument.models.Report;
import org.endeavourhealth.enterprise.core.querydocument.models.ReportItem;
import org.endeavourhealth.enterprise.core.requestParameters.RequestParametersSerializer;
import org.endeavourhealth.enterprise.core.requestParameters.models.RequestParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Path("/report")
public final class ReportEndpoint extends AbstractItemEndpoint
{
    private static final Logger LOG = LoggerFactory.getLogger(ReportEndpoint.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/getReport")
    public Response getReport(@Context SecurityContext sc, @QueryParam("uuid") String uuidStr) throws Exception
    {
        UUID reportUuid = UUID.fromString(uuidStr);

        LOG.trace("GettingReport for UUID {}", reportUuid);

        DbItem item = DbItem.retrieveLatestForUUid(reportUuid);
        String xml = item.getXmlContent();

        Report ret = QueryDocumentSerializer.readReportFromXml(xml);

        return Response
                .ok()
                .entity(ret)
                .build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/saveReport")
    public Response saveReport(@Context SecurityContext sc, Report report) throws Exception
    {
        UUID orgUuid = getOrganisationUuidFromToken(sc);
        UUID userUuid = getEndUserUuidFromToken(sc);

        UUID reportUuid = parseUuidFromStr(report.getUuid());
        String name = report.getName();
        String description = report.getDescription();
        UUID folderUuid = parseUuidFromStr(report.getFolderUuid());

        LOG.trace("SavingReport UUID {}, Name {} FolderUuid", reportUuid, name, folderUuid);

        boolean inserting = reportUuid == null;
        if (inserting) {
            reportUuid = UUID.randomUUID();
            report.setUuid(reportUuid.toString());
        }

        QueryDocument doc = new QueryDocument();
        doc.getReport().add(report);

        super.saveItem(inserting, reportUuid, orgUuid, userUuid, DefinitionItemType.Report, name, description, doc, folderUuid);

        //return the UUID of the query
        Report ret = new Report();
        ret.setUuid(reportUuid.toString());

        return Response
                .ok()
                .entity(ret)
                .build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/deleteReport")
    public Response deleteReport(@Context SecurityContext sc, Report reportParameters) throws Exception {

        UUID reportUuid = parseUuidFromStr(reportParameters.getUuid());
        UUID orgUuid = getOrganisationUuidFromToken(sc);
        UUID userUuid = getEndUserUuidFromToken(sc);

        LOG.trace("DeletingReport UUID {}", reportUuid);

        JsonDeleteResponse ret = deleteItem(reportUuid, orgUuid, userUuid);

        return Response
                .ok()
                .entity(ret)
                .build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/scheduleReport")
    public Response scheduleReport(@Context SecurityContext sc, RequestParameters requestParameters) throws Exception
    {
        UUID orgUuid = getOrganisationUuidFromToken(sc);
        UUID userUuid = getEndUserUuidFromToken(sc);

        UUID reportUuid = parseUuidFromStr(requestParameters.getReportUuid());
        String parameterXml = RequestParametersSerializer.writeToXml(requestParameters);

        if (reportUuid == null) {
            throw new BadRequestException("Missing report UUID");
        }

        LOG.trace("ScheduilingReport UUID {}", reportUuid);

        DbRequest request = new DbRequest();
        request.setReportUuid(reportUuid);
        request.setOrganisationUuid(orgUuid);
        request.setEndUserUuid(userUuid);
        request.setTimeStamp(Instant.now());
        request.setParameters(parameterXml);

        request.writeToDb();

        return Response
                .ok()
                .build();
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/getReportSchedules")
    public Response getReportSchedules(@Context SecurityContext sc, @QueryParam("uuid") String reportUuidStr, @QueryParam("count") int count) throws Exception
    {
        UUID orgUuid = getOrganisationUuidFromToken(sc);
        UUID reportUuid = UUID.fromString(reportUuidStr);

        LOG.trace("getPastSchedules for report UUID {} and count {}", reportUuid, count);

        List<DbRequest> requests = DbRequest.retrieveForItem(orgUuid, reportUuid, count);

        HashMap<UUID, DbJob> hmJobsByUuid = new HashMap<>();
        List<DbJob> jobs = DbJob.retrieveForRequests(requests);
        for (DbJob job: jobs) {
            hmJobsByUuid.put(job.getJobUuid(), job);
        }

        HashMap<UUID, DbEndUser> hmUsersByUuid = new HashMap<>();
        List<DbEndUser> users = DbEndUser.retrieveForRequests(requests);
        for (DbEndUser user: users) {
            hmUsersByUuid.put(user.getEndUserUuid(), user);
        }

        List<JsonReportRequest> ret = new ArrayList<>();

        for (DbRequest request: requests) {
            DbJob job = hmJobsByUuid.get(request.getJobUuid());
            DbEndUser user = hmUsersByUuid.get(request.getEndUserUuid());

            ret.add(new JsonReportRequest(request, job, user));
        }


        return Response
                .ok()
                .entity(ret)
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/getScheduleResults")
    public Response getScheduleResults(@Context SecurityContext sc, @QueryParam("uuid") String requestUuidStr, @QueryParam("organisation") String organisationOdsCode) throws Exception
    {
        UUID orgUuid = getOrganisationUuidFromToken(sc);
        UUID requestUuid = UUID.fromString(requestUuidStr);

        LOG.trace("getScheduleResults for request UUID {}", requestUuid);

        DbRequest request = DbRequest.retrieveForUuid(requestUuid);
        UUID jobUuid = request.getJobUuid();
        UUID reportUuid = request.getReportUuid();
        String parameters = request.getParameters();

        if (jobUuid == null) {
            throw new BadRequestException("Schedule not run yet");
        }

        if (!request.getOrganisationUuid().equals(orgUuid)) {
            throw new BadRequestException("Requesting a schedule at another organisation");
        }

        //get the population count and results for each query
        Integer populationCount = null;
        HashMap<UUID, Integer> hmResultsByQuery = new HashMap<>();

        DbJobReport jobReport = DbJobReport.retrieveForJobAndReportAndParameters(jobUuid, reportUuid, parameters);
        if (organisationOdsCode == null) {
            populationCount = jobReport.getPopulationCount();
        } else {
            DbJobReportOrganisation jobReportOrganisation = DbJobReportOrganisation.retrieveForJobReportAndOdsCode(jobReport, organisationOdsCode);
            populationCount = jobReportOrganisation.getPopulationCount();
        }

        List<DbJobReportItem> jobReportItems = DbJobReportItem.retrieveForJobReport(jobReport.getJobReportUuid());
        for (DbJobReportItem jobReportItem: jobReportItems) {

            if (organisationOdsCode == null) {
                hmResultsByQuery.put(jobReportItem.getItemUuid(), jobReportItem.getResultCount());
            } else {
                DbJobReportItemOrganisation jobReportItemOrganisation = DbJobReportItemOrganisation.retrieveForJobReportItemAndOdsCode(jobReportItem, organisationOdsCode);
                if (jobReportItemOrganisation != null) { //this may be null if the reportItem is a listOutput
                    hmResultsByQuery.put(jobReportItem.getItemUuid(), jobReportItemOrganisation.getResultCount());
                }

            }
        }

        //retrieve the DbItem for the report, so we can work out the report query hierarchy
        DbItem reportItemObj = DbItem.retrieveLatestForUUid(reportUuid);
        String xml = reportItemObj.getXmlContent();
        Report report = QueryDocumentSerializer.readReportFromXml(xml);

        //we'll need the all the child queries in the report, so get them in as few DB hits as possile
        HashMap<UUID, DbItem> hmItemsByUuid = getItemsForReport(report);

        List<ReportItem> reportItems = report.getReportItem();
        JsonQueryResult dummyResult = new JsonQueryResult();
        populateReportResults(dummyResult, hmResultsByQuery, hmItemsByUuid, reportItems, populationCount);
        List<JsonQueryResult> queryResults = dummyResult.getChildQueries();

        JsonReportResult ret = new JsonReportResult();
        ret.setPopulationCount(populationCount);
        ret.setQueryResults(queryResults);

        return Response
                .ok()
                .entity(ret)
                .build();
    }

    private static void getQueryUuids(List<ReportItem> reportItems, List<UUID> uuids) {
        for (ReportItem reportItem: reportItems) {
            String queryUuidStr = reportItem.getQueryLibraryItemUuid();
            if (queryUuidStr == null || queryUuidStr.isEmpty()) {
                continue;
            }

            UUID queryUuid = UUID.fromString(queryUuidStr);
            uuids.add(queryUuid);
        }
    }
    private static HashMap<UUID, DbItem> getItemsForReport(Report report) throws Exception {
        List<UUID> itemUuids = new ArrayList<>();
        getQueryUuids(report.getReportItem(), itemUuids);

        HashMap<UUID, DbItem> ret = new HashMap<>();

        List<DbItem> items = DbItem.retrieveLatestForUuids(itemUuids);
        for (DbItem item: items) {
            ret.put(item.getItemUuid(), item);
        }

        return ret;
    }

    private static void populateReportResults(JsonQueryResult parent, HashMap<UUID, Integer> hmResultsByItem,
                                              HashMap<UUID, DbItem> hmItemsByUuid, List<ReportItem> reportItems, Integer parentResult) {
        for (ReportItem reportItem: reportItems) {
            String queryUuidStr = reportItem.getQueryLibraryItemUuid();
            if (queryUuidStr == null || queryUuidStr.isEmpty()) {
                continue;
            }

            UUID queryUuid = UUID.fromString(queryUuidStr);
            Integer queryResult = hmResultsByItem.get(queryUuid);
            DbItem item = hmItemsByUuid.get(queryUuid);

            JsonQueryResult result = new JsonQueryResult(item, queryResult, parentResult);
            parent.addChildReult(result);

            //if this report item has child report items of its own, then recurse
            List<ReportItem> childReportItems = reportItem.getReportItem();
            if (!childReportItems.isEmpty()) {
                populateReportResults(result, hmResultsByItem, hmItemsByUuid, childReportItems, queryResult);
            }
        }
    }
}
