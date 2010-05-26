package grails.plugin.asyncevents

import java.util.concurrent.CountDownLatch
import org.codehaus.groovy.grails.plugin.asyncevents.test.Album
import org.codehaus.groovy.grails.plugin.asyncevents.test.DummyEvent
import org.codehaus.groovy.grails.plugin.asyncevents.test.ExceptionTrap
import org.codehaus.groovy.grails.plugin.asyncevents.test.Song
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static org.hamcrest.CoreMatchers.notNullValue
import org.junit.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail
import static org.junit.matchers.JUnitMatchers.hasItems
import java.util.concurrent.TimeUnit

class ListenerHibernateTests {

	static transactional = false

	def applicationEventMulticaster
	ExceptionTrap exceptionTrap = new ExceptionTrap()
	ClosureListener listener

	@BeforeClass
	static void setUpSampleData() {
		Album.withNewSession {
			def album = new Album(artist: "The Hold Steady", name: "Heaven Is Whenever")
			["The Sweet Part of the City", "Soft In the Center", "The Weekenders", "The Smidge", "Rock Problems", "We Can Get Together", "Hurricane J", "Barely Breathing", "Our Whole Lives"].each {
				album.tracks << new Song(name: it)
			}
			album.save(flush: true, failOnError: true)
		}
	}

	@AfterClass
	static void tearDownSampleData() {
		Album.withNewSession { session ->
			Album.list()*.delete()
			session.flush()
		}
	}

	@Before
	void setUpListener() {
		listener = new ClosureListener()
		applicationEventMulticaster.addApplicationListener(listener)
	}

	@Before
	void setUpExceptionTrap() {
		applicationEventMulticaster.errorHandler = exceptionTrap
	}

	@After
	void removeAllListeners() {
		applicationEventMulticaster.removeAllListeners()
	}

	@Test
	void listenersCanLookupDomainObjects() {
		def domainObjectInstance = null
		listener.onEvent = { ApplicationEvent event ->
			domainObjectInstance = Album.findByArtist("The Hold Steady")
		}

		applicationEventMulticaster.multicastEvent(new DummyEvent())

		waitForListenerToComplete()
		assertThat "domain object", domainObjectInstance, notNullValue()
	}

	@Test
	void listenersCanAttachDetatchedDomainObjects() {
		listener.onEvent = { DomainEvent event ->
			def domainObjectInstance = event.domainObjectInstance
			domainObjectInstance.attach()
			if (!domainObjectInstance.isAttached()) {
				throw new IllegalStateException("Could not attach domain instance to the session")
			}
		}

		def domainObjectInstance = Album.findByArtist("The Hold Steady")
		domainObjectInstance.discard()
		applicationEventMulticaster.multicastEvent(new DomainEvent(domainObjectInstance: domainObjectInstance))

		waitForListenerToComplete()
		assertThat "domain object", domainObjectInstance, notNullValue()
	}

	@Test
	void listenersCanLazyLoadDomainObjectProperties() {
		def songNames = null
		listener.onEvent = { ApplicationEvent event ->
			def domainObjectInstance = Album.findByArtist("The Hold Steady")
			songNames = domainObjectInstance.tracks.name
		}

		applicationEventMulticaster.multicastEvent(new DummyEvent())

		waitForListenerToComplete()
		assertThat "lazy loaded domain association", songNames, hasItems("The Sweet Part of the City", "Soft In the Center", "The Weekenders")
	}

	void waitForListenerToComplete() {
		if (!listener.awaitCompletion(250, MILLISECONDS)) {
			if (exceptionTrap.handledError) {
				fail "Listener threw exception $exceptionTrap.handledError"
			} else {
				fail "Timed out waiting for listener to complete"
			}
		}
	}

}

class DomainEvent extends DummyEvent {
	Album domainObjectInstance
}

class ClosureListener implements ApplicationListener {

	private final CountDownLatch latch
	Closure onEvent

	ClosureListener() {
		this.latch = new CountDownLatch(1)
	}

	void onApplicationEvent(ApplicationEvent event) {
		onEvent(event)
		latch.countDown()
	}

	boolean awaitCompletion(long timeout, TimeUnit timeUnit) {
		return latch.await(timeout, timeUnit)
	}
}