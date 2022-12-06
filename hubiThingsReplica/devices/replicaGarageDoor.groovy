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
public static String version() {return "1.2.0"}

metadata 
{
    definition(name: "Replica Garage Door", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaGarageDoor.groovy")
    {
        capability "Actuator"
        capability "Alarm"
        capability "Battery"
        capability "Configuration"
        capability "ContactSensor"
        capability "DoorControl"
        capability "GarageDoorControl"       
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {   
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
    log.info "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([ "setAlarmValue":[[name:"alarm*",type:"ENUM"]], "setAlarmOff":[], "setAlarmBoth":[], "setAlarmSiren":[], "setAlarmStrobe":[], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], 
              "setContactValue":[[name:"contact*",type:"ENUM"]], "setContactClosed":[], "setContactOpen":[], "setDoorValue":[[name:"door*",type:"ENUM"]], "setDoorClosed":[], "setDoorClosing":[], 
              "setDoorOpen":[], "setDoorOpening":[], "setDoorUnknown":[], "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setAlarmValue(String value) {
    String descriptionText = "${device.displayName} alarm is $value"
    sendEvent(name: "alarm", value: value, descriptionText: descriptionText)
    log.info descriptionText
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
    log.info descriptionText
}

def setContactValue(String value) {
    String descriptionText = "${device.displayName} contact is $value"
    sendEvent(name: "contact", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setContactClosed() {
    setContactValue("closed")
}

def setContactOpen() {
    setContactValue("open")
}

def setDoorValue(String value) {
    String descriptionText = "${device.displayName} door is $value"
    sendEvent(name: "door", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setDoorClosed() {
    setDoorValue("closed")
}

def setDoorClosing() {
    setDoorValue("closing")
}

def setDoorOpen() {
    setDoorValue("open")
}

def setDoorOpening() {
    setDoorValue("opening")
}

def setDoorUnknown() {
    setDoorValue("unknown")
}

def setSwitchValue(value) {
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    log.info descriptionText
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
    return ([ "both":[] , "siren":[], "strobe":[], "off":[], "on":[], "close":[], "open":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
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

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
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
    return """ {"version":1,"components":[{"trigger":{"name":"close","label":"command: close()","type":"command"},"command":{"name":"close","type":"command","capability":"doorControl","label":"command: close()"},"type":"hubitatTrigger"},{"trigger":{"name":"open","label":"command: open()","type":"command"},"command":{"name":"open","type":"command","capability":"doorControl","label":"command: open()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"command":{"name":"setContactValue","label":"command: setContactValue(contact*)","type":"command","parameters":[{"name":"contact*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"doorControl","attribute":"door","label":"attribute: door.*"},"command":{"name":"setDoorValue","label":"command: setDoorValue(door*)","type":"command","parameters":[{"name":"door*","type":"ENUM"}]},"type":"smartTrigger"}]}
status: {"components":{"main":{"doorControl":{"door":{"value":"closed","timestamp":"2022-11-20T01:40:51.228Z"}},"contactSensor":{"contact":{"value":"closed","timestamp":"2022-11-20T01:40:51.232Z"}},"switch":{"switch":{"value":"on","timestamp":"2022-11-20T01:40:51.229Z"}}}}}"""
}
