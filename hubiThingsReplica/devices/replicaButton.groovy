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
    definition(name: "Replica Button", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaButton.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "DoubleTapableButton"
        capability "HoldableButton"
        capability "PushableButton"
        capability "Refresh"
        capability "ReleasableButton"
        capability "TemperatureMeasurement"

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
    return ([ "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setDoubleTappedValue":[[name:"buttonNumber",type:"NUMBER"]], "setHeldValue":[[name:"buttonNumber",type:"NUMBER"]], "setNumberOfButtonsValue":[[name:"numberOfButtons*",type:"NUMBER"]],             
              "setPushedValue":[[name:"buttonNumber",type:"NUMBER"]], "setReleasedValue":[[name:"buttonNumber",type:"NUMBER"]], "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    log.info descriptionText
}

def setDoubleTappedValue(value=1) {
    String descriptionText = "${device.displayName} button $value was double tapped"
    sendEvent(name: "doubleTapped", value: value, descriptionText: descriptionText, isStateChange: true)
    log.info descriptionText
}

def setHeldValue(value=1) {
    String descriptionText = "${device.displayName} button $value was held"
    sendEvent(name: "held", value: value, descriptionText: descriptionText, isStateChange: true)
    log.info descriptionText
}

def setNumberOfButtonsValue(value=1) {
    sendEvent(name: "numberOfButtons", value: value, descriptionText: "${device.displayName} has $value number of buttons")
}

def setPushedValue(value=1) {
    String descriptionText = "${device.displayName} button $value was pushed"
    sendEvent(name: "pushed", value: value, descriptionText: descriptionText, isStateChange: true)
    log.info descriptionText
}

def setReleasedValue(value=1) {
    String descriptionText = "${device.displayName} button $value was released"
    sendEvent(name: "released", value: value, descriptionText: descriptionText, isStateChange: true)
    log.info descriptionText
}

def setTemperatureValue(value) {
    String unit = "??${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    log.info descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "doubleTap":[[name:"buttonNumber",type:"NUMBER"]], "hold":[[name:"buttonNumber",type:"NUMBER"]], "push":[[name:"buttonNumber",type:"NUMBER"]], "release":[[name:"buttonNumber",type:"NUMBER"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

def doubleTap(value=1) {
    sendCommand("doubleTap", value)    
}

def hold(value=1) {
    sendCommand("hold", value)    
}

def push(value=1) {
    sendCommand("push", value)    
}

def release(value=1) {
    sendCommand("release", value)    
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"numberOfButtons","label":"attribute: numberOfButtons.*"},"command":{"name":"setNumberOfButtonsValue","label":"command: setNumberOfButtonsValue(numberOfButtons*)","type":"command","parameters":[{"name":"numberOfButtons*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.held","value":"held","dataType":"ENUM"},"command":{"name":"setHeldValue","label":"command: setHeldValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.double","value":"double","dataType":"ENUM"},"command":{"name":"setDoubleTappedValue","label":"command: setDoubleTappedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed","dataType":"ENUM"},"command":{"name":"setPushedValue","label":"command: setPushedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger","mute":true}]}"""
}
