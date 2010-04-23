# Grails Asynchronous Events Plugin

The Grails Asynchronous Events plugin provides a lightweight mechanism for asynchronously publishing and receiving events.

## Publishing events

The plugin provides a service called _eventPublisherService_ that is used to publish events asynchronously. The service can be auto-wired into your Grails artefacts just like any other Spring bean. In order to publish an event simply call the method `pubishEvent(ApplicationEvent)` on the service passing the new event object. The method queues the event for notification to all listeners and returns immediately without blocking until the listeners have completed.

#### _Example_ Firing an event when a domain class is updated:

	class Pirate {
		String name
		
		def eventPublisherService
		
		void afterUpdate() {
			def event = new PirateUpdateEvent(this)
			eventPublisherService.publishEvent(event)
		}
	}
	
	class PirateUpdateEvent extends ApplicationEvent {
		PirateUpdateEvent(Pirate source) {
			super(source)
		}
	}

## Defining Listeners

The _eventPublisherService_ is aware of any beans in the Spring context that implement the `grails.plugin.asyncevents.AsyncEventListener` interface. You can register listener beans in `resources.groovy`. Also, remember that Grails services are Spring beans, so simply implementing the interface in a service will automatically register it as a listener.

Listeners are notified of events via the `onApplicationEvent(ApplicationEvent)` method. Note that this method returns `boolean` to indicate whether processing of the event was successful or not. Simple implementations may just return `true` but listeners that rely on resources that may be temporarily unavailable can return `false` to indicate that the event notification should be retried later.

#### _Example_ A listener that calls an unreliable external service

	class UnreliableListener implements AsyncEventListener {
		
		def unreliableService
		
		boolean onApplicationEvent(ApplicationEvent event) {
			boolean success = true
			if (event instanceof AnInterestingEvent) {
				if (unreliableService.isAvailable()) {
					unreliableService.doSomething()
				} else {
					success = false
				}
			}
			return success
		}
	}
	
In this example the listener returns `false` to indicate that the external service it attempts to call is not currently available and notification should be attempted later.

## Retrying failed notifications

The listener interface also defines the method `getRetryPolicy()`. If the listener returns `false` from the `onApplicationEvent` method then the _eventPublisherService_ will automatically re-notify the listener at some time in the future according to the retry policy returned by `getRetryPolicy`. The method should return an instance of `grails.plugin.asyncevents.RetryPolicy`.

The `RetryPolicy` class simply defines the rules governing how and when the _eventPublisherService_ will re-notify the listener of any events it fails to handle. It defines the following properties:

* `maxRetries`: The maximum number of times that the listener will be re-notified of an event. After `maxRetries` is reached an exception is thrown and will be handled as any other exception thrown by the listener would be. A value of `-1` indicates that the listener should be re-notified indefinitely until it successfully processes the event.
* `initialRetryDelayMillis`: The initial period in milliseconds that the service will wait before re-notifying the listener.
* `backoffMultiplier`: The multiplier applied to the retry timeout before the second and subsequent retry. For example with a `backoffMultiplier` of `2` and `initialRetryDelayMillis` of `1000` the listener will be re-notified after 1000 milliseconds, 2000 milliseconds, 4000 milliseconds, 8000 milliseconds and so on. A `backoffMultiplier` of `1` would mean the listener will be re-notified at a fixed interval until it successfully handles the event or `maxRetries` is exceeded.

## Customising the event publisher service

The _eventPublisherService_ has several default dependencies that can be overridden using [property override configuration].

### Handling notification errors

If a listener throws an exception from its `onApplicationEvent` method or its retry policy's `maxRetries` is exceeded then _eventPublisherService_ will notify its error handler. By default the service simply logs errors but you can override the default error handler by assigning a different [ErrorHandler] implementation to the service in `Config.groovy`:

### Customising threading policy

Internally the _eventPublisherService_ maintains a queue of pending notifications and uses a [ExecutorService] to poll the queue and notify the target listener. By default the service uses a [newSingleThreadExecutor] but you can use an alternate `ExecutorService` implementation by overriding the property `eventProcessor` in `Config.groovy`.

Similarly the service uses a [ScheduledExecutorService] to re-queue failed notifications after the delay specified by the listener's retry policy. The default implementation used is a [newSingleThreadScheduledExecutor] which can be overridden by setting the property `retryScheduler` in `Config.groovy`.

An example of overriding the dependencies of the service:

	beans {
		eventPublisherService {
			errorHandler = new SomeErrorHandlerImpl()
			eventProcessor = java.util.concurrent.Executors.newCachedThreadPool()
			retryScheduler = java.util.concurrent.Executors.newScheduledThreadPool(5)
		}
	}
	
[Property Override Configuration]: http://grails.org/doc/latest/guide/14.%20Grails%20and%20Spring.html#14.6%20Property%20Override%20Configuration
[ErrorHandler]: http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/util/ErrorHandler.html "org.springframework.util.ErrorHandler"
[ExecutorService]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/ExecutorService.html "java.util.concurrent.ExecutorService"
[ScheduledExecutorService]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/ScheduledExecutorService.html "java.util.concurrent.ScheduledExecutorService"
[newSingleThreadExecutor]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor() "java.util.concurrent.Executors.newSingleThreadExecutor()"
[newSingleThreadScheduledExecutor]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/Executors.html#newSingleThreadScheduledExecutor() "java.util.concurrent.Executors.newSingleThreadScheduledExecutor()"
