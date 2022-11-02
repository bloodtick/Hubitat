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
metadata 
{
    definition(name: "Replica Envisalink", namespace: "hubitat", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaEnvisalink.groovy")
    {
        capability "Actuator"
        capability "Alarm"
        capability "Refresh"
        capability "Switch"
        
        attribute "selection", "string"
        attribute "securitySystem", "enum", ["armedAway", "armedStay", "disarmed" ]
        attribute "partitionCommand", "string"
        attribute "partitionStatus", "string"
        attribute "ledStatus", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setAlarmOff"
        command "setSwitchOff"
        command "setSwitch", [[name: "string*", type: "STRING", description: "Set switch value"]]
        command "setSelection", [[name: "string*", type: "STRING", description: "Set selection value"]]
        command "armedAway"
        command "armedStay"
        command "disarmed"
        command "setPartitionCommand", [[name: "string*", type: "STRING", description: "Set partition command value"]]
        command "setPartStatus", [[name: "string*", type: "STRING", description: "Set partition status value"]]
        command "setLedStatus", [[name: "string*", type: "STRING", description: "Set LED status value"]]
        command "setAlarm", [[name: "string*", type: "STRING", description: "Set Alarm value"]]
        command "setHealthStatus", [[name: "healthStatus*", type: "ENUM", description: "Any Supported healthStatus Commands", constraints: ["offline","online"]]]
    }
    preferences {
        input(name: "deviceOffCommand", type: "enum", title: "<b>Action from Off command:</b>", required: true, options: [0:"Both Switch and Alarm turn off",1:"Only Switch turns off",2:"Only Alarm turns off"], defaultValue:2)        
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

def setSwitch(value) {    
    sendEvent(name: "switch", value: "$value", descriptionText: "${device.displayName} switch is $value")
}

def setSelection(value) {    
    sendEvent(name: "selection", value: "$value", descriptionText: "${device.displayName} selection is $value")
}

def armedAway() {
    sendEvent(name: "securitySystem", value: "armedAway", descriptionText: "${device.displayName} securitySystem set to armedAway")
}

def armedStay() {
    sendEvent(name: "securitySystem", value: "armedStay", descriptionText: "${device.displayName} securitySystem set to armedStay")
}

def disarmed() {
    sendEvent(name: "securitySystem", value: "disarmed", descriptionText: "${device.displayName} securitySystem set to disarmed")
}

def setPartitionCommand(value) {    
    sendEvent(name: "partitionCommand", value: "$value", descriptionText: "${device.displayName} partitionCommand is $value")
}

def setPartStatus(value) {    
    sendEvent(name: "partitionStatus", value: "$value", descriptionText: "${device.displayName} partitionStatus is $value")
}

def setLedStatus(value) {    
    sendEvent(name: "ledStatus", value: "$value", descriptionText: "${device.displayName} ledStatus is $value")
}

def setAlarm(value) {    
    sendEvent(name: "alarm", value: "$value", descriptionText: "${device.displayName} alarm is $value")
}

def setSwitchOff() {
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch set to off")
}

def off() { 
    if(deviceOffCommand=='0' || deviceOffCommand=='1') { setSwitchOff() }
    if(deviceOffCommand=='0' || deviceOffCommand=='2') { setAlarmOff() }
}

def on() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch set to on")
}

def setHealthStatus(value) {    
    sendEvent(name: "healthStatus", value: "$value", descriptionText: "${device.displayName} healthStatus set to $value")
}

void refresh() {
    parent?.componentRefresh(this.device)
}
