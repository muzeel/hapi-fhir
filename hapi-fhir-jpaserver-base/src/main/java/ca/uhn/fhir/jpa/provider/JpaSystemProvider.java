/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2026 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.jpa.provider;

import ca.uhn.fhir.batch2.api.IBatch2JobAuditSvc;
import ca.uhn.fhir.batch2.api.IJobCoordinator;
import ca.uhn.fhir.batch2.api.JobOperationResultJson;
import ca.uhn.fhir.batch2.model.BatchInstanceStatusDTO;
import ca.uhn.fhir.batch2.model.BatchWorkChunkStatusDTO;
import ca.uhn.fhir.batch2.model.JobInstance;
import ca.uhn.fhir.batch2.models.JobInstanceFetchRequest;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.api.IInterceptorBroadcaster;
import ca.uhn.fhir.interceptor.model.ReadPartitionIdRequestDetails;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.interceptor.ProvenanceAgentsPointcutUtil;
import ca.uhn.fhir.jpa.model.util.JpaConstants;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.merge.MergeResourceHelper;
import ca.uhn.fhir.model.api.IProvenanceAgent;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.replacereferences.ReplaceReferencesRequest;
import ca.uhn.fhir.replacereferences.UndoReplaceReferencesRequest;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.util.ParametersUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_REPLACE_REFERENCES_OUTPUT_PARAM_TASK;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_REPLACE_REFERENCES_PARAM_SOURCE_REFERENCE_ID;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_REPLACE_REFERENCES_PARAM_TARGET_REFERENCE_ID;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_UNDO_REPLACE_REFERENCES;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static software.amazon.awssdk.utils.StringUtils.isBlank;

public final class JpaSystemProvider<T, MT> extends BaseJpaSystemProvider<T, MT> {
	@Autowired
	private IRequestPartitionHelperSvc myRequestPartitionHelperSvc;

	@Autowired
	private IInterceptorBroadcaster myInterceptorBroadcaster;

	@Autowired
	private IJobCoordinator myJobCoordinator;

	@Autowired
	private IBatch2JobAuditSvc myBatch2JobAuditSvc;

	@Description(
			"Marks all currently existing resources of a given type, or all resources of all types, for reindexing.")
	@Operation(
			name = MARK_ALL_RESOURCES_FOR_REINDEXING,
			idempotent = false,
			returnParameters = {@OperationParam(name = "status")})
	/**
	 * @deprecated
	 * @see ReindexProvider#Reindex(List, IPrimitiveType, RequestDetails)
	 */
	@Deprecated
	public IBaseResource markAllResourcesForReindexing(
			@OperationParam(name = "type", min = 0, max = 1, typeName = "code") IPrimitiveType<String> theType) {

		if (theType != null && isNotBlank(theType.getValueAsString())) {
			getResourceReindexingSvc().markAllResourcesForReindexing(theType.getValueAsString());
		} else {
			getResourceReindexingSvc().markAllResourcesForReindexing();
		}

		IBaseParameters retVal = ParametersUtil.newInstance(getContext());

		IPrimitiveType<?> string = ParametersUtil.createString(getContext(), "Marked resources");
		ParametersUtil.addParameterToParameters(getContext(), retVal, "status", string);

		return retVal;
	}

	@Description("Forces a single pass of the resource reindexing processor")
	@Operation(
			name = PERFORM_REINDEXING_PASS,
			idempotent = false,
			returnParameters = {@OperationParam(name = "status")})
	/**
	 * @deprecated
	 * @see ReindexProvider#Reindex(List, IPrimitiveType, RequestDetails)
	 */
	@Deprecated
	public IBaseResource performReindexingPass() {
		Integer count = getResourceReindexingSvc().runReindexingPass();

		IBaseParameters retVal = ParametersUtil.newInstance(getContext());

		IPrimitiveType<?> string;
		if (count == null) {
			string = ParametersUtil.createString(getContext(), "Index pass already proceeding");
		} else {
			string = ParametersUtil.createString(getContext(), "Indexed " + count + " resources");
		}
		ParametersUtil.addParameterToParameters(getContext(), retVal, "status", string);

		return retVal;
	}

