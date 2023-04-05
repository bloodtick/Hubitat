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
    definition(name: "Replica Location Knob", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaLocationKnob.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Notification"
        capability "Switch"
        capability "Refresh"
        
        attribute "mode", "string"
        attribute "modes", "JSON_OBJECT"
        attribute "latitude", "string"
        attribute "longitude", "string"
        attribute "countryCode", "string"
        attribute "locationName", "string"
        attribute "temperatureScale", "string"
        attribute "timeZoneId", "string"
        attribute "replica", "string"
        attribute "oauthStatus", "enum", ["unknown", "authorized", "failure", "pending"]
        attribute "healthStatus", "enum", ["offline", "online"]        

        command "deleteLocationMode", [[name: "modeName*", type: "STRING", description: "Delete mode name"]]
        command "createLocationMode", [[name: "modeName*", type: "STRING", description: "Create mode name"]]
        command "setLocationMode", [[name: "modeName*", type: "STRING", description: "Set mode string"]]        
        
        // https://github.com/SmartThingsCommunity/smartthings-core-sdk/blob/main/src/endpoint/notifications.ts
        // https://community.smartthings.com/t/notifications-on-rest-api/221042
        command "sendLocationNotification", [
            [name: "title*", type: "STRING", description: "Notification title text"], 
            [name: "message*", type: "STRING", description: "Notification message text"],
            [name: "notification", type: "ENUM", description: "Notification type", constraints: ["ALERT","SUGGESTED_ACTION","EVENT_LOGGING","AUTOMATION_INFO","WARNING","WARNING_CLEAR","FLASH_TOAST","OPTION"]]
        ]        
    }
    preferences {
        input(name:"deviceModeHubitatFollows", type: "bool", title: "<b>Hubitat Hub follows SmartThings Mode Updates:</b>", defaultValue: false)
        input(name:"deviceModeSmartThingsFollows", type: "bool", title: "<b>SmartThings Location follows Hubitat Mode Updates:</b>", defaultValue: false)
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
        input(name:"deviceDebugEnable", type: "bool", title: "Enable Debug logging:", defaultValue: false) 
    }
}

def installed() {
	initialize()
    setOauthStatusValue('unknown')
}

