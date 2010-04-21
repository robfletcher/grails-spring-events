package grails.plugin.asyncevents

import java.util.concurrent.TimeUnit

class RetryLaterException extends Exception {

	final int delay
	final TimeUnit delayUnit

	RetryLaterException(int delay, TimeUnit delayUnit) {
		this.delay = delay
		this.delayUnit = delayUnit
	}

	RetryLaterException(Throwable cause, int delay, TimeUnit delayUnit) {
		super(cause)
		this.delay = delay
		this.delayUnit = delayUnit
	}

	RetryLaterException(String message, Throwable cause, int delay, TimeUnit delayUnit) {
		super(message, cause)
		this.delay = delay
		this.delayUnit = delayUnit
	}

	RetryLaterException(String message, int delay, TimeUnit delayUnit) {
		super(message)
		this.delay = delay
		this.delayUnit = delayUnit
	}

}
