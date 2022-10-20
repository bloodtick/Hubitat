/**
*  Copyright 2021 Bloodtick
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
*  Fully Kiosk Browser Device
*  Thanks to Arn Burkhoff similar driver concerning TTS functions. 
*
*  Update: Bloodtick Jones
*  Date: 2021-10-24
*
*  2.0.00 2021-10-24 First complete rewrite pass removing all ST components. Added javascript from webCoRE solution for local (yeah!).
*
*/

import groovy.json.*
import java.net.URLEncoder
import groovy.transform.Field

private getVersionNum()   { return "2.0.00" }
private getVersionLabel() { return "Fully Kiosk Browser Device, version ${getVersionNum()}" }

// STATICALLY DEFINED VARIABLES
@Field static final String sNOIMAGE  = 'Image Capture Disabled'
@Field static final String sBLUE     = '#1a77c9'
@Field static final String sRED      = '#cc2d3b'

// IN-MEMORY VARIABLES (Cleared only on HUB REBOOT or CODE UPDATES)
// Once cleared, will run 'initialize' to rebuild maps on next action
@Field volatile static Map<String,Map> deviceInfo = [:]
@Field volatile static Map<String,Map> listSettings = [:]
@Field volatile static Map<String,List> eventList = [:]
@Field volatile static Map<String,Integer> txCounter = [:]
@Field volatile static Map<String,Integer> rxCounter = [:]
@Field volatile static Map<String,Integer> myCounter = [:]
@Field volatile static Map<String,Long> takeTimer = [:]
@Field volatile static Map<String,Boolean> isRunning = [:]
@Field volatile static Map<String,Boolean> isInitialized = [:]

metadata {
    definition (name: "Fully Kiosk Browser Device", namespace: "hubitat", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/fullyKioskBrowserDevice.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Sensor"
        capability "Refresh"
        capability "Battery"
        capability "SpeechSynthesis"
        capability "ImageCapture"
        capability "MotionSensor"
        capability "Tone"
        capability "Alarm"
        capability "AudioNotification"
        capability "AccelerationSensor"
        capability "PowerSource"
        capability "TemperatureMeasurement"
        capability "AudioVolume"

        attribute "wifiSignalLevel", "number"
        //attribute "volume", "number"
        attribute "screen", "string"
        attribute "screensaver", "string"
        attribute "currentPage", "string"
        //attribute "image", "string"
        attribute "image-public", "string"
        attribute "version", "string"
        attribute "status", "string"
        //attribute "alarm", "string"
        //attribute "temperature", "number"
        attribute "speak", "string"

        command "screenOn"
        command "screenOff"
        command "startScreensaver"
        command "stopScreensaver"
        command "triggerMotion"
        command "toForeground"
        command "fetchSettings"
        command "fetchInfo"
        command "loadStartURL"
        command "loadURL", [[name: "Load URL*", type: "STRING", description: "Absolute URI/URL to load"]]
        command "setStreamVolume", [[name: "Volume*", type: "NUMBER", description: "Volume level (0 to 100)"], [name: "Stream", type: "NUMBER", description: "Stream (1 to 10)"]]
        command "setScreenBrightness", [[name: "Level*", type: "NUMBER", description: "Brightness level (0 to 100)"]]
        command "setOnDuration", [[name: "Seconds*", type: "NUMBER", description: "Duration in seconds, 0 is disable"]]
        command "setStringSetting", [[name: "Setting Key*", type: "STRING", description: "Fully Kiosk Browser Setting Key"], [name: "Value*", type: "STRING", description: "Fully Kiosk Browser Setting Value"]]
        command "setBooleanSetting", [[name: "Setting Key*", type: "STRING", description: "Fully Kiosk Browser Setting Key"], [name: "Value*", type: "STRING", description: "Fully Kiosk Browser Setting Value [true|false]"]]
        command "getStringSetting", [[name: "Setting Key*", type: "STRING", description: "Fully Kiosk Browser Setting Key"]]        
        command "getBooleanSetting", [[name: "Setting Key*", type: "STRING", description: "Fully Kiosk Browser Setting Key"]]
        command "sendCommand", [[name: "Command Key*", type: "STRING", description: "Fully Kiosk Browser Commands"]]
        command "speechTestAction"
        command "getCamshot"
        command "getScreenshot"
        command "chime"
        command "alarm"
        command "playSound", [[name: "Track URI*", type: "STRING", description: "URI/URL of sound to play"]]
        command "stopSound"
        command "alarmOff"
        command "setMediaVolume", [[name: "Volume*", type: "NUMBER", description: "Volume level (0 to 100)"]]
        command "setAlarmVolume", [[name: "Volume*", type: "NUMBER", description: "Volume level (0 to 100)"]]
        command "setNotifyVolume", [[name: "Volume*", type: "NUMBER", description: "Volume level (0 to 100)"]]        
        command "setTakeTimeout", [[name: "Seconds*", type: "NUMBER", description: "Duration in seconds, 0 is disable"]]
    }
}

