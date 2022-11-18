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
public static String version() {return "1.1.2"}

metadata 
{
    definition(name: "Replica Garage Door", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaGarageDoor.groovy")
    {
        capability "Actuator"
        capability "Alarm"
        capability "Battery"
        capability "ContactSensor"
        capability "DoorControl"
        capability "GarageDoorControl"       
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        
        attribute "command", "enum", ["both", "siren", "strobe", "close", "open", "off", "on", "refresh"]
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setAlarmValue", [[name: "alarm*", type: "ENUM", description: "Any supported alarm attribute", constraints: ["off","both","siren","strobe"]]]
        command "setAlarmOff"
        command "setAlarmBoth"
        command "setAlarmSiren"
        command "setAlarmStrobe"
        command "setBatteryValue", [[name: "number*", type: "NUMBER", description: "Battery level in %"]]
        command "setContactValue", [[name: "contact*", type: "ENUM", description: "Any supported contact attribute", constraints: ["closed","open"]]]
        command "setContactClosed"
        command "setContactOpen"
        command "setDoorValue", [[name: "door*", type: "ENUM", description: "Any supported door attribute", constraints: ["closed","closing","open","opening","unknown"]]]
        command "setDoorClosed"
        command "setDoorClosing"
        command "setDoorOpen"
        command "setDoorOpening"
        command "setDoorUnknown"
        command "setSwitchValue", [[name: "switch*", type: "ENUM", description: "Any supported switch attribute", constraints: ["off","on"]]]
        command "setSwitchOff"
        command "setSwitchOn"
        command "setHealthStatusValue", [[name: "healthStatus*", type: "ENUM", description: "Any supported healthStatus attribute", constraints: ["offline","online"]]]
        command "replicaRules" // indicates getReplicaRules is available
    }
}

def installed() {
    log.info "${device.displayName} installed"
    initialize()
}

def updated() {
    log.info "${device.displayName} updated"
    initialize()
}

def initialize() {
}

def parse(String description) {
    log.info "${device.displayName} parse"
}

def setAlarmValue(String value) {
    sendEvent(name: "alarm", value: value, descriptionText: "${device.displayName} alarm set to $value")
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
    sendEvent(name: "battery", value: "$value", unit: "%", descriptionText: "${device.displayName} battery is ${value}%")
}

def setContactValue(String value) {
    sendEvent(name: "contact", value: value, descriptionText: "${device.displayName} contact set to $value")
}

def setContactClosed() {
    setContactValue("closed")
}

def setContactOpen() {
    setContactValue("open")
}

def setDoorValue(String value) {
    sendEvent(name: "door", value: value, descriptionText: "${device.displayName} door set to $value")
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

def setSwitchValue(String value) {
    sendEvent(name: "switch", value: value, descriptionText: "${device.displayName} switch set to $value")
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

private def sendCommand(String value, Map data=null, Boolean isStateChange=true) {
    sendEvent(name: "command", value: value, descriptionText: "${device.displayName} sending command ${data ? data : value }", data: data, isStateChange: isStateChange, displayed: false)
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

void replicaRules() {
    log.info getReplicaRules()
}

String getReplicaRules() {
    return """{"components":[{"trigger":{"attribute: command.close":{"dataType":"ENUM","name":"command","label":"attribute: command.close","value":"close"}},"command":{"command: close()":{"name":"close","capability":"doorControl","label":"command: close()"}},"type":"hubitatTrigger"},{"trigger":{"attribute: command.open":{"dataType":"ENUM","name":"command","label":"attribute: command.open","value":"open"}},"command":{"command: open()":{"name":"open","capability":"doorControl","label":"command: open()"}},"type":"hubitatTrigger"},{"trigger":{"attribute: contact.*":{"properties":{"value":{"title":"ContactState","type":"string","enum":["closed","open"]}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"}},"command":{"command: setContactValue(contact*)":{"arguments":["ENUM"],"parameters":[{"name":"contact*","description":"Any supported contact attribute","type":"ENUM","constraints":["closed","open"]}],"name":"setContactValue","label":"command: setContactValue(contact*)"}},"type":"smartTrigger"},{"trigger":{"attribute: door.*":{"properties":{"value":{"type":"string","enum":["closed","closing","open","opening","unknown"]}},"additionalProperties":false,"required":["value"],"capability":"doorControl","attribute":"door","label":"attribute: door.*"}},"command":{"command: setDoorValue(door*)":{"arguments":["ENUM"],"parameters":[{"name":"door*","description":"Any supported door attribute","type":"ENUM","constraints":["closed","closing","open","opening","unknown"]}],"name":"setDoorValue","label":"command: setDoorValue(door*)"}},"type":"smartTrigger"},{"trigger":{"attribute: healthStatus.*":{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.* "}},"command":{"command: setHealthStatusValue(healthStatus*)":{"arguments":["ENUM"],"parameters":[{"name":"healthStatus*","description":"Any supported healthStatus attribute","type":"ENUM","constraints":["offline","online"]}],"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)"}},"type":"smartTrigger","mute":true},{"trigger":{"attribute: switch.*":{"properties":{"value":{"title":"SwitchState","type":"string","enum":["on","off"]}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"}},"command":{"command: setSwitchValue(switch*)":{"arguments":["ENUM"],"parameters":[{"name":"switch*","description":"Any supported switch attribute","type":"ENUM","constraints":["off","on"]}],"name":"setSwitchValue","label":"command: setSwitchValue(switch*)"}},"type":"smartTrigger"}]}"""
}
