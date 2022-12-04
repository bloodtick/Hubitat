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
*  HubiThings Replica
*
*  Update: Bloodtick Jones
*  Date: 2022-10-01
*
*  1.0.00  2022-12-04 First pass.
*  1.0.01  2022-12-04 Updated to hide PAT as a password
*/

public static String version() {  return "1.0.01"  }
public static String copyright() {"&copy; 2022 ${author()}"}
public static String author() { return "Bloodtick Jones" }

import groovy.json.*
import java.util.*
import java.text.SimpleDateFormat
import java.net.URLEncoder
import hubitat.helper.RMUtils
import com.hubitat.app.ChildDeviceWrapper
import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static final Integer iSmartAppDeviceLimit=20
@Field static final Integer iHttpSuccess=200
@Field static final Integer iHttpError=400
@Field static final Integer iRefreshInterval=0
@Field static final String  sURI="https://api.smartthings.com"
@Field static final String  sOauthURI="https://auth-global.api.smartthings.com"
@Field static final List    lOauthScope=["r:locations:*", "x:locations:*", "r:devices:*", "x:devices:*"]
@Field static final String  sColorDarkBlue="#1A77C9"
@Field static final String  sColorLightGrey="#DDDDDD"
@Field static final String  sColorDarkGrey="#696969"
@Field static final String  sColorDarkRed="DarkRed"
@Field static final String  sCodeRelease="Alpha"
@Field volatile static Map<Long,Integer> g_iRefreshInterval = [:]

definition(
    name: "HubiThings Oauth",
    namespace: "replica",
    author: "bloodtick",
    description: "Hubitat Application to manage SmartThings Oauth",
    category: "Convenience",
    importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/hubiThingsOauth.groovy",
    iconUrl: "",
    iconX2Url: "",
    oauth: [displayName: "HubiThings Oauth", displayLink: ""],
    singleInstance: false
){}

preferences {
    page name:"mainPage"
}

mappings { 
    path("/callback") { action: [ POST: "callback"] }
    path("/oauth/callback") { action: [ GET: "oauthCallback" ] }
}

def installed() {
    state.isInstalled = true
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    logInfo "${app.getLabel()} executing 'initialize()'"
}

def uninstalled() {
    logInfo "${app.getLabel()} executing 'uninstalled()'"    
    if(state.appId) deleteApp(state.appId)
}

public Map getSmartDevices() {
    Long appId = app.getId()
    if(g_mSmartDeviceList[appId]==null) {
        g_mSmartDeviceList[appId]=[:]
        if(state?.installedAppId) {
            refresh()
            Integer count=0
            while(count<20 && g_mSmartDeviceList[appId]==[:] ) { pauseExecution(250); count++ } // wait a max of 5 seconds
        }
    }
    return g_mSmartDeviceList[appId]
}

public Map getSmartRooms() {
    getSmartDevices()
    return g_mSmartRoomList[app.getId()]
}

public Map getSmartLocations() {
    getSmartDevices()
    return g_mSmartLocationList[app.getId()]
}

public Map getSmartSubscriptions() {
    return g_mSmartSubscriptionList[app.getId()] ?: state.subscriptions
}

public void refresh() {
    logInfo "${app.getLabel()} executing refresh"
    // these are async calls and will not block
    getSmartLocationList()
    getSmartSubscriptionList()
}

String getTargetUrl() {
    return "${getApiServerUrl()}/${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}"
}

String getRedirectUri() {
    return "https://cloud.hubitat.com/oauth/stateredirect"
}

String getOauthState() {
    return "${getHubUID()}/apps/${app.id}/oauth/callback?access_token=${state.accessToken}"
}

String getOauthAuthorizeUri() {
    String clientId = state.oauthClientId
    String scope = URLEncoder.encode(lOauthScope?.join(' '), "UTF-8")
    String redirectUri = URLEncoder.encode(getRedirectUri(), "UTF-8")
    String oauthState = URLEncoder.encode(getOauthState(), "UTF-8")         
    return "$sURI/oauth/authorize?client_id=${clientId}&scope=${scope}&response_type=code&redirect_uri=${redirectUri}&state=${oauthState}"
}

def mainPage(){
    if(!state.accessToken) { 
        def install = installHelper()
        if(install) return install
    }

    Integer refreshInterval = g_iRefreshInterval[app.getId()]?:0
    if(state.appId && !state.installedAppId) refreshInterval=5
         
    return dynamicPage(name: "mainPage", install: true, refreshInterval: refreshInterval) {
        displayHeader()
        
        section(menuHeader("${app.getLabel()} Configuration ${refreshInterval?"[Auto Refresh]":""} $sHubitatIconStatic $sSamsungIconStatic")) {
            input(name: "clientSecretPAT", type: "password", title: getFormat("hyperlink","$sSamsungIcon SmartThings Personal Access Token:","https://account.smartthings.com/tokens"), description: "SmartThings Token UUID", width: 6, submitOnChange: true, newLineAfter:true)
            if(state.user=="bloodtick") { input(name: "mainPage::test", type: "button", width: 2, title: "Test", style:"width:75%;") }
        }
        
        if(clientSecretPAT) {
            section(menuHeader("SmartThings API")) {
                if(!state.appId) {
                    input(name: "mainPage::create", type: "button", width: 2, title: "Create", style:"width:75%; color:$sColorDarkBlue; font-weight:bold;")
                    paragraph( getFormat("text", "Select 'Create' to begin initialization of SmartThings API") )
                    if(state.createAppError) paragraph( "SmartThings API ERROR: "+state.createAppError )
                }
                else {
                    input(name: "mainPage::delete", type: "button", width: 2, title: "Delete", style:"width:75%;")                
                
                    paragraph("SmartThings API is ${state.installedAppId ? "configured : select 'Delete' to remove all authorizations" : "available for Oauth configuration : select link below to continue"}")
                    String status  =  "OAuth Client ID: ${state.oauthClientId}\n"
                        status += "OAuth Client Secret: ${state.oauthClientSecret}\n"
                        status += "OAuth Hubitat Callback: ${state.oauthCallback}\n"
                        status += "Installed App ID: ${state.installedAppId ?: "Pending Authorization"}"
                    paragraph(status)
                
                    if(state.installedAppId) {
                        paragraph( getFormat("hyperlink","$sSamsungIcon Click here to refresh SmartThings Oauth Authorization", getOauthAuthorizeUri()) )
                    }
                    else {
                        paragraph( getFormat("hyperlink","$sSamsungIcon 'Click Here' for SmartThings Oauth Authorization and select 'Refresh' when completed", getOauthAuthorizeUri()) )
                        input(name: "mainPage::noop", type: "button", width: 2, title: "Refresh", style:"width:75%; color:$sColorDarkBlue; font-weight:bold;", newLineAfter:true)
                    }
                }
            }
        }
  
        if(state.installedAppId) {
            Map smartDevices = getSmartDevices() // this could block up to five seconds if we don't have devices cached
            section(menuHeader("SmartThings Devices")) {
                if(smartDevices) {
                    List smartDevicesSelect = []
                    smartDevices?.items?.sort{ a,b -> getSmartRoomName(a.roomId) <=> getSmartRoomName(b.roomId) ?: getSmartDeviceName(a.deviceId) <=> getSmartDeviceName(b.deviceId) }?.each {
                        Map device = [ "${it.deviceId}" : "${getSmartRoomName(it.roomId)} : ${getSmartDeviceName(it.deviceId)}" ]
                        smartDevicesSelect.add(device)   
                    }
                    input(name: "mainPageSmartDevices", type: "enum", title: "SmartThings Devices (${mainPageSmartDevices?.size() ?: 0} of max ${iSmartAppDeviceLimit}):", description: "Choose a SmartThings devices", options: smartDevicesSelect, multiple: true, submitOnChange:true, width:6, newLineAfter:true)
                    if(iSmartAppDeviceLimit >=mainPageSmartDevices?.size()) {
                        Map update = checkSmartDeviceSubscriptions()
                        if(update?.ready && !g_iRefreshInterval[app.getId()]) {
                            input(name: "mainPage::configure", type: "button", width: 2, title: "Configure", style:"width:75%; color:$sColorDarkBlue; font-weight:bold;")
                            paragraph( getFormat("text", "Select 'Configure' to update SmartThings device subscriptions") )
                        }
                        else {
                            input(name: "mainPage::noop", type: "button", width: 2, title: "Refresh", style:"width:75%;")
                            g_iRefreshInterval[app.getId()] = 0
                        }
                    }
                    else {
                        paragraph( getFormat("text","Action: Too many SmartThings devices selected. The maximum device count supported is $iSmartAppDeviceLimit per '${app.getLabel()}' instance.") )
                    }                        
                } 
                else {
                     input(name: "mainPage::noop", type: "button", width: 2, title: "Refresh", style:"width:75%;")
                }            
                smartDevicesSection()
            }
        }        
    }
}

