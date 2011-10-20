package grails.plugin.springevents

import spock.lang.Specification
import grails.plugin.spock.IntegrationSpec
import java.util.concurrent.*
import static java.util.concurrent.TimeUnit.MILLISECONDS
import org.codehaus.groovy.grails.plugin.springevents.test.*
import org.springframework.context.*

class ListenerGormPersistenceSpec extends IntegrationSpec {

	static transactional = false

	def asyncApplicationEventMulticaster
	ExceptionTrap exceptionTrap = new ExceptionTrap()
	ClosureListener listener

	def setup() {
		Album.withNewSession {
			def album = new Album(artist: "The Hold Steady", name: "Heaven Is Whenever")
			for (name in ["The Sweet Part of the City", "Soft In the Center", "The Weekenders", "The Smidge", "Rock Problems", "We Can Get Together", "Hurricane J", "Barely Breathing", "Our Whole Lives"]) {
				album.addToTracks(new Song(name: name))
			}
			album.save(flush: true, failOnError: true)
		}

		listener = new ClosureListener()
		asyncApplicationEventMulticaster.addApplicationListener(listener)
		asyncApplicationEventMulticaster.errorHandler = exceptionTrap
	}

	def cleanup() {
		asyncApplicationEventMulticaster.removeApplicationListener(listener)

		Album.withNewSession { session ->
			Album.list()*.delete()
			session.flush()
		}
	}

	def "listeners can lookup domain objects"() {
		given:
		def domainObjectInstance = null
		listener.onEvent = { ApplicationEvent event ->
			try {
			domainObjectInstance = Album.findByArtist("The Hold Steady")
			} catch (e) {
				e.printStackTrace()
			}
		}

		when:
		asyncApplicationEventMulticaster.multicastEvent(new DummyEvent())

		then:
		waitForListenerToComplete()
		domainObjectInstance != null
	}

	def "listeners can attach detached domain objects"() {
		given:
		listener.onEvent = { DomainEvent event ->
			def domainObjectInstance = event.domainObjectInstance
			domainObjectInstance.attach()
			Album.withSession { session ->
				if (!session.contains(domainObjectInstance)) {
					throw new IllegalStateException("Could not attach domain instance to the session")
				}
			}
		}

		when:
		def domainObjectInstance = Album.findByArtist("The Hold Steady")
		domainObjectInstance.discard()
		asyncApplicationEventMulticaster.multicastEvent(new DomainEvent(domainObjectInstance: domainObjectInstance))

		then:
		waitForListenerToComplete()
		domainObjectInstance != null
	}

	def "listeners can lazy load domain object properties"() {
		given:
		def songNames = null
		listener.onEvent = { ApplicationEvent event ->
			def domainObjectInstance = Album.findByArtist("The Hold Steady")
			songNames = domainObjectInstance.tracks.name
		}

		when:
		asyncApplicationEventMulticaster.multicastEvent(new DummyEvent())

		then:
		waitForListenerToComplete()
		songNames.containsAll(["The Sweet Part of the City", "Soft In the Center", "The Weekenders"])
	}

	void waitForListenerToComplete() {
		if (!listener.awaitCompletion(250, MILLISECONDS)) {
			if (exceptionTrap.handledError) {
				throw new AssertionError("Listener threw exception $exceptionTrap.handledError")
			} else {
				throw new AssertionError("Timed out waiting for listener to complete")
			}
		}
	}
}

class DomainEvent extends DummyEvent {
	Album domainObjectInstance
}

class ClosureListener implements ApplicationListener<DummyEvent> {

	private final CountDownLatch latch
	Closure onEvent

	ClosureListener() {
		this.latch = new CountDownLatch(1)
	}

	void onApplicationEvent(DummyEvent event) {
		onEvent(event)
		latch.countDown()
	}

	boolean awaitCompletion(long timeout, TimeUnit timeUnit) {
		return latch.await(timeout, timeUnit)
	}
}
