package org.codehaus.groovy.grails.plugin.asyncevents.test

class Song {

	static belongsTo = [album: Album]

	String name

	static constraints = {
		name blank: false
	}

}
