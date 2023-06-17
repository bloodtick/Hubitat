/**
*  Copyright 2023 Bloodtick
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
    definition(name: "Replica Fan Control", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaFanControl.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "RelativeHumidityMeasurement"
        capability "Switch"
        capability "SwitchLevel"
        capability "TemperatureMeasurement"
        capability "Refresh"
        
        attribute "fanSpeed", "number" //capability "fanSpeed" in SmartThings
        attribute "fanOscillationMode", "enum", ["off", "individual", "fixed", "vertical", "horizontal", "all", "indirect", "direct", "fixedCenter", "fixedLeft", "fixedRight", "far", "wide", "mid", "spot", "swing"] //capability "fanOscillationMode" in SmartThings
        attribute "supportedFanOscillationModes", "JSON_OBJECT" //capability "fanOscillationMode" in SmartThings
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setFanSpeed", [[name: "speed*", type: "NUMBER", description: "Set the fan to this speed"]] //capability "fanSpeed" in SmartThings
        command "setFanOscillationMode", [[name: "fanOscillationMode*", type: "ENUM", description: "Set oscillation mode", constraints: ["off", "individual", "fixed", "vertical", "horizontal", "all", "indirect", "direct", "fixedCenter", "fixedLeft", "fixedRight", "far", "wide", "mid", "spot", "swing"]]] //capability "fanOscillationMode" in SmartThings
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
static Map getReplicaCommands() {
    return ([ 
	    "setSwitchValue":[[name:"switch*",type:"ENUM"]], 
	    "setSwitchOff":[], 
	    "setSwitchOn":[], 
	    "setFanSpeedValue":[[name:"fanSpeed*",type:"NUMBER"]], 
	    "setLevelValue":[[name:"level*",type:"NUMBER"]], 
	    "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], 
	    "setHumidityValue":[[name:"humidity*",type:"NUMBER"]], 
	    "setFanOscillationModeValue":[[name:"fanOscillationMode*",type:"ENUM"]],
	    "setSupportedFanOscillationModesValue":[[name:"supportedFanOscillationModes*",type:"JSON_OBJECT"]],
	    "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHumidityValue(value) {
    String unit = "%rh"
    String descriptionText = "${device.displayName} humidity is $value $unit"
    sendEvent(name: "humidity", value: value, unit: unit, descriptionText: descriptionText)
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

def setLevelValue(value) {
    String descriptionText = "${device.displayName} set level to $value%"
    sendEvent(name: "level", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}   
             
//capability "fanSpeed"
def setFanSpeedValue(value) {
    String descriptionText = "${device.displayName} fan speed is $value"
    sendEvent(name: "fanSpeed", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "fanOscillationMode"
def setFanOscillationModeValue(value) {
    String descriptionText = "${device.displayName} fan oscillation mode is $value"
    sendEvent(name: "fanOscillationMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedFanOscillationModesValue(value) {
    String descriptionText = "${device.displayName} supported fan oscillation modes are $value"
    sendEvent(name: "supportedFanOscillationModes", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ 
	    "off":[] , 
	    "on":[], 
	    "setFanSpeed":[[name:"speed*",type:"NUMBER"]], 
	    "setLevel":[[name:"level*",type:"NUMBER"],[name:"duration",type:"NUMBER"]],
	    "setFanOscillationMode":[[name:"fanOscillationMode*",type:"ENUM"]], 
	    "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

def setLevel(level, duration=null) {
    sendCommand("setLevel",level,duration)
}

//capability "fanSpeed"
def setFanSpeed(speed) {
    sendCommand("setFanSpeed", speed)    
}

//capability "fanOscillationMode"
def setFanOscillationMode(fanOscillationMode) {
    sendCommand("setFanOscillationMode", fanOscillationMode)    
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"name":"setFanSpeed","label":"command: setFanSpeed(speed*)","type":"command","parameters":[{"name":"speed*","type":"NUMBER"}]},"command":{"name":"setFanSpeed","arguments":[{"name":"speed","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"fanSpeed","label":"command: setFanSpeed(speed*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"fanSpeed","attribute":"fanSpeed","label":"attribute: fanSpeed.*"},"command":{"name":"setFanSpeedValue","label":"command: setFanSpeedValue(fanSpeed*)","type":"command","parameters":[{"name":"fanSpeed*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"setFanOscillationMode","label":"command: setFanOscillationMode(fanOscillationMode*)","type":"command","parameters":[{"name":"fanOscillationMode*","type":"ENUM"}]},"command":{"name":"setFanOscillationMode","arguments":[{"name":"fanOscillationMode","optional":false,"schema":{"title":"FanOscillationMode","type":"string","enum":["off","individual","fixed","vertical","horizontal","all","indirect","direct","fixedCenter","fixedLeft","fixedRight","far","wide","mid","spot","swing"]}}],"type":"command","capability":"fanOscillationMode","label":"command: setFanOscillationMode(fanOscillationMode*)"},"type":"hubitatTrigger"},{"trigger":{"title":"Percent","type":"attribute","properties":{"value":{"type":"number","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"relativeHumidityMeasurement","attribute":"humidity","label":"attribute: humidity.*"},"command":{"name":"setHumidityValue","label":"command: setHumidityValue(humidity*)","type":"command","parameters":[{"name":"humidity*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"FanOscillationMode","type":"string"}},"additionalProperties":false,"required":[],"capability":"fanOscillationMode","attribute":"fanOscillationMode","label":"attribute: fanOscillationMode.*"},"command":{"name":"setFanOscillationModeValue","label":"command: setFanOscillationModeValue(fanOscillationMode*)","type":"command","parameters":[{"name":"fanOscillationMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"title":"FanOscillationMode","type":"string","enum":["off","individual","fixed","vertical","horizontal","all","indirect","direct","fixedCenter","fixedLeft","fixedRight","far","wide","mid","spot","swing"]},"type":"array"}},"additionalProperties":false,"required":["value"],"capability":"fanOscillationMode","attribute":"supportedFanOscillationModes","label":"attribute: supportedFanOscillationModes.*"},"command":{"name":"setSupportedFanOscillationModesValue","label":"command: setSupportedFanOscillationModesValue(supportedFanOscillationModes*)","type":"command","parameters":[{"name":"supportedFanOscillationModes*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"switchLevel","attribute":"level","label":"attribute: level.*"},"command":{"name":"setLevelValue","label":"command: setLevelValue(level*)","type":"command","parameters":[{"name":"level*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"setLevel","label":"command: setLevel(level*, duration)","type":"command","parameters":[{"name":"level*","type":"NUMBER"},{"name":"duration","type":"NUMBER"}]},"command":{"name":"setLevel","arguments":[{"name":"level","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}},{"name":"rate","optional":true,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"switchLevel","label":"command: setLevel(level*, rate)"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
