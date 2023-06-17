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
public static String version() {return "1.3.3"}

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
        attribute "oauthStatus", "enum", ["unknown", "authorized", "warning", "failure", "pending"]
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
        
		capability "TemperatureMeasurement"
		//capability "IlluminanceMeasurement"
		capability "RelativeHumidityMeasurement"
		//capability "PressureMeasurement"
		capability "UltravioletIndex"

        //	The following attributes may be needed for dashboards that require these attributes,
        //	so they are always available and shown by default.
		//attribute "city", "string"			//Hubitat  OpenWeather  SharpTool.io  SmartTiles
		attribute "feelsLike", "number"		//SharpTool.io  SmartTiles
		//attribute "forecastIcon", "string"	//SharpTool.io
		attribute "localSunrise", "string"	//SharpTool.io  SmartTiles
		attribute "localSunset", "string"	//SharpTool.io  SmartTiles
		//attribute "percentPrecip", "number"	//SharpTool.io  SmartTiles
		//attribute "pressured", "string"		//UNSURE SharpTool.io  SmartTiles
		attribute "weather", "string"		//SharpTool.io  SmartTiles
		attribute "weatherIcon", "string"	//SharpTool.io  SmartTiles
		//attribute "weatherIcons", "string"	//Hubitat  openWeather
		attribute "wind", "number"			//SharpTool.io
		attribute "windDirection", "number"	//Hubitat  OpenWeather
		//attribute "windSpeed", "number"		//Hubitat  OpenWeather        
        attribute "uvDescription", "string"
        attribute "windDirectionCardinal", "string"
        attribute "windGust", "string"
        attribute "cloudCover", "string"
        attribute "visibility", "number"
        attribute "cloudCeiling", "string" // could be 'unlimited' so is a string
	    attribute "iconCode", "number"

        attribute "precip1Hour", "number"
        attribute "precip6Hour", "number"    
        attribute "precip24Hour", "number"
        attribute "snow1Hour", "number"
        attribute "snow6Hour", "number"
        attribute "snow24Hour", "number"
        attribute "temperatureAmount1Hour", "number"
        attribute "temperatureAmount2Hour", "number"
        attribute "temperatureAmount3Hour", "number"
        attribute "temperatureAmount4Hour", "number"
        attribute "temperatureAmount5Hour", "number"
        attribute "temperatureAmount6Hour", "number"
        attribute "temperatureAmount7Hour", "number"
        attribute "temperatureAmount8Hour", "number"
        attribute "temperatureAmount9Hour", "number"
        attribute "temperatureAmount10Hour", "number"
        attribute "temperatureAmount11Hour", "number"
        attribute "temperatureAmount12Hour", "number"
        
        attribute "airQualityIndex", "number"
        attribute "no2Amount", "number"
        attribute "no2Index", "number"
        attribute "o3Amount", "number"
        attribute "o3Index", "number"
        attribute "so2Amount", "number"
        attribute "so2Index", "number"
        attribute "coAmount", "number"
        attribute "coIndex", "number"
        attribute "pm10Amount", "number"
        attribute "pm10Index" , "number"
        attribute "pm25Amount", "number"
        attribute "pm25Index", "number"

        // deviceAdditionalAttributes
        attribute "isDay", "enum", ["true", "false"]
        attribute "alert", "enum", ["on", "off"]
        attribute "alertIssueTime", "string"
        attribute "alertExpireTime", "string"
        attribute "alertSeverity", "string"
        attribute "alertHeadlineText", "string"                
        attribute "lastUpdateTime", "string"                
        attribute "airQualityLevel", "string"
        attribute "airQualityDescription", "string"
       
        command "pollServices"
        command "testServiceAlert"
    }
    preferences {
        input(name:"deviceModeHubitatFollows", type: "bool", title: "<b>Hubitat Hub follows SmartThings Mode Updates:</b>", defaultValue: false)
        input(name:"deviceModeSmartThingsFollows", type: "bool", title: "<b>SmartThings Location follows Hubitat Mode Updates:</b>", defaultValue: false)       
        input(name:"deviceSmartThingServices", type:"enum", title: "SmartThings Services:", options: ["disable":"No Services (disable)", "weather":"Weather Only", "forecast":"Forecast Only", "airQuality":"AirQuality Only", 
                                                                                                      "weatherForecast":"Weather and Forecast", "weatherAirQuality":"Weather and AirQuality", "forecastAirQuality":"Forecast and AirQuality", "all":"All Services"], defaultValue: "disable", required: true)
        if(settings?.deviceSmartThingServices && settings.deviceSmartThingServices != "disable") {
            input(name:"deviceServicesPollInterval", type: "enum", title: "Services Poll Interval", options: [ "manual":"Manual Poll Only", "runEvery10Minutes":"10 Minutes", "runEvery30Minutes":"30 Minutes", "runEvery1Hour":"1 Hour", "runEvery3Hour":"3 Hours"], defaultValue: "runEvery10Minutes", required: true)
            input(name:"deviceMeasurementFormat", type:"enum", title: "Measurement Unit:", options: ["imperial":"Imperial system (inches)", "meters":"Metric system (mm or cm)"], defaultValue: "imperial", required: true)
            input(name:"deviceWindSpeedFormat", type:"enum", title: "Wind speed Unit:", options: ["mph":"miles (mph)", "kph":"kilometers (kph)", "kn":"knots (kn)", "m/s":"meters (m/s)"], defaultValue: "mph", required: true)
            input(name:"deviceTemperaturePrecision", type:"enum", title: "Temperature Precision:", options: [0:"0", 1:"1", 2:"2", 3:"3"], defaultValue: 0, required: true)           
            input(name:"deviceMeasurementPrecision", type:"enum", title: "Measurement Precision:", options: [0:"0", 1:"1", 2:"2", 3:"3"], defaultValue: 1, required: true)            
            input(name:"deviceWindSpeedPrecision", type:"enum",   title: "Wind speed Precision:",  options: [0:"0", 1:"1", 2:"2", 3:"3"], defaultValue: 0, required: true)
            input(name:"deviceAdditionalAttributes", type: "bool", title: "Display Additional Services Attributes + Alerts:", defaultValue: false)
        }        
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
        input(name:"deviceDebugEnable", type: "bool", title: "Enable Debug logging:", defaultValue: false) 
    }
}

