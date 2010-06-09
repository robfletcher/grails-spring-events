# Grails Spring Events Plugin

The _Grails Spring Events_ plugin provides a lightweight mechanism for asynchronously publishing _Spring_ application events.

The plugin overrides the default [ApplicationEventMulticaster][7] with one that processes events asynchronously and is capable of retrying certain types of notification failure. 

## Publishing events

To publish an event you simply need to call the `publishEvent` method on any _Spring_ registered [ApplicationEventPublisher][8] implementation. The `ApplicationContext` is the most obvious candidate, although you could also have a service implement the `ApplicationEventPublisher` interface and use that.

To make things even easier the plugin adds a `publishEvent` method to every domain class, controller and service in the application.

#### _Example_: Firing an event when a domain class is updated:

	class Pirate {
		String name
		
		void afterUpdate() {
			def event = new PirateUpdateEvent(this)
			publishEvent(event)
		}
	}
	
	class PirateUpdateEvent extends ApplicationEvent {
		PirateUpdateEvent(Pirate source) {
			super(source)
		}
	}

## Defining Listeners

Events are dispatched to any beans in the _Spring_ context that implement the [ApplicationListener][9] interface. You can register listener beans in `resources.groovy`. Also, remember that _Grails_ services are _Spring_ beans, so simply implementing the interface in a service will automatically register it as a listener.

### Filtering the type of event

The `ApplicationListener` interface has a generic type parameter that you can use to filter the types of event that a listener implementation will be notified about. _Spring_ will simply not invoke your listener for other types of event.

#### _Example_: Using generics to filter the event type in a listener

	class PirateUpdateResponderService implements ApplicationListener<PirateUpdateEvent> {
		void onApplicationEvent(PirateUpdateEvent event) {
			log.info "Yarrr! The dread pirate $event.source.name has been updated!"
		}
	}

## Retrying failed notifications

Listener implementations may declare a `retryPolicy` property of type `grails.plugin.springevents.RetryPolicy` (or declare a `getRetryPolicy()` method). If such a property is present and the listener throws `grails.plugin.springevents.RetryableFailureException` from the `onApplicationEvent` method it will be re-notified at some time in the future according to the `retryPolicy` value. Throwing any other exception type will _not_ result in notification being retried.

Note: A `RetryableFailureException` thrown by a listener implementation is treated just like any other exception if the listener does not declare a `retryPolicy`.

The `RetryPolicy` class simply defines the rules governing how and when to re-notify the listener of any events it fails to handle. It defines the following properties:

* `maxRetries`: The maximum number of times that the listener will be re-notified of an event. After `maxRetries` is reached an exception is thrown and will be handled as any other exception thrown by the listener would be. A value of `-1` indicates that the listener should be re-notified indefinitely until it successfully processes the event. Defaults to _3_.
* `initialRetryDelayMillis`: The initial period in milliseconds that the service will wait before re-notifying the listener. Defaults to 1 minute.
* `backoffMultiplier`: The multiplier applied to the retry timeout before the second and subsequent retry. For example with a `backoffMultiplier` of _2_ and `initialRetryDelayMillis` of _1000_ the listener will be re-notified after 1000 milliseconds, 2000 milliseconds, 4000 milliseconds, 8000 milliseconds and so on. A `backoffMultiplier` of _1_ would mean the listener will be re-notified at a fixed interval until it successfully handles the event or `maxRetries` is exceeded. Defaults to _2_.

#### _Example_: A listener that calls an unreliable external service:

	class UnreliableListener implements ApplicationListener {
		
		def unreliableService
		final RetryPolicy retryPolicy = new RetryPolicy()
		
		void onApplicationEvent(ApplicationEvent event) {
			boolean success = true
			if (event instanceof AnInterestingEvent) {
				if (unreliableService.isAvailable()) {
					unreliableService.doSomething()
				} else {
					throw new RetryableFailureException("the unreliable service is currently unavailable")
				}
			}
		}
	}
	
In this example the listener throws `RetryableFailureException` to indicate that the external service it attempts to call is not currently available and notification should be attempted later.

## Customising the multicaster

The multicaster has several default dependencies that can be overridden using [Grails' property override configuration][1] mechanism.

### Handling notification errors

If a listener throws an exception from its `onApplicationEvent` method (or its retry policy's `maxRetries` is exceeded) then the multicaster will notify its error handler. The default error handler simply logs errors but you can override it by assigning a different [ErrorHandler][2] implementation to the service in `Config.groovy`:

### Customising threading policy

The multicaster uses a [ExecutorService][3] to poll the queue and notify the target listener. By default the service uses a [single thread][4] but you can use an alternate `ExecutorService` implementation by overriding the service's `taskExecutor` property in `Config.groovy`.

Similarly the service uses a [ScheduledExecutorService][5] to re-queue failed notifications after the delay specified by the listener's retry policy. The default implementation uses a [single thread][6] which can be overridden by setting the property `retryScheduler` in `Config.groovy`.

#### _Example_: Overriding the dependencies of the multicaster in `Config.groovy`:

	beans {
		applicationEventMulticaster {
			errorHandler = new SomeErrorHandlerImpl()
			taskExecutor = java.util.concurrent.Executors.newCachedThreadPool()
			retryScheduler = java.util.concurrent.Executors.newScheduledThreadPool(5)
		}
	}
	
[1]: http://grails.org/doc/latest/guide/14.%20Grails%20and%20Spring.html#14.6%20Property%20Override%20Configuration
[2]: http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/util/ErrorHandler.html "org.springframework.util.ErrorHandler"
[3]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/ExecutorService.html "java.util.concurrent.ExecutorService"
[4]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor() "java.util.concurrent.Executors.newSingleThreadExecutor()"
[5]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/ScheduledExecutorService.html "java.util.concurrent.ScheduledExecutorService"
[6]: http://java.sun.com/javase/6/docs/api/java/util/concurrent/Executors.html#newSingleThreadScheduledExecutor() "java.util.concurrent.Executors.newSingleThreadScheduledExecutor()"
[7]: http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/context/event/ApplicationEventMulticaster.html "org.springframework.context.event.ApplicationEventMulticaster"
[8]: http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/context/ApplicationEventPublisher.html "org.springframework.context.ApplicationEventPublisher"
[9]: http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/context/ApplicationListener.html "org.springframework.context.ApplicationListener"