/*
 * Copyright 2010 Robert Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springevents

import java.util.concurrent.CountDownLatch
import org.codehaus.groovy.grails.plugin.springevents.TooManyRetriesException
import org.codehaus.groovy.grails.plugin.springevents.test.DummyEvent
import org.codehaus.groovy.grails.plugin.springevents.test.ExceptionTrap
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import static grails.plugin.springevents.RetryPolicy.DEFAULT_BACKOFF_MULTIPLIER
import static grails.test.MockUtils.mockLogging
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.codehaus.groovy.grails.plugin.springevents.test.AsynchronousAssertions.waitFor
import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue
import org.codehaus.groovy.grails.plugin.springevents.NoRetryPolicyDefinedException

class GrailsApplicationEventMulticasterTests {

	static final long RETRY_DELAY_MILLIS = 250

	GrailsApplicationEventMulticaster multicaster = new GrailsApplicationEventMulticaster()
	ApplicationEvent event = new DummyEvent()

	@Test
	void shutsDownCleanly() {
		multicaster.destroy()
		assertTrue "Worker thread has not stopped", multicaster.taskExecutor.awaitTermination(1, SECONDS)
		assertTrue "Worker thread has not stopped", multicaster.retryScheduler.awaitTermination(1, SECONDS)
	}

	@Test
	void publishesEventToAllListeners() {
		int numListeners = 2
		def latch = new CountDownLatch(numListeners)

		numListeners.times { i ->
			multicaster.addApplicationListener new CountingListener(latch)
		}

		multicaster.multicastEvent(event)

		waitFor "all listeners to be notified", latch
	}

	@Test
	void notifiesListenersOnASeparateThread() {
		def latch = new CountDownLatch(1)
		def listener = new ThreadRecordingListener(latch)

		multicaster.addApplicationListener listener

		multicaster.multicastEvent(event)

		waitFor "all listeners to be notified", latch
		assertThat "thread used for listener notification", listener.thread, not(sameInstance(Thread.currentThread()))
	}

	@Test
	void notifiesErrorHandlerOfListenerException() {
		def latch = new CountDownLatch(1)
		def exception = new RuntimeException("Event listener fail")
		def errorHandler = new ExceptionTrap(latch)

		multicaster.errorHandler = errorHandler
		multicaster.addApplicationListener({ApplicationEvent e -> throw exception} as ApplicationListener)

		multicaster.multicastEvent(event)

		waitFor "error handler to be called", latch
		assertThat "Exception passed to error handler", errorHandler.handledError, sameInstance(exception)
	}

	@Test
	void survivesListenerException() {
		def latch = new CountDownLatch(2)

		multicaster.addApplicationListener new ExceptionThrowingListener(latch)
		multicaster.addApplicationListener new CountingListener(latch)

		multicaster.multicastEvent(event)

		waitFor "all listeners to be notified", latch
	}

	@Test
	void retriesAfterDelayIfListenerThrowsRetryableFailureException() {
		def latch = new CountDownLatch(2)

		multicaster.addApplicationListener new FailingListener(latch, 1)

		multicaster.multicastEvent(event)

		// wait for some time after which the retry should not have occurred
		MILLISECONDS.sleep(RETRY_DELAY_MILLIS - 50)
		assertThat "Number of notifications still expected", latch.count, equalTo(1L)

		waitFor "listener to be notified", latch
	}

	@Test
	void retriesBackOffAccordingToListenersMultiplier() {
		def latch = new CountDownLatch(4)

		multicaster.addApplicationListener new FailingListener(latch, 3)

		multicaster.multicastEvent(event)

		// wait for some time after which the first retry should not have occurred
		MILLISECONDS.sleep(RETRY_DELAY_MILLIS - 50)
		assertThat "Number of notifications still expected", latch.count, equalTo(3L)

		// wait for some time after which the second retry should not have occurred
		MILLISECONDS.sleep(DEFAULT_BACKOFF_MULTIPLIER * RETRY_DELAY_MILLIS)
		assertThat "Number of notifications still expected", latch.count, equalTo(2L)

		// wait for some time after which the third retry should not have occurred
		MILLISECONDS.sleep(2 * DEFAULT_BACKOFF_MULTIPLIER * RETRY_DELAY_MILLIS)
		assertThat "Number of notifications still expected", latch.count, equalTo(1L)

		waitFor "listener to be notified", latch
	}

	@Test
	void notifiesErrorHandlerIfRetriedTooManyTimes() {
		def listenerLatch = new CountDownLatch(2)
		def errorHandlerLatch = new CountDownLatch(1)

		def listener = new FailingListener(listenerLatch, 2)
		listener.retryPolicy.maxRetries = 1
		multicaster.addApplicationListener listener

		def errorHandler = new ExceptionTrap(errorHandlerLatch)
		multicaster.errorHandler = errorHandler

		multicaster.multicastEvent(event)

		waitFor "listener to be notified", listenerLatch, DEFAULT_BACKOFF_MULTIPLIER * RETRY_DELAY_MILLIS
		waitFor "error handler to be called", errorHandlerLatch
		assertThat "Exception passed to error handler", errorHandler.handledError, instanceOf(TooManyRetriesException)
		assertThat "Event attached to exception", errorHandler.handledError.event, sameInstance(event)
	}

	@Test
	void doesNotRetryIfListenerDoesNotDefineRetryPolicy() {
		def listenerLatch = new CountDownLatch(1)
		def errorHandlerLatch = new CountDownLatch(1)

		multicaster.addApplicationListener new ExceptionThrowingListener(listenerLatch, new RetryableFailureException())

		def errorHandler = new ExceptionTrap(errorHandlerLatch)
		multicaster.errorHandler = errorHandler

		multicaster.multicastEvent(event)

		waitFor "listener to be notified", listenerLatch
		waitFor "error handler to be called", errorHandlerLatch
		assertThat "Exception passed to error handler", errorHandler.handledError, instanceOf(NoRetryPolicyDefinedException)
	}

	@BeforeClass
	static void enableLogging() {
		mockLogging GrailsApplicationEventMulticaster, true
	}

	@Before
	void initialiseMulticaster() {
		multicaster.afterPropertiesSet()
	}

	@After
	void destroyMulticaster() {
		multicaster.destroy()
	}

}

class CountingListener implements ApplicationListener {

	private final CountDownLatch latch

	CountingListener(CountDownLatch latch) {
		this.latch = latch
	}

	void onApplicationEvent(ApplicationEvent e) {
		latch.countDown()
	}
}

class ThreadRecordingListener extends CountingListener {

	Thread thread

	ThreadRecordingListener(CountDownLatch latch) {
		super(latch)
	}

	void onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		thread = Thread.currentThread()
	}
}

class ExceptionThrowingListener extends CountingListener {

	private final Exception exception

	ExceptionThrowingListener(CountDownLatch latch) {
		this(latch, new RuntimeException("Event listener fail"))
	}

	ExceptionThrowingListener(CountDownLatch latch, Exception exception) {
		super(latch)
		this.exception = exception
	}

	void onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		throw exception
	}
}

class FailingListener extends CountingListener {

	final RetryPolicy retryPolicy = new RetryPolicy(initialRetryDelayMillis: GrailsApplicationEventMulticasterTests.RETRY_DELAY_MILLIS)
	private final Exception exception
	private int failThisManyTimes = 1

	FailingListener(CountDownLatch latch, int failThisManyTimes) {
		this(latch, new RetryableFailureException("Event listener fail"), failThisManyTimes)
	}

	FailingListener(CountDownLatch latch, Exception exception, int failThisManyTimes) {
		super(latch)
		this.exception = exception
		this.failThisManyTimes = failThisManyTimes
	}

	void onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		if(shouldFail()) {
			throw exception
		}
	}

	boolean shouldFail() {
		def fail = failThisManyTimes > 0
		failThisManyTimes--
		return fail
	}
}
