package grails.plugin.asyncevents

class RetryableFailureException extends RuntimeException {

	RetryableFailureException() {
		super()
	}

	RetryableFailureException(String message) {
		super(message)
	}

	RetryableFailureException(String message, Throwable cause) {
		super(message, cause)
	}

	RetryableFailureException(Throwable cause) {
		super(cause)
	}
}
