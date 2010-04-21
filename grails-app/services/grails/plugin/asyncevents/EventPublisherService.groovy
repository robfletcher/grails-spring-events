package grails.plugin.asyncevents

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import static java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.ExecutorService

class EventPublisherService implements InitializingBean, DisposableBean {

	static transactional = false

	private final Collection<ApplicationListener> listeners = []
	private final ExecutorService executor = Executors.newSingleThreadExecutor()
	private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor()
	private final BlockingQueue<ApplicationEvent> queue = new LinkedBlockingQueue<ApplicationEvent>()
	private boolean done = false

	void addListener(ApplicationListener listener) {
		listeners << listener
	}

	void publishEvent(ApplicationEvent event) {
		queue.put(event)
	}

	void afterPropertiesSet() {
		executor.execute {
			while (!done) {
				log.debug "polling queue..."
				ApplicationEvent event = queue.poll(1, SECONDS)
				if (event) {
					println "got event $event from queue"
					notifyListeners(event)
				}
			}
			println "event notifier thread exiting..."
		}
	}

	void destroy() {
		done = true
		executor.shutdown()
		retryExecutor.shutdown()
	}

	private void notifyListeners(ApplicationEvent event) {
		listeners.each {ApplicationListener listener ->
			try {
				listener.onApplicationEvent(event)
			} catch (RetryLaterException e) {
				log.warn "Notifying listener $listener failed. Will retry in $e.delay $e.delayUnit"
				retryExecutor.schedule(this.&publishEvent.curry(event), e.delay, e.delayUnit)
				log.info "Event scheduled for re-execution"
			} catch (Exception e) {
				log.error "Notififying listener $listener failed.", e
			}
		}
	}

	ExecutorService getExecutor() { executor }
	ScheduledExecutorService getRetryExecutor() { retryExecutor }

}