package grails.plugin.asyncevents

import org.springframework.context.ApplicationListener

interface RetryableApplicationListener extends ApplicationListener {

	RetryPolicy getRetryPolicy()

}