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
package ca.uhn.fhir.jpa.dao.data;

import ca.uhn.fhir.jpa.entity.Batch2JobAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface IBatch2JobAuditRepository extends JpaRepository<Batch2JobAuditEntity, Long>, IHapiFhirJpaRepository {

	@Query("SELECT a FROM Batch2JobAuditEntity a WHERE a.myInstanceId = :instanceId ORDER BY a.myCreateTime DESC")
	Page<Batch2JobAuditEntity> findByInstanceId(@Param("instanceId") String theInstanceId, Pageable thePageable);

	@Query(
			"SELECT a FROM Batch2JobAuditEntity a WHERE a.myInstanceId = :instanceId AND a.myOperation = :operation ORDER BY a.myCreateTime DESC")
	Page<Batch2JobAuditEntity> findByInstanceIdAndOperation(
			@Param("instanceId") String theInstanceId, @Param("operation") String theOperation, Pageable thePageable);

	@Query(
			"SELECT a FROM Batch2JobAuditEntity a WHERE a.myInstanceId = :instanceId AND a.myCreateTime >= :fromTime AND a.myCreateTime <= :toTime ORDER BY a.myCreateTime DESC")
	List<Batch2JobAuditEntity> findByInstanceIdAndCreateTimeRange(
			@Param("instanceId") String theInstanceId,
			@Param("fromTime") Date theFromTime,
			@Param("toTime") Date theToTime);

	@Query("SELECT a FROM Batch2JobAuditEntity a WHERE a.myDefinitionId = :definitionId ORDER BY a.myCreateTime DESC")
	Page<Batch2JobAuditEntity> findByDefinitionId(@Param("definitionId") String theDefinitionId, Pageable thePageable);

	@Query("SELECT a FROM Batch2JobAuditEntity a WHERE (:instanceId IS NULL OR a.myInstanceId = :instanceId) "
			+ "AND (:operation IS NULL OR a.myOperation = :operation) "
			+ "AND (:fromTime IS NULL OR a.myCreateTime >= :fromTime) "
			+ "AND (:toTime IS NULL OR a.myCreateTime <= :toTime) "
			+ "ORDER BY a.myCreateTime DESC")
	Page<Batch2JobAuditEntity> findByFilters(
			@Param("instanceId") String theInstanceId,
			@Param("operation") String theOperation,
			@Param("fromTime") Date theFromTime,
			@Param("toTime") Date theToTime,
			Pageable thePageable);
}
