/**
*  Copyright 2025 Bloodtick
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*  Bose SoundTouch
*  Ported from https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/bose-soundtouch.src/bose-soundtouch.groovy
*
*  Update: Bloodtick Jones
*  Date: 2020-09-13
*
*  1.0.00 2020-09-13 First release to support Hubitat. Ported from old SmartThings base code. Probably very buggy.
*  1.0.01 2020-09-17 Added trackData to support SharpTools cover art images.
*  1.1.00 2020-10-08 Added basic webSocket processing for Hubitat only based upon tomw code base.
*  1.1.01 2020-10-24 Ignore exception error on xml only when using parseLanMessage() in parse function.
*  1.1.02 2024-03-15 Remove a lot of the SmartThings nonsense. Improve webSocket. Added HDMI and home automation buttons. Rename back to just Bose SoundTouch.
*  1.1.03 2024-09-24 Updated socket logic to restart if nothing in an hour. Sometimes was in a weird state. Added healthStatus attribute and Initialize capability
*  1.1.04 2025-03-24 Added volume and volumePattern matcher
*/

import groovy.json.*
import groovy.xml.XmlUtil
import groovy.transform.CompileStatic
import groovy.transform.Field

@Field volatile static Map<String,Long> g_mWebSocketTimestamp = [:]
@Field volatile static Map<String,Map> g_mStatePending = [:]
@Field volatile static Map<String,Map> g_mStateReady = [:]
@Field volatile static Map<String,Integer> g_mVolume = [:]
@Field static final Integer checkInterval = 300

metadata {
    definition (name: "Bose SoundTouch", namespace: "bloodtick", author: "Hubitat", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/boseSoundTouch/boseSoundTouch.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level" // added for Alexa
        capability "Refresh"
        capability "Music Player"
        capability "AudioVolume"
        capability "PushableButton"
        capability "Initialize"
        capability "HealthCheck"
        
        command "preset1"
        command "preset2"
        command "preset3"
        command "preset4"
        command "preset5"
        command "preset6"
        command "aux"
        command "hdmi"

        attribute "preset1", "JSON_OBJECT"
        attribute "preset2", "JSON_OBJECT"
        attribute "preset3", "JSON_OBJECT"
        attribute "preset4", "JSON_OBJECT"
        attribute "preset5", "JSON_OBJECT"
        attribute "preset6", "JSON_OBJECT"

        attribute "info", "JSON_OBJECT"

        attribute "trackData", "JSON_OBJECT"  //added for sharptools
        attribute "trackStation", "string"    //active station        
        attribute "station1", "string"        //preset information for ST
        attribute "station2", "string"
        attribute "station3", "string"
        attribute "station4", "string"
        attribute "station5", "string"
        attribute "station6", "string"
        
        attribute "volumePattern", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
    }
}

preferences {
    input(name:"deviceIp", type:"text", title: "<b>Device IP Address</b>", description: "Local lan IPv4 Address", defaultValue: "127.0.0.1", required: true)
    input(name:"numberOfButtons", type: "number", title: "<b>Set Number of Buttons:</b>", range: "1...", defaultValue: 4, required: true)
    input(name:"deviceInfoDisable", type:"bool", title: "Disable Info logging:", defaultValue: false)
    input(name:"deviceDebugEnable", type: "bool", title: "Enable debug logging", defaultValue: false) 
    input(name:"deviceTraceEnable", type: "bool", title: "Enable trace logging", defaultValue: false)
}

def installed() {
    settings.deviceIp = "127.0.0.1"
    settings.deviceDebugEnable = false
    settings.deviceTraceEnable = false
    sendEvent(name: "level", value: "0", unit: "%")
    sendEvent(name: "volume", value: "0", unit: "%")
    sendEvent(name: "switch", value:"off", displayed: false)
    logInfo "${device.displayName} executing 'installed()' with settings: ${settings}"
    initialize()
}

def updated() {
    logDebug "Executing 'updated()' with new preferences: ${settings}"
    logInfo "${device.displayName} preferences saved"
    initialize()
}

def initialize() {
    logInfo "Executing 'initialize()'"
    unschedule()
    sendEvent(name: "numberOfButtons", value: (settings?.numberOfButtons)?:4)
    sendEvent(name: "checkInterval", value: checkInterval)
    interfaces.webSocket.close()
    g_mWebSocketTimestamp[device.getId()] = null
    runIn(2, ping)
    runIn(5, refresh)
}