void delLocationAdditionalAttributesAttributes(Boolean force=false) {
    if(settings.deviceAdditionalAttributes.toBoolean()==false || force)
        for(String item : ["isDay", "alert", "alertIssueTime", "alertExpireTime", "alertSeverity", "alertHeadlineText", "lastUpdateTime", "airQualityLevel", "airQualityDescription"]) {
            device.deleteCurrentState(item)
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
    unschedule()
    runEvery1Hour(refresh)
    runIn(15, refresh) // replica needs to load 'description' information before we can startup
    
    // set defaults. these might not set immediately.
    if(settings?.deviceSmartThingServices==null)   device.updateSetting('deviceSmartThingServices',[value:"disable",type:"enum"])
    if(settings?.deviceServicesPollInterval==null) device.updateSetting('deviceServicesPollInterval',[value:"runEvery10Minutes",type:"enum"])
    if(settings?.deviceMeasurementFormat==null)    device.updateSetting('deviceMeasurementFormat',[value:"imperial",type:"enum"])
    if(settings?.deviceWindSpeedFormat==null)      device.updateSetting('deviceWindSpeedFormat',[value:"mph",type:"enum"])
    if(settings?.deviceTemperaturePrecision==null) device.updateSetting('deviceTemperaturePrecision',[value:"0",type:"enum"])
    if(settings?.deviceMeasurementPrecision==null) device.updateSetting('deviceMeasurementPrecision',[value:"1",type:"enum"])
    if(settings?.deviceWindSpeedPrecision==null)   device.updateSetting('deviceWindSpeedPrecision',[value:"0",type:"enum"])
    if(settings?.deviceAdditionalAttributes==null) device.updateSetting('deviceAdditionalAttributes',[value:false, type:"bool"])
    if(settings?.deviceServicesPollInterval==null) runIn(5, initializeServices); else initializeServices() // ensure our updateSetting complete so we don't have to worry about null values.
}

def initializeServices() {
    pollServices("initialize")
    if(settings.deviceSmartThingServices && settings.deviceSmartThingServices!="disable" && settings.deviceServicesPollInterval!="manual") {
        "${settings.deviceServicesPollInterval}"(pollServices)
    }
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
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
static Map getReplicaTriggers() {
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

static String getReplicaRules() {
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

void setModeAttribute(mode) {
    sendEvent(name: "mode", value: mode, descriptionText: "${device.displayName} mode is $mode")
    if(deviceModeHubitatFollows) getParent()?.setLocationMode(mode)
}

void setModesAttributes(modesMap) {
    state.modes = modesMap?.items?.sort{ (it?.label?:it?.name) }.collect{ [id:it?.id, label:(it?.label?:it?.name)] }
    List modes = modesMap?.items?.collect{ it?.label }.sort()
    if(modes?.size()) sendEvent(name: "modes", value: modes, descriptionText: "${device.displayName} modes are $modes")
}

void setLocationAttributes(locationMap) {
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
    Map data = [
        uri: sURI,
        path: "/locations/${getLocationId()}",
        method: "getLocationInfo"        
    ]    
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

Map getLocationInfoSync() {
    logDebug "${device.displayName} executing 'getLocationInfoSync()'"
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
        logWarn "${device.displayName} has getLocationInfoSync() error: $e"        
    }
    return response
}

void deviceNotification(String message) {
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

// https://github.com/SmartThingsCommunity/smartthings-core-sdk/blob/main/src/endpoint/services.ts
// options: ["disable":"No Services", "weather":"Weather Only", "forecast":"Forecast Only", "airQuality":"AirQuality Only", "weatherForecast":"Weather and Forecast", "weatherAirQuality":"Weather and AirQuality", "forecastAirQuality":"Forecast and AirQuality", "all":"All Services"], defaultValue: "disable", required: true)
void pollServices(String reason="poll") {
    logDebug "${device.displayName} executing 'pollServices($reason)'"    
    String services = settings.deviceSmartThingServices
    
    if(reason=="initialize") {
        logInfo "${device.displayName} ${services=="disable"?"disabling all services":"initialize $services service(s)"}"
        
        if(!(services=="weather" || services=="weatherForecast" || services=="weatherAirQuality" || services=="all"))
            delLocationWeatherAttributes()
        if(!(services=="forecast" || services=="weatherForecast" || services=="forecastAirQuality" || services=="all"))
            delLocationForecastAttributes()
        if(!(services=="airQuality" || services=="weatherAirQuality" || services=="forecastAirQuality" || services=="all"))
            delLocationAirQualityAttributes()
        delLocationAdditionalAttributesAttributes(services=="disable")
        g_mDeviceServiceLastUpdateTime[device.getIdAsLong()] = null
        g_mDeviceWeatherLastUpdateTime[device.getIdAsLong()] = null
        g_mDeviceForcastLastUpdateTime[device.getIdAsLong()] = null
        g_mDeviceAirQualityLastUpdateTime[device.getIdAsLong()] = null
        state.remove("alert")        
        state.remove("alertCount")
        state.remove("weatherDetailUrl")
        reason="poll"
    }
    
    if(reason=="poll" && (services=="weather" || services=="weatherForecast" || services=="weatherAirQuality" || services=="all")) {    
        getLocationService("weather")                 
    }
    else if((reason=="poll" || reason=="getLocationService(weather)") && (services=="forecast" || services=="weatherForecast" || services=="forecastAirQuality" || services=="all")) {
        getLocationService("forecast")        
    }
    else if((reason=="poll" || reason=="getLocationService(forecast)" || reason=="getLocationForecast") && (services=="airQuality" || services=="weatherAirQuality" || services=="forecastAirQuality" || services=="all")) {
        getLocationService("airQuality")        
        //getLocationService("airQualityForecast") // doesn't seem to work        
    }
    else if(services!="disable" && settings?.deviceAdditionalAttributes) {
        getLocationService("alert")
    }        
}

Map getLocationService(String name) {
    logDebug "${device.displayName} executing 'getLocationService($service)'"
    Map response = [statusCode:iHttpError]
    Map data = [
        uri: sURI,
        path: "/services/coordinate/locations/${getLocationId()}/capabilities",
        query: [ name:name ], // forecast, weather, airQuality
        method: "getLocationService($name)"        
    ]    
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

private Map asyncHttpGet(String callbackMethod, Map data) {
    logDebug "${device.displayName} executing 'asyncHttpGet()'"
    Map response = [statusCode:iHttpError]
	
    Map params = [
	    uri: data.uri,
	    path: data.path,
        query: (data?.query ?: [:]),
		headers: [ Authorization: "Bearer ${getAuthToken()}" ]
    ]
	try {
	    asynchttpGet(callbackMethod, params, data)
        response.statusCode = iHttpSuccess
	} catch (e) {
	    logWarn "${device.displayName} asyncHttpGet() error: $e"
	}
    return response
}

void asyncHttpGetCallback(resp, data) {
    logDebug "${device.displayName} executing 'asyncHttpGetCallback()' status: ${resp.status} method: ${data?.method}"
    
    if (resp.status == iHttpSuccess) {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logTrace "response data: ${resp.data}"
        
        switch(data?.method) {
            case "getLocationService(weather)":
                Map respJson = new groovy.json.JsonSlurper().parseText(resp.data)                
                setLocationWeatherAttributes(respJson?.weather)
                pollServices(data.method)
                respJson = null
                break
            case "getLocationService(forecast)":
                Map respJson = new groovy.json.JsonSlurper().parseText(resp.data)                
                setLocationForecastAttributes(respJson?.forecast)
                pollServices(data.method)
                respJson = null
                break
            case "getLocationService(airQuality)":
                Map respJson = new groovy.json.JsonSlurper().parseText(resp.data)                
                setLocationAirQualityAttributes(respJson?.airQuality)
                pollServices(data.method)
                respJson = null
                break
            case "getLocationService(alert)":
                Map respJson = new groovy.json.JsonSlurper().parseText(resp.data)
                setLocationAlertAttributes(respJson?.alert)
                respJson = null
                break            
            case "getLocationService(airQualityForecast)":
                Map respJsonrespJson = new groovy.json.JsonSlurper().parseText(resp.data)                
                logInfo respJson            
                respJson = null
                break
            case "getLocationInfo":
                def respJson = new groovy.json.JsonSlurper().parseText(resp.data)
                setLocationAttributes(respJson)
                respJson = null
                break
            default:
                logWarn "${device.displayName} asyncHttpGetCallback() ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        logWarn("${device.displayName} asyncHttpGetCallback() ${data?.method} ${data?.deviceId ? getReplicaDevices(data.deviceId) : ""}  status:${resp.status} reason:${resp.errorMessage}")
    }
}

// this value can vary a couple of seconds between each service call. Did it this way to not have multiple events.
@Field volatile static Map<Long,String> g_mDeviceServiceLastUpdateTime = [:]
void lastUpdateTime(String value) {
    Date lastUpdateTimeDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", value, TimeZone.getTimeZone("UTC"))
    if(lastUpdateTimeDate > Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", g_mDeviceServiceLastUpdateTime[device.getIdAsLong()]?:"2000-01-01T12:00:00.000Z", TimeZone.getTimeZone("UTC"))) {
        g_mDeviceServiceLastUpdateTime[device.getIdAsLong()] = value
        
        String timeZoneId = device.currentValue("timeZoneId").toString()
        TimeZone tz = TimeZone.getTimeZone(timeZoneId) // figure out the local timezone of the Hub. Might be different than Hubitat hub. 
        sendEvent(name: "lastUpdateTime", value: lastUpdateTimeDate.format("yyyy-MM-dd'T'HH:mm:ssXXX", tz).toString())      
    }
}

// [{severity={value=3}, expireTime={value=2023-06-03T04:00:00.000Z}, messageType={value=2}, issueTime={value=2023-06-01T05:46:00.000Z}, lastUpdateTime={value=2023-06-01T11:46:49.197Z}, headlineText={value=Flood Watch until SAT 12:00 AM EDT}}]
def testServiceAlert() {
    if(device.currentValue("alert").toString()=="on")
        setLocationAlertAttributes( [] )
    else 
        setLocationAlertAttributes( [[severity:[value:3], expireTime:[value:"2023-06-03T04:00:00.000Z"], messageType:[value:2], issueTime:[value:"2023-06-01T05:46:00.000Z"], lastUpdateTime:[value:"2023-06-01T11:46:49.197Z"], headlineText:[value:"Flood Watch until SAT 12:00 AM EDT"]]] )
}

void setLocationAlertAttributes(List alertList) {
    logDebug "${device.displayName} executing 'setLocationAlertAttributes()'"    
    if(settings?.deviceAdditionalAttributes.toBoolean()!=true) return 
    
    alertList?.each { alert ->
        alert.remove("lastUpdateTime")
    }    
    if(alertList!=null && !alertList.isEmpty() && !state?.alert.equals( alertList )) {
        state.alert = alertList                
        if(!state?.alertCount) state.alertCount=1; else state.alertCount+=1; 
        
        sendEvent(name: "alert", value: "on",  data: [ alert: alertList ], descriptionText: "${device.displayName} alert set to on", isStateChange: true)
        String timeZoneId = device.currentValue("timeZoneId").toString()
        TimeZone tz = TimeZone.getTimeZone(timeZoneId) // figure out the local timezone of the Hub. Might be different than Hubitat hub.            

        alertList.each{ alertMap -> 
            if(alertMap?.issueTime?.value) {
                Date issueTime =  Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", alertMap.issueTime.value, TimeZone.getTimeZone("UTC"))
                sendEvent(name: "alertIssueTime", value: issueTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tz).toString())
            }
            if(alertMap?.expireTime?.value) {
                Date expireTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", alertMap.expireTime.value, TimeZone.getTimeZone("UTC"))
                sendEvent(name: "alertExpireTime", value: expireTime.format("yyyy-MM-dd'T'HH:mm:ssXXX", tz).toString())
            }
            if(alertMap?.severity) sendEvent(name: "alertSeverity", value: alertMap.severity.value.toString())
            if(alertMap?.headlineText) sendEvent(name: "alertHeadlineText", value: alertMap.headlineText.value.toString())
        }        
        logInfo "$device.displayName alert: ${groovy.json.JsonOutput.toJson(alertList)}" 
    } else if(alertList!=null && state.alert!=alertList) {
        state.alert = alertList
        sendEvent(name: "alert", value: "off", data: [ alert: alertList ], descriptionText: "${device.displayName} alert set to off")
        sendEvent(name: "alertIssueTime", value: " ")
        sendEvent(name: "alertExpireTime", value: " ")
        sendEvent(name: "alertSeverity", value: " ")
        sendEvent(name: "alertHeadlineText", value: " ")
    }
}

void delLocationWeatherAttributes() {
    for(String item : ["weather", "weatherIcon","temperature", "feelsLike", "wind", "windDirectionCardinal", "windGust", "ultravioletIndex", "uvDescription",
                       "humidity", "windDirection", "localSunrise", "localSunset", "weatherUpdateTime", "cloudCover", "iconCode", "cloudCeiling", "visibility" ]) {
        device.deleteCurrentState(item)
    } 
}  

@Field volatile static Map<Long,String> g_mDeviceWeatherLastUpdateTime = [:]
void setLocationWeatherAttributes(Map weatherMap) {
    logDebug "${device.displayName} executing 'setLocationWeatherAttributes()'"
    
    if(weatherMap?.lastUpdateTime) {        
        if(g_mDeviceWeatherLastUpdateTime[device.getIdAsLong()] == weatherMap.lastUpdateTime.value) return        
        g_mDeviceWeatherLastUpdateTime[device.getIdAsLong()] = weatherMap.lastUpdateTime.value.toString()
        if(settings?.deviceAdditionalAttributes) lastUpdateTime(weatherMap.lastUpdateTime.value)
    }
    logDebug groovy.json.JsonOutput.toJson(weatherMap)
    
    // temp widgets
    if(weatherMap?.temperature) sendEvent(name: "temperature", value: convertTemperatureIfNeeded(weatherMap.temperature.value, weatherMap.temperature.unit, settings.deviceTemperaturePrecision.toInteger()), unit: "°"+getTemperatureScale())    
    if(weatherMap?.temperatureFeelsLike) sendEvent(name: "feelsLike", value: convertTemperatureIfNeeded(weatherMap.temperatureFeelsLike.value, weatherMap.temperatureFeelsLike.unit, settings.deviceTemperaturePrecision.toInteger()), unit: "°"+getTemperatureScale())
    
    // speed widgets
    Map windSpeed = convertWindSpeedIfNeeded(weatherMap?.windSpeed?.value?:0, settings.deviceWindSpeedPrecision.toInteger())
    sendEvent(name: "wind", value: windSpeed.value, unit: windSpeed.unit)        
    Map windGust = convertWindSpeedIfNeeded(weatherMap?.windGust?.value?:0, settings.deviceWindSpeedPrecision.toInteger())
    sendEvent(name: "windGust", value: windGust.value, unit: windGust.unit)
    
    // other things
    if(weatherMap?.windDirectionCardinal) sendEvent(name: "windDirectionCardinal", value: weatherMap.windDirectionCardinal.value)    
    if(weatherMap?.uvIndex) sendEvent(name: "ultravioletIndex", value: weatherMap.uvIndex.value)    
    if(weatherMap?.uvDescription) sendEvent(name: "uvDescription", value: weatherMap.uvDescription.value)    
    if(weatherMap?.relativeHumidity) sendEvent(name: "humidity", value: weatherMap.relativeHumidity.value, unit: '%rh')    
    if(weatherMap?.windDirection) sendEvent(name: "windDirection", value: weatherMap.windDirection.value, unit: '°')    
    if(weatherMap?.cloudCoverPhrase) sendEvent(name: "cloudCover", value: weatherMap.cloudCoverPhrase.value) 
    if(weatherMap?.iconCode) sendEvent(name: "iconCode", value: weatherMap.iconCode.value)
    
    // detail https link to weather
    if(weatherMap?.weatherDetailUrl) state.weatherDetailUrl = """<a href="${weatherMap.weatherDetailUrl.value}" target="_blank">${weatherMap.weatherDetailUrl.value}</a>"""

    // distance widgets
    Map visibility = convertMeasureIfNeeded(weatherMap?.visibility?.value?:0, weatherMap?.visibility?.unit?:"Km", settings.deviceMeasurementFormat, settings.deviceMeasurementPrecision.toInteger())
    sendEvent(name: "visibility", value: visibility.value, unit: visibility.unit)
    Map cloudCeiling = convertMeasureIfNeeded(weatherMap?.cloudCeiling?.value?:0, weatherMap?.cloudCeiling?.unit?:"m", settings.deviceMeasurementFormat, 0)
    sendEvent(name: "cloudCeiling", value: weatherMap?.cloudCeiling?cloudCeiling.value:"unlimited", unit: cloudCeiling.unit)

    // time widgets
    Boolean isDay = false
    if(weatherMap?.sunriseTimeLocal && weatherMap?.sunsetTimeLocal) {        
        String timeZoneId = device.currentValue("timeZoneId").toString()
        BigDecimal latitude = device.currentValue("latitude").toBigDecimal()
        TimeZone tz = TimeZone.getTimeZone(timeZoneId) // figure out the local timezone of the Hub. Might be different than Hubitat hub.
        
        Date sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", weatherMap.sunriseTimeLocal.value, TimeZone.getTimeZone("UTC"))
        Date sunsetTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", weatherMap.sunsetTimeLocal.value, TimeZone.getTimeZone("UTC"))
        
        if(sunriseTime == sunsetTime) {            
            Date midnightSun = Date.parse("yyyy-MM-dd", (latitude>0 ? (new Date().format('yyyy', tz) + '-03-21') : (new Date().format('yyyy', tz) + '-09-21')), tz)
            Date polarNight  = Date.parse("yyyy-MM-dd", (latitude<0 ? (new Date().format('yyyy', tz) + '-03-21') : (new Date().format('yyyy', tz) + '-09-21')), tz)           
            isDay = ((new Date()).after(midnightSun) && (new Date()).before(polarNight))
            logDebug "$device.displayName midnightSun: $midnightSun, polarNight: $polarNight, isDay: $isDay"
        }
        else {
            isDay = ((new Date()).after(sunriseTime) && (new Date()).before(sunsetTime))
        }
        sendEvent(name: "localSunrise", value: sunriseTime.format('h:mm a', tz).toString())
        sendEvent(name: "localSunset", value: sunsetTime.format('h:mm a', tz).toString())               
    }
    if(settings?.deviceAdditionalAttributes) sendEvent(name: "isDay", value: isDay.toString()) 
    
    if(weatherMap?.wxPhraseLong) {
        sendEvent(name: "weather", value: weatherMap.wxPhraseLong.value)
        sendEvent(name: "weatherIcon", value: getWeatherIcon(weatherMap?.iconCode?.value, weatherMap.wxPhraseLong.value, isDay))
    }
}

void delLocationForecastAttributes() {
    for(String item : ["precip1Hour","precip6Hour","precip24Hour","snow1Hour","snow6Hour","snow24Hour", 
                       "temperatureAmount1Hour","temperatureAmount2Hour","temperatureAmount3Hour","temperatureAmount4Hour","temperatureAmount5Hour","temperatureAmount6Hour",
                       "temperatureAmount7Hour","temperatureAmount8Hour","temperatureAmount9Hour","temperatureAmount10Hour","temperatureAmount11Hour","temperatureAmount12Hour"]) {
        device.deleteCurrentState(item)
    }    
}

@Field volatile static Map<Long,String> g_mDeviceForcastLastUpdateTime = [:]
void setLocationForecastAttributes(Map forecastMap) {
    logDebug "${device.displayName} executing 'setLocationForecastAttributes()'"
    
    if(forecastMap?.lastUpdateTime) {        
        if(g_mDeviceForcastLastUpdateTime[device.getIdAsLong()] == forecastMap.lastUpdateTime.value) return        
        g_mDeviceForcastLastUpdateTime[device.getIdAsLong()] = forecastMap.lastUpdateTime.value.toString()
        if(settings?.deviceAdditionalAttributes) lastUpdateTime(forecastMap.lastUpdateTime.value)
    }
    logDebug groovy.json.JsonOutput.toJson(forecastMap)
 
    for(String item : ["precip1Hour","precip6Hour","precip24Hour","snow1Hour","snow6Hour","snow24Hour"]) {        
        if(forecastMap?.containsKey(item)) {
            Map rainsnow = convertMeasureIfNeeded(forecastMap."$item".value, forecastMap."$item".unit, settings.deviceMeasurementFormat, settings.deviceMeasurementPrecision.toInteger())
            sendEvent(name: item, value: rainsnow.value, unit: rainsnow.unit)
        }
    }    
    for(String item : ["temperatureAmount1Hour","temperatureAmount2Hour","temperatureAmount3Hour","temperatureAmount4Hour","temperatureAmount5Hour","temperatureAmount6Hour",
                       "temperatureAmount7Hour","temperatureAmount8Hour","temperatureAmount9Hour","temperatureAmount10Hour","temperatureAmount11Hour","temperatureAmount12Hour"]) {
        if(forecastMap?.containsKey(item)) sendEvent(name: item, value: convertTemperatureIfNeeded(forecastMap."$item".value, forecastMap."$item".unit, settings.deviceTemperaturePrecision.toInteger()), unit: "°"+getTemperatureScale())
    }  
}

void delLocationAirQualityAttributes() {
    for(String item : ["airQualityIndex","no2Amount","no2Index","o3Amount","o3Index","so2Amount", "so2Index",
                       "coAmount","coIndex","pm10Amount","pm10Index","pm25Amount","pm25Index"]) {
        device.deleteCurrentState(item)
    }    
}

@Field volatile static Map<Long,String> g_mDeviceAirQualityLastUpdateTime = [:]
void setLocationAirQualityAttributes(Map airQualityMap) {
    logDebug "${device.displayName} executing 'setLocationAirQualityAttributes()'"
    
     if(airQualityMap?.lastUpdateTime) {        
        if(g_mDeviceAirQualityLastUpdateTime[device.getIdAsLong()] == airQualityMap.lastUpdateTime.value) return        
        g_mDeviceAirQualityLastUpdateTime[device.getIdAsLong()] = airQualityMap.lastUpdateTime.value.toString()
        if(settings?.deviceAdditionalAttributes) lastUpdateTime(airQualityMap.lastUpdateTime.value)
    }
    logDebug groovy.json.JsonOutput.toJson(airQualityMap)
    
    for(String item : ["airQualityIndex","no2Amount","no2Index","o3Amount","o3Index","so2Amount", "so2Index",
                       "coAmount","coIndex","pm10Amount","pm10Index","pm25Amount","pm25Index"]) {
        if(airQualityMap?.containsKey(item)) sendEvent(name: item, value: airQualityMap."$item"?.value, unit: airQualityMap."$item"?.unit?:"")
    }
    if(settings?.deviceAdditionalAttributes && airQualityMap?.airQualityIndex) {
        Integer aqi = (airQualityMap.airQualityIndex.value.toInteger()<=500) ? airQualityMap.airQualityIndex.value.toInteger() : 500
        Integer key = AQICodePLU.find{ k, v -> v.rangeLo<=aqi && v.rangeHi>=aqi }.key
        sendEvent(name: "airQualityLevel", value: AQICodePLU[key].level)
        sendEvent(name: "airQualityDescription", value: AQICodePLU[key].desription)        
    }
}

Map convertMeasureIfNeeded(BigDecimal value, String unit, String format, Integer precision) {
	if(format == "meters") {
    	return [value:roundAndRemoveTrailingZeros(value, precision), unit:unit]
    } else if (unit=="mm" && format=="imperial") {
    	return [value:roundAndRemoveTrailingZeros((value * 0.0393701), precision), unit:"in"]
    } else if (unit=="cm" && format=="imperial") {
    	return [value:roundAndRemoveTrailingZeros((value * 0.393701), precision), unit:"in"]
    } else if (unit=="Km" && format=="imperial") {
    	return [value:roundAndRemoveTrailingZeros((value * 0.621371), precision), unit:"mi"]
    } else if (unit=="m" && format=="imperial") {
    	return [value:roundAndRemoveTrailingZeros((value * 3.28084), precision), unit:"ft"]
    }
}

// options: ["mph":"miles (mph)", "kph":"kilometers (kph)", "kn":"knots (kn)", "m/s":"meters (m/s)"]
Map convertWindSpeedIfNeeded(BigDecimal windSpeed, Integer precision) {
    String windSpeedFormat = settings.deviceWindSpeedFormat
    
	if(windSpeedFormat == "kph") {
    	return [value:roundAndRemoveTrailingZeros(windSpeed, precision), unit:windSpeedFormat]
    } else if (windSpeedFormat == "m/s") {
    	return [value:roundAndRemoveTrailingZeros((windSpeed * 0.277778), precision), unit:windSpeedFormat]
    } else if (windSpeedFormat == "mph") {
    	return [value:roundAndRemoveTrailingZeros((windSpeed * 0.621371192), precision), unit:windSpeedFormat] 
    } else if (windSpeedFormat == "kn") {
    	return [value:roundAndRemoveTrailingZeros((windSpeed * 0.539956803), precision), unit:windSpeedFormat]
    }
}

BigDecimal roundAndRemoveTrailingZeros(BigDecimal number, Integer precision) {
    number = number.setScale(precision, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
    if (number.scale() < 0) {
        number = number.setScale(0)
    }
    return number
}

String getWeatherIcon(Integer iconCode, String wxPhraseLong, Boolean isDay)     {
    if(iconCode!=null && iconCodePLU[iconCode] && iconCodePLU[iconCode][3]?.find{ it==wxPhraseLong })
        logDebug "$device.displayName found iconCode:$iconCode with wxPhraseLong:$wxPhraseLong"
    else
        logWarn "$device.displayName missing iconCode:$iconCode with wxPhraseLong:$wxPhraseLong"
    
    return (iconCode!=null && iconCodePLU[iconCode]) ? (isDay ? iconCodePLU[iconCode][2] : "nt_" + iconCodePLU[iconCode][2]) : getWUIconName(wxPhraseLong, isDay)
}

String getWUIconName(String wxPhraseLong, Boolean isDay)     {
    String key = wxPhraseLong.replaceAll("[ /]", "_").toLowerCase()
    String wuIcon = (conditionFactor[key] ? conditionFactor[key][2] : '')
    if(!isDay && wuIcon) wuIcon = "nt_" + wuIcon;
    if(!wuIcon) logWarn "$device.displayName could not determine weatherIcon using '$wxPhraseLong' as a key word" 
    return wuIcon
}

// https://www.airnow.gov/aqi/aqi-basics/#:~:text=Think%20of%20the%20AQI%20as,300%20represents%20hazardous%20air%20quality.
@Field static final Map<Integer, List<Object>> AQICodePLU = [
    0: [color:"green",  level:"Good", rangeLo:0, rangeHi:50,  desription:"Air quality is satisfactory and air pollution poses little or no risk"],
    1: [color:"yellow", level:"Moderate", rangeLo:51, rangeHi:100, desription:"Air quality is acceptable. There may be a risk for people who are unusually sensitive to air pollution"],
    2: [color:"orange", level:"Unhealthy Sensitive Groups", rangeLo:101, rangeHi:150, desription:"Members of sensitive groups may experience health effects"],
    3: [color:"red",    level:"Unhealthy", rangeLo:151, rangeHi:200, desription:"Some members of the general public may experience health effects; members of sensitive groups may experience more serious health effects"],
    4: [color:"purple", level:"Very Unhealthy", rangeLo:201, rangeHi:300, desription:"Health alert: The risk of health effects is increased for everyone"],
    5: [color:"maroon", level:"Hazardous",  rangeLo:301, rangeHi:500, desription:"Health warning of emergency conditions: everyone is more likely to be affected"]
];

// Starting to map the Weather Channel iconCode to weatherIcon expected values. Haven't found any documentation to match. Else try matching from the long phrase.
// Found this for sharptools.io https://gist.github.com/joshualyon/7bb3b2a9e2a6801ff673bf4c1e159452
// ["chanceflurries", "chancerain", "chancesleet", "chancesnow", "chancetstorms", "clear", "cloudy", "flurries", "fog", "hazy", "mostlycloudy", "mostlysunny", "partlycloudy", "partlysunny", "rain", "sleet", "snow", "sunny", "tstorms"]
@Field static final Map<Integer, List<Object>> iconCodePLU = [
     0: [1000, 1.0, "clear",        ["Clear","Sunny","Fair","Showers in the Vicinity","Sunny/Wind","Fair/Wind"]],
     1: [1003, 0.8, "partlycloudy", ["Partly Cloudy","Partly Cloudy/Wind","Showers in the Vicinity"]],
     2: [1006, 0.6, "cloudy",       ["Cloudy","Mostly Cloudy","Mostly Cloudy/Wind","Cloudy/Wind","Showers in the Vicinity"]],
     3: [1135, 0.2, "fog",          ["Fog","Haze","Smoke","Fog/Wind"]],
     4: [1189, 0.4, "rain",         ["Rain"]],
     5: [1183, 0.7, "chancerain",   ["Light Rain","Rain Shower","Light Rain/Wind"]],
     9: [1087, 0.2, "tstorms",      ["Thunder","Thunder in the Vicinity"]],
    10: [1213, 0.7, "snow",         ["Light Snow","Light Snow/Wind"]],
    14: [1219, 0.5, "snow",         ["Snow"]],
    15: [1198, 0.7, "sleet",        ["Wintry Mix","Light Freezing Rain"]],
    20: [1273, 0.5, "tstorms",      ["Light Rain with Thunder","Thunderstorm","Heavy Thunderstorm","Thunderstorm/Wind"]]
];

// values pulled from https://github.com/adey/bangali/blob/4145c4ef4430a04530129a9d39ca7636944c8dc2/driver/apixu-weather.groovy#L481
// and https://wiki.webcore.co/TWC_Weather and then refactor via chatGPT 4.0 (so awesome)
@Field static final Map<String, List<Object>> conditionFactor = [
        "sunny": [1000, 1, "sunny"],
        "partly_cloudy": [1003, 0.8, "partlycloudy"],
        "cloudy": [1006, 0.6, "cloudy"],
        "overcast": [1009, 0.5, "cloudy"],
        "mist": [1030, 0.5, "fog"],
        "patchy_rain_possible": [1063, 0.8, "chancerain"],
        "patchy_snow_possible": [1066, 0.6, "chancesnow"],
        "patchy_sleet_possible": [1069, 0.6, "chancesleet"],
        "patchy_freezing_drizzle_possible": [1072, 0.4, "chancesleet"],
        "thundery_outbreaks_possible": [1087, 0.2, "chancetstorms"],
        "blowing_snow": [1114, 0.3, "snow"],
        "blizzard": [1117, 0.1, "snow"],
        "fog": [1135, 0.2, "fog"],
        "freezing_fog": [1147, 0.1, "fog"],
        "patchy_light_drizzle": [1150, 0.8, "rain"],
        "light_drizzle": [1153, 0.7, "rain"],
        "freezing_drizzle": [1168, 0.5, "sleet"],
        "heavy_freezing_drizzle": [1171, 0.2, "sleet"],
        "patchy_light_rain": [1180, 0.8, "rain"],
        "light_rain": [1183, 0.7, "rain"],
        "moderate_rain_at_times": [1186, 0.5, "rain"],
        "moderate_rain": [1189, 0.4, "rain"],
        "heavy_rain_at_times": [1192, 0.3, "rain"],
        "heavy_rain": [1195, 0.2, "rain"],
        "light_freezing_rain": [1198, 0.7, "sleet"],
        "moderate_or_heavy_freezing_rain": [1201, 0.3, "sleet"],
        "light_sleet": [1204, 0.5, "sleet"],
        "moderate_or_heavy_sleet": [1207, 0.3, "sleet"],
        "patchy_light_snow": [1210, 0.8, "flurries"],
        "light_snow": [1213, 0.7, "snow"],
        "patchy_moderate_snow": [1216, 0.6, "snow"],
        "moderate_snow": [1219, 0.5, "snow"],
        "patchy_heavy_snow": [1222, 0.4, "snow"],
        "heavy_snow": [1225, 0.3, "snow"],
        "ice_pellets": [1237, 0.5, "sleet"],
        "light_rain_shower": [1240, 0.8, "rain"],
        "moderate_or_heavy_rain_shower": [1243, 0.3, "rain"],
        "torrential_rain_shower": [1246, 0.1, "rain"],
        "light_sleet_showers": [1249, 0.7, "sleet"],
        "moderate_or_heavy_sleet_showers": [1252, 0.5, "sleet"],
        "light_snow_showers": [1255, 0.7, "snow"],
        "moderate_or_heavy_snow_showers": [1258, 0.5, "snow"],
        "light_showers_of_ice_pellets": [1261, 0.7, "sleet"],
        "moderate_or_heavy_showers_of_ice_pellets": [1264,0.3, "sleet"],
        "patchy_light_rain_with_thunder": [1273, 0.5, "tstorms"],
        "moderate_or_heavy_rain_with_thunder": [1276, 0.3, "tstorms"],
        "patchy_light_snow_with_thunder": [1279, 0.5, "tstorms"],
        "moderate_or_heavy_snow_with_thunder": [1282, 0.3, "tstorms"],
        "clear": [1000, 1, "clear"],
        "cloudy_wind": [1006, 0.6, "cloudy"],
        "fair": [1003, 0.8, "sunny"],
        "fair_wind": [1003, 0.8, "sunny"],
        "flurries": [1210, 0.8, "flurries"],
        "frozen_rain_mix": [1198, 0.7, "sleet"],
        "heavy_rain_wind": [1195, 0.2, "rain"],
        "light_rain_wind": [1183, 0.7, "rain"],
        "light_rain_with_thunder": [1183, 0.7, "rain"],
        "mostly_cloudy": [1006, 0.6, "cloudy"],
        "mostly_cloudy_wind": [1006, 0.6, "cloudy"],
        "partly_cloudy_wind": [1003, 0.8, "partlycloudy"],
        "rain_freezing_rain": [1201, 0.3, "sleet"],
        "rain_shower": [1189, 0.4, "rain"],
        "rain_shower_wind": [1189, 0.4, "rain"],
        "rain_shower_with_thunder": [1189, 0.3, "tstorms"],
        "rain_snow": [1255, 0.7, "snow"],
        "rain_wind": [1189, 0.4, "rain"],
        "snow_shower": [1258, 0.5, "snow"],
        "snow_shower_wind": [1258, 0.5, "snow"],
        "snow_wind": [1258, 0.5, "snow"],
        "sunny_wind": [1000, 1, "sunny"],
        "rain": [1189, 0.4, "rain"],
        "wintry_mix": [1198, 0.7, "sleet"],
        "snow": [1219, 0.5, "snow"],
        "showers_in_the_vicinity": [1006, 0.6, "cloudy"],
        "thunder": [1087, 0.2, "chancetstorms"],
        "heavy_thunderstorm": [1195, 0.2, "tstorms"],
        "thunderstorm": [1195, 0.2, "tstorms"],
];

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }

/*
https://community.sharptools.io/t/weather-tile-custom-device-definition/445
weather - description of the weather/forecast [required]
weatherIcon - reports a Weather Underground or TWC compatible icon id 22
forecastIcon - should report a Weather Underground compatible icon name 6. The weather tile falls back to this attribute if the weatherIcon is not available
city - replaces the device name in the top-left (location is used as a fallback)
temperature - numeric temperature (without units)
feelsLike - numeric temperature (without units)
wind - in MPH
illuminance - hidden for now
humidity - as a percentage (without units)
percentPrecip - as a percentage (without units)
localSunrise - time
localSunset - time

[locationId:3bd6c80e-7e2b-4ad2-9437-8c8d6b52f76e, 
weather:[
*iconCode:[value:0], 
*cloudCoverPhrase:[value:Partly Cloudy], 
expirationTime:[value:1684880746000], 
*relativeHumidity:[value:51, unit:%], 
*sunriseTimeLocal:[value:2023-05-23T09:32:20.000Z], 
*sunsetTimeLocal:[value:2023-05-24T00:13:32.000Z], 
*temperature:[value:17, unit:C], 
*temperatureFeelsLike:[value:17, unit:C], 
*uvDescription:[value:Low], 
*uvIndex:[value:1], 
*visibility:[value:16.09, unit:Km], 
*windDirection:[value:150, unit:°], 
*windDirectionCardinal:[value:SSE], 
*windGust:[value:24, unit:Km/h],
*windSpeed:[value:13, unit:Km/h],
*wxPhraseLong:[value:Sunny], 
*weatherDetailUrl:[value:https://weather.com/weather/today/l/7d6cf89713cf0b1b4565151c049d921738418b0de7aec10bd8ef39c2cdf6b2e2?par=samsung_stdash], 
vendor:[value:TheWeatherChannel], 
version:[value:v3.0], 
lastUpdateTime:[value:2023-05-23T22:19:57.000Z]]]

[locationId:3bd6c80e-7e2b-4ad2-9437-8c8d6b52f76e, 
forecast:[
precip1Hour:[value:0, unit:mm], 
precip6Hour:[value:0, unit:mm], 
precip24Hour:[value:0, unit:mm], 
snow1Hour:[value:0, unit:cm], 
snow6Hour:[value:0, unit:cm], 
snow24Hour:[value:0, unit:cm], 
temperatureAmount1Hour:[value:15, unit:C], 
temperatureAmount2Hour:[value:16, unit:C], 
temperatureAmount3Hour:[value:17, unit:C], 
temperatureAmount4Hour:[value:19, unit:C], 
temperatureAmount5Hour:[value:21, unit:C], 
temperatureAmount6Hour:[value:23, unit:C], 
temperatureAmount7Hour:[value:23, unit:C], 
temperatureAmount8Hour:[value:24, unit:C], 
temperatureAmount9Hour:[value:23, unit:C], 
temperatureAmount10Hour:[value:21, unit:C], 
temperatureAmount11Hour:[value:20, unit:C], 
temperatureAmount12Hour:[value:19, unit:C], 
lastUpdateTime:[value:2023-05-24T11:39:56.000Z]]]

[locationId:3bd6c80e-7e2b-4ad2-9437-8c8d6b52f76e, 
airQuality:[
airQualityIndex:[value:29, unit:CAQI], 
expirationTime:[value:1685052114000], 
no2Amount:[value:10.19, unit:μg/m^3], 
no2Index:[value:1, unit:EPA], 
o3Amount:[value:81.64, unit:μg/m^3], 
o3Index:[value:1, unit:EPA], 
so2Amount:[value:1.63, unit:μg/m^3], 
so2Index:[value:1, unit:EPA], 
coAmount:[value:172.13, unit:μg/m^3], 
coIndex:[value:1, unit:EPA], 
pm10Amount:[value:14.84, unit:μg/m^3], 
pm10Index:[value:1, unit:EPA], 
pm25Amount:[value:7.45, unit:μg/m^3], 
pm25Index:[value:1, unit:EPA], 
lastUpdateTime:[value:2023-05-25T21:19:51.000Z]]]
*/

