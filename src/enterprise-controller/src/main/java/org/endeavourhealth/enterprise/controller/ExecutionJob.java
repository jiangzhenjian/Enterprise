package org.endeavourhealth.enterprise.controller;

import org.endeavourhealth.enterprise.controller.configuration.models.Configuration;
import org.endeavourhealth.enterprise.controller.configuration.ConfigurationAPI;
import org.endeavourhealth.enterprise.core.database.DatabaseManager;
import org.endeavourhealth.enterprise.core.database.DbAbstractTable;
import org.endeavourhealth.enterprise.core.database.TableSaveMode;
import org.endeavourhealth.enterprise.core.database.execution.*;
import org.endeavourhealth.enterprise.enginecore.carerecord.CareRecordDal;
import org.endeavourhealth.enterprise.enginecore.carerecord.SourceStatistics;
import org.endeavourhealth.enterprise.enginecore.communication.*;
import org.endeavourhealth.enterprise.enginecore.database.DatabaseConnectionDetails;
import org.endeavourhealth.enterprise.core.ExecutionStatus;
import org.endeavourhealth.enterprise.core.queuing.QueueConnectionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

class ExecutionJob {

    private UUID executionUuid = UUID.randomUUID();
    private Configuration configuration;
    private final static Logger logger = LoggerFactory.getLogger(ExecutionJob.class);
    private SourceStatistics primaryTableStats;
    private HashSet<Long> workerItemStartIds = new HashSet<>();
    private DbJob dbJob;


    public ExecutionJob(Configuration configuration) {
        this.configuration = configuration;
    }

    public UUID getExecutionUuid() {
        return executionUuid;
    }

    public void start() throws Exception {
        logger.debug("Starting job: " + executionUuid);

        clearPreviousJobs();
        List<DbRequest> itemRequests = getItemRequests();

        if (itemRequests.isEmpty()) {
            createJobAsFinished(ExecutionStatus.NoJobRequests);
            logger.debug("No jobs to run.  Job UUID: " + executionUuid);
            return;
        }

        setPatientStatistics();

        if (primaryTableStats.getRecordCount() == 0) {
            createJobAsFinished(ExecutionStatus.Failed);
            logger.error("Tried to start execution but zero patients to process.  Job UUID: " + executionUuid);
            return;
        }

        prepareExecutionTables(itemRequests);
        createAndPopulateWorkerQueue();
        startExecutionNodes();
    }

    private void clearPreviousJobs() throws Exception {
        PreviousJobClearup.clearPreviousJobs(configuration.getCoreDatabase(), configuration.getMessageQueuing());
    }

    private void setPatientStatistics() throws Exception {
        DatabaseConnectionDetails connectionDetails = ConfigurationAPI.convertConnection(configuration.getCareRecordDatabase());
        primaryTableStats = CareRecordDal.calculateTableStatistics(connectionDetails);
    }

    private void createJobAsFinished(ExecutionStatus executionStatus) throws Exception {
        DbJob job = new DbJob();
        job.setSaveMode(TableSaveMode.INSERT);

        Instant currentDate = Instant.now();

        job.setJobUuid(executionUuid);
        job.setStatusId(executionStatus);
        job.setStartDateTime(currentDate);
        job.setEndDateTime(currentDate);
        job.setBaselineAuditVersion(0);

        if (primaryTableStats != null)
            job.setPatientsInDatabase(primaryTableStats.getRecordCount());

        job.writeToDb();
    }

    private List<DbRequest> getItemRequests() throws Exception {
        return DbRequest.retrieveAllPending();
    }

    private void prepareExecutionTables(List<DbRequest> requests) throws Exception {

        List<DbAbstractTable> toSave = new ArrayList<>();

        dbJob = createJobAsStarted();
        toSave.add(dbJob);

        JobContentRetriever jobContentRetriever = new JobContentRetriever(requests);

        prepareJobContentTable(jobContentRetriever.getLibraryItemToAuditMap(), toSave);

        for (DbRequest request: requests) {

            DbJobReport jobReport = createJobReport(request, jobContentRetriever.getAuditUuid(request.getReportUuid()));
            toSave.add(jobReport);

            prepareJobReportItemTable(jobReport.getJobReportUuid(), request.getReportUuid(), jobContentRetriever, toSave);
        }

        DatabaseManager.db().writeEntities(toSave);
    }

    private void prepareJobReportItemTable(
            UUID jobReportUuid,
            UUID reportUuid,
            JobContentRetriever jobContentRetriever,
            List<DbAbstractTable> toSave
            ) throws Exception {

        RequestProcessor requestProcessor = new RequestProcessor(jobReportUuid, reportUuid, jobContentRetriever);

        for (DbJobReportItem reportItem: requestProcessor.getDbJobReportItems() ) {
            toSave.add(reportItem);
        }
    }

