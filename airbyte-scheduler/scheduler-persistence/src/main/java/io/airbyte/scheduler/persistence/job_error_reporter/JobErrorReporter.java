/*
 * Copyright (c) 2022 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.scheduler.persistence.job_error_reporter;

import io.airbyte.config.AttemptFailureSummary;
import io.airbyte.config.FailureReason;
import io.airbyte.config.FailureReason.FailureOrigin;
import io.airbyte.config.JobSyncConfig;
import io.airbyte.config.StandardDestinationDefinition;
import io.airbyte.config.StandardSourceDefinition;
import io.airbyte.config.StandardWorkspace;
import io.airbyte.config.persistence.ConfigRepository;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobErrorReporter {

  private static final String AIRBYTE_VERSION_META_KEY = "airbyte_version";
  private static final String FAILURE_ORIGIN_META_KEY = "failure_origin";
  private static final String FAILURE_TYPE_META_KEY = "failure_type";
  private static final String CONNECTION_ID_META_KEY = "connection_id";
  private static final String CONNECTOR_NAME_META_KEY = "connector_name";
  private static final String CONNECTOR_DEFINITION_ID_META_KEY = "connector_definition_id";
  private static final String CONNECTOR_RELEASE_STAGE_META_KEY = "connector_release_stage";

  private final ConfigRepository configRepository;
  private final String airbyteVersion;
  private final ErrorReportingClient errorReportingClient;

  public JobErrorReporter(
      final ConfigRepository configRepository,
      final String airbyteVersion,
      final ErrorReportingClient errorReportingClient) {

    this.configRepository = configRepository;
    this.airbyteVersion = airbyteVersion;
    this.errorReportingClient = errorReportingClient;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(JobErrorReporter.class);

  public void reportSyncJobFailure(final UUID connectionId, final AttemptFailureSummary failureSummary, final JobSyncConfig jobSyncConfig) {
    LOGGER.info("reportSyncJobFailure");
    final List<FailureReason> traceMessageFailures = failureSummary.getFailures().stream()
        .filter(failure -> failure.getMetadata().getAdditionalProperties().containsKey("from_trace_message")).toList();

    final StandardWorkspace workspace = configRepository.getStandardWorkspaceFromConnection(connectionId, true);

    for(final FailureReason failureReason : traceMessageFailures) {
      final FailureOrigin failureOrigin = failureReason.getFailureOrigin();

      final HashMap<String, String> metadata = new HashMap<>();
      metadata.put(CONNECTION_ID_META_KEY, connectionId.toString());
      metadata.put(AIRBYTE_VERSION_META_KEY, airbyteVersion);
      metadata.put(FAILURE_ORIGIN_META_KEY, failureOrigin.value());
      metadata.put(FAILURE_TYPE_META_KEY, failureReason.getFailureType().value());

      if(failureOrigin == FailureOrigin.SOURCE) {
        final StandardSourceDefinition sourceDefinition = configRepository.getSourceDefinitionFromConnection(connectionId);
        final String dockerImage = jobSyncConfig.getSourceDockerImage();

        metadata.put(CONNECTOR_DEFINITION_ID_META_KEY, sourceDefinition.getSourceDefinitionId().toString());
        metadata.put(CONNECTOR_NAME_META_KEY, sourceDefinition.getName());
        metadata.put(CONNECTOR_RELEASE_STAGE_META_KEY, sourceDefinition.getReleaseStage().value());

        errorReportingClient.report(workspace, failureReason, dockerImage, metadata);
      } else if(failureOrigin == FailureOrigin.DESTINATION) {
        final StandardDestinationDefinition destinationDefinition = configRepository.getDestinationDefinitionFromConnection(connectionId);
        final String dockerImage = jobSyncConfig.getDestinationDockerImage();

        metadata.put(CONNECTOR_DEFINITION_ID_META_KEY, destinationDefinition.getDestinationDefinitionId().toString());
        metadata.put(CONNECTOR_NAME_META_KEY, destinationDefinition.getName());
        metadata.put(CONNECTOR_RELEASE_STAGE_META_KEY, destinationDefinition.getReleaseStage().value());

        errorReportingClient.report(workspace, failureReason, dockerImage, metadata);
      }
    }
  }
}