def smartDevicesSection(){
    Map update = checkSmartDeviceSubscriptions()
    
    List deviceIds = update?.current ?: []
    deviceIds = update?.select ? update?.select + deviceIds : deviceIds
    deviceIds = update?.delete ? update?.delete + deviceIds : deviceIds    
    List selectDevices = deviceIds?.unique()?.collect{ deviceId -> smartDevices?.items?.find{ it.deviceId==deviceId } }
  
    String smartDeviceList = "<span><table style='width:100%;'>"
    smartDeviceList += "<tr><th>SmartThings Device</th><th>SmartThings Room</th><th style='text-align:center;'>Device Subscription Status</th></tr>"
    selectDevices?.sort{ a,b -> getSmartRoomName(a?.roomId) <=> getSmartRoomName(b?.roomId) ?: getSmartDeviceName(a?.deviceId) <=> getSmartDeviceName(b?.deviceId) }.each { device ->
        String status = (update?.select?.find{it==device.deviceId}) ? "Pending Select" : (update?.delete?.find{it==device.deviceId}) ? "Pending Delete" : "Subscribed"        
        smartDeviceList += "<tr><td>${getSmartDeviceName(device.deviceId)}</td>"
        smartDeviceList += "<td>${getSmartRoomName(device.roomId)}</td>"
        smartDeviceList += "<td style='text-align:center;'>$status</td></tr>"
    }
    smartDeviceList +="</table>"
    
    if (selectDevices?.size()){        
        paragraph( smartDeviceList )
        paragraph("<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>")
    }
}

def installHelper() {
    if(!state?.isInstalled && !state?.accessToken) {        
        return dynamicPage(name: "mainPage", install: true, refreshInterval: 0){
            displayHeader()
            section(menuHeader("Complete Install $sHubitatIconStatic $sSamsungIconStatic")) {
                paragraph("Please complete the install <b>(click done)</b> and then return to $sHubitatIcon SmartApp to continue configuration")
                if(!pageMainPageAppLabel) { app.updateSetting( "pageMainPageAppLabel", app.getLabel()) }
                input(name: "pageMainPageAppLabel", type: "text", title: getFormat("text","$sHubitatIcon Change ${app.getLabel()} SmartApp Name:"), width: 6, default:app.getLabel(), submitOnChange: true, newLineAfter:true)
                if(pageMainPageAppLabel && pageMainPageAppLabel!=app.getLabel()) { app.updateLabel( pageMainPageAppLabel ) }
            }
        }
    }
    if(!state?.accessToken){	
        try { createAccessToken() } catch(e) { logWarn e }	
    }
    if(!state?.accessToken) {        
        return dynamicPage(name: "mainPage", install: true, refreshInterval: 0){
            displayHeader()
            section(menuHeader("Complete OAUTH Install $sHubitatIconStatic $sSamsungIconStatic")) {
                paragraph("Problem with OAUTH installation! Please remove $sHubitatIcon '${app.getLabel()}' and authorize OAUTH in Apps Code source code and reinstall")

            }
        }
    }
    return null
}

void appButtonHandler(String btn) {
    logDebug "${app.getLabel()} executing 'appButtonHandler($btn)'"   
    if(btn.contains("::")) { 
        List items = btn.tokenize("::")
        if(items && items.size() > 1 && items[1]) {
            String k = (String)items[0]
            String v = (String)items[1]
            logTrace "Button [$k] [$v] pressed"
            switch(k) {                
                case "mainPage":                    
                    switch(v) {                       
                        case "test":
                            testButton()
                            break
                        case "noop":
                            break
                        case "configure":
                            g_iRefreshInterval[app.getId()]=5
                            refresh()
                            break
                        case "create":
                            createApp()
                            break
                        case "delete":
                            deleteApp(state.appId)
                            break
                    }                            
                    break                  
                default:
                    logInfo "Not supported"
            }
        }
    }    
}

def callback() {   
    Map response = [statusCode:iHttpSuccess]
    def event = new JsonSlurper().parseText(request.body)
    logDebug "${app.getLabel()} ${event?.messageType}: $event"
    switch(event?.messageType) {
        case 'PING':
            response = [statusCode:200, pingData: [challenge: event?.pingData.challenge]]
		    break;
        case 'CONFIRMATION':
            response = [statusCode:200, targetUrl: getTargetUrl()]
            runIn(2, handleConfirm, [data: event?.confirmationData])
            break;
        case 'EVENT':
            logInfo "${app.getLabel()} ${event?.messageType}: $request.body"
            if(event?.eventData?.events?.find{ it.eventType=="DEVICE_LIFECYCLE_EVENT" }) { runIn(1,refresh) }
            //response = handleEvent(event?.eventData, eventPostTime)
            break;        
        default:
          logWarn "${app.getLabel()} lifecycle ${event?.messageType} not supported"
    }    
    event = null
    
    logDebug "RESPONSE: ${JsonOutput.toJson(response)}"
    return render(status:response.statusCode, data:JsonOutput.toJson(response))    
}

