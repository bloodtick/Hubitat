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
@SuppressWarnings('unused')
public static String version() {return "1.2.0"}

metadata 
{
    definition(name: "Replica Motion Sensor", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaMotionSensor.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Battery"
        capability "MotionSensor"
        capability "Refresh"
        capability "TemperatureMeasurement"

        attribute "healthStatus", "enum", ["offline", "online"]

        command "inactive"
        command "active"
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
    return ([ "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setMotionValue":[[name:"motion*",type:"ENUM"]], "setMotionActive":[], "setMotionInactive":[],             
              "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    log.info descriptionText
}

def setMotionValue(value) {
    String descriptionText = "${device.displayName} motion is $value"
    sendEvent(name: "motion", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setMotionActive() {
    setMotionValue("active")
}

def setMotionInactive() {
    setMotionValue("inactive")    
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    log.info descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "inactive":[] , "active":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

def inactive() {
    sendCommand("inactive")    
}

def active() {
    sendCommand("active")    
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"OpenableState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"windowShade","attribute":"windowShade","label":"attribute: windowShade.*"},"command":{"name":"setWindowShadeValue","label":"command: setWindowShadeValue(windowShade*)","type":"command","parameters":[{"name":"windowShade*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"windowShadeLevel","attribute":"shadeLevel","label":"attribute: shadeLevel.*"},"command":{"name":"setShadeLevelValue","label":"command: setShadeLevelValue(shadeLevel*)","type":"command","parameters":[{"name":"shadeLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"close","label":"command: close()","type":"command"},"command":{"name":"close","type":"command","capability":"windowShade","label":"command: close()"},"type":"hubitatTrigger"},{"trigger":{"name":"open","label":"command: open()","type":"command"},"command":{"name":"open","type":"command","capability":"windowShade","label":"command: open()"},"type":"hubitatTrigger"},{"trigger":{"name":"pause","label":"command: pause()","type":"command"},"command":{"name":"pause","type":"command","capability":"windowShade","label":"command: pause()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["open","close","pause"]}}},"additionalProperties":false,"required":[],"capability":"windowShade","attribute":"supportedWindowShadeCommands","label":"attribute: supportedWindowShadeCommands.*"},"command":{"name":"setSupportedWindowShadeCommandsValue","label":"command: setSupportedWindowShadeCommandsValue(supportedWindowShadeCommands*)","type":"command","parameters":[{"name":"supportedWindowShadeCommands*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setShadeLevel","label":"command: setShadeLevel(shadeLevel*)","type":"command","parameters":[{"name":"shadeLevel*","type":"NUMBER"}]},"command":{"name":"setShadeLevel","arguments":[{"name":"shadeLevel","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"windowShadeLevel","label":"command: setShadeLevel(shadeLevel*)"},"type":"hubitatTrigger"}]}
status: {"components":{"main":{"contactSensor":{"contact":{"value":"closed","timestamp":"2022-12-05T14:46:32.409Z"}},"windowShadeLevel":{"shadeLevel":{"value":50,"unit":"%","timestamp":"2022-12-05T16:09:20.462Z"}},"windowShade":{"supportedWindowShadeCommands":{"value":["open","close"],"timestamp":"2022-12-05T14:46:32.347Z"},"windowShade":{"value":"partially open","timestamp":"2022-12-05T16:09:20.461Z"}}}}}"""
}