preferences {
    input(name:"deviceIp", type:"text", title: "<b style='color:DarkBlue'>Device IP Address:</b>", description: "<i>Static IP Address is required</i>", defaultValue: "127.0.0.1", required: true)
    input(name:"devicePort", type:"number", title: "<b style='color:DarkBlue'>Device IP Port:</b>", description: "<i>Default is port 2323</i>", range: "1..65535", defaultValue: "2323", required: true)
    input(name:"devicePassword", type:"string", title:"<b style='color:DarkBlue'>Fully Kiosk Browser Password:</b>", description: "<i>Password is required</i>", required: true)
    
    input(name:"deviceMediaUrl", type:"string", title:"<b>Audio Media URL (Chime):</b>", defaultValue:"", required:false)
    input(name:"deviceAlarmUrl", type:"string", title:"<b>Audio Alarm URL:</b>", defaultValue:"", required:false)
    input(name:"deviceAllowScreenOff", type: "bool", title: "<b>Allow Screen Off and On Commands:</b>", description: "<i>Diverts screen off and on commands to screensaver on and off commands. Default to <b>off</b> for Fire tablets</i>", defaultValue: "false", required:false)
    
    input(name:"deviceMediaVolume", type:"number", title:"<b>Media Volume (Chime):</b>", range: "0..100", defaultValue:"100", required:false)
    input(name:"deviceAlarmVolume", type:"number", title:"<b>Alarm Volume:</b>", range: "0..100", defaultValue:"100", required:false)
    input(name:"deviceNotfyVolume", type:"number", title:"<b>Notify Volume (TTS):</b>", range: "0..100", defaultValue:"75", required:false)
    
    input(name:"deviceMediaStream", type:"number", title:"<b>Media Stream (1-9, default 3):</b>", description: "<i>Media is 3 on Fire HD</i>", range: "1..9", defaultValue:"3", required:false)
    input(name:"deviceAlarmStream", type:"number", title:"<b>Alarm Stream (1-9, default 3):</b>", description: "<i>Alarm is 4 on Fire HD, but use 3 with FKB</i>", range: "1..9", defaultValue:"3", required:false)
    input(name:"deviceNotfyStream", type:"number", title:"<b>Notify Stream (1-9, default 3):</b>", description: "<i>Notify is 2 on Fire HD, but use 3 with FKB</i>", range: "1..9", defaultValue:"3", required:false)
            
    input(name:"deviceOnDuration", type:"number", title:"<b>On Duration:</b>", description: "<i>Turn screen/screensaver off after some idle seconds, set 0 for disabled</i>", range: "0..", defaultValue:"0", required:false)
    input(name:"devicePollRateSecs", type: "enum", title: "<b>Keep Alive Rate:</b>", description: "<i>Query FKB on defined interval. Default is 5 minutes</i>", options: getTimeList3(), defaultValue:300, required: false)
    input(name:"deviceTTSVoices", type:"enum", title:"<b>TTS Poly Voices:</b>", description: "<i>AWS poly voice id. Sends text to FKB TTS engine with No Selection</i>", options: getVoicesList(), defaultValue:null, required:false)

    input(name:"deviceMotionTimeout", type:"enum", title:"<b>Motion Timeout:</b>", description: "<i>Number of seconds before motion is inactive</i>", options: getTimeList2(), defaultValue:0, required:true)
    input(name:"deviceAccelTimeout", type:"enum", title:"<b>Acceleration Timeout:</b>", description: "<i>Number of seconds before acceleration is inactive</i>", options: getTimeList2(), defaultValue:0, required:true)
    input(name:"deviceTakeTimeout", type:"enum", title:"<b>Image Capture Timeout or Disable:</b>", description:"<i>Number of seconds between image capture during motion. Default is disabled, suggest updating 'event history size' to less than 5 before enabling</i>", options: getTimeList1(), defaultValue:0, required:true)
    
    if (deviceTakeTimeout && deviceTakeTimeout.toInteger()) {
        input(name:"deviceS3url", type:"string", title:"AWS Image Lambda URL (optional):", defaultValue: "")
        input(name:"deviceS3key", type:"string", title:"AWS Image Lambda X-Api-Key (optional):", defaultValue: "")
        input(name:"deviceS3ret", type: "bool", title: "AWS Image Store (optional):", description: "<i>Store image in a public accessible location</i>", defaultValue: false)
    }    
    
    input(name:"deviceInfoEnable", type: "bool", title: "<b>Enable info logging:</b>", defaultValue: true)
    input(name:"deviceDebugEnable", type: "bool", title: "<b>Enable debug logging:</b>", defaultValue: false) 
    input(name:"deviceTraceEnable", type: "bool", title: "<b>Enable trace logging:</b>", defaultValue: false)
}

def getTimeList1() {
    [0:"Disable", 1:"1 second", 5:"5 seconds", 10:"10 seconds", 20:"20 seconds", 30:"30 seconds", 60:"60 seconds"]
}
def getTimeList2() {
    [0:"Disable", 1:"1 second", 5:"5 seconds", 10:"10 seconds", 20:"20 seconds", 30:"30 seconds", 60:"1 minute", 120:"2 minutes", 300:"5 minutes", 600:"10 minutes", 900:"15 minutes"]
}
def getTimeList3() {
    [0:"Disable", 30:"30 seconds", 60:"1 minute", 120:"2 minutes", 300:"5 minutes", 600:"10 minutes", 900:"15 minutes"]
}
def getVoicesList(){
	def voices = getTTSVoices()
	voices.sort{ a, b ->
		a.language <=> b.language ?: a.gender <=> b.gender ?: a.gender <=> b.gender  
	}    
    def list = voices.collect{ ["${it.name}": "${it.language}: ${it.name} (${it.gender})"] }
	return list
}

def installed() {
    sendEvent(name: "mute", value: "unmuted", descriptionText: "${device.displayName} volume is unmuted")
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
    sendEvent(name: "battery", value: "100", unit: "%", descriptionText: "${device.displayName} battery is 100%")
    sendEvent(name: "level", value: "0", unit: "%", descriptionText: "${device.displayName} screen brightness is 0%")
    sendEvent(name: "motion", value: "inactive", descriptionText: "${device.displayName} motion is inactive")
    sendEvent(name: "acceleration", value: "inactive", descriptionText: "${device.displayName} acceleration is inactive")
    sendEvent(name: "powerSource", value: "unknown", descriptionText: "${device.displayName} powerSource is unknown")
    sendEvent(name: "alarm", value: "off", descriptionText: "${device.displayName} alarm is off")
    sendEvent(name: "temperature", value: "0", unit: ("°"+location.getTemperatureScale()), descriptionText: "${device.displayName} temperature is ${("0°"+location.getTemperatureScale())}")
    sendEvent(name: "volume", value: settings.deviceNotfyVolume, unit: "%", descriptionText: "${device.displayName} volume is ${settings.deviceNotfyVolume}%")
    logInfo logColor(sBLUE, "${device.displayName} executing 'installed()' with settings: ${settings}")
    initialize()
}

