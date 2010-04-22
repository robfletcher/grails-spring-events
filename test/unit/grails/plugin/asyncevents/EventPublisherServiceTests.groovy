package grails.plugin.asyncevents

import grails.test.MockUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.springframework.context.ApplicationEvent
import org.springframework.util.ErrorHandler
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.*

class EventPublisherServiceTests {

	static final long RETRY_DELAY_MILLIS = 250
	static final int BACKOFF_MULTIPLIER = 2

	EventPublisherService service = new EventPublisherService()
	ApplicationEvent event = new DummyEvent()

	@Test
	void shutsDownCleanly() {
		service.destroy()
		assertTrue "Worker thread has not stopped", service.executor.awaitTermination(1, SECONDS)
	}

	@Test
	void publishesEventToAllListeners() {
		int numListeners = 2
		def latch = new CountDownLatch(numListeners)

		numListeners.times {
			service.addListener new CountingListener(latch)
		}

		service.publishEvent(event)

		waitFor "all listeners to be notified", latch
	}

	@Test
	void notifiesListenersOnASeparateThread() {
		def latch = new CountDownLatch(1)
		def listener = new ThreadRecordingListener(latch)

		service.addListener listener

		service.publishEvent(event)

		waitFor "all listeners to be notified", latch
		assertThat "thread used for listener notification", listener.thread, not(sameInstance(Thread.currentThread()))
	}

	@Test
	void notifiesErrorHandlerOfListenerException() {
		def latch = new CountDownLatch(1)
		def exception = new RuntimeException("Event listener fail")
		def errorHandler = new ExceptionTrap(latch)

		service.errorHandler = errorHandler
		service.addListener({ApplicationEvent e -> throw exception} as AsyncEventListener)

		service.publishEvent(event)

		waitFor "error handler to be called", latch
		assertThat "Exception passed to error handler", errorHandler.handledError, sameInstance(exception)
	}

	@Test
	void survivesListenerException() {
		def latch = new CountDownLatch(2)

		service.addListener new ExceptionThrowingListener(latch)
		service.addListener new CountingListener(latch)

		service.publishEvent(event)

		waitFor "all listeners to be notified", latch
	}

	@Test
	void retriesAfterDelayIfListenerReturnsFalse() {
		def latch = new CountDownLatch(2)

		service.addListener new FailingListener(latch, 1)

		service.publishEvent(event)

		// wait for some time after which the retry should not have occurred
		MILLISECONDS.sleep(RETRY_DELAY_MILLIS - 50)
		assertThat "Number of notifications still expected", latch.count, equalTo(1L)

		waitFor "listener to be notified", latch
	}

	@Test
	void retriesBackOffAccordingToListenersMultiplier() {
		def latch = new CountDownLatch(4)

		def listener = new FailingListener(latch, 3)
		listener.backoffMultiplier = 2
		service.addListener listener

		service.publishEvent(event)

		// wait for some time after which the first retry should not have occurred
		MILLISECONDS.sleep(RETRY_DELAY_MILLIS - 50)
		assertThat "Number of notifications still expected", latch.count, equalTo(3L)

		// wait for some time after which the second retry should not have occurred
		MILLISECONDS.sleep(2 * RETRY_DELAY_MILLIS)
		assertThat "Number of notifications still expected", latch.count, equalTo(2L)

		// wait for some time after which the third retry should not have occurred
		MILLISECONDS.sleep(4 * RETRY_DELAY_MILLIS)
		assertThat "Number of notifications still expected", latch.count, equalTo(1L)

		waitFor "listener to be notified", latch
	}

	@Test
	void notifiesErrorHandlerIfRetriedTooManyTimes() {
		def listenerLatch = new CountDownLatch(2)
		def errorHandlerLatch = new CountDownLatch(1)

		def listener = new FailingListener(listenerLatch, 2)
		listener.maxRetries = 1
		service.addListener listener

		def errorHandler = new ExceptionTrap(errorHandlerLatch)
		service.errorHandler = errorHandler

		service.publishEvent(event)

		waitFor "listener to be notified", listenerLatch, 2 * RETRY_DELAY_MILLIS, MILLISECONDS
		waitFor "error handler to be called", errorHandlerLatch
		assertThat "Exception passed to error handler", errorHandler.handledError, instanceOf(RetriedTooManyTimesException)
		assertThat "Event attached to exception", errorHandler.handledError.event, sameInstance(event)
	}

	private void waitFor(String message, CountDownLatch latch, long timeout = RETRY_DELAY_MILLIS, TimeUnit unit = MILLISECONDS) {
		if (!latch.await(timeout, unit)) {
			fail "Timed out waiting for $message, expecting $latch.count more calls"
		}
	}

	@BeforeClass
	static void enableLogging() {
		MockUtils.mockLogging EventPublisherService, true
	}

	@Before
	void initialiseService() {
		service.afterPropertiesSet()
	}

	@After
	void stopExecutors() {
		service.destroy()
	}

}

class DummyEvent extends ApplicationEvent {
	static final DUMMY_EVENT_SOURCE = new Object()

	DummyEvent() {
		super(DUMMY_EVENT_SOURCE)
	}
}

class CountingListener implements AsyncEventListener {

	private final CountDownLatch latch
	int maxRetries = UNLIMITED_RETRIES
	int backoffMultiplier = EventPublisherServiceTests.BACKOFF_MULTIPLIER

	CountingListener(CountDownLatch latch) {
		this.latch = latch
	}

	boolean onApplicationEvent(ApplicationEvent e) {
		latch.countDown()
		return true
	}

	long getRetryDelay() {
		return EventPublisherServiceTests.RETRY_DELAY_MILLIS
	}
}

class ThreadRecordingListener extends CountingListener {

	Thread thread

	ThreadRecordingListener(CountDownLatch latch) {
		super(latch)
	}

	boolean onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		thread = Thread.currentThread()
		return true
	}
}

class ExceptionThrowingListener extends CountingListener {

	private final Exception exception

	def ExceptionThrowingListener(CountDownLatch latch) {
		this(latch, new RuntimeException("Event listener fail"))
	}

	ExceptionThrowingListener(CountDownLatch latch, Exception exception) {
		super(latch)
		this.exception = exception
	}

	boolean onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		throw exception
	}
}

class FailingListener extends CountingListener {

	private int failThisManyTimes = 1

	FailingListener(CountDownLatch latch, int failThisManyTimes) {
		super(latch)
		this.failThisManyTimes = failThisManyTimes
	}

	boolean onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		return !shouldFail()
	}

	boolean shouldFail() {
		def fail = failThisManyTimes > 0
		failThisManyTimes--
		return fail
	}
}

class ExceptionTrap implements ErrorHandler {

	private final CountDownLatch latch
	Throwable handledError

	ExceptionTrap(CountDownLatch latch) {
		this.latch = latch
	}

	void handleError(Throwable t) {
		handledError = t
		latch.countDown()
	}
}