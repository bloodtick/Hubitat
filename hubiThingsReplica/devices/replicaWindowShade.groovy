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

//file:noinspection unused

public static String version() {return "1.3.1"}
    
metadata 
{
    definition(name: "Replica Window Shade", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaWindowShade.groovy")
	{
        capability "Actuator"
        capability "Configuration"
        capability "EnergyMeter"
        capability "PowerMeter"
        capability "Refresh"
        capability "WindowShade"
        //capability "windowShadeLevel" // doesn't exist in Hubitat
        
        attribute "supportedWindowShadeCommands", "JSON_OBJECT" //capability "windowShade" in SmartThings
        attribute "shadeLevel", "number" //capability "windowShadeLevel" in SmartThings       
        attribute "healthStatus", "enum", ["offline", "online"]
		
        command "pause" //capability "windowShade" in SmartThings
        command "setShadeLevel", [[name: "shadeLevel*", type: "NUMBER", description: "Shade level in %"]] //capability "windowShadeLevel" in SmartThings
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
    return ([ "setEnergyValue":[[name:"energy*",type:"NUMBER"]], "setPowerValue":[[name:"power*",type:"NUMBER"]], "setShadeLevelValue":[[name:"shadeLevel*",type:"NUMBER"]], 
              "setSupportedWindowShadeCommandsValue":[[name:"supportedWindowShadeCommands*",type:"JSON_OBJECT"]], "setWindowShadeValue":[[name:"windowShade*",type:"ENUM"]], 
              "setWindowShadePartiallyOpen":[], "setWindowShadeClosed":[], "setWindowShadeOpen":[], "setWindowShadeClosing":[], "setWindowShadeUnknown":[], 
              "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setEnergyValue(value) {
    String descriptionText = "${device.displayName} energy is $value kWh"
    sendEvent(name: "energy", value: "$value", unit: "kWh", descriptionText: descriptionText)
    logInfo descriptionText
}

def setPowerValue(value) {
    String descriptionText = "${device.displayName} power is $value W"
    sendEvent(name: "power", value: "$value", unit: "W", descriptionText: descriptionText)
    logInfo descriptionText
}

def setShadeLevelValue(value) {
    String descriptionText = "${device.displayName} shade level is $value %"
    sendEvent(name: "shadeLevel", value: "$value", unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedWindowShadeCommandsValue(value) {
    String descriptionText = "${device.displayName} supported window shade commands are $value"
    sendEvent(name: "supportedWindowShadeCommands", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setWindowShadeValue(value) {
    String descriptionText = "${device.displayName} window shade is $value"
    sendEvent(name: "windowShade", value: value, descriptionText: descriptionText)
    logInfo descriptionText
    switch (value){
        case 'open':
            setPositionValue(100)
            break
        case 'closed':
            setPositionValue(0)
            break
        case 'partially open':
            setPositionValue(50)
            break
    }
}

def setPositionValue(value) {
    String descriptionText = "${device.displayName} position is $value"
    sendEvent(name: "position", value: "$value", unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setWindowShadeOpening() {
    setWindowShadeValue("opening")
}

def setWindowShadePartiallyOpen() {
    setWindowShadeValue("partially open")
}

def setWindowShadeClosed() {
    setWindowShadeValue("closed")
}

def setWindowShadeOpen() {
    setWindowShadeValue("open")
}

def setWindowShadeClosing() {
    setWindowShadeValue("closing")
}

def setWindowShadeUnknown() {
    setWindowShadeValue("unknown")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "close":[] , "open":[], "pause":[], "setShadeLevel":[[name:"shadeLevel*",type:"NUMBER"]], "setPosition":[[name:"position*",type:"NUMBER"]], "startPositionChange":[[name:"direction*",type:"ENUM"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def close() { 
    sendCommand("close")
}

def open() {
    sendCommand("open")
}

def pause() {
    sendCommand("pause")
}

def setShadeLevel(value) {
    sendCommand("setShadeLevel", value, "%")    
}

def setPosition(value) {
    sendCommand("setPosition", value, "%")    
}

def startPositionChange(value) {
    sendCommand("startPositionChange", value)    
}

void stopPositionChange() {
    sendCommand("stopPositionChange")
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"OpenableState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"windowShade","attribute":"windowShade","label":"attribute: windowShade.*"},"command":{"name":"setWindowShadeValue","label":"command: setWindowShadeValue(windowShade*)","type":"command","parameters":[{"name":"windowShade*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"windowShadeLevel","attribute":"shadeLevel","label":"attribute: shadeLevel.*"},"command":{"name":"setShadeLevelValue","label":"command: setShadeLevelValue(shadeLevel*)","type":"command","parameters":[{"name":"shadeLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"close","label":"command: close()","type":"command"},"command":{"name":"close","type":"command","capability":"windowShade","label":"command: close()"},"type":"hubitatTrigger"},{"trigger":{"name":"open","label":"command: open()","type":"command"},"command":{"name":"open","type":"command","capability":"windowShade","label":"command: open()"},"type":"hubitatTrigger"},{"trigger":{"name":"pause","label":"command: pause()","type":"command"},"command":{"name":"pause","type":"command","capability":"windowShade","label":"command: pause()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["open","close","pause"]}}},"additionalProperties":false,"required":[],"capability":"windowShade","attribute":"supportedWindowShadeCommands","label":"attribute: supportedWindowShadeCommands.*"},"command":{"name":"setSupportedWindowShadeCommandsValue","label":"command: setSupportedWindowShadeCommandsValue(supportedWindowShadeCommands*)","type":"command","parameters":[{"name":"supportedWindowShadeCommands*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setShadeLevel","label":"command: setShadeLevel(shadeLevel*)","type":"command","parameters":[{"name":"shadeLevel*","type":"NUMBER"}]},"command":{"name":"setShadeLevel","arguments":[{"name":"shadeLevel","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"windowShadeLevel","label":"command: setShadeLevel(shadeLevel*)"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
