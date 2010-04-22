# Grails Asynchronous Events Plugin

The Grails Asynchronous Events plugin provides a lightweight mechanism for publishing and receiving events.

## Publishing events

The plugin provides a service called `eventPublisherService` that is used to publish events asynchronously. In order to publish an event simply call the method `pubishEvent(ApplicationEvent)` on the service passing the new event object. The method queues the event for notification to all listeners.

## Defining Listeners

The `eventPublisherService` is aware of any beans in the Spring context that implement the `grails.plugin.asyncevents.AsyncEventListener` interface. You can register listener beans in `resources.groovy`. Also, remember that Grails services are Spring beans, so simply implementing the interface in a service will automatically register it as a listener.

The `AsyncEventListener` is notified of events via the `onApplicationEvent(ApplicationEvent)` method. Note that this method returns `boolean` to indicate whether processing of the event was successful or not. Simple implementations may just return `true` but listeners that rely on resources that may be temporarily unavailable can return `false` to indicate that the event notification should be retried later.

The listener interface also defines the method `getRetryPolicy()`. If the listener returns `false` from the `onApplicationEvent` method then the `eventPublisherService` will automatically re-notify the listener at some time in the future according to the retry policy returned by `getRetryPolicy`.

## Retrying failed notifications

The `grails.plugin.asyncevents.RetryPolicy` class simply defines the rules governing how and when the `eventPublisherService` will re-notify the listener of any events it fails to handle. It defines the following properties:

* `maxRetries`: The maximum number of times that the listener will be re-notified of an event. After `maxRetries` is reached an exception is thrown and will be handled as any other exception thrown by the listener would be. A value of `-1` indicates that the listener should be re-notified indefinitely until it successfully processes the event.
* `initialRetryDelayMillis`: The initial period in milliseconds that the service will wait before re-notifying the listener.
* `backoffMultiplier`: The multiplier applied to the retry timeout before the second and subsequent retry. For example with a `backoffMultiplier` of `2` and `initialRetryDelayMillis` of `1000` the listener will be re-notified after 1000 milliseconds, 2000 milliseconds, 4000 milliseconds, 8000 milliseconds and so on. A `backoffMultiplier` of `1` would mean the listener will be re-notified at a fixed interval until it successfully handles the event or `maxRetries` is exceeded.

## Customising the event publisher service

The `eventPublisherService` has several default dependencies that can be overridden using [property override configuration].

### Handling notification errors

If a listener throws an exception from its `onApplicationEvent` method or its retry policy's `maxRetries` is exceeded then `eventPublisherService` will notify its error handler. By default the service simply logs errors but you can override the default error handler by assigning a different [ErrorHandler] implementation to the service in `Config.groovy`:

### Customising threading policy

Internally the `eventPublisherService` maintains a queue of pending notifications and uses a [ExecutorService] to poll the queue and notify the target listener. By default the service uses a [newSingleThreadExecutor] but you can use an alternate `ExecutorService` implementation by overriding the property `eventProcessor` in `Config.groovy`.

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
