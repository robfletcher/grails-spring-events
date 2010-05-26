package grails.plugin.asyncevents

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
