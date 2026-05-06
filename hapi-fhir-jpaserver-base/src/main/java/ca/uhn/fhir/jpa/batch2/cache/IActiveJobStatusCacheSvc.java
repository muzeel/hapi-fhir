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
package ca.uhn.fhir.jpa.batch2.cache;

import ca.uhn.fhir.batch2.model.StatusEnum;

import java.util.Map;

/**
 * Cache for active job instance statuses to reduce DB queries.
 */
public interface IActiveJobStatusCacheSvc {

	/**
	 * Get the cached status for a job instance.
	 * @return the status or null if not cached
	 */
	StatusEnum getCachedStatus(String theInstanceId);

	/**
	 * Update the cached status for a job instance.
	 */
	void updateCachedStatus(String theInstanceId, StatusEnum theStatus);

	/**
	 * Remove the cached status for a job instance.
	 */
	void invalidateStatus(String theInstanceId);

	/**
	 * Get a snapshot of all cached active job statuses.
	 */
	Map<String, StatusEnum> getAllActiveStatuses();

	/**
	 * Clear the entire cache.
	 */
	void clear();
}
