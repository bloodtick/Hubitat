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
public static String version() {return "1.3.0"} 

metadata 
{
    definition(name: "Replica Envisalink", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaEnvisalink.groovy")
    {
        capability "Actuator"
        capability "Alarm"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        //capability "securitySystem" // doesn't exist in Hubitat     
        
        attribute "ledStatus", "string" //custom 
        attribute "partStatus", "string" //custom
        attribute "partitionCommand", "string" //custom
        attribute "partitionStatus", "string" //custom
        attribute "securitySystemStatus", "enum", ["armedAway", "armedStay", "disarmed" ] //capability "securitySystem" in SmartThings             
        attribute "selection", "string" //custom
        attribute "healthStatus", "enum", ["offline", "online"]

        command "armedAway", [[name: "bypassAll*", type: "STRING", description: "Set armedAway bypassAll value"]]
        command "armedStay", [[name: "bypassAll*", type: "STRING", description: "Set armedStay bypassAll value"]]
        command "disarmed"
        command "setLedStatus", [[name: "ledStatus*", type: "STRING", description: "Set LED status value"]]
        command "setPartStatus", [[name: "partStatus*", type: "STRING", description: "Set partition status value"]]
        command "setPartitionCommand", [[name: "partitionCommand*", type: "STRING", description: "Set partition command value"]]
        command "setSelection", [[name: "selection*", type: "STRING", description: "Set selection value"]]
        command "setSwitch", [[name: "switch*", type: "ENUM", description: "Set switch value", constraints: ["off","on"]]]
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
    return ([ "setAlarmValue":[[name:"alarm*",type:"ENUM"]], "setAlarmBoth":[], "setAlarmOff":[],  "setAlarmSiren":[], "setAlarmStrobe":[], 
              "setLedStatusValue":[[name:"ledStatus*",type:"STRING"]], "setPartStatusValue":[[name:"partStatus*",type:"STRING"]], "setPartitionCommandValue":[[name:"partitionCommand*",type:"STRING"]],
              "setSecuritySystemStatusValue":[[name:"securitySystemStatus*",type:"ENUM"]], "setSecuritySystemStatusArmedAway":[], "setSecuritySystemStatusArmedStay":[], "setSecuritySystemStatusDisarmed":[],
              "setSelectionValue":[[name:"selection*",type:"STRING"]], "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setAlarmValue(String value) {
    String descriptionText = "${device.displayName} alarm is $value"
    sendEvent(name: "alarm", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAlarmBoth() {
    setAlarmValue("both")
}

def setAlarmOff() {
    setAlarmValue("off")
}

def setAlarmSiren() {
    setAlarmValue("siren")
}

def setAlarmStrobe() {
    setAlarmValue("strobe")
}

def setLedStatusValue(value) {
    String descriptionText = "${device.displayName} led status is $value"
    sendEvent(name: "ledStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPartStatusValue(value) {
    String descriptionText = "${device.displayName} part status is $value"
    sendEvent(name: "partStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPartitionCommandValue(value) {
    String descriptionText = "${device.displayName} partition command is $value"
    sendEvent(name: "partitionCommand", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSecuritySystemStatusValue(String value) {
    String descriptionText = "${device.displayName} security system status is $value"
    sendEvent(name: "securitySystemStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSecuritySystemStatusArmedAway() {
    setSecuritySystemStatusValue("armedAway")
}

def setSecuritySystemStatusArmedStay() {
    setSecuritySystemStatusValue("armedStay")
}

def setSecuritySystemStatusDisarmed() {
    setSecuritySystemStatusValue("disarmed")
}

def setSelectionValue(value) {
    String descriptionText = "${device.displayName} selection is $value"
    sendEvent(name: "selection", value: "$value", descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchValue(value) {
    String descriptionText = "${device.displayName} switch was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

def setHealthStatusValue(String value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "armAway":[[name:"bypassAll*",type:"BOOLEAN"]], "armStay":[[name:"bypassAll*",type:"BOOLEAN"]], "both":[] , "disarm":[], "off":[], "setPartStatus":[[name:"partStatus*",type:"STRING"]], "setPartitionCommand":[[name:"partitionCommand*",type:"STRING"]], 
             "setSelection":[[name:"selection*",type:"STRING"]], "setSwitch":[[name:"switch*",type:"ENUM"]], "setLedStatus":[[name:"ledStatus*",type:"STRING"]], "siren":[], "strobe":[], "refresh":[]
            ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def armAway(value) {
    sendCommand("armAway", value)    
}

def armStay(value) {
    sendCommand("armStay", value)    
}

def both() {
    sendCommand("both")
}

def disarm() {
    sendCommand("disarm")
}

def off() { 
    sendCommand("off")
}

def on() { 
    sendCommand("on") // no operation on this driver
}

def setPartStatus(value) {
    sendCommand("setPartStatus", value)    
}

def setPartitionCommand(value) {
    sendCommand("setPartitionCommand", value)    
}

def setSelection(value) {
    sendCommand("setSelection", value)    
}

def setSwitch(value) {
    sendCommand("setSwitch", value)    
}

def setLedStatus(value) {
    sendCommand("setLedStatus", value)    
}

def siren() {
    sendCommand("siren")
}

def strobe() {
    sendCommand("strobe")
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"strobe","label":"command: strobe()","type":"command"},"command":{"name":"strobe","type":"command","capability":"alarm","label":"command: strobe()"},"type":"hubitatTrigger"},{"trigger":{"name":"siren","label":"command: siren()","type":"command"},"command":{"name":"siren","type":"command","capability":"alarm","label":"command: siren()"},"type":"hubitatTrigger"},{"trigger":{"name":"setLedStatus","label":"command: setLedStatus(ledStatus*)","type":"command","parameters":[{"name":"ledStatus*","type":"STRING"}]},"command":{"name":"setledStatus","arguments":[{"name":"value","optional":false,"schema":{"type":"string","maxLength":30}}],"type":"command","capability":"partyvoice23922.ledStatus","label":"command: setledStatus(value*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setSelection","label":"command: setSelection(selection*)","type":"command","parameters":[{"name":"selection*","type":"STRING"}]},"command":{"name":"setSelection","arguments":[{"name":"value","optional":false,"schema":{"type":"string","maxLength":20}}],"type":"command","capability":"partyvoice23922.dscselectswitch2","label":"command: setSelection(value*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setPartitionCommand","label":"command: setPartitionCommand(partitionCommand*)","type":"command","parameters":[{"name":"partitionCommand*","type":"STRING"}]},"command":{"name":"setPartitionCommand","arguments":[{"name":"value","optional":false,"schema":{"type":"string","maxLength":16}}],"type":"command","capability":"partyvoice23922.partitioncommand2","label":"command: setPartitionCommand(value*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setPartStatus","label":"command: setPartStatus(partStatus*)","type":"command","parameters":[{"name":"partStatus*","type":"STRING"}]},"command":{"name":"setPartStatus","arguments":[{"name":"value","optional":false,"schema":{"type":"string","maxLength":16}}],"type":"command","capability":"partyvoice23922.partitionstatus2","label":"command: setPartStatus(value*)"},"type":"hubitatTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"disarm","label":"command: disarm()","type":"command"},"command":{"name":"disarm","type":"command","capability":"securitySystem","label":"command: disarm()"},"type":"hubitatTrigger"},{"trigger":{"name":"both","label":"command: both()","type":"command"},"command":{"name":"both","type":"command","capability":"alarm","label":"command: both()"},"type":"hubitatTrigger"},{"trigger":{"name":"armAway","label":"command: armAway(bypassAll*)","type":"command","parameters":[{"name":"bypassAll*","type":"BOOLEAN"}]},"command":{"name":"armAway","arguments":[{"name":"bypassAll","optional":false,"schema":{"type":"boolean"}}],"type":"command","capability":"securitySystem","label":"command: armAway(bypassAll*)"},"type":"hubitatTrigger"},{"trigger":{"name":"armStay","label":"command: armStay(bypassAll*)","type":"command","parameters":[{"name":"bypassAll*","type":"BOOLEAN"}]},"command":{"name":"armStay","arguments":[{"name":"bypassAll","optional":false,"schema":{"type":"boolean"}}],"type":"command","capability":"securitySystem","label":"command: armStay(bypassAll*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","maxLength":20}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.dscselectswitch2","attribute":"selection","label":"attribute: selection.*"},"command":{"name":"setSelectionValue","label":"command: setSelectionValue(selection*)","type":"command","parameters":[{"name":"selection*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"securitySystem","attribute":"securitySystemStatus","label":"attribute: securitySystemStatus.*"},"command":{"name":"setSecuritySystemStatusValue","label":"command: setSecuritySystemStatusValue(securitySystemStatus*)","type":"command","parameters":[{"name":"securitySystemStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","maxLength":16}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.partitioncommand2","attribute":"partitionCommand","label":"attribute: partitionCommand.*"},"command":{"name":"setPartitionCommandValue","label":"command: setPartitionCommandValue(partitionCommand*)","type":"command","parameters":[{"name":"partitionCommand*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","maxLength":16}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.partitionstatus2","attribute":"partStatus","label":"attribute: partStatus.*"},"command":{"name":"setPartStatusValue","label":"command: setPartStatusValue(partStatus*)","type":"command","parameters":[{"name":"partStatus*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","maxLength":30}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.ledStatus","attribute":"ledStatus","label":"attribute: ledStatus.*"},"command":{"name":"setLedStatusValue","label":"command: setLedStatusValue(ledStatus*)","type":"command","parameters":[{"name":"ledStatus*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"setSwitch","label":"command: setSwitch(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"command":{"name":"setSwitch","arguments":[{"name":"value","optional":false,"schema":{"type":"string"}}],"type":"command","capability":"partyvoice23922.dscstayswitch","label":"command: setSwitch(value*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"String","type":"string","maxLength":255}},"additionalProperties":false,"required":["value"],"capability":"securitySystem","attribute":"alarm","label":"attribute: alarm.*"},"command":{"name":"setAlarmValue","label":"command: setAlarmValue(alarm*)","type":"command","parameters":[{"name":"alarm*","type":"ENUM"}]},"type":"smartTrigger"}]}
status: {"components":{"main":{"actuator":{},"switchLevel":{"level":{"value":73,"unit":"%","timestamp":"2022-11-11T23:16:54.163Z"}},"sensor":{},"switch":{"switch":{"value":"off","timestamp":"2022-12-06T02:41:37.500Z"}}}}}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
