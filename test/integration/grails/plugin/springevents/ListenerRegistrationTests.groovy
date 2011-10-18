/*
 * Copyright 2010 Luke Daley
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

import grails.plugin.springevents.test.*
import static org.codehaus.groovy.grails.plugin.springevents.test.AsynchronousAssertions.waitFor

class ListenerRegistrationTests extends GroovyTestCase {

	def asyncApplicationEventMulticaster
	def grailsApplication

	void testListenerServiceIsRegistered() {
		assert TestListenerService in asyncApplicationEventMulticaster.applicationListeners*.class
	}

	void testTransactionalListenerServiceIsRegistered() {
		assert TestTransactionalListenerService in asyncApplicationEventMulticaster.applicationListeners*.class
	}

	void testDispatchingToListeners() {
		// Must lazily load these in this manner because of the above tests
		def testListenerService = grailsApplication.mainContext.testListenerService
		def testTransactionalListenerService = grailsApplication.mainContext.testTransactionalListenerService

		[testListenerService, testTransactionalListenerService]*.counter = 0

		def event = new TestEvent(1)
		asyncApplicationEventMulticaster.multicastEvent(event)

		waitFor(1000) { testListenerService.counter > 0 }
		waitFor(1000) { testTransactionalListenerService.counter > 0 }

		// http://jira.codehaus.org/browse/GRAILSPLUGINS-2317
		shouldFail {
			waitFor(2000) { testTransactionalListenerService.counter == 2 }
		}

		assert testListenerService.counter == 1
		assert testTransactionalListenerService.counter == 1

		assert testListenerService.received == event.source
		assert testTransactionalListenerService.received == event.source

		[testListenerService, testTransactionalListenerService]*.counter = 0
	}
}
