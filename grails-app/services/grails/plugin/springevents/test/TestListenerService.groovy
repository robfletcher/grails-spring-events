/*
 * Copyright 2010 Luke Daley
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
package grails.plugin.springevents.test

import org.springframework.context.ApplicationListener

class TestListenerService implements ApplicationListener<TestEvent>{
	
	static transactional = false
	
	def received = null
	def counter = 0
	
	void onApplicationEvent(TestEvent event) {
		++counter
		received = event.source
	}

}