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
    definition(name: "Replica Envisalink", namespace: "hubitat", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaEnvisalink.groovy",)
	{
		capability "Actuator"
        capability "Alarm"
        capability "Refresh"
        
        attribute "switch", "string"
        attribute "selection", "string"
        attribute "securitySystem", "enum", ["armedAway", "armedStay", "disarmed" ]
        attribute "partitionCommand", "string"
        attribute "partitionStatus", "string"
        attribute "ledStatus", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setSwitch", [[name: "Set Switch*", type: "STRING", description: "Set switch value"]]
        command "setSelection", [[name: "Set Selection*", type: "STRING", description: "Set selection value"]]
        command "armedAway"
        command "armedStay"
        command "disarmed"
        command "setPartitionCommand", [[name: "Set Partition Command*", type: "STRING", description: "Set partition command value"]]
        command "setPartStatus", [[name: "Set Partition Status*", type: "STRING", description: "Set partition status value"]]
        command "setLedStatus", [[name: "Set LED Status*", type: "STRING", description: "Set LED status value"]]
        command "setAlarm", [[name: "Set Alarm*", type: "STRING", description: "Set Alarm value"]]
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

def off() {
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

def offline() {
    sendEvent(name: "healthStatus", value: "offline", descriptionText: "${device.displayName} healthStatus set to offline")
}

def online() {
    sendEvent(name: "healthStatus", value: "online", descriptionText: "${device.displayName} healthStatus set to online")
}

void refresh() {
    parent?.componentRefresh(this.device)
}
