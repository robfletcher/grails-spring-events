import grails.plugin.asyncevents.SessionAwareMulticaster
import java.util.concurrent.Executors

class AsyncEventsGrailsPlugin {

	def version = "1.0-SNAPSHOT"
	def grailsVersion = "1.3.0 > *"
	def dependsOn = [:]
	def pluginExcludes = [
			"grails-app/views/error.gsp"
	]

	def author = "Rob Fletcher"
	def authorEmail = "rob@energizedwork.com"
	def title = "Grails Asynchronous Events Plugin"
	def description = '''\\
Provides asynchronous application event processing for Grails applications
'''

	// URL to the plugin's documentation
	def documentation = "http://grails.org/plugin/async-events"

	def doWithSpring = {
		applicationEventMulticaster(SessionAwareMulticaster) {
			sessionFactory = ref("sessionFactory")
		}
	}
}
