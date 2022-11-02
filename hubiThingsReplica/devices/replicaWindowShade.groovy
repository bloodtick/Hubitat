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
*  version 1.0.0
*/
import groovy.json.*
    
metadata 
{
    definition(name: "Replica Window Shade", namespace: "hubitat", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaWindowShade.groovy")
	{
        capability "Actuator"
        capability "EnergyMeter"
        capability "PowerMeter"
        capability "Refresh"
        capability "WindowShade"
        //capability "windowShadeLevel" // doesn't exist in Hubitat
        
        attribute "supportedWindowShadeCommands", "JSON_OBJECT"
        attribute "shadeLevel", "number"        
        attribute "healthStatus", "enum", ["offline", "online"]
		
        command "pause" // doesn't exist in Hubitat capability "windowShade"
        command "setEnergy", [[name: "number*", type: "NUMBER", description: "Energy level in kWh"]]
        command "setPower", [[name: "number*", type: "NUMBER", description: "Power level in W"]]
        command "setShadeLevel", [[name: "number*", type: "NUMBER", description: "Shade level in %"]]
        command "setSupportedWindowShadeCommands", [[name: "JSON_OBJECT*", type: "JSON_OBJECT", description: "Define windowShade Supported Commands"]]
        command "setWindowShade", [[name: "windowShade*", type: "STRING", description: "Any Supported windowShade Commands"]]
        command "setWindowShadeOpening"
        command "setWindowShadePartiallyOpen"
        command "setWindowShadeClosed"
        command "setWindowShadeOpen"
        command "setWindowShadeClosing"
        command "setWindowShadeUnknown"
        command "setHealthStatus", [[name: "healthStatus*", type: "ENUM", description: "Any Supported healthStatus Commands", constraints: ["offline","online"]]]
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

def setEnergy(value) {    
    sendEvent(name: "energy", value: "$value", unit: "kWh", descriptionText: "${device.displayName} energy is $value kWh")
}

def setPower(value) {    
    sendEvent(name: "power", value: "$value", unit: "W", descriptionText: "${device.displayName} power is $value W")
}

def setShadeLevel(value) {    
    sendEvent(name: "shadeLevel", value: "$value", unit: "%", descriptionText: "${device.displayName} shade level is ${value}%")
}

def close() {
    setWindowShade("close")
}

def open() {
    setWindowShade("open")
}

def pause() {
    setWindowShade("pause")
}

def setPosition(value) {    
    sendEvent(name: "position", value: "$value", unit: "%", descriptionText: "${device.displayName} position is ${value}%")
}

def startPositionChange(value) {    
    if(value=="close") close()
    if(value=="open") open()
}

def stopPositionChange() {
    pause()
}

def setSupportedWindowShadeCommands(value) {
    try {
        List list = new JsonSlurper().parseText(value)
        sendEvent(name: "supportedWindowShadeCommands", value: list, descriptionText: "${device.displayName} supported WindowShade Commands are ${list}")
    }
    catch(e) {
        log.info """${device.displayName} recieved $value which is not valid JSON use ["close","pause","open"]"""
    }
}

def setWindowShade(value) {
    List list = ((device.currentValue("supportedWindowShadeCommands"))?.tokenize(',[]')?.collect{ it?.trim() })
    if( list?.find{ it == value } != null ) {
        sendEvent(name: "windowShade", value: "$value", descriptionText: "${device.displayName} window shade is $value")
    }
    else {
        log.info "${device.displayName} does not support '$value' window shade command"
    }
}

def setWindowShadeOpening() {
    setWindowShade("opening")
}

def setWindowShadePartiallyOpen() {
    setWindowShade("partially open")
}

def setWindowShadeClosed() {
    setWindowShade("closed")
}

def setWindowShadeOpen() {
    setWindowShade("open")
}

def setWindowShadeClosing() {
    setWindowShade("closing")
}

def setWindowShadeUnknown() {
    setWindowShade("unknown")
}

def setHealthStatus(value) {    
    sendEvent(name: "healthStatus", value: "$value", descriptionText: "${device.displayName} healthStatus set to $value")
}

void refresh() {
    parent?.componentRefresh(this.device)
}
