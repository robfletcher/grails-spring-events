package org.codehaus.groovy.grails.plugin.asyncevents.test

import org.springframework.util.ErrorHandler
import java.util.concurrent.CountDownLatch

class ExceptionTrap implements ErrorHandler {

	private final CountDownLatch latch
	Throwable handledError

	ExceptionTrap() { }

	ExceptionTrap(CountDownLatch latch) {
		this.latch = latch
	}

	void handleError(Throwable t) {
		handledError = t
		latch?.countDown()
	}
}
