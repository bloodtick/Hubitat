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

metadata {
    definition(name: "Matter Device Monitor", namespace: "bloodtick", author: "Hubitat", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/matterDeviceMonitor/matterDeviceMonitor.groovy")
    {
        capability "Actuator"
        capability "Polling"
        capability "Initialize"
        capability "ContactSensor"

        attribute "offlineSummary", "string"
        attribute "devicesOnline", "number"
        attribute "devicesOffline", "number"
        attribute "devices", "JSON_OBJECT"
        attribute "contactDate", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
    }

    preferences {
        input(name:"deviceIp", type: "text", title: "Hubitat Hub IP:", defaultValue: "127.0.0.1", required: true)
        input(name:"devicePollInterval", type: "number", title: "Poll Interval (minutes):", range: "1...", defaultValue: 2, required: true)
        input(name:"deviceFormat", type:"string", title: "Date format (default: 'yyyy-MM-dd h:mm:ss a'):", description: "<a href='https://en.wikipedia.org/wiki/ISO_8601' target='_blank'>ISO 8601 date/time string legal format</a>", defaultValue: "yyyy-MM-dd h:mm:ss a")
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
    if(devicePollInterval.toInteger()>1) runIn(90, poll) // when the hub first boots the stack isn't ready. If just an update this will get overwritten. 
    sendContactEvent("initialize")
}

def poll() {
    String url = "http://${deviceIp}:8080/hub/matterDetails/json"
    Map params = [
        uri: url,
        contentType: "application/json",
        requestContentType: "application/json",
        timeout: 10
    ]

    logDebug "async polling matter device status from ${url}"
    
    try {
        asynchttpGet("handlePollResponse", params)
    } catch (Exception e) {
        logError "async error polling Matter API: ${e.message}"
        sendEvent(name: "healthStatus", value: "offline")
    }
}

def handlePollResponse(resp, data) {
    if (resp?.status == 200 && resp?.json?.devices) {
        List report = resp.json.devices.collect {
            [name: it.name, dni: it.dni, online: it.online]
        }.sort { it.name }
        String jsonString = groovy.json.JsonOutput.toJson(report)
        sendEvent(name: "devices", value: jsonString)

        Integer total = report?.size() ?: 0
        Integer onlineCount = report?.count { it.online } ?: 0
        
        List offlineDevices = report.findAll { !it.online }*.name
        String offlineSummary = offlineDevices?.join(', ') ?: "none"

        sendEvent(name: "offlineSummary", value: offlineSummary)
        sendEvent(name: "devicesOnline", value: onlineCount)
        sendEvent(name: "devicesOffline", value: total-onlineCount)
        sendEvent(name: "healthStatus", value: "online")
        sendContactEvent((total>0 && total-onlineCount==0) ? "closed" : "open")
        logDebug "async updated with $total devices and $onlineCount online"
        
    } else {
        logWarn "Matter API async unexpected status:${resp?.status} ${resp?.status==200 ? "device count:${resp?.json?.devices?.size()}" : ""}"
        sendEvent(name: "healthStatus", value: "offline")
    }
}

def sendContactEvent(String contact) {   
    if(device.currentValue("contact")!=contact) {
	    sendEvent(name: "contact", value: contact)
        logInfo "contact is $contact"
        
        String formattedDate = "Date Format Invalid"
        try {
        	SimpleDateFormat sdf = new SimpleDateFormat(settings?.deviceFormat)
    		formattedDate = sdf.format((new Date()))
        } catch(e) {
            logWarn "${settings?.deviceFormat} format invalid"
        }
        sendEvent(name: "contactDate", value: formattedDate)        
    }
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${device.displayName} ${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${device.displayName} ${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${device.displayName} ${msg}" } }
private logWarn(msg)  { log.warn   "${device.displayName} ${msg}" }
private logError(msg) { log.error  "${device.displayName} ${msg}" }
