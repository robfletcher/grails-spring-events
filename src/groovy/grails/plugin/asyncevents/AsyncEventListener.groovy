package grails.plugin.asyncevents

import org.springframework.context.ApplicationEvent

interface AsyncEventListener {

	boolean onApplicationEvent(ApplicationEvent event)

	RetryPolicy getRetryPolicy()

}