def push(buttonNumber=null, String descriptionText=null) {
    if(buttonNumber==null && state?.volumePattern) {
        // these are rapid volume control keys that can be used for automation
        //if(state.volumePattern == "++--") push(1, "${device.displayName} volume matched '${state.volumePattern}' and pushed button 1")
        //if(state.volumePattern == "--++") push(2, "${device.displayName} volume matched '${state.volumePattern}' and pushed button 2")
        //if(state.volumePattern == "+-+-") push(3, "${device.displayName} volume matched '${state.volumePattern}' and pushed button 3")
        //if(state.volumePattern == "-+-+") push(4, "${device.displayName} volume matched '${state.volumePattern}' and pushed button 4")
        if(state.volumePattern.size()>3) sendEvent(name: "volumePattern", value: state.volumePattern, isStateChange: true) // for other solutions if you don't want to use above.
        state.remove('volumePattern')
    } else {
        sendEvent(name: "pushed", value: buttonNumber, isStateChange: true, descriptionText: (descriptionText ?: "${device.displayName} pushed button $buttonNumber"))
        if(descriptionText) logInfo descriptionText
    }
}

/**************************************************************************
* The following section simply maps the actions as defined in
* the metadata into onAction() calls.
*
* This is preferred since some actions can be dealt with more
* efficiently this way. Also keeps all user interaction code in
* one place.
*
*/
def off() {
    if (device.currentState("switch")?.value == "on") {
        onAction("off")
    }
}

def on() {
    if (device.currentState("switch")?.value == "off") {
        onAction("on")
    }
}

def volumeUp() { onAction("volumeUp") }
def volumeDown() { onAction("volumeDown") }
def preset1() { onAction("1") }
def preset2() { onAction("2") }
def preset3() { onAction("3") }
def preset4() { onAction("4") }
def preset5() { onAction("5") }
def preset6() { onAction("6") }
def aux() { onAction("aux") }
def hdmi() { onAction("product") }
def refresh() { onAction("refresh") }
def setVolume(level) { setLevel(level) }
def setLevel(level,duration=0) { onAction("volume", level) }
def play() { onAction("play") }
def stop() { pause() }
def pause() { onAction("pause") }
def mute() { onAction("mute") }
def unmute() { onAction("unmute") }
def previousTrack() { onAction("previous") }
def nextTrack() { onAction("next") }
def playTrack(uri) { onAction("playTrack", uri) } // I have no idea if this will work. It doesn't on a Soundtouch 300.
def setTrack(value) { unsupported("setTrack") }
def resumeTrack(value) { unsupported("resumeTrack") }
def restoreTrack(value) { unsupported("restoreTrack") }
def playText(value) { unsupported("playText") }
def unsupported(func) { logInfo "${device.displayName} does not support ${func}" }
//def everywhereJoin() { onAction("ejoin") }
//def everywhereLeave() { onAction("eleave") }
/**************************************************************************/

/**
* Main point of interaction with things.
* This function is called by SmartThings Cloud with the resulting data from
* any action (see HubAction()).
*
* Conversely, to execute any actions, you need to return them as a single
* item or a list (flattened).
*
* @param data Data provided by the cloud
* @return an action or a list() of actions. Can also return null if no further
*         action is desired at this point.
*/
def parse(String event) {
    def data = []
    try {
        data = parseLanMessage(event)
    }
    catch (Exception e) {
        // this will fail when pure xml. ignore it.
        logTrace "parse() exception ignored: $e"
    }
    def actions = []
    logTrace "parse() header:${data.header}"
    logTrace "parse() body:${data.body}"

    // List of permanent root node handlers
    def handlers = [
        "nowPlaying" : "boseParseNowPlaying",
        "volume" : "boseParseVolume",
        "presets" : "boseParsePresets",
        "zone" : "boseParseEverywhere",
        "info" : "boseParseInfo"
    ]

    if (!data.header) {
        // most likely this was a websocket callback.
        logDebug "parse() websocket: ${groovy.xml.XmlUtil.escapeXml(event)}"
        def xml = new XmlSlurper().parseText(event)        
        if((!xml?.volumeUpdated?.isEmpty()) || (!xml?.nowPlayingUpdated?.isEmpty()) || (!xml?.infoUpdated?.isEmpty()))
        {
            runIn(3, ping)
        }
        // figure out a home automation trick since the websocket is very fast
        if(!xml?.volumeUpdated?.isEmpty()) {
            g_mVolume[device.getId()] = g_mVolume[device.getId()] ?: 0  
            String direction = (xml?.volumeUpdated?.volume?.targetvolume.toInteger() > g_mVolume[device.getId()]) ? "+" : "-"
            g_mVolume[device.getId()] = xml?.volumeUpdated?.volume?.targetvolume.toInteger()
            state.volumePattern = (state?.volumePattern ?: "") + direction
            runIn(1, push)
        }        
        g_mWebSocketTimestamp[device.getId()] = now()
        return null
    }
    // Move any pending callbacks into ready state
    prepareCallbacks()

    def xml = new XmlSlurper().parseText(data.body)
    logDebug "parse() xml:${xml.text()}"    
    if (xml.text()=="unsupported device") {
        logInfo "${device.displayName} not supported by this device"
        return null
    }    

    // Let each parser take a stab at it
    handlers.each { node,func ->
        if (xml.name() == node)
        actions << "$func"(xml)
    }
    // If we have callbacks waiting for this...
    actions << processCallbacks(xml)

    // Be nice and helpful
    if (actions.size() == 0) {
        logWarn "parse(): Unhandled data = " + data
        return null
    }

    // Issue new actions
    return actions.flatten()
}

