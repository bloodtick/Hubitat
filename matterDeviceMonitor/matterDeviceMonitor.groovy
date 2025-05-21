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
        attribute "healthStatus", "enum", ["offline", "online"]
    }

    preferences {
        input(name:"hubIp", type: "text", title: "Hubitat Hub IP:", defaultValue: "127.0.0.1", required: true)
        input(name:"pollInterval", type: "number", title: "Poll Interval (minutes):", range: "1...", defaultValue: 2, required: true)
        input(name:"deviceInfoDisable", type:"bool", title: "Disable Info logging:", defaultValue: false)
    	input(name:"deviceDebugEnable", type:"bool", title: "Enable Debug logging:", defaultValue: false)
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    logInfo "scheduling poll every ${pollInterval} minutes"
    schedule("0 */${pollInterval} * * * ?", poll)
    runIn(1, poll)
}

def poll() {
    String url = "http://${hubIp}:8080/hub/matterDetails/json"
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
        sendEvent(name: "contact", value: "open")
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
        sendEvent(name: "contact", value: (total>0 && total-onlineCount==0) ? "closed" : "open")
        sendEvent(name: "healthStatus", value: (resp.json?.installed && resp.json?.enabled) ? "online" : "offline")

        logDebug "async updated with $total devices and $onlineCount online"

    } else {
        logWarn "Matter API async unexpected status: ${resp?.status} installed:${resp.json?.installed} enabled:${resp.json?.enabled}"
        sendEvent(name: "healthStatus", value: "offline")
        sendEvent(name: "contact", value: "open")
    }
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${device.displayName} ${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${device.displayName} ${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${device.displayName} ${msg}" } }
private logWarn(msg)  { log.warn   "${device.displayName} ${msg}" }
private logError(msg) { log.error  "${device.displayName} ${msg}" }