Map handleConfirm(Map confirmationData) {
    logDebug "${app.getLabel()} executing 'handleConfirm()' url:${confirmationData?.confirmationUrl}"
    Map response = [statusCode:iHttpError]
    
    try {
        httpGet(confirmationData?.confirmationUrl) { resp ->
            logDebug "response data: ${resp?.data}"
            if (resp?.data?.targetUrl == getTargetUrl()) {
                logInfo "${app.getLabel()} callback confirmation success"
                state.oauthCallback = "CONFIRMED"
            }
            else {
                logWarn "${app.getLabel()} callback confirmation failure with url:${resp?.data?.targetUrl}"
                state.oauthCallback = "ERROR"
            }
            response.statusCode = resp.status
            response['targetUrl'] = resp.data.targetUrl
        }        
    } catch (e) {
        logWarn "${app.getLabel()} handleConfirm() error: $e"     
    }
    return response
}

Map checkSmartDeviceSubscriptions() {
    List currentIds = getSmartSubscriptions()?.items?.each{ it.sourceType=="DEVICE" }?.device?.deviceId
    List selectIds = mainPageSmartDevices?.clone()
    List deleteIds = currentIds?.clone()
    if(selectIds) { deleteIds?.intersect(selectIds)?.each{ deleteIds?.remove(it); selectIds?.remove(it) } }
    Boolean ready = (selectIds?.size() || deleteIds?.size())
    
    return ([current:currentIds, select:(selectIds?:[]), delete:(deleteIds?:[]), ready:ready])
}

void setSmartSubscriptions() {
    if(getSmartSubscriptions()?.items?.find{ it.sourceType=="DEVICE_HEALTH" }==null)    
        setSmartHealthSubscription()
    if(getSmartSubscriptions()?.items?.find{ it.sourceType=="DEVICE_LIFECYCLE" }==null)    
        setSmartLifecycleSubscription()
    if(getSmartSubscriptions()?.items?.find{ it.sourceType=="MODE" }==null)    
        setSmartModeSubscription()
    
    Map update = checkSmartDeviceSubscriptions()    
    update?.select?.each{ deviceId ->
        logDebug "${app.getLabel()} subscribed to $deviceId"
        setSmartDeviceSubscription(deviceId)
    }
    update?.delete?.each{ deviceId ->
        logDebug "${app.getLabel()} unsubscribe to $deviceId"
        deleteSmartSubscriptions(deviceId)
    }
    if(update?.select?.size() || update?.delete?.size()) { runIn(2, getSmartSubscriptionList) }
}

Map deleteSmartSubscriptions(String deviceId) {
    logDebug "${app.getLabel()} executing 'deleteSmartSubscriptions($deviceId)'"
    Map response = [statusCode:iHttpError]
    String subscriptionId = getSmartSubscriptions()?.items?.find{ it.sourceType=="DEVICE" && it.device.deviceId==deviceId }?.id

    Map params = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions/$subscriptionId",
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpDelete(params) { resp ->
            logInfo "${app.getLabel()} '${getSmartDeviceName(deviceId)}' delete subscription status:${resp.status}"
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "${app.getLabel()} deleteSmartSubscriptions($deviceId) error: $e"
    }    
    return response
}

Map setSmartDeviceSubscription(String deviceId) {
    logDebug "${app.getLabel()} executing 'setSmartDeviceSubscription($deviceId)'"
    Map response = [statusCode:iHttpError]
    
    Map subscription = [ sourceType: "DEVICE", device: [ deviceId: deviceId, componentId: "main", capability: "*", attribute: "*", stateChangeOnly: true, subscriptionName: deviceId, value: "*" ]]    
    Map data = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",        
        body: JsonOutput.toJson(subscription),
        method: "setSmartDeviceSubscription",
        deviceId: deviceId       
    ]
    response.statusCode = asyncHttpPostJson("asyncHttpPostCallback", data).statusCode    
    return response
}

Map setSmartHealthSubscription() {
    logDebug "${app.getLabel()} executing 'setSmartHealthSubscription()'"
    Map response = [statusCode:iHttpError]
    
    Map health = [ sourceType: "DEVICE_HEALTH", deviceHealth: [ locationId: state?.locationId, subscriptionName: state?.locationId ]]
    Map data = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",
        body: JsonOutput.toJson(health),
        method: "setSmartHealthSubscription",       
    ]
    response.statusCode = asyncHttpPostJson("asyncHttpPostCallback", data).statusCode    
    return response
}

Map setSmartLifecycleSubscription() {
    logDebug "${app.getLabel()} executing 'setSmartHealthSubscription()'"
    Map response = [statusCode:iHttpError]
    
    Map health = [ sourceType: "DEVICE_LIFECYCLE", deviceLifecycle: [ locationId: state?.locationId, subscriptionName: state?.locationId ]]
    Map data = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",
        body: JsonOutput.toJson(health),
        method: "setSmartLifecycleSubscription",       
    ]
    response.statusCode = asyncHttpPostJson("asyncHttpPostCallback", data).statusCode    
    return response
}

Map setSmartModeSubscription() {
    logDebug "${app.getLabel()} executing 'setSmartModeSubscription()'"
    Map response = [statusCode:iHttpError]
    
    Map mode = [ sourceType: "MODE", mode: [ locationId: state?.locationId, subscriptionName: state?.locationId ]]
    Map data = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",
        body: JsonOutput.toJson(mode),
        method: "setSmartModeSubscription",        
    ]
    response.statusCode = asyncHttpPostJson("asyncHttpPostCallback", data).statusCode    
    return response
}

private Map asyncHttpPostJson(String callbackMethod, Map data) {
    logDebug "${app.getLabel()} executing 'asyncHttpPostJson()'"
    Map response = [statusCode:iHttpError]
	
    Map params = [
	    uri: data.uri,
	    path: data.path,
        body: data.body,
        contentType: "application/json",
        requestContentType: "application/json",
		headers: [ Authorization: "Bearer ${state.authToken}" ]
    ]
	try {
	    asynchttpPost(callbackMethod, params, data)
        response.statusCode = iHttpSuccess
	} catch (e) {
	    logWarn "${app.getLabel()} asyncHttpPostJson error: $e"
	}    
    return response
}