	@Operation(name = JpaConstants.OPERATION_GET_RESOURCE_COUNTS, idempotent = true)
	@Description(
			shortDefinition =
					"Provides the number of resources currently stored on the server, broken down by resource type")
	public IBaseParameters getResourceCounts() {
		IBaseParameters retVal = ParametersUtil.newInstance(getContext());

		Map<String, Long> counts = getDao().getResourceCountsFromCache();
		counts = defaultIfNull(counts, Collections.emptyMap());
		counts = new TreeMap<>(counts);
		for (Map.Entry<String, Long> nextEntry : counts.entrySet()) {
			ParametersUtil.addParameterToParametersInteger(
					getContext(),
					retVal,
					nextEntry.getKey(),
					nextEntry.getValue().intValue());
		}

		return retVal;
	}

	@Operation(
			name = ProviderConstants.OPERATION_META,
			idempotent = true,
			returnParameters = {@OperationParam(name = "return", typeName = "Meta")})
	public IBaseParameters meta(RequestDetails theRequestDetails) {
		IBaseParameters retVal = ParametersUtil.newInstance(getContext());
		ParametersUtil.addParameterToParameters(
				getContext(), retVal, "return", getDao().metaGetOperation(theRequestDetails));
		return retVal;
	}

	@SuppressWarnings("unchecked")
	@Transaction
	public IBaseBundle transaction(RequestDetails theRequestDetails, @TransactionParam IBaseBundle theResources) {
		startRequest(((ServletRequestDetails) theRequestDetails).getServletRequest());
		try {
			IFhirSystemDao<T, MT> dao = getDao();
			return (IBaseBundle) dao.transaction(theRequestDetails, (T) theResources);
		} finally {
			endRequest(((ServletRequestDetails) theRequestDetails).getServletRequest());
		}
	}

	@Operation(name = ProviderConstants.OPERATION_REPLACE_REFERENCES, global = true)
	@Description(
			value =
					"This operation searches for all references matching the provided id and updates them to references to the provided target-reference-id.",
			shortDefinition = "Repoints referencing resources to another resources instance")
	public IBaseParameters replaceReferences(
			@OperationParam(
							name = ProviderConstants.OPERATION_REPLACE_REFERENCES_PARAM_SOURCE_REFERENCE_ID,
							min = 1,
							typeName = "string")
					IPrimitiveType<String> theSourceId,
			@OperationParam(
							name = ProviderConstants.OPERATION_REPLACE_REFERENCES_PARAM_TARGET_REFERENCE_ID,
							min = 1,
							typeName = "string")
					IPrimitiveType<String> theTargetId,
			@OperationParam(
							name = ProviderConstants.OPERATION_REPLACE_REFERENCES_RESOURCE_LIMIT,
							typeName = "unsignedInt")
					IPrimitiveType<Integer> theResourceLimit,
			ServletRequestDetails theServletRequest) {
		startRequest(theServletRequest);

		try {
			validateReplaceReferencesParams(theSourceId, theTargetId);

			int resourceLimit = MergeResourceHelper.setResourceLimitFromParameter(myStorageSettings, theResourceLimit);

			IdDt sourceId = new IdDt(theSourceId.getValue());
			IdDt targetId = new IdDt(theTargetId.getValue());
			RequestPartitionId partitionId = myRequestPartitionHelperSvc.determineReadPartitionForRequest(
					theServletRequest, ReadPartitionIdRequestDetails.forRead(targetId));

			List<IProvenanceAgent> provenanceAgents =
					ProvenanceAgentsPointcutUtil.ifHasCallHooks(theServletRequest, myInterceptorBroadcaster);

			ReplaceReferencesRequest replaceReferencesRequest = new ReplaceReferencesRequest(
					sourceId, targetId, resourceLimit, partitionId, true, provenanceAgents);
			IBaseParameters retval =
					getReplaceReferencesSvc().replaceReferences(replaceReferencesRequest, theServletRequest);
			if (ParametersUtil.getNamedParameter(getContext(), retval, OPERATION_REPLACE_REFERENCES_OUTPUT_PARAM_TASK)
					.isPresent()) {
				HttpServletResponse response = theServletRequest.getServletResponse();
				response.setStatus(HttpServletResponse.SC_ACCEPTED);
			}
			return retval;
		} finally {
			endRequest(theServletRequest);
		}
	}

