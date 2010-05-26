package org.codehaus.groovy.grails.plugin.asyncevents.test

import org.springframework.context.ApplicationEvent

class DummyEvent extends ApplicationEvent {
	
	static final DUMMY_EVENT_SOURCE = new Object()

	DummyEvent() {
		super(DUMMY_EVENT_SOURCE)
	}
}
