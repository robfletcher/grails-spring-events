package grails.plugin.asyncevents

import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import static java.util.concurrent.TimeUnit.SECONDS
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.sameInstance
import static org.junit.Assert.*
import grails.test.MockUtils

class EventPublisherServiceTests {

	EventPublisherService service = new EventPublisherService()

	@Before
	void setUp() {
		MockUtils.mockLogging EventPublisherService, true
		service.afterPropertiesSet()
	}

	@After
	void tearDown() {
		service.destroy()
		if (!service.executor.isTerminated()) {
			println "forcing executor to shut down"
			service.executor.shutdownNow()
			assert service.executor.awaitTermination(1, SECONDS)
		}
		if (!service.retryExecutor.isTerminated()) {
			println "forcing retry executor to shut down"
			service.retryExecutor.shutdownNow()
			assert service.retryExecutor.awaitTermination(1, SECONDS)
		}
	}

	@Test
	void shutsDownCleanly() {
		service.destroy()

		assertTrue "Worker thread has not stopped after 5 seconds", service.executor.awaitTermination(5, SECONDS)
	}

	@Test
	void publishesEventToAllListeners() {
		int numListeners = 2
		def latch = new CountDownLatch(numListeners)
		def event = new MockEvent()

		numListeners.times {
			service.addListener new MockListener(latch)
		}

		service.publishEvent(event)

		waitForAllListenersToBeNotified(latch)
	}

	@Test
	void notifiesListenersOnASeparateThread() {
		def latch = new CountDownLatch(1)
		def event = new MockEvent()
		def listener = new ThreadRecordingListener(latch)

		service.addListener listener

		service.publishEvent(event)

		waitForAllListenersToBeNotified(latch)

		assertThat "thread used for listener notification", listener.thread, not(sameInstance(Thread.currentThread()))
	}

	@Test
	void survivesListenerException() {
		def latch = new CountDownLatch(2)
		def event = new MockEvent()

		service.addListener new FailingListener(latch)
		service.addListener new MockListener(latch)

		service.publishEvent(event)

		waitForAllListenersToBeNotified(latch)
	}

	@Test
	void retriesAfterDelayIfListenerThrowsRecoverableException() {
		def latch = new CountDownLatch(2)
		def event = new MockEvent()

		service.addListener new FailOnceListener(latch, new RetryLaterException(1, SECONDS))

		service.publishEvent(event)

		waitForAllListenersToBeNotified(latch)
	}

	private void waitForAllListenersToBeNotified(CountDownLatch latch) {
		if (!latch.await(5, SECONDS)) {
			fail "Timeout out waiting for event notifications. Still expecting $latch.count notifications"
		}
	}

}

class MockEvent extends ApplicationEvent {
	static final DUMMY_EVENT_SOURCE = new Object()

	MockEvent() {
		super(DUMMY_EVENT_SOURCE)
	}
}

class MockListener implements ApplicationListener {

	private final CountDownLatch latch

	MockListener(CountDownLatch latch) {
		this.latch = latch
	}

	void onApplicationEvent(ApplicationEvent e) {
		latch.countDown()
	}
}

class ThreadRecordingListener extends MockListener {

	Thread thread

	ThreadRecordingListener(CountDownLatch latch) {
		super(latch)
	}

	void onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		thread = Thread.currentThread()
	}
}

class FailingListener extends MockListener {

	private final Exception exception

	def FailingListener(CountDownLatch latch) {
		this(latch, new RuntimeException("Event listener fail"))
	}

	FailingListener(CountDownLatch latch, Exception exception) {
		super(latch)
		this.exception = exception
	}

	void onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
		if (shouldFail()) {
			throw exception
		}
	}

	boolean shouldFail() {
		return true
	}

}

class FailOnceListener extends FailingListener {

	private int failThisManyTimes = 1

	FailOnceListener(CountDownLatch latch) {
		super(latch)
	}

	FailOnceListener(CountDownLatch latch, Exception exception) {
		super(latch, exception)
	}

	void onApplicationEvent(ApplicationEvent e) {
		super.onApplicationEvent(e)
	}

	boolean shouldFail() {
		def fail = failThisManyTimes > 0
		failThisManyTimes--
		return fail
	}


}