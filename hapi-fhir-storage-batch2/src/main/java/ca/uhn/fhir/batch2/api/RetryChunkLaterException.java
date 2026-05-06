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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Exception that is thrown when a polling step needs to be retried at a later
 * time.
 */
public class RetryChunkLaterException extends RuntimeException {

	private static final Duration ONE_MINUTE = Duration.of(1, ChronoUnit.MINUTES);

	/**
	 * The delay to wait (in ms) for the next poll call.
	 * For now, it's a constant, but we hold it here in
	 * case we want to change this behaviour in the future.
	 */
	private final Duration myNextPollDuration;

	public RetryChunkLaterException() {
		this("", ONE_MINUTE);
	}

	/**
	 * For HAPI exceptions, use {@link RetryChunkLaterException#RetryChunkLaterException(String, Duration)}
	 */
	public RetryChunkLaterException(Duration theDuration) {
		this("", theDuration);
	}

	/**
	 * For HAPI exceptions that accept the default duration
	 */
	public RetryChunkLaterException(String theCode) {
		this(theCode, ONE_MINUTE);
	}

	public RetryChunkLaterException(String theCode, Duration theDuration) {
		super(theCode);
		this.myNextPollDuration = theDuration;
	}

	public Duration getNextPollDuration() {
		return myNextPollDuration;
	}

	/**
	 * Calculates an exponential backoff delay based on the current error count.
	 * Formula: baseDelay * 2^(errorCount - 1), capped at maxDelay.
	 *
	 * @param theErrorCount current error count (1-based)
	 * @param theBaseDelay base delay for first retry
	 * @param theMaxDelay maximum delay cap
	 * @return calculated delay with exponential backoff
	 */
	public static Duration calculateExponentialBackoff(int theErrorCount, Duration theBaseDelay, Duration theMaxDelay) {
		if (theErrorCount <= 0) {
			return theBaseDelay;
		}
		long multiplier = 1L << (theErrorCount - 1);
		long delayMillis = theBaseDelay.toMillis() * multiplier;
		long maxMillis = theMaxDelay.toMillis();
		return Duration.ofMillis(Math.min(delayMillis, maxMillis));
	}

	/**
	 * Creates a RetryChunkLaterException with exponential backoff based on error count.
	 *
	 * @param theErrorCount current error count (1-based)
	 * @param theBaseDelay base delay for first retry (default: 1 second)
	 * @param theMaxDelay maximum delay cap (default: 5 minutes)
	 * @return new RetryChunkLaterException with calculated delay
	 */
	public static RetryChunkLaterException withExponentialBackoff(
			int theErrorCount, Duration theBaseDelay, Duration theMaxDelay) {
		Duration delay = calculateExponentialBackoff(theErrorCount, theBaseDelay, theMaxDelay);
		return new RetryChunkLaterException(delay);
	}

	/**
	 * Creates a RetryChunkLaterException with exponential backoff using sensible defaults.
	 * Base delay: 1 second, Max delay: 5 minutes.
	 *
	 * @param theErrorCount current error count (1-based)
	 * @return new RetryChunkLaterException with calculated delay
	 */
	public static RetryChunkLaterException withExponentialBackoff(int theErrorCount) {
		return withExponentialBackoff(theErrorCount, Duration.ofSeconds(1), Duration.ofMinutes(5));
	}
}
