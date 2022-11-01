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
metadata 
{
    definition(name: "Replica Window Shade", namespace: "hubitat", author: "bloodtick", , importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaWindowShade.groovy")
	{
        capability "Actuator"
        capability "EnergyMeter"
        capability "PowerMeter"
        capability "Refresh"
        capability "WindowShade"
        //capability "windowShadeLevel" // doesn't exist in Hubitat
        
        attribute "shadeLevel", "number"
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setEnergy", [[name: "number*", type: "NUMBER", description: "Energy level in kWh"]]
        command "setPower", [[name: "number*", type: "NUMBER", description: "Power level in W"]]
        command "setShadeLevel", [[name: "number*", type: "NUMBER", description: "Shade level in %"]]
        command "setWindowShadeOpening"
        command "setWindowShadePartiallyOpen"
        command "setWindowClosed"
        command "setWindowClosing"
        command "setWindowUnknown"
        command "offline"
        command "online"
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
    sendEvent(name: "supportedWindowShadeCommands", value: "close", descriptionText: "${device.displayName} supported window shade command is close")
}

def open() {
    sendEvent(name: "supportedWindowShadeCommands", value: "open", descriptionText: "${device.displayName} supported window shade command is open")
}

def pause() {
    sendEvent(name: "supportedWindowShadeCommands", value: "pause", descriptionText: "${device.displayName} supported window shade command is pause")
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

def setWindowShadeOpening() {
    sendEvent(name: "windowShade", value: "opening", descriptionText: "${device.displayName} window shade is opening")
}

def setWindowShadePartiallyOpen() {
    sendEvent(name: "windowShade", value: "partially open", descriptionText: "${device.displayName} window shade is partially open")
}

def setWindowClosed() {
    sendEvent(name: "windowShade", value: "closed", descriptionText: "${device.displayName} window shade is closed")
}

def setWindowClosing() {
    sendEvent(name: "windowShade", value: "closing", descriptionText: "${device.displayName} window shade is closing")
}

def setWindowUnknown() {
    sendEvent(name: "windowShade", value: "unknown", descriptionText: "${device.displayName} window shade is unknown")
}

def offline() {
    sendEvent(name: "healthStatus", value: "offline", descriptionText: "${device.displayName} healthStatus set to offline")
}

def online() {
    sendEvent(name: "healthStatus", value: "online", descriptionText: "${device.displayName} healthStatus set to online")
}

void refresh() {
    parent?.componentRefresh(this.device)
}
