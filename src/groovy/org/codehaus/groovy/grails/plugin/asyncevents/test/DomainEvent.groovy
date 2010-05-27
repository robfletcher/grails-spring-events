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
package org.codehaus.groovy.grails.plugin.asyncevents.test

import org.springframework.context.ApplicationEvent

abstract class DomainEvent extends ApplicationEvent {

	DomainEvent(Object source) {
		super(source)
	}

}

class InsertEvent extends DomainEvent {

	InsertEvent(Object source) {
		super(source)
	}

}

class UpdateEvent extends DomainEvent {

	UpdateEvent(Object source) {
		super(source)
	}

}

class DeleteEvent extends DomainEvent {

	DeleteEvent(Object source) {
		super(source)
	}

}