/**
* Called by onAction every checkInterval seconds
* If device doesn't respond it will be marked offline (not available) and socket retried
*/
def ping() {
    logDebug("ping()")
    
    Long lastSocketMsg = now() - ((Long)g_mWebSocketTimestamp[device.getId()]?:0L)
    g_mWebSocketTimestamp[device.getId()] = ( device.currentValue("switch")=="on" && lastSocketMsg > 60L*60L*1000L ) ? null : g_mWebSocketTimestamp[device.getId()]
    
    if(g_mWebSocketTimestamp[device.getId()]==null) {
        interfaces.webSocket.close()
        runIn(5,"setHealthStatusValue")
        logInfo("${device.displayName} connecting webSocket ws://${getDeviceIP()}:8080")
        interfaces.webSocket.connect("ws://${getDeviceIP()}:8080/", headers: ["Sec-WebSocket-Protocol":"gabbo"])
    }
    onAction("ping")
}

def webSocketStatus(String message) {
    logDebug("${device.displayName} webSocket $message")
    if(message?.contains("open")) {
        g_mWebSocketTimestamp[device.getId()] = now() //this will get updated every websocket callback too
    } else {
        g_mWebSocketTimestamp[device.getId()] = null        
    }
    runIn(2,"setHealthStatusValue")
}

def setHealthStatusValue() {
    String value = g_mWebSocketTimestamp[device.getId()]==null ? "offline" : "online"
    if(device.currentValue("healthStatus")!=value) {
        String descriptionText = "${device.displayName} healthStatus set to $value"
        sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText)
        logInfo descriptionText
    }
}

/**
* Responsible for dealing with user input and taking the
* appropiate action.
*
* @param user The user interaction
* @param data Additional data (optional)
* @return action(s) to take (or null if none)
*/
def onAction(String user, data=null) {
    logDebug "onAction(${user})"

    // Process action
    def actions = null
    switch (user) {
        case "on":
            boseSetPowerState(true)
            break
        case "off":
            boseSetNowPlaying(null, "STANDBY")
            boseSetPowerState(false)
            break
        case "volume":
            actions = boseSetVolume(data)
            break
        case "volumeUp":
            def level = (device.currentValue("level").toInteger()+1)
            actions = boseSetVolume(level)
            break
        case "volumeDown":
            def level = (device.currentValue("level").toInteger()-1)
            actions = boseSetVolume(level)
            break 
        case "product":
            boseSetNowPlaying(null, "PRODUCT")
            actions = boseSetInput("PRODUCT")
            break
        case "aux":
            boseSetNowPlaying(null, "AUX")
            actions = boseSetInput(user)
            break        
        case "1":
        case "2":
        case "3":
        case "4":
        case "5":
        case "6":
            actions = boseSetInput(user)
            break
        case "refresh":
            boseSetNowPlaying(null, "REFRESH")
            actions = [boseRefreshNowPlaying(), boseGetPresets(), boseGetVolume(), boseGetEverywhereState(), boseGetInfo()]
            break
        case "ping":
            if(device.currentState("switch")?.value == "on")
                actions = [boseGetVolume(), boseRefreshNowPlaying()]
            else
                actions = boseRefreshNowPlaying()
            break
        case "play":
            actions = [boseSetPlayMode(true), boseRefreshNowPlaying()]
            break
        case "pause":
            actions = [boseSetPlayMode(false), boseRefreshNowPlaying()]
            break
        case "previous":
            actions = [boseChangeTrack(-1), boseRefreshNowPlaying()]
            break
        case "next":
            actions = [boseChangeTrack(1), boseRefreshNowPlaying()]
            break
        case "mute":
            actions = boseSetMute(true)
            break
        case "unmute":
            actions = boseSetMute(false)
            break
        case "ejoin":
            actions = boseZoneJoin()
            break
        case "eleave":
            actions = boseZoneLeave()
            break
        case "playTrack":
            actions = bosePlayTrack(data)
            break
        default:
            log.error "Unhandled action: " + user
    }

    if(user=="ping" || user=="refresh") 
        runIn(checkInterval, ping)
    else
        runIn(5, ping)        

    // Make sure we don't have nested lists
    if (actions instanceof List)
        return actions.flatten()
    return actions
}

