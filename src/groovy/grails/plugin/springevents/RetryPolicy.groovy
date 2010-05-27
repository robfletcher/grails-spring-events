/*
 * Copyright 2010 Robert Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springevents

import java.util.concurrent.TimeUnit

final class RetryPolicy {

	static final int UNLIMITED_RETRIES = -1
	static final int DEFAULT_RETRIES = 3
	static final long DEFAULT_RETRY_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1)
	static final int DEFAULT_BACKOFF_MULTIPLIER = 2

	int maxRetries = DEFAULT_RETRIES
	long initialRetryDelayMillis = DEFAULT_RETRY_DELAY_MILLIS
	int backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER

	boolean retryIndefinitely() {
		maxRetries == UNLIMITED_RETRIES
	}

}
