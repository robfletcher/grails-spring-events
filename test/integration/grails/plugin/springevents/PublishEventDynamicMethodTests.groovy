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

import static org.codehaus.groovy.grails.plugin.springevents.test.AsynchronousAssertions.waitFor

import java.util.concurrent.CountDownLatch

import org.codehaus.groovy.grails.plugin.springevents.test.*
import org.junit.After
import org.junit.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

class PublishEventDynamicMethodTests {

	def asyncApplicationEventMulticaster

	private insertLatch = new CountDownLatch(1)
	private updateLatch = new CountDownLatch(1)
	private deleteLatch = new CountDownLatch(1)
	private listener = { ApplicationEvent event ->
		switch (event) {
			case InsertEvent:
				insertLatch.countDown()
				break
			case UpdateEvent:
				updateLatch.countDown()
				break
			case DeleteEvent:
				deleteLatch.countDown()
				break
		}
	} as ApplicationListener

	@After
	void tearDownListeners() {
		asyncApplicationEventMulticaster.removeApplicationListener listener
	}

	@Test
	void domainClassesCanPublishEvents() {
		asyncApplicationEventMulticaster.addApplicationListener listener

		def album = new Album(artist: "Yeasayer", name: "Odd Blood").save(flush: true, failOnError: true)

		waitFor "Insert event", insertLatch

		album.addToTracks new Song(name: "Ambling Alp")
		album.save(flush: true, failOnError: true)

		waitFor "Update event", updateLatch

		album.delete(flush: true)

		waitFor "Delete event", deleteLatch
	}
}
