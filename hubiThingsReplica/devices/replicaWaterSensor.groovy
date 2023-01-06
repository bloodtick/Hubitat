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
public static String version() {return "1.2.1"}

metadata 
{
    definition(name: "Replica Water Sensor", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaWaterSensor.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"
        capability "WaterSensor"
        
        attribute "signalMetrics", "string" //custom capability
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setSignalMetrics", [[name: "value*", type: "NUMBER", description: "Set Signal Metrics (custom)"]] //custom capability
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
    return ([ "setSignalMetricsValue":[[name:"signalMetrics*",type:"STRING"]], "setWaterValue":[[name:"water*",type:"ENUM"]], "setWaterDry":[], "setWaterWet":[], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    log.info descriptionText
}

def setWaterValue(value) {
    String descriptionText = "${device.displayName} water state is $value"
    sendEvent(name: "water", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setWaterDry() {
    setWaterValue("dry")
}

def setWaterWet() {
    setWaterValue("wet")    
}

def setSignalMetricsValue(value) {
    String descriptionText = "${device.displayName} signal metrics are $value"
    sendEvent(name: "signalMetrics", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "setSignalMetrics":[[name:"value*",type:"STRING"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

//custom capability
def setSignalMetrics(value) {
    sendCommand("setSignalMetrics", value)    
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"MoistureState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"waterSensor","attribute":"water","label":"attribute: water.*"},"command":{"name":"setWaterValue","label":"command: setWaterValue(water*)","type":"command","parameters":[{"name":"water*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"legendabsolute60149.signalMetrics","attribute":"signalMetrics","label":"attribute: signalMetrics.*"},"command":{"name":"setSignalMetricsValue","label":"command: setSignalMetricsValue(signalMetrics*)","type":"command","parameters":[{"name":"signalMetrics*","type":"STRING"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"setSignalMetrics","label":"command: setSignalMetrics(value*)","type":"command","parameters":[{"name":"value*","type":"STRING"}]},"command":{"name":"setSignalMetrics","arguments":[{"name":"value","optional":false,"schema":{"type":"string"}}],"type":"command","capability":"legendabsolute60149.signalMetrics","label":"command: setSignalMetrics(value*)"},"type":"hubitatTrigger","mute":true}]}"""
}
