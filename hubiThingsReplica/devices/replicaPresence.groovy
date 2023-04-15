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
    definition(name: "Replica Presence", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaPresence.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "PresenceSensor"
        capability "Refresh"
        
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "arrived"
        command "departed"
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
    return ([ "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setPresenceValue":[[name:"presence*",type:"ENUM"]], "setPresenceNotPresent":[], "setPresencePresent":[], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setPresenceValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "presence", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPresenceNotPresent() {
    setPresenceValue("not present")
}

def setPresencePresent() {
    setPresenceValue("present")    
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "refresh":[], "arrived":[], "departed":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}


void arrived() {
    sendCommand("arrived")
}

void departed() {
    sendCommand("departed")
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"PresenceState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"presenceSensor","attribute":"presence","label":"attribute: presence.*"},"command":{"name":"setPresenceValue","label":"command: setPresenceValue(presence*)","type":"command","parameters":[{"name":"presence*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"arrived","label":"command: arrived()","type":"command"},"command":{"type":"attribute","properties":{"value":{"title":"PresenceState","type":"string","enum":["present","not present"]}},"additionalProperties":false,"required":["value"],"capability":"presenceSensor","attribute":"presence","label":"attribute: presence.present","value":"present","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"departed","label":"command: departed()","type":"command"},"command":{"type":"attribute","properties":{"value":{"title":"PresenceState","type":"string","enum":["present","not present"]}},"additionalProperties":false,"required":["value"],"capability":"presenceSensor","attribute":"presence","label":"attribute: presence.not present","value":"not present","dataType":"ENUM"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
