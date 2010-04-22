package grails.plugin.asyncevents

import com.energizedwork.grails.plugin.asyncevents.RetryableNotification
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.util.ErrorHandler
import java.util.concurrent.*
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

/**
 * A service that asynchronously publishes events to listeners.
 *
 * Each listener can signal whether it has successfully handled an event and if not, the policy to use to retry event
 * notification. Event notification is only retried on normal completion of the listener method, i.e. if the listener
 * throws any type of exception it will not be notified again about that event.
 *
 * @see AsyncEventListener
 */
class EventPublisherService implements ApplicationContextAware {

	static transactional = false

	ExecutorService eventProcessor
	ScheduledExecutorService retryScheduler
	ErrorHandler errorHandler
	ApplicationContext applicationContext

	private final Collection<AsyncEventListener> listeners = []
	private final BlockingQueue<RetryableNotification> queue = new LinkedBlockingQueue<RetryableNotification>()

	private boolean done = false

	/**
	 * Asynchronously publishes an event to all currently registered listeners.
	 */
	void publishEvent(ApplicationEvent event) {
		listeners.each { AsyncEventListener listener ->
			queue.put(new RetryableNotification(listener, event))
		}
	}

	@PostConstruct
	void autowireListeners() {
		applicationContext.getBeansOfType(AsyncEventListener).each { String name, AsyncEventListener listener ->
			log.debug "Autowiring listener $name"
			addListener(listener)
		}
	}

	@PostConstruct
	void start() {
		if (!eventProcessor) eventProcessor = Executors.newSingleThreadExecutor()
		if (!retryScheduler) retryScheduler = Executors.newSingleThreadScheduledExecutor()
		eventProcessor.execute {
			while (!done) {
				log.debug "polling queue..."
				RetryableNotification event = queue.poll(250, MILLISECONDS)
				if (event) {
					log.info "got event $event from queue"
					notifyListener(event)
				}
			}
			log.warn "event notifier thread exiting..."
		}
	}

	@PreDestroy
	void stop() {
		done = true
		shutdownExecutor(eventProcessor, 1, SECONDS)
		shutdownExecutor(retryScheduler, 1, SECONDS)
	}

	void addListener(AsyncEventListener listener) {
		listeners << listener
	}

	private void notifyListener(RetryableNotification event) {
		try {
			def success = event.tryNotifyingListener()
			if (!success) {
				rescheduleNotification(event)
			}
		} catch (Exception e) {
			log.error "Notififying listener $event.target failed", e
			errorHandler?.handleError(e)
		}
	}

	private void rescheduleNotification(RetryableNotification event) {
		log.warn "Notifying listener $event.target failed"
		if (event.shouldRetry()) {
			long retryDelay = event.retryDelayMillis
			log.warn "Will retry in $retryDelay $MILLISECONDS"
			event.incrementRetryCount()
			retryScheduler.schedule(this.&notifyListener.curry(event), retryDelay, MILLISECONDS)
		} else {
			throw new RetriedTooManyTimesException(event)
		}
	}

	private void shutdownExecutor(ExecutorService executor, int timeout, TimeUnit unit) {
		executor.shutdown()
		if (!executor.awaitTermination(timeout, unit)) {
			log.warn "Executor still alive $timeout $unit after shutdown, forcing..."
			executor.shutdownNow()
			assert executor.awaitTermination(timeout, unit), "Forced shutdown of executor incomplete after $timeout $unit."
		}
	}
}
