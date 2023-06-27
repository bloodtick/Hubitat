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
public static String version() {return "1.3.0"}

metadata 
{
    definition(name: "Replica Samsung OCF Microwave Hood", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaSamsungOcfMicrowaveHood.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        // Special capablity to allow for Hubitat dashboarding to set commands via the Button template
        // Use Hubitat 'Button Controller' built in app to set commands to run. Defaults to 10 commands increased or decreased by setNumberOfButtons.
        capability "PushableButton"
        command "setNumberOfButtons", [[name: "numberOfButtons*", type: "NUMBER", description: "Set the numberOfButtons this device support"]]
        
        //capability "samsungce.hoodFanSpeed"
        attribute "settableMaxFanSpeed", "number"
        attribute "hoodFanSpeed", "number"
        attribute "supportedHoodFanSpeed", "JSON_OBJECT"
        attribute "settableMinFanSpeed", "number"
        command "setHoodFanSpeed", [[name: "speed*", type: "NUMBER", description: "Set the hood fan speed to supported level"]]
        
        //capability "samsungce.lamp"        
        attribute "brightnessLevel", "enum", ["off", "on", "low", "mid", "high", "extraHigh"]                                                                                                                                                                                                                                                                                                                                                                                                         
        attribute "supportedBrightnessLevel", "JSON_OBJECT"
        command "setBrightnessLevel", [[name: "brightnessLevel*", type: "ENUM", description: "Set the brightness to supported level", constraints: ["off", "on", "low", "mid", "high", "extraHigh"]]] 
        
        attribute "healthStatus", "enum", ["offline", "online"]
       
    }
    preferences {
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
    }
}

def push(buttonNumber) {
    sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
}

def setNumberOfButtons(buttonNumber) {
    sendEvent(name: "numberOfButtons", value: buttonNumber)
}

def installed() {
	initialize()
    setNumberOfButtons(10)
}

def updated() {
	initialize()    
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
    // Test Debug
    //setSupportedHoodFanSpeedValue( [value:[0, 1, 2, 3, 4, 5]] )
    //setSupportedBrightnessLevelValue( [value:["off", "low", "high"]] )
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ "setSettableMaxFanSpeedValue":[[name:"settableMaxFanSpeed*",type:"NUMBER"]], "setHoodFanSpeedValue":[[name:"hoodFanSpeed*",type:"NUMBER"]], 
              "setSettableMinFanSpeedValue":[[name:"settableMinFanSpeed*",type:"NUMBER"]], "setSupportedHoodFanSpeedValue":[[name:"supportedHoodFanSpeed*",type:"JSON_OBJECT"]], 
              "setBrightnessLevelValue":[[name:"brightnessLevel*",type:"STRING"]], "setSupportedBrightnessLevelValue":[[name:"supportedBrightnessLevel*",type:"JSON_OBJECT"]], 
              "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]] ])
}

