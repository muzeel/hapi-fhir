/*-
 * #%L
 * HAPI FHIR JPA Server - Batch2 Task Processor
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
package ca.uhn.fhir.batch2.api;

import ca.uhn.fhir.batch2.model.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;

/**
 * Service for recording and querying audit events for Batch2 job operations.
 */
public interface IBatch2JobAuditSvc {

	String OP_CANCEL = "CANCEL";
	String OP_PAUSE = "PAUSE";
	String OP_RESUME = "RESUME";
	String OP_STATUS_CHANGE = "STATUS_CHANGE";
	String OP_START = "START";
	String OP_COMPLETE = "COMPLETE";
	String OP_FAIL = "FAIL";

	/**
	 * Records an audit event for a job operation (cancel, pause, resume).
	 */
	void recordOperation(
			String theInstanceId, String theDefinitionId, String theOperation, String theUsername, String theMessage);

	/**
	 * Records an audit event for a status change.
	 */
	void recordStatusChange(
			String theInstanceId,
			String theDefinitionId,
			StatusEnum thePriorStatus,
			StatusEnum theNewStatus,
			String theMessage);

	/**
	 * Fetches audit events for a specific job instance with pagination.
	 */
	Page<Batch2JobAuditEntry> getAuditHistory(String theInstanceId, Pageable thePageable);

	/**
	 * Fetches audit events for a specific job instance filtered by operation.
	 */
	Page<Batch2JobAuditEntry> getAuditHistoryByOperation(
			String theInstanceId, String theOperation, Pageable thePageable);

	/**
	 * Fetches audit events for a job instance within a time range.
	 */
	List<Batch2JobAuditEntry> getAuditHistoryByTimeRange(String theInstanceId, Date theFromTime, Date theToTime);

	/**
	 * Fetches all audit events matching the provided filters.
	 */
	Page<Batch2JobAuditEntry> getAuditHistoryWithFilters(
			String theInstanceId, String theOperation, Date theFromTime, Date theToTime, Pageable thePageable);

	/**
	 * Represents an audit log entry.
	 */
	class Batch2JobAuditEntry {
		private final Long myId;
		private final String myInstanceId;
		private final String myDefinitionId;
		private final String myOperation;
		private final String myPriorStatus;
		private final String myNewStatus;
		private final Date myCreateTime;
		private final String myUsername;
		private final String myMessage;

		public Batch2JobAuditEntry(
				Long theId,
				String theInstanceId,
				String theDefinitionId,
				String theOperation,
				String thePriorStatus,
				String theNewStatus,
				Date theCreateTime,
				String theUsername,
				String theMessage) {
			myId = theId;
			myInstanceId = theInstanceId;
			myDefinitionId = theDefinitionId;
			myOperation = theOperation;
			myPriorStatus = thePriorStatus;
			myNewStatus = theNewStatus;
			myCreateTime = theCreateTime;
			myUsername = theUsername;
			myMessage = theMessage;
		}

		public Long getId() {
			return myId;
		}

		public String getInstanceId() {
			return myInstanceId;
		}

		public String getDefinitionId() {
			return myDefinitionId;
		}

		public String getOperation() {
			return myOperation;
		}

		public String getPriorStatus() {
			return myPriorStatus;
		}

		public String getNewStatus() {
			return myNewStatus;
		}

		public Date getCreateTime() {
			return myCreateTime;
		}

		public String getUsername() {
			return myUsername;
		}

		public String getMessage() {
			return myMessage;
		}
	}
}
