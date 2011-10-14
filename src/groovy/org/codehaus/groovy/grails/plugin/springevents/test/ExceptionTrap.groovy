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
package org.codehaus.groovy.grails.plugin.springevents.test

import java.util.concurrent.CountDownLatch

import org.springframework.util.ErrorHandler

class ExceptionTrap implements ErrorHandler {

	private final CountDownLatch latch
	Throwable handledError

	ExceptionTrap() { }

	ExceptionTrap(CountDownLatch latch) {
		this.latch = latch
	}

	void handleError(Throwable t) {
		handledError = t
		latch?.countDown()
	}
}