void asyncHttpPostCallback(resp, data) {
    logDebug "${app.getLabel()} executing 'asyncHttpPostCallback()' status: ${resp.status} method: ${data?.method}"
    
    if(resp.status==iHttpSuccess) {
        resp.headers.each { logDebug "${it.key} : ${it.value}" }
        logDebug "response data: ${resp.data}"
        
        switch(data?.method) {
            case "setSmartDeviceCommand":
                Map command = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} successful ${data?.method}:${command}"
                break
            case "setSmartDeviceSubscription":
                Map deviceSubscription = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} ${data?.method}: ${deviceSubscription}"
                logInfo "${app.getLabel()} '${getSmartDeviceName(data?.deviceId)}' DEVICE subscription status:${resp.status}"
                break            
            case "setSmartHealthSubscription":
                Map healthSubscription = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} ${data?.method}: ${healthSubscription}"
                logInfo "${app.getLabel()} HEALTH subscription status:${resp.status}"
                break
            case "setSmartLifecycleSubscription":
                Map deviceLifecycle = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} ${data?.method}: ${deviceLifecycle}"
                logInfo "${app.getLabel()} DEVICE_LIFECYCLE subscription status:${resp.status}"
                break
            case "setSmartModeSubscription":
                Map modeSubscription = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} ${data?.method}: ${modeSubscription}"
                logInfo "${app.getLabel()} MODE subscription status:${resp.status}"
                break
            default:
                logWarn "${app.getLabel()} asyncHttpPostCallback ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logWarn("${app.getLabel()} asyncHttpPostCallback ${data?.method} status:${resp.status} reason:${resp.errorMessage}")
    }
}

def oauthCallback() {
    logDebug "${app.getLabel()} oauthCallback() $params"   
    String code = params.code
    String client_id = state.oauthClientId
    String client_secret = state.oauthClientSecret
    String redirect_uri = getRedirectUri()
       
    Map params = [
        uri: sOauthURI,
        path: "/oauth/token", 
        query: [ grant_type:"authorization_code", code:code, client_id:client_id, redirect_uri:redirect_uri ],
        contentType: "application/x-www-form-urlencoded",
        requestContentType: "application/json",
		headers: [ Authorization: "Basic ${("${client_id}:${client_secret}").bytes.encodeBase64().toString()}" ]
    ]

    try { 
        httpPost(params) { resp ->
            if (resp && resp.data && resp.success) {
                String respStr = resp.data.toString().replace("[{","{").replace("}:null]","}")
                Map respStrJson = new JsonSlurper().parseText(respStr)
                state.installedAppId = respStrJson.installed_app_id
                state.authToken = respStrJson.access_token
                state.refreshToken = respStrJson.refresh_token
                state.authTokenExpires = (now() + (respStrJson.expires_in * 1000))
                runIn(1,startState)
            }
        }
    }
    catch (e) {
        logWarn "${app.getLabel()} oauthCallback() error: $e"
    }

	if (state.authToken)
        return render(status:iHttpSuccess, contentType: 'text/html', data: """<p>Your SmartThings Account is now connected to Hubitat</p><p>Close this window and press refresh continue setup.</p>""")
	else
        return render(status:iHttpError, contentType: 'text/html', data: """<p>The SmartThings connection could not be established!</p><p>Close this window to try again.</p>""")
}

Map oauthRefresh() {
    logDebug "${app.getLabel()} executing 'oauthRefresh()'"
    Map response = [statusCode:iHttpError]
    
    String refresh_token = state.refreshToken
    String client_id = state.oauthClientId
    String client_secret = state.oauthClientSecret
  
    Map params = [
        uri: sOauthURI,
        path: "/oauth/token",  
        query: [ grant_type:"refresh_token", client_id:client_id, refresh_token:refresh_token ],
        contentType: "application/x-www-form-urlencoded",
        requestContentType: "application/json",
		headers: [ Authorization: "Basic ${("${client_id}:${client_secret}").bytes.encodeBase64().toString()}" ]
    ] 
    
    try {
        httpPost(params) { resp ->
            // strange json'y response. this works good enough to solve. 
            String respStr = resp.data.toString().replace("[{","{").replace("}:null]","}")
            Map respStrJson = new JsonSlurper().parseText(respStr)
            state.installedAppId = respStrJson.installed_app_id
            state.authToken = respStrJson.access_token
            state.refreshToken = respStrJson.refresh_token
            state.authTokenExpires = (now() + (respStrJson.expires_in * 1000))
            response.statusCode = resp.status
            logInfo "${app.getLabel()} updated authorization token"
         }
    } catch (e) {
        logWarn "${app.getLabel()} oauthRefresh() error: $e"
        state.authTokenExpires = 0
        runIn(1*60*60, oauthRefresh) // problem. lets try every hour.
    }
    
    return [statusCode:response.statusCode]
}

