package org.endeavour.enterprise.endpoints;

import org.endeavourhealth.common.security.SecurityUtils;
import org.endeavourhealth.enterprise.core.database.ResultsManager;
import org.endeavourhealth.enterprise.core.database.models.ItemEntity;
import org.endeavourhealth.enterprise.core.json.JsonCohortRun;
import org.endeavourhealth.enterprise.core.querydocument.QueryDocumentSerializer;
import org.endeavourhealth.enterprise.core.querydocument.models.LibraryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("/report")
public final class ReportEndpoint extends AbstractItemEndpoint {

	private static final Logger LOG = LoggerFactory.getLogger(ReportEndpoint.class);

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/run")
	public Response run(@Context SecurityContext sc, JsonCohortRun report) throws Exception {
		super.setLogbackMarkers(sc);

		String userUuid = SecurityUtils.getCurrentUserId(sc).toString();

		ResultsManager resultsManager = new ResultsManager();

		ItemEntity item = ItemEntity.retrieveLatestForUUid(report.getQueryItemUuid());
		String xml = item.getXmlContent();
		LibraryItem libraryItem = QueryDocumentSerializer.readLibraryItemFromXml(xml);

		resultsManager.runReport(libraryItem, report, userUuid);

		clearLogbackMarkers();

		return Response
				.ok()
				.entity(null)
				.build();
	}

}
