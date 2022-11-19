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
    definition(name: "Replica Button", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaButton.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "DoubleTapableButton"
        capability "HoldableButton"
        capability "PushableButton"
        capability "ReleasableButton"
        capability "TemperatureMeasurement"
        
        attribute "command", "enum", ["doubleTap.*", "hold.*", "push.*", "release.*"]
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setBatteryValue", [[name: "battery*", type: "NUMBER", description: "Battery level in %"]]
        command "setDoubleTappedValue", [[name: "doubleTapped*", type: "NUMBER", description: "Button number double tapped"]]  
        command "setHeldValue", [[name: "held*", type: "NUMBER", description: "Button number held"]]
        command "setNumberOfButtonsValue", [[name: "numberOfButtons*", type: "NUMBER", description: "Number of device buttons"]]
        command "setPushedValue", [[name: "pushed*", type: "NUMBER", description: "Button number pushed"]]
        command "setReleasedValue", [[name: "released*", type: "NUMBER", description: "Button number released"]]
        command "setTemperatureValue", [[name: "temperature*", type: "NUMBER", description: "Temperature level in F or C"]]
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

def setBatteryValue(value) {
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: "${device.displayName} battery level is $value %")
}

def setDoubleTappedValue(value=1) {
    sendEvent(name: "doubleTapped", value: value, descriptionText: "${device.displayName} button $value was double tapped", isStateChange: true)
}

def setHeldValue(value=1) {
    sendEvent(name: "held", value: value, descriptionText: "${device.displayName} button $value was held", isStateChange: true)
}

def setNumberOfButtonsValue(value=1) {
    sendEvent(name: "numberOfButtons", value: value, descriptionText: "${device.displayName} has $value number of buttons")
}

def setPushedValue(value=1) {
    sendEvent(name: "pushed", value: value, descriptionText: "${device.displayName} button $value was pushed", isStateChange: true)
}

def setReleasedValue(value=1) {
    sendEvent(name: "released", value: value, descriptionText: "${device.displayName} button $value was released", isStateChange: true)
}

def setTemperatureValue(value) {
    sendEvent(name: "temperature", value: value, unit: "°F", descriptionText: "${device.displayName} temperature is $value °F")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

private def sendCommand(String value, Map data=null, Boolean isStateChange=true) {
    sendEvent(name: "command", value: value, descriptionText: "${device.displayName} sending command ${data ? data : value }", data: data, isStateChange: isStateChange, displayed: false)
}

def doubleTap(value=1) {
    sendCommand("doubleTap.*", ["doubleTap.*":[doubleTap:value]])    
}

def hold(value=1) {
    sendCommand("hold.*", ["hold.*":[hold:value]])    
}

def push(value=1) {
    sendCommand("push.*", ["push.*":[push:value]])    
}

def release(value=1) {
    sendCommand("release.*", ["release.*":[release:value]])    
}

void replicaRules() {
    log.info getReplicaRules()
}

String getReplicaRules() {
    return """{"components":[{"trigger":{"attribute: battery.*":{"title":"IntegerPercent","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"}},"command":{"command: setBatteryValue(battery*)":{"arguments":["NUMBER"],"parameters":[{"name":"battery*","description":"Battery level in %","type":"NUMBER"}],"name":"setBatteryValue","label":"command: setBatteryValue(battery*)"}},"type":"smartTrigger","mute":true},{"trigger":{"attribute: button.double":{"properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.double","value":"double"}},"command":{"command: setDoubleTappedValue(doubleTapped*)":{"arguments":["NUMBER"],"parameters":[{"name":"doubleTapped*","description":"Button number double tapped","type":"NUMBER"}],"name":"setDoubleTappedValue","label":"command: setDoubleTappedValue(doubleTapped*)"}},"type":"smartTrigger","disableStatus":true},{"trigger":{"attribute: button.held":{"properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.held","value":"held"}},"command":{"command: setHeldValue(held*)":{"arguments":["NUMBER"],"parameters":[{"name":"held*","description":"Button number held","type":"NUMBER"}],"name":"setHeldValue","label":"command: setHeldValue(held*)"}},"type":"smartTrigger","disableStatus":true},{"trigger":{"attribute: button.pushed":{"properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed"}},"command":{"command: setPushedValue(pushed*)":{"arguments":["NUMBER"],"parameters":[{"name":"pushed*","description":"Button number pushed","type":"NUMBER"}],"name":"setPushedValue","label":"command: setPushedValue(pushed*)"}},"type":"smartTrigger","disableStatus":true},{"trigger":{"attribute: healthStatus.*":{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.* "}},"command":{"command: setHealthStatusValue(healthStatus*)":{"arguments":["ENUM"],"parameters":[{"name":"healthStatus*","description":"Any supported healthStatus attribute","type":"ENUM","constraints":["offline","online"]}],"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)"}},"type":"smartTrigger","mute":true},{"trigger":{"attribute: temperature.*":{"properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"}},"command":{"command: setTemperatureValue(temperature*)":{"arguments":["NUMBER"],"parameters":[{"name":"temperature*","description":"Temperature level in F or C","type":"NUMBER"}],"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)"}},"type":"smartTrigger","mute":true},{"trigger":{"attribute: numberOfButtons.*":{"properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"numberOfButtons","label":"attribute: numberOfButtons.*"}},"command":{"command: setNumberOfButtonsValue(numberOfButtons*)":{"arguments":["NUMBER"],"parameters":[{"name":"numberOfButtons*","description":"Number of device buttons","type":"NUMBER"}],"name":"setNumberOfButtonsValue","label":"command: setNumberOfButtonsValue(numberOfButtons*)"}},"type":"smartTrigger","mute":true}]}"""
}