@Field volatile static Map<Long,Map> g_mSmartSubscriptionList = [:]
Map getSmartSubscriptionList() {
    logDebug "${app.getLabel()} executing 'getSmartSubscriptionList()'"
    Map response = [statusCode:iHttpError]   
    Map data = [ uri: sURI, path: "/installedapps/${state.installedAppId}/subscriptions", method: "getSmartSubscriptionList"]
    response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

@Field volatile static Map<Long,Map> g_mSmartDeviceList = [:]
Map getSmartDeviceList() {
    logDebug "${app.getLabel()} executing 'getSmartDeviceList()'"
    Map response = [statusCode:iHttpError]   
	Map data = [ uri: sURI, path: "/devices", method: "getSmartDeviceList"]
    response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}
String getSmartDeviceName(String deviceId) {
    Map device = g_mSmartDeviceList[app.getId()] ? g_mSmartDeviceList[app.getId()]?.items?.find{ it.deviceId==deviceId } ?: [label:"Name Not Defined"] : [label:deviceId]
    return (device?.label ?: device?.name) 
}

@Field volatile static Map<Long,Map> g_mSmartRoomList = [:]
Map getSmartRoomList() {
    logDebug "${app.getLabel()} executing 'getSmartRoomList($locationId)'"
    Map response = [statusCode:iHttpError]   
    Map data = [ uri: sURI, path: "/locations/${state.locationId}/rooms", method: "getSmartRoomList" ]
    response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}
String getSmartRoomName(String roomId) {
    return g_mSmartRoomList[app.getId()] ? g_mSmartRoomList[app.getId()]?.items?.find{ it.roomId==roomId }?.name ?: "Room Not Defined" : roomId
}

@Field volatile static Map<Long,Map> g_mSmartLocationList = [:]
Map getSmartLocationList() {
    logDebug "${app.getLabel()} executing 'getSmartLocationList()'"
    Map response = [statusCode:iHttpError]   
	Map data = [ uri: sURI, path: "/locations", method: "getSmartLocationList" ]
    response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}
String getSmartLocationName(String locationId) {
    return g_mSmartLocationList[app.getId()] ? g_mSmartLocationList[app.getId()]?.items?.find{ it.locationId==locationId }?.name ?: "Location Not Defined" : locationId
}
    
private Map asyncHttpGet(String callbackMethod, Map data) {
    logDebug "${app.getLabel()} executing 'asyncHttpGet()'"
    Map response = [statusCode:iHttpError]
	
    Map params = [
	    uri: data.uri,
	    path: data.path,
		headers: [ Authorization: "Bearer ${state?.authToken}" ]
    ]
	try {
	    asynchttpGet(callbackMethod, params, data)
        response.statusCode = iHttpSuccess
	} catch (e) {
	    logWarn "${app.getLabel()} asyncHttpGet error: $e"
	}
    return response
}

void asyncHttpGetCallback(resp, data) {
    logDebug "${app.getLabel()} executing 'asyncHttpGetCallback()' status: ${resp.status} method: ${data?.method}"
    
    if (resp.status == iHttpSuccess) {       
        switch(data?.method) {
            case "getSmartSubscriptionList":            
                Map subscriptionList = new JsonSlurper().parseText(resp.data)
                if(!(subscriptionList?.sort()?.equals(g_mSmartSubscriptionList[app.getId()]?.sort()))) {
                    state.subscriptions = g_mSmartSubscriptionList[app.getId()] = subscriptionList
                    logInfo "${app.getLabel()} updated subscription list"
                }
                setSmartSubscriptions()
                break
            case "getSmartDeviceList":            
                Map deviceList = new JsonSlurper().parseText(resp.data)
                if(!(deviceList?.sort()?.equals(g_mSmartDeviceList[app.getId()]?.sort()))) {                    
                    g_mSmartDeviceList[app.getId()] = deviceList
                    logInfo "${app.getLabel()} updated device list"
                }
                break
            case "getSmartRoomList":            
                Map roomList = new JsonSlurper().parseText(resp.data)
                if(!(roomList?.sort()?.equals(g_mSmartRoomList[app.getId()]?.sort()))) {                    
                    g_mSmartRoomList[app.getId()] = roomList
                    logInfo "${app.getLabel()} updated room list"
                }
                getSmartDeviceList()
                break
            case "getSmartLocationList":            
                Map locationList = new JsonSlurper().parseText(resp.data)
                if(!locationList?.equals(g_mSmartLocationList[app.getId()])) {                     
                    g_mSmartLocationList[app.getId()] = locationList
                    state.locationId = locationList?.items?.collect{ it.locationId }?.unique()?.getAt(0)
                    logInfo "${app.getLabel()} updated location list"
                }
                getSmartRoomList()
                break
            default:
                logWarn "${app.getLabel()} asyncHttpGetCallback ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logTrace "response data: ${resp.data}"
    }
    else {
        logWarn("${app.getLabel()} asyncHttpGetCallback ${data?.method} status:${resp.status} reason:${resp.errorMessage}")
        runIn(15*60, data?.method)
    }
}

void clearState() {                
    state.remove('appId')                   
    state.remove('authToken')
    state.remove('authTokenExpires')
    state.remove('installedAppId')
    state.remove('locationId')
    state.remove('oauthCallback')
    state.remove('oauthClientId')
    state.remove('oauthClientSecret')
    state.remove('refreshToken') 
    state.remove('subscriptions') 
    unschedule()
}

void startState() {
    refresh()    
    String crontab = "${Calendar.instance[ Calendar.SECOND ]} ${Calendar.instance[ Calendar.MINUTE ]} */12 * * ?" // every 12 hours from midnight+min+sec
    schedule(crontab, 'oauthRefresh') // tokens are good for 24 hours
}

void testButton() {  
    def appId = '42639b20-71a8-4288-944e-c9c32a15303b'
    
    //oauthRefresh()    
    //createApp()
    //createAppSecret(appId)
    //getApp(appId)
    //updateApp(appId)
    //deleteApp(appId)
    
    listApps()
    //listInstalledApps()
    
    logInfo clientSecretPAT
    return
}

def createApp() {
    logInfo "${app.getLabel()} creating SmartThings API"
    def response = [statusCode:iHttpError]
    
    String displayName = app.getLabel()
    def app = [
        appName: "hubithings-${UUID.randomUUID().toString()}",
        displayName: displayName,
        description: "SmartThings Service to connect with Hubitat",
        iconImage: [ url:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/icon/replica.png" ],
        appType: "API_ONLY",
        classifications: ["CONNECTED_SERVICE"],
        singleInstance: true,
        apiOnly: [targetUrl:getTargetUrl()],
        oauth: [
            clientName: "HubiThings Replica Oauth",
            scope: lOauthScope,
            redirectUris: [getRedirectUri()]
        ]
    ]
    
    def params = [
        uri: sURI,
        path: "/apps",
        body: JsonOutput.toJson(app),
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]        
    ]

    try {
        httpPostJson(params) { resp ->
            if(resp.status==200) {                
                logDebug "createApp() response data: ${JsonOutput.toJson(resp.data)}"
                state.appId = resp.data.app.appId
                state.oauthClientId = resp.data.oauthClientId
                state.oauthClientSecret = resp.data.oauthClientSecret
                state.oauthCallback = resp.data.app?.apiOnly?.subscription?.targetStatus
                state.remove('createAppError')
            }
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "createApp() error: $e"
        state.createAppError = e.toString()
    }
    return [statusCode:response.statusCode]
}

def deleteApp(appNameOrId) {
    logDebug "executing 'deleteApp($appNameOrId)'"
    def response = [statusCode:iHttpError]
    
    def params = [
        uri: sURI,
        path: "/apps/$appNameOrId",
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]        
    ]
    try {
        httpDelete(params) { resp ->   
            logDebug "deleteApp() response data: ${JsonOutput.toJson(resp.data)}"
            if(resp.status==200 && state.appId==appNameOrId) {
                logInfo "${app.getLabel()} successfully deleted SmartThings API"
                clearState()
            }
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "deleteApp() error: $e"
    }    
    return response
}

def getApp(appNameOrId) {
    logInfo "executing 'getApp($appNameOrId)'"
	def params = [
        uri: sURI,
		path: "/apps/$appNameOrId",
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]
		]    
    def data = [method:"getApp"]
	try {
	    asynchttpGet("appCallback", params, data)
	} catch (e) {
	    logWarn "getApp() error: $e"
	}
}

def getInstalledApp(installedAppId) {
    logInfo "executing 'getInstalledApp($installedAppId)'"
	def params = [
        uri: sURI,
		path: "installedapps/$installedAppId",
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]
		]    
    def data = [method:"getInstalledApp"]
	try {
	    asynchttpGet("appCallback", params, data)
	} catch (e) {
	    logWarn "getInstalledApp() error: $e"
	}
}

def listApps() {
    logInfo "executing 'listApps()'"
	def params = [
        uri: sURI,
		path: "/apps",
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]
		]    
    def data = [method:"listApps"]
	try {
	    asynchttpGet("appCallback", params, data)
	} catch (e) {
	    logWarn "listApps() error: $e"
	}
}

def listInstalledApps() {
    logInfo "executing 'listInstalledApps()'"
	def params = [
        uri: sURI,
        path: "/installedapps",
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]
		]    
    def data = [method:"listInstalledApps"]
	try {
	    asynchttpGet("appCallback", params, data)
	} catch (e) {
	    logWarn "listInstalledApps() error: $e"
	}
}

def appCallback(resp, data) {
    logInfo "executing 'appCallback()' status: ${resp.status} method: ${data?.method}"
    if(resp.status==200) logInfo "response data: ${resp?.data}"      
}


