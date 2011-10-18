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

package grails.plugin.springevents;

import org.springframework.aop.*;
import org.springframework.aop.framework.*;
import org.springframework.aop.target.*;
import org.springframework.util.*;

public abstract class ProxyUtils {

	public static Object ultimateTarget(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Object current = candidate;
		while (current instanceof TargetClassAware) {
			Object nested = null;
			if (current instanceof Advised) {
				TargetSource targetSource = ((Advised) current).getTargetSource();
				if (targetSource instanceof SingletonTargetSource) {
					nested = ((SingletonTargetSource) targetSource).getTarget();
				}
			}
			current = nested;
		}
		return current;
	}
}
