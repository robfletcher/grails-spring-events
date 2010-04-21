package grails.plugin.asyncevents

import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationEvent
import java.util.concurrent.*
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

class EventPublisherService implements InitializingBean, DisposableBean {

	static transactional = false

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
					log.warn "Notifying listener $listener failed. Will retry in $listener.retryDelay $MILLISECONDS"
					retryExecutor.schedule(this.&publishEvent.curry(event), listener.retryDelay, MILLISECONDS)
					log.info "Event scheduled for re-execution"
				}
			} catch (Exception e) {
				log.error "Notififying listener $listener failed.", e
			}
		}
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