@Field static final String sSamsungIconStatic="""<svg style="display:none" version="2.0"><defs><symbol id="samsung-svg" viewbox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
<path fill="currentColor" d="m24.016 0.19927c-6.549 0-13.371 1.1088-18.203 5.5609-4.5279 5.1206-5.5614 11.913-5.5614 18.234v1.9451c0 6.5338 1.1094 13.417 5.5614 18.248 5.1509 4.5586 11.973 5.5614 18.324 5.5614h1.7622c6.5641 0 13.478-1.0942 18.34-5.5614 4.5586-5.1509 5.5614-11.988 5.5614-18.339v-1.7632c0-6.5638-1.0942-13.478-5.5614-18.324-5.1202-4.5431-11.897-5.5609-18.218-5.5609zm1.0077 7.2347c2.5689 0 4.6591 2.0435 4.6591 4.5553 0 2.1999-1.4161 4.0808-3.5398 4.5041v3.2794c3.0529 0.44309 5.2173 2.9536 5.2173 6.0591 0 3.416-2.8427 6.1945-6.3366 6.1945-3.4939 0-6.336-2.7785-6.336-6.1945 0-0.35031 0.03655-0.69134 0.09405-1.0258l-3.1724-1.0072c-0.81421 1.41-2.344 2.3115-4.0494 2.3115-0.4886 0-0.97399-0.0758-1.4418-0.22428-2.4433-0.77611-3.7851-3.3512-2.991-5.7402 0.62583-1.8828 2.406-3.1481 4.4302-3.1481 0.48824 0 0.97279 0.0754 1.4402 0.22427 1.1836 0.37606 2.1472 1.1805 2.712 2.265 0.42122 0.80821 0.58276 1.6995 0.47904 2.5797l3.1698 1.0072c0.90699-1.7734 2.8428-2.9998 4.9206-3.3011v-3.2794c-2.1237-0.42334-3.9145-2.3042-3.9145-4.5041 0-2.5118 2.0899-4.5553 4.6591-4.5553zm0 1.8221c-1.5413 0-2.7952 1.2261-2.7952 2.7332s1.2539 2.7326 2.7952 2.7326c1.5416 0 2.7957-1.2256 2.7957-2.7326s-1.2541-2.7332-2.7957-2.7332zm13.467 7.7417c2.0235 0 3.804 1.2655 4.4302 3.1486 0.38418 1.1568 0.28436 2.3906-0.28008 3.4747-0.5655 1.0844-1.5286 1.8889-2.7125 2.265-0.46708 0.14852-0.95146 0.22376-1.4397 0.22376-1.7053 0-3.2355-0.90075-4.0504-2.3104l-1.309 0.41651-0.57619-1.7332 1.3064-0.41496c-0.24412-2.1061 1.0506-4.1659 3.1905-4.8457 0.46814-0.14887 0.95285-0.22427 1.4407-0.22427zm-26.933 1.8221c-1.2143 0-2.2825 0.75934-2.6582 1.8893-0.47625 1.4333 0.32893 2.9781 1.7947 3.4437 0.28152 0.0896 0.57278 0.13539 0.86558 0.13539 1.2139 0 2.2818-0.75986 2.6572-1.8898 0.23107-0.69391 0.17072-1.4341-0.16795-2.0846-0.33937-0.65087-0.91663-1.1333-1.6268-1.3591-0.28152-0.0892-0.57209-0.13487-0.86455-0.13487zm26.933 0c-0.29245 0-0.58355 0.0456-0.86506 0.13487-1.4658 0.46567-2.2715 2.0109-1.7952 3.4442 0.37571 1.13 1.444 1.8888 2.6582 1.8888 0.2921 0 0.58287-0.0456 0.86403-0.13487 0.71085-0.22542 1.2883-0.7077 1.6273-1.3586 0.33867-0.65052 0.39867-1.3911 0.16795-2.0846-0.37571-1.1303-1.4436-1.8898-2.6572-1.8898zm-13.467 3.0034c-2.261 0-4.1 1.7982-4.1 4.008 0 2.2105 1.8391 4.0085 4.1 4.0085 2.2606 0 4.1-1.798 4.1-4.0085 0-2.2098-1.8394-4.008-4.1-4.008zm-5.5805 9.9746 1.509 1.0712-0.8077 1.0873c1.4651 1.5642 1.6559 3.9761 0.33228 5.7573-0.87489 1.1769-2.2862 1.879-3.775 1.879-0.98884 0-1.9356-0.30066-2.7378-0.87075-2.0796-1.4774-2.5419-4.3338-1.0309-6.3665 0.87418-1.1769 2.2852-1.8795 3.775-1.8795 0.67275 0 1.3247 0.14236 1.9265 0.41083zm11.166 0 0.80822 1.0878c0.60148-0.26846 1.2527-0.41135 1.9255-0.41135 1.488 0 2.8985 0.70228 3.7724 1.8784 1.5099 2.0327 1.0474 4.8869-1.0304 6.3629-0.80116 0.56903-1.7473 0.86972-2.7358 0.86972-1.4891 0-2.8986-0.70212-3.7724-1.8779-1.3222-1.7787-1.1316-4.1886 0.33176-5.7521l-0.80719-1.0862zm2.7337 2.4986c-0.59196 0-1.1587 0.18096-1.6402 0.52245-1.2467 0.88618-1.524 2.599-0.61805 3.8179 0.52388 0.70556 1.3702 1.1265 2.2645 1.1265 0.59196 0 1.1597-0.18063 1.6402-0.52141 1.2471-0.88583 1.5241-2.5992 0.61857-3.8184-0.52458-0.7059-1.3714-1.1271-2.265-1.1271zm-16.635 3e-3c-0.89464 0-1.7419 0.42116-2.2665 1.1271-0.90629 1.2203-0.62869 2.9339 0.61908 3.8204 0.48119 0.3422 1.0489 0.52245 1.6412 0.52245 0.89394 0 1.7414-0.42132 2.266-1.1276 0.90664-1.2203 0.62956-2.9339-0.61857-3.8204-0.48084-0.34184-1.0482-0.52193-1.6412-0.52193z">
</path></symbol></defs><use href="#samsung-svg"/></svg>"""
@Field static final String sSamsungIcon="""<svg width="1.0em" height="1.0em" version="2.0"><use href="#samsung-svg"/></svg>"""

