/**
 *  Copyright 2025 Bloodtick Jones
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
 *  Matter Device Monitor
 *
 *  Author: bloodtick
 *  Date: 2025-05-20
 */
public static String version() {return "1.0.00"}

import java.text.SimpleDateFormat
import groovy.transform.Field

@Field volatile static Map<String,Boolean> isInitialized = [:]

metadata {
    definition(name: "Matter Device Monitor", namespace: "bloodtick", author: "Hubitat", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/matterDeviceMonitor/matterDeviceMonitor.groovy")
    {
        capability "Actuator"
        capability "Polling"
        capability "Initialize"
        capability "ContactSensor"

        attribute "offlineSummary", "string"
        attribute "offlineDni", "JSON_OBJECT"
        attribute "devicesOnline", "number"
        attribute "devicesOffline", "number"
        attribute "devices", "JSON_OBJECT"
        attribute "contactDate", "string"
        attribute "healthStatus", "enum", ["offline","online","initialize","starting"]
    }

    preferences {
        input(name:"deviceIp", type: "text", title: "Hubitat Hub IP:", defaultValue: "127.0.0.1", required: true)
        input(name:"devicePollInterval", type: "number", title: "Poll Interval (minutes):", range: "1...", defaultValue: 2, required: true)
        input(name:"deviceDateFormat", type:"string", title: "Date format (default: 'yyyy-MM-dd h:mm:ss a'):", description: "<a href='https://en.wikipedia.org/wiki/ISO_8601' target='_blank'>ISO 8601 date/time string legal format</a>", defaultValue: "yyyy-MM-dd h:mm:ss a")
        input(name:"deviceShowDevices", type:"bool", title: "Enable 'devices' Attribute:", defaultValue: true)
        input(name:"deviceInfoDisable", type:"bool", title: "Disable Info logging:", defaultValue: false)
    	input(name:"deviceDebugEnable", type:"bool", title: "Enable Debug logging:", defaultValue: false)
    }
}

def installed() {
    updated()
}

def updated() {
    initialize()
    runIn(1, poll)
}

def initialize() {
    logInfo "scheduling poll every $devicePollInterval minutes"
    unschedule()
    schedule("0 */${devicePollInterval} * * * ?", poll)
    runIn(30, poll) // when the hub first boots the stack isn't ready. If just an update this will get overwritten. 
    sendEvent(name: "healthStatus", value: "initialize")
}

def poll() {
    String uri = "http://${deviceIp}:8080/hub/matterDetails/json"
    Map params = [
        uri: uri,
        contentType: "application/json",
        requestContentType: "application/json",
        timeout: 10
    ]

    logDebug "async polling matter device status from $uri"    
    try {
        asynchttpGet("handlePollResponse", params)
    } catch (Exception e) {
        logError "async error polling Matter API: ${e.message}"
        sendEvent(name: "healthStatus", value: "offline")
    }
}

def handlePollResponse(resp, data) {    
    try { // sometimes on start up we get a bad resp before the system is ready. 
        def ignored = resp?.json
        
        isInitialized[device.getId()] = true
    } catch(e) {
        if(isInitialized[device.getId()]) { // lets wait one time before yelling about bad JSON.
            logError "Matter API at $deviceIp did not return valid JSON with status:${resp?.status}"
            sendEvent(name: "healthStatus", value: "offline")
        }
        isInitialized[device.getId()] = true        
        return
    }    
    
    if(resp?.status==200 && resp?.json?.devices) {
        List report = resp.json.devices.collect {
            [name: it.name, dni: it.dni, online: it.online]
        }.sort { it.name }
        if(deviceShowDevices) {
        	String jsonString = groovy.json.JsonOutput.toJson(report)
        	sendEvent(name: "devices", value: jsonString)
        }
        else device.deleteCurrentState("devices")

        Integer total = report?.size() ?: 0
        Integer onlineCount = report?.count { it.online } ?: 0
        
        List offlineDevices = report.findAll { !it.online }
        List offlineNames = offlineDevices*.name
        List offlineDni = offlineDevices*.dni

        String offlineSummary = offlineNames?.join(', ') ?: "none"
        String offlineDniJson = groovy.json.JsonOutput.toJson(offlineDni)

        sendContactEvent((total > 0 && total - onlineCount == 0) ? "closed" : "open", offlineSummary)
        sendEvent(name: "offlineSummary", value: offlineSummary)
        sendEvent(name: "offlineDni", value: offlineDniJson)
        sendEvent(name: "devicesOnline", value: onlineCount)
        sendEvent(name: "devicesOffline", value: total - onlineCount)
        sendEvent(name: "healthStatus", value: "online")        
        logDebug "async updated with $total devices and $onlineCount online"        
    } else {
        if(resp?.status==200 && resp?.json?.networkState?.toString().toLowerCase()=="starting") {
            runIn(30, poll)
            sendEvent(name: "healthStatus", value: "starting")
        } else {
            logWarn "Matter API at $deviceIp returned status:${resp?.status} enabled:${resp?.json?.enabled} networkState:${resp?.json?.networkState} devices:${resp?.json?.devices?.size()}"
            sendEvent(name: "healthStatus", value: (resp?.json?.enabled) ? "online" : "offline")
        }
    }
}

def sendContactEvent(String contact, String offlineSummary=null) {   
    if(device.currentValue("contact")!=contact || device.currentValue("offlineSummary")!=offlineSummary || state.dateFormat != deviceDateFormat) {
        state.dateFormat = deviceDateFormat
	    sendEvent(name: "contact", value: contact)
        logInfo "contact is $contact${(contact=="open" && offlineSummary!=null) ? " with '$offlineSummary' offline" : ""}"
        
        String formattedDate = "Date Format Invalid"
        try {
        	SimpleDateFormat sdf = new SimpleDateFormat(settings?.deviceDateFormat)
    		formattedDate = sdf.format((new Date()))
        } catch(e) {
            logWarn "${settings?.deviceDateFormat} format invalid"
        }
        sendEvent(name: "contactDate", value: formattedDate)        
    }
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${device.displayName} ${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${device.displayName} ${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${device.displayName} ${msg}" } }
private logWarn(msg)  { log.warn   "${device.displayName} ${msg}" }
private logError(msg) { log.error  "${device.displayName} ${msg}" }
