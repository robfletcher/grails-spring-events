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
package grails.plugin.springevents

import grails.test.AbstractCliTestCase
import java.util.zip.ZipFile
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class PluginPackagingTests extends AbstractCliTestCase {

	@Test
	void testClassesAndCruftAreNotBundledWithPlugin() {
		execute(["package-plugin"])
		assertThat "command exit code", waitForProcess(), equalTo(0)
		verifyHeader()

		def packagedPlugin = new File("grails-spring-events-${pluginVersion}.zip")
		assertTrue "plugin zip file", packagedPlugin.isFile()

		def zipFile = new ZipFile(packagedPlugin)
		try {
			def entryNames = zipFile.entries()*.name
			assertThat "Plugin zip contents", entryNames, not(hasItem(endsWith(".jpg")))
			assertThat "Plugin zip contents", entryNames, not(hasItem(endsWith(".png")))
			assertThat "Plugin zip contents", entryNames, not(hasItem(endsWith(".js")))
			assertThat "Plugin zip contents", entryNames, not(hasItem(endsWith(".css")))
			assertThat "Plugin zip contents", entryNames, not(hasItem(endsWith(".gsp")))
			assertThat "Plugin zip contents", entryNames, not(hasItem(containsString("/grails/plugin/springevents/test/")))
			assertThat "Plugin zip contents", entryNames, not(hasItem(startsWith("src/templates")))
		} finally {
			zipFile.close()
		}
	}

	def getPluginVersion() {
		"1.0-SNAPSHOT" // TODO: work this out rather than hardcoding
	}

}