@Field static final String sHubitatIconStatic="""<svg style="display:none" version="2.0"><defs><symbol id="hubitat-svg" viewbox="0 0 130 130" xmlns="http://www.w3.org/2000/svg">
<path fill="currentColor" d="m40.592 3.6222c-1.4074 0.031312-2.9567 0.66796-4.1641 1.0723-1.6729 0.56186-3.2257 1.44-4.7793 2.2676-3.2391 1.7251-6.4448 4.0107-9.2188 6.4102-5.7396 4.9663-10.707 10.694-14.391 17.355-4.2146 7.6219-6.8328 16.011-7.6641 24.684-0.79583 8.314 0.58216 17.529 3.1426 25.533 3.5646 11.143 11.782 29.281 37.533 40.475 19.111 8.3084 40.065 4.959 54.133-2.2676 14.629-7.512 26.684-21.062 31.73-36.793 0.0106-0.0032 0.0576-0.1198 0.21484-0.63086 2.8705-9.9146 3.2267-15.26 2.3106-24.543-0.9192-10.232-3.4611-15.992-5.5606-20.678-1.9937-4.452-4.4114-9.2086-7.666-12.887-0.40521 0.33078 2.6483 3.3871 2.5488 4.123-0.43226-0.01825-1.1165-1.4416-1.3613-1.7734-0.2244-0.30414-0.72566-0.51094-0.8418-0.70898-0.79947-1.3631-2.0565-2.6974-3.1289-3.8594-0.37815 0.13803 0.33559 0.54626 0.16211 0.63281 0.012-0.0063-2.4119-2.5439-2.5723-2.7012-0.13547 0.14321 3.1729 3.8723 3.5391 4.3359 0.29373 0.37652 2.8795 3.439 2.5879 3.7637-0.4136-0.87923-6.1644-7.4554-7.1523-8.3887-0.41147-0.38906-1.8488-2.041-2.3926-1.9238 0.55306 0.70934 1.2317 1.3052 1.8516 1.9531 0.3208 0.33294 0.6153 0.93792 0.99609 1.1836-0.25839 0.07347-1.0788-0.87933-1.3125-1.1016-0.31-0.29628-1.9434-2.1659-2.3633-1.9883-0.16866 0.47652 3.3778 3.5548 3.8008 4.0625 0.61361 0.73962 1.8546 1.6129 2.1582 2.6113-1.2009-0.85361-5.7741-6.1246-7.1699-7.334-1.376-1.189-2.7999-2.367-4.3223-3.3633-0.4896-0.32077-3.3759-2.5313-3.8535-2.2285 1.0765 1.0959 2.8324 1.9251 4.0996 2.8496 1.45 1.0588 2.8712 2.1607 4.2129 3.3555 1.5307 1.364 2.9504 2.8516 4.2852 4.4062 0.97187 1.1312 2.5503 2.58 3.1035 3.9707-0.39694-0.17598-0.66443-0.63092-0.93165-0.94727-0.62293-0.73652-4.6414-5.0809-6.1367-6.4043-2.8256-2.4991-5.9318-4.9751-9.2578-6.7734-0.02814 0.63798 3.288 2.291 3.8984 2.752 1.2073 0.912 2.5721 1.8036 3.5918 2.9297-0.28027-0.13077-1.1122-0.95164-1.3242-0.75586 0.522 0.53439 1.1364 0.99056 1.6894 1.4941 2.5532 2.3251 4.4689 4.4836 6.6465 7.2227 2.5588 3.2177 4.8833 6.6804 6.6562 10.395 3.7249 7.8109 5.6608 15.932 6.0742 24.561 0.19746 3.9968-0.15858 8.5023-1.1758 12.381-1.0656 4.0678-2.0254 8.1482-3.8144 11.98-3.3724 7.224-8.0298 14.858-14.357 19.869-3.2312 2.5577-6.313 5.0111-9.9453 7.0059-3.8025 2.088-7.8317 3.8475-11.986 5.1074-8.2923 2.5162-17.204 3.3879-25.764 1.6504-4.2604-0.86669-8.4568-1.709-12.484-3.4121-3.8161-1.6151-7.443-3.6428-10.871-5.9668-6.7484-4.5781-12.572-10.857-16.619-17.937-8.736-15.276-10.322-33.882-3.6016-50.25 3.0911-7.5292 7.8795-14.31 13.855-19.828 2.9855-2.7552 6.2349-5.2298 9.7109-7.3359 0.99693-0.6048 11.073-5.5999 10.926-6.0332-0.02293-0.06811-1.5626-0.48959-1.8184-0.40039-0.46573 0.16146-6.9184 3.3332-9.6758 4.7461 1.5713-0.80572 3.0907-1.7893 4.6152-2.6855 0.30613-0.18028 4.3375-2.776 4.3828-2.7363-0.54226-0.49213-2.6009 0.74097-3.0176 0.94922 0.6172-0.31147 1.2072-0.70483 1.7988-1.0586 0.2984-0.17866 0.63032-0.34222 0.89844-0.56445 0.01561-0.01251-0.13137-0.61428-0.24805-0.5625-1.0969 0.49157-11.565 6.1743-15.629 8.6133 1.1224-0.65412 2.1609-1.5684 3.2754-2.2617 4.1839-2.6052 8.5841-4.8702 12.996-7.0566-0.45467 0.22492-4.8364 1.6149-4.9629 1.3848 0.01506 0.027061 3.3272-1.5054 3.3359-1.4902-0.29534-0.53707-6.1584 1.9871-6.8887 2.1953 0.07453 0.029216 2.2283-1.2006 2.5059-1.3242 0.364-0.16256 2.1408-1.2509 2.498-1.125-0.4199-0.15493-0.87267-0.21161-1.3418-0.20117zm48.314 2.3438c0.94267 0.38642 1.7734 0.88288 2.6953 1.2344 0.33693 0.1276 0.75174 0.22808 0.9668 0.58789 0.0516 0.08443 1.196 0.49155 1.2559 0.47266 0.22401-0.08088 0.39012 0.04834 0.5625 0.12695 1.2395 0.5692 4.0458 2.2936 4.3203 2.3926 0-0.06057 0.0062-0.12098 0.0078-0.17969-0.0932-0.11528-0.22544-0.17939-0.3457-0.25195-2.8161-1.7344-5.8256-3.0624-8.8984-4.2676-0.16826-0.065764-0.33526-0.14925-0.56445-0.11523zm5.3203 1.4473c0.90934 0.47293 1.8178 0.94504 2.7246 1.418 0.01398-0.02494 0.02741-0.05349 0.04102-0.07617-0.8792-0.53027-1.7814-1.016-2.7656-1.3418zm8.166 4.0059c0.66258 0.48385 4.5548 3.4835 5.7637 4.6035 0.0745 0.06917 0.17375 0.22194 0.27735 0.10742 0.0995-0.11263-0.0594-0.19835-0.13867-0.26562-0.38014-0.32451-3.463-2.886-4.6641-3.7891-0.37487-0.27889-0.76159-0.54788-1.2383-0.65625zm5.748 2.5527c-0.016 0.01678-0.0358 0.03613-0.0547 0.05469 0.9068 0.84839 4.0619 3.8602 4.7363 4.5176 0.0219-0.0257 0.0438-0.04776 0.0684-0.07422-0.064-0.14778-0.19091-0.24592-0.30078-0.35742-0.5292-0.54055-1.0509-1.0914-1.5977-1.6133-0.9188-0.88029-1.8485-1.7429-2.8516-2.5273zm2.6738 0.80273c-0.0219 0.01587-0.0413 0.03579-0.0625 0.05469 0.0235 0.03651 2.7538 2.7734 4.0371 4.1641 0.0537 0.05632 0.0977 0.13753 0.22265 0.10352 0-0.1081-0.0765-0.17491-0.14062-0.24219-1.2364-1.3588-2.4809-2.704-3.8496-3.9316-0.0605-0.05632-0.13462-0.09855-0.20703-0.14844zm-0.10742 1.0605c-0.0149-0.0083-0.0315-0.0064-0.0469 0.01172-0.0344 0.03402 4e-3 0.06074 0.0391 0.07812 0.0276 0.03024 1.0764 1.1106 1.1035 1.1387 0.3724 0.37493 0.7455 0.74812 1.1152 1.123 0.0287 0.02646 2.1856 2.416 2.1856 2.416 0.0515 0.04989 0.10661 0.09937 0.1582 0.15039 0.0219-0.05783 0.0591-0.09993 0.11914-0.12109-0.64279-0.82133-1.37-1.5658-2.123-2.2871 0 0-0.10642-0.10818-0.17578-0.17578-0.0231-0.02254-0.0522-0.05557-0.0586-0.05859v-2e-3c-0.33441-0.4292-0.70386-0.82018-1.1445-1.1426-2e-3 -2e-3 -0.0713-0.06788-0.084-0.08008-0.15961-0.15308-1.0195-0.99002-1.0469-1.0117-0.0121-0.01482-0.0265-0.03094-0.041-0.03906zm-44.055 2.5215c-0.05185-0.01091-0.09832-0.0095-0.13672 0.0098-0.786 0.39281-23.224 19.66-26.447 22.463-1.6385 1.4245-3.2613 2.8666-4.5801 4.6016-0.46866 0.61455-1.1188 1.6068-0.8125 2.4219 0.23534 0.62865 0.59362 1.5561 0.9707 2.1191 0.52293 0.77813 0.3716 1.8222 1.6055 1.3613 0.86614-0.32349 6.0136-3.4432 7.7266-4.6094 3.7323-2.5416 17.33-13.594 20.539-16.408 0.27453-0.24216 0.77888-0.97611 1.1914-0.60156 0.31867 0.28694 0.66743 0.54869 0.99609 0.82422 0.82973 0.69267 21.763 17.29 23.613 18.781 0.53174 0.42973 1.9878 1.1834 1.9961 1.9512 0.01107 1.0735 0.12435 21.487 0.04102 30.623-0.0088 1.0147-3.125 0.98206-4.1875 0.98047-2.1297-3e-3 -11.14 0.13748-13.516 0.16016-2.0917 0.01825-4.1537 0.32392-6.2402 0.4082-4.3443 0.17654-21.704 1.4142-22.695 1.5352-0.64533 0.0796-1.7726-0.04032-2.2793 0.45703 1.5572 0.01701 3.1126 0.03774 4.6699 0.05664-2.2147 0.59002-4.8056 0.51081-7.0879 0.68164-2.4401 0.18123-4.9662 0.2512-7.3594 0.79492 0.7932 0.26192 1.6308 0.1482 2.459 0.22266 0.59586 0.048-0.23716 0.45396-0.28516 0.54883 0.18494 0.02268 0.35214 0.09094 0.5 0.20508-0.42867 0.3157-1.1119 0.1143-1.4473 0.56641 0.50733 0.22613 5.8137-0.19418 5.8262 0.52344-0.3244 0.1251-0.69258 0.02285-0.98633 0.25 1.0296 0.04346 2.0183 0.22877 3.0605 0.23633 0.40253 0 1.1166 0.05761 1.4863 0.17969 0.52293 0.17133-1.1513 0.58827-1.248 0.59961-0.69173 0.08655-2.4595 0.08395-2.6309 0.21094 2.2625 0.16025 4.5287-0.0031 6.7891 0.18359 2.1573 0.18123 4.3165 0.32386 6.4785 0.40625 4.2989 0.16668 30.235 0.23985 38.902 0.22852 2.1563-0.0015 4.4357-0.21042 6.5566 0.27344 0.58227 0.13236 0.89969-0.35917 1.5352-0.2832 0.52525 0.07922 0.15229-0.85472 0.56055-0.91406 0.10945-0.01466-0.36158-4.6921-0.36914-5.2285-0.0375-2.1011-0.35956-34.458-0.36523-35.674-4e-3 -0.63028 0.21192-1.8778-0.0859-2.4434-0.3708-0.70893-1.5804-1.3625-2.1934-1.875-0.25951-0.21777-9.2403-7.6819-12.693-10.43-6.3213-5.0319-12.643-10.08-18.689-15.445-0.16684-0.14862-0.80503-0.87675-1.168-0.95312zm48.371 0.74609c0.0145 0.22046 0.12749 0.29406 0.20508 0.38476 0.28653 0.34016 2.1462 2.6135 2.791 3.4141 0.0369-0.03628 0.0758-0.07158 0.11132-0.10938-0.0901-0.23494-1.6863-2.1365-2.3144-2.9141-0.22027-0.26933-0.4575-0.52292-0.79297-0.77539zm3.748 8.6816c0.0885-0.08228 0.31395 0.34306 0.29883 0.38086 9e-3 -0.03326-0.14404-0.1269-0.17578-0.17188-0.0276-0.0094-0.17411-0.15796-0.12305-0.20898z">
</path></symbol></defs><use href="#hubitat-svg"/></svg>"""       
@Field static final String sHubitatIcon="""<svg width="1.0em" height="1.0em" version="2.0"><use href="#hubitat-svg"/></svg>"""

