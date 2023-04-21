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
@SuppressWarnings('unused')
public static String version() {return "1.3.1"}

metadata 
{
    definition(name: "Replica Multipurpose Sensor", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaMultipurposeSensor.groovy")
    {
        capability "AccelerationSensor"
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "ContactSensor"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "ThreeAxis"
        
        attribute "vibration", "enum", ["active", "inactive"] // smartthings hack. no documentation or event.
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "inactive"
        command "active"
        command "close"
        command "open"       
        command "setTemperature", [[name: "temperature*", type: "NUMBER", description: "Set Temperature in local scale °C or °F"]]
        command "setBattery", [[name: "battery*", type: "NUMBER", description: "Set Battery level %"]]
        command "setThreeAxis", [[name: "threeAxis*", type: "JSON_OBJECT", description: """Set three axis as '[ #, #, # ]' or '{ "x":#, "y":#, "z":# }' where # is [-10000,10000]"]"""]]
    }
    preferences {
        input(name:"deviceThreeAxisOptions", type: "enum", title: "Three Axis reporting options:", options: [0:"Disabled", 1:"Hubitat Format {x=1, y=2, z=3}", 2:"SmartThings Format [1, 2, 3]"], defaultValue: "0")
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
    }
}

def installed() {
	initialize()
}

def updated() {
	initialize()
    if(deviceThreeAxisOptions=="0") sendEvent(name: "threeAxis", value: [])
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
    refresh()
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([ "setAccelerationValue":[[name:"acceleration*",type:"ENUM"]], "setAccelerationActive":[], "setAccelerationInactive":[], "setBatteryValue":[[name:"battery*",type:"NUMBER"]], 
              "setContactSensorValue":[[name:"contact*",type:"ENUM"]], "setContactSensorClosed":[], "setContactSensorOpen":[], "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], 
              "setThreeAxisValue":[[name:"threeAxis*",type:"VECTOR3"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setAccelerationValue(value) {
    String descriptionText = "${device.displayName} acceleration is $value"
    sendEvent(name: "acceleration", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAccelerationActive() {
    setAccelerationValue("active")
}

def setAccelerationInactive() {
    setAccelerationValue("inactive")    
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setContactSensorValue(value) {
    String descriptionText = "${device.displayName} contact is $value"
    sendEvent(name: "contact", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setContactSensorClosed() {
    setContactSensorValue("closed")
}

def setContactSensorOpen() {
    setContactSensorValue("open")    
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setThreeAxisValue(value) {
    if( deviceThreeAxisOptions!="0" ) {
        String descriptionText = "${device.displayName} 3-axis is x:${value[0]}, y:${value[1]}, z:${value[2]}"
        value = updateAxisValue(value, deviceThreeAxisOptions)
        sendEvent(name: "threeAxis", value: value, descriptionText: descriptionText)
    }
    vibrationActive()
}

def updateAxisValue(axisValue, axisFormat) {    
    if(!(axisValue in List)) {
        logDebug "${device.displayName} Hubitat $axisFormat $axisValue"
        if(axisFormat=="2") return [axisValue?.x?:0,axisValue?.y?:0,axisValue?.z?:0] // ST Format wanted
    } else {
        logDebug "${device.displayName} SmartThings $axisFormat $axisValue"
        if(axisFormat=="1") return [ x:axisValue[0],y:axisValue[1],z:axisValue[2] ] // HE Format wanted
    }
    return axisFormat=="0"?null:axisValue
}

def vibrationActive() {
    sendEvent(name: "vibration", value: "active", descriptionText: "${device.displayName} vibration is active")
    runIn(5,'vibrationInactive') 
}
          
def vibrationInactive() {
    sendEvent(name: "vibration", value: "inactive", descriptionText: "${device.displayName} vibration is inactive")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "inactive":[] , "active":[], "close":[] , "open":[], "setTemperature":[[name:"temperature*",type:"NUMBER"]], "setBattery":[[name:"battery*",type:"NUMBER"]], "setThreeAxis":[[name:"threeAxis*",type:"JSON_OBJECT"]], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def inactive() {
    sendCommand("inactive")    
}

def active() {
    sendCommand("active")    
}

def close() {
    sendCommand("close")
}

def open() {
    sendCommand("open")    
}
             
def setTemperature(temperature) {
    sendCommand("setTemperature", temperature, getTemperatureScale())    
}

def setBattery(battery) {
    sendCommand("setBattery", battery, "%")    
}

def setThreeAxis(threeAxis) {
    try {
        threeAxis = new groovy.json.JsonSlurper().parseText(threeAxis.toLowerCase())
        sendCommand("setThreeAxis", updateAxisValue(threeAxis,"2"), "mG")
    } catch (e) {
        logWarn """${device.displayName} expected three axis as '[ #, #, # ]' or '{ "x":#, "y":#, "z":# }' where # is [-10000,10000]"]"""
    }
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"accelerationSensor","attribute":"acceleration","label":"attribute: acceleration.*"},"command":{"name":"setAccelerationValue","label":"command: setAccelerationValue(acceleration*)","type":"command","parameters":[{"name":"acceleration*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"command":{"name":"setContactSensorValue","label":"command: setContactSensorValue(contact*)","type":"command","parameters":[{"name":"contact*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"integer","minimum":-10000,"maximum":10000},"minItems":3,"maxItems":3},"unit":{"type":"string","enum":["mG"],"default":"mG"}},"additionalProperties":false,"required":["value"],"capability":"threeAxis","attribute":"threeAxis","label":"attribute: threeAxis.*"},"command":{"name":"setThreeAxisValue","label":"command: setThreeAxisValue(threeAxis*)","type":"command","parameters":[{"name":"threeAxis*","type":"VECTOR3"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"active","label":"command: active()","type":"command"},"command":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string","enum":["active","inactive"]}},"additionalProperties":false,"required":["value"],"capability":"accelerationSensor","attribute":"acceleration","label":"attribute: acceleration.active","value":"active","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"inactive","label":"command: inactive()","type":"command"},"command":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string","enum":["active","inactive"]}},"additionalProperties":false,"required":["value"],"capability":"accelerationSensor","attribute":"acceleration","label":"attribute: acceleration.inactive","value":"inactive","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"open","label":"command: open()","type":"command"},"command":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string","enum":["closed","open"]}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.open","value":"open","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"close","label":"command: close()","type":"command"},"command":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string","enum":["closed","open"]}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.closed","value":"closed","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"name":"setBattery","label":"command: setBattery(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"command":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"type":"hubitatTrigger"},{"trigger":{"name":"setTemperature","label":"command: setTemperature(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"command":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"type":"hubitatTrigger"},{"trigger":{"name":"setThreeAxis","label":"command: setThreeAxis(threeAxis*)","type":"command","parameters":[{"name":"threeAxis*","type":"JSON_OBJECT"}]},"command":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"integer","minimum":-10000,"maximum":10000},"minItems":3,"maxItems":3},"unit":{"type":"string","enum":["mG"],"default":"mG"}},"additionalProperties":false,"required":["value"],"capability":"threeAxis","attribute":"threeAxis","label":"attribute: threeAxis.*"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
