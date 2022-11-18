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
    definition(name: "Replica Switch", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaSwitch.groovy")
    {
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        
        attribute "command", "enum", ["off", "on", "refresh"]
        attribute "healthStatus", "enum", ["offline", "online"]
     
        command "setSwitchValue", [[name: "switch*", type: "ENUM", description: "Any supported switch attribute", constraints: ["off","on"]]]
        command "setSwitchOff"
        command "setSwitchOn"
        command "setHealthStatusValue", [[name: "healthStatus*", type: "ENUM", description: "Any supported healthStatus attribute", constraints: ["offline","online"]]]
        command "replicaRules" // indicates getReplicaRules is available
    }
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
}

def parse(String description) {
    log.info "${device.displayName} parse"
}

def setSwitchValue(value) {
    sendEvent(name: "switch", value: value, descriptionText: "${device.displayName} switch set to $value")
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

private def sendCommand(String value, Map data=null, Boolean isStateChange=true) {
    sendEvent(name: "command", value: value, descriptionText: "${device.displayName} sending command ${data ? data : value }", data: data, isStateChange: isStateChange, displayed: false)
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

void refresh() {
    sendCommand("refresh")
}

void replicaRules() {
    log.info getReplicaRules()
}

String getReplicaRules() {
    return """{"components":[{"trigger":{"attribute: command.on":{"dataType":"ENUM","name":"command","label":"attribute: command.on","value":"on"}},"command":{"command: on()":{"name":"on","capability":"switch","label":"command: on()"}},"type":"hubitatTrigger"},{"trigger":{"attribute: command.off":{"dataType":"ENUM","name":"command","label":"attribute: command.off","value":"off"}},"command":{"command: off()":{"name":"off","capability":"switch","label":"command: off()"}},"type":"hubitatTrigger"},{"trigger":{"attribute: switch.*":{"properties":{"value":{"title":"SwitchState","type":"string","enum":["on","off"]}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"}},"command":{"command: setSwitchValue(switch*)":{"arguments":["ENUM"],"parameters":[{"name":"switch*","description":"Any supported switch attribute","type":"ENUM","constraints":["off","on"]}],"name":"setSwitchValue","label":"command: setSwitchValue(switch*)"}},"type":"smartTrigger"},{"trigger":{"attribute: healthStatus.*":{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.* "}},"command":{"command: setHealthStatusValue(healthStatus*)":{"arguments":["ENUM"],"parameters":[{"name":"healthStatus*","description":"Any supported healthStatus attribute","type":"ENUM","constraints":["offline","online"]}],"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)"}},"type":"smartTrigger","mute":true}]}"""
}