def updated() {
    logDebug "${device.displayName} executing 'updated()' with new preferences: ${settings}"
    initialize()
}

def initialize() {
    logInfo logColor(sBLUE, "${device.displayName} executing 'initialize()'")
    String appId = device.getId()
    
    unschedule()
    state.remove("deviceInfo") // this could exist if trace enabled
    state.remove("listSettings") // this could exist if trace enabled
    isInitialized[appId] = true
    deviceInfo[appId] = ['status': false]
    listSettings[appId] = ['status': false]
    clrEvents()

    if (setNetworkId()) {
        // start collecting listSettings and deviceInfo from device.     
        fetchSettings()
        fetchInfo()
        runEvery5Minutes(fetchSettings)
        runIn(5, "initialize2") // delay so checkReady() should be true        
    } else {        
        fetchInfo()
        runIn(30, "initialize") // if the MAC wasn't in the ARP table, we need to wait for it
    }
        
    if(advLogsActive()) { runIn(1800, "logsOff") }
}

def initialize2() {
    logInfo "${device.displayName} executing 'initialize2()'"

    if (settings.deviceTakeTimeout=="0") {
        sendEvent(name: "image", value: sNOIMAGE)
        sendEvent(name: "image-public", value: sNOIMAGE)
    }
    onMotionStop()
    onMovementStop()
    
    if (checkReady()) {
        
        Boolean motion = (settings.deviceTakeTimeout.toInteger()>0 || settings.deviceMotionTimeout.toInteger()>0)
        Boolean movement = (settings.deviceAccelTimeout.toInteger()>0)
        
        setBooleanSetting("websiteIntegration","true")
        setBooleanSetting("motionDetection", (motion ? "true" : "false"))
        setBooleanSetting("movementDetection", (movement ? "true" : "false"))
        setBooleanSetting("ignoreMotionWhenScreensaverOnOff","true")
        setStringSetting("webviewMixedContent","0")
        setStringSetting("screensaverBrightness","0")
        setStringSetting("actionBarTitle", device.displayName)
        if (setStringSetting("injectJsCode", getJavaScript())) {
            if (settings.deviceAllowScreenOff==true) on() // the application is sometimes sleeping in the background when screen off.
            loadStartURL() // refresh to activate the javascript
        }
        setOnDuration()
    } else {
        String appId = device.getId()
        logInfo logColor(sRED, "${device.displayName} is not ready with deviceInfo:${deviceInfo[appId].status} listSettings:${listSettings[appId].status} ")
        fetchSettings()
        fetchInfo()
        runIn(30, "initialize2") // loop so checkReady() should be true
    } 
}

Boolean setNetworkId() {
    Boolean ret = false
    def macAddress = getMACFromIP(settings.deviceIp)
    if (settings.deviceIp != "127.0.0.1" && macAddress) {        
        device.deviceNetworkId = macAddress
        logInfo "${device.displayName} network id is ${device.deviceNetworkId}"
        ret = true
    } else {
        logInfo logColor(sRED, "${device.displayName} was unable to fetch MAC with network IP:${settings.deviceIp}")
    }
    return ret
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "${device.displayName} disabling debug logs"
}
Boolean advLogsActive() { return ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) }

def on() {
    screenOn()  
}

def off() {
    if (device.currentValue("alarm") != "off") { 
        alarmOff()
    } 
    else screenOff()
}

def setOnDuration(seconds=settings.deviceOnDuration) {
    if (settings.deviceAllowScreenOff==false) {
        setScreensaverTimeout(seconds)
        setScreenOffTimeout(0)
    } else {
        setScreensaverTimeout(0)
        setScreenOffTimeout(seconds)
    }
    device.updateSetting("deviceOnDuration", [value: seconds, type:"number"])
}

def take() {
    String appId = device.getId()
    // wait settings.deviceTakeTimeout seconds between allowing takes. '0' is disabled.
    def take = takeTimer[appId]
    logDebug "${device.displayName} executing 'take()' duration:${ (new Date().getTime())-take }"
    if (settings.deviceTakeTimeout.toInteger() && ( (new Date().getTime()) - take > settings.deviceTakeTimeout.toLong() * 1000)) {
        getCamshot()        
        takeTimer[appId] = (new Date().getTime())
    }
}

def setTakeTimeout(seconds) {
    device.updateSetting("deviceTakeTimeout", [value: seconds, type:"number"])
}

def setLevel(level) {
    runIn(1,"setScreenBrightness",[data:level])
}

def setScreenBrightness(level) {
    String value = Math.round(level.toInteger()*2.55).toString()
    String appId = device.getId()
    logDebug "setScreenBrightness level: ${level}% (${value})"
    
    if (setStringSetting("screenBrightness", value)) {
        if (device.currentValue("switch") == "on") { 
            deviceInfo[appId].screenBrightness = value
            if (device.currentValue("level") != "${level}") {
                sendEvent(name: "level", value: "${level}", unit: "%", descriptionText: "${device.displayName} screen brightness is ${level}%")
            }
        }
    }
}

def speechTestAction() {
    speak("Fully Kiosk Browser Speaking Test on ${device.displayName}", 80)
}