/**
* Joins this speaker into the everywhere zone
*/
def boseZoneJoin() {
    logTrace "boseZoneJoin()"

    def results = []
    def posts = parent.boseZoneJoin(this)

    for (post in posts) {
        if (post['endpoint'])
        results << bosePOST(post['endpoint'], post['body'], post['host'])
    }
    //sendEvent(name:"everywhere", value:"leave")
    results << boseRefreshNowPlaying()

    return results
}

/**
* Removes this speaker from the everywhere zone
*/
def boseZoneLeave() {
    logTrace "boseZoneLeave()"

    def results = []
    def posts = parent.boseZoneLeave(this)

    for (post in posts) {
        if (post['endpoint'])
        results << bosePOST(post['endpoint'], post['body'], post['host'])
    }
    //sendEvent(name:"everywhere", value:"join")
    results << boseRefreshNowPlaying()

    return results
}

/**
* Removes this speaker and any children WITHOUT
* signaling the speakers themselves. This is needed
* in certain cases where we know the user action will
* cause the zone to collapse (for example, AUX)
*/
def boseZoneReset() {
    logTrace "boseZoneReset()"

    parent.boseZoneReset()
}

/**
* Handles <nowPlaying></nowPlaying> information and can also
* perform addtional actions if there is a pending command
* stored in the state variable. For example, the power is
* handled this way.
*
* @param xmlData Data to parse
* @return command
*/
def boseParseNowPlaying(xmlData) {
    logTrace "boseParseNowPlaying(${xmlData})"

    def result = []

    // Perform display update, allow it to add additional commands
    if (boseSetNowPlaying(xmlData)) {
        result << boseRefreshNowPlaying()
    }

    return result
}

/**
* Parses volume data
*
* @param xmlData Data to parse
* @return command
*/
def boseParseVolume(xmlData) {
    logTrace "boseParseVolume(${xmlData})"

    def result = []

    def level = xmlData.actualvolume.text()
    def mute = (xmlData.muteenabled.text().toBoolean()) ? "muted" : "unmuted"

    if( device.currentValue("level").toString() != level ) {
        sendEvent(name:"level", value: level, unit: "%")
        sendEvent(name:"volume", value: level, unit: "%")
        logInfo "${device.displayName} volume is ${level}%"
    }
    if( device.currentValue("mute") != mute ) {
        sendEvent(name:"mute", value: mute)
        logInfo "${device.displayName} is ${mute}"
    }

    return result
}

/**
* Parses the result of the boseGetInfo() call
*
* @param xmlData
*/
def boseParseInfo(xmlData) {
    logTrace "boseParseInfo(${xmlData})"
    
    def info = [:]
    
    info['manufacturer'] = "Bose"
    info['type'] = xmlData.type.text()    
    info['name'] = xmlData.name.text()
    info['deviceID'] = xmlData.@deviceID.text()
    
    logDebug "info: ${info}"
    sendEvent(name:"info", value: JsonOutput.toJson(info))
}

/**
* Parses the result of the boseGetEverywhereState() call
*
* @param xmlData
*/
def boseParseEverywhere(xmlData) {
    logTrace "boseParseEverywhere(${xmlData})"
    // No good way of detecting the correct state right now
}

/**
* Parses presets and updates the buttons
*
* @param xmlData Data to parse
* @return command
*/
def boseParsePresets(xmlData) {
    logTrace "boseParsePresets(${xmlData})"
    def result = []

    state.preset = [:]

    def missing = ["1", "2", "3", "4", "5", "6"]
    for (preset in xmlData.preset) {
        def id = preset.attributes()['id']
        def mediaSource = preset.ContentItem[0].attributes()['source']
        def name = preset.ContentItem.itemName[0].text()
        def imageUrl = preset.ContentItem.containerArt[0].text()
        
        def item = [:]
        item['id'] = id
        item['mediaSource'] = mediaSource
        item['name'] = name
        item['imageUrl'] = imageUrl        

        sendEvent(name:"station${id}", value: name)
        sendEvent(name:"preset${id}", value: JsonOutput.toJson(item))
        missing = missing.findAll { it -> it != id }
        // Store the presets into the state for recall later
        state.preset["$id"] = XmlUtil.serialize(preset.ContentItem)
    }

    for (id in missing) {
        state.preset["$id"] = null
        sendEvent(name:"station${id}", value:"")
        sendEvent(name:"preset${id}", value:"")
    }

    return result
}

