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

import org.springframework.context.event.AbstractApplicationEventMulticaster
import org.springframework.scheduling.support.TaskUtils
import org.springframework.util.ErrorHandler
import java.util.concurrent.*
import static java.util.concurrent.TimeUnit.*
import org.codehaus.groovy.grails.plugin.springevents.*
import org.slf4j.*
import org.springframework.beans.factory.*
import org.springframework.context.*

/**
 * An ApplicationEventMulticaster implementation that uses an ExecutorService to asynchronously notify listeners. The
 * implementation binds a persistence session to the notification thread so that listeners have full access to Grails
 * domain objects. Notifications can be re-attempted if a listener throws RetryableFailureException.
 */
class GrailsApplicationEventMulticaster extends AbstractApplicationEventMulticaster implements InitializingBean, DisposableBean {

	ExecutorService taskExecutor
	ScheduledExecutorService retryScheduler
	ErrorHandler errorHandler
	def persistenceInterceptor

	private final Logger log = LoggerFactory.getLogger(GrailsApplicationEventMulticaster)

	GrailsApplicationEventMulticaster() { }

	GrailsApplicationEventMulticaster(BeanFactory beanFactory) {
		setBeanFactory(beanFactory)
	}

	void multicastEvent(ApplicationEvent event) {
		getApplicationListeners(event).each { ApplicationListener listener ->
			def notification = new ApplicationEventNotification(listener, event)
			taskExecutor.execute {
				withPersistenceSession {
					notifyListener notification
				}
			}
		}
	}

	protected void notifyListener(ApplicationEventNotification notification) {
		// TODO: this method has to be protected due to http://jira.codehaus.org/browse/GROOVY-4170
		try {
			try {
				notification.notifyListener()
			} catch (RetryableFailureException e) {
				rescheduleNotification(notification, e)
			}
		} catch (e) {
			errorHandler?.handleError(e)
		}
	}

	private void rescheduleNotification(ApplicationEventNotification notification, RetryableFailureException exception) {
		if (!notification.target.hasProperty("retryPolicy")) {
			throw new NoRetryPolicyDefinedException(notification, exception)
		} else if (notification.shouldRetry()) {
			long retryDelay = notification.retryDelayMillis
			log.warn "Notifying listener $notification.target failed. Will retry in $retryDelay $MILLISECONDS"
			notification.incrementRetryCount()
			retryScheduler.schedule(this.&notifyListener.curry(notification), retryDelay, MILLISECONDS)
		} else {
			throw new TooManyRetriesException(notification, exception)
		}
	}

	private void withPersistenceSession(Closure closure) {
		log.debug "Initializing PersistenceContextInterceptor ${persistenceInterceptor.getClass().name}"
		persistenceInterceptor?.init()
		try {
			closure()
		} finally {
			persistenceInterceptor?.flush()
			persistenceInterceptor?.destroy()
		}
	}

	void afterPropertiesSet() {
		if (!taskExecutor) taskExecutor = Executors.newSingleThreadExecutor()
		if (!retryScheduler) retryScheduler = Executors.newSingleThreadScheduledExecutor()
		if (!errorHandler) errorHandler = TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER
	}

	void destroy() {
		shutdownExecutor taskExecutor, 1, SECONDS
		shutdownExecutor retryScheduler, 1, SECONDS
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
