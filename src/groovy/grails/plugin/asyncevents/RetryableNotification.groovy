package grails.plugin.asyncevents

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

class RetryableNotification {

	private int retryCount = 0
	final ApplicationListener target
	final ApplicationEvent event

	RetryableNotification(ApplicationListener target, ApplicationEvent event) {
		this.target = target
		this.event = event
	}

	boolean tryNotifyingListener() {
		try {
			target.onApplicationEvent(event)
			return true
		} catch (RetryableFailureException e) {
			return false
		}
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
		if (target instanceof RetryableApplicationListener) {
			return target.retryPolicy.retryIndefinitely() || retryCount < target.retryPolicy.maxRetries
		} else {
			return false
		}
	}
}