    private void prepareJobContentTable(Map<UUID, UUID> libraryItemToAuditMap, List<DbAbstractTable> toSave) {

        for (UUID itemUuid: libraryItemToAuditMap.keySet()) {

            DbJobContent jobContent = new DbJobContent();
            jobContent.setJobUuid(executionUuid);
            jobContent.setItemUuid(itemUuid);
            jobContent.setAuditUuid(libraryItemToAuditMap.get(itemUuid));
            jobContent.setSaveMode(TableSaveMode.INSERT); //because the primary keys have been explicitly set, we need to force insert mode
            toSave.add(jobContent);
        }
    }

    private DbJobReport createJobReport(DbRequest request, UUID auditUuid) throws Exception {

        DbJobReport jobReport = new DbJobReport();
        jobReport.assignPrimaryUUid();
        jobReport.setSaveMode(TableSaveMode.INSERT);

        jobReport.setJobUuid(executionUuid);
        jobReport.setReportUuid(request.getReportUuid());
        jobReport.setAuditUuid(auditUuid);
        jobReport.setOrganisationUuid(request.getOrganisationUuid());
        jobReport.setEndUserUuid(request.getEndUserUuid());
        jobReport.setParameters(request.getParameters());
        jobReport.setStatusId(ExecutionStatus.Executing);

        return jobReport;
    }

    private DbJob createJobAsStarted() {
        DbJob job = new DbJob();
        job.setSaveMode(TableSaveMode.INSERT);

        job.setJobUuid(executionUuid);
        job.setStatusId(ExecutionStatus.Executing);
        job.setStartDateTime(Instant.now());
        job.setBaselineAuditVersion(0);
        job.setPatientsInDatabase(primaryTableStats.getRecordCount());

        return job;
    }

    private void createAndPopulateWorkerQueue() throws Exception {

        QueueConnectionProperties connectionProperties = ConfigurationAPI.convertConnection(configuration.getMessageQueuing());
        long minimumId = primaryTableStats.getMinimumId();
        long maximumId;
        int numberOfBatches = 0;
        String queueName = WorkerQueue.calculateWorkerQueueName(configuration.getMessageQueuing().getWorkerQueuePrefix(), executionUuid);

        try (WorkerQueue queue = new WorkerQueue(connectionProperties, queueName)) {

            queue.create();

            while (true) {

                maximumId = minimumId + configuration.getPatientBatchSize() - 1;

                WorkerQueueBatchMessage message = WorkerQueueBatchMessage.CreateAsNew(
                        executionUuid,
                        minimumId,
                        maximumId);

                queue.sendMessage(message);
                workerItemStartIds.add(minimumId);
                numberOfBatches++;

                if (maximumId >= primaryTableStats.getMaximumId())
                    break;

                minimumId = maximumId + 1;
            }

            String message = String.format("Added IDs %s to %s in %s batches to Worker queue", primaryTableStats.getMinimumId(), primaryTableStats.getMaximumId(), numberOfBatches);
            queue.logDebug(message);
        }
    }

    private void startExecutionNodes() throws Exception {
        QueueConnectionProperties queueConnectionProperties = ConfigurationAPI.convertConnection(configuration.getMessageQueuing());
        String workerQueueName = WorkerQueue.calculateWorkerQueueName(configuration.getMessageQueuing().getWorkerQueuePrefix(), executionUuid);
        DatabaseConnectionDetails coreDatabaseConnectionDetails = ConfigurationAPI.convertConnection(configuration.getCoreDatabase());
        DatabaseConnectionDetails careRecordDatabaseConnectionDetails = ConfigurationAPI.convertConnection(configuration.getCareRecordDatabase());

        ProcessorNodesStartMessage.StartMessagePayload payload = new ProcessorNodesStartMessage.StartMessagePayload();
        payload.setJobUuid(executionUuid);
        payload.setWorkerQueueName(workerQueueName);
        payload.setCoreDatabaseConnectionDetails(coreDatabaseConnectionDetails);
        payload.setCareRecordDatabaseConnectionDetails(careRecordDatabaseConnectionDetails);
        payload.setControllerQueueName(configuration.getMessageQueuing().getControllerQueueName());

        ProcessorNodesStartMessage message = ProcessorNodesStartMessage.CreateAsNew(payload);

        ProcessorNodesExchange exchange = new ProcessorNodesExchange(queueConnectionProperties, configuration.getMessageQueuing().getProcessorNodesExchangeName());
        exchange.sendMessage(message);
    }

    public synchronized void workerItemComplete(ControllerQueueWorkItemCompleteMessage.WorkItemCompletePayload payload) {
        if (!executionUuid.equals(payload.getExecutionUuid()))
            return;

        if (workerItemStartIds.contains(payload.getStartId()))
            workerItemStartIds.remove(payload.getStartId());

        if (workerItemStartIds.isEmpty())
            jobFinished();
    }

    private void jobFinished() {

        dbJob.setSaveMode(TableSaveMode.UPDATE);
        dbJob.markAsFinished(ExecutionStatus.Succeeded);

        try {
            dbJob.writeToDb();
            logger.debug("Execution Job finished: " + executionUuid.toString());
        } catch (Exception e) {
            logger.error("Job Finished exception", e);
        }
    }
}