/**
*  Copyright 2024 Bloodtick
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
public static String version() {return "1.3.1"}

metadata 
{
    definition(name: "Replica Aeotec Camera", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaAeotecCamera.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "ImageCapture"
        capability "MotionSensor"
        capability "SoundSensor"
        capability "Switch"
        capability "Refresh"
        capability "VideoCapture"
        
        command "startStream"
        command "stopStream"
        command "startAudio"
        command "stopAudio"
        command "enableSoundDetection"
        command "disableSoundDetection"
        command "capture", [[name: "startTime*", type: "STRING", description: "Time, in ISO 8601 format, when the video capture should start"],[name: "captureTime*", type: "STRING", description: "Video capture time, in ISO 8601 format"],
                           [name: "endTime*", type: "STRING", description: "Time, in ISO 8601 format, when the video capture should end"],[name: "correlationId", type: "STRING", description: "Optional"],[name: "reason", type: "STRING", description: "Optional"]]

        command "captureNow", [[name: "duration", type: "STRING", description: "Capture length in seconds. Default 10"],[name: "correlationId", type: "STRING", description: "Optional"],[name: "reason", type: "STRING", description: "Optional"]]
        
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
    refresh()
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[],         
              "setMotionValue":[[name:"motion*",type:"ENUM"]], "setMotionActive":[], "setMotionInactive":[], 
              "setSoundValue":[[name:"sound*",type:"ENUM"]], "setSoundDetected":[], "setSoundNotDetected":[],
              "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
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

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "off":[] , "on":[], "startStream":[] , "stopStream":[], "startAudio":[] , "stopAudio":[], "enableSoundDetection":[] , "disableSoundDetection":[], "refresh":[],
              "capture":[[name:"startTime*",type:"STRING"],[name:"captureTime*",type:"STRING",data:"captureTime"],[name:"endTime*",type:"STRING",data:"endTime"],[name:"correlationId",type:"STRING",data:"correlationId"],[name:"reason",type:"STRING",data:"reason"]],
              "take":[[name:"correlationId",type:"STRING",data:"correlationId"],[name:"reason",type:"STRING",data:"reason"]],
            ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

def startStream() {
    sendCommand("startStream")
}

def stopStream() {
    sendCommand("stopStream")
}

def startAudio() {
    sendCommand("startAudio")
}

def stopAudio() {
    sendCommand("stopAudio")
}

def enableSoundDetection() {
    sendCommand("enableSoundDetection")
}

def disableSoundDetection() {
    sendCommand("disableSoundDetection")
}

def take(String correlationId=null, String reason=null) {
    sendCommand("take", correlationId, null, [reason:reason] )
}

def captureNow(String duration="10", String correlationId=now().toString(), String reason="Capture now pressed") {
    String startTime = getCurrentIso8601()
    String captureTime = startTime
    String endTime = getCurrentIso8601(duration?.toInteger()?:10)
    capture(startTime, captureTime, endTime, correlationId, reason)
}

String getCurrentIso8601(Integer seconds=0) {
    def date = new Date()
    date = new Date(date.time + (seconds * 1000))
    def formattedDate = date.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
    return formattedDate
}

def isValidIso8601(String dateTime) {
    def pattern = /^(?:[1-9]\d{3}-?(?:(?:0[1-9]|1[0-2])-?(?:0[1-9]|1\d|2[0-8])|(?:0[13-9]|1[0-2])-?(?:29|30)|(?:0[13578]|1[02])-?31)|(?:[1-9]\d(?:0[48]|[2468][048]|[13579][26])|(?:[2468][048]|[13579][26])00)-?02-?29)T(?:[01]\d|2[0-3]):?[0-5]\d:?[0-5]\d(?:\.\d{3})?(?:Z|[+-][01]\d(?::?[0-5]\d)?)$/
    return dateTime.matches(pattern)
}

def capture(String startTime, String captureTime, String endTime, String correlationId=null, String reason=null) {
    logInfo "${device.displayName} executing capture ${reason?"reason:'$reason'":""}"
    if(!isValidIso8601(startTime)) { logWarn "${device.displayName} capture startTime:'$startTime' not valid. Example:'${getCurrentIso8601()}'"}
    if(!isValidIso8601(captureTime)) { logWarn "${device.displayName} capture captureTime:'$captureTime' not valid. Example:'${getCurrentIso8601()}'"}
    if(!isValidIso8601(endTime)) { logWarn "${device.displayName} capture endTime:'$endTime' not valid. Example:'${getCurrentIso8601()}'"}
    
    sendCommand("capture", startTime, null, [captureTime:captureTime, endTime:endTime, correlationId:correlationId, reason:reason] )    
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"command":{"label":"command: setMotionValue(motion*)","name":"setMotionValue","parameters":[{"name":"motion*","type":"ENUM"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"motion","capability":"motionSensor","label":"attribute: motion.*","properties":{"value":{"title":"ActivityState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"capability":"switch","label":"command: off()","name":"off","type":"command"},"trigger":{"label":"command: off()","name":"off","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"switch","label":"command: on()","name":"on","type":"command"},"trigger":{"label":"command: on()","name":"on","type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setSoundValue(sound*)","name":"setSoundValue","parameters":[{"name":"sound*","type":"ENUM"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"sound","capability":"soundSensor","label":"attribute: sound.*","properties":{"value":{"title":"ActivityState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setBatteryValue(battery*)","name":"setBatteryValue","parameters":[{"name":"battery*","type":"NUMBER"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"battery","capability":"battery","label":"attribute: battery.*","properties":{"unit":{"default":"%","enum":["%"],"type":"string"},"value":{"maximum":100,"minimum":0,"type":"integer"}},"required":["value"],"title":"IntegerPercent","type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setSwitchValue(switch*)","name":"setSwitchValue","parameters":[{"name":"switch*","type":"ENUM"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"switch","capability":"switch","label":"attribute: switch.*","properties":{"value":{"title":"SwitchState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setHealthStatusValue(healthStatus*)","name":"setHealthStatusValue","parameters":[{"name":"healthStatus*","type":"ENUM"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"healthStatus","capability":"healthCheck","label":"attribute: healthStatus.*","properties":{"value":{"title":"HealthState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"capability":"soundDetection","label":"command: disableSoundDetection()","name":"disableSoundDetection","type":"command"},"trigger":{"label":"command: disableSoundDetection()","name":"disableSoundDetection","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"soundDetection","label":"command: enableSoundDetection()","name":"enableSoundDetection","type":"command"},"trigger":{"label":"command: enableSoundDetection()","name":"enableSoundDetection","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"audioStream","label":"command: startAudio()","name":"startAudio","type":"command"},"trigger":{"label":"command: startAudio()","name":"startAudio","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"videoStream","label":"command: startStream()","name":"startStream","type":"command"},"trigger":{"label":"command: startStream()","name":"startStream","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"audioStream","label":"command: stopAudio()","name":"stopAudio","type":"command"},"trigger":{"label":"command: stopAudio()","name":"stopAudio","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"videoStream","label":"command: stopStream()","name":"stopStream","type":"command"},"trigger":{"label":"command: stopStream()","name":"stopStream","type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"startTime","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"captureTime","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"endTime","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"correlationId","optional":true,"schema":{"title":"String","type":"string"}},{"name":"reason","optional":true,"schema":{"title":"String","type":"string"}}],"capability":"videoCapture","label":"command: capture(startTime*, captureTime*, endTime*, correlationId, reason)","name":"capture","type":"command"},"trigger":{"label":"command: capture(startTime*, captureTime*, endTime*, correlationId, reason)","name":"capture","parameters":[{"name":"startTime*","type":"STRING"},{"data":"captureTime","name":"captureTime*","type":"STRING"},{"data":"endTime","name":"endTime*","type":"STRING"},{"data":"correlationId","name":"correlationId","type":"NUMBER"},{"data":"reason","name":"reason","type":"NUMBER"}],"type":"command"},"type":"hubitatTrigger"},{"trigger":{"name":"take","label":"command: take(correlationId, reason)","type":"command","parameters":[{"data":"correlationId","name":"correlationId","type":"STRING"},{"data":"reason","name":"reason","type":"STRING"}]},"command":{"arguments":[{"name":"correlationId","optional":true,"schema":{"title":"String","type":"string"}},{"name":"reason","optional":true,"schema":{"title":"String","type":"string"}}],"capability":"imageCapture","label":"command: take(correlationId, reason)","name":"take","type":"command"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