def getCamshot() {
    if (getBooleanSetting("remoteAdmin") && getBooleanSetting("remoteAdminCamshot") && getBooleanSetting("motionDetection")) {
        sendCommand("getCamshot")
    } else {
        logInfo logColor(sRED, "${device.displayName} getCamshot misconfigured - remoteAdmin:${getBooleanSetting("remoteAdmin")} remoteAdminCamshot:${getBooleanSetting("remoteAdminCamshot")} motionDetection:${getBooleanSetting("motionDetection")}")
    }
}

def getScreenshot() {
    if (getBooleanSetting("remoteAdmin") && getBooleanSetting("remoteAdminScreenshot")) {
        if (device.currentValue("switch") != "on") {
            on() // must be 'on' otherwise you get the screensaver black. then delay a bit.
            runIn(5, "sendCommand", [data:"getScreenshot"])
        } else {
            sendCommand("getScreenshot")
        }
    } else {
        logInfo logColor(sRED, "${device.displayName} getScreenshot misconfigured - remoteAdmin:${getBooleanSetting("remoteAdmin")} remoteAdminScreenshot:${getBooleanSetting("remoteAdminScreenshot")}")
    }
}

def playText(text, level=settings.deviceNotfyVolume)
{
    speak(text, level)
}

def playTextAndRestore(text, level=settings.deviceNotfyVolume)
{
    playText(text, level)
}

def playTextAndResume(text, level=settings.deviceNotfyVolume)
{
    playText(text, level)
}

def playTrack(trackuri, level=settings.deviceMediaVolume)
{
    logDebug "${device.displayName} executing 'playTrack(${trackuri},${level})'"
    setMediaVolume(level)
    playSound(trackuri)
}

def playTrackAndRestore(trackuri, level=settings.deviceMediaVolume)
{
    playTrack(trackuri, level)
}

def playTrackAndResume(trackuri, level=settings.deviceMediaVolume)
{
    playTrack(trackuri, level)
}

def setScreensaverTimeout(value) {
    setStringSetting("timeToScreensaverV2", "${value}")
}

def setScreenOffTimeout(value) {
    setStringSetting("timeToScreenOffV2", "${value}")
}

def fetchSettings() {
    sendCommand("listSettings")
}

def fetchInfo() {
    sendCommand("getDeviceInfo")
}

def screenOn() {
   //if (settings.deviceAllowScreenOff==false) return stopScreensaver()
   stopScreensaver()
   if (device.currentValue("screen") != "on") {
       sendCommand("screenOn")
   }
}

def screenOff() {
    if (settings.deviceAllowScreenOff==false) 
        return startScreensaver()
    if (device.currentValue("screen") == "on") {
        sendCommand("screenOff")
    }
}

def startScreensaver() {
    if (device.currentValue("screensaver") != "on") {
        sendCommand("startScreensaver")
    }
}

def stopScreensaver() {
    if (device.currentValue("screensaver") == "on") {
        sendCommand("stopScreensaver")
    }
}

def triggerMotion() {
    sendCommand("triggerMotion")
}

def toForeground() {
    sendCommand("toForeground")
}

def loadStartURL() {
    sendCommand("loadStartURL")
}

def loadURL(String value) {
    addEvent(["command", "loadURL", null, "cmd=loadURL&url=${value}"])
}


def volumeUp() {
    setVolume(Math.round((device.currentValue("volume") + 10)/10)*10)
}

def volumeDown() {
    setVolume(Math.round((device.currentValue("volume") - 10)/10)*10)
}

def setVolume(level) {
    if (level>=0 && level<=100) {
        sendEvent(name: "volume", value: "${level}", unit: "%", descriptionText: "${device.displayName} volume is ${level}%")
        unmute()
        runIn(1,"setNotifyVolume",[data:level])
    }
}

def setStreamVolume(level, stream=settings.deviceNotfyStream) {
    if (level>=0 && level<=100 && stream>=1 && stream<=10) {
        if (stream==settings.deviceNotfyStream) {
            sendEvent(name: "volume", value: "${level}", unit: "%", descriptionText: "${device.displayName} volume is ${level}%")
        }
        addEvent(["command", "setAudioVolume", null, "cmd=setAudioVolume&level=${level}&stream=${stream}"])
    }
}

def mute() {    
    sendEvent(name: "mute", value: "muted", descriptionText: "${device.displayName} volume is muted")
    alarmOff()
    stopSound()    
}

def unmute() {
    sendEvent(name: "mute", value: "unmuted", descriptionText: "${device.displayName} volume is unmuted")
}

def setMediaVolume(level=settings.deviceMediaVolume) {
    if (level>=0 && level<=100) {
        setStreamVolume(level, settings.deviceMediaStream)
        device.updateSetting("deviceMediaVolume", [value: level, type:"number"])
    }    
}

def setAlarmVolume(level=settings.deviceAlarmVolume) {
    if (level>=0 && level<=100) {
        setStreamVolume(level, settings.deviceAlarmStream)
        device.updateSetting("deviceAlarmVolume", [value: level, type:"number"])
    }
}

def setNotifyVolume(level=settings.deviceNotfyVolume) {
    if (level>=0 && level<=100) {
        setStreamVolume(level, settings.deviceNotfyStream)
        device.updateSetting("deviceNotfyVolume", [value: level, type:"number"])
    }
}

def speak(String text, level=settings.deviceNotfyVolume, voice=settings.deviceTTSVoices) {
    logDebug "${device.displayName} executing 'speak(${text},${level},${voice})'"
    
    if (device.currentValue("mute") != "muted") {
        if (voice==null) {
            setNotifyVolume(level)
            addEvent(["command", "textToSpeech", "${text}", "cmd=textToSpeech&text=${URLEncoder.encode(text, "UTF-8")}"])
        } else {
            setStreamVolume(level, settings.deviceMediaStream)
            def tts = textToSpeech(text, voice)
            playSound(tts.uri)
            sendEvent(name: "speak", value: text, descriptionText: "TTS: ${voice} is speaking")
            logInfo "${device.displayName} has ${voice} speaking '${text}' as ${tts.uri}"
        }
    }
}