def updated() {
	initialize()    
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
    runEvery1Hour(refresh)
    runIn(15, refresh) // replica needs to load 'description' information before we can startup
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([ "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], "setReplicaValue":[[name:"replica*",type:"STRING"]], "setModeValue":[[name:"mode*",type:"STRING"]], "setOauthStatusValue":[[name:"oauthStatus*",type:"ENUM"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setSwitchValue(value) {
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

def setModeValue(value) {
    String mode = state?.modes?.find{ it?.id==value }?.label
    setModeAttribute(mode)
}

def setReplicaValue(value) {    
    sendEvent(name: "replica", value: value, descriptionText: "${device.displayName} replica set to $value")
}

def setOauthStatusValue(value) {    
    sendEvent(name: "oauthStatus", value: value, descriptionText: "${device.displayName} oauthStatus set to $value")
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "off":[] , "on":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

void refresh() {
    //sendCommand("refresh")
    setReplicaValue( getParent()?.getLabel() )
    getLocationInfo()
    getLocationModes()
    getLocationMode()
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"}]}"""
}

import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static final Integer iHttpSuccess=200
@Field static final Integer iHttpError=400
@Field static final String  sURI="https://api.smartthings.com"

private String getAuthToken() {
    return parent?.getAuthToken()
}

private String getLocationId() {
    String locationId = null
    try {
        String description = getDataValue("description")
        if(description) {
            Map descriptionJson = new groovy.json.JsonSlurper().parseText(description)
            locationId = descriptionJson?.locationId
        }        
    } catch (e) {
        logWarn "${device.displayName} getLocationId error: $e"
    }
    return locationId
}

def setModeAttribute(mode) {
    sendEvent(name: "mode", value: mode, descriptionText: "${device.displayName} mode is $mode")
    if(deviceModeHubitatFollows) getParent()?.setLocationMode(mode)
}

def setModesAttributes(modesMap) {
    state.modes = modesMap?.items?.sort{ (it?.label?:it?.name) }.collect{ [id:it?.id, label:(it?.label?:it?.name)] }
    List modes = modesMap?.items?.collect{ it?.label }.sort()
    if(modes?.size()) sendEvent(name: "modes", value: modes, descriptionText: "${device.displayName} modes are $modes")
}

def setLocationAttributes(locationMap) {
    sendEvent(name: "latitude", value: locationMap.latitude, unit: "°", descriptionText: "${device.displayName} latitude is $locationMap.latitude°")
    sendEvent(name: "longitude", value: locationMap.longitude, unit: "°", descriptionText: "${device.displayName} latitude is $locationMap.longitude°")
    sendEvent(name: "countryCode", value: locationMap.countryCode, descriptionText: "${device.displayName} country code is $locationMap.countryCode")
    sendEvent(name: "locationName", value: locationMap.name, descriptionText: "${device.displayName} location name is $locationMap.name")
    sendEvent(name: "temperatureScale", value: locationMap.temperatureScale, unit: "°", descriptionText: "${device.displayName} temperature scale is $locationMap.temperatureScale°")
    sendEvent(name: "timeZoneId", value: locationMap.timeZoneId, descriptionText: "${device.displayName} time zone ID is $locationMap.timeZoneId")
}

Map setLocationMode(String modeName, event=false) {
    logDebug "${device.displayName} executing 'setLocationMode($modeName)'"
    Map response = [statusCode:iHttpError]
    
    getLocationModes()
    String modeId = state?.modes?.find{ it?.label?.toLowerCase()==modeName?.toLowerCase() }?.id
    if(!modeId || (event && !deviceModeSmartThingsFollows)) return response
    
    Map params = [
        uri: sURI,
        path: "/locations/${getLocationId()}/modes/current",
        body: groovy.json.JsonOutput.toJson([modeId:modeId]),
        contentType: "application/json",
        requestContentType: "application/json",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpPut(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${device.displayName} set SmartThings mode to '${resp.data.label}'"
        }
    } catch (e) {
        logWarn "${device.displayName} has setLocationMode('$modeName' : '$modeId') error: $e"        
    }
    return response
}

Map getLocationMode() {
    logDebug "${device.displayName} executing 'getLocationMode()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/locations/${getLocationId()}/modes/current",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status            
            setModeAttribute(resp.data?.label)
        }
    } catch (e) {
        logWarn "${device.displayName} has getLocationMode() error: $e"        
    }
    return response
}

Map createLocationMode(String modeName) {
    logDebug "${device.displayName} executing 'createLocationMode()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        body: groovy.json.JsonOutput.toJson([label:modeName,name:modeName]), 
        path: "/locations/${getLocationId()}/modes",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpPost(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${device.displayName} created SmartThings mode '${resp.data.label}'"
            getLocationModes()
        }
    } catch (e) {
        logWarn "${device.displayName} has createLocationMode() error: $e"        
    }
    return response
}

Map deleteLocationMode(String modeName) {
    logDebug "${device.displayName} executing 'deleteLocationMode()'"
    Map response = [statusCode:iHttpError]
    
    String modeId = state?.modes?.find{ it?.label?.toLowerCase()==modeName?.toLowerCase() }?.id
    if(!modeId) return response
    
    Map params = [
        uri: sURI,
        path: "/locations/${getLocationId()}/modes/$modeId",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpDelete(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${device.displayName} deleted SmartThings mode '$modeName'"
            getLocationModes()
        }
    } catch (e) {
        logWarn "${device.displayName} has deleteLocationMode() error: $e"        
    }
    return response
}

Map getLocationModes() {
    logDebug "${device.displayName} executing 'getLocationModes()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/locations/${getLocationId()}/modes",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            setModesAttributes(resp.data)           
        }
    } catch (e) {
        logWarn "${device.displayName} has getLocationModes() error: $e"        
    }
    return response
}

Map getLocationInfo() {
    logDebug "${device.displayName} executing 'getLocationInfo()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/locations/${getLocationId()}",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            setLocationAttributes(resp.data)
        }
    } catch (e) {
        logWarn "${device.displayName} has getLocationInfo() error: $e"        
    }
    return response
}

def deviceNotification(String message) {
    if (message && message[0] == "{") {
        try {
            Map jsonMsg = new groovy.json.JsonSlurper().parseText(message)
            sendLocationNotification( jsonMsg?.title ?:"Notification Alert", jsonMsg?.message ?:"Alert Message", jsonMsg?.notification ?:"ALERT" )
        } catch(e) {
            logWarn "${device.displayName} deviceNotification() JSON format expects: { \"title\":\"the title\", \"message\":\"the message\" }"
        }
    } else {    
        sendLocationNotification("Notification Alert", message)
    }
}

Map sendLocationNotification(String title, String message, String notification="ALERT") {
    logDebug "${device.displayName} executing 'sendLocationNotification($title, $message, $notification)'"
    Map response = [statusCode:iHttpError]
    
    Map body = [
      locationId: getLocationId(),
      type: notification,
      messages: [ [ default: [ title: title, body: message ] ] ]
    ]
    
    Map params = [
        uri: sURI,
        body: groovy.json.JsonOutput.toJson(body), 
        path: "/notification",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpPost(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${device.displayName} sent SmartThings $notification notification"
        }
    } catch (e) {
        logWarn "${device.displayName} has sendLocationNotification() error: $e"        
    }
    return response
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
