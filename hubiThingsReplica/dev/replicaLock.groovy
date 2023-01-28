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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static final String  codeUnknown="local"
@Field static final Boolean testEnable=true

metadata 
{
    definition(name: "Replica Lock", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaLock.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "ContactSensor"
        capability "Lock"
        capability "LockCodes"
        capability "Refresh"
        capability "TamperAlert"
        
        attribute "scanCodes", "string" //capability "lockCodes" in SmartThings
        attribute "minCodeLength", "number" //capability "lockCodes" in SmartThings
        attribute "maxCodeLength", "number" //capability "lockCodes" in SmartThings
        attribute "codeReport", "number" //capability "lockCodes" in SmartThings
        attribute "method", "enum", ["manual","keypad","auto","command","rfid","fingerprint","bluetooth"]
        attribute "healthStatus", "enum", ["offline", "online"]        
        //command "updateCodes", [[name: "codes*", type: "JSON_OBJECT", description: """Update to these codes as {"1":"John Doe","2":"Jane Doe"}"""]] //capability "lockCodes" in SmartThings
        command "unlockWithTimeout" //capability "lockCodes" in SmartThings
        command "requestCode", [[name: "codeSlot*", type: "NUMBER", description: "Code Slot Number"]] //capability "lockCodes" in SmartThings
        command "nameSlot", [[name: "codeSlot*", type: "NUMBER", description: "Code Slot Number"],[name: "codeName*", type: "STRING", description: "Name of this Slot"]] //capability "lockCodes" in SmartThings
        //command "reloadAllCodes" //capability "lockCodes" in SmartThings
        if(testEnable){
            command "setLockLocked"
            command "setLockUnlocked"
            command "testSetLockCodesValue1"
            command "testSetLockCodesValue2"
            command "testSetLockCodesValue3"
        }
    }
    preferences {
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
        input(name:"deviceDebugEnable", type: "bool", title: "Enable Debug logging:", defaultValue: false)
    }
}

def installed() {
	initialize()
    setMaxCodesValue(20)
    setCodeLengthValue(4)
    setLockLocked()
}

