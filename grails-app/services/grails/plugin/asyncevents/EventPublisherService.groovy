package grails.plugin.asyncevents

import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationEvent
import org.springframework.util.ErrorHandler
import java.util.concurrent.*
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

class EventPublisherService implements InitializingBean, DisposableBean {

	static transactional = false

	ErrorHandler errorHandler
	
	private final Collection<AsyncEventListener> listeners = []
	private final ExecutorService executor = Executors.newSingleThreadExecutor()
	private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor()
	private final BlockingQueue<ApplicationEvent> queue = new LinkedBlockingQueue<ApplicationEvent>()
	private boolean done = false

	void addListener(AsyncEventListener listener) {
		listeners << listener
	}

	void publishEvent(ApplicationEvent event) {
		queue.put(event)
	}

	void afterPropertiesSet() {
		executor.execute {
			while (!done) {
				log.debug "polling queue..."
				ApplicationEvent event = queue.poll(250, MILLISECONDS)
				if (event) {
					log.info "got event $event from queue"
					notifyListeners(event)
				}
			}
			log.warn "event notifier thread exiting..."
		}
	}

	void destroy() {
		done = true
		shutdown(executor, 1, SECONDS)
		shutdown(retryExecutor, 1, SECONDS)
	}

	private void notifyListeners(ApplicationEvent event) {
		listeners.each {AsyncEventListener listener ->
			try {
				def success = listener.onApplicationEvent(event)
				if (!success) {
					rescheduleNotification(listener, event)
				}
			} catch (Exception e) {
				log.error "Notififying listener $listener failed.", e
				errorHandler?.handleError(e)
			}
		}
	}

	private void rescheduleNotification(AsyncEventListener listener, ApplicationEvent event) {
		int retryCount = event instanceof RetryCountingEventDecorator ? event.retryCount : 0
		long retryDelay = calculateRetryCount(listener, retryCount)
		log.warn "Notifying listener $listener failed. Will retry in $retryDelay $MILLISECONDS"
		def originalEvent = event instanceof RetryCountingEventDecorator ? event.originalEvent : event
		def retryEvent = new RetryCountingEventDecorator(this, originalEvent, retryCount + 1)
		retryExecutor.schedule(this.&publishEvent.curry(retryEvent), retryDelay, MILLISECONDS)
	}

	private long calculateRetryCount(AsyncEventListener listener, int retryCount) {
		long retryDelay = listener.retryDelay
		retryCount.times {
			retryDelay *= 2
		}
		return retryDelay
	}

	private void shutdown(ExecutorService executor, int timeout, TimeUnit unit) {
		executor.shutdown()
		if (!executor.awaitTermination(timeout, unit)) {
			log.warn "Executor still alive $timeout $unit after shutdown, forcing..."
			executor.shutdownNow()
			assert executor.awaitTermination(timeout, unit), "Forced shutdown of executor incomplete after $timeout $unit."
		}
	}
}

class RetryCountingEventDecorator extends ApplicationEvent {

	final ApplicationEvent originalEvent
	final int retryCount

	def RetryCountingEventDecorator(Object source, ApplicationEvent originalEvent, int retryCount) {
		super(source)
		this.originalEvent = originalEvent
		this.retryCount = retryCount
	}
}