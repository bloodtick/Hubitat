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
 *  Cudy Router R700
 *  For configuration see here: https://community.hubitat.com/t/run-dual-internet-providers-at-home/149109
 *
 *  Author: bloodtick
 *  Date: 2025-02-07
 */
public static String version() {return "1.0.00"}

import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.transform.Field
import java.text.SimpleDateFormat

@Field volatile static Map<Long,Integer> g_iRefreshCount = [:]

metadata {
    definition(name: "Cudy Router R700", namespace: "bloodtick", author: "Hubitat", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/refs/heads/main/cudy/cudyRouterR700.groovy")
    {
        capability "Actuator"
        capability "ContactSensor"
        capability "Initialize"
        capability "Refresh"
        
        command "reboot"
        
        attribute "publicIpAddress", "string"
        attribute "lastRebootTime", "string"
        attribute "balancerStatus", "enum", ["online", "degraded", "offline", "unknown"]
        attribute "wan1Status", "enum", ["offline", "online", "unknown"]
        attribute "wan2Status", "enum", ["offline", "online", "unknown"]        
        attribute "wan1IpAddress", "string"
        attribute "wan2IpAddress", "string"
        attribute "wan1OnlineSince", "string"
        attribute "wan2OnlineSince", "string"
        attribute "cpuPercent", "number"
        attribute "memPercent", "number"        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
}

preferences {
    input(name:"routerHost", type:"string", title:"Router Host", description:"Enter router IP address", required: true)
    input(name:"routerUsername", type:"string", title:"Router Username  (default: 'admin')", description:"Enter username", defaultValue: "admin", required: false)
    input(name:"routerPassword", type:"password", title:"Router Password", description:"Enter password", required: true)
    input(name:"routerCpuMemStats", type:"bool", title: "Enable router CPU and memory attributes:", defaultValue: true)
    input(name:"deviceFormat", type:"string", title: "Date format (default: 'yyyy-MM-dd h:mm:ss a'):", description: "<a href='https://en.wikipedia.org/wiki/ISO_8601' target='_blank'>ISO 8601 date/time string legal format</a>", defaultValue: "yyyy-MM-dd h:mm:ss a")
    input(name:"deviceInfoDisable", type:"bool", title: "Disable Info logging:", defaultValue: false)
    input(name:"deviceDebugEnable", type:"bool", title: "Enable Debug logging:", defaultValue: false)
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "disabling debug logs"
}
Boolean autoLogsOff() { if ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) runIn(1800, "logsOff"); else unschedule('logsOff');}

def installed() {
    logInfo "executing 'installed()'"
    setHealthStatusEvent()
}

def updated() {
    logDebug "executing 'updated()'"
    unschedule()
    autoLogsOff()
    if(!validDateFormat(settings?.deviceFormat)) logWarn "invalid ISO 8601 format"
    if(!settings?.routerCpuMemStats) { device.deleteCurrentState("cpuPercent"); device.deleteCurrentState("memPercent") }
    if(validIp(routerHost) && routerPassword && authenticate()) {
        initialize()
    } else {
        setHealthStatusEvent("offline")
        if(!validIp(routerHost)) logWarn "$routerHost is not a valid IPv4 address"
    }        
}

def initialize() {
    logInfo "executing 'initialize()'"
    unschedule('refresh')
    sendEvent(name:"balancerStatus", value:"unknown")
    sendEvent(name:"contact", value: "open")
    sendEvent(name:"publicIpAddress", value:"unknown")
    if(state?.authToken || authenticate()) {
        schedule("*/10 * * * * ?", refresh) // every 10 seconds
        g_iRefreshCount[device.getIdAsLong()]=null
        state.remove("authenticateDelay")
        refresh()
    } else {
        state.authenticateDelay = state?.authenticateDelay ? Math.min( state.authenticateDelay * 2, 600 ) : 1
        if(state.authenticateDelay>2) log.warn "delaying authenticate retry by $state.authenticateDelay seconds"
        runIn(state.authenticateDelay,"initialize")
    }
}

def refresh() {
    logDebug "executing 'refresh()'"
    if(state?.authToken || authenticate()) {
        if(g_iRefreshCount[device.getIdAsLong()]==null || g_iRefreshCount[device.getIdAsLong()]%(6*2) == 0) { //every 2 minutes
            runIn(1,"refreshAll")
        } else {
            (g_iRefreshCount[device.getIdAsLong()])++
            getBalancerStatus()
        }
    } else initialize()
}

def refreshAll() {
    g_iRefreshCount[device.getIdAsLong()] = 1    
    // should use a queue. but i am being lazy.
    if(settings?.routerCpuMemStats) getSystemLoad()
    //pauseExecution(100)
    //getSystemStatus() // don't think we need this other than first time if we capture the time.
    pauseExecution(100)
    getBalancerStatus()
    pauseExecution(100)
    getWan1Status()
    pauseExecution(100)
    getWan2Status()
    // do some checks on our public IP Address is valid
    String publicIpAddress = device.currentValue("publicIpAddress")
    if((!validIp(publicIpAddress) && !(["unknown", "offline"].contains(device.currentValue("balancerStatus")))) || 
       (publicIpAddress != device.currentValue("wan1IpAddress") && publicIpAddress != device.currentValue("wan2IpAddress"))) { 
        runIn(10, "getPublicIpAddress")
    }
}

void setHealthStatusEvent(String healthStatus="offline") {
    unschedule('setHealthStatusEvent') 
    if(device.currentValue("healthStatus")!=healthStatus) {
        sendEvent(name: "healthStatus", value: healthStatus, descriptionText: "${device.displayName} healthStatus set to $healthStatus")
        if(healthStatus=="offline") { 
            sendEvent(name: "balancerStatus", value: "unknown")
            logWarn("healthStatus set to $healthStatus")
        } else logInfo("healthStatus set to $healthStatus")
    }
}

def authenticate() {
    def response = false
    runIn(10, "setHealthStatusEvent") // will move to offline if not set to online
    logDebug "executing 'authenticate()'"

    def timeclock = (now() / 1000).toInteger()  // Safe Hubitat timestamp
    def zonename = TimeZone.getDefault().getID()
    def username = routerUsername?.trim() ?: "admin" // Default username if empty    
    def body = "zonename=${URLEncoder.encode(zonename, 'UTF-8')}" + "&timeclock=${timeclock}" + "&luci_language=auto" +
               "&luci_username=${URLEncoder.encode(username, 'UTF-8')}" + "&luci_password=${URLEncoder.encode(routerPassword, 'UTF-8')}"

    Map params = [
        uri            : "http://${routerHost}/cgi-bin/luci/",
        headers        : [ "Content-Type" : "application/x-www-form-urlencoded" ],
        body           : body,
        ignoreSSLIssues: true, // In case of SSL issues on some routers
        followRedirects: false, // Prevents Hubitat from failing due to redirect
        timeout: 5,
    ]

    try {
        httpPost(params) { resp ->
            logDebug "authentication Response: HTTP ${resp.status}"
            if (resp.status == 302 || resp.status == 200) {  // Accept 302 and 200 as success
                def cookieHeader = resp.headers.findAll { it.name == "Set-Cookie" }*.value          
                def sysauthCookie = cookieHeader.find { it.contains("sysauth=") }
                
                if (sysauthCookie) {
                    state.authToken = sysauthCookie.split(";")[0]  // Store session cookie
                    logInfo "authentication successful"
                    setHealthStatusEvent("online")
                    response = true
                } else logWarn "authentication response did not contain sysauth cookie."
            } else {
                logError "authentication failed: HTTP ${resp.status}"
            }
        }
    } catch (Exception e) {
        if (e.message.contains("Premature end of chunk coded message body") && response) {
            logDebug "ignoring chunk encoding error. Authentication likely succeeded."
        } else {
            logError "error during authentication: ${e.message}"
        }
    }
    if(!response) state.remove('authToken')
    return response
}

def reboot() {
   def params = [
        uri: "http://${routerHost}/cgi-bin/luci/admin/system/reboot/apply",
        headers : ["Cookie": state.authToken, "X-Requested-With": "XMLHttpRequest" ],
        timeout: 5,
    ]

    try {
        httpGet(params) { response ->
            if (response.status == 200) {
                logInfo "reboot triggered successfully!"
            } else {
                logWarn "failed to reboot the router! Status: ${response.status}"
            }
        }
    } catch (Exception e) {
        logError "error sending reboot command: ${e.message}"
    }
}

def getPublicIpAddress() {
    logDebug "executing 'getPublicIpAddress()'"
    def params = [
        uri: "https://checkip.amazonaws.com",
        timeout: 5,
    ]
    
    try {
	    asynchttpGet("asyncHttpCallback", params, [method:"getPublicIpAddress", type:"text", params:params])
	} catch (e) {
	    logWarn "'getPublicIpAddress()' asynchttpGet() error: $e"
	}
}

def getSystemStatus() {
    getRouterDataAsync("getSystemStatus","admin/system/status?detail=1")
}

def getSystemLoad() {
    getRouterDataAsync("getSystemLoad","admin/status/load","json")
}

def getWan1Status() {
    getRouterDataAsync("getWan1Status","admin/network/wan/status?detail=1")
}

def getWan2Status() {
    getRouterDataAsync("getWan2Status","admin/network/wan/status?detail=1&iface=wanb")
}

def getBalancerStatus() {
    getRouterDataAsync("getBalancerStatus","admin/network/mwan3/status?detail=1")
}

void getRouterDataAsync(String method, String endpoint, String type="html") {
    logDebug "executing 'getRouterDataAsync($method,$endpoint)'"
    Map params = [
        uri : "http://${routerHost}/cgi-bin/luci/${endpoint}",
        headers : ["Cookie": state.authToken],
        timeout: 5,
    ]
    
    try {
	    asynchttpGet("asyncHttpCallback", params, [method:method, type:type, params:params])
	} catch (e) {
	    logWarn "'getRouterDataAsync($method)' error: $e"
	}
}

void asyncHttpCallback(resp, data) {
    logDebug "executing 'asyncHttpCallback()' status: ${resp.status} method: ${data?.method} type: ${data?.type}"
    
    if(resp.status == 200) {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logTrace "response data: ${resp.data}"
        Map dataMap = data?.type=="html" ? parseHtmlToMap( groovy.xml.XmlUtil.serialize(resp.data) ) : data?.type=="json" ? [ load:(new JsonSlurper().parseText(resp.data))?:[] ] : null
        //logInfo (dataMap?:resp.data)
        switch(data?.method) {
            case "getPublicIpAddress":
                String publicIpAddress = resp.data.trim().toString()
                if(validIp(publicIpAddress) && device.currentValue("publicIpAddress")!=publicIpAddress) {
                    sendEvent(name:"publicIpAddress", value: publicIpAddress)
                    logInfo("publicIpAddress set to $publicIpAddress")
                }
                break
            case "getSystemLoad":
                Map load = calcAvgCpuMem(dataMap?.load)
                sendEvent(name:"cpuPercent", value: load?.cpu, unit:"%")
                sendEvent(name:"memPercent", value: load?.mem, unit:"%")
                break
            case "getSystemStatus":
                sendEvent(name: "lastRebootTime", value: uptimeToTimestamp(dataMap?.uptime)?:"unknown")
                break
            case "getWan1Status":
                String wan1IpAddress = (validIp(dataMap?.ip_address) && device.currentValue("wan1Status")=="online") ? dataMap.ip_address : dataMap?.status ?: "unknown"
                if(device.currentValue("wan1IpAddress")!=wan1IpAddress) {
                    sendEvent(name:"wan1IpAddress", value: wan1IpAddress)
                    logInfo("wan1IpAddress set to $wan1IpAddress")
                    if(validIp(wan1IpAddress)) runIn(10,"getPublicIpAddress")
                }
                sendEvent(name: "wan1OnlineSince", value: uptimeToTimestamp(dataMap?.connected_time)?:dataMap?.status?:"unknown")
                break
            case "getWan2Status":
                String wan2IpAddress = (validIp(dataMap?.ip_address) && device.currentValue("wan2Status")=="online") ? dataMap.ip_address : dataMap?.status ?: "unknown"
                if(device.currentValue("wan2IpAddress")!=wan2IpAddress) {
                    sendEvent(name:"wan2IpAddress", value: wan2IpAddress)
                    logInfo("wan2IpAddress set to $wan2IpAddress")
                    if(validIp(wan2IpAddress)) runIn(10,"getPublicIpAddress")
                }
                sendEvent(name: "wan2OnlineSince", value: uptimeToTimestamp(dataMap?.connected_time)?:dataMap?.status?:"unknown")
                break
            case "getBalancerStatus":
                String wan1Status = dataMap?.wan1?:"unknown"
                if(device.currentValue("wan1Status")!=wan1Status) {
                    sendEvent(name:"wan1Status", value: wan1Status)
                    logInfo("wan1Status set to $wan1Status")
                    runIn(1,"getWan1Status") 
                }
                String wan2Status = dataMap?.wan2?:"unknown"
                if(device.currentValue("wan2Status")!=wan2Status) { 
                    sendEvent(name:"wan2Status", value: wan2Status)
                    logInfo("wan2Status set to $wan2Status")
                    runIn(1,"getWan2Status")
                }            
                String balancerStatus = (dataMap?.status=="connected") ? "online" : (dataMap?.wan1=="online" || dataMap?.wan2=="online") ? "degraded" : dataMap?.status=="not connected" ? "offline" : "unknown"
                if(device.currentValue("balancerStatus")!=balancerStatus) {
                    sendEvent(name:"balancerStatus", value: balancerStatus)
                    if(balancerStatus=="online") {
                        logInfo("balancerStatus set to $balancerStatus")
                        sendEvent(name:"contact", value: "closed")
                    } else {
                        logWarn("balancerStatus set to $balancerStatus")
                        sendEvent(name:"contact", value: "open")
                    }
                    runIn(1,"getSystemStatus")
                    runIn(10,"getPublicIpAddress")
                }
                break
            default:
                logWarn "asyncHttpGetCallback() ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else if(resp.status == 408 && !!state?.authToken && data?.method=="getBalancerStatus") {
        authenticate()            
    }
    else if(resp.status == 403 && !!state?.authToken && !!data?.method && authenticate()) {        
        runIn(5, data.method)     
    }
    else {
        logWarn("asyncHttpGetCallback() ${data?.method} status:${resp.status} errorMessage:${resp?.errorMessage?:"none"} params:${data?.params}")
        logTrace("Available Properties: ${resp.properties}")
    }
}

String uptimeToTimestamp(String uptime) {
    if(uptime==null) return null
    String time = (uptime.contains("day") ? uptime.replaceAll("\\s*days?\\s*", ":") : "0:$uptime").replaceAll(" ", "")
    def (days, h, m, s) = time.tokenize(":")*.toInteger()
    Long timestamp = ((now() / 1000).toLong() - (days * 86400 + h * 3600 + m * 60 + s)) * 1000
    
    if(!state?.dateFormatInvalid && settings?.deviceFormat) {
        SimpleDateFormat sdf = new SimpleDateFormat(settings?.deviceFormat)
        return sdf.format(new Date(timestamp))
    } else return timestamp.toString()
}

String normalizeKey(String key) {
    return key?.toLowerCase()
              ?.replaceAll("\\s+", "_")
              ?.replaceAll("[^a-z0-9_]", "")
              ?.replaceAll("_+", "_")
}

Map parseHtmlToMap(String html) {
    logDebug "executing 'parseHtmlToMap()' html:${ escapeXmlForLog(html) }"
    Map result = [:]

    try {
        def parsedXml = new XmlSlurper().parseText(html)

        def rows = parsedXml?.div?.table?.tbody?.tr
        logDebug "extracted tbody Rows: ${rows}"

        rows?.each { row ->
            String key = normalizeKey(row?.td[1]?.div?.p?.find { it.text()?.trim() }?.text()?.trim())
            String value = row?.td[2]?.div?.p?.find { it.text()?.trim() }?.text()?.trim()?.toLowerCase()
            if (key && value) {
                logDebug "extracted Key: ${key}, Value: ${value}"
                result[key] = value
            }
        }
        
        rows = parsedXml?.div?.table?.thead?.tr
        logDebug "extracted thead Rows: ${rows}"

        rows?.each { row ->
            String key = normalizeKey(row?.th[1]?.find { it.text()?.trim() }?.text()?.trim())
            String value = row?.th[2]?.find { it.text()?.trim() }?.text()?.trim()?.toLowerCase()
            if (key && value) {
                logDebug "extracted Key: ${key}, Value: ${value}"
                result[key] = value
            }
        }

        logDebug "final map: ${result}"
    } catch (Exception e) {
        logError "error parsing HTML: ${e.message}"
    }

    return result
}

Map calcAvgCpuMem(List<List<Long>> data, Integer size=0) {
    if (size == 0) size = data.size() - 1
    if (data.size() < size + 1) return [ cpu:0.0, mem:0.0 ]

    List cpuUsages = [], memUsages = []
    for (Integer i = data.size() - size - 1; i < data.size() - 1; i++) {
        def prev = data[i], latest = data[i + 1]
        def totalDiff = latest[2] - prev[2], idleDiff = latest[1] - prev[1]
        if (totalDiff > 0 && idleDiff >= 0) cpuUsages << (1 - (idleDiff / totalDiff)) * 100
        memUsages << (latest[3] / 100.0)
    }

    return [
        cpu: (cpuUsages ? (cpuUsages.sum() / cpuUsages.size()).toFloat().round(1) : 0.0),
        mem: (memUsages ? (memUsages.sum() / memUsages.size()).toFloat().round(1) : 0.0)
    ]
}

String escapeXmlForLog(String input) {
    return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
}

Boolean validIp(String ip) {
    return (ip && ip ==~ /\b(?:\d{1,3}\.){3}\d{1,3}\b/)
}

Boolean validDateFormat(String dateFormat) {
    Boolean response = false
    try {
    	SimpleDateFormat sdf = new SimpleDateFormat(dateFormat)
    	logDebug sdf.format(new Date())
        state.remove('dateFormatInvalid')
        response = true
    } catch (e) { state.dateFormatInvalid = true }
    return response
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${device.displayName} ${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${device.displayName} ${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${device.displayName} ${msg}" } }
private logWarn(msg)  { log.warn   "${device.displayName} ${msg}" }
private logError(msg) { log.error  "${device.displayName} ${msg}" }
