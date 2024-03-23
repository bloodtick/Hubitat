/**
*  Copyright 2024 Bloodtick
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
public static String version() {return "1.3.3"}

import groovy.transform.CompileStatic
import groovy.transform.Field
@Field volatile static Map<String,Long> g_mEventSendTime = [:]
// this is how SmartThings does it. So seems to be a char limitation for the enum in Hubitat, so I commented out the swipe functions and put them in their own attribute. Command is the same.
@Field static final List g_lButtonValues = ["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x"]//,"up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]
@Field static final List g_lSwipesValues = ["swipe_up","swipe_down","swipe_left","swipe_right"]

metadata 
{
    definition(name: "Replica Button", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaButton.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "DoubleTapableButton"
        capability "HoldableButton"
        capability "ReleasableButton"
        capability "PushableButton"
        capability "TemperatureMeasurement"
        capability "Switch"
        capability "Refresh"
        
        command "button", [[name: "button*", type: "ENUM", description: "SmartThings Button Value", constraints: g_lButtonValues]] 
        command "swipe", [[name: "button*", type: "ENUM", description: "SmartThings Button Value", constraints: g_lSwipesValues]]
        command "down", [[name: "down*", type: "NUMBER", description: "Button number that was down"]]
        //command "test"        
        
        attribute "button", "enum", g_lButtonValues
        attribute "swipe",  "enum", g_lSwipesValues
        attribute "down", "number"

        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {
        input(name:"deviceDefaultButton", type: "number", title: "Default Device Button Number (Default:1):", range: "0..10000", defaultValue: 1)
        input(name:"deviceNumberOfButtons", type: "number", title: "Number Of Device Buttons (Disable:0):", range: "0..10000", defaultValue: 0)
        input(name:"deviceDebounce", type: "number", title: "Button debounce in milliseconds:", range: "0..10000", defaultValue: 0)
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
        input(name:"deviceDebugEnable", type: "bool", title: "Enable Debug logging:", defaultValue: false)
    }
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "${device.displayName} disabling debug logs"
}
Boolean autoLogsOff() { if ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) runIn(1800, "logsOff"); else unschedule('logsOff');}

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
    refresh()
    autoLogsOff()
    
    location.hub.each{
        logWarn groovy.json.JsonOutput.toJson(it)
    }
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ "setButtonValue":[[name:"button*",type:"NUMBER"]], "setSupportedButtonValuesValue":[[name:"supportedButtonValues*",type:"STRING"]], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], 
              "setDoubleTappedValue":[[name:"buttonNumber",type:"NUMBER"]], "setHeldValue":[[name:"buttonNumber",type:"NUMBER"]], "setNumberOfButtonsValue":[[name:"numberOfButtons*",type:"NUMBER"]], 
              "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], "setPushedValue":[[name:"buttonNumber",type:"NUMBER"]], "setReleasedValue":[[name:"buttonNumber",type:"NUMBER"]], 
              "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setSwitchValue(value) {
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

def setButtonValue(value) {
    // first do what ST does
    if(debounce("setButtonValue_$value")) return    
    sendEvent(name: (value?.contains("swipe") ? "swipe" : "button"), value: value, descriptionText: "${device.displayName} button value is $value", isStateChange: true)
    // now attempt to do what HE does
    Map button = buildMapFromString(value)
    button?.each{ k,v ->
        switch(k) {
            case "pushed": setPushedValue(v); break
            case "held": setHeldValue(v); break
            case "double": setDoubleTappedValue(v); break
            case "up": setReleasedValue(v); break
            case "down": sendEvent(name: "down", value: v, descriptionText: "${device.displayName} button $v is down", isStateChange: true); break
            default:
                logDebug "${device.displayName} does not handle '$value' use ${k?.contains("swipe") ? "swipe" : "button"} attribute"
        }
    }    
}

def setSupportedButtonValuesValue(value) {
    List supportedButtonValues = state?.supportedButtonValues ? (new groovy.json.JsonSlurper().parseText(state.supportedButtonValues)) : []
    if(value==null || value.sort()==supportedButtonValues.sort()) return 
    
    state.supportedButtonValues=groovy.json.JsonOutput.toJson(value)
    logInfo "${device.displayName} supported button values are $value"
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    //logInfo descriptionText
}

def setDoubleTappedValue(value=deviceDefaultButton?:1) {
    if(debounce("setDoubleTappedValue_$value")) return
    String descriptionText = "${device.displayName} button $value was double tapped"
    sendEvent(name: "doubleTapped", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setHeldValue(value=deviceDefaultButton?:1) {
    if(debounce("setHeldValue_$value")) return
    String descriptionText = "${device.displayName} button $value was held"
    sendEvent(name: "held", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setNumberOfButtonsValue(value=1) {
    sendEvent(name: "numberOfButtons", value: value, descriptionText: "${device.displayName} has $value number of buttons")
}

def setPushedValue(value=deviceDefaultButton?:1) {
    if(debounce("setPushedValue_$value")) return
    String descriptionText = "${device.displayName} button $value was pushed"
    sendEvent(name: "pushed", value: value, descriptionText: descriptionText, isStateChange: true)
    logInfo descriptionText
}

def setReleasedValue(value=deviceDefaultButton?:1) {
    if(debounce("setReleasedValue_$value")) return
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
static Map getReplicaTriggers() {
    return ([ "off":[] , "on":[], "button":[[name:"button",type:"ENUM"]], "doubleTap":[[name:"buttonNumber",type:"NUMBER"]], "hold":[[name:"buttonNumber",type:"NUMBER"]],
              "push":[[name:"buttonNumber",type:"NUMBER"]], "release":[[name:"buttonNumber",type:"NUMBER"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

def button(value) {
    sendCommand("button", value)    
}

def swipe(value) {
    sendCommand("button", value)    
}

def doubleTap(value=deviceDefaultButton?:1) {
    button( buildStringFromMap(["double":value]))
    //sendCommand("doubleTap", value)    
}

def hold(value=deviceDefaultButton?:1) {
    button( buildStringFromMap(["held":value]))
    //sendCommand("hold", value)    
}

def push(value=deviceDefaultButton?:1) {
    button( buildStringFromMap(["pushed":value]))
    //sendCommand("push", value)    
}

def release(value=deviceDefaultButton?:1) {
    button( buildStringFromMap(["up":value]))
    //sendCommand("release", value)    
}

def down(value=deviceDefaultButton?:1) {
    button( buildStringFromMap(["down":value]))
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"command":{"label":"command: setNumberOfButtonsValue(numberOfButtons*)","name":"setNumberOfButtonsValue","parameters":[{"name":"numberOfButtons*","type":"NUMBER"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"numberOfButtons","capability":"button","label":"attribute: numberOfButtons.*","properties":{"value":{"minimum":0,"title":"PositiveInteger","type":"integer"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setBatteryValue(battery*)","name":"setBatteryValue","parameters":[{"name":"battery*","type":"NUMBER"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"battery","capability":"battery","label":"attribute: battery.*","properties":{"unit":{"default":"%","enum":["%"],"type":"string"},"value":{"maximum":100,"minimum":0,"type":"integer"}},"required":["value"],"title":"IntegerPercent","type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setTemperatureValue(temperature*)","name":"setTemperatureValue","parameters":[{"name":"temperature*","type":"NUMBER"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"temperature","capability":"temperatureMeasurement","label":"attribute: temperature.*","properties":{"unit":{"enum":["F","C"],"type":"string"},"value":{"maximum":10000,"minimum":-460,"title":"TemperatureValue","type":"number"}},"required":["value","unit"],"type":"attribute"},"type":"smartTrigger"},{"command":{"additionalProperties":false,"attribute":"numberOfButtons","capability":"button","label":"attribute: numberOfButtons.*","properties":{"value":{"minimum":0,"title":"PositiveInteger","type":"integer"}},"required":["value"],"type":"attribute"},"mute":true,"trigger":{"dataType":"NUMBER","label":"attribute: numberOfButtons.*","name":"numberOfButtons","type":"attribute"},"type":"hubitatTrigger"},{"command":{"label":"command: setButtonValue(button*)","name":"setButtonValue","parameters":[{"name":"button*","type":"NUMBER"}],"type":"command"},"disableStatus":true,"trigger":{"additionalProperties":false,"attribute":"button","capability":"button","label":"attribute: button.*","properties":{"value":{"title":"ButtonState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"additionalProperties":false,"attribute":"button","capability":"button","label":"attribute: button.*","properties":{"value":{"enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"],"title":"ButtonState","type":"string"}},"required":["value"],"type":"attribute"},"disableStatus":true,"trigger":{"label":"command: button(button)","name":"button","parameters":[{"name":"button","type":"ENUM"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setSupportedButtonValuesValue(supportedButtonValues*)","name":"setSupportedButtonValuesValue","parameters":[{"name":"supportedButtonValues*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"supportedButtonValues","capability":"button","label":"attribute: supportedButtonValues.*","properties":{"value":{"items":{"enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"],"title":"ButtonState","type":"string"},"type":"array"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setHealthStatusValue(healthStatus*)","name":"setHealthStatusValue","parameters":[{"name":"healthStatus*","type":"ENUM"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"healthStatus","capability":"healthCheck","label":"attribute: healthStatus.*","properties":{"value":{"title":"HealthState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setSwitchValue(switch*)","name":"setSwitchValue","parameters":[{"name":"switch*","type":"ENUM"}],"type":"command"},"disableStatus":false,"mute":true,"trigger":{"additionalProperties":false,"attribute":"switch","capability":"switch","label":"attribute: switch.*","properties":{"value":{"title":"SwitchState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"capability":"switch","label":"command: off()","name":"off","type":"command"},"trigger":{"label":"command: off()","name":"off","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"switch","label":"command: on()","name":"on","type":"command"},"trigger":{"label":"command: on()","name":"on","type":"command"},"type":"hubitatTrigger"}]}"""
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

def test() {
    List actions = [null, "pushed_2", "up_hold_2x","swipe_up_2x","swipe_down_2x","swipe_left_2x","swipe_right_2x"] + g_lButtonValues + g_lSwipesValues
    //List actions = ["up", "down", "pushed", "up_hold","down_hold","held","up_2x", "down_2x","pushed_2x","up_3x","down_3x", "pushed_3x"]    
    actions.each{ 
        logInfo "$it = ${buildMapFromString(it)} = ${buildStringFromMap(buildMapFromString(it))}"
        setButtonValue(it)
    }    
}    

Map buildMapFromString(value) {
    Map result = [:] // Initialize an empty map to store the parsing result
    // Split the input string by '_'
    String[] parts = value?.split("_")
    // Check the number of parts to determine how to proceed
    if(parts && !parts[parts.length-1]?.matches("^\\d+x?\$")) {
        // If there's only one part, it means there's no number specified, default to 1
        result[value] = 1
    } else if (parts) {
        // If there are two parts, the second part should indicate the number
        // Check if the second part is numeric or ends with 'x' and then remove 'x'
        Integer number = parts[parts.length-1].replaceAll("x", "") as Integer
        result[value.replaceAll("_${parts[parts.length-1]}\$", "")] = number
    }
    return result
}

String buildStringFromMap(Map value) {
    String result = null
    value?.each{ k, v ->
        if (v == 1) {
            result = k // If the value is 1, just use the key
        } else {
            result = "${k}_${v}x" // Otherwise, format with _<value>x
        }
    }
    return result
} 

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
