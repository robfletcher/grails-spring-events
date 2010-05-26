package org.codehaus.groovy.grails.plugin.asyncevents.test

class Album {

	String artist
	String name
	List tracks = []
	
	static hasMany = [tracks: Song]

    static constraints = {
		artist blank: false
		name blank: false, unique: "artist"
    }
}
