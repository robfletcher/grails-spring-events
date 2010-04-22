package com.energizedwork.grails.plugin.asyncevents

interface Retryable {
	void incrementRetryCount()

	long getRetryDelayMillis()

	boolean shouldRetry()
}
