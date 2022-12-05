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
*  version 1.2.0
*/
import groovy.json.*
    
metadata 
{
    definition(name: "Replica Window Shade", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaWindowShade.groovy")
	{
        capability "Actuator"
        capability "Configuration"
        capability "EnergyMeter"
        capability "PowerMeter"
        capability "Refresh"
        capability "WindowShade"
        //capability "windowShadeLevel" // doesn't exist in Hubitat
        
        attribute "supportedWindowShadeCommands", "JSON_OBJECT" //capability "windowShade" in SmartThings
        attribute "shadeLevel", "number" //capability "windowShadeLevel" in SmartThings       
        attribute "healthStatus", "enum", ["offline", "online"]
		
        command "pause" //capability "windowShade" in SmartThings
        command "setShadeLevel", [[name: "shadeLevel*", type: "NUMBER", description: "Shade level in %"]] //capability "windowShadeLevel" in SmartThings
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
    return ([ "setEnergyValue":[[name:"energy*",type:"NUMBER"]], "setPowerValue":[[name:"power*",type:"NUMBER"]], "setShadeLevelValue":[[name:"shadeLevel*",type:"NUMBER"]], 
              "setSupportedWindowShadeCommandsValue":[[name:"supportedWindowShadeCommands*",type:"JSON_OBJECT"]], "setWindowShadeValue":[[name:"windowShade*",type:"ENUM"]], 
              "setWindowShadePartiallyOpen":[], "setWindowShadeClosed":[], "setWindowShadeOpen":[], "setWindowShadeClosing":[], "setWindowShadeUnknown":[], 
              "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setEnergyValue(value) {
    String descriptionText = "${device.displayName} energy is $value kWh"
    sendEvent(name: "energy", value: "$value", unit: "kWh", descriptionText: descriptionText)
    log.info descriptionText
}

def setPowerValue(value) {
    String descriptionText = "${device.displayName} power is $value W"
    sendEvent(name: "power", value: "$value", unit: "W", descriptionText: descriptionText)
    log.info descriptionText
}

def setShadeLevelValue(value) {
    String descriptionText = "${device.displayName} shade level is $value %"
    sendEvent(name: "shadeLevel", value: "$value", unit: "%", descriptionText: descriptionText)
    log.info descriptionText
}

def setSupportedWindowShadeCommandsValue(value) {
    String descriptionText = "${device.displayName} supported window shade commands are $value"
    sendEvent(name: "supportedWindowShadeCommands", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setWindowShadeValue(value) {
    String descriptionText = "${device.displayName} window shade is $value"
    sendEvent(name: "windowShade", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setWindowShadeOpening() {
    setWindowShadeValue("opening")
}

def setWindowShadePartiallyOpen() {
    setWindowShadeValue("partially open")
}

def setWindowShadeClosed() {
    setWindowShadeValue("closed")
}

def setWindowShadeOpen() {
    setWindowShadeValue("open")
}

def setWindowShadeClosing() {
    setWindowShadeValue("closing")
}

def setWindowShadeUnknown() {
    setWindowShadeValue("unknown")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "close":[] , "open":[], "pause":[], "setShadeLevel":[[name:"shadeLevel*",type:"NUMBER"]], "setPosition":[[name:"position*",type:"NUMBER"]], "startPositionChange":[[name:"direction*",type:"ENUM"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

def close() { 
    sendCommand("close")
}

def open() {
    sendCommand("open")
}

def pause() {
    sendCommand("pause")
}

def setShadeLevel(value) {
    sendCommand("setShadeLevel", value, "%")    
}

def setPosition(value) {
    sendCommand("setPosition", value, "%")    
}

def startPositionChange(value) {
    sendCommand("startPositionChange", value)    
}

void stopPositionChange() {
    sendCommand("stopPositionChange")
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"OpenableState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"windowShade","attribute":"windowShade","label":"attribute: windowShade.*"},"command":{"name":"setWindowShadeValue","label":"command: setWindowShadeValue(windowShade*)","type":"command","parameters":[{"name":"windowShade*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"windowShadeLevel","attribute":"shadeLevel","label":"attribute: shadeLevel.*"},"command":{"name":"setShadeLevelValue","label":"command: setShadeLevelValue(shadeLevel*)","type":"command","parameters":[{"name":"shadeLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"close","label":"command: close()","type":"command"},"command":{"name":"close","type":"command","capability":"windowShade","label":"command: close()"},"type":"hubitatTrigger"},{"trigger":{"name":"open","label":"command: open()","type":"command"},"command":{"name":"open","type":"command","capability":"windowShade","label":"command: open()"},"type":"hubitatTrigger"},{"trigger":{"name":"pause","label":"command: pause()","type":"command"},"command":{"name":"pause","type":"command","capability":"windowShade","label":"command: pause()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["open","close","pause"]}}},"additionalProperties":false,"required":[],"capability":"windowShade","attribute":"supportedWindowShadeCommands","label":"attribute: supportedWindowShadeCommands.*"},"command":{"name":"setSupportedWindowShadeCommandsValue","label":"command: setSupportedWindowShadeCommandsValue(supportedWindowShadeCommands*)","type":"command","parameters":[{"name":"supportedWindowShadeCommands*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setShadeLevel","label":"command: setShadeLevel(shadeLevel*)","type":"command","parameters":[{"name":"shadeLevel*","type":"NUMBER"}]},"command":{"name":"setShadeLevel","arguments":[{"name":"shadeLevel","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"windowShadeLevel","label":"command: setShadeLevel(shadeLevel*)"},"type":"hubitatTrigger"}]}
status: {"components":{"main":{"contactSensor":{"contact":{"value":"closed","timestamp":"2022-12-05T14:46:32.409Z"}},"windowShadeLevel":{"shadeLevel":{"value":50,"unit":"%","timestamp":"2022-12-05T16:09:20.462Z"}},"windowShade":{"supportedWindowShadeCommands":{"value":["open","close"],"timestamp":"2022-12-05T14:46:32.347Z"},"windowShade":{"value":"partially open","timestamp":"2022-12-05T16:09:20.461Z"}}}}}"""
}
