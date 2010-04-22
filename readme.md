# Grails Asynchronous Events Plugin

The Grails Asynchronous Events plugin provides a lightweight mechanism for publishing and receiving events.

## Publishing events

The plugin provides a service called `eventPublisherService` that is used to publish events asynchronously. In order to publish an event simply call the method `pubishEvent(ApplicationEvent)` on the service passing the new event object. The method queues the event for notification to all listeners.

## Defining Listeners

The `eventPublisherService` is aware of any beans in the Spring context that implement the `grails.plugin.asyncevents.AsyncEventListener` interface. You can register listeners in `resources.groovy`. Also, remember that Grails services are Spring beans, so simply implementing the interface in your application's services will automatically register it as a listener.

The `AsyncEventListener` is notified of events via the `onApplicationEvent(ApplicationEvent)` method. Note that this method returns `boolean` to indicate whether processing of the event was successful or not. Simple implementations may just return `true` but listeners that rely on resources that may be temporarily unavailable can return `false` to indicate that the event notification should be retried later.

The listener interface also defines the method `getRetryPolicy()`. If the listener returns `false` from the `onApplicationEvent` method then the `eventPublisherService` will automatically re-notify the listener at some time in the future according to the retry policy returned by `getRetryPolicy`.

## Retrying failed notifications

The `grails.plugin.asyncevents.RetryPolicy` class simply defines the rules governing how and when the `eventPublisherService` will re-notify the listener of any events it fails to handle. It defines the following properties:

* `maxRetries`:
* `initialRetryDelayMillis`:
* `backoffMultiplier`: