package grails.plugin.asyncevents

import org.springframework.context.ApplicationEvent

class RetriedTooManyTimesException extends RuntimeException {

	final ApplicationEvent event

	RetriedTooManyTimesException(int retryCount, AsyncEventListener listener, ApplicationEvent event) {
		super("Exceeded maximum retries of $retryCount when trying to notify listener $listener" as String)
		this.event = event
	}
}