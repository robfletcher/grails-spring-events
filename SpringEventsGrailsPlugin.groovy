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
import org.springframework.context.ApplicationListener
import grails.plugin.springevents.ProxyUtils
import org.codehaus.groovy.grails.plugins.PluginManagerHolder

class SpringEventsGrailsPlugin {

	def version = "1.1"
	def grailsVersion = "1.2.0 > *"
	def dependsOn = [:]
	def observe = ["controllers", "services"]
	def pluginExcludes = [
			"grails-app/domain/**/*",
			"src/templates/**/*",
			"**/org/codehaus/groovy/grails/plugin/springevents/test/**/*",
			"**/grails/plugin/springevents/test/**/*",
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

	def doWithApplicationContext = { ctx ->
		/*
			Because transactional beans are behind a factory and are lazy, they do not
			get found in time to be automatically registered. To force the issue, we hand
			register ALL service beans to ensure there is no weirdness. There is no harm
			in hand registering a listener that would be found anyway.
			
			See: http://jira.codehaus.org/browse/GRAILSPLUGINS-2552
		*/
		def multicaster = ctx.getBean('applicationEventMulticaster')
		application.serviceClasses.each {
			if (ApplicationListener.isAssignableFrom(it.clazz)) {
				log.debug "pre-registering service $it.name"
				multicaster.addApplicationListenerBean(it.propertyName)
			}
		}

		/*
			Beans with transactional proxies end up getting registered twice, due to a
			bug in AbstractApplicationEventMulticaster. To counter, we manually go and remove
			the non proxy bean from the multicaster.
			
			See: http://jira.codehaus.org/browse/GRAILSPLUGINS-2317
		*/
		def servicesPlugin = PluginManagerHolder.pluginManager.getGrailsPlugin("services").instance
		application.serviceClasses.each {
			if (ApplicationListener.isAssignableFrom(it.clazz) && servicesPlugin.shouldCreateTransactionalProxy(it)) {
				def proxy = ctx.getBean(it.propertyName)
				multicaster.removeApplicationListener(ProxyUtils.ultimateTarget(proxy))
			}
		}
		
	}
	
	def doWithDynamicMethods = {
		[application.controllerClasses, application.serviceClasses, application.domainClasses].flatten().each {
			addPublishEvent(it, application.mainContext)
		}
	}
	
	def onChange = { event ->
		if (application.isControllerClass(event.source) || 
			application.isServiceClass(event.source) ||
			application.isDomainClass(event.source)
		) {
			addPublishEvent(event.source, application.mainContext)
		}
	}
	
	def addPublishEvent(subject, ctx) {
		subject.metaClass.publishEvent = { ApplicationEvent event ->
			ctx.publishEvent(event)
		}
	}
}
