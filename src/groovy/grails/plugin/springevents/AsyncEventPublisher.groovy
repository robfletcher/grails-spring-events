/*
 * Copyright 2010-2011 Robert Fletcher
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

import java.lang.reflect.Field

import org.springframework.beans.BeansException
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.support.GenericApplicationContext
import org.springframework.util.Assert
import org.springframework.util.ReflectionUtils

/**
 * Event publisher used by GrailsApplicationEventMulticaster to multicast events asynchronously.
 *
 * @author Burt Beckwith
 */
class AsyncEventPublisher extends GenericApplicationContext implements InitializingBean {

	private ApplicationEventMulticaster eventMulticaster

	@Override
	protected void initApplicationEventMulticaster() {
		// the default behavior is to lookup the multicaster by bean name, but we need a specific one;
		// unfortunately the field is private
		Field field = ReflectionUtils.findField(getClass(), 'applicationEventMulticaster', ApplicationEventMulticaster)
		ReflectionUtils.makeAccessible field
		ReflectionUtils.setField field, this, eventMulticaster
	}

	/**
	 * Dependency injection for the async multicaster bean.
	 * @param multicaster the multicaster
	 */
	void setAsyncApplicationEventMulticaster(ApplicationEventMulticaster multicaster) {
		eventMulticaster = multicaster
	}

	void afterPropertiesSet() {
		Assert.notNull eventMulticaster, 'ApplicationEventMulticaster must be specified'
	}
}
