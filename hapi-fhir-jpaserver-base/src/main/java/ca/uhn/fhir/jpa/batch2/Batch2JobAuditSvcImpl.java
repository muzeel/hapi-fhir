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
package ca.uhn.fhir.jpa.batch2;

import ca.uhn.fhir.batch2.api.IBatch2JobAuditSvc;
import ca.uhn.fhir.batch2.model.StatusEnum;
import ca.uhn.fhir.jpa.dao.data.IBatch2JobAuditRepository;
import ca.uhn.fhir.jpa.entity.Batch2JobAuditEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class Batch2JobAuditSvcImpl implements IBatch2JobAuditSvc {

	private final IBatch2JobAuditRepository myAuditRepository;

	@Autowired
	public Batch2JobAuditSvcImpl(IBatch2JobAuditRepository theAuditRepository) {
		myAuditRepository = theAuditRepository;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordOperation(
			String theInstanceId, String theDefinitionId, String theOperation, String theUsername, String theMessage) {
		Batch2JobAuditEntity entity = new Batch2JobAuditEntity();
		entity.setInstanceId(theInstanceId);
		entity.setDefinitionId(theDefinitionId);
		entity.setOperation(theOperation);
		entity.setUsername(theUsername);
		entity.setMessage(theMessage);
		entity.setCreateTime(new Date());
		myAuditRepository.save(entity);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordStatusChange(
			String theInstanceId,
			String theDefinitionId,
			StatusEnum thePriorStatus,
			StatusEnum theNewStatus,
			String theMessage) {
		Batch2JobAuditEntity entity = new Batch2JobAuditEntity();
		entity.setInstanceId(theInstanceId);
		entity.setDefinitionId(theDefinitionId);
		entity.setOperation(OP_STATUS_CHANGE);
		entity.setPriorStatus(thePriorStatus != null ? thePriorStatus.name() : null);
		entity.setNewStatus(theNewStatus != null ? theNewStatus.name() : null);
		entity.setMessage(theMessage);
		entity.setCreateTime(new Date());
		myAuditRepository.save(entity);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Page<Batch2JobAuditEntry> getAuditHistory(String theInstanceId, Pageable thePageable) {
		return myAuditRepository.findByInstanceId(theInstanceId, thePageable).map(this::toEntry);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Page<Batch2JobAuditEntry> getAuditHistoryByOperation(
			String theInstanceId, String theOperation, Pageable thePageable) {
		return myAuditRepository
				.findByInstanceIdAndOperation(theInstanceId, theOperation, thePageable)
				.map(this::toEntry);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public List<Batch2JobAuditEntry> getAuditHistoryByTimeRange(
			String theInstanceId, Date theFromTime, Date theToTime) {
		return myAuditRepository.findByInstanceIdAndCreateTimeRange(theInstanceId, theFromTime, theToTime).stream()
				.map(this::toEntry)
				.collect(Collectors.toList());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Page<Batch2JobAuditEntry> getAuditHistoryWithFilters(
			String theInstanceId, String theOperation, Date theFromTime, Date theToTime, Pageable thePageable) {
		return myAuditRepository
				.findByFilters(theInstanceId, theOperation, theFromTime, theToTime, thePageable)
				.map(this::toEntry);
	}

	private Batch2JobAuditEntry toEntry(Batch2JobAuditEntity theEntity) {
		return new Batch2JobAuditEntry(
				theEntity.getId(),
				theEntity.getInstanceId(),
				theEntity.getDefinitionId(),
				theEntity.getOperation(),
				theEntity.getPriorStatus(),
				theEntity.getNewStatus(),
				theEntity.getCreateTime(),
				theEntity.getUsername(),
				theEntity.getMessage());
	}
}
