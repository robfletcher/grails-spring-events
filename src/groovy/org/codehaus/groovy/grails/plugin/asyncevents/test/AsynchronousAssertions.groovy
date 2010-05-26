package org.codehaus.groovy.grails.plugin.asyncevents.test

import java.util.concurrent.CountDownLatch
import org.junit.Assert
import static java.util.concurrent.TimeUnit.MILLISECONDS

class AsynchronousAssertions {

	static final long DEFAULT_TIMEOUT = 250L

	static void waitFor(String message, CountDownLatch latch, long timeout = DEFAULT_TIMEOUT) {
		if (!latch.await(timeout, MILLISECONDS)) {
			Assert.fail "Timed out waiting for $message, expecting $latch.count more events"
		}
	}

}
