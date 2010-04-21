package grails.plugin.asyncevents

import org.springframework.context.ApplicationEvent

interface AsyncEventListener {

	static final int UNLIMITED_RETRIES = -1

	boolean onApplicationEvent(ApplicationEvent event)

	long getRetryDelay()

	int getMaxRetries()

}