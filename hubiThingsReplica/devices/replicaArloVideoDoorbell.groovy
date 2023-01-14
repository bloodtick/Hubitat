/**
*  Copyright 2022 Bloodtick
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
*/
@SuppressWarnings('unused')
public static String version() {return "1.3.0"}

metadata 
{
    definition(name: "Replica Arlo Video + Doorbell", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaArloVideoDoorbell.groovy")
    {
        capability "Actuator"
        capability "Alarm"
        capability "Battery"
        capability "Configuration"
        //capability "DoubleTapableButton"
        //capability "HoldableButton"
        capability "MotionSensor"
        capability "PushableButton"
        capability "SoundSensor"
        capability "Switch"
        capability "Refresh"
        capability "VideoCapture"
        
        attribute "supportedButtonValues", "JSON_OBJECT" //capability "button" in SmartThings
        attribute "stream", "JSON_OBJECT" //capability "videoCapture,videoStream" in SmartThings
        attribute "video", "enum", ["active", "inactive"] //I just made this up to help watching active video. requires 'stream' interaction with ST. 
        
        command "alarmOff" // do this when both Alarm and Swtich capability are present
        command "switchOff" // do this when both Alarm and Swtich capability are present
        command "capture", [[name: "startTime*", type: "STRING", description: "Time, in ISO 8601 format, when the video capture should start"],
                            [name: "captureTime*", type: "STRING", description: "Video capture time, in ISO 8601 format"],
                            [name: "endTime*", type: "STRING", description: "Time, in ISO 8601 format, when the video capture should end"],
                            [name: "correlationId", type: "STRING", description: "correlationId (not required)"],
                            [name: "reason", type: "STRING", description: "reason (not required)"]]
        command "stopStream" //capability "videoStream" in SmartThings
        command "startStream" //capability "videoStream" in SmartThings
        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
    }
}
 
def installed() {
	initialize()
}

def updated() {
	initialize()    
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([ "setMotionValue":[[name:"motion*",type:"ENUM"]], "setMotionActive":[], "setMotionInactive":[],"setStreamValue":[[name:"stream*",type:"JSON_OBJECT"]], "setClipValue":[[name:"clip*",type:"JSON_OBJECT"]], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], 
              "setDoubleTappedValue":[[name:"buttonNumber",type:"NUMBER"]], "setHeldValue":[[name:"buttonNumber",type:"NUMBER"]], "setNumberOfButtonsValue":[[name:"numberOfButtons*",type:"NUMBER"]], "setPushedValue":[[name:"buttonNumber",type:"NUMBER"]], 
              "setSoundValue":[[name:"sound*",type:"ENUM"]], "setSoundDetected":[], "setSoundNotDetected":[], "setReleasedValue":[[name:"buttonNumber",type:"NUMBER"]], "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], 
              "setSupportedButtonValuesValue":[[name:"supportedButtonValues*",type:"JSON_OBJECT"]], "setAlarmValue":[[name:"alarm*",type:"ENUM"]], "setAlarmOff":[], "setAlarmBoth":[], "setAlarmSiren":[], "setAlarmStrobe":[], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setAlarmValue(String value) {
    String descriptionText = "${device.displayName} alarm is $value"
    sendEvent(name: "alarm", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAlarmOff() {
    setAlarmValue("off")
}

def setAlarmBoth() {
    setAlarmValue("both")
}

def setAlarmSiren() {
    setAlarmValue("siren")
}

def setAlarmStrobe() {
    setAlarmValue("strobe")
}

def setSoundValue(value) {
    String descriptionText = "${device.displayName} sound is $value"
    sendEvent(name: "sound", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSoundDetected() {
    setSoundValue("detected")
}

def setSoundNotDetected() {
    setSoundValue("not detected")    
}

def setMotionValue(value) {
    String descriptionText = "${device.displayName} motion is $value"
    sendEvent(name: "motion", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMotionActive() {
    setMotionValue("active")
}

def setMotionInactive() {
    setMotionValue("inactive")    
}

//capability "videoCapture" in SmartThings
def setClipValue(value) {
    String descriptionText = "${device.displayName} clip is $value"
    sendEvent(name: "clip", value: value, descriptionText: descriptionText)
    //logInfo descriptionText
}

//capability "videoCapture,videoStream"
def setStreamValue(value) {
    String descriptionText = "${device.displayName} stream is $value"
    sendEvent(name: "stream", value: value, descriptionText: descriptionText)
    //logInfo descriptionText    
    String video = (value?.InHomeURL?.size()||value?.OutHomeURL.size()) ? "active" : "inactive"
    descriptionText = "${device.displayName} video is $video"
    sendEvent(name: "video", value: video, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "button"
def setSupportedButtonValuesValue(value) {
    String descriptionText = "${device.displayName} supported button values are $value"
    sendEvent(name: "supportedButtonValues", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setDoubleTappedValue(value=1) {
    String descriptionText = "${device.displayName} button $value was double tapped"
    sendEvent(name: "doubleTapped", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setHeldValue(value=1) {
    String descriptionText = "${device.displayName} button $value was held"
    sendEvent(name: "held", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setNumberOfButtonsValue(value=1) {
    sendEvent(name: "numberOfButtons", value: value, descriptionText: "${device.displayName} has $value number of buttons")
}

def setPushedValue(value=1) {
    String descriptionText = "${device.displayName} button $value was pushed"
    sendEvent(name: "pushed", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setReleasedValue(value=1) {
    String descriptionText = "${device.displayName} button $value was released"
    sendEvent(name: "released", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setSwitchValue(value) {
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "capture":[[name:"startTime*",type:"STRING"],[name:"captureTime*",type:"STRING",data:"captureTime"],[name:"endTime*",type:"STRING",data:"endTime"],[name:"correlationId",type:"STRING",data:"correlationId"],[name:"reason",type:"STRING",data:"reason"]],
              "stopStream":[], "startStream":[], "both":[] , "siren":[], "strobe":[], "alarmOff":[], "switchOff":[], "off":[], "on":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

//capability "videoCapture"
def capture(startTime, captureTime, endTime, correlationId=null, reason=null) {
    sendCommand("capture", startTime, null, [captureTime:captureTime, endTime:endTime, correlationId:correlationId, reason:reason] )    
}

//capability "videoStream"
def stopStream() { 
    sendCommand("stopStream")
}

//capability "videoStream"
def startStream() {
    sendCommand("startStream")
}

def both() {
    sendCommand("both")
}

def siren() {
    sendCommand("siren")
}

def strobe() {
    sendCommand("strobe")
}

def alarmOff() { 
    sendCommand("alarmOff")
}

def switchOff() { 
    sendCommand("switchOff")
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

def doubleTap(value=1) {
    logInfo "${device.displayName} doubleTap not supported"    
}

def hold(value=1) {
    logInfo "${device.displayName} hold not supported"    
}

def push(value=1) {
    logInfo "${device.displayName} push not supported"    
}

def release(value=1) {
    logInfo "${device.displayName} release not supported"    
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"both","label":"command: both()","type":"command"},"command":{"name":"both","type":"command","capability":"alarm","label":"command: both()"},"type":"hubitatTrigger"},{"trigger":{"name":"strobe","label":"command: strobe()","type":"command"},"command":{"name":"strobe","type":"command","capability":"alarm","label":"command: strobe()"},"type":"hubitatTrigger"},{"trigger":{"name":"siren","label":"command: siren()","type":"command"},"command":{"name":"siren","type":"command","capability":"alarm","label":"command: siren()"},"type":"hubitatTrigger"},{"trigger":{"name":"startStream","label":"command: startStream()","type":"command"},"command":{"name":"startStream","type":"command","capability":"videoStream","label":"command: startStream()"},"type":"hubitatTrigger"},{"trigger":{"name":"stopStream","label":"command: stopStream()","type":"command"},"command":{"name":"stopStream","type":"command","capability":"videoStream","label":"command: stopStream()"},"type":"hubitatTrigger"},{"trigger":{"name":"capture","label":"command: capture(startTime*, captureTime*, endTime*, correlationId, reason)","type":"command","parameters":[{"name":"startTime*","type":"STRING"},{"name":"captureTime*","type":"STRING","data":"captureTime"},{"name":"endTime*","type":"STRING","data":"endTime"},{"name":"correlationId","type":"STRING","data":"correlationId"},{"name":"reason","type":"STRING","data":"reason"}]},"command":{"name":"capture","arguments":[{"name":"startTime","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"captureTime","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"endTime","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"correlationId","optional":true,"schema":{"title":"String","type":"string","maxLength":255}},{"name":"reason","optional":true,"schema":{"title":"String","type":"string","maxLength":255}}],"type":"command","capability":"videoCapture","label":"command: capture(startTime*, captureTime*, endTime*, correlationId, reason)"},"type":"hubitatTrigger"},{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AlertState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"alarm","attribute":"alarm","label":"attribute: alarm.*"},"command":{"name":"setAlarmValue","label":"command: setAlarmValue(alarm*)","type":"command","parameters":[{"name":"alarm*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed","dataType":"ENUM"},"command":{"name":"setPushedValue","label":"command: setPushedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.held","value":"held","dataType":"ENUM"},"command":{"name":"setHeldValue","label":"command: setHeldValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.*"},"command":{"name":"setMotionValue","label":"command: setMotionValue(motion*)","type":"command","parameters":[{"name":"motion*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"soundSensor","attribute":"sound","label":"attribute: sound.*"},"command":{"name":"setSoundValue","label":"command: setSoundValue(sound*)","type":"command","parameters":[{"name":"sound*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"numberOfButtons","label":"attribute: numberOfButtons.*"},"command":{"name":"setNumberOfButtonsValue","label":"command: setNumberOfButtonsValue(numberOfButtons*)","type":"command","parameters":[{"name":"numberOfButtons*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"switchOff","label":"command: switchOff()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: switch:off()"},"type":"hubitatTrigger"},{"trigger":{"name":"alarmOff","label":"command: alarmOff()","type":"command"},"command":{"name":"off","type":"command","capability":"alarm","label":"command: alarm:off()"},"type":"hubitatTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: switch:off()"},"type":"hubitatTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"alarm","label":"command: alarm:off()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}}},"additionalProperties":false,"required":[],"capability":"button","attribute":"supportedButtonValues","label":"attribute: supportedButtonValues.*"},"command":{"name":"setSupportedButtonValuesValue","label":"command: setSupportedButtonValuesValue(supportedButtonValues*)","type":"command","parameters":[{"name":"supportedButtonValues*","type":"JSON_OBJECT"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.double","value":"double","dataType":"ENUM"},"command":{"name":"setDoubleTappedValue","label":"command: setDoubleTappedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"JsonObject","type":"object"}},"additionalProperties":false,"required":["value"],"capability":"videoCapture","attribute":"stream","label":"attribute: stream.*"},"command":{"name":"setStreamValue","label":"command: setStreamValue(stream*)","type":"command","parameters":[{"name":"stream*","type":"JSON_OBJECT"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"JsonObject","type":"object"}},"additionalProperties":false,"required":["value"],"capability":"videoCapture","attribute":"clip","label":"attribute: clip.*"},"command":{"name":"setClipValue","label":"command: setClipValue(clip*)","type":"command","parameters":[{"name":"clip*","type":"JSON_OBJECT"}]},"type":"smartTrigger","mute":true,"disableStatus":true}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
