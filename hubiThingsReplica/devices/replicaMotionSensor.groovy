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
    definition(name: "Replica Motion Sensor", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaMotionSensor.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "MotionSensor"
        capability "TemperatureMeasurement"
        
        attribute "command", "enum", ["active", "inactive"]
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setBatteryValue", [[name: "battery*", type: "NUMBER", description: "Battery level in %"]]
        command "setMotionValue", [[name: "motion*", type: "ENUM", description: "Any supported motion attribute", constraints: ["active","inactive"]]]
        command "setMotionActive"
        command "setMotionInactive"
        command "setTemperatureValue", [[name: "temperature*", type: "NUMBER", description: "Temperature level in F or C"]]
        command "setHealthStatusValue", [[name: "healthStatus*", type: "ENUM", description: "Any supported healthStatus attribute", constraints: ["offline","online"]]]
        command "inactive"
        command "active"
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

def setBatteryValue(value) {
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: "${device.displayName} battery level is $value %")
}

def setMotionValue(value) {
    sendEvent(name: "motion", value: value, descriptionText: "${device.displayName} motion set to $value")
}

def setMotionActive() {
    setMotionValue("active")
}

def setMotionInactive() {
    setMotionValue("inactive")    
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: "${device.displayName} temperature is $value $unit")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

private def sendCommand(String value, Map data=null, Boolean isStateChange=true) {
    sendEvent(name: "command", value: value, descriptionText: "${device.displayName} sending command ${data ? data : value }", data: data, isStateChange: isStateChange, displayed: false)
}

def inactive() {
    sendCommand("inactive")    
}

def active() {
    sendCommand("active")    
}

void replicaRules() {
    log.info getReplicaRules()
}

String getReplicaRules() {
    return """{"components":[{"trigger":{"attribute: battery.*":{"title":"IntegerPercent","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"}},"command":{"command: setBatteryValue(battery*)":{"arguments":["NUMBER"],"parameters":[{"name":"battery*","description":"Battery level in %","type":"NUMBER"}],"name":"setBatteryValue","label":"command: setBatteryValue(battery*)"}},"type":"smartTrigger","mute":true},{"trigger":{"attribute: healthStatus.*":{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.* "}},"command":{"command: setHealthStatusValue(healthStatus*)":{"arguments":["ENUM"],"parameters":[{"name":"healthStatus*","description":"Any supported healthStatus attribute","type":"ENUM","constraints":["offline","online"]}],"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)"}},"type":"smartTrigger","mute":true},{"trigger":{"attribute: motion.*":{"properties":{"value":{"title":"ActivityState","type":"string","enum":["active","inactive"]}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.*"}},"command":{"command: setMotionValue(motion*)":{"arguments":["ENUM"],"parameters":[{"name":"motion*","description":"Any supported motion attribute","type":"ENUM","constraints":["active","inactive"]}],"name":"setMotionValue","label":"command: setMotionValue(motion*)"}},"type":"smartTrigger"},{"trigger":{"attribute: temperature.*":{"properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"}},"command":{"command: setTemperatureValue(temperature*)":{"arguments":["NUMBER"],"parameters":[{"name":"temperature*","description":"Temperature level in F or C","type":"NUMBER"}],"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)"}},"type":"smartTrigger","mute":true}]}"""
}
