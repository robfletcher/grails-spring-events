package com.energizedwork.grails.plugin.asyncevents

import grails.plugin.asyncevents.AsyncEventListener
import org.springframework.context.ApplicationEvent

class RetryableNotification implements Retryable {

	private int retryCount = 0
	final AsyncEventListener target
	final ApplicationEvent event

	RetryableNotification(AsyncEventListener target, ApplicationEvent event) {
		this.target = target
		this.event = event
	}

	boolean tryNotifyingListener() {
		return target.onApplicationEvent(event)
	}

	void incrementRetryCount() {
		retryCount++
	}

	long getRetryDelayMillis() {
		long retryDelay = target.retryPolicy.initialRetryDelayMillis
		retryCount.times {
			retryDelay *= target.retryPolicy.backoffMultiplier
		}
		return retryDelay
	}

	boolean shouldRetry() {
		return target.retryPolicy.retryIndefinitely() || retryCount < target.retryPolicy.maxRetries
	}

}