def strobe() {
    alarm()
}

def siren() {
    alarm()
}

def both() {
    alarm()
}

def alarm() {
    if(settings?.deviceAlarmUrl?.length()) {
        unmute()
        setAlarmVolume()
        sendEvent(name: "alarm", value: "siren", descriptionText: "${device.displayName} alarm is siren")
        addEvent(["command", "alarm", "${url}", "cmd=playSound&url=${settings.deviceAlarmUrl}&loop=true"])
    } else logInfo "${device.displayName} alarm URL is not set"
}

def alarmOff() {
    stopSound()
    sendEvent(name: "alarm", value: "off", descriptionText: "${device.displayName} alarm is off")
}

def chime() {
    beep()
}    

def beep() {
    if(settings?.deviceMediaUrl?.length()) {
        setMediaVolume()
        sendEvent(name: "alarm", value: "off", descriptionText: "${device.displayName} alarm is off")
        playSound(settings.deviceMediaUrl)
    } else logInfo "${device.displayName} media URL is not set"
}

def playSound(String url) {
    unmute()
    addEvent(["command", "playSound", "${url}", "cmd=playSound&url=${url}"])
}

def stopSound() {    
    sendCommand("stopSound")
}

def sendCommand(value) {
    addEvent(["command", value, null, "cmd=${URLEncoder.encode(value, "UTF-8")}"])
}

def setStringSetting(String key, String value, refresh=1) {
    logDebug "Executing 'setStringSetting()' key:${key} value:${value} refresh:${refresh}"
    Boolean response = false
    String appId = device.getId()
    if (checkReady() && getStringSetting(key)!=value) {
        listSettings[appId][key] = value
        addEvent(["setStringSetting", key, value, "cmd=setStringSetting&key=${key}&value=${URLEncoder.encode(value, "UTF-8")}"])
        response = true
    }
    refresh ?: runIn(refresh, fetchSettings)
    return response
}

def setBooleanSetting(String key, String value, refresh=1) {
    logDebug "Executing 'setBooleanSetting()' key:${key} value:${value} refresh:${refresh}"
    Boolean response = false
    String appId = device.getId()
    if (checkReady() && getBooleanSetting(key)!=value.toBoolean()) {
        listSettings[appId][key] = value.toBoolean()
        addEvent(["setBooleanSetting", key, value, "cmd=setBooleanSetting&key=${key}&value=${URLEncoder.encode(value, "UTF-8")}"])
        response = true
    }
    refresh ?: runIn(refresh, fetchSettings)
    return response
}

def getStringSetting(String key) {
    String appId = device.getId()
    if (checkReady() && listSettings[appId]!=null && listSettings[appId].get(key)!=null) {
        return listSettings[appId].get(key)
    }
    return "false"
}

def getBooleanSetting(String key) {
    return (getStringSetting(key).toBoolean())
}

def refresh() {
    logDebug "${device.displayName} executing 'refresh()'"
    fetchInfo()
}

Boolean checkInitialized() {
    Boolean response = false
    String appId = device.getId()
    try {
        if (isInitialized[appId]) {
            response = true
        }
    } catch (e) {}
    
    if (!response) {
        runIn(1, "initialize")
    }    
    return response
}

Boolean checkReady() {
    String appId = device.getId()
    return deviceInfo[appId]?.status && listSettings[appId]?.status
}

def clrEvents() {
    String appId = device.getId()
    if(eventList[appId]) {
        logInfo "${device.displayName} clearing command queue: ${eventList[appId]}"
        sendEvent(name: "status", value: "offline", descriptionText: "${device.displayName} is offline and deleted ${eventList[appId].size()} command(s)")
    }
    eventList[appId]=[]
    isRunning[appId]=false
    myCounter[appId]=0
    txCounter[appId]=0
    rxCounter[appId]=0
    takeTimer[appId]=0
}

def addEvent(event) {
    if (!checkInitialized()) return false // check to see if we are intialized. Needed for static objects that are no longer state variables.
    String appId = device.getId()
    
    myCounter[appId]+=1
    def eventList = eventList[appId]
    eventList << [type:event[0], key:event[1], value:event[2], postCmd:event[3], sequence:myCounter[appId]]
    logTrace "Event list is: ${eventList}"
    runIn(20, clrEvents) // watchdog: needs to be less then settings.devicePollRateSecs
    if (isRunning[appId]==false) {
        //unschedule(clrEvents)
        unschedule(runPostCmd)
        sendPostCmdDelay()
    }
    return true
}

def pullEvent(removeEvent=true) {
    String appId = device.getId()
    isRunning[appId]=true
    def eventList = eventList[appId]    
    if (eventList.size() == 0) {
        isRunning[appId]=false
        return null
    }
    def event = eventList[0]
    if (removeEvent) eventList.remove(0)
    logTrace "Event list is: ${eventList} after ${removeEvent ? "pulling" : "peaking"}: ${event}"    
    return event
}

def peakEvent() {
    return pullEvent(false)
}

def sendPostCmdDelay() {
    runInMillis(200, sendPostCmd) // Hubitat is actually faster.
}

def runPostCmd() {
    String appId = device.getId()
    if (peakEvent()) {
        logInfo "Running sendPostCmd again since queue was not cleared"
        txCounter[appId]=-1
    }        
    sendPostCmd()
}