//capability "samsungce.hoodFanSpeed"
def setSettableMaxFanSpeedValue(value) {
    String descriptionText = "${device.displayName} settable max fan speed is $value"
    sendEvent(name: "settableMaxFanSpeed", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHoodFanSpeedValue(value) {
    String descriptionText = "${device.displayName} hood fan speed is $value"
    sendEvent(name: "hoodFanSpeed", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSettableMinFanSpeedValue(value) {
    String descriptionText = "${device.displayName} settable min fan speed is $value"
    sendEvent(name: "settableMinFanSpeed", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedHoodFanSpeedValue(value) {
    String descriptionText = "${device.displayName} supported hood fan speeds are ${value?.value}"
    sendEvent(name: "supportedHoodFanSpeed", value: groovy.json.JsonOutput.toJson(value?.value), descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.lamp"
def setBrightnessLevelValue(value) {
    String descriptionText = "${device.displayName} brightness level is $value"
    sendEvent(name: "brightnessLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
    
    if(value!="off") {
        state.lastBrightnessLevel = value
        sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch set to on [$value]")
    } else {
        sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch set to off")
    }
}

def setSupportedBrightnessLevelValue(value) {
    String descriptionText = "${device.displayName} supported brightness levels are ${value?.value}"
    sendEvent(name: "supportedBrightnessLevel", value: groovy.json.JsonOutput.toJson(value?.value), descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "setHoodFanSpeed":[[name:"speed*",type:"NUMBER"]], "setBrightnessLevel":[[name:"level*",type:"STRING"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def on() {
    String lastBrightnessLevel = state?.lastBrightnessLevel ?: (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedBrightnessLevel")))?.find{ it!="off" } ?: null
    if(lastBrightnessLevel) setBrightnessLevel(lastBrightnessLevel)
}
 
def off() {
    setBrightnessLevel("off")
}

def setHoodFanSpeed(speed) {
    if(device.currentValue("settableMaxFanSpeed")!=null && speed.toInteger()>device.currentValue("settableMaxFanSpeed").toInteger()) {
        speed = device.currentValue("settableMaxFanSpeed").toInteger()
    }
    sendCommand("setHoodFanSpeed", speed)
}

def setBrightnessLevel(level) {
    if(device.currentValue("supportedBrightnessLevel")==null || (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedBrightnessLevel")))?.find{ it==level }) {       
        // workaround until next release of HubiThings Replica to support strange dual schema for both integer OR string
        parent?.setSmartDeviceCommand(parent.getReplicaDeviceId(device), parent.getReplicaDataJsonValue(device, "replica")?.componentId, "samsungce.lamp", "setBrightnessLevel", [ level ])
        //sendCommand("setBrightnessLevel", level)
    } else if (device.currentValue("supportedBrightnessLevel")!=null) {
        logWarn "${device.displayName} setBrightnessLevel '$level' not found in ${device.currentValue("supportedBrightnessLevel")}"
    }
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.hoodFanSpeed","attribute":"hoodFanSpeed","label":"attribute: hoodFanSpeed.*"},"command":{"name":"setHoodFanSpeedValue","label":"command: setHoodFanSpeedValue(hoodFanSpeed*)","type":"command","parameters":[{"name":"hoodFanSpeed*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.hoodFanSpeed","attribute":"settableMaxFanSpeed","label":"attribute: settableMaxFanSpeed.*"},"command":{"name":"setSettableMaxFanSpeedValue","label":"command: setSettableMaxFanSpeedValue(settableMaxFanSpeed*)","type":"command","parameters":[{"name":"settableMaxFanSpeed*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.hoodFanSpeed","attribute":"settableMinFanSpeed","label":"attribute: settableMinFanSpeed.*"},"command":{"name":"setSettableMinFanSpeedValue","label":"command: setSettableMinFanSpeedValue(settableMinFanSpeed*)","type":"command","parameters":[{"name":"settableMinFanSpeed*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"integer"}}},"additionalProperties":false,"required":["value"],"capability":"samsungce.hoodFanSpeed","attribute":"supportedHoodFanSpeed","label":"attribute: supportedHoodFanSpeed.*"},"command":{"name":"setSupportedHoodFanSpeedValue","label":"command: setSupportedHoodFanSpeedValue(supportedHoodFanSpeed*)","type":"command","parameters":[{"name":"supportedHoodFanSpeed*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"oneOf":[{"type":"string","enum":["off","on","low","mid","high","extraHigh"]},{"type":"integer","minimum":0,"maximum":100}]}},"additionalProperties":false,"required":["value"],"capability":"samsungce.lamp","attribute":"brightnessLevel","label":"attribute: brightnessLevel.*"},"command":{"name":"setBrightnessLevelValue","label":"command: setBrightnessLevelValue(brightnessLevel*)","type":"command","parameters":[{"name":"brightnessLevel*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"oneOf":[{"type":"string","enum":["off","on","low","mid","high","extraHigh"]},{"type":"integer","minimum":0,"maximum":100}]},"type":"array"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.lamp","attribute":"supportedBrightnessLevel","label":"attribute: supportedBrightnessLevel.*"},"command":{"name":"setSupportedBrightnessLevelValue","label":"command: setSupportedBrightnessLevelValue(supportedBrightnessLevel*)","type":"command","parameters":[{"name":"supportedBrightnessLevel*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setHoodFanSpeed","label":"command: setHoodFanSpeed(speed*)","type":"command","parameters":[{"name":"speed*","type":"NUMBER"}]},"command":{"name":"setHoodFanSpeed","arguments":[{"name":"speed","optional":false,"schema":{"type":"integer"}}],"type":"command","capability":"samsungce.hoodFanSpeed","label":"command: setHoodFanSpeed(speed*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setBrightnessLevel","label":"command: setBrightnessLevel(level*)","type":"command","parameters":[{"name":"level*","type":"STRING"}]},"command":{"name":"setBrightnessLevel","arguments":[{"name":"level","optional":false,"schema":{"oneOf":[{"type":"string","enum":["off","on","low","mid","high","extraHigh"]},{"type":"integer","minimum":0,"maximum":100}]}}],"type":"command","capability":"samsungce.lamp","label":"command: setBrightnessLevel(level*)"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
