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
import grails.plugin.springevents.GrailsApplicationEventMulticaster
import org.springframework.context.ApplicationEvent

class SpringEventsGrailsPlugin {

	def version = "1.1-SNAPSHOT"
	def grailsVersion = "1.2.0 > *"
	def dependsOn = [:]
	def pluginExcludes = [
			"grails-app/domain/**/*",
			"src/templates/**/*",
			"src/groovy/org/codehaus/groovy/grails/plugin/springevents/test/**/*",
			"grails-app/views/error.gsp",
			"web-app/**/*"
	]

	def author = "Rob Fletcher"
	def authorEmail = "rob@energizedwork.com"
	def title = "Grails Spring Events Plugin"
	def description = '''\\
Provides asynchronous Spring application event processing for Grails applications
'''

	// URL to the plugin's documentation
	def documentation = "http://grails.org/plugin/spring-events"

	def doWithSpring = {
		applicationEventMulticaster(GrailsApplicationEventMulticaster) {
			sessionFactory = ref("sessionFactory")
		}
	}

	def doWithDynamicMethods = {
		[application.controllerClasses, application.serviceClasses, application.domainClasses].flatten().each {
			it.metaClass.publishEvent = { ApplicationEvent event ->
				application.mainContext.publishEvent(event)
			}
		}
	}
}