/**
* Based on <nowPlaying></nowPlaying>, updates the visual
* representation of the speaker
*
* @param xmlData The nowPlaying info
* @param override Provide the source type manually (optional)
*
* @return true if it would prefer a refresh soon
*/
def boseSetNowPlaying(xmlData, override=null) {
    logTrace "boseSetNowPlaying(xmlData: ${xmlData})"

    def needrefresh = false

    if (xmlData && xmlData.playStatus) {
        switch(xmlData.playStatus) {
            case "BUFFERING_STATE":
                sendEvent(name:"status", value:"buffering")
                needrefresh = true
                break
            case "PLAY_STATE":
                sendEvent(name:"status", value:"playing")
                break
            case "PAUSE_STATE":
                sendEvent(name:"status", value:"paused")
                break
            case "STOP_STATE":
                sendEvent(name:"status", value:"stopped")
                break
        }
    }

    // Some last parsing which only deals with actual data from device
    if (xmlData) {
        if (xmlData.attributes()['source'] == "STANDBY") {
            if(device.currentState("switch")?.value == "on") {
                sendEvent(name:"switch", value:"off")
                sendEvent(name:"status", value:"stopped")
                logInfo "${device.displayName} is off"
            }
        } else {
            if(device.currentState("switch")?.value != "on") {
                sendEvent(name:"switch", value:"on")
                logInfo "${device.displayName} is on at ${device.currentValue("level")}%"
                g_mWebSocketTimestamp[device.getId()] = null // redo the websocket
            }
        }
        boseSetPlayerAttributes(xmlData)
    }

    // Do not allow a standby device or AUX to be master
    /*if (!parent.boseZoneHasMaster() && (override ? override : xmlData.attributes()['source']) == "STANDBY")
		sendEvent(name:"everywhere", value:"unavailable")
	else if ((override ? override : xmlData.attributes()['source']) == "AUX")
		sendEvent(name:"everywhere", value:"unavailable")
	else if (boseGetZone()) {
		logInfo "We're in the zone: " + boseGetZone()
		sendEvent(name:"everywhere", value:"leave")
	} else
		sendEvent(name:"everywhere", value:"join")
	*/

    return needrefresh
}

/**
* Updates the attributes exposed by the music Player capability
*
* @param xmlData The NowPlaying XML data
*/
def boseSetPlayerAttributes(xmlData) {
    logTrace "boseSetPlayerAttributes(xmlData: ${xmlData})"

    // Refresh attributes
    def trackDesc = "Standby"
    def trackData = [:]

    trackData["mediaSource"] = xmlData.@source.text()
    trackData["sourceAccount"] = xmlData.@sourceAccount.text()

    switch (xmlData.attributes()['source']) {
        case "PRODUCT":
            trackData["station"] = trackDesc = trackData.sourceAccount
            break
        case "STANDBY":
            trackData["station"] = trackDesc = "Standby"
            break
        case "AUX":
            trackData["station"] = trackDesc = "Auxiliary Input"
            break
        case "AIRPLAY":
            trackData["station"] = trackDesc = "AirPlay"
            if (!trackData.sourceAccount.contains("AirPlay2"))
                break
        case "SPOTIFY":
        case "DEEZER":
        case "PANDORA":
        case "IHEART":
        case "STORED_MUSIC": // Tested on Synology NAS
        case "AMAZON":
        case "SIRIUSXM_EVEREST":
            trackData["artist"]  = xmlData.artist ? "${xmlData.artist.text()}" : ""
            trackData["title"]   = xmlData.track  ? "${xmlData.track.text()}"  : ""
            trackData["station"] = xmlData.stationName ? "${xmlData.stationName.text()}" : trackData.mediaSource
            trackData["album"]   = xmlData.album ? "${xmlData.album.text()}" : ""
            trackData["albumArtUrl"]   = xmlData.art && xmlData.art.@artImageStatus.text()=="IMAGE_PRESENT" ? "${xmlData.art.text()}" : "${xmlData.ContentItem.containerArt.text()}"
            trackDesc = trackData.artist + ": " + trackData.title 
            break
        case "INTERNET_RADIO":
            trackData["station"] = xmlData.stationName ? "${xmlData.stationName.text()}" : trackData.mediaSource
            trackData["description"]  = xmlData.description ? "${xmlData.description.text()}" : ""
            trackDesc = trackData.station + ": " + trackData.description
            break
        default:
            trackData["station"] = trackDesc = trackData.mediaSource
    }

    logDebug "trackData: ${trackData}"
    sendEvent(name:"trackStation", value: trackData.station, display: false, displayed: false)
    sendEvent(name:"trackDescription", value: trackDesc, display: false, displayed: false)
    sendEvent(name:"trackData", value: JsonOutput.toJson(trackData), display: false, displayed: false)
    
}

