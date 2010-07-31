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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.AbstractApplicationEventMulticaster
import org.springframework.orm.hibernate3.SessionFactoryUtils
import org.springframework.orm.hibernate3.SessionHolder
import org.springframework.scheduling.support.TaskUtils
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.ErrorHandler
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import org.codehaus.groovy.grails.plugin.springevents.ApplicationEventNotification
import org.codehaus.groovy.grails.plugin.springevents.TooManyRetriesException
import org.codehaus.groovy.grails.plugin.springevents.NoRetryPolicyDefinedException

/**
 * An ApplicationEventMulticaster implementation that uses an ExecutorService to asynchronously notify listeners. The
 * implementation binds a Hibernate session to the notification thread so that listeners have full access to Grails
 * domain objects. Notifications can be re-attempted if a listener throws RetryableFailureException. 
 */
class GrailsApplicationEventMulticaster extends AbstractApplicationEventMulticaster implements InitializingBean, DisposableBean {

	ExecutorService taskExecutor
	ScheduledExecutorService retryScheduler
	ErrorHandler errorHandler
	SessionFactory sessionFactory

	private final Logger log = LoggerFactory.getLogger(GrailsApplicationEventMulticaster)

	GrailsApplicationEventMulticaster() { }

	GrailsApplicationEventMulticaster(BeanFactory beanFactory) {
		setBeanFactory(beanFactory)
	}

	void multicastEvent(ApplicationEvent event) {
		getApplicationListeners(event).each { ApplicationListener listener ->
			def notification = new ApplicationEventNotification(listener, event)
			taskExecutor.execute {
				withHibernateSession {
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

	private void withHibernateSession(Closure closure) {
		if (sessionFactory) {
			def session = SessionFactoryUtils.getSession(sessionFactory, true)
			session.flushMode = FlushMode.AUTO
			TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))
			if (log.isDebugEnabled()) log.debug "Bound session to thread"
		}
		try {
			closure()
		} finally {
			if (sessionFactory) {
				SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
				if (sessionHolder.session.flushMode != FlushMode.MANUAL) {
					sessionHolder.session.flush()
				}
				TransactionSynchronizationManager.unbindResource(sessionFactory)
				SessionFactoryUtils.closeSession(sessionHolder.session)
				if (log.isDebugEnabled()) log.debug "Unbound session"
			}
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
