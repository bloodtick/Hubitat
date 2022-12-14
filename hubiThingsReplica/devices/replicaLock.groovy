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
public static String version() {return "1.2.1"}

metadata 
{
    definition(name: "Replica Lock", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaLock.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Lock"
        capability "LockCodes"
        capability "Refresh"
        
        attribute "scanCodes", "string" //capability "lockCodes" in SmartThings
        attribute "minCodeLength", "number" //capability "lockCodes" in SmartThings
        attribute "maxCodeLength", "number" //capability "lockCodes" in SmartThings
        attribute "codeReport", "number" //capability "lockCodes" in SmartThings
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "updateCodes", [[name: "codes*", type: "JSON_OBJECT", description: """Update to these codes as {"1":"Update","2":"Codes"}"""]] //capability "lockCodes" in SmartThings
        command "unlockWithTimeout" //capability "lockCodes" in SmartThings
        command "requestCode", [[name: "codeSlot*", type: "NUMBER", description: "Code Slot Number"]] //capability "lockCodes" in SmartThings
        command "nameSlot", [[name: "codeSlot*", type: "NUMBER", description: "Code Slot Number"],[name: "codeName*", type: "STRING", description: "Name of this Slot"]] //capability "lockCodes" in SmartThings
        command "reloadAllCodes" //capability "lockCodes" in SmartThings
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
    return ([ "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setScanCodesValue":[[name:"scanCodes*",type:"NUMBER"]], "setMinCodeLengthValue":[[name:"minCodeLength*",type:"NUMBER"]], "setMaxCodeLengthValue":[[name:"maxCodeLength*",type:"NUMBER"]], 
              "setCodeReportValue":[[name:"codeReport*",type:"NUMBER"]], "setMaxCodesValue":[[name:"maxCodes*",type:"NUMBER"]], "setLockCodesValue":[[name:"lockCodes*",type:"JSON_OBJECT"]], "setCodeLengthValue":[[name:"codeLength*",type:"NUMBER"]], 
              "setCodeChangedValue":[[name:"codeChanged*",type:"ENUM"]], "setCodeChangedAdded":[], "setCodeChangedChanged":[], "setCodeChangedDeleted":[], "setCodeChangedFailed":[], "setLockValue":[[name:"lock*",type:"ENUM"]], "setLockLocked":[], 
              "setLockUnlockedWithTimeout":[], "setLockLocked":[], "setLockUnknown":[], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    log.info descriptionText
}

def setScanCodesValue(value) {
    String descriptionText = "${device.displayName} scan codes are $value"
    sendEvent(name: "scanCodes", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setMinCodeLengthValue(value) {
    String descriptionText = "${device.displayName} min code length is $value"
    sendEvent(name: "minCodeLength", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setMaxCodeLengthValue(value) {
    String descriptionText = "${device.displayName} max code length is $value"
    sendEvent(name: "maxCodeLength", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setCodeReportValue(value) {
    String descriptionText = "${device.displayName} code report is $value"
    sendEvent(name: "codeReport", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setMaxCodesValue(value) {
    String descriptionText = "${device.displayName} max code is $value"
    sendEvent(name: "maxCodes", value: value, descriptionText: descriptionText)
    log.info descriptionText
}
            
def setLockCodesValue(value) {
    String descriptionText = "${device.displayName} locked codes are $value"
    sendEvent(name: "lockCodes", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setCodeLengthValue(value) {
    String descriptionText = "${device.displayName} code length is $value"
    sendEvent(name: "codeLength", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setCodeChangedValue(value) {
    String descriptionText = "${device.displayName} code is $value"
    sendEvent(name: "codeChanged", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setCodeChangedAdded() {
    setCodeChangedValue("added")
}

def setCodeChangedChanged() {
    setCodeChangedValue("changed")    
}

def setCodeChangedDeleted() {
    setCodeChangedValue("deleted")    
}

def setCodeChangedFailed() {
    setCodeChangedValue("failed")    
}

def setLockValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "lock", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setLockLocked() {
    setLockValue("locked")
}

def setLockUnlockedWithTimeout() {
    setLockValue("unlocked with timeout")    
}

def setLockUnlocked() {
    setLockValue("unlocked")    
}

def setLockUnknown() {
    setLockValue("unknown")    
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "updateCodes":[[name:"codes*",type:"JSON_OBJECT"]], "unlockWithTimeout":[], "setCodeLength":[[name:"length*",type:"NUMBER"]], "requestCode":[[name:"codeSlot*",type:"NUMBER"]], "reloadAllCodes":[], "nameSlot":[[name:"codeSlot*",type:"NUMBER"],[name:"codeName*",type:"STRING",data:"codeName"]], 
              "setCodeLength":[[name:"length*",type:"NUMBER"]], "setCode":[[name:"codeSlot*",type:"NUMBER"],[name:"codePIN*",type:"STRING",data:"codePIN"],[name:"codeName",type:"STRING",data:"codeName"]], "getCodes":[], "deleteCode":[[name:"codeSlot*",type:"NUMBER"]], "lock":[], "unlock":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) { 
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

def updateCodes(codes) {
    sendCommand("updateCodes", codes)    
}

def unlockWithTimeout() {
    sendCommand("unlockWithTimeout")
}

def requestCode(codeSlot) {
    sendCommand("requestCode", codeSlot)    
}

def reloadAllCodes() { 
    sendCommand("reloadAllCodes")
}

def nameSlot(codeSlot, codeName) {
    sendCommand("nameSlot", codeSlot, null, [codeName:codeName])
}

def setCodeLength(length) {
    sendCommand("setCodeLength", length)    
} 

def setCode(codeSlot, codePIN, codeName="codeName") {
    sendCommand("setCode", codeSlot, null, [codePIN:codePIN, codeName:codeName])    
}    

def getCodes() { // Hubitat only
    sendCommand("getCodes")
}    

def deleteCode(codeSlot) {
    sendCommand("deleteCode", codeSlot)    
}    

def lock() { 
    sendCommand("lock")
}

def unlock() {
    sendCommand("unlock")
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"deleteCode","label":"command: deleteCode(codeSlot*)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"}]},"command":{"name":"deleteCode","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"lockCodes","label":"command: deleteCode(codeSlot*)"},"type":"hubitatTrigger"},{"trigger":{"name":"lock","label":"command: lock()","type":"command"},"command":{"name":"lock","type":"command","capability":"lockCodes","label":"command: lock()"},"type":"hubitatTrigger"},{"trigger":{"name":"nameSlot","label":"command: nameSlot(codeSlot*, codeName*)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"},{"name":"codeName*","type":"STRING","data":"codeName"}]},"command":{"name":"nameSlot","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}},{"name":"codeName","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],\
"type":"command","capability":"lockCodes","label":"command: nameSlot(codeSlot*, codeName*)"},"type":"hubitatTrigger"},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"name":"reloadAllCodes","label":"command: reloadAllCodes()","type":"command"},"command":{"name":"reloadAllCodes","type":"command","capability":"lockCodes","label":"command: reloadAllCodes()"},"type":"hubitatTrigger"},{"trigger":{"name":"requestCode","label":"command: requestCode(codeSlot*)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"}]},"command":{"name":"requestCode","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"lockCodes","label":"command: requestCode(codeSlot*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setCode","label":"command: setCode(codeSlot*, codePIN*, codeName)","type":"command",\
"parameters":[{"name":"codeSlot*","type":"NUMBER"},{"name":"codePIN*","type":"STRING","data":"codePIN"},{"name":"codeName","type":"STRING","data":"codeName"}]},"command":{"name":"setCode","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}},{"name":"codePIN","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}},{"name":"codeName","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],"type":"command","capability":"lockCodes","label":"command: setCode(codeSlot*, codePIN*, codeName*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setCodeLength","label":"command: setCodeLength(length*)","type":"command","parameters":[{"name":"length*","type":"NUMBER"}]},"command":{"name":"setCodeLength","arguments":[{"name":"length","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"lockCodes","label":"command: setCodeLength(length*)"},"type":"hubitatTrigger"},{"trigger":{"name":"unlock","label":"command: unlock()",\
"type":"command"},"command":{"name":"unlock","type":"command","capability":"lockCodes","label":"command: unlock()"},"type":"hubitatTrigger"},{"trigger":{"name":"unlockWithTimeout","label":"command: unlockWithTimeout()","type":"command"},"command":{"name":"unlockWithTimeout","type":"command","capability":"lockCodes","label":"command: unlockWithTimeout()"},"type":"hubitatTrigger"},{"trigger":{"name":"updateCodes","label":"command: updateCodes(codes*)","type":"command","parameters":[{"name":"codes*","type":"JSON_OBJECT"}]},"command":{"name":"updateCodes","arguments":[{"name":"codes","optional":false,"schema":{"title":"JsonObject","type":"object"}}],"type":"command","capability":"lockCodes","label":"command: updateCodes(codes*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)",\
"type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"codeName":{"type":"string"}}}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"codeChanged","label":"attribute: codeChanged.*"},"command":{"name":"setCodeChangedValue","label":"command: setCodeChangedValue(codeChanged*)","type":"command","parameters":[{"name":"codeChanged*","type":"ENUM"}]},\
"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"codeLength","label":"attribute: codeLength.*"},"command":{"name":"setCodeLengthValue","label":"command: setCodeLengthValue(codeLength*)","type":"command","parameters":[{"name":"codeLength*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"lockCodes","attribute":"codeReport","label":"attribute: codeReport.*"},"command":{"name":"setCodeReportValue","label":"command: setCodeReportValue(codeReport*)","type":"command","parameters":[{"name":"codeReport*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"LockState","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],\
"properties":{"method":{"enum":["manual","keypad","auto","command","rfid","fingerprint","bluetooth"],"type":"string"},"codeName":{"type":"string"},"codeId":{"type":"string"},"timeout":{"pattern":"removed","title":"Iso8601Date","type":"string"}}}},"additionalProperties":false,"required":["value"],"capability":"lockCodes","attribute":"lock","label":"attribute: lock.*"},"command":{"name":"setLockValue","label":"command: setLockValue(lock*)","type":"command","parameters":[{"name":"lock*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"lockCodes","label":"attribute: lockCodes.*"},"command":{"name":"setLockCodesValue",\
"label":"command: setLockCodesValue(lockCodes*)","type":"command","parameters":[{"name":"lockCodes*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"maxCodeLength","label":"attribute: maxCodeLength.*"},"command":{"name":"setMaxCodeLengthValue","label":"command: setMaxCodeLengthValue(maxCodeLength*)","type":"command","parameters":[{"name":"maxCodeLength*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"maxCodes","label":"attribute: maxCodes.*"},"command":{"name":"setMaxCodesValue","label":"command: setMaxCodesValue(maxCodes*)","type":"command","parameters":[{"name":"maxCodes*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger",\
"type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"minCodeLength","label":"attribute: minCodeLength.*"},"command":{"name":"setMinCodeLengthValue","label":"command: setMinCodeLengthValue(minCodeLength*)","type":"command","parameters":[{"name":"minCodeLength*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"scanCodes","label":"attribute: scanCodes.*"},"command":{"name":"setScanCodesValue","label":"command: setScanCodesValue(scanCodes*)","type":"command","parameters":[{"name":"scanCodes*","type":"NUMBER"}]},"type":"smartTrigger"}]}"""
}