/**
* Queries the state of the "play everywhere" mode
*
* @return command
*/
def boseGetEverywhereState() {
    logTrace "boseGetEverywhereState()"

    return boseGET("/getZone")
}

/**
* Generates a remote key event
*
* @param key The name of the key
*
* @return command
*
* @note It's VITAL that it's done as two requests, or it will ignore the
*       the second key info.
*/
def boseKeypress(key) {
    logTrace "boseKeypress(key: ${key})"

    def press = "<key state=\"press\" sender=\"Gabbo\">${key}</key>"
    def release = "<key state=\"release\" sender=\"Gabbo\">${key}</key>"

    return [bosePOST("/key", press), bosePOST("/key", release)]
}

/**
* Pauses or plays current preset
*
* @param play If true, plays, else it pauses (depending on preset, may stop)
*
* @return command
*/
def boseSetPlayMode(boolean play) {
    logTrace "boseSetPlayMode(play: ${play})"

    return boseKeypress(play ? "PLAY" : "PAUSE")
}

/**
* Sets the volume in a deterministic way.
*
* @param New volume level, ranging from 0 to 100
*
* @return command
*/
def boseSetVolume(level) {
    logTrace "boseSetVolume(level: ${level})"

    def result = []
    int vol = Math.min(100, Math.max((int)level, 0))

    return [bosePOST("/volume", "<volume>${vol}</volume>"), boseGetVolume()]
}

/**
* Sets the mute state, unfortunately, for now, we need to query current
* state before taking action (no discrete mute/unmute)
*
* @param mute If true, mutes the system
* @return command
*/
def boseSetMute(boolean mute) {
    logTrace "boseSetMute(mute: ${mute})"

    queueCallback('volume', 'cb_boseSetMute', mute ? 'MUTE' : 'UNMUTE')
    return boseGetVolume()
}

/**
* Callback for boseSetMute(), checks current state and changes it
* if it doesn't match the requested state.
*
* @param xml The volume XML data
* @param mute The new state of mute
*
* @return command
*/
def cb_boseSetMute(xml, muted) {
    logTrace "cb_boseSetMute(xml: ${xml.muteenabled.text()}, muted: ${muted})"

    def result = []
    if ((xml.muteenabled.text() == 'false' && muted == 'MUTE') ||
        (xml.muteenabled.text() == 'true' && muted == 'UNMUTE'))
    {
        result << boseKeypress("MUTE")

        def mute = (muted == 'MUTE') ? "muted" : "unmuted"
        if( device.currentValue("mute") != mute ) {
            sendEvent(name:"mute", value: mute)
            logInfo "${device.displayName} is ${mute}"
        }
    }
    return result
}

/**
* Refreshes the state of the volume
*
* @return command
*/
def boseGetVolume() {
    logTrace "boseGetVolume()"

    return boseGET("/volume")
}

/**
* Changes the track to either the previous or next
*
* @param direction > 0 = next track, < 0 = previous track, 0 = no action
* @return command
*/
def boseChangeTrack(int direction) {
    logTrace "boseChangeTrack(direction: ${direction})"

    if (direction < 0) {
        return boseKeypress("PREV_TRACK")
    } else if (direction > 0) {
        return boseKeypress("NEXT_TRACK")
    }
    return []
}

/**
* Sets the input to preset 1-6 or AUX
*
* @param input The input (one of 1,2,3,4,5,6,aux)
*
* @return command
*
* @note If no presets have been loaded, it will first refresh the presets.
*/
def boseSetInput(input) {
    logTrace "boseSetInput(input: ${input})"

    def result = []

    if (!state.preset) {
        result << boseGetPresets()
        queueCallback('presets', 'cb_boseSetInput', input)
    } else {
        result << cb_boseSetInput(null, input)
    }
    return result
}