def sendPostCmd() {    
    def event = peakEvent()
    String appId = device.getId()    
    // have I seen this event already?
    if (event && txCounter[appId]!=event.sequence) {
        txCounter[appId]=event.sequence
        // not a best practice but if we didnt get a parse event this will retry the command
        // until the queue gets cleared in 10 seconds. appears to work okay. 
        runIn(10, runPostCmd) 

        def cmd = "?type=json&password=${devicePassword}&" + event.postCmd
        logDebug "tx: ${txCounter[appId]} :: (${cmd})"

        def param = [
            method: "POST",
            path: cmd,
            headers: [HOST: "${settings.deviceIp}:${settings.devicePort}"] + ["X-Request-ID": UUID.randomUUID().toString()]] //nanohttpd doesn't seem to support X-Request-ID
        def hubAction = hubitat.device.HubAction.newInstance(param)
        hubAction.options = (event.key=="getCamshot" || event.key=="getScreenshot") ? [callback:parseHubitatImage] : [callback:parseHubitat]
        if (hubAction) {
            try {
                sendHubCommand( hubAction )
            }
            catch (Exception e) {
                logError "sendPostCmd() $e"
            }
        }
    }
    return event
}

def getJavaScript() {
    return ("""
    var loc;
    function mySend(value) {
        var xhr=new XMLHttpRequest();
        xhr.timeout=3000;
        xhr.open('POST',"http://${location.hub.localIP}:39501",true);
        xhr.send(JSON.stringify({command:value}));
        delete xhr;
    }
    fully.bind('onDaydreamStop',"mySend('onDaydreamStop');");
    fully.bind('onDaydreamStart',"mySend('onDaydreamStart');");
    fully.bind('onScreensaverStop',"mySend('onScreensaverStop');");
    fully.bind('onScreensaverStart',"mySend('onScreensaverStart');");
    fully.bind('screenOn',"mySend('screenOn');");
    fully.bind('screenOff',"mySend('screenOff');");
    fully.bind('unplugged',"mySend('unplugged');");
    fully.bind('pluggedAC',"mySend('plugged');");
    fully.bind('pluggedUSB',"mySend('plugged');");
    fully.bind('pluggedWireless',"mySend('plugged');");
    fully.bind('onBatteryLevelChanged',"mySend('onBatteryLevelChanged');");
    fully.bind('internetReconnect',"mySend('internetReconnect');");
    fully.bind('onMovement',"mySend('onMovement');");
    fully.bind('onMotion',"mySend('onMotion');");
    setInterval(function() {if(location.href!=loc) {mySend('onUrlChange'); loc=location.href;}},2000);
    """).replaceAll("\t", " ");
}

def parse(description) {
    if (!checkInitialized()) return // check to see if we are intialized. Needed for static objects that are no longer state variables.
    
    def msg = parseLanMessage(description)
    logTrace "parse() msg: ${msg}"
    def body = [:]
    if (msg?.body) {        
        try {
            body = new JsonSlurper().parseText(msg.body)
        }
        catch (Exception e) {
            logTrace "parseHubitat() exception ignored: $e"
        }
    }
    if (body?.command) {
        logDebug "Received ${body.command}"
        switch (body.command) {            
            case "onMovement":
                if (settings.deviceAccelTimeout.toInteger()>0) {
                    sendEvent(name: "acceleration", value: "active", descriptionText: "${device.displayName} acceleration is active")
                    runIn(settings.deviceAccelTimeout.toInteger(), "onMovementStop")
                }
                break
            case "onMotion":
                if (settings.deviceMotionTimeout.toInteger()>0) {
                    sendEvent(name: "motion", value: "active", descriptionText: "${device.displayName} motion is active")
                    runIn(settings.deviceMotionTimeout.toInteger(), "onMotionStop")
                }
                take()
                break
            case "onDaydreamStop":
            case "onScreensaverStop":
            case "screenOn":
                take()
                runIn(1,on)
                break            
            case "onDaydreamStart":            
            case "onScreensaverStart":
            case "screenOff":
                runIn(1,off)
                break            
            case "internetReconnect":
                logInfo logColor(sBLUE, "${device.displayName} reconnected to Internet")
                runIn(10,refresh) // sometimes sends a couple of times. not in a hurry to refresh.
                break
            case "unplugged":
            case "plugged":
                logInfo logColor(sBLUE, "${device.displayName} power is ${body.command}")
            case "onBatteryLevelChanged":            
            case "onUrlChange":
                runIn(1,refresh)
                break
            default:
                logWarn "Unhandled parse() command: ${body.command}"
                break
        }   
    }    
}

def onMotionStop() { sendEvent(name: "motion", value: "inactive", descriptionText: "${device.displayName} motion is inactive") }
def onMovementStop() { sendEvent(name: "acceleration", value: "inactive", descriptionText: "${device.displayName} acceleration is inactive") }

def parseHubitat(response) {
    def msg = parseLanMessage(response.description)
    logTrace "parseHubitat() msg: ${msg}"
    if (msg?.status == 200) {        
        try {
            def body = new JsonSlurper().parseText(msg.body)
            decodePostResponse(body)
            sendEvent(name: "status", value: "online")
        }
        catch (Exception e) {
            logTrace "parseHubitat() exception: $e"
        }      
    } else {
        logError "parseHubitat() parseLanMessage did not return 200: ${msg}"
        pullEvent()
    }    

    runIn(20, clrEvents)  // watchdog: needs to be less then settings.devicePollRateSecs
    sendPostCmdDelay()
}

def parseHubitatImage(response) {    
    def event = pullEvent()
    String appId = device.getId()
    rxCounter[appId]+=1

    def msg = parseLanMessage( response.description )
    msg.remove("body")
    logTrace "parseHubitatImage() msg: ${msg}"

    if( msg?.headers?.'Content-Type'.contains("image")) {
        def strImageType = msg.headers.'Content-Type'.contains("jpeg") ? 'jpeg' : 'png'
        logDebug "rx: ${rxCounter[appId]} :: image type: ${strImageType}" 
        
        try
        {
            def strBase64Image = parseDescriptionAsMap(response.description).body
            storeImage(strBase64Image, strImageType)
        }
        catch( Exception e )
        {
            logError "parseHubitatImage() $e"
        }
    }

    runIn(20, clrEvents) // watchdog: needs to be less then settings.devicePollRateSecs
    sendPostCmdDelay()
}