	@Operation(name = OPERATION_UNDO_REPLACE_REFERENCES, global = true)
	@Description(
			value =
					"This operation undoes the effects of a previous $hapi.fhir.replace-references operation by restoring "
							+ "references that were replaced from the target back to the original source.",
			shortDefinition =
					"Restores references from target back to source for resources that were previously updated by a $hapi.fhir.replace-references operation.")
	public IBaseParameters undoReplaceReferences(
			@OperationParam(
							name = ProviderConstants.OPERATION_REPLACE_REFERENCES_PARAM_SOURCE_REFERENCE_ID,
							min = 1,
							typeName = "string")
					IPrimitiveType<String> theSourceId,
			@OperationParam(
							name = ProviderConstants.OPERATION_REPLACE_REFERENCES_PARAM_TARGET_REFERENCE_ID,
							min = 1,
							typeName = "string")
					IPrimitiveType<String> theTargetId,
			ServletRequestDetails theRequestDetails) {
		startRequest(theRequestDetails);

		try {

			validateReplaceReferencesParams(theSourceId, theTargetId);

			IdDt sourceId = new IdDt(theSourceId.getValue());
			IdDt targetId = new IdDt(theTargetId.getValue());

			int resourceLimit = myStorageSettings.getInternalSynchronousSearchSize();

			UndoReplaceReferencesRequest undoReplaceReferencesRequest =
					new UndoReplaceReferencesRequest(sourceId, targetId, resourceLimit);

			return getUndoReplaceReferencesSvc().undoReplaceReferences(undoReplaceReferencesRequest, theRequestDetails);
		} finally {
			endRequest(theRequestDetails);
		}
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_LIST, idempotent = true)
	@Description(shortDefinition = "Returns Batch2 jobs with optional filtering")
	public IBaseParameters batch2JobList(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_DEFINITION_ID, typeName = "string")
					IPrimitiveType<String> theJobDefinitionId,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_STATUS, typeName = "code")
					IPrimitiveType<String> theStatus,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, typeName = "string")
					IPrimitiveType<String> theJobId,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_FROM, typeName = "instant")
					IPrimitiveType<Date> theFrom,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_TO, typeName = "instant")
					IPrimitiveType<Date> theTo,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_PAGE_START, typeName = "integer")
					IPrimitiveType<Integer> thePageStart,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_BATCH_SIZE, typeName = "integer")
					IPrimitiveType<Integer> theBatchSize) {
		JobInstanceFetchRequest request = new JobInstanceFetchRequest();
		request.setJobDefinitionId(getOptionalPrimitiveValue(theJobDefinitionId));
		request.setJobStatus(getOptionalPrimitiveValue(theStatus));
		request.setJobId(getOptionalPrimitiveValue(theJobId));
		request.setJobCreateTimeFrom(getOptionalPrimitiveValue(theFrom));
		request.setJobCreateTimeTo(getOptionalPrimitiveValue(theTo));
		request.setPageStart(defaultIfNull(getOptionalPrimitiveValue(thePageStart), 0));
		request.setBatchSize(defaultIfNull(getOptionalPrimitiveValue(theBatchSize), 20));
		request.setSort(Sort.by(Sort.Direction.DESC, "myCreateTime"));

		var page = myJobCoordinator.fetchAllJobInstances(request);
		IBaseParameters retVal = ParametersUtil.newInstance(getContext());
		ParametersUtil.addParameterToParametersInteger(getContext(), retVal, "total", (int) page.getTotalElements());
		for (JobInstance next : page.getContent()) {
			IBaseParameters nextParam = ParametersUtil.newInstance(getContext());
			addJobInstanceToParameters(nextParam, next);
			ParametersUtil.addParameterToParameters(getContext(), retVal, "job", nextParam);
		}
		return retVal;
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_GET, idempotent = true)
	@Description(shortDefinition = "Returns detailed status for a Batch2 job")
	public IBaseParameters batch2JobGet(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, min = 1, typeName = "string")
					IPrimitiveType<String> theJobId) {
		String jobId = requireParam(theJobId, ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID);
		JobInstance instance = myJobCoordinator.getInstance(jobId);
		BatchInstanceStatusDTO status = myJobCoordinator.getBatchInstanceStatus(jobId);
		IBaseParameters retVal = ParametersUtil.newInstance(getContext());
		addJobInstanceToParameters(retVal, instance);
		ParametersUtil.addParameterToParametersString(
				getContext(), retVal, "instanceStatus", status.status().name());
		return retVal;
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_GET_CHUNKS, idempotent = true)
	@Description(shortDefinition = "Returns per-status chunk summary for a Batch2 job")
	public IBaseParameters batch2JobGetChunks(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, min = 1, typeName = "string")
					IPrimitiveType<String> theJobId) {
		String jobId = requireParam(theJobId, ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID);
		List<BatchWorkChunkStatusDTO> chunkStatuses = myJobCoordinator.getWorkChunkStatus(jobId);
		IBaseParameters retVal = ParametersUtil.newInstance(getContext());
		for (BatchWorkChunkStatusDTO next : chunkStatuses) {
			IBaseParameters nextParam = ParametersUtil.newInstance(getContext());
			ParametersUtil.addParameterToParametersString(getContext(), nextParam, "status", next.status.name());
			ParametersUtil.addParameterToParametersInteger(
					getContext(), nextParam, "count", Math.toIntExact(next.totalChunks));
			ParametersUtil.addParameterToParameters(getContext(), retVal, "chunk", nextParam);
		}
		return retVal;
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_CANCEL, idempotent = false)
	public IBaseParameters batch2JobCancel(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, min = 1, typeName = "string")
					IPrimitiveType<String> theJobId) {
		return toOperationResult(myJobCoordinator.cancelInstance(
				requireParam(theJobId, ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID)));
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_PAUSE, idempotent = false)
	public IBaseParameters batch2JobPause(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, min = 1, typeName = "string")
					IPrimitiveType<String> theJobId) {
		return toOperationResult(myJobCoordinator.pauseInstance(
				requireParam(theJobId, ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID)));
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_RESUME, idempotent = false)
	public IBaseParameters batch2JobResume(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, min = 1, typeName = "string")
					IPrimitiveType<String> theJobId) {
		return toOperationResult(myJobCoordinator.resumeInstance(
				requireParam(theJobId, ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID)));
	}

	@Operation(name = ProviderConstants.OPERATION_BATCH2_JOB_HISTORY, idempotent = true)
	@Description(shortDefinition = "Returns audit history for a Batch2 job")
	public IBaseParameters batch2JobHistory(
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID, min = 1, typeName = "string")
					IPrimitiveType<String> theJobId,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_OPERATION, typeName = "string")
					IPrimitiveType<String> theOperation,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_FROM, typeName = "instant")
					IPrimitiveType<Date> theFrom,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_TO, typeName = "instant")
					IPrimitiveType<Date> theTo,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_PAGE_START, typeName = "integer")
					IPrimitiveType<Integer> thePageStart,
			@OperationParam(name = ProviderConstants.OPERATION_BATCH2_PARAM_BATCH_SIZE, typeName = "integer")
					IPrimitiveType<Integer> theBatchSize) {
		String jobId = requireParam(theJobId, ProviderConstants.OPERATION_BATCH2_PARAM_JOB_ID);

		Page<IBatch2JobAuditSvc.Batch2JobAuditEntry> page = myBatch2JobAuditSvc.getAuditHistoryWithFilters(
				jobId,
				getOptionalPrimitiveValue(theOperation),
				getOptionalPrimitiveValue(theFrom),
				getOptionalPrimitiveValue(theTo),
				org.springframework.data.domain.PageRequest.of(
						defaultIfNull(getOptionalPrimitiveValue(thePageStart), 0),
						defaultIfNull(getOptionalPrimitiveValue(theBatchSize), 20),
						Sort.by(Sort.Direction.DESC, "myCreateTime")));

		IBaseParameters retVal = ParametersUtil.newInstance(getContext());
		ParametersUtil.addParameterToParametersInteger(getContext(), retVal, "total", (int) page.getTotalElements());
		for (IBatch2JobAuditSvc.Batch2JobAuditEntry entry : page.getContent()) {
			IBaseParameters entryParam = ParametersUtil.newInstance(getContext());
			ParametersUtil.addParameterToParametersString(
					getContext(), entryParam, "instanceId", entry.getInstanceId());
			ParametersUtil.addParameterToParametersString(
					getContext(), entryParam, "definitionId", entry.getDefinitionId());
			ParametersUtil.addParameterToParametersString(getContext(), entryParam, "operation", entry.getOperation());
			if (entry.getPriorStatus() != null) {
				ParametersUtil.addParameterToParametersString(
						getContext(), entryParam, "priorStatus", entry.getPriorStatus());
			}
			if (entry.getNewStatus() != null) {
				ParametersUtil.addParameterToParametersString(
						getContext(), entryParam, "newStatus", entry.getNewStatus());
			}
			if (entry.getUsername() != null) {
				ParametersUtil.addParameterToParametersString(
						getContext(), entryParam, "username", entry.getUsername());
			}
			if (entry.getMessage() != null) {
				ParametersUtil.addParameterToParametersString(getContext(), entryParam, "message", entry.getMessage());
			}
			if (entry.getCreateTime() != null) {
				ParametersUtil.addParameterToParameters(
						getContext(),
						entryParam,
						"timestamp",
						ParametersUtil.createInstant(getContext(), entry.getCreateTime()));
			}
			ParametersUtil.addParameterToParameters(getContext(), retVal, "auditEntry", entryParam);
		}
		return retVal;
	}

	private IBaseParameters toOperationResult(JobOperationResultJson theResult) {
		IBaseParameters retVal = ParametersUtil.newInstance(getContext());
		ParametersUtil.addParameterToParametersBoolean(getContext(), retVal, "success", theResult.getSuccess());
		ParametersUtil.addParameterToParametersString(getContext(), retVal, "operation", theResult.getOperation());
		ParametersUtil.addParameterToParametersString(getContext(), retVal, "message", theResult.getMessage());
		return retVal;
	}

	private void addJobInstanceToParameters(IBaseParameters theTarget, JobInstance theInstance) {
		ParametersUtil.addParameterToParametersString(getContext(), theTarget, "jobId", theInstance.getInstanceId());
		ParametersUtil.addParameterToParametersString(
				getContext(), theTarget, "jobDefinitionId", theInstance.getJobDefinitionId());
		ParametersUtil.addParameterToParametersString(
				getContext(), theTarget, "status", theInstance.getStatus().name());
		ParametersUtil.addParameterToParametersBoolean(getContext(), theTarget, "cancelled", theInstance.isCancelled());
		ParametersUtil.addParameterToParametersString(getContext(), theTarget, "progressPct", Integer.toString((int)
				Math.round(theInstance.getProgress() * 100)));
		if (theInstance.getCombinedRecordsProcessed() != null) {
			ParametersUtil.addParameterToParametersInteger(
					getContext(), theTarget, "recordsProcessed", theInstance.getCombinedRecordsProcessed());
		}
		if (theInstance.getEstimatedTimeRemaining() != null) {
			ParametersUtil.addParameterToParametersString(
					getContext(), theTarget, "estimatedTimeRemaining", theInstance.getEstimatedTimeRemaining());
		}
		if (theInstance.getCreateTime() != null) {
			ParametersUtil.addParameterToParameters(
					getContext(),
					theTarget,
					"createTime",
					ParametersUtil.createInstant(getContext(), theInstance.getCreateTime()));
		}
		if (theInstance.getStartTime() != null) {
			ParametersUtil.addParameterToParameters(
					getContext(),
					theTarget,
					"startTime",
					ParametersUtil.createInstant(getContext(), theInstance.getStartTime()));
		}
		if (theInstance.getEndTime() != null) {
			ParametersUtil.addParameterToParameters(
					getContext(),
					theTarget,
					"endTime",
					ParametersUtil.createInstant(getContext(), theInstance.getEndTime()));
		}
	}

	private static String requireParam(IPrimitiveType<String> theParam, String theName) {
		if (theParam == null || isBlank(theParam.getValue())) {
			throw new InvalidRequestException(Msg.code(4000) + "Parameter '" + theName + "' is required");
		}
		return theParam.getValue();
	}

	private static <T> T getOptionalPrimitiveValue(IPrimitiveType<T> thePrimitive) {
		return thePrimitive != null ? thePrimitive.getValue() : null;
	}

	private static void validateReplaceReferencesParams(
			IPrimitiveType<String> theSourceId, IPrimitiveType<String> theTargetId) {
		if (theSourceId == null || isBlank(theSourceId.getValue())) {
			throw new InvalidRequestException(Msg.code(2583) + "Parameter '"
					+ OPERATION_REPLACE_REFERENCES_PARAM_SOURCE_REFERENCE_ID + "' is blank");
		}

		if (theTargetId == null || isBlank(theTargetId.getValue())) {
			throw new InvalidRequestException(Msg.code(2584) + "Parameter '"
					+ OPERATION_REPLACE_REFERENCES_PARAM_TARGET_REFERENCE_ID + "' is blank");
		}
	}
}
