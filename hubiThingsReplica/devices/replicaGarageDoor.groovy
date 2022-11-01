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
    definition(name: "Replica Garage Door", namespace: "hubitat", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaGarageDoor.groovy",)
	{
	    capability "Actuator"
        capability "Alarm"
        capability "Battery"
        capability "ContactSensor"
        capability "DoorControl"
        capability "GarageDoorControl"       
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setAlarmOff"
        command "setBattery", [[name: "number*", type: "NUMBER", description: "Battery level in %"]]
        command "setContactClosed"
        command "setContactOpen"
        command "setDoorClosed"
        command "setDoorClosing"
        command "setDoorOpen"
        command "setDoorOpening"
        command "setDoorUnknown"
        command "setSwitchOff"
        command "offline"
        command "online"
    }
}

def installed() {
    log.info "${device.displayName} installed"
	initialize()
}

def updated() {
    log.info "${device.displayName} updated"
	initialize()
}

def initialize() {
}

def parse(String description) {
    log.info "${device.displayName} parse"
}

def both() {
    sendEvent(name: "alarm", value: "both", descriptionText: "${device.displayName} alarm set to both")
}

def setAlarmOff() {
    sendEvent(name: "alarm", value: "off", descriptionText: "${device.displayName} alarm set to off")
}

def siren() {
    sendEvent(name: "alarm", value: "siren", descriptionText: "${device.displayName} alarm set to siren")
}

def strobe() {
    sendEvent(name: "alarm", value: "strobe", descriptionText: "${device.displayName} alarm set to strobe")
}

def setBattery(value) {    
    sendEvent(name: "battery", value: "$value", unit: "%", descriptionText: "${device.displayName} battery is ${value}%")
}

def setContactClosed() {
    sendEvent(name: "contact", value: "closed", descriptionText: "${device.displayName} contact set to closed")
}

def setContactOpen() {
    sendEvent(name: "contact", value: "open", descriptionText: "${device.displayName} contact set to open")
}

def close() {
    setDoorClosed()
}

def open() {
    setDoorOpen()    
}

def setDoorClosed() {
    sendEvent(name: "door", value: "closed", descriptionText: "${device.displayName} door set to closed")
}

def setDoorClosing() {
    sendEvent(name: "door", value: "closing", descriptionText: "${device.displayName} door set to closing")
}

def setDoorOpen() {
    sendEvent(name: "door", value: "open", descriptionText: "${device.displayName} door set to open")
}

def setDoorOpening() {
    sendEvent(name: "door", value: "opening", descriptionText: "${device.displayName} door set to opening")
}

def setDoorUnknown() {
    sendEvent(name: "door", value: "unknown", descriptionText: "${device.displayName} door set to unknown")
}

def setSwitchOff() {
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch set to off")
}

def off() {
    setAlarmOff()
    setSwitchOff()    
}

def on() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch set to on")
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
