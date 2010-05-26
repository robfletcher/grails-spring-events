package grails.plugin.asyncevents

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

class SessionAwareMulticaster extends AbstractApplicationEventMulticaster implements InitializingBean, DisposableBean {

	ExecutorService taskExecutor
	ScheduledExecutorService retryScheduler
	ErrorHandler errorHandler
	SessionFactory sessionFactory

	private final Logger log = LoggerFactory.getLogger(SessionAwareMulticaster)

	SessionAwareMulticaster() { }

	SessionAwareMulticaster(BeanFactory beanFactory) {
		setBeanFactory(beanFactory)
	}

	void multicastEvent(ApplicationEvent event) {
		getApplicationListeners(event).each { ApplicationListener listener ->
			def retryableEvent = new RetryableNotification(listener, event)
			taskExecutor.execute {
				withHibernateSession {
					notifyListener retryableEvent
				}
			}
		}
	}

	protected void notifyListener(RetryableNotification event) {
		// TODO: this method has to be protected due to http://jira.codehaus.org/browse/GROOVY-4170
		try {
			def success = event.tryNotifyingListener()
			if (!success) {
				rescheduleNotification(event)
			}
		} catch (e) {
			errorHandler?.handleError(e)
		}
	}

	private void withHibernateSession(Closure closure) {
		def session = SessionFactoryUtils.getSession(sessionFactory, true)
		session.flushMode = FlushMode.AUTO
		TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))
		if (log.isDebugEnabled()) log.debug "Bound session to thread"
		try {
			closure()
		} finally {
			SessionHolder sessionHolder = TransactionSynchronizationManager.unbindResource(sessionFactory)
			if (sessionHolder.session.flushMode != FlushMode.MANUAL) {
				sessionHolder.session.flush()
			}
			SessionFactoryUtils.closeSession(sessionHolder.session)
			if (log.isDebugEnabled()) log.debug "Unbound session"
		}
	}

	private void rescheduleNotification(RetryableNotification event) {
		if (event.shouldRetry()) {
			long retryDelay = event.retryDelayMillis
			log.warn "Notifying listener $event.target failed. Will retry in $retryDelay $MILLISECONDS"
			event.incrementRetryCount()
			retryScheduler.schedule(this.&notifyListener.curry(event), retryDelay, MILLISECONDS)
		} else {
			throw new RetriedTooManyTimesException(event)
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