def updated() {
	initialize()    
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
    fixLockCodesV()
    if(!deviceDebugEnable) state.remove('codeChangedValues')
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([ "setBatteryValue":[[name:"battery*",type:"NUMBER"]], "setScanCodesValue":[[name:"scanCodes*",type:"NUMBER"]], "setMinCodeLengthValue":[[name:"minCodeLength*",type:"NUMBER"]], "setMaxCodeLengthValue":[[name:"maxCodeLength*",type:"NUMBER"]], 
              "setCodeReportValue":[[name:"codeReport*",type:"STRING"]], "setMaxCodesValue":[[name:"maxCodes*",type:"NUMBER"]], "setLockCodesValue":[[name:"lockCodes*",type:"STRING"]], "setCodeLengthValue":[[name:"codeLength*",type:"NUMBER"]], 
              "setCodeChangedValue":[[name:"codeChanged*",type:"ENUM"]], "setCodeChangedAdded":[], "setCodeChangedChanged":[], "setCodeChangedDeleted":[], "setCodeChangedFailed":[], "setLockValue":[[name:"lock*",type:"JSON_OBJECT"]], "setLockLocked":[], 
              "setLockUnlockedWithTimeout":[], "setLockUnlocked":[], "setLockUnknown":[], "setContactSensorValue":[[name:"contact*",type:"ENUM"]], "setContactSensorClosed":[], "setContactSensorOpen":[], "setTamperValue":[[name:"tamper*",type:"ENUM"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
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

def setScanCodesValue(value) {
    String descriptionText = "${device.displayName} scan codes are $value"
    sendEvent(name: "scanCodes", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMinCodeLengthValue(value) {
    String descriptionText = "${device.displayName} min code length is $value"
    sendEvent(name: "minCodeLength", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMaxCodeLengthValue(value) {
    String descriptionText = "${device.displayName} max code length is $value"
    sendEvent(name: "maxCodeLength", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setCodeReportValue(value) {
    String descriptionText = "${device.displayName} code report is $value"
    sendEvent(name: "codeReport", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMaxCodesValue(value) {
    String descriptionText = "${device.displayName} max code is $value"
    sendEvent(name: "maxCodes", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//https://community.smartthings.com/t/capability-lockcodes-update/17510            
def setLockCodesValue(value) {
    logDebug "${device.displayName} executing 'setLockCodesValue($value)'"
    if(!state?.metadata) state.metadata = [:]
    state.stLockCodes = value
    
    Map lockCodes = [:]
    Map stLockCodes = (new groovy.json.JsonSlurper().parseText(value))?.sort{ a, b -> a?.key?.toInteger() <=> b?.key?.toInteger() }
    Map metadata = state?.metadata?.clone() ?: [:]
    
    logDebug "stLockCodes: $stLockCodes"
    logDebug "metadata: $metadata"
    
    stLockCodes?.each{ key, val ->
        state.metadata[key] = [reason:metadata?."$key"?.reason?:"added", name:val, code:metadata?."$key"?.code?:codeUnknown]
        lockCodes[key] = [name:val, code:metadata?."$key"?.code?:codeUnknown] 
        metadata?.remove(key)
    }
    metadata?.each{ key, val ->
        logDebug "state remove $key"
        state?.metadata?."$key"?.reason = "deleted"
    }
    
    value = groovy.json.JsonOutput.toJson(lockCodes)    
    String descriptionText = "${device.displayName} locked codes are $value [digital]"
    sendEvent(name: "lockCodes", value: value, type:"digital", descriptionText: descriptionText)
    logInfo descriptionText
}

def setCodeLengthValue(value) {
    String descriptionText = "${device.displayName} code length is $value"
    sendEvent(name: "codeLength", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setCodeChangedValue(codeChanged) { // '5 renamed', '5 set',  '5 deleted', 'all deleted', '5 failed', "5 unset", "set", "changed", "deleted", "failed"
    if(deviceDebugEnable && !state?.codeChangedValues) state.codeChangedValues = []
    if(deviceDebugEnable && !state?.codeChangedValues.contains(codeChanged)) state.codeChangedValues << codeChanged
    
    String descriptionText = "${device.displayName} code change was $codeChanged [digital]"
    //don't send, these have nothing todo with what hubitat wants to see.
    //sendEvent(name: "codeChanged", value: codeChanged, type:"digital", descriptionText: descriptionText)
    state.codeChanged = codeChanged
    logInfo descriptionText
    
    String codeNumber = codeChanged.split(' ')[0] as String
    String change = codeChanged.split(' ')[1] as String
    logDebug "codeNumber:$codeNumber change:$change"
    Map metadata = state?.metadata
    logDebug "metadata.$codeNumber: ${metadata?."$codeNumber"}"
    String reason = metadata?."$codeNumber"?.reason
    Map data = [ ("$codeNumber" as String) : [ code: metadata?."$codeNumber"?.code, name: metadata?."$codeNumber"?.name ]]

    if(change=="set" || change=="renamed") {
        if(reason=="added" || reason=="changed") {
            updateCodeChanged(reason, data)
            state?.metadata?."$codeNumber"?.reason = change
        }
        else if(reason=="requestCode" || (device.currentValue("scanCodes")?.toLowerCase()=="scanning")) {
            //noop
            state?.metadata?."$codeNumber"?.reason = change
        }
        else {
            // someone else set the code & i don't know it            
            state?.metadata?."$codeNumber"?.code = codeUnknown
            data."$codeNumber".code = codeUnknown 
            updateCodeChanged("changed", data)
            setLockCodesValue(state.stLockCodes)
            logWarn "${device.displayName} was $change from another source"
        }        
    }
    if(change=="unset" || change=="deleted") {
        if(data) {
            updateCodeChanged('deleted', data)
            metadata?.remove(codeNumber)
        }
    }
    state.remove("stLockCodes")
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

def setLockValue(event) {
    String descriptionText = "${device.displayName} is $event"
    sendEvent(name: "lock", value: ((event instanceof String) ? event : event?.value), data: ((event instanceof String) ? [:] : event?.data), descriptionText: descriptionText)
    if(!(event instanceof String)) 
       sendEvent(name: "method", value: event?.data?.method?:"command")
    logInfo descriptionText    
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

def setTamperValue(value) {
    String descriptionText = "${device.displayName} tamper is $value"
    sendEvent(name: "tamper", value: value, descriptionText: descriptionText)
    logInfo descriptionText
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
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def updateCodes(codes) {
    logDebug "${device.displayName} executing 'updateCodes($codes)'"
    sendCommand("updateCodes", codes)    
}

def unlockWithTimeout() {
    logDebug "${device.displayName} executing 'unlockWithTimeout()'"
    sendCommand("unlockWithTimeout")
}

def requestCode(codeSlot) {
    logDebug "${device.displayName} executing 'requestCode($codeSlot)'"
    state?.metadata?."$codeSlot"?.reason = "requestCode"
    testCodeChanged("$codeSlot set")
    sendCommand("requestCode", codeSlot)    
}

def reloadAllCodes() {
    logDebug "${device.displayName} executing 'reloadAllCodes()'"
    sendCommand("reloadAllCodes")
}

def nameSlot(codeSlot, codeName) {
    logDebug "${device.displayName} executing 'nameSlot($codeSlot,$codeName)'"
    if(nameSlotV(codeSlot, codeName)) sendCommand("nameSlot", codeSlot, null, [codeName:codeName])
}

def setCodeLength(length) {
    logDebug "${device.displayName} executing 'setCodeLength($length)'"
    sendCommand("setCodeLength", length)    
} 

def setCode(codeSlot, codePIN, codeName=null) {
    logDebug "${device.displayName} executing 'setCode($codeSlot,$codePIN,$codeName)'"
    if (!codeName) codeName = "code #${codeSlot}"
    if(setCodeV(codeSlot, codePIN, codeName)) sendCommand("setCode", codeSlot, null, [codePIN:codePIN, codeName:codeName])    
}    

def getCodes() {
    logDebug "${device.displayName} executing 'getCodes()'"
    //sendCommand("getCodes") // Hubitat only. I think this is the ST call.
    reloadAllCodes()
}    

def deleteCode(codeSlot) {
    logDebug "${device.displayName} executing 'deleteCode($codeSlot)'"
    deleteCodeV(codeSlot)
    sendCommand("deleteCode", codeSlot)    
}    

def lock() { 
    sendCommand("lock")
}

def unlock() {
    sendCommand("unlock")
}

void refresh() {
    logDebug "${device.displayName} executing 'refresh()'"
    fixLockCodesV()
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"deleteCode","label":"command: deleteCode(codeSlot*)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"}]},"command":{"name":"deleteCode","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"lockCodes","label":"command: deleteCode(codeSlot*)"},"type":"hubitatTrigger"},{"trigger":{"name":"nameSlot","label":"command: nameSlot(codeSlot*, codeName*)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"},{"name":"codeName*","type":"STRING","data":"codeName"}]},"command":{"name":"nameSlot","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}},{"name":"codeName","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],"type":"command","capability":"lockCodes","label":"command: nameSlot(codeSlot*, codeName*)"},"type":"hubitatTrigger"},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"name":"reloadAllCodes","label":"command: reloadAllCodes()","type":"command"},"command":{"name":"reloadAllCodes","type":"command","capability":"lockCodes","label":"command: reloadAllCodes()"},"type":"hubitatTrigger"},{"trigger":{"name":"requestCode","label":"command: requestCode(codeSlot*)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"}]},"command":{"name":"requestCode","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"lockCodes","label":"command: requestCode(codeSlot*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setCode","label":"command: setCode(codeSlot*, codePIN*, codeName)","type":"command","parameters":[{"name":"codeSlot*","type":"NUMBER"},{"name":"codePIN*","type":"STRING","data":"codePIN"},{"name":"codeName","type":"STRING","data":"codeName"}]},"command":{"name":"setCode","arguments":[{"name":"codeSlot","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}},{"name":"codePIN","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}},{"name":"codeName","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],"type":"command","capability":"lockCodes","label":"command: setCode(codeSlot*, codePIN*, codeName*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setCodeLength","label":"command: setCodeLength(length*)","type":"command","parameters":[{"name":"length*","type":"NUMBER"}]},"command":{"name":"setCodeLength","arguments":[{"name":"length","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"lockCodes","label":"command: setCodeLength(length*)"},"type":"hubitatTrigger"},{"trigger":{"name":"unlockWithTimeout","label":"command: unlockWithTimeout()","type":"command"},"command":{"name":"unlockWithTimeout","type":"command","capability":"lockCodes","label":"command: unlockWithTimeout()"},"type":"hubitatTrigger"},{"trigger":{"name":"updateCodes","label":"command: updateCodes(codes*)","type":"command","parameters":[{"name":"codes*","type":"JSON_OBJECT"}]},"command":{"name":"updateCodes","arguments":[{"name":"codes","optional":false,"schema":{"title":"JsonObject","type":"object"}}],"type":"command","capability":"lockCodes","label":"command: updateCodes(codes*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"codeLength","label":"attribute: codeLength.*"},"command":{"name":"setCodeLengthValue","label":"command: setCodeLengthValue(codeLength*)","type":"command","parameters":[{"name":"codeLength*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"lockCodes","attribute":"codeReport","label":"attribute: codeReport.*"},"command":{"name":"setCodeReportValue","label":"command: setCodeReportValue(codeReport*)","type":"command","parameters":[{"name":"codeReport*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"maxCodeLength","label":"attribute: maxCodeLength.*"},"command":{"name":"setMaxCodeLengthValue","label":"command: setMaxCodeLengthValue(maxCodeLength*)","type":"command","parameters":[{"name":"maxCodeLength*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"maxCodes","label":"attribute: maxCodes.*"},"command":{"name":"setMaxCodesValue","label":"command: setMaxCodesValue(maxCodes*)","type":"command","parameters":[{"name":"maxCodes*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"minCodeLength","label":"attribute: minCodeLength.*"},"command":{"name":"setMinCodeLengthValue","label":"command: setMinCodeLengthValue(minCodeLength*)","type":"command","parameters":[{"name":"minCodeLength*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"scanCodes","label":"attribute: scanCodes.*"},"command":{"name":"setScanCodesValue","label":"command: setScanCodesValue(scanCodes*)","type":"command","parameters":[{"name":"scanCodes*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"unlock","label":"command: unlock()","type":"command"},"command":{"name":"unlock","type":"command","capability":"lock","label":"command: lock:unlock()"},"type":"hubitatTrigger"},{"trigger":{"name":"lock","label":"command: lock()","type":"command"},"command":{"name":"lock","type":"command","capability":"lock","label":"command: lock:lock()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"tamperAlert","attribute":"tamper","label":"attribute: tamper.*"},"command":{"name":"setTamperValue","label":"command: setTamperValue(tamper*)","type":"command","parameters":[{"name":"tamper*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"LockState","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"method":{"type":"string","enum":["manual","keypad","auto","command","rfid","fingerprint","bluetooth"]},"codeId":{"type":"string"},"codeName":{"type":"string"},"timeout":{"title":"Iso8601Date","type":"string","pattern":"removed"}}}},"additionalProperties":false,"required":["value"],"capability":"lock","attribute":"lock","label":"attribute: lock.*"},"command":{"name":"setLockValue","label":"command: setLockValue(lock*)","type":"command","parameters":[{"name":"lock*","type":"JSON_OBJECT"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"codeName":{"type":"string"}}}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"codeChanged","label":"attribute: codeChanged.*"},"command":{"name":"setCodeChangedValue","label":"command: setCodeChangedValue(codeChanged*)","type":"command","parameters":[{"name":"codeChanged*","type":"ENUM"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"additionalProperties":false,"required":[],"capability":"lockCodes","attribute":"lockCodes","label":"attribute: lockCodes.*"},"command":{"name":"setLockCodesValue","label":"command: setLockCodesValue(lockCodes*)","type":"command","parameters":[{"name":"lockCodes*","type":"STRING"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"command":{"name":"setContactSensorValue","label":"command: setContactSensorValue(contact*)","type":"command","parameters":[{"name":"contact*","type":"ENUM"}]},"type":"smartTrigger"}]}"""
}

void fixLockCodesV() { //needed to update v1.3.0. Remove someday.    
    String lockCodes = device.currentValue("lockCodes")
    if (lockCodes && lockCodes[0] == "{" && lockCodes?.size()>3 && !lockCodes.contains("name")) {
        setLockCodesValue(lockCodes)
    }   
}

def testSetLockCodesValue1() {    
    setLockCodesValue("""{"1":"John Doe"}""")
    setCodeChangedValue("1 set")
}

def testSetLockCodesValue2() {
    setLockCodesValue("""{"1":"Jone Doe","2":"Jane Does"}""")
    setCodeChangedValue("2 renamed")
}

def testSetLockCodesValue3() {
    setLockCodesValue("""{"1":"John Doe","10":"Jane Doe","3":"Tomm Doe"}""")
    setCodeChangedValue("3 set")
}

def testCodeChanged(codeChanged) {
    if(!testEnable) return
    state.testCodeChanged = codeChanged
    runIn(1,'testCodeChangedValue')
}

def testCodeChangedValue() {
    if(state.testCodeChanged && getLockCodes()) {
        Map lockCodes = [:]
        state.metadata.each{ key, value ->
            if(value?.reason!="deleted") lockCodes[key] = value?.name
        }           
        setLockCodesValue(JsonOutput.toJson(lockCodes))
        setCodeChangedValue(state.testCodeChanged)
        state.remove('testCodeChanged')
    }
}

Boolean nameSlotV(codeNumber, name) {
    Map lockCodes = getLockCodes()
    Map codeMap = getCodeMap(lockCodes,codeNumber)
    return setCodeV(codeNumber, codeMap?.code, name) 
}

Boolean setCodeV(codeNumber, code, name) {
    fixLockCodesV()
    /*
	on sucess
		name		value								data												notes
		codeChanged	added | changed						[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]	default name to code #<codeNumber>
		lockCodes	JSON map of all lockCode
	*/
 	if (codeNumber == null || codeNumber == 0 || code == null) return false

    Map lockCodes = getLockCodes()
    Map codeMap = getCodeMap(lockCodes,codeNumber)
    String value = null
    
    if (!changeIsValid(lockCodes,codeMap,codeNumber,code,name)) {        
        Map data = ["${codeNumber}":["name":"${name}", "code":"${code}"]]
        updateCodeChanged("failed", data)   
        return false
    }    
    logDebug "${device.displayName} setting code ${codeNumber} to ${code} for lock code name ${name}"
    if (codeMap) {
        if (codeMap.name != name || codeMap.code != code) {
            codeMap = ["name":"${name}", "code":"${code}"]
            value = "changed"
            testCodeChanged("$codeNumber renamed")
        }
    } else {
        codeMap = ["name":"${name}", "code":"${code}"]
        value = "added"
        testCodeChanged("$codeNumber set")
    }
    
    if(!state?.metadata) state.metadata = [:]
    state.metadata[codeNumber] = [reason:value, name:codeMap.name, code:codeMap.code]
    return true
}

void deleteCodeV(codeNumber) {
    fixLockCodesV()
    /*
	on sucess
		name		value								data
		codeChanged	deleted								[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
		lockCodes	[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"],<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
	*/
    Map lockCodes = getLockCodes()
    Map codeMap = getCodeMap(lockCodes,"${codeNumber}")
    if (codeMap) {
		Map result = [:]
        //build new lockCode map, exclude deleted code
        lockCodes.each{
            if (it.key != "${codeNumber}"){
                result << it
            }
        }
        testCodeChanged("$codeNumber deleted")
        
        if(!state?.metadata) state.metadata = [:]
        state.metadata[codeNumber] = [reason:"deleted", name:codeMap.name, code:codeMap.code]
        return 
    }
}

//helpers
Boolean changeIsValid(lockCodes, codeMap, codeNumber, code, name){
    //validate proposed lockCode change
    Boolean result = true
    Integer maxCodeLength = device.currentValue("codeLength")?.toInteger() ?: 4
    Integer maxCodes = device.currentValue("maxCodes")?.toInteger() ?: 20
    Boolean isBadLength = (code!=codeUnknown && code.size() > maxCodeLength)
    Boolean isBadCodeNum = maxCodes < codeNumber
    if (lockCodes) {
        List nameSet = lockCodes.collect{ it.value.name }
        List codeSet = lockCodes.collect{ it.value.code }
        if (codeMap) {
            nameSet = nameSet.findAll{ it != codeMap.name }
            codeSet = codeSet.findAll{ it != codeMap.code }
        }
        Boolean nameInUse = name in nameSet
        Boolean codeInUse = code in codeSet
        if (nameInUse || codeInUse) {
            if (nameInUse) { logWarn "${device.displayName} changeIsValid:false, name:${name} is in use:${ lockCodes.find{ it.value.name == "${name}" } }" }
            if (codeInUse) { logWarn "${device.displayName} changeIsValid:false, code:${code} is in use:${ lockCodes.find{ it.value.code == "${code}" } }" }
            result = false
        }
    }
    if (isBadLength || isBadCodeNum) {
        if (isBadLength)  { logWarn "${device.displayName} changeIsValid:false, length of code ${code} does not match codeLength of ${maxCodeLength}" }
        if (isBadCodeNum) { logWarn "${device.displayName} changeIsValid:false, codeNumber ${codeNumber} is larger than maxCodes of ${maxCodes}" }
        result = false
    }
    return result
}

Map getCodeMap(lockCodes, codeNumber){
    Map codeMap = [:]
    Map lockCode = lockCodes?."${codeNumber}"
    if (lockCode) {
        codeMap = ["name":"${lockCode.name}", "code":"${lockCode.code}"]
    }
    return codeMap
}

Map getLockCodes() {
    // on a real lock we would fetch these from the response to a userCode report request
    String lockCodes = device.currentValue("lockCodes")
    Map result = [:]
    if (lockCodes && lockCodes[0] == "{") 
        result = new JsonSlurper().parseText(lockCodes)    
    return result
}

void updateCodeChanged(value, data) {
    String descriptionText = "${device.displayName} code change $data was $value"
    sendEvent(name:"codeChanged", value:value, data:data, descriptionText:descriptionText, isStateChange: true)
    logInfo descriptionText
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
