/*
 * Copyright 2011 Robert Fletcher
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

package grails.plugin.springevents

import grails.util.GrailsUtil
import org.springframework.util.ErrorHandler

/**
 * An error handler implementation that sanitizes the exception stack and passes the exception on to a delegate.
 */
class StackFilteringErrorHandler implements ErrorHandler {

	private final ErrorHandler delegate

	static ErrorHandler decorate(ErrorHandler delegate) {
		new StackFilteringErrorHandler(delegate)
	}

	StackFilteringErrorHandler(ErrorHandler delegate) {
		this.delegate = delegate
	}

	void handleError(Throwable throwable) {
		delegate.handleError GrailsUtil.sanitize(throwable)
	}

}
