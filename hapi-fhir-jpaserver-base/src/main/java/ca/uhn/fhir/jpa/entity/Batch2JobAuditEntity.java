/*-
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
package ca.uhn.fhir.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(
		name = "BT2_JOB_AUDIT",
		indexes = {
			@Index(name = "IDX_BT2_AUDIT_INSTANCE_ID", columnList = "INSTANCE_ID"),
			@Index(name = "IDX_BT2_AUDIT_OPERATION", columnList = "OPERATION"),
			@Index(name = "IDX_BT2_AUDIT_CREATE_TIME", columnList = "CREATE_TIME")
		})
public class Batch2JobAuditEntity implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final int OPERATION_MAX_LENGTH = 50;
	public static final int STATUS_MAX_LENGTH = 20;
	public static final int USERNAME_MAX_LENGTH = 200;
	public static final int MESSAGE_MAX_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BT2_JOB_AUDIT_ID")
	@SequenceGenerator(name = "SEQ_BT2_JOB_AUDIT_ID", sequenceName = "SEQ_BT2_JOB_AUDIT_ID")
	@Column(name = "PID", nullable = false)
	private Long myId;

	@Column(name = "INSTANCE_ID", length = 100, nullable = false)
	private String myInstanceId;

	@Column(name = "DEFINITION_ID", length = 100, nullable = false)
	private String myDefinitionId;

	@Column(name = "OPERATION", length = OPERATION_MAX_LENGTH, nullable = false)
	private String myOperation;

	@Column(name = "PRIOR_STATUS", length = STATUS_MAX_LENGTH, nullable = true)
	private String myPriorStatus;

	@Column(name = "NEW_STATUS", length = STATUS_MAX_LENGTH, nullable = true)
	private String myNewStatus;

	@Column(name = "CREATE_TIME", nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date myCreateTime;

	@Column(name = "USER_NAME", length = USERNAME_MAX_LENGTH, nullable = true)
	private String myUsername;

	@Column(name = "MESSAGE", length = MESSAGE_MAX_LENGTH, nullable = true)
	private String myMessage;

	public Long getId() {
		return myId;
	}

	public void setId(Long theId) {
		myId = theId;
	}

	public String getInstanceId() {
		return myInstanceId;
	}

	public void setInstanceId(String theInstanceId) {
		myInstanceId = theInstanceId;
	}

	public String getDefinitionId() {
		return myDefinitionId;
	}

	public void setDefinitionId(String theDefinitionId) {
		myDefinitionId = theDefinitionId;
	}

	public String getOperation() {
		return myOperation;
	}

	public void setOperation(String theOperation) {
		myOperation = theOperation;
	}

	public String getPriorStatus() {
		return myPriorStatus;
	}

	public void setPriorStatus(String thePriorStatus) {
		myPriorStatus = thePriorStatus;
	}

	public String getNewStatus() {
		return myNewStatus;
	}

	public void setNewStatus(String theNewStatus) {
		myNewStatus = theNewStatus;
	}

	public Date getCreateTime() {
		return myCreateTime;
	}

	public void setCreateTime(Date theCreateTime) {
		myCreateTime = theCreateTime;
	}

	public String getUsername() {
		return myUsername;
	}

	public void setUsername(String theUsername) {
		myUsername = theUsername;
	}

	public String getMessage() {
		return myMessage;
	}

	public void setMessage(String theMessage) {
		myMessage = theMessage;
	}
}
