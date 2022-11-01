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
    definition(name: "Replica Power Outlet", namespace: "hubitat", author: "bloodtick", , importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaPowerOutlet.groovy")
	{
		capability "Actuator"
		capability "Switch"
		capability "PowerMeter"
		capability "VoltageMeasurement"
        capability "Refresh"
        
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setPower", [[name: "number*", type: "NUMBER", description: "Power level in Watts"]]
        command "setVoltage", [[name: "number*", type: "NUMBER", description: "Voltage level in Volts"]]
        command "setFrequency", [[name: "number*", type: "NUMBER", description: "Frequency in Hz"]]
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

def on() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
}

def off() {
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
}

def setPower(watt) {    
    sendEvent(name: "power", value: "$watt", unit: "W", descriptionText: "${device.displayName} power is $watt W")
}

def setVoltage(voltage) {    
    sendEvent(name: "voltage", value: "$voltage", unit: "V", descriptionText: "${device.displayName} voltage is $voltage V")
}

def setFrequency(voltage) {    
    sendEvent(name: "frequency", value: "$frequency", unit: "Hz", descriptionText: "${device.displayName} frequency is $frequency Hz")
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
