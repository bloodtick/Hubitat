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
@SuppressWarnings('unused')
public static String version() {return "1.3.1"}

metadata 
{
    definition(name: "Replica Alarm", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaAlarm.groovy")
    {
        capability "Actuator"
        capability "Alarm"
        capability "Battery"
        capability "Configuration"
        capability "ContactSensor"      
        capability "Refresh"
        
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
Map getReplicaCommands() {
    return ([ "setAlarmValue":[[name:"alarm*",type:"ENUM"]], "setAlarmOff":[], "setAlarmBoth":[], "setAlarmSiren":[], "setAlarmStrobe":[], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], 
              "setContactValue":[[name:"contact*",type:"ENUM"]], "setContactClosed":[], "setContactOpen":[], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
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

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setContactValue(String value) {
    String descriptionText = "${device.displayName} contact is $value"
    sendEvent(name: "contact", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setContactClosed() {
    setContactValue("closed")
}

def setContactOpen() {
    setContactValue("open")
}

def setHealthStatusValue(String value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "off":[], "both":[] , "siren":[], "strobe":[], "close":[], "open":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def off() {
    sendCommand("off")
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

def close() {
    sendCommand("close")
}

def open() {
    sendCommand("open")    
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"command":{"name":"setContactValue","label":"command: setContactValue(contact*)","type":"command","parameters":[{"name":"contact*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AlertState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"alarm","attribute":"alarm","label":"attribute: alarm.*"},"command":{"name":"setAlarmValue","label":"command: setAlarmValue(alarm*)","type":"command","parameters":[{"name":"alarm*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"both","label":"command: both()","type":"command"},"command":{"name":"both","type":"command","capability":"alarm","label":"command: both()"},"type":"hubitatTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"alarm","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"siren","label":"command: siren()","type":"command"},"command":{"name":"siren","type":"command","capability":"alarm","label":"command: siren()"},"type":"hubitatTrigger"},{"trigger":{"name":"strobe","label":"command: strobe()","type":"command"},"command":{"name":"strobe","type":"command","capability":"alarm","label":"command: strobe()"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
