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

import ca.uhn.fhir.batch2.api.IJobPersistence;
import ca.uhn.fhir.batch2.model.JobInstance;
import ca.uhn.fhir.batch2.model.StatusEnum;
import ca.uhn.fhir.jpa.model.sched.HapiJob;
import ca.uhn.fhir.jpa.model.sched.IHasScheduledJobs;
import ca.uhn.fhir.jpa.model.sched.ISchedulerService;
import ca.uhn.fhir.jpa.model.sched.ScheduledJobDefinition;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the status of active job instances to reduce DB queries.
 * Active statuses: QUEUED, IN_PROGRESS, FINALIZE
 */
@Service
public class ActiveJobStatusCacheSvcImpl implements IActiveJobStatusCacheSvc, IHasScheduledJobs {

	private static final Logger ourLog = LoggerFactory.getLogger(ActiveJobStatusCacheSvcImpl.class);

	private static final Set<StatusEnum> ACTIVE_STATUSES =
			EnumSet.of(StatusEnum.QUEUED, StatusEnum.IN_PROGRESS, StatusEnum.FINALIZE);

	private final ConcurrentHashMap<String, StatusEnum> myCache = new ConcurrentHashMap<>();
	private volatile long myCacheMillis = 30_000; // default 30s
	private volatile long myLastRefresh;

	@Lazy
	@Autowired(required = false)
	private IJobPersistence myJobPersistence;

	@Override
	public StatusEnum getCachedStatus(String theInstanceId) {
		return myCache.get(theInstanceId);
	}

	@Override
	public void updateCachedStatus(String theInstanceId, StatusEnum theStatus) {
		if (ACTIVE_STATUSES.contains(theStatus)) {
			myCache.put(theInstanceId, theStatus);
		} else {
			myCache.remove(theInstanceId);
		}
	}

	@Override
	public void invalidateStatus(String theInstanceId) {
		myCache.remove(theInstanceId);
	}

	@Override
	public Map<String, StatusEnum> getAllActiveStatuses() {
		return Collections.unmodifiableMap(myCache);
	}

	@Override
	public void clear() {
		ourLog.info("Clearing active job status cache");
		myCache.clear();
		myLastRefresh = 0;
	}

	/**
	 * Refresh the cache from the database if TTL has expired.
	 */
	public void refreshIfNeeded() {
		if (myJobPersistence == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - myLastRefresh < myCacheMillis) {
			return;
		}

		ourLog.debug("Refreshing active job status cache");
		int pageSize = 500;
		int pageIndex = 0;
		List<JobInstance> instances;
		do {
			instances = myJobPersistence.fetchInstances(pageSize, pageIndex);
			for (JobInstance instance : instances) {
				if (ACTIVE_STATUSES.contains(instance.getStatus())) {
					myCache.put(instance.getInstanceId(), instance.getStatus());
				} else {
					myCache.remove(instance.getInstanceId());
				}
			}
			pageIndex++;
		} while (!instances.isEmpty() && pageIndex < 10); // safety limit

		myLastRefresh = now;
		ourLog.debug("Active job status cache refreshed, {} entries", myCache.size());
	}

	public void setCacheMillis(long theCacheMillis) {
		myCacheMillis = theCacheMillis;
	}

	@Override
	public void scheduleJobs(ISchedulerService theSchedulerService) {
		ScheduledJobDefinition jobDetail = new ScheduledJobDefinition();
		jobDetail.setId(getClass().getName());
		jobDetail.setJobClass(Job.class);
		theSchedulerService.scheduleLocalJob(5 * DateUtils.MILLIS_PER_MINUTE, jobDetail);
	}

	public static class Job implements HapiJob {
		@Autowired
		private ActiveJobStatusCacheSvcImpl myTarget;

		@Override
		public void execute(org.quartz.JobExecutionContext theContext) {
			myTarget.refreshIfNeeded();
		}
	}
}