def decodePostResponse(body) {
    logDebug "${device.displayName} executing 'decodePostResponse()'"
    def event = pullEvent()
    String appId = device.getId()
    rxCounter[appId]+=1

    if (body.containsKey("deviceId")) {    	
        logDebug "rx: ${rxCounter[appId]} :: getDeviceInfo"

        if (event==null || event.key!="getDeviceInfo")
        logInfo "${device.displayName} getDeviceInfo event was expected but was: ${event}"        

        if(settings?.deviceLogEnable == true) {
            state.deviceInfo = body.clone()
        }
        deviceInfo[appId] = body.clone()
        deviceInfo[appId].status = true
    }
    else if (body.containsKey("actionBarTitle")) {
        logDebug "rx: ${rxCounter[appId]} :: listSettings"

        if (event==null || event.key!="listSettings")
        logInfo "${device.displayName} listSettings event was expected but was: ${event}"

        if(settings?.deviceLogEnable == true) {
            state.listSettings = body.clone()
        }
        listSettings[appId] = body.clone()
        listSettings[appId].status = true
    }
    else if (checkReady() && body?.status=="OK") {
        logDebug "rx: ${rxCounter[appId]} :: ${body.statustext}"

        if (event==null || !body.containsKey("statustext")) {
            logInfo "${device.displayName} command or setting event was expected but was: ${event}"
            runIn(2, fetchSettings)
        }
        logTrace "Processing event: ${body} with event: ${event}"
        switch (body.statustext) {
            case "Screensaver stopped":
                deviceInfo[appId].isInScreensaver = false
                deviceInfo[appId].screenBrightness = listSettings[appId].screenBrightness.toInteger()
                break;
            case "Switching the screen on":
                deviceInfo[appId].screenOn = true
                logDebug "${body.statustext}"
                break;
            case "Screensaver started":
                deviceInfo[appId].isInScreensaver = true
                deviceInfo[appId].screenBrightness = listSettings[appId].screensaverBrightness.toInteger()
                break;
            case "Switching the screen off":
                deviceInfo[appId].screenOn = false
                logDebug "${body.statustext}"
                break;            
            case "Text To Speech Ok":
                sendEvent(name: "speak", value: event.value, descriptionText: "TTS: '${event.value}'")
            default:
                logDebug "statustext: '${body?.statustext}' from event: ${event?.type}:${event?.key}"
            break;            
        }
        logInfo "${device.displayName} ${body.statustext}" 
    }
    else if (body?.status=="Error" && body?.statustext=="Please login") {
        logInfo logColor(sRED, "${device.displayName} password is not accepted")   
    }
    else {
        logWarn "unhandled event: ${event} with reponse:'${body}'"
    }

    def nextRefresh = update()
    logDebug "Refresh in ${nextRefresh} seconds"
    runIn(nextRefresh, refresh)
}

def update() {
    logDebug "${device.displayName} executing 'update()'"
    def nextRefresh = settings.devicePollRateSecs.toInteger()
    String appId = device.getId()

    if (checkReady()) {        
        def pLS = listSettings[appId]
        def pDI = deviceInfo[appId]

        sendEvent(name: "screen", value: (pDI.screenOn?"on":"off"), descriptionText: "${device.displayName} screen is ${(pDI.isInScreensaver?"on":"off")}")
        sendEvent(name: "screensaver", value: (pDI.isInScreensaver?"on":"off"), descriptionText: "${device.displayName} screensaver is ${(pDI.isInScreensaver?"on":"off")}")
        
        if (pDI.screenOn && !pDI.isInScreensaver) {

            if (device.currentValue("switch") != "on") {
                sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
                logInfo "${device.displayName} switch is on"
            }
            
            def timeout = (settings.deviceAllowScreenOff==false) ? pLS.timeToScreensaverV2.toInteger() : pLS.timeToScreenOffV2.toInteger()
            logTrace "Timeout is: ${timeout} with allow screen off: ${settings.deviceAllowScreenOff}"
            if(timeout.toInteger() && nextRefresh > timeout.toInteger())
                nextRefresh = timeout + 1          
        }
        else {
            if (device.currentValue("switch") == "on") {
                sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
                logInfo "${device.displayName} switch is off"
            }
        }

        logDebug "Brightness is: ${pDI.screenBrightness} (${pDI.screenBrightness.toInteger()*100/255}%)"
        logDebug "Screen is: ${(pDI.screenOn?"on":"off")}"
        logDebug "Screensaver is: ${(pDI.isInScreensaver?"on":"off")}"
        logDebug "Screensaver timeout is: ${pLS.timeToScreensaverV2.toInteger()} seconds"
        logDebug "Screen off timeout is: ${pLS.timeToScreenOffV2.toInteger()} seconds"

        def level = Math.round(pDI.screenBrightness.toInteger()/2.55)
        if (device.currentValue("level") != "${level}") {
            sendEvent(name: "level", value: "${level}", unit: "%", descriptionText: "${device.displayName} screen brightness is ${level}%")
        }

        sendEvent(name: "currentPage", value: "${pDI.currentPageUrl}", descriptionText: "${device.displayName} current page is ${pDI.currentPageUrl}")
        sendEvent(name: "battery", value: "${pDI.batteryLevel}", unit: "%", descriptionText: "${device.displayName} battery is ${pDI.batteryLevel}%")
        sendEvent(name: "powerSource", value: pDI.isPlugged?"mains":"battery", descriptionText: "${device.displayName} powerSource is ${(pDI.isPlugged?"mains":"battery")}")
        sendEvent(name: "version", value: "${pDI.version}", descriptionText: "${device.displayName} version is ${pDI.appVersionName}")
        sendEvent(name: "wifiSignalLevel", value: "${pDI.wifiSignalLevel}", descriptionText: "${device.displayName} wifi signal level is ${pDI.wifiSignalLevel}")
        def temperature = (location.getTemperatureScale() == "C" ? "${pDI.batteryTemperature}" : "${celsiusToFahrenheit(pDI.batteryTemperature).toInteger()}")
        sendEvent(name: "temperature", value: temperature, unit: ("°"+location.getTemperatureScale()), descriptionText: "${device.displayName} temperature is ${(temperature+"°"+location.getTemperatureScale())}")
    }
    return nextRefresh
}

