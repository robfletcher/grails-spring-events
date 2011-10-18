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

import static java.util.concurrent.TimeUnit.MILLISECONDS

import java.util.concurrent.CountDownLatch

import org.junit.Assert

class AsynchronousAssertions {

	static final long DEFAULT_TIMEOUT = 250L
	static final long DEFAULT_INTERVAL = 500L

	static void waitFor(String message, CountDownLatch latch, long timeout = DEFAULT_TIMEOUT) {
		if (!latch.await(timeout, MILLISECONDS)) {
			Assert.fail "Timed out waiting for $message, expecting $latch.count more events"
		}
	}

	static waitFor(Closure condition) {
		waitFor(null, condition)
	}

	static waitFor(Long timeout, Closure condition) {
		waitFor(timeout, null, condition)
	}

	static waitFor(Long timeout, Long interval, Closure condition) {
		timeout = timeout ?: DEFAULT_TIMEOUT
		interval = [timeout, interval ?: DEFAULT_INTERVAL].min()

		def loops = Math.ceil(timeout / interval)
		def pass = condition()
		def i = 0

		while (!pass && i++ < loops) {
			Thread.sleep((interval) as long)
			pass = condition()
		}

		if (i >= loops) {
			throw new AssertionError("condition did not pass in $timeout milliseconds")
		}

		pass
	}
}
