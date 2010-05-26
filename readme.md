# Grails Asynchronous Events Plugin

The Grails Asynchronous Events plugin provides a lightweight mechanism for asynchronously publishing Spring application events.

## Publishing events

The plugin overrides the default `ApplicationEventMulticaster` with one that processes events asynchronously and is capable of retrying certain types of notification failure. 

#### _Example_ Firing an event when a domain class is updated:

	class Pirate {
		String name
		
		def grailsApplication
		
		void afterUpdate() {
			def event = new PirateUpdateEvent(this)
			grailsApplication.mainContext.publishEvent(event)
		}
	}
	
	class PirateUpdateEvent extends ApplicationEvent {
		PirateUpdateEvent(Pirate source) {
			super(source)
		}
	}

## Defining Listeners

Events are dispatched to any beans in the Spring context that implement the `ApplicationListener` interface. You can register listener beans in `resources.groovy`. Also, remember that Grails services are Spring beans, so simply implementing the interface in a service will automatically register it as a listener.

The plugin also provides a sub-interface `RetryableApplicationListener`. An implementation can throw `RetryableFailureException` from its `onApplicationEvent` method to indicate that the notification should be attempted again later. Throwing any other exception type will _not_ result in notification being retried.

#### _Example_ A listener that calls an unreliable external service:

	class UnreliableListener implements RetryableApplicationListener {
		
		def unreliableService
		final RetryPolicy retryPolicy = new RetryPolicy()
		
		void onApplicationEvent(ApplicationEvent event) {
			boolean success = true
			if (event instanceof AnInterestingEvent) {
				if (unreliableService.isAvailable()) {
					unreliableService.doSomething()
				} else {
					throw new RetryableFailureException("the service is currently unavailable")
				}
			}
		}
	}
	
In this example the listener throws `RetryableFailureException` to indicate that the external service it attempts to call is not currently available and notification should be attempted later.

## Retrying failed notifications

The `RetryableApplicationListener` interface also defines the method `getRetryPolicy()`. If the listener throws `RetryableFailureException` from the `onApplicationEvent` method it will be re-notified at some time in the future according to value returned by `getRetryPolicy`. The method should return an instance of `grails.plugin.asyncevents.RetryPolicy`.

The `RetryPolicy` class simply defines the rules governing how and when to re-notify the listener of any events it fails to handle. It defines the following properties:

* `maxRetries`: The maximum number of times that the listener will be re-notified of an event. After `maxRetries` is reached an exception is thrown and will be handled as any other exception thrown by the listener would be. A value of `-1` indicates that the listener should be re-notified indefinitely until it successfully processes the event. Defaults to _3_.
* `initialRetryDelayMillis`: The initial period in milliseconds that the service will wait before re-notifying the listener. Defaults to 1 minute.
* `backoffMultiplier`: The multiplier applied to the retry timeout before the second and subsequent retry. For example with a `backoffMultiplier` of `2` and `initialRetryDelayMillis` of `1000` the listener will be re-notified after 1000 milliseconds, 2000 milliseconds, 4000 milliseconds, 8000 milliseconds and so on. A `backoffMultiplier` of `1` would mean the listener will be re-notified at a fixed interval until it successfully handles the event or `maxRetries` is exceeded. Defaults to _2_.

## Customising the multicaster

The multicaster has several default dependencies that can be overridden using [Grails' property override configuration][1] mechanism.

### Handling notification errors

If a listener throws an exception from its `onApplicationEvent` method or its retry policy's `maxRetries` is exceeded then _eventPublisherService_ will notify its error handler. By default the service simply logs errors but you can override the default error handler by assigning a different [ErrorHandler][2] implementation to the service in `Config.groovy`:

### Customising threading policy

The multicaster uses a [ExecutorService][3] to poll the queue and notify the target listener. By default the service uses a [single thread][4] but you can use an alternate `ExecutorService` implementation by overriding the service's `taskExecutor` property in `Config.groovy`.

Similarly the service uses a [ScheduledExecutorService][5] to re-queue failed notifications after the delay specified by the listener's retry policy. The default implementation uses a [single thread][6] which can be overridden by setting the property `retryScheduler` in `Config.groovy`.

#### _Example_ Overriding the dependencies of the service in `Config.groovy`:

	beans {
		eventPublisherService {
			errorHandler = new SomeErrorHandlerImpl()
			eventProcessor = java.util.concurrent.Executors.newCachedThreadPool()
			retryScheduler = java.util.concurrent.Executors.newScheduledThreadPool(5)
		}
	}
	
[1]: http://grails.org/doc/latest/guide/14.%20Grails%20and%20Spring.html#14.6%20Property%20Override%20Configuration
[2]: http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/util/ErrorHandler.html "org.springframework.util.ErrorHandler"
[3]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/ExecutorService.html "java.util.concurrent.ExecutorService"
[4]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor() "java.util.concurrent.Executors.newSingleThreadExecutor()"
[5]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/ScheduledExecutorService.html "java.util.concurrent.ScheduledExecutorService"
[6]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/Executors.html#newSingleThreadScheduledExecutor() "java.util.concurrent.Executors.newSingleThreadScheduledExecutor()"