def storeImage(strBase64Image, strImageType) {
    //byte[] imageBytes = strBase64Image.decodeBase64()
    //logInfo "${device.displayName} captured image type: ${strImageType} size: ${imageBytes.size()} bytes"
    
    def image = "http://${settings.deviceIp}:${settings.devicePort}/?cmd=getCamshot&password=${settings.devicePassword}&time=${new Date().getTime()}"
    //image = "<img style='height:120px;' src='${image}'/>"    
    image = "<img style='height:120px;' src='data:image/${strImageType};base64,${strBase64Image}'/>"
    sendEvent(name: "image", value: image, archivable: false, displayed: false)
    
    if(settings?.deviceS3url?.trim()) {
        def location = storeImageS3(strBase64Image, strImageType)
        sendEvent(name: "image-public", value: location&&settings.deviceS3ret ? location : noimage, displayed: false)            
    }
}

def storeImageS3(strBase64Image, strImageType) {
    logDebug "${device.displayName} executing 'storeImageS3()' to ${settings.deviceS3url}"
    def response = null
    String appId = device.getId()

    def params = [
        uri: settings.deviceS3url+'/store',
        body: JsonOutput.toJson([ 
            'device': "${device.displayName}", 
            'title': "${new Date().getTime()}"+(strImageType=='jpeg'?'.jpg':'.png'), 
            'image': "${strBase64Image}",
            'altitude': deviceInfo[appId]?.altitude,
            'latitude': deviceInfo[appId]?.locationLatitude, 
            'longitude': deviceInfo[appId]?.locationLongitude,
            'public': settings.deviceS3ret
        ])
    ]
    if(settings?.deviceS3key?.trim()) { // you don't need to use x-api-key with lambda. but good idea.
        params['headers'] = [ "X-Api-Key": settings.deviceS3key ]
    }

    try {
        httpPostJson(params) { resp ->
            resp.headers.each { logTrace "${it.name} : ${it.value}" }
            logTrace "response contentType: ${resp.contentType}"
            logTrace "response data: ${resp.data}"
            logInfo "${device.displayName} stored image to '${resp.data.location}'"
            response = resp.data.location
        }
    }
    catch (e) {
        logError "storeImageS3: $e"
    }
    return response;
}

def fetchImageS3(strImageName) { // this function is no longer used. leaving for reference
    logDebug "${device.displayName} executing 'fetchImageS3()' to ${settings.deviceS3url}"

    def params = [
        uri: settings.deviceS3url+'/fetch',
        body: JsonOutput.toJson([ 
        'device': "${device.displayName}", 
        'title': "${strImageName}", 
        ])
    ]
    if(settings?.deviceS3key?.trim()) { // you don't need to use x-api-key with lambda. but good idea.
        params['headers'] = [ "X-Api-Key": settings.deviceS3key ]
    }
    try {
        httpPostJson(params) { resp ->
            resp.headers.each { logTrace "${it.name} : ${it.value}" }
            logTrace "response contentType: ${resp.contentType}"
            //logTrace "response data: ${data}"
            if (resp?.data?.body) {
                logInfo "${device.displayName} fetched S3 image '${strImageName}'"
                byte[] imageBytes = resp.data.body.decodeBase64()
                storeImage(strImageName, imageBytes)
            }
        }
    }
    catch (e) {
        logError e
    } 
}

private parseDescriptionAsMap( description )
{
    description.split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}


//private void logInfo(String msg) { log.info logPrefix(msg, "#0299b1") }
private logInfo(msg)  { if(settings?.deviceInfoEnable == true)  { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn  "${msg}" }
private logError(msg) { log.error  "${msg}" }
//static String logColor(color, msg) { return "<span style='color:${color}'>${msg}</span>" }
static String logColor(color, msg) { return msg }

@Field static final String devVersionFLD  = "4.1.9.9"
@Field static final String devModifiedFLD = "2021-09-10"
@Field static final String sNULL          = (String)null
@Field static final String sBLANK         = ''
@Field static final String sSPACE         = ' '
@Field static final String sLINEBR        = '<br>'
@Field static final String sTRUE          = 'true'
@Field static final String sFALSE         = 'false'
@Field static final String sCLRRED        = 'red'
@Field static final String sCLRGRY        = 'gray'
@Field static final String sCLRORG        = 'orange'
@Field static final String sAPPJSON       = 'application/json'

static String logPrefix(String msg, String color = sNULL) {
    return span("Echo (v" + devVersionFLD + ") | ", sCLRGRY) + span(msg, color)
}

static String span(String str, String clr=sNULL, String sz=sNULL, Boolean bld=false, Boolean br=false) { return str ? "<span ${(clr || sz || bld) ? "style='${clr ? "color: ${clr};" : sBLANK}${sz ? "font-size: ${sz};" : sBLANK}${bld ? "font-weight: bold;" : sBLANK}'" : sBLANK}>${str}</span>${br ? sLINEBR : sBLANK}" : sBLANK }

