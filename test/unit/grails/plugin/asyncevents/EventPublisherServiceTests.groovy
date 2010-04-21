package grails.plugin.asyncevents

import grails.test.MockUtils
import java.util.concurrent.CountDownLatch
import org.gmock.WithGMock
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.springframework.context.ApplicationEvent
import org.springframework.util.ErrorHandler
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

@WithGMock
class EventPublisherServiceTests {

	static final long RETRY_DELAY_MILLIS = 250

	EventPublisherService service = new EventPublisherService()

	@Test(timeout = 1000L)
	void shutsDownCleanly() {
		service.destroy()
		assertTrue "Worker thread has not stopped", service.executor.awaitTermination(1, SECONDS)
	}

	@Test(timeout = 1000L)
	void publishesEventToAllListeners() {
		int numListeners = 2
		def latch = new CountDownLatch(numListeners)
		def event = new MockEvent()

		numListeners.times {
			service.addListener new MockListener(latch)
		}

		service.publishEvent(event)

		latch.await()
	}

	@Test(timeout = 1000L)
	void notifiesListenersOnASeparateThread() {
		def latch = new CountDownLatch(1)
		def event = new MockEvent()
		def listener = new ThreadRecordingListener(latch)

		service.addListener listener

		service.publishEvent(event)

		latch.await()

		assertThat "thread used for listener notification", listener.thread, not(sameInstance(Thread.currentThread()))
	}

	@Test(timeout = 1500L)
	void notifiesErrorHandlerOfListenerException() {
		def latch = new CountDownLatch(1)
		def event = new MockEvent()
		def exception = new RuntimeException("Event listener fail")

		service.errorHandler = mock(ErrorHandler) {
			handleError(exception)
		}
		service.addListener new ExceptionThrowingListener(latch, exception)

		play {
			service.publishEvent(event)
			latch.await()
		}
	}

	@Test(timeout = 1000L)
	void survivesListenerException() {
		def latch = new CountDownLatch(2)
		def event = new MockEvent()

		service.addListener new ExceptionThrowingListener(latch)
		service.addListener new MockListener(latch)

		service.publishEvent(event)

		latch.await()
	}

	@Test(timeout = 1000L)
	void retriesAfterDelayIfListenerReturnsFalse() {
		def latch = new CountDownLatch(2)
		def event = new MockEvent()

		service.addListener new FailingListener(latch, 1)

		service.publishEvent(event)

		// wait for some time after which the retry should not have occurred
		MILLISECONDS.sleep(RETRY_DELAY_MILLIS - 50)
		assertThat "Number of notifications still expected", latch.count, equalTo(1L)

		latch.await()
	}

	@Test(timeout = 2000L)
	void retriesBackOffExponentially() {
		def latch = new CountDownLatch(4)
		def event = new MockEvent()

		service.addListener new FailingListener(latch, 3)

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

		latch.await()
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

class MockEvent extends ApplicationEvent {
	static final DUMMY_EVENT_SOURCE = new Object()

	MockEvent() {
		super(DUMMY_EVENT_SOURCE)
	}
}

class MockListener implements AsyncEventListener {

	private final CountDownLatch latch

	MockListener(CountDownLatch latch) {
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

class ThreadRecordingListener extends MockListener {

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

class ExceptionThrowingListener extends MockListener {

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

class FailingListener extends MockListener {

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