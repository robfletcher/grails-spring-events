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

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static org.hamcrest.CoreMatchers.notNullValue
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail
import static org.junit.matchers.JUnitMatchers.hasItems

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.codehaus.groovy.grails.plugin.springevents.test.Album
import org.codehaus.groovy.grails.plugin.springevents.test.DummyEvent
import org.codehaus.groovy.grails.plugin.springevents.test.ExceptionTrap
import org.codehaus.groovy.grails.plugin.springevents.test.Song
import org.junit.*
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

class ListenerHibernateTests {

	static transactional = false

	def asyncApplicationEventMulticaster
	ExceptionTrap exceptionTrap = new ExceptionTrap()
	ClosureListener listener

	@BeforeClass
	static void setUpSampleData() {
		Album.withNewSession {
			def album = new Album(artist: "The Hold Steady", name: "Heaven Is Whenever")
			["The Sweet Part of the City", "Soft In the Center", "The Weekenders", "The Smidge", "Rock Problems", "We Can Get Together", "Hurricane J", "Barely Breathing", "Our Whole Lives"].each {
				album.addToTracks new Song(name: it)
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
		asyncApplicationEventMulticaster.addApplicationListener(listener)
	}

	@Before
	void setUpExceptionTrap() {
		asyncApplicationEventMulticaster.errorHandler = exceptionTrap
	}

	@After
	void removeAllListeners() {
		asyncApplicationEventMulticaster.removeApplicationListener(listener)
	}

	@Test
	void listenersCanLookupDomainObjects() {
		def domainObjectInstance = null
		listener.onEvent = { ApplicationEvent event ->
			domainObjectInstance = Album.findByArtist("The Hold Steady")
		}

		asyncApplicationEventMulticaster.multicastEvent(new DummyEvent())

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
		asyncApplicationEventMulticaster.multicastEvent(new DomainEvent(domainObjectInstance: domainObjectInstance))

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

		asyncApplicationEventMulticaster.multicastEvent(new DummyEvent())

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