/**
* Callback used by boseSetInput(), either called directly by
* boseSetInput() if we already have presets, or called after
* retreiving the presets for the first time.
*
* @param xml The presets XML data
* @param input Desired input
*
* @return command
*
* @note Uses KEY commands for AUX, otherwise /select endpoint.
*       Reason for this is latency. Since keypresses are done
*       in pairs (press + release), you could accidentally change
*       the preset if there is a long delay between the two.
*/
def cb_boseSetInput(xml, input) {
    logTrace "cb_boseSetInput(${xml},${input})"

    def result = []

    if (input >= "1" && input <= "6" && state.preset["$input"])
    result << bosePOST("/select", state.preset["$input"])
    else if (input.toLowerCase() == "aux") {
        result << boseKeypress("AUX_INPUT")
    }
    else if (input.toLowerCase() == "product") {
        result << [ bosePOST("/select", "<ContentItem source=\"PRODUCT\" sourceAccount=\"TV\"></ContentItem>") ]
    }
    // Horrible workaround... but we need to delay
    // the update by at least a few seconds...
    result << boseRefreshNowPlaying(3000)
    return result
}

/**
* Sets the power state of the bose unit
*
* @param device The device in-question
* @param enable True to power on, false to power off
*
* @return command
*
* @note Will first query state before acting since there
*       is no discreete call.
*/
def boseSetPowerState(boolean enable) {
    logTrace "boseSetPowerState(enable: ${enable})"

    bosePOST("/key", "<key state=\"press\" sender=\"Gabbo\">POWER</key>")
    bosePOST("/key", "<key state=\"release\" sender=\"Gabbo\">POWER</key>")
    boseGET("/now_playing")
    if (enable) {
        queueCallback('nowPlaying', "cb_boseConfirmPowerOn", 5)
    }
}

/**
* Callback function used by boseSetPowerState(), is used
* to handle the fact that we only have a toggle for power.
*
* @param xml The XML data from nowPlaying
* @param state The requested state
*
* @return command
*/
def cb_boseSetPowerState(xml, state) {
    logTrace "cb_boseSetPowerState(${xml},${state})"

    def result = []
    if ( (xml.attributes()['source'] == "STANDBY" && state == "POWERON") ||
        (xml.attributes()['source'] != "STANDBY" && state == "POWEROFF") )
    {
        result << boseKeypress("POWER")
        if (state == "POWERON") {
            result << boseRefreshNowPlaying()
            queueCallback('nowPlaying', "cb_boseConfirmPowerOn", 5)
        }
    }
    return result.flatten()
}

/**
* We're sometimes too quick on the draw and get a refreshed nowPlaying
* which shows standby (essentially, the device has yet to completely
* transition to awake state), so we need to poll a few times extra
* to make sure we get it right.
*
* @param xml The XML data from nowPlaying
* @param tries A counter which will decrease, once it reaches zero,
*              we give up and assume that whatever we got was correct.
* @return command
*/
def cb_boseConfirmPowerOn(xml, tries) {
    logTrace "cb_boseConfirmPowerOn(${xml},${tries})"

    def result = []
    def attempt = tries as Integer
    log.warn "boseConfirmPowerOn() attempt #$attempt"
    if (xml.attributes()['source'] == "STANDBY" && attempt > 0) {
        result << boseRefreshNowPlaying()
        queueCallback('nowPlaying', "cb_boseConfirmPowerOn", attempt-1)
    }
    return result
}

/**
* Requests an update on currently playing item(s)
*
* @param delay If set to non-zero, delays x ms before issuing
*
* @return command
*/
def boseRefreshNowPlaying(delay=500) {
    logDebug "boseRefreshNowPlaying(delay: ${delay})"

    if (delay > 0) {
        return ["delay ${delay}", boseGET("/now_playing")]
    }
    return boseGET("/now_playing")
}

def bosePlayTrack(url, vol="40", service="") {    
    logDebug "bosePlayTrack(url: ${url})"
    if (service.isEmpty()) service = "${device.displayName}"    
    // really don't know where the key is from or if it works.
    def data = "<?xml version=\"1.0\" encoding=\"UTF-8\"><play_info><app_key>Ml7YGAI9JWjFhU7D348e86JPXtisddBa</app_key><url>"+url+"</url><service>"+service+"</service><volume>"+vol+"</volume></play_info>" 

    bosePOST("/speaker", data)
}

/**
* Requests the list of presets
*
* @return command
*/
def boseGetPresets() {
    logTrace "boseGetPresets()"

    return boseGET("/presets")
}

/**
* Requests the info page
*
* @return command
*/
def boseGetInfo() {
    logTrace "boseGetInfo()"

    return boseGET("/info")
}

/**
* Utility function, makes GET requests to BOSE device
*
* @param path What endpoint
*
* @return command
*/
def boseGET(String path) {
    logTrace "Executing 'boseGET(${path})'"

    def hubAction 	
    try {
        def param = [
            method: "GET",
            path: path,
            headers: [HOST: getDeviceIP() + ":" + getDevicePort(), 'Content-Type':'text/xml; charset="utf-8"']]
        logTrace "boseGET param: ${param}"
        hubAction = hubitat.device.HubAction.newInstance(param)
    }
    catch (Exception e) {
        logError "boseGET() $e on $hubAction"
    }
    if (hubAction) {
        try {
            sendHubCommand( hubAction )
        }
        catch (Exception e) {
            logError "boseGET() $e on $sendHubCommand"
        }
    }    
}

