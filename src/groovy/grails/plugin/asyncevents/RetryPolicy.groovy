package grails.plugin.asyncevents

final class RetryPolicy {

	static final int UNLIMITED_RETRIES = -1
	static final int DEFAULT_BACKOFF_MULTIPLIER = 2

	int maxRetries = UNLIMITED_RETRIES
	long initialRetryDelayMillis
	int backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER

	boolean retryIndefinitely() {
		maxRetries == UNLIMITED_RETRIES
	}

}
