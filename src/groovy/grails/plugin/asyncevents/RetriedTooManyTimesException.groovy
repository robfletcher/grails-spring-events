package grails.plugin.asyncevents

import org.springframework.context.ApplicationEvent
import com.energizedwork.grails.plugin.asyncevents.RetryableNotification

class RetriedTooManyTimesException extends RuntimeException {

	final ApplicationEvent event

	RetriedTooManyTimesException(RetryableNotification retryableEvent) {
		super("Exceeded maximum retries of $retryableEvent.retryCount when trying to notify listener $retryableEvent.target" as String)
		this.event = retryableEvent.event
	}
}