// thanks to DCMeglio (Hubitat Package Manager) for a lot of formatting hints
def getFormat(type, myText="", myHyperlink=""){   
    if(type == "line")      return "<hr style='background-color:${sColorDarkBlue}; height: 1px; border: 0;'>"
	if(type == "title")     return "<h2 style='color:${sColorDarkBlue};font-weight: bold'>${myText}</h2>"
    if(type == "text")      return "<span style='color:${sColorDarkBlue};font-weight: bold'>${myText}</span>"
    if(type == "hyperlink") return "<a href='${myHyperlink}' target='_blank' rel='noopener noreferrer' style='color:${sColorDarkBlue};font-weight:bold'>${myText}</a>"
}

def displayHeader() { 
    section (getFormat("title", "${app.getLabel()}${sCodeRelease?.size() ? " : $sCodeRelease" : ""}"  )) { 
        paragraph "<div style='color:${sColorDarkBlue};text-align:right;font-weight:small;font-size:9px;'>Developed by: ${author()}<br/>Current Version: ${version()} -  ${copyright()}</div>"
        paragraph( getFormat("line") ) 
    }
}

def displayFooter(){
	section() {
		paragraph( getFormat("line") )
		paragraph "<div style='color:{sColorDarkBlue};text-align:center;font-weight:small;font-size:11px;'>${app.getLabel()}<br><br><a href='${paypal()}' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This application took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}       
}

def menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}

private logInfo(msg)  { log.info "${msg}" }
private logDebug(msg) { if(appLogEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(appTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn  "${msg}" } 
private logError(msg) { log.error  "${msg}" }
