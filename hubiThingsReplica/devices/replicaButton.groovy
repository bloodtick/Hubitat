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
public static String version() {return "1.3.2"}

import groovy.transform.CompileStatic
import groovy.transform.Field
@Field volatile static Map<String,Long> g_mEventSendTime = [:]

metadata 
{
    definition(name: "Replica Button + Debounce", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaButton.groovy")
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
        input(name:"deviceDefaultButton", type: "number", title: "Default Device Button Number (Default:1):", range: "0..10000", defaultValue: 1)
        input(name:"deviceNumberOfButtons", type: "number", title: "Number Of Device Buttons (Disable:0):", range: "0..10000", defaultValue: 0)
        input(name:"deviceDebounce", type: "number", title: "Button debounce in milliseconds:", range: "0..10000", defaultValue: 0)
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
    }
}

def installed() {
	initialize()
}

def updated() {
	initialize()
    if(deviceNumberOfButtons) setNumberOfButtonsValue(deviceNumberOfButtons)
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
    return ([ "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setDoubleTappedValue":[[name:"buttonNumber",type:"NUMBER"]], "setHeldValue":[[name:"buttonNumber",type:"NUMBER"]], "setNumberOfButtonsValue":[[name:"numberOfButtons*",type:"NUMBER"]],             
              "setPushedValue":[[name:"buttonNumber",type:"NUMBER"]], "setReleasedValue":[[name:"buttonNumber",type:"NUMBER"]], "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setDoubleTappedValue(value=deviceDefaultButton?:1) {
    if(debounce("setDoubleTappedValue")) return
    String descriptionText = "${device.displayName} button $value was double tapped"
    sendEvent(name: "doubleTapped", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setHeldValue(value=deviceDefaultButton?:1) {
    if(debounce("setHeldValue")) return
    String descriptionText = "${device.displayName} button $value was held"
    sendEvent(name: "held", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setNumberOfButtonsValue(value=1) {
    sendEvent(name: "numberOfButtons", value: value, descriptionText: "${device.displayName} has $value number of buttons")
}

def setPushedValue(value=deviceDefaultButton?:1) {
    if(debounce("setPushedValue")) return
    String descriptionText = "${device.displayName} button $value was pushed"
    sendEvent(name: "pushed", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setReleasedValue(value=deviceDefaultButton?:1) {
    if(debounce("setReleasedValue")) return
    String descriptionText = "${device.displayName} button $value was released"
    sendEvent(name: "released", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "doubleTap":[[name:"buttonNumber",type:"NUMBER"]], "hold":[[name:"buttonNumber",type:"NUMBER"]], "push":[[name:"buttonNumber",type:"NUMBER"]], "release":[[name:"buttonNumber",type:"NUMBER"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def doubleTap(value=deviceDefaultButton?:1) {
    sendCommand("doubleTap", value)    
}

def hold(value=deviceDefaultButton?:1) {
    sendCommand("hold", value)    
}

def push(value=deviceDefaultButton?:1) {
    sendCommand("push", value)    
}

def release(value=deviceDefaultButton?:1) {
    sendCommand("release", value)    
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"numberOfButtons","label":"attribute: numberOfButtons.*"},"command":{"name":"setNumberOfButtonsValue","label":"command: setNumberOfButtonsValue(numberOfButtons*)","type":"command","parameters":[{"name":"numberOfButtons*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.held","value":"held","dataType":"ENUM"},"command":{"name":"setHeldValue","label":"command: setHeldValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.double","value":"double","dataType":"ENUM"},"command":{"name":"setDoubleTappedValue","label":"command: setDoubleTappedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed","dataType":"ENUM"},"command":{"name":"setPushedValue","label":"command: setPushedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"push","label":"command: push(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"doubleTap","label":"command: doubleTap(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.double","value":"double","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"hold","label":"command: hold(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.held","value":"held","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"release","label":"command: release(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.up","value":"up","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.up","value":"up","dataType":"ENUM"},"command":{"name":"setReleasedValue","label":"command: setReleasedValue(buttonNumber)","type":"command","parameters":[{"name":"buttonNumber","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"dataType":"NUMBER","name":"numberOfButtons","type":"attribute","label":"attribute: numberOfButtons.*"},"command":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"numberOfButtons","label":"attribute: numberOfButtons.*"},"type":"hubitatTrigger"}]}"""
}

private Boolean debounce(String method) {
    Boolean response = false
    String methodDeviceId = "$method${device.getId()}"
    if(deviceDebounce && g_mEventSendTime[methodDeviceId] && (now() - g_mEventSendTime[methodDeviceId] < deviceDebounce)) {
        response = true
        logInfo "${device.displayName} $method debonce"
    } else g_mEventSendTime[methodDeviceId] = now()   
    return response
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
