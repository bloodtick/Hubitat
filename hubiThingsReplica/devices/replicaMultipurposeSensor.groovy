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
    definition(name: "Replica Multipurpose Sensor", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaMultipurposeSensor.groovy")
    {
        capability "AccelerationSensor"
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "ContactSensor"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "ThreeAxis"
        
        attribute "vibration", "enum", ["active", "inactive"] // smartthings hack. no documentation or event.
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
    return ([ "setAccelerationValue":[[name:"acceleration*",type:"ENUM"]], "setAccelerationActive":[], "setAccelerationInactive":[], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], 
              "setContactSensorValue":[[name:"contact*",type:"ENUM"]], "setContactSensorClosed":[], "setContactSensorOpen":[], "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], 
              "setThreeAxisValue":[[name:"threeAxis*",type:"VECTOR3"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setAccelerationValue(value) {
    String descriptionText = "${device.displayName} acceleration is $value"
    sendEvent(name: "acceleration", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setAccelerationActive() {
    setAccelerationValue("active")
}

def setAccelerationInactive() {
    setAccelerationValue("inactive")    
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    log.info descriptionText
}

def setContactSensorValue(value) {
    String descriptionText = "${device.displayName} contact is $value"
    sendEvent(name: "contact", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setContactSensorClosed() {
    setContactSensorValue("closed")
}

def setContactSensorOpen() {
    setContactSensorValue("open")    
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    log.info descriptionText
}

def setThreeAxisValue(value) {
    String descriptionText = "${device.displayName} 3-axis is X:${value[0]}, Y:${value[1]}, Z:${value[2]}"
    sendEvent(name: "threeAxis", value: value, descriptionText: descriptionText)
    vibrationActive()
}

def vibrationActive() {
    sendEvent(name: "vibration", value: "active", descriptionText: "${device.displayName} vibration is active")
    runIn(5,'vibrationInactive') 
}
          
def vibrationInactive() {
    sendEvent(name: "vibration", value: "inactive", descriptionText: "${device.displayName} vibration is inactive")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return (["refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"accelerationSensor","attribute":"acceleration","label":"attribute: acceleration.*"},"command":{"name":"setAccelerationValue","label":"command: setAccelerationValue(acceleration*)","type":"command","parameters":[{"name":"acceleration*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"command":{"name":"setContactSensorValue","label":"command: setContactSensorValue(contact*)","type":"command","parameters":[{"name":"contact*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"integer","minimum":-10000,"maximum":10000},"minItems":3,"maxItems":3},"unit":{"type":"string","enum":["mG"],"default":"mG"}},"additionalProperties":false,"required":["value"],"capability":"threeAxis","attribute":"threeAxis","label":"attribute: threeAxis.*"},"command":{"name":"setThreeAxisValue","label":"command: setThreeAxisValue(threeAxis*)","type":"command","parameters":[{"name":"threeAxis*","type":"VECTOR3"}]},"type":"smartTrigger","mute":true}]}"""
}