/**
* Utility function, makes a POST request to the BOSE device with
* the provided data.
*
* @param path What endpoint
* @param data What data
* @param address Specific ip and port (optional)
*
* @return command
*/
def bosePOST(String path, String data, String address=null) {
    logTrace "Executing 'bosePOST(${path})' data: ${data}"

    def hubAction 	
    try {
        def param = [
            method: "POST",
            path: path,
            body: data,
            headers: [HOST: address ?: (getDeviceIP() + ":" + getDevicePort()), 'Content-Type':'text/xml; charset="utf-8"']]
        logTrace "bosePOST param: ${param}"
        hubAction = hubitat.device.HubAction.newInstance(param)
    }
    catch (Exception e) {
        logError "bosePOST() $e on $hubAction"
    }
    if (hubAction) {
        try {
            sendHubCommand( hubAction )
        }
        catch (Exception e) {
            logError "bosePOST() $e on $sendHubCommand"
        }
    }
}

/**
* Queues a callback function for when a specific XML root is received
* Will execute on subsequent parse() call(s), never on the current
* parse() call.
*
* @param root The root node that this callback should react to
* @param func Name of the function
* @param param Parameters for function (optional)
*/
def queueCallback(String root, String func, param=null) {
    logTrace "queueCallback root:$root func:$func param:$param"
    
    if (g_mStatePending[device.getId()]==null)
        g_mStatePending[device.getId()] = [:]
    if (g_mStatePending[device.getId()][root]==null)
        g_mStatePending[device.getId()][root] = []
    g_mStatePending[device.getId()][root] << ["$func":"$param"]
    
    logTrace "queueCallback pending:${g_mStatePending[device.getId()]}"
}

/**
* Transfers the pending callbacks into readiness state
* so they can be executed by processCallbacks()
*
* This is needed to avoid reacting to queueCallbacks() within
* the same loop.
*/
def prepareCallbacks() {    
    logTrace "prepareCallbacks ${g_mStatePending[device.getId()]}"
    
    if (g_mStatePending[device.getId()]==null || g_mStatePending[device.getId()].isEmpty())
        return
    if (g_mStateReady[device.getId()]==null)
        g_mStateReady[device.getId()] = [:]
    g_mStateReady[device.getId()] << g_mStatePending[device.getId()]
    g_mStatePending[device.getId()] = [:]
}

/**
* Executes any ready callback for a specific root node
* with associated parameter and then clears that entry.
*
* If a callback returns data, it's added to a list of
* commands which is returned to the caller of this function
*
* Once a callback has been used, it's removed from the list
* of queued callbacks (ie, it executes only once!)
*
* @param xml The XML data to be examined and delegated
* @return list of commands
*/
def processCallbacks(xml) {
    logTrace "processCallbacks(xml: ${xml})"

    def result = []

    if (g_mStateReady[device.getId()]==null || g_mStateReady[device.getId()].isEmpty())
    return result

    if (g_mStateReady[device.getId()][xml.name()]) {
        g_mStateReady[device.getId()][xml.name()].each { callback ->
            callback.each { func, param ->
                if (func != "func") {
                    logTrace "**** processCallbacks: ${func}"
                    if (param)
                        result << "$func"(xml, param)
                    else
                        result << "$func"(xml)
                }
            }
        }
        g_mStateReady[device.getId()].remove(xml.name())
    }
    return result.flatten()
}

/**
* State managament for the Play Everywhere zone.
* This is typically called from the parent.
*
* A device is either:
*
* null = Not participating
* server = running the show
* client = under the control of the server
*
* @param newstate (see above for types)
*/
def boseSetZone(String newstate) {
    logTrace "boseSetZone(newstate: ${newstate})"

    state.zone = newstate

    // Refresh our state
    if (newstate) {
        sendEvent(name:"everywhere", value:"leave")
    } else {
        sendEvent(name:"everywhere", value:"join")
    }
}

/**
* Used by the Everywhere zone, returns the current state
* of zone membership (null, server, client)
* This is typically called from the parent.
*
* @return state
*/
def boseGetZone() {
    return state.zone
}

/**
* Returns the IP of this device
*
* @return IP address
*/
def getDeviceIP() {
    return settings.deviceIp
}

def getDevicePort() { 
    return "8090"
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
