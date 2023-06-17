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
*  HubiThings Replica
*
*  Update: Bloodtick Jones
*  Date: 2022-10-01
*
*  1.0.00 2022-10-01 First pass.
*  ...    Deleted
*  1.3.00 2023-01-13 Formal Release Candidate
*  1.3.02 2023-01-26 Support for passing unit:'' and data:[:] structures from ST. Intial work to support ST Virtual device creation (not completed)
*  1.3.03 2023-02-09 Support for SmartThings Virtual Devices. Major UI Button overhaul. Work to improve refresh.
*  1.3.04 2023-02-16 Support for SmartThings Scene MVP. Not released.
*  1.3.05 2023-02-18 Support for 200+ SmartThings devices. Increase OAuth maximum from 20 to 30.
*  1.3.06 2023-02-26 Natural order sorting. [patched 2023-02-28 for event sorting]
*  1.3.07 2023-03-14 Bug fixes for possible Replica UI list nulls. C-8 hub migration OAuth warning.
*  1.3.08 2023-04-23 Support for more SmartThings Virtual Devices. Refactor of deviceTriggerHandlerPrivate() to support.
*  1.3.09 2023-06-05 Updated to support 'warning' for token refresh with still valid OAuth authorization.
*  1.3.10 2023-06-17 Support SmartThings Virtual Lock, add default values to ST Virtuals, fix mirror/create flow logic
*  LINE 30 MAX */ 

public static String version() { return "1.3.10" }
public static String copyright() { return "&copy; 2023 ${author()}" }
public static String author() { return "Bloodtick Jones" }

import groovy.json.*
import java.util.*
import java.text.SimpleDateFormat
import java.net.URLEncoder
import hubitat.helper.RMUtils
import com.hubitat.app.ChildDeviceWrapper
import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static final String  sDefaultAppName="HubiThings Replica"
@Field static final Integer iUseJqueryDataTables=25
@Field static final Integer iHttpSuccess=200
@Field static final Integer iHttpError=400
@Field static final String  sURI="https://api.smartthings.com"
@Field static final Integer iPageMainRefreshInterval=60*30
@Field static final String  sColorDarkBlue="#1A77C9"
@Field static final String  sColorLightGrey="#DDDDDD"
@Field static final String  sColorDarkGrey="#696969"
@Field static final String  sColorDarkRed="DarkRed"
@Field static final String  sNotAuthorized="&ZeroWidthSpace;Not Authorized"
@Field static final String  sNoRules="&ZeroWidthSpace;No Rules"
@Field static final String  sOffline="&ZeroWidthSpace;Offline"

// IN-MEMORY VARIABLES (Cleared on HUB REBOOT or CODE UPDATES)
@Field volatile static Map<Long,Map>   g_mSmartDeviceStatusMap = [:]
@Field volatile static Map<Long,Map>   g_mSmartDeviceListCache = [:]
@Field volatile static Map<Long,Map>   g_mVirtualDeviceListCache = [:] 
@Field volatile static Map<Long,Map>   g_mSmartLocationListCache = [:]
@Field volatile static Map<Long,Map>   g_mSmartRoomListCache = [:]
@Field volatile static Map<Long,Map>   g_mSmartSceneListCache = [:]
@Field volatile static Map<Long,Map>   g_mReplicaDeviceCache = [:]
@Field volatile static Map<String,Map> g_mAppDeviceSettings = [:] // don't clear

void clearAllVolatileCache() {
    Long appId = app.getId()
    g_mSmartDeviceStatusMap[appId]=null
    g_mSmartDeviceListCache[appId]=null
    g_mVirtualDeviceListCache[appId]=null
    g_mSmartLocationListCache[appId]=null
    g_mSmartRoomListCache[appId]=null
    g_mSmartSceneListCache[appId]=null
    g_mReplicaDeviceCache[appId]=null
}

definition(
    name: sDefaultAppName,
    namespace: "replica",
    author: "bloodtick",
    description: "Hubitat Application to manage SmartThings Devices",
    category: "Convenience",
    importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/hubiThingsReplica.groovy",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: false
){}

preferences {
    page name:"pageMain"
    page name:"pageAuthDevice"
    page name:"pageHubiThingDevice"
    page name:"pageMirrorDevice"
    page name:"pageConfigureDevice"
    page name:"pageVirtualDevice"
}

def installed() {
    state.isInstalled = now()
    app.updateSetting("pageMainShowConfig", true)
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    logInfo "${app.getLabel()} executing 'initialize()'"
    if(pageMainPageAppLabel && pageMainPageAppLabel!=app.getLabel()) { app.updateLabel( pageMainPageAppLabel ) }
    subscribe(location, "mode", locationModeHandler)
}

def uninstalled() {
    logInfo "${app.getLabel()} executing 'uninstalled()'"    
    unsubscribe()
    unschedule()
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
    getChildApps().each { deleteChildApp(it.id) }
}

/************************************** CHILD METHODS START *******************************************************/

public void childInitialize( childApp ) {
    logDebug "${app.getLabel()} executing 'childInitialize($childApp.id)'"
}

public void childUpdated( childApp ) {
    logDebug "${app.getLabel()} executing 'childUpdated($childApp.id)'"
}

public void childUninstalled( childApp ) {
    logDebug "${app.getLabel()} executing 'childUninstalled($childApp.id)'"
    runIn(2, allSmartDeviceRefresh)
    runIn(5, updateLocationSubscriptionSettings) // not the best place for this. not sure where is the best place.
}

public void childSubscriptionDeviceListChanged( childApp, data=null ) {
    logDebug "${app.getLabel()} executing 'childSubscriptionDeviceListChanged($childApp.id, $data)'"
    try {
        getSmartDevicesMap()?.items?.removeAll{ it.appId == childApp.getId() }
        getSmartDevicesMap()?.items?.addAll( childApp.getSmartSubscribedDevices()?.items )
    } catch(e) {
        logInfo "${app.getLabel()} 'childSubscriptionDeviceListChanged($childApp.id)' did not complete successfully"
    }
    runIn(5, updateLocationSubscriptionSettings) // not the best place for this. not sure where is the best place.
}

public List childGetOtherSubscribedDeviceIds( childApp ) {
    logDebug "${app.getLabel()} executing 'childGetOtherSubscribedDeviceIds($childApp.id)'"
    List devices = []
    getChildApps()?.each{ oauthApp -> 
        if(oauthApp.getId() != childApp.getId()) {
            devices += oauthApp?.getSmartDeviceSelectList()
        }
    }
    return devices?.unique()
}

public void childSubscriptionEvent( childApp, event ) {
    oauthEventHandler( event?.eventData, now() )
}

public void childHealthChanged( childApp ) {
    logDebug "${app.getLabel()} executing 'childHealthChanged($childApp.id)'"
    String locationId = childApp?.getLocationId()
    String oauthStatus = "UNKNOWN"    
    getChildApps().each{ 
        logTrace "${it?.getAuthStatus()?.toLowerCase()} ${it?.getLocationId()}"
        if(it?.getLocationId() == locationId) {
            if(it?.getAuthStatus()=="FAILURE")
                oauthStatus = "FAILURE"
            else if(oauthStatus!="FAILURE" && it?.getAuthStatus()=="WARNING")
                oauthStatus = "WARNING"            
            else if(oauthStatus!="FAILURE" && oauthStatus!="WARNING" && it?.getAuthStatus()=="PENDING")
                oauthStatus = "PENDING"            
            else if(oauthStatus!="FAILURE" && oauthStatus!="WARNING" && oauthStatus!="PENDING" && it?.getAuthStatus()=="AUTHORIZED")
                oauthStatus = "AUTHORIZED"            
        }    
    }   
    getAllReplicaDevices()?.each { replicaDevice ->
        if(hasCommand(replicaDevice, 'setOauthStatusValue')) {
            Map description = getReplicaDataJsonValue(replicaDevice, "description")
            if(description?.locationId == locationId) {
                replicaDevice.setOauthStatusValue(oauthStatus?.toLowerCase())
            }
        }
   }
}

public String getAuthToken() {
    return userSmartThingsPAT
}

public void setLocationMode(String mode) {
    logDebug "${app.getLabel()} executing 'setLocationMode($mode)'"
    app.setLocationMode(mode)
}

public void updateLocationSubscriptionSettings() {
    List primaryApps = getChildApps()?.clone()?.sort{ it?.getId() }?.unique{ a, b -> a.getLocationId() <=> b.getLocationId() }  
    getChildApps()?.each{ ouathApp ->
        if( primaryApps?.find{ it?.getId()==ouathApp.getId() } ) {
            logDebug "${app.getLabel()} Leader**: location:${ouathApp?.getLocationId()} OAuthId:${ouathApp?.getOauthId()}"
            ouathApp.updateLocationSubscriptionSettings(true)
        } else {
            logDebug "${app.getLabel()} Follower: location:${ouathApp?.getLocationId()} OAuthId:${ouathApp?.getOauthId()}"
            ouathApp.updateLocationSubscriptionSettings(false)
        }
        ouathApp.setSmartDeviceSubscriptions()
    }
}

/************************************** CHILD METHODS STOP ********************************************************/

Map getSmartLocations() {
    return g_mSmartLocationListCache[app.getId()] ?: (g_mSmartLocationListCache[app.getId()]=getSmartLocationList()?.data) ?: [:] //blocking http
}

Map getSmartRooms(String locationId) {
    if(!g_mSmartRoomListCache[app.getId()]) g_mSmartRoomListCache[app.getId()] = [:]
    return g_mSmartRoomListCache[app.getId()]?."$locationId" ?: (g_mSmartRoomListCache[app.getId()]."$locationId"=getSmartRoomList(locationId)?.data) ?: [:] //blocking http
}

Map getSmartScenes(String locationId) {
    if(!g_mSmartSceneListCache[app.getId()]) g_mSmartSceneListCache[app.getId()] = [:]
    return g_mSmartSceneListCache[app.getId()]?."$locationId" ?: (g_mSmartSceneListCache[app.getId()]."$locationId"=getSmartSceneList(locationId)?.data) ?: [:] //blocking http
}

Map getVirtualDevices() {
    return g_mVirtualDeviceListCache[app.getId()] ?: (g_mVirtualDeviceListCache[app.getId()]=getVirtualDeviceList()?.data) ?: [:] //blocking http
}

String getSmartLocationName(String locationId) {
    getSmartLocations()?.items?.find{ it.locationId==locationId }?.name
}

Map getSmartDevicesMap() {
    Long appId = app.getId()
    if(g_mSmartDeviceListCache[appId]==null) {
        g_mSmartDeviceListCache[appId]=[:]
        allSmartDeviceRefresh()
        Integer count=0
        while(count<40 && (g_mSmartDeviceListCache[appId])?.items==null ) { pauseExecution(250); count++ } // wait a max of 10 seconds
    }
    return g_mSmartDeviceListCache[appId]?:[:]
}

void setSmartDevicesMap(Map deviceList) {
    g_mSmartDeviceListCache[app.getId()] = deviceList
    clearReplicaDataCache() // lets clear the cache of any stale devices
    logInfo "${app.getLabel()} caching SmartThings device list"
}

def pageMain(){
    if(!state?.isInstalled) {        
        return dynamicPage(name: "pageMain", install: true, refreshInterval: 0) {
            displayHeader()
            section(menuHeader("Complete Install $sHubitatIconStatic $sSamsungIconStatic")) {
                paragraph("Please complete the install <b>(click done)</b> and then return to $sHubitatIcon SmartApp to continue configuration")
                input(name: "pageMainPageAppLabel", type: "text", title: getFormat("text","$sHubitatIcon Change ${app.getLabel()} SmartApp Name:"), width: 6, defaultValue:app.getLabel(), submitOnChange: true, newLineAfter:true)
            }
        }
    }    
    
    Map smartDevices = getSmartDevicesMap()?.clone()
    Integer deviceAuthCount = getAuthorizedDevices()?.size() ?: 0
    
    return dynamicPage(name: "pageMain", install: true, refreshInterval:iPageMainRefreshInterval) {
        displayHeader()
        state.pageMainLastRefresh = now()
        
        section(menuHeader("Replica Configuration $sHubitatIconStatic $sSamsungIconStatic")) {
            
            input(name: "pageMainShowConfig", type: "bool", title: "$sHubitatIcon Show Configuration", defaultValue: true, submitOnChange: true)
            paragraph( getFormat("line") )            
            if(pageMainShowConfig) {
                String comments = "This application utilizes the SmartThings Cloud API to create, delete and query devices. <b>You must supply a SmartThings Personal Access Token (PAT) with all Authorized Scopes permissions to enable functionality</b>. "
                       comments+= "A PAT is valid for 50 years from creation date. Click the ${sSamsungIcon} SmartThings Personal Access Token link to be directed to the SmartThings website."
                paragraph( getFormat("comments",comments,null,"Gray") )
                
                input(name: "userSmartThingsPAT", type: "password", title: getFormat("hyperlink","&ensp;$sSamsungIcon SmartThings Personal Access Token:","https://account.smartthings.com/tokens"), description: "SmartThings UUID Token", width: 6, submitOnChange: true, newLineAfter:true)
                paragraph("") 
                
                if(userSmartThingsPAT) {
                    comments = "HubiThings OAuth Applications are required to enable SmartThings devices for replication. Each OAuth Application can subscribe up to 30 devices and is hub and location independent. "
                    comments+= "HubiThings Replica allows for multiple OAuth Applications to be created for solution requirements beyond 30 devices. <b>Click the '${sSamsungIcon} Authorize SmartThings Devices : Create OAuth Applications' link to create one or more OAuth Applications</b>."
                    paragraph( getFormat("comments",comments,null,"Gray") )
                    
                    app(name: "oauthChildApps", appName: "HubiThings OAuth", namespace: "replica", title: "${getFormat("text","$sSamsungIcon Authorize SmartThings Devices")} : Create OAuth Applications", multiple: true)                
                    paragraph( getFormat("line") )
  
                    input(name: "pageMainShowAdvanceConfiguration", type: "bool", title: "$sHubitatIcon Advanced Configuration", defaultValue: false, submitOnChange: true)                
                    if(pageMainShowAdvanceConfiguration) {             
                        pageAuthDeviceUserInputCtl()
                        paragraph("")
   
                        input(name: "pageMainPageAppLabel", type: "text", title: "&ensp;$sHubitatIcon Hubitat SmartApp Name:", width: 6, submitOnChange: true, newLineAfter:true)
                        input(name: "dynamic::pageMainChangeAppNameButton", type: "button", title: "Change Name", width: 3, style:"width:50%;", newLineAfter:true)                
                    }
                }
            }
        }
            
        if(pageMainShowConfig && pageMainShowAdvanceConfiguration) {  
            section(menuHeader("Replica Application Logging")) {
                input(name: "appLogEventEnable", type: "bool", title: "Enable Event and Status Info logging", required: false, defaultValue: false, submitOnChange: true)
                if(appLogEventEnable) {
                    List smartDevicesSelect = []
                    smartDevices?.items?.sort{ it.label }?.each {    
                        def device = [ "${it.deviceId}" : "${it.label} &ensp; (deviceId: ${it.deviceId})" ]
                        smartDevicesSelect.add(device)   
                    }
                    input(name: "appLogEventEnableDevice", type: "enum", title: getFormat("text","&ensp;$sSamsungIcon Selective SmartThings Info Logging:"), description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
                    paragraph( getFormat("line") )
                } else { 
                    app.removeSetting("appLogEventEnableDevice")
                }
                input(name: "appInfoDisable", type: "bool", title: "Disable info logging", required: false, defaultValue: false, submitOnChange: true)
                input(name: "appDebugEnable", type: "bool", title: "Enable debug logging", required: false, defaultValue: false, submitOnChange: true)
                input(name: "appTraceEnable", type: "bool", title: "Enable trace logging", required: false, defaultValue: false, submitOnChange: true)
            }
        }        
        if(state?.user=="bloodtick") {
            section(menuHeader("Replica Application Development")) {
                input(name: "dynamic::pageMainTestButton", type: "button", width: 2, title: "$sHubitatIcon Test", style:"width:75%;")
                input(name: "dynamic::allSmartDeviceStatus",      type: "button", width: 2, title: "$sSamsungIcon Status", style:"width:75%;")
                input(name: "dynamic::allSmartDeviceDescription", type: "button", width: 2, title: "$sSamsungIcon Description", style:"width:75%;")
                input(name: "dynamic::allSmartDeviceHealth",      type: "button", width: 2, title: "$sSamsungIcon Health", style:"width:75%;")
            }
        }               
        
        if(userSmartThingsPAT&&getChildApps()?.size()) {            
			section(menuHeader("HubiThings Device List")){           
				if (smartDevices) {
					String devicesTable  = "<table id='devicesTable' role='table' class='compact' style='width:100%'><thead>"
					       devicesTable += "<tr><th>$sSamsungIcon Device</th><th>$sHubitatIcon Device</th><th>$sHubitatIcon OAuth</th><th style='text-align:center;'>$sSamsungIcon Events</th></tr>"
					       devicesTable += "</thead><tbody>"
                    
                    List deviceIds = getAllReplicaDeviceIds()
                    try {
					smartDevices?.items?.sort{ it?.label }?.each { smartDevice ->
                        deviceIds.remove(smartDevice?.deviceId)
						List hubitatDevices = getReplicaDevices(smartDevice?.deviceId)
						for (Integer i = 0; i ==0 || i < hubitatDevices.size(); i++) {
							def replicaDevice = hubitatDevices[i]?:null
							String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice?.getId()}"                        
							String oauthUrl = "http://${location.hub.getDataValue("localIP")}/installedapp/configure/${smartDevice?.appId}"
							devicesTable += "<tr>"
							devicesTable += smartDevice?.label   ? "<td>${smartDevice?.label}</td>" : "<td>--</td>"                  
							devicesTable += replicaDevice        ? "<td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${replicaDevice?.getDisplayName()}</a></td>" : "<td>--</td>"
							devicesTable += smartDevice?.oauthId ? "<td><a href='${oauthUrl}'>${smartDevice?.locationName?:""} : ${smartDevice?.oauthId?:""}</a></td>" : "<td>--</td>"
							devicesTable += replicaDevice        ? "<td style='text-align:center;' id='${replicaDevice?.deviceNetworkId}'>${updateSmartDeviceEventsStatus(replicaDevice)}</td>" : "<td style='text-align:center;'>0</td>"
							devicesTable += "</tr>"
						}
                    } } catch(e) { } //noop. have a concurrency problem once and a while.
                    logDebug "${app.getLabel()} deviceIds not found $deviceIds"
 					deviceIds?.each { deviceId ->
						List hubitatDevices = getReplicaDevices(deviceId)
						for (Integer i = 0; i ==0 || i < hubitatDevices.size(); i++) {
							def replicaDevice = hubitatDevices[i]?:null
							String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice?.getId()}"                        
							devicesTable += "<tr>"
							devicesTable += "<td>--</td>"                  
							devicesTable += "<td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${replicaDevice?.getDisplayName()}</a></td>"
							devicesTable += "<td>--</td>"
							devicesTable += "<td style='text-align:center;color:$sColorDarkRed;'>$sNoStatusIcon $sNotAuthorized</td>"
							devicesTable += "</tr>"
						}
					}                                  

					devicesTable +="</tbody></table>"
                    devicesTable += """<style>@media screen and (max-width:800px) { table th:nth-of-type(3),td:nth-of-type(3) { display: none; } }</style>"""
                    // Hubitat includes jquery DataTables in web code. https://datatables.net
                    if(smartDevices?.items?.size()+deviceIds?.size() > iUseJqueryDataTables) {
                        devicesTable += """<script>$naturalSortFunction \$(document).ready(function () { \$('#devicesTable').DataTable( { destroy: true, stateSave: true, lengthMenu:[ [25, 50, 100, -1], [25, 50, 100, "All"] ], columnDefs:[ { type:'natural-nohtml', targets:'_all' } ]} );});</script>"""                
					    //devicesTable += """<script>\$(document).ready(function () { \$('#devicesTable').DataTable( { stateSave: true, lengthMenu:[ [25, 50, 100, -1], [25, 50, 100, "All"] ] } );});</script>"""
                        devicesTable += """<style>#devicesTable tbody tr.even:hover { background-color: #F5F5F5 !important; }</style>"""
                    } else {
                        devicesTable += """<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>"""
                    }                    
					paragraph( devicesTable )

					String socketstatus = """<span style='color:${sColorDarkRed}' id='socketstatus'></span>"""					
					socketstatus += """<script>if(typeof websocket_start === 'undefined'){ window.websocket_start=true; console.log('websocket_start'); var ws = new WebSocket("ws://${location.hub.localIP}:80/eventsocket"); ws.onmessage=function(evt){ var e=JSON.parse(evt.data); if(e.installedAppId=="${app.getId()}") { smartEvent(e); }}; ws.onclose=function(){ onclose(); delete websocket_start;};}</script>"""
					if(smartDevices?.items?.size()+deviceIds?.size() > iUseJqueryDataTables)
                        socketstatus += """<script>function smartEvent(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='smartEvent'){ \$('#devicesTable').DataTable().cell(`#\${dt.deviceNetworkId}`)?.data(evt.value).draw(false); }}</script>"""
                    else
                        socketstatus += """<script>function smartEvent(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='smartEvent' && document.getElementById(dt.deviceNetworkId)){ document.getElementById(dt.deviceNetworkId).innerHTML = evt.value; }}</script>"""
                    socketstatus += """<script>function onclose() { console.log("Connection closed"); if(document.getElementById('socketstatus')){ document.getElementById('socketstatus').textContent = "Notice: Websocket closed. Please refresh page to restart.";}}</script>""" 
					paragraph( rawHtml: true, socketstatus )
				}            
				input(name: "dynamic::allSmartDeviceRefresh",  type: "button", width: 2, title: "$sSamsungIcon Refresh", style:"width:75%;")
			}

			section(menuHeader("Replica Device Creation and Control")){             
				href "pageHubiThingDevice", title: "$sImgDevh Configure HubiThings Devices ", description: "Click to show"
                href "pageVirtualDevice",   title: "$sImgDevv Configure Virtual Devices, Modes and Scenes", description: "Click to show"
				href "pageConfigureDevice", title: "$sImgRule Configure HubiThings Rules", description: "Click to show"
                if(deviceAuthCount>0) href "pageMirrorDevice", title: "$sImgMirr Mirror Hubitat Devices (Advanced)", description: "Click to show"
            }

			if(pageMainShowConfig || appDebugEnable || appTraceEnable) {
				runIn(1800, updatePageMain)
			} else {
				unschedule('updatePageMain')
			}            
        } //if(userSmartThingsPAT&&getChildApps()?.size())
        
        displayFooter()
    }    
}

void updatePageMain() {
    logInfo "${app.getLabel()} disabling debug and trace logs"
    app.updateSetting("pageMainShowConfig", false)
    app.updateSetting("pageMainShowAdvanceConfiguration", false)
    app.updateSetting("appDebugEnable", false)
    app.updateSetting("appTraceEnable", false)    
}

def pageAuthDevice() {    
//    Integer deviceAuthCount = getAuthorizedDevices()?.size() ?: 0    
    return dynamicPage(name: "pageAuthDevice", uninstall: false) {
//        displayHeader()

        section(menuHeader("Authorize Hubitat Devices to Mirror $sHubitatIconStatic $sSamsungIconStatic")) {                         
            paragraph( getFormat("comments","<b>Hubitat Security</b> requires each local device to be authorized with internal controls before HubiThings Replica can access. Please select Hubitat devices below before attempting mirror functions.",null,"Gray") ) 
            input(name: "userAuthorizedDevices", type: "capability.*", title: "Hubitat Devices:", description: "Choose a Hubitat devices", multiple: true, submitOnChange: true, newLineAfter:true)

            paragraph( """<br/><br/><br/><input type="button" class="mdl-button mdl-button--raised btn" value="Done" onclick="self.close()">""" )
        }
    }
}

def pageAuthDeviceUserInputCtl() {   
    //http://192.168.1.33/installedapp/configure/314/pageMain/pageAuthDevice
    String authorizeUrl = "http://${location.hub.getDataValue("localIP")}/installedapp/configure/${app.getId()}/pageAuthDevice"
    //String authorizeTitle = "&ensp;$sHubitatIcon Select Hubitat Device:"
    //authorizeTitle = """<a href="$authorizeUrl" target="popup" onclick="var w=800; var h=1400; window.open('$authorizeUrl','popup','left='+(screen.width-w)/2+',top='+(screen.height-h)/4+',width='+w+',height='+h+''); return true;">$authorizeTitle</a>"""
                
    Integer deviceAuthCount = getAuthorizedDevices()?.size() ?: 0
    String deviceText = (deviceAuthCount<1 ? ": (Select to Authorize Devices to Mirror)" : (deviceAuthCount==1 ?  ": ($deviceAuthCount Device Authorized to Mirror)" : ": ($deviceAuthCount Devices Authorized to Mirror)"))
    // this is a workaround for the form data submission on 'external' modal boxes. not sure why hubitat is failing. 
    paragraph (rawHtml: true, """<script>\$(function() { const pageUri = window.location.href; const pageUriNoQueryString = pageUri.split("?")[0]; if (pageUri != pageUriNoQueryString) window.location.href = pageUriNoQueryString;});</script>""")
    href url: authorizeUrl, style: "external", title: "$sHubitatIcon Authorize Hubitat Devices $deviceText", description: "Click to show"
    //href "pageAuthDevice", title: "$sHubitatIcon Authorize Hubitat Devices $deviceText", description: "Click to show"    
}


void pageMainChangeAppNameButton() {
    logDebug "${app.getLabel()} executing 'pageMainChangeAppNameButton()' $pageMainPageAppLabel"
    if(pageMainPageAppLabel && pageMainPageAppLabel!=app.getLabel()) {
        logInfo "${app.getLabel()} changing Hubitat SmartApp from ${app.getLabel()} to $pageMainPageAppLabel"
        app.updateLabel( pageMainPageAppLabel )
    }
}

Map getDeviceHandlers() {    
    Map handlers = [ items: [
        [id:"1", name:"Virtual Switch",             namespace:"hubitat" ],
        [id:"2", name:"Virtual Contact Sensor",     namespace:"hubitat" ],
        [id:"3", name:"Virtual Motion Sensor",      namespace:"hubitat" ],
        [id:"4", name:"Virtual Temperature Sensor", namespace:"hubitat" ],
        [id:"5", name:"Virtual Humidity Sensor",    namespace:"hubitat" ],
        [id:"6", name:"Virtual Presence",           namespace:"hubitat" ],
        [id:"7", name:"Virtual Shade",              namespace:"hubitat" ],
        [id:"8", name:"Virtual Thermostat",         namespace:"hubitat" ]
    ]]
    getDriverList()?.items?.each{ if(it?.namespace=='replica') handlers.items.add(it) }    
    return handlers
}    

def pageHubiThingDevice(){
    app.removeSetting('pageCreateDeviceLabel') //1.3.03
    app.removeSetting('pageCreateDeviceShowAllDevices') //1.3.03
    app.removeSetting('pageCreateDeviceSmartDevice') //1.3.03
    app.removeSetting('pageCreateDeviceSmartDeviceComponent') //1.3.03
    app.removeSetting('pageCreateDeviceType') //1.3.03
    
    Map smartDevices = getSmartDevicesMap()

    List smartDevicesSelect = []
    smartDevices?.items?.sort{ a,b -> naturalSort(a.label,b.label) }?.each { smartDevice ->
        smartDevice?.components?.each { component ->
            if( !smartDevicesSelect?.find{ smartDevice.deviceId==it.keySet().getAt(0) } && (pageHubiThingDeviceShowAllDevices || !getReplicaDevices(smartDevice.deviceId, component?.id)) )
                smartDevicesSelect.add([ "$smartDevice.deviceId" : "$smartDevice.label &ensp; (deviceId: $smartDevice.deviceId)" ])       
        }
    }    
   
    List smartComponentsSelect = []       
    smartDevices?.items?.find{it.deviceId == pageHubiThingDeviceSmartDevice}?.components.each { component ->
        if(pageHubiThingDeviceShowAllDevices || !getReplicaDevices(pageHubiThingDeviceSmartDevice, component?.id))
            smartComponentsSelect.add(component.id)
    }
     
    List hubitatDeviceTypes = []
    getDeviceHandlers()?.items?.sort{ a,b -> b?.namespace <=> a?.namespace ?: a?.name <=> b?.name }?.each {    
        hubitatDeviceTypes.add([ "$it.id" : "$it.name &ensp; (namespace: $it.namespace)" ])   
    }

    if(smartComponentsSelect?.size()==1) { 
        app.updateSetting( "pageHubiThingDeviceSmartDeviceComponent", [type:"enum", value: smartComponentsSelect?.get(0)] )
    }
    
    String smartStats = getSmartDeviceStats(pageHubiThingDeviceSmartDevice, pageHubiThingDeviceSmartDeviceComponent)
    if(pageHubiThingDeviceSmartDevice!=g_mAppDeviceSettings?.pageHubiThingDeviceSmartDevice || pageHubiThingDeviceSmartDeviceComponent!=g_mAppDeviceSettings?.pageHubiThingDeviceSmartDeviceComponent) {
        g_mAppDeviceSettings['pageHubiThingDeviceSmartDevice'] = pageHubiThingDeviceSmartDevice
        g_mAppDeviceSettings['pageHubiThingDeviceSmartDeviceComponent'] = pageHubiThingDeviceSmartDeviceComponent
        String deviceLabel = smartDevices?.items?.find{it.deviceId == pageHubiThingDeviceSmartDevice}?.label
        app.updateSetting( "pageHubiThingDeviceLabel", deviceLabel ? "$deviceLabel${smartComponentsSelect?.size()>1 ? " - $pageHubiThingDeviceSmartDeviceComponent" : ""}" : "" )
    }
    
    Integer refreshInterval = (g_mAppDeviceSettings?.pageHubiThingDeviceCreateButton || g_mAppDeviceSettings?.pageHubiThingDeviceModifyButton) ? 15 : 0
    return dynamicPage(name: "pageHubiThingDevice", uninstall: false, refreshInterval:refreshInterval) {
        displayHeader()        

        section(menuHeader("Create HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) { 
            input(name: "pageHubiThingDeviceSmartDevice", type: "enum", title: "&ensp;$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
            if(pageHubiThingDeviceSmartDevice && smartComponentsSelect?.size()>1)
                input(name: "pageHubiThingDeviceSmartDeviceComponent", type: "enum", title: "$sSamsungIcon Select SmartThings Device Component:", description: "Choose a Device Component", options: smartComponentsSelect, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            paragraph( smartStats )
            input(name: "pageHubiThingDeviceShowAllDevices", type: "bool", title: "Show All Authorized SmartThings Devices", defaultValue: false, submitOnChange: true, newLineAfter:true)
            paragraph( getFormat("line") )
            
            input(name: "pageHubiThingDeviceType", type: "enum", title: "&ensp;$sHubitatIcon Select Hubitat Device Type:", description: "Choose a Hubitat device type", options: hubitatDeviceTypes, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            input(name: "pageHubiThingDeviceLabel", type: "text", title: "$sHubitatIcon Set Hubitat Device Label:", submitOnChange: true, width: 6, newLineAfter:true)
            input(name: "dynamic::pageHubiThingDeviceCreateButton", type: "button", width: 2, title: "$sHubitatIcon Create", style:"width:75%;")
            
            if( g_mAppDeviceSettings?.pageHubiThingDeviceCreateButton) {
                paragraph( getFormat("line") )
                paragraph( g_mAppDeviceSettings.pageHubiThingDeviceCreateButton )
                g_mAppDeviceSettings.pageHubiThingDeviceCreateButton = null
                href "pageConfigureDevice", title: "$sImgRule Configure HubiThings Rules", description: "Click to show"                
            }
        }
        commonReplicaDevicesSection("pageHubiThingDevice")
    }
}

def commonReplicaDevicesSection(String dynamicPageName) {    
    List hubitatDevicesSelect = []
    getAllReplicaDevices()?.sort{ a,b -> naturalSort(a.getDisplayName(),b.getDisplayName()) }?.findAll{ (dynamicPageName=="pageHubiThingDevice" && getChildDevice( it?.deviceNetworkId )) || (dynamicPageName!="pageHubiThingDevice" && !getChildDevice( it?.deviceNetworkId )) }?.each{
        Map device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
        hubitatDevicesSelect.add(device)   
    }
    section(menuHeader("Modify HubiThings Device")) {
        paragraph( getFormat("comments","${(dynamicPageName=="pageHubiThingDevice")?"<b>Replace</b> updates the 'Select SmartThings Device' to replica the 'Select Hubitat Device'. ":""}<b>Remove</b> deletes the 'Select Hubitat Device' child device from Hubitat, but will only decouple a mirror device and will not delete.",null,"Gray") ) 
        input(name: "pageHubiThingDeviceModify", type: "enum",  title: "&ensp;$sHubitatIcon Select HubiThings Device:", description: "Choose a HubiThings device", multiple: false, options: hubitatDevicesSelect, submitOnChange: true, width: 6, newLineAfter:true)
        if(dynamicPageName=="pageHubiThingDevice")
            input(name: "dynamic::pageHubiThingDeviceReplaceButton",  type: "button", width: 2, title: "$sHubitatIcon Replace", style:"width:75%;")
        input(name: "dynamic::pageHubiThingDeviceRemoveButton",  type: "button", width: 2, title: "$sHubitatIcon Remove", style:"width:75%;", newLineAfter:true)
        if(g_mAppDeviceSettings?.pageHubiThingDeviceModifyButton) {
            paragraph( g_mAppDeviceSettings?.pageHubiThingDeviceModifyButton )
            g_mAppDeviceSettings.pageHubiThingDeviceModifyButton = null
        }            
    }    
    Map smartDevices = getSmartDevicesMap()
    
    String devicesTable  = "<table id='devicesTable' role='table' class='compact' style='width:100%;'>"
           devicesTable += "<thead><tr><th>$sHubitatIcon&nbsp;Device</th><th>$sHubitatIcon&nbsp;Type</th><th>$sHubitatIcon&nbsp;OAuth</th><th style='text-align:center;'>$sHubitatIcon&nbsp;Class</th></tr></thead><tbody>"
    List devices = getAllReplicaDevices()?.sort{ it.getDisplayName() }?.findAll{ (dynamicPageName=="pageHubiThingDevice" && getChildDevice( it?.deviceNetworkId )) || (dynamicPageName!="pageHubiThingDevice" && !getChildDevice( it?.deviceNetworkId )) }?.each{  replicaDevice ->
        Boolean isChildDevice = (getChildDevice( replicaDevice?.deviceNetworkId ) != null)
        String deviceId = getReplicaDeviceId(replicaDevice)
        Map smartDevice = smartDevices?.items?.find{ it?.deviceId == deviceId }
        String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice.getId()}"
        String oauthUrl = "http://${location.hub.getDataValue("localIP")}/installedapp/configure/${smartDevice?.appId}"
        devicesTable += "<tr><td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${replicaDevice.getDisplayName()}</a></td>"
        devicesTable += "<td>${replicaDevice.typeName}</td>"
        devicesTable += smartDevice?.oauthId ? "<td><a href='${oauthUrl}'>${smartDevice?.locationName?:""} : ${smartDevice?.oauthId?:""}</a></td>" : "<td style='color:$sColorDarkRed;'>$sNoStatusIcon $sNotAuthorized</td>"
        devicesTable += "<td style='text-align:center;'>${isChildDevice?'Child':'Mirror'}</td></tr>"
    }
    devicesTable +="</tbody></table>"
    devicesTable += """<style>@media screen and (max-width:800px) { table th:nth-of-type(4),td:nth-of-type(4) { display: none; } }</style>"""
    if(devices?.size() > iUseJqueryDataTables) {
        devicesTable += """<script>$naturalSortFunction \$(document).ready(function () { \$('#devicesTable').DataTable( { destroy: true, stateSave: true, lengthMenu:[ [25, 50, 100, -1], [25, 50, 100, "All"] ], columnDefs:[ { type:'natural-nohtml', targets:'_all' } ]} );});</script>"""                
        //devicesTable += """<script>\$(document).ready(function () { \$('#devicesTable').DataTable( { stateSave: true, lengthMenu:[ [25, 50, 100, -1], [25, 50, 100, "All"] ] } );});</script>"""                
        devicesTable += """<style>#childDeviceList tbody tr.even:hover { background-color: #F5F5F5 !important; }</style>"""
    } else {
        devicesTable += """<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>"""
    }    
    
    section(menuHeader("HubiThings Device List")) {
        if (devices?.size()) {        
            paragraph( devicesTable )
        }
    }
}

void pageHubiThingDeviceCreateButton() {
    logDebug "${app.getLabel()} executing 'pageHubiThingDeviceCreateButton()' $pageHubiThingDeviceSmartDevice $pageHubiThingDeviceSmartDeviceComponent $pageHubiThingDeviceType $pageHubiThingDeviceLabel"
    if(!pageHubiThingDeviceSmartDevice)
        g_mAppDeviceSettings['pageHubiThingDeviceCreateButton'] = errorMsg("Error: SmartThings Device selection is invalid")
    else if(!pageHubiThingDeviceSmartDeviceComponent)
        g_mAppDeviceSettings['pageHubiThingDeviceCreateButton'] = errorMsg("Error: SmartThings Device Component selection is invalid")
    else if(!pageHubiThingDeviceType)
        g_mAppDeviceSettings['pageHubiThingDeviceCreateButton'] = errorMsg("Error: Hubitat Device Type is invalid") 
    else if(!pageHubiThingDeviceLabel)
        g_mAppDeviceSettings['pageHubiThingDeviceCreateButton'] = errorMsg("Error: Hubitat Device Label is invalid")
    else {
        Map deviceType = getDeviceHandlers()?.items?.find{ it?.id==pageHubiThingDeviceType }
        String name  = (getSmartDevicesMap()?.items?.find{it?.deviceId == pageHubiThingDeviceSmartDevice}?.name)
        String label = pageHubiThingDeviceLabel
        String deviceId = pageHubiThingDeviceSmartDevice
        String componentId = pageHubiThingDeviceSmartDeviceComponent
        g_mAppDeviceSettings['pageHubiThingDeviceCreateButton'] = createChildDevice(deviceType, name, label, deviceId, componentId)
    }
}

String createChildDevice(Map deviceType, String name, String label, String deviceId, String componentId) {
    logDebug "${app.getLabel()} executing 'createChildDevice()' $deviceType $name $label $deviceId $componentId"
    String response = errorMsg("Error: '$label' was not created")       
    String deviceNetworkId = "${UUID.randomUUID().toString()}"    
    try {
        def replicaDevice = addChildDevice(deviceType.namespace, deviceType.name, deviceNetworkId, null, [name: name, label: label, completedSetup: true])
        // the deviceId makes this a hubiThing
        // Needed for mirror function to prevent two SmartApps talking to same device.
        Map replica = [ deviceId:deviceId, componentId:componentId, replicaId:(app.getId()), type:'child']
        setReplicaDataJsonValue(replicaDevice, "replica", replica)
        if(replicaDevice?.hasCommand('configure')) replicaDevice.configure()
        replicaDeviceRefresh(replicaDevice)

        logInfo "${app.getLabel()} created device '${replicaDevice.getDisplayName()}' with deviceId: $deviceId"    
        app.updateSetting( "pageConfigureDeviceReplicaDevice", [type:"enum", value: replicaDevice.deviceNetworkId] )
        app.updateSetting( "pageHubiThingDeviceModify", [type:"enum", value: replicaDevice.deviceNetworkId] )       
        response = statusMsg("'$label' was created with deviceId: $deviceId and deviceNetworkId: $deviceNetworkId")        
    } catch (e) {        
        logWarn "${app.getLabel()} error creating $label: ${e}"
        logInfo pageHubiThingDeviceType
        logInfo getDeviceHandlers()
    }
    return response
}


void pageHubiThingDeviceRemoveButton() {
    logDebug "${app.getLabel()} executing 'pageHubiThingDeviceRemoveButton()' $pageHubiThingDeviceModify"
    if(pageHubiThingDeviceModify) {
        g_mAppDeviceSettings['pageHubiThingDeviceModifyButton'] = deleteChildDevice(pageHubiThingDeviceModify)
    }
}

String deleteChildDevice(String deviceNetworkId) {
    String response = errorMsg("Error: Could not find device to delete")
    def replicaDevice = getDevice( deviceNetworkId )    
    
    if(replicaDevice) {
        String label = replicaDevice?.getDisplayName()
        try {
            Boolean isChild = getChildDevice( deviceNetworkId )            
            app.removeSetting("pageHubiThingDeviceModify")
            
            if(isChild) { 
                unsubscribe(replicaDevice)                
                app.deleteChildDevice(replicaDevice?.deviceNetworkId)
                clearReplicaDataCache(replicaDevice)
                logInfo "${app.getLabel()} deleted '$label' with deviceNetworkId: ${replicaDevice?.deviceNetworkId}"
                response = statusMsg("'$label' was deleted with deviceNetworkId: ${replicaDevice?.deviceNetworkId}")
            }
            else {           
                unsubscribe(replicaDevice)
                clearReplicaDataCache(replicaDevice, "capabilities", true)
                clearReplicaDataCache(replicaDevice, "description", true)
                clearReplicaDataCache(replicaDevice, "health", true)
                clearReplicaDataCache(replicaDevice, "replica", true)
                clearReplicaDataCache(replicaDevice, "rules", true)
                clearReplicaDataCache(replicaDevice, "status", true)
                logInfo "${app.getLabel()} detached '$label' with deviceNetworkId: ${replicaDevice?.deviceNetworkId}"
                response = statusMsg("'$label' was detached with deviceNetworkId: ${replicaDevice?.deviceNetworkId}")
            }
            
        } catch (e) {
            logWarn "${app.getLabel()} error deleting $label: ${e}"
            response = errorMsg("Error: '$label' was not detached or deleted") 
        }
    }
    return response
}

void pageHubiThingDeviceReplaceButton() {
    logDebug "${app.getLabel()} executing 'pageHubiThingDeviceReplaceButton()' $pageHubiThingDeviceModify $pageHubiThingDeviceSmartDevice $pageHubiThingDeviceSmartDeviceComponent"
    if(!pageHubiThingDeviceSmartDevice)
        g_mAppDeviceSettings['pageHubiThingDeviceModifyButton'] = errorMsg("Error: SmartThings Device selection is invalid")
    else if(!pageHubiThingDeviceSmartDeviceComponent)
        g_mAppDeviceSettings['pageHubiThingDeviceModifyButton'] = errorMsg("Error: SmartThings Device Component selection is invalid")
    else if(!pageHubiThingDeviceModify)
        g_mAppDeviceSettings['pageHubiThingDeviceModifyButton'] = errorMsg("Error: Hubitat Device selection is invalid") 
    else
        g_mAppDeviceSettings['pageHubiThingDeviceModifyButton'] = replaceChildDevice(pageHubiThingDeviceModify, pageHubiThingDeviceSmartDevice, pageHubiThingDeviceSmartDeviceComponent)
}

String replaceChildDevice(String deviceNetworkId, String deviceId, String componentId) {    
    String response = errorMsg("Error: Could not find device to replace")
    def replicaDevice = getDevice(deviceNetworkId)
    
    if(replicaDevice) {
        String label = replicaDevice?.getDisplayName()
        try {
            Map replica = [ deviceId:deviceId, componentId:componentId, replicaId:(app.getId()), type:( getChildDevice( replicaDevice?.deviceNetworkId )!=null ? 'child' : 'mirror')]
            setReplicaDataJsonValue(replicaDevice, "replica", replica)
            clearReplicaDataCache(replicaDevice, "capabilities", true)
            clearReplicaDataCache(replicaDevice, "description", true)
            clearReplicaDataCache(replicaDevice, "health", true)
            clearReplicaDataCache(replicaDevice, "status", true)
            replicaDeviceRefresh(replicaDevice)
    
            logInfo "${app.getLabel()} replaced device'${replicaDevice.getDisplayName()}' with deviceId: $deviceId and deviceNetworkId: $replicaDevice.deviceNetworkId"                    
            app.updateSetting( "pageConfigureDeviceReplicaDevice", [type:"enum", value: replicaDevice.deviceNetworkId] )
            app.updateSetting( "pageHubiThingDeviceModify", [type:"enum", value: replicaDevice.deviceNetworkId] )
            response = statusMsg("'$label' was replaced with deviceId: $deviceId and deviceNetworkId: $replicaDevice.deviceNetworkId")
        } catch (e) {
            logWarn "${app.getLabel()} error reassigning $label: ${e}"
            response = errorMsg("Error: '$label' was not replaced") 
        } 
    }
    return response
}

String getHubitatDeviceStats(hubitatDevice) {
    String hubitatStats =  ""
    if(hubitatDevice) {
        hubitatStats += "Device Type: ${hubitatDevice?.getTypeName()}\n"
        hubitatStats += "Capabilities: ${hubitatDevice?.getCapabilities()?.collect{it?.toString()?.uncapitalize()}?.unique().sort()?.join(', ')}\n"
        hubitatStats += "Commands: ${hubitatDevice?.getSupportedCommands()?.sort{it.toString()}?.unique()?.join(', ')}\n"
        hubitatStats += "Attributes: ${hubitatDevice?.getSupportedAttributes()?.sort{it.toString()}?.unique()?.join(', ')}"
    }
    return hubitatStats
}

String getSmartDeviceStats(smartDeviceId, smartDeviceComponentId) {
    String smartStats =  ""
    if(smartDeviceId) {
        Map smartDevices = getSmartDevicesMap()
        
        List smartCapabilities = []
        smartDevices?.items?.find{it.deviceId == smartDeviceId}?.components?.each{ component ->        
            if(component.id == smartDeviceComponentId) {
                component?.capabilities?.each { capability ->
                    smartCapabilities.add("$capability.id")
                }
            }
        }       
        smartStats += "Device Type: ${smartDevices?.items?.find{it.deviceId == smartDeviceId}?.deviceTypeName ?: (smartDevices?.items?.find{it.deviceId == smartDeviceId}?.name ?: "UNKNOWN")}\n"
        smartStats += "Component: ${smartDeviceComponentId}\n"
        smartStats += "Capabilities: ${smartCapabilities?.sort()?.join(', ')}"
    }
    return smartStats
}

void replicaDeviceRefresh(replicaDevice, delay=1) {
    logInfo "${app.getLabel()} refreshing '$replicaDevice' device"
    runIn(delay<1?:1, replicaDeviceRefreshHelper, [data: [deviceNetworkId:(replicaDevice.deviceNetworkId)]])
}

void replicaDeviceRefreshHelper(data) {
    def replicaDevice = getDevice(data?.deviceNetworkId)
    getReplicaDeviceRefresh(replicaDevice)    
}

void getReplicaDeviceRefresh(replicaDevice) {
    logDebug "${app.getLabel()} executing 'getReplicaDeviceRefresh($replicaDevice)'"    
    
    String deviceId = getReplicaDeviceId(replicaDevice)    
    if(deviceId) {
        replicaDeviceSubscribe(replicaDevice)
        getSmartDeviceStatus(deviceId)
        pauseExecution(250) // no need to hammer ST
        getSmartDeviceHealth(deviceId)
        pauseExecution(250) // no need to hammer ST
        getSmartDeviceDescription(deviceId)       
    } 
    else if(replicaDevice) {
        unsubscribe(replicaDevice)
    }
}

void replicaDeviceSubscribe(replicaDevice) {
    if(replicaDevice) {
        Map replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules")
        List<String> ruleAttributes = replicaDeviceRules?.components?.findAll{ it.type == "hubitatTrigger" && it?.trigger?.type == "attribute" }?.collect{ rule -> rule?.trigger?.name }?.unique()
        List<String> appSubscriptions = app.getSubscriptions()?.findAll{ it?.deviceId?.toInteger() == replicaDevice?.id?.toInteger() }?.collect{ it?.data }?.unique()
        
        if(ruleAttributes) { appSubscriptions?.intersect(ruleAttributes)?.each{ appSubscriptions?.remove(it); ruleAttributes?.remove(it) } }        
        appSubscriptions?.each{ attribute ->
            logInfo "${app.getLabel()} '$replicaDevice' unsubscribed to $attribute"
            unsubscribe(replicaDevice, attribute)
        }
        ruleAttributes?.each{ attribute ->
            logInfo "${app.getLabel()} '$replicaDevice' subscribed to $attribute"
            subscribe(replicaDevice, attribute, deviceTriggerHandler)
        }
    }
}

def pageMirrorDevice(){    
    Map smartDevices = getSmartDevicesMap()
    
    List smartDevicesSelect = []
    smartDevices?.items?.sort{ a,b -> naturalSort(a.label,b.label) }?.each { smartDevice ->
        smartDevice?.components?.each { component ->
            if( !smartDevicesSelect?.find{ smartDevice.deviceId==it.keySet().getAt(0) }  )
                smartDevicesSelect.add([ "$smartDevice.deviceId" : "$smartDevice.label &ensp; (deviceId: $smartDevice.deviceId)" ])       
        }
    }
    
    List smartComponentsSelect = []
    smartDevices?.items?.find{it.deviceId == pageMirrorDeviceSmartDevice}?.components.each { component ->
        smartComponentsSelect.add(component.id)
    }

    List hubitatDevicesSelect = []
    getAuthorizedDevices().sort{ a,b -> naturalSort(a.getDisplayName(),b.getDisplayName()) }?.each {
        if(pageMirrorDeviceShowAllDevices || !getReplicaDataJsonValue(it, "replica"))
            hubitatDevicesSelect.add([ "$it.deviceNetworkId" : "${it.getDisplayName()} &ensp; (deviceNetworkId: $it.deviceNetworkId)" ])
    }
    
    if(smartComponentsSelect?.size()==1) { 
        app.updateSetting( "pageMirrorDeviceSmartDeviceComponent", [type:"enum", value: smartComponentsSelect?.get(0)] )
    }

    def hubitatDevice = getDevice( pageMirrorDeviceHubitatDevice )
    String hubitatStats =  getHubitatDeviceStats(hubitatDevice)
    String smartStats = getSmartDeviceStats(pageMirrorDeviceSmartDevice, pageMirrorDeviceSmartDeviceComponent)

    Integer refreshInterval = (g_mAppDeviceSettings?.pageMirrorDeviceMirrorButton || g_mAppDeviceSettings?.pageHubiThingDeviceModifyButton) ? 15 : 0
    return dynamicPage(name: "pageMirrorDevice", uninstall: false, refreshInterval:refreshInterval) {
        displayHeader()        

        section(menuHeader("Mirror HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) { 
            input(name: "pageMirrorDeviceSmartDevice", type: "enum", title: "&ensp;$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
            if(pageMirrorDeviceSmartDevice && smartComponentsSelect?.size()>1)
                input(name: "pageMirrorDeviceSmartDeviceComponent", type: "enum", title: "$sSamsungIcon Select SmartThings Device Component:", description: "Choose a Device Component", options: smartComponentsSelect, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            paragraph( smartStats )
            paragraph( getFormat("line") )
            
            input(name: "pageMirrorDeviceHubitatDevice", type: "enum", title: "&ensp;$sHubitatIcon Select Hubitat Device:", description: "Choose a Hubitat device", options: hubitatDevicesSelect, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            paragraph( hubitatStats )
            input(name: "pageMirrorDeviceShowAllDevices", type: "bool", title: "Show All Authorized Hubitat Devices", defaultValue: false, submitOnChange: true, width: 6, newLineAfter:true)
            input(name: "dynamic::pageMirrorDeviceMirrorButton", type: "button", width: 2, title: "$sHubitatIcon Mirror", style:"width:75%;")
            
            if( g_mAppDeviceSettings?.pageMirrorDeviceMirrorButton) {
                paragraph( getFormat("line") )
                paragraph( g_mAppDeviceSettings.pageMirrorDeviceMirrorButton )
                g_mAppDeviceSettings.pageMirrorDeviceMirrorButton = null
                href "pageConfigureDevice", title: "$sImgRule Configure HubiThings Rules", description: "Click to show"                
            }
        }
        commonReplicaDevicesSection("pageMirrorDevice")
    }
}

void pageMirrorDeviceMirrorButton() {
    logDebug "${app.getLabel()} executing 'pageMirrorDeviceMirrorButton()' $pageMirrorDeviceSmartDevice $pageMirrorDeviceSmartDeviceComponent $pageMirrorDeviceHubitatDevice"    
    if(!pageMirrorDeviceSmartDevice)
        g_mAppDeviceSettings['pageMirrorDeviceMirrorButton'] = errorMsg("Error: SmartThings Device selection is invalid")
    else if(!pageMirrorDeviceSmartDeviceComponent)
        g_mAppDeviceSettings['pageMirrorDeviceMirrorButton'] = errorMsg("Error: SmartThings Device Component selection is invalid")
    else if(!pageMirrorDeviceHubitatDevice)
        g_mAppDeviceSettings['pageMirrorDeviceMirrorButton'] = errorMsg("Error: Hubitat Device selection is invalid") 
    else
        g_mAppDeviceSettings['pageMirrorDeviceMirrorButton'] = createMirrorDevice(pageMirrorDeviceSmartDevice, pageMirrorDeviceSmartDeviceComponent, pageMirrorDeviceHubitatDevice)
}

String createMirrorDevice(String deviceId, String componentId, String deviceNetworkId) {    
    String response = errorMsg("Error: Could not find Hubitat device to mirror")    
    def replicaDevice = getDevice(deviceNetworkId)
    
    if(replicaDevice) {  
        Map replica = [ deviceId:deviceId, componentId:componentId, replicaId:(app.getId()), type:( getChildDevice( replicaDevice?.deviceNetworkId )!=null ? 'child' : 'mirror')]
        setReplicaDataJsonValue(replicaDevice, "replica", replica)
        clearReplicaDataCache(replicaDevice, "capabilities", true)
        clearReplicaDataCache(replicaDevice, "description", true)
        clearReplicaDataCache(replicaDevice, "health", true)
        clearReplicaDataCache(replicaDevice, "status", true)
        replicaDeviceRefresh(replicaDevice)            
    
        logInfo "${app.getLabel()} mirrored device'${replicaDevice.getDisplayName()}' with deviceId: $deviceId and deviceNetworkId: $replicaDevice.deviceNetworkId"                    
        app.updateSetting( "pageConfigureDeviceReplicaDevice", [type:"enum", value: replicaDevice.deviceNetworkId] )
        app.updateSetting( "pageHubiThingDeviceModify", [type:"enum", value: replicaDevice.deviceNetworkId] )
        response = statusMsg("'${replicaDevice.getDisplayName()}' was mirrored with deviceId: $deviceId and deviceNetworkId: $replicaDevice.deviceNetworkId")
    }
    return response
}

def pageVirtualDevice() {
    
    List virtualDeviceTypesSelect = []    
    getVirtualDeviceTypes()?.sort{ it.id }?.each {
        virtualDeviceTypesSelect.add([ "${it.id}" : "${it.name} ${it?.attributes ? " &ensp; (${it.attributes.sort().join(', ')})" : ""}" ])   
    }    
   
    Map allSmartLocations = getSmartLocations()
    List smartLocationSelect = []    
    allSmartLocations?.items?.sort{ it.name }.each { smartLocation ->
        smartLocationSelect.add([ "${smartLocation.locationId}" : "$smartLocation.name &ensp; (locationId: $smartLocation.locationId)" ])   
    }

    Map allSmartRooms = getSmartRooms(pageVirtualDeviceLocation)    
    List smartRoomSelect = []    
    allSmartRooms?.items?.sort{ it.name }.each { smartRoom ->
        smartRoomSelect.add([ "${smartRoom.roomId}" : "$smartRoom.name &ensp; (roomId: $smartRoom.roomId)" ])   
    }
    
    Map allSmartScenes = getSmartScenes(pageVirtualDeviceLocation)
    List smartSceneSelect = []    
    allSmartScenes?.items?.sort{ it.name }.each { smartScene ->
        smartSceneSelect.add([ "${smartScene.sceneId}" : "$smartScene.sceneName &ensp; (sceneId: $smartScene.sceneId)" ])   
    }

    List oauthSelect = []
    getChildApps()?.each{ oauth ->                
        if(pageVirtualDeviceLocation==oauth?.getLocationId()) {
            Integer deviceCount = oauth?.getSmartDeviceSelectList()?.size() ?: 0
            String locationName = allSmartLocations?.items?.find{ it.locationId==oauth?.getLocationId() }?.name ?: oauth?.getLocationId()
            if(oauth?.getMaxDeviceLimit() - deviceCount > 0) {
                oauthSelect.add([ "${oauth?.getId()}" : "${oauth?.getLabel()} ${deviceCount} of ${oauth?.getMaxDeviceLimit()} ($locationName)" ])
            }
        }
    }
    
    Map allVirtualDevices = getVirtualDevices()
    List virtualDeviceDeleteSelect = []    
    allVirtualDevices?.items?.sort{ a,b -> naturalSort(a.label,b.label) }?.each { smartDevice ->
        virtualDeviceDeleteSelect.add([ "${smartDevice.deviceId}" : "$smartDevice.label &ensp; (deviceId: $smartDevice.deviceId)" ])   
    }    
        
    if(pageVirtualDeviceModify!=g_mAppDeviceSettings?.pageVirtualDeviceModify) {
        g_mAppDeviceSettings['pageVirtualDeviceModify'] = pageVirtualDeviceModify
        String deviceLabel = allVirtualDevices?.items?.find{it.deviceId == pageVirtualDeviceModify}?.label ?: ""
        app.updateSetting( "pageVirtualDeviceLabel", deviceLabel )
    }
    
    Integer refreshInterval = (g_mAppDeviceSettings?.pageVirtualDeviceCreateButton || g_mAppDeviceSettings?.pageVirtualDeviceModifyButtons) ? 15 : 0    
    return dynamicPage(name: "pageVirtualDevice", uninstall: false, refreshInterval:refreshInterval) {
        displayHeader()

        section(menuHeader("Create Virtual Device, Mode or Scene $sHubitatIconStatic $sSamsungIconStatic")) {
            
            if(getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.replicaType == 'location') {
                paragraph( getFormat("comments","<b>Location Mode Knob</b> allows for creation, deletion, updating and mirroring the SmartThings mode within a Hubitat Device. Similar to Hubitat, each SmartThings location only supports a singular mode.",null,"Gray") ) 
                app.updateSetting("pageVirtualDeviceEnableMirrorHubitatDevice", false)
            }
                
            if(getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.replicaType == 'scene') {
                paragraph( getFormat("comments","<b>Scene Knob</b> allows for triggering and mirroring a SmartThings scene within a Hubitat Device. <b>NOTE for proper usage:</b> A SmartThings virtual switch will be created, and it <b>must be added</b> to the SmartThings Scene 'actions' to update the switch to 'Turn on' when the scene is triggered.",null,"Gray") ) 
                app.updateSetting("pageVirtualDeviceEnableMirrorHubitatDevice", false)
            }
                
            input(name: "pageVirtualDeviceType", type: "enum", title: "&ensp;$sSamsungIcon Select SmartThings Virtual Device Type:", description: "Choose a SmartThings virtual device type", multiple: false, options: virtualDeviceTypesSelect, required: false, submitOnChange: true, width: 6, newLineAfter:true)
            input(name: "pageVirtualDeviceLocation", type: "enum", title: "&ensp;$sSamsungIcon Select SmartThings Location:", description: "Choose a SmartThings location", multiple: false, options: smartLocationSelect, required: false, submitOnChange: true, width: 6, newLineAfter:true)
            input(name: "pageVirtualDeviceRoom", type: "enum", title: "&ensp;$sSamsungIcon Select SmartThings Room:", description: "Choose a SmartThings room (not required)", multiple: false, options: smartRoomSelect, submitOnChange: true, width: 6, newLineAfter:true)
           
            if(getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.replicaType == 'scene') 
                input(name: "pageVirtualDeviceScene", type: "enum", title: "&ensp;$sSamsungIcon Select SmartThings Scene:", description: "Choose a SmartThings scene", multiple: false, options: smartSceneSelect, required: false, submitOnChange: true, width: 6, newLineAfter:true)
 
            if(oauthSelect?.size())
               input(name: "pageVirtualDeviceOauth", type: "enum", title: "&ensp;$sHubitatIcon Select HubiThings OAuth:", description: "Choose a HubiThings OAuth (not required)", multiple: false, options: oauthSelect, submitOnChange: true, width: 6, newLineAfter:true)
            else {
               app.removeSetting('pageVirtualDeviceOauth')
               paragraph("No HubiThings OAuth authorized for this location or have reached maximum capacity of devices.") 
            }
            
            if(oauthSelect?.size() && pageVirtualDeviceOauth)
                input(name: "pageVirtualDeviceEnableMirrorHubitatDevice", type: "bool", title: "Mirror Existing Hubitat Devices", defaultValue: false, submitOnChange: true, width: 6, newLineAfter:true)
            
            if(pageVirtualDeviceEnableMirrorHubitatDevice && oauthSelect?.size() && pageVirtualDeviceOauth) {
                List hubitatDevicesSelect = []
                getAuthorizedDevices().sort{ a,b -> naturalSort(a.getDisplayName(),b.getDisplayName()) }?.each {
                if(pageMirrorDeviceShowAllDevices || !getReplicaDataJsonValue(it, "replica"))
                    hubitatDevicesSelect.add([ "$it.deviceNetworkId" : "${it.getDisplayName()} &ensp; (deviceNetworkId: $it.deviceNetworkId)" ])
                }
                
                pageAuthDeviceUserInputCtl()
 
                input(name: "pageVirtualDeviceHubitatDevice", type: "enum", title: "&ensp;$sHubitatIcon Select Hubitat Device:", description: "Choose a Hubitat device", options: hubitatDevicesSelect, required: false, submitOnChange:true, width: 6, newLineAfter:true)
                paragraph( hubitatStats )             
                input(name: "pageMirrorDeviceShowAllDevices", type: "bool", title: "Show All Authorized Hubitat Devices", defaultValue: false, submitOnChange: true, width: 6, newLineAfter:true)
                input(name: "dynamic::pageVirtualDeviceCreateButton", type: "button", width: 2, title: "$sHubitatIcon Mirror", style:"width:75%;", newLineAfter:true)
            }
            else {                    
                List hubitatDeviceTypes = []
                getDeviceHandlers()?.items?.sort{ a,b -> b?.namespace <=> a?.namespace ?: a?.name <=> b?.name }?.each {    
                    hubitatDeviceTypes.add([ "$it.id" : "$it.name &ensp; (namespace: $it.namespace)" ])   
                }
                
                if(pageVirtualDeviceType!=g_mAppDeviceSettings?.pageVirtualDeviceType) {
                    g_mAppDeviceSettings['pageVirtualDeviceType'] = pageVirtualDeviceType
                    String replicaName = getVirtualDeviceTypes()?.find{it.id.toString() == pageVirtualDeviceType}?.replicaName ?: ""
                    String hubitatType = hubitatDeviceTypes?.find{ it?.find{ k,v -> v?.contains( replicaName ) } }?.keySet()?.getAt(0)
                    app.updateSetting( "pageVirtualDeviceHubitatType", [type:"enum", value: (replicaName!=""?hubitatType:null)] )
                }
                
                if(oauthSelect?.size() && pageVirtualDeviceOauth)
                   input(name: "pageVirtualDeviceHubitatType", type: "enum", title: "&ensp;$sHubitatIcon Select Hubitat Device Type:", description: "Choose a Hubitat device type (not required)", options: hubitatDeviceTypes, submitOnChange:true, width: 6, newLineAfter:true)
                input(name: "dynamic::pageVirtualDeviceCreateButton",  type: "button", width: 2, title: "$sSamsungIcon Create", style:"width:75%;", newLineAfter:true)
            }
            
            if(g_mAppDeviceSettings?.pageVirtualDeviceCreateButton)  {
                paragraph( getFormat("line") )
                paragraph( g_mAppDeviceSettings.pageVirtualDeviceCreateButton )
                g_mAppDeviceSettings.pageVirtualDeviceCreateButton = null
                href "pageConfigureDevice", title: "$sImgRule Configure HubiThings Rules", description: "Click to show"
            }
        }
        section(menuHeader("Modify Virtual Device, Mode or Scene")) {
            paragraph( getFormat("comments","<b>Create</b> and <b>Remove</b> utilize the SmartThings Cloud API to create and delete subscriptions. SmartThings enforces a rate limit of 15 requests per 15 minutes to query the subscription API for status updates.",null,"Gray") ) 
            input(name: "pageVirtualDeviceModify", type: "enum",  title: "&ensp;$sSamsungIcon Select SmartThings Virtual Device:", description: "Choose a SmartThings virtual device", multiple: false, options: virtualDeviceDeleteSelect, submitOnChange: true, width: 6, newLineAfter:true)
            input(name: "pageVirtualDeviceLabel", type: "text", title: "$sSamsungIcon SmartThings Virtual Device Label:", submitOnChange: true, width: 6, newLineAfter:true)           
            input(name: "dynamic::pageVirtualDeviceRenameButton",  type: "button", width: 2, title: "$sSamsungIcon Rename", style:"width:75%;")
            input(name: "dynamic::pageVirtualDeviceRemoveButton",  type: "button", width: 2, title: "$sSamsungIcon Remove", style:"width:75%;", newLineAfter:true)
            if(g_mAppDeviceSettings?.pageVirtualDeviceModifyButtons) {
                paragraph( g_mAppDeviceSettings?.pageVirtualDeviceModifyButtons )
                g_mAppDeviceSettings.pageVirtualDeviceModifyButtons = null
            }
        }
        virtualDevicesSection(allVirtualDevices, allSmartLocations)        
    }
}

String virtualDevicesSection(Map allVirtualDevices, Map allSmartLocations) {    
    String devicesTable  = "<table id='devicesTable' role='table' class='compact' style='width:100%;'>"
           devicesTable += "<thead><tr><th>$sSamsungIcon Device</th><th>$sHubitatIcon Device</th><th>$sSamsungIcon Type</th><th style='text-align:center;'>$sSamsungIcon Location</th></tr></thead><tbody>"    
    allVirtualDevices?.items?.sort{ a,b -> naturalSort(a.label,b.label) }.each { virtualDevice ->
        List hubitatDevices = getReplicaDevices(virtualDevice.deviceId)
		for (Integer i = 0; i ==0 || i < hubitatDevices.size(); i++) {
            def replicaDevice = hubitatDevices[i]?:null
            String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice?.getId()}"                            
            String location = allSmartLocations?.items?.find{ it.locationId==virtualDevice.locationId }?.name ?: virtualDevice.locationId
            devicesTable += "<tr><td>${virtualDevice.label}</td>"
            devicesTable += replicaDevice ? "<td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${replicaDevice?.getDisplayName()}</a></td>" : "<td>--</td>"            
            devicesTable += "<td>${virtualDevice.name}</td>"
            devicesTable += "<td style='text-align:center;'>$location</td></tr>"
        }
    }
    devicesTable +="</tbody></table>"
    if(allVirtualDevices?.items?.size() > iUseJqueryDataTables) {
        devicesTable += """<script>$naturalSortFunction \$(document).ready(function () { \$('#devicesTable').DataTable( { destroy: true, stateSave: true, lengthMenu:[ [25, 50, 100, -1], [25, 50, 100, "All"] ], columnDefs:[ { type:'natural-nohtml', targets:'_all' } ]} );});</script>"""                
        //devicesTable += """<script>\$(document).ready(function () { \$('#devicesTable').DataTable( { stateSave: true, lengthMenu:[ [25, 50, 100, -1], [25, 50, 100, "All"] ] } );});</script>"""
        devicesTable += """<style>#childDeviceList tbody tr.even:hover { background-color: #F5F5F5 !important; }</style>"""
    } else {
        devicesTable += """<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>"""
    }
    devicesTable += """<style>@media screen and (max-width:800px) { table th:nth-of-type(3),td:nth-of-type(3) { display: none; } }</style>"""    
            
    section(menuHeader("SmartThings Virtual Device List")) {
        if(allVirtualDevices?.items?.size()) {
            paragraph( devicesTable )            
        }
        input(name: "dynamic::pageVirtualDeviceButtonRefresh",  type: "button", width: 2, title: "$sSamsungIcon Refresh", style:"width:75%;")
    }        
}

void pageVirtualDeviceCreateButton() {
    logDebug "${app.getLabel()} executing 'pageVirtualDeviceCreateButton()' $pageVirtualDeviceType $pageVirtualDeviceLocation $pageVirtualDeviceRoom $pageVirtualDeviceOauth $pageHubiThingDeviceType $pageVirtualDeviceHubitatDevice"
    String name = getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.name
    String prototype = getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.typeId
    
    if(!pageVirtualDeviceType)
        g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = errorMsg("Error: SmartThings Virtual Device Type selection is invalid")
    else if(!pageVirtualDeviceLocation)
        g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = errorMsg("Error: SmartThings Location selection is invalid")
    else if(getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.replicaType == 'scene' && !pageVirtualDeviceScene)
        g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = errorMsg("Error: SmartThings Scene selection is invalid")        
    else {
        Map response = createVirtualDevice(pageVirtualDeviceLocation, pageVirtualDeviceRoom, name, prototype)
        if(response?.statusCode==200) {
            g_mVirtualDeviceListCache[app.getId()]=null
            app.updateSetting( "pageVirtualDeviceModify", [type:"enum", value: response?.data?.deviceId] )
            g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = statusMsg("'$name' was created with deviceId: ${response?.data?.deviceId}")
            
            setVirtualDeviceDefaults(response?.data?.deviceId, prototype)
            
            if(pageVirtualDeviceOauth) {
                getChildAppById(pageVirtualDeviceOauth.toLong())?.createSmartDevice(pageVirtualDeviceLocation, response?.data?.deviceId, true)
            }
            if(pageVirtualDeviceOauth&&(pageVirtualDeviceHubitatType&&!pageVirtualDeviceEnableMirrorHubitatDevice || pageVirtualDeviceHubitatDevice&&pageVirtualDeviceEnableMirrorHubitatDevice)) {
                String deviceId = response?.data?.deviceId
                String componentId = "main"
                if(pageVirtualDeviceEnableMirrorHubitatDevice) {
                    if(getVirtualDeviceRules(prototype)) setReplicaDataValue(getDevice(pageVirtualDeviceHubitatDevice), "rules", getVirtualDeviceRules(prototype))
                    g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = createMirrorDevice(deviceId, componentId, pageVirtualDeviceHubitatDevice)
                    renameVirtualDevice(deviceId, getDevice(pageVirtualDeviceHubitatDevice)?.getDisplayName())
                }
                else {
                    Map deviceType = getDeviceHandlers()?.items?.find{ it?.id==pageVirtualDeviceHubitatType }
                    String label = name                    
                    g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = createChildDevice(deviceType, name, label, deviceId, componentId)
                }                
            }
            if(getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.replicaType == 'scene') {
                Map allSmartScenes = getSmartScenes(pageVirtualDeviceLocation)
                String sceneName = "Scene - ${allSmartScenes?.items?.find{ it?.sceneId==pageVirtualDeviceScene }?.sceneName}"
                app.updateSetting( "pageVirtualDeviceLabel", [type:"enum", value: sceneName] )
                pageVirtualDeviceRenameButton()
                getReplicaDevices(pageVirtualDeviceModify)?.each{ replicaDevice->
                    replicaDevice?.setSceneIdValue( pageVirtualDeviceScene )
                }                   
            }
            if(getVirtualDeviceTypes()?.find{ it?.id==pageVirtualDeviceType?.toInteger() }?.replicaType == 'location') {
                Map allSmartLocations = getSmartLocations()
                String locationName = "Location - ${allSmartLocations?.items?.find{ it?.locationId==pageVirtualDeviceLocation }?.name}"
                app.updateSetting( "pageVirtualDeviceLabel", [type:"enum", value: locationName] )
                pageVirtualDeviceRenameButton()
            }
        }
        else
            g_mAppDeviceSettings['pageVirtualDeviceCreateButton'] = errorMsg("Error: '$name' was not created")
    }
}

void pageVirtualDeviceRemoveButton() {
    logDebug "${app.getLabel()} executing 'pageVirtualDeviceRemoveButton()' $pageVirtualDeviceModify"
    if(pageVirtualDeviceModify) {
        Map allVirtualDevices = getVirtualDevices()
        String label = allVirtualDevices?.items?.find{ it?.deviceId==pageVirtualDeviceModify }?.label ?: "unknown device"
        if(deleteSmartDevice(pageVirtualDeviceModify)?.statusCode==200) {
            g_mVirtualDeviceListCache[app.getId()]=null
            g_mAppDeviceSettings['pageVirtualDeviceModifyButtons'] = statusMsg("'$label' was removed with deviceId: $pageVirtualDeviceModify")
            getReplicaDevices(pageVirtualDeviceModify)?.each{ replicaDevice->
                deleteChildDevice( replicaDevice?.deviceNetworkId )
            }
            app.removeSetting("pageVirtualDeviceModify")
        }
        else
            g_mAppDeviceSettings['pageVirtualDeviceModifyButtons'] = errorMsg("Error: '$label' was not removed")
    }
}

void pageVirtualDeviceRenameButton() {
    logDebug "${app.getLabel()} executing 'pageVirtualDeviceRenameButton()' $pageVirtualDeviceModify $pageVirtualDeviceLabel"
    if(pageVirtualDeviceModify && pageVirtualDeviceLabel) {
        Map allVirtualDevices = getVirtualDevices()
        String label = allVirtualDevices?.items?.find{ it?.deviceId==pageVirtualDeviceModify }?.label
        if(renameVirtualDevice(pageVirtualDeviceModify, pageVirtualDeviceLabel)?.statusCode==200) {
            g_mVirtualDeviceListCache[app.getId()]=null
            g_mAppDeviceSettings['pageVirtualDeviceModifyButtons'] = statusMsg("'$label' was renamed to '$pageVirtualDeviceLabel'")
            getReplicaDevices(pageVirtualDeviceModify)?.each{ replicaDevice->
                if(replicaDevice?.getLabel()==label) replicaDevice?.setLabel( pageVirtualDeviceLabel )
            }                
        }
        else
            g_mAppDeviceSettings['pageVirtualDeviceModifyButtons'] = errorMsg("Error: '$label' was not renamed to '$pageVirtualDeviceLabel'")
    }
}

void pageVirtualDeviceButtonRefresh() {
    logDebug "${app.getLabel()} executing 'pageVirtualDeviceButtonRefresh()'"
    g_mVirtualDeviceListCache[app.getId()] = null
    g_mSmartLocationListCache[app.getId()] = null
    g_mSmartRoomListCache[app.getId()] = null
    g_mSmartSceneListCache[app.getId()] = null
}

static final String getVirtualDeviceRules(String typeId) {
     switch(typeId) {
         case 'VIRTUAL_SWITCH':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"switch","type":"attribute","label":"attribute: switch.off","value":"off"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"switch","type":"attribute","label":"attribute: switch.on","value":"on"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.off","value":"off","dataType":"ENUM"},"command":{"name":"off","type":"command","label":"command: off()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.on","value":"on","dataType":"ENUM"},"command":{"name":"on","type":"command","label":"command: on()"},"type":"smartTrigger"}]}"""
         case 'VIRTUAL_DIMMER':
             return """{"version":1,"components":[{"trigger":{"dataType":"NUMBER","name":"level","type":"attribute","label":"attribute: level.*"},"command":{"name":"setLevel","arguments":[{"name":"level","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}},{"name":"rate","optional":true,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"switchLevel","label":"command: setLevel(level*, rate)"},"type":"hubitatTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"switchLevel","attribute":"level","label":"attribute: level.*"},"command":{"arguments":["NUMBER","NUMBER"],"parameters":[{"name":"Level*","type":"NUMBER","constraints":["NUMBER"]},{"name":"Duration","type":"NUMBER","constraints":["NUMBER"]}],"name":"setLevel","type":"command","label":"command: setLevel(level*, duration)"},"type":"smartTrigger"}]}"""
         case 'VIRTUAL_DIMMER_SWITCH':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"switch","type":"attribute","label":"attribute: switch.on","value":"on"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"switch","type":"attribute","label":"attribute: switch.off","value":"off"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"level","type":"attribute","label":"attribute: level.*"},"command":{"name":"setLevel","arguments":[{"name":"level","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}},{"name":"rate","optional":true,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"switchLevel","label":"command: setLevel(level*, rate)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.off","value":"off","dataType":"ENUM"},"command":{"name":"off","type":"command","label":"command: off()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.on","value":"on","dataType":"ENUM"},"command":{"name":"on","type":"command","label":"command: on()"},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"switchLevel","attribute":"level","label":"attribute: level.*"},"command":{"arguments":["NUMBER","NUMBER"],"parameters":[{"name":"Level*","type":"NUMBER","constraints":["NUMBER"]},{"name":"Duration","type":"NUMBER","constraints":["NUMBER"]}],"name":"setLevel","type":"command","label":"command: setLevel(level*, duration)"},"type":"smartTrigger","disableStatus":true}]}"""
         case 'VIRTUAL_BUTTON':
             return """{"version":1,"components":[{"trigger":{"dataType":"NUMBER","name":"pushed","type":"attribute","label":"attribute: pushed.*"},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"numberOfButtons","type":"attribute","label":"attribute: numberOfButtons.*"},"command":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"numberOfButtons","label":"attribute: numberOfButtons.*"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"held","type":"attribute","label":"attribute: held.*"},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.held","value":"held","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"doubleTapped","type":"attribute","label":"attribute: doubleTapped.*"},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.double","value":"double","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"released","type":"attribute","label":"attribute: released.*"},"command":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string","enum":["pushed","held","double","pushed_2x","pushed_3x","pushed_4x","pushed_5x","pushed_6x","down","down_2x","down_3x","down_4x","down_5x","down_6x","down_hold","up","up_2x","up_3x","up_4x","up_5x","up_6x","up_hold","swipe_up","swipe_down","swipe_left","swipe_right"]}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.up","value":"up","dataType":"ENUM"},"type":"hubitatTrigger"}]}"""
         case 'VIRTUAL_CONTACT_SENSOR':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"contact","type":"attribute","label":"attribute: contact.*"},"command":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string","enum":["closed","open"]}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.closed","value":"closed","dataType":"ENUM"},"command":{"name":"close","type":"command","label":"command: close()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.open","value":"open","dataType":"ENUM"},"command":{"name":"open","type":"command","label":"command: open()"},"type":"smartTrigger"}]}"""
         case 'VIRTUAL_GARAGE_DOOR_OPENER':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"contact","type":"attribute","label":"attribute: contact.*"},"command":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string","enum":["closed","open"]}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"doorControl","attribute":"door","label":"attribute: door.closed","value":"closed","dataType":"ENUM"},"command":{"name":"close","type":"command","label":"command: close()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"doorControl","attribute":"door","label":"attribute: door.open","value":"open","dataType":"ENUM"},"command":{"name":"open","type":"command","label":"command: open()"},"type":"smartTrigger"},{"trigger":{"dataType":"ENUM","name":"door","type":"attribute","label":"attribute: door.closed","value":"closed"},"command":{"type":"attribute","properties":{"value":{"type":"string","enum":["closed","closing","open","opening","unknown"]}},"additionalProperties":false,"required":["value"],"capability":"doorControl","attribute":"door","label":"attribute: door.closed","value":"closed","dataType":"ENUM"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"door","type":"attribute","label":"attribute: door.open","value":"open"},"command":{"type":"attribute","properties":{"value":{"type":"string","enum":["closed","closing","open","opening","unknown"]}},"additionalProperties":false,"required":["value"],"capability":"doorControl","attribute":"door","label":"attribute: door.open","value":"open","dataType":"ENUM"},"type":"hubitatTrigger"}]}"""         
         case 'VIRTUAL_LOCK':         
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"lock","type":"attribute","label":"attribute: lock.*"},"command":{"type":"attribute","properties":{"value":{"title":"LockState","type":"string","enum":["locked","unknown","unlocked","unlocked with timeout"]},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"method":{"type":"string","enum":["manual","keypad","auto","command","rfid","fingerprint","bluetooth"]},"codeId":{"type":"string"},"codeName":{"type":"string"},"timeout":{"title":"Iso8601Date","type":"string"}}}},"additionalProperties":false,"required":["value"],"capability":"lock","attribute":"lock","label":"attribute: lock.*"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"LockState","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"method":{"type":"string","enum":["manual","keypad","auto","command","rfid","fingerprint","bluetooth"]},"codeId":{"type":"string"},"codeName":{"type":"string"},"timeout":{"title":"Iso8601Date","type":"string"}}}},"additionalProperties":false,"required":["value"],"capability":"lock","attribute":"lock","label":"attribute: lock.locked","value":"locked","dataType":"ENUM"},"command":{"name":"lock","type":"command","label":"command: lock()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"LockState","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"method":{"type":"string","enum":["manual","keypad","auto","command","rfid","fingerprint","bluetooth"]},"codeId":{"type":"string"},"codeName":{"type":"string"},"timeout":{"title":"Iso8601Date","type":"string"}}}},"additionalProperties":false,"required":["value"],"capability":"lock","attribute":"lock","label":"attribute: lock.unlocked","value":"unlocked","dataType":"ENUM"},"command":{"name":"unlock","type":"command","label":"command: unlock()"},"type":"smartTrigger"},{"trigger":{"dataType":"NUMBER","name":"battery","type":"attribute","label":"attribute: battery.*"},"command":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"type":"hubitatTrigger","mute":true}]}"""
         case 'VIRTUAL_METERED_SWITCH':
             return """{"version":1,"components":[{"trigger":{"dataType":"NUMBER","name":"energy","type":"attribute","label":"attribute: energy.*"},"command":{"type":"attribute","properties":{"value":{"type":"number"},"unit":{"type":"string","enum":["Wh","kWh","mWh","kVAh"],"default":"kWh"}},"additionalProperties":false,"required":["value"],"capability":"energyMeter","attribute":"energy","label":"attribute: energy.*"},"type":"hubitatTrigger","mute":true},{"trigger":{"dataType":"NUMBER","name":"power","type":"attribute","label":"attribute: power.*"},"command":{"type":"attribute","properties":{"value":{"type":"number"},"unit":{"type":"string","enum":["W"],"default":"W"}},"additionalProperties":false,"required":["value"],"capability":"powerMeter","attribute":"power","label":"attribute: power.*"},"type":"hubitatTrigger","mute":true},{"trigger":{"dataType":"ENUM","name":"switch","type":"attribute","label":"attribute: switch.off","value":"off"},"command":{"name":"off","type":"command","capability":"switch","label":"command: switch:off()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"switch","type":"attribute","label":"attribute: switch.on","value":"on"},"command":{"name":"on","type":"command","capability":"switch","label":"command: switch:on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.off","value":"off","dataType":"ENUM"},"command":{"name":"off","type":"command","label":"command: off()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.on","value":"on","dataType":"ENUM"},"command":{"name":"on","type":"command","label":"command: on()"},"type":"smartTrigger"}]}"""
         case 'VIRTUAL_MOTION_SENSOR':
             return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"arguments":["Number"],"parameters":[{"type":"Number"}],"name":"setTemperature","type":"command","label":"command: setTemperature(number*)"},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.active","value":"active","dataType":"ENUM"},"command":{"name":"active","type":"command","label":"command: active()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.inactive","value":"inactive","dataType":"ENUM"},"command":{"name":"inactive","type":"command","label":"command: inactive()"},"type":"smartTrigger"},{"trigger":{"dataType":"ENUM","name":"motion","type":"attribute","label":"attribute: motion.*"},"command":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string","enum":["active","inactive"]}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.*"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"battery","type":"attribute","label":"attribute: battery.*"},"command":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"temperature","type":"attribute","label":"attribute: temperature.*"},"command":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"type":"hubitatTrigger","mute":true}]}"""
         case 'VIRTUAL_MULTI_SENSOR':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"acceleration","type":"attribute","label":"attribute: acceleration.*"},"command":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string","enum":["active","inactive"]}},"additionalProperties":false,"required":["value"],"capability":"accelerationSensor","attribute":"acceleration","label":"attribute: acceleration.*"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"battery","type":"attribute","label":"attribute: battery.*"},"command":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"contact","type":"attribute","label":"attribute: contact.*"},"command":{"type":"attribute","properties":{"value":{"title":"ContactState","type":"string","enum":["closed","open"]}},"additionalProperties":false,"required":["value"],"capability":"contactSensor","attribute":"contact","label":"attribute: contact.*"},"type":"hubitatTrigger"},{"trigger":{"dataType":"NUMBER","name":"temperature","type":"attribute","label":"attribute: temperature.*"},"command":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"type":"hubitatTrigger"}]}"""
         case 'VIRTUAL_PRESENCE_SENSOR':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"presence","type":"attribute","label":"attribute: presence.*"},"command":{"type":"attribute","properties":{"value":{"title":"PresenceState","type":"string","enum":["present","not present"]}},"additionalProperties":false,"required":["value"],"capability":"presenceSensor","attribute":"presence","label":"attribute: presence.*"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PresenceState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"presenceSensor","attribute":"presence","label":"attribute: presence.present","value":"present","dataType":"ENUM"},"command":{"name":"arrived","type":"command","label":"command: arrived()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PresenceState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"presenceSensor","attribute":"presence","label":"attribute: presence.not present","value":"not present","dataType":"ENUM"},"command":{"name":"departed","type":"command","label":"command: departed()"},"type":"smartTrigger"}]}"""
         case 'VIRTUAL_SIREN':
             return """{"version":1,"components":[{"trigger":{"dataType":"ENUM","name":"alarm","type":"attribute","label":"attribute: alarm.both","value":"both"},"command":{"name":"both","type":"command","capability":"alarm","label":"command: both()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"alarm","type":"attribute","label":"attribute: alarm.off","value":"off"},"command":{"name":"off","type":"command","capability":"alarm","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"alarm","type":"attribute","label":"attribute: alarm.siren","value":"siren"},"command":{"name":"siren","type":"command","capability":"alarm","label":"command: siren()"},"type":"hubitatTrigger"},{"trigger":{"dataType":"ENUM","name":"alarm","type":"attribute","label":"attribute: alarm.strobe","value":"strobe"},"command":{"name":"strobe","type":"command","capability":"alarm","label":"command: strobe()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AlertState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"alarm","attribute":"alarm","label":"attribute: alarm.both","value":"both","dataType":"ENUM"},"command":{"name":"both","type":"command","label":"command: both()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AlertState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"alarm","attribute":"alarm","label":"attribute: alarm.off","value":"off","dataType":"ENUM"},"command":{"name":"off","type":"command","label":"command: off()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AlertState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"alarm","attribute":"alarm","label":"attribute: alarm.siren","value":"siren","dataType":"ENUM"},"command":{"name":"siren","type":"command","label":"command: siren()"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AlertState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"alarm","attribute":"alarm","label":"attribute: alarm.strobe","value":"strobe","dataType":"ENUM"},"command":{"name":"strobe","type":"command","label":"command: strobe()"},"type":"smartTrigger"}]}"""
         default:
            return null
     }
}

static final List getVirtualDeviceDefaults(String typeId) {
    switch(typeId) {
        case 'VIRTUAL_SWITCH':
            return [ [componentId:"main", capability:"switch", attribute:"switch", value:"off", unit:null, data:null] ]
        case 'VIRTUAL_DIMMER':
            return [ [componentId:"main", capability:"switchLevel", attribute:"level", value:100, unit:null, data:null] ]
        case 'VIRTUAL_DIMMER_SWITCH':
            return [ [componentId:"main", capability:"switch", attribute:"switch", value:"off", unit:null, data:null], [componentId:"main", capability:"switchLevel", attribute:"level", value:100, unit:null, data:null] ]
        case 'VIRTUAL_BUTTON':
            return [ [componentId:"main", capability:"button", attribute:"numberOfButtons", value:1, unit:null, data:null] ]            
        case 'VIRTUAL_CONTACT_SENSOR': 
            return [ [componentId:"main", capability:"contactSensor", attribute:"contact", value:"closed", unit:null, data:null] ]
        case 'VIRTUAL_GARAGE_DOOR_OPENER':
            return [ [componentId:"main", capability:"contactSensor", attribute:"contact", value:"closed", unit:null, data:null], [componentId:"main", capability:"doorControl", attribute:"door", value:"closed", unit:null, data:null] ]
        case 'VIRTUAL_LOCK':
             return [ [componentId:"main", capability:"lock", attribute:"lock", value:"unknown", unit:null, data:null], [componentId:"main", capability:"battery", attribute:"battery", value:100, unit:"%", data:null] ]
        case 'VIRTUAL_METERED_SWITCH':
            return [ [componentId:"main", capability:"healthCheck", attribute:"DeviceWatch-DeviceStatus", value:"online", unit:null, data:null], [componentId:"main", capability:"energyMeter", attribute:"energy", value:0, unit:"kWh", data:null], [componentId:"main", capability:"switch", attribute:"switch", value:"off", unit:null, data:null], [componentId:"main", capability:"powerMeter", attribute:"power", value:0, unit:"W", data:null] ]
        case 'VIRTUAL_MOTION_SENSOR':
            return [ [componentId:"main", capability:"temperatureMeasurement", attribute:"temperature", value:0, unit:"C", data:null], [componentId:"main", capability:"motionSensor", attribute:"motion", value:"inactive", unit:null, data:null], [componentId:"main", capability:"battery", attribute:"battery", value:100, unit:"%", data:null] ]
        case 'VIRTUAL_MULTI_SENSOR':
            return [ [componentId:"main", capability:"accelerationSensor", attribute:"acceleration", value:"inactive", unit:null, data:null], [componentId:"main", capability:"threeAxis", attribute:"threeAxis", value:[0,0,0], unit:null, data:null], [componentId:"main", capability:"battery", attribute:"battery", value:100, unit:"%", data:null], [componentId:"main", capability:"contactSensor", attribute:"contact", value:"closed", unit:null, data:null], [componentId:"main", capability:"temperatureMeasurement", attribute:"temperature", value:0, unit:"C", data:null] ]
        case 'VIRTUAL_PRESENCE_SENSOR':
            return [ [componentId:"main", capability:"presenceSensor", attribute:"presence", value:"not present", unit:null, data:null] ]
        case 'VIRTUAL_SIREN':
            return [ [componentId:"main", capability:"alarm", attribute:"alarm", value:"off", unit:null, data:null] ]       
         default:
            return null
    }
}

static final List getVirtualDeviceTypes() {
    // https://community.smartthings.com/t/smartthings-virtual-devices-using-cli/244347
    // https://community.smartthings.com/t/smartthings-cli-create-virtual-device/249199
    // https://raw.githubusercontent.com/SmartThingsCommunity/smartthings-cli/eb1aab896d4248d293c662317056097aad777438/packages/cli/src/lib/commands/virtualdevices-util.ts
    List devices = [ 
        [id:1, name: "Virtual Switch", typeId: "VIRTUAL_SWITCH", replicaName: "Replica Switch", attributes:["rw:switch"]  ],
        [id:2, name: "Virtual Dimmer Switch", typeId: "VIRTUAL_DIMMER_SWITCH", replicaName: "Replica Dimmer", attributes:["rw:switch","rw:level"] ],
        [id:3, name: "Virtual Button", typeId: "VIRTUAL_BUTTON", replicaName: "Replica Button", attributes:["r:held","r:pushed","r:doubleTapped","r:released"] ],
        //[id:4, name: "Virtual Camera", typeId: "VIRTUAL_CAMERA" ],
        //[id:5, name: "Virtual Color Bulb", typeId: "VIRTUAL_COLOR_BULB" ],
        [id:6, name: "Virtual Contact Sensor", typeId: "VIRTUAL_CONTACT_SENSOR", replicaName: "Replica Multipurpose Sensor", attributes:["r:contact"] ],
        //[id:7, name: "Virtual Dimmer (no switch)", typeId: "VIRTUAL_DIMMER", replicaName: "Replica Dimmer" ], // why does this exist? 
        [id:8, name: "Virtual Garage Door Opener", typeId: "VIRTUAL_GARAGE_DOOR_OPENER", replicaName: "Replica Garage Door", attributes:["r:contact","rw:door"] ],
        [id:9, name: "Virtual Lock", typeId: "VIRTUAL_LOCK", replicaName: "Replica Lock", attributes:["rw:lock","r:battery"] ],
        [id:10, name: "Virtual Metered Switch", typeId: "VIRTUAL_METERED_SWITCH", attributes:["r:energy","r:power","rw:switch"] ],
        [id:11, name: "Virtual Motion Sensor", typeId: "VIRTUAL_MOTION_SENSOR", replicaName: "Replica Motion Sensor", attributes:["r:temperature","r:motion","r:battery"] ],
        [id:12, name: "Virtual Multipurpose Sensor", typeId: "VIRTUAL_MULTI_SENSOR", replicaName: "Replica Multipurpose Sensor", attributes:["r:acceleration","r:contact","r:temperature","r:battery"] ],
        [id:13, name: "Virtual Presence Sensor", typeId: "VIRTUAL_PRESENCE_SENSOR", replicaName: "Replica Presence", attributes:["r:presence"] ],
        //[id:14, name: "Virtual Refrigerator", typeId: "VIRTUAL_REFRIGERATOR" ],
        //[id:15, name: "Virtual RGBW Bulb", typeId: "VIRTUAL_RGBW_BULB" ],
        [id:16, name: "Virtual Siren", typeId: "VIRTUAL_SIREN", replicaName: "Replica Alarm", attributes:["rw:alarm"] ],
        //[id:17, name: "Virtual Thermostat", typeId: "VIRTUAL_THERMOSTAT" ],
        [id:18, name: "Location Mode Knob", typeId: "VIRTUAL_SWITCH", replicaName: "Replica Location Knob", replicaType: "location", mirror: false ],
        [id:19, name: "Scene Knob", typeId: "VIRTUAL_SWITCH", replicaName: "Replica Scene Knob", replicaType: "scene", mirror: false ]
    ]
    return devices
}

void setVirtualDeviceDefaults(String deviceId, String prototype) {
    logDebug "${app.getLabel()} executing 'setVirtualDeviceDefaults($deviceId, $prototype)'"
    runIn(5, setVirtualDeviceDefaultsHelper, [data: [deviceId:deviceId, prototype:prototype]])    
}

void setVirtualDeviceDefaultsHelper(data) {
    setVirtualDeviceDefaultsPrivate(data.deviceId, data.prototype)    
}

private void setVirtualDeviceDefaultsPrivate(String deviceId, String prototype) {
    logDebug "${app.getLabel()} executing 'setVirtualDeviceDefaultsPrivate($deviceId, $prototype)'"
    getVirtualDeviceDefaults(prototype)?.each{
        setVirtualDeviceAttribute(deviceId, it.componentId, it.capability, it.attribute, it.value, it.unit, it.data)
    }
}

Map createVirtualDevice(String locationId, String roomId, String name, String prototype) {
    logDebug "${app.getLabel()} executing 'createVirtualDevice($locationId, $prototype, $name)'"
    Map response = [statusCode:iHttpError]

    def device = [
      name: name,
      roomId: roomId,
      prototype: prototype,
      owner: [
        ownerType: "LOCATION",
        ownerId: locationId
      ]
    ]
    Map params = [
        uri: sURI,
        body: JsonOutput.toJson(device), 
        path: "/virtualdevices/prototypes",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpPost(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${app.getLabel()} created SmartThings Virtual Device '${resp.data.label}'"
        }
    } catch (e) {
        logWarn "${app.getLabel()} has createVirtualDevice() error: $e"        
    }
    return response
}

Map getVirtualDeviceList() {
    logDebug "${app.getLabel()} executing 'getVirtualDeviceList()'"
    Map response = [statusCode:iHttpError]

    Map params = [
        uri: sURI,
        path: "/virtualdevices",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status         
        }
    } catch (e) {
        logWarn "${app.getLabel()} has getVirtualDeviceList() error: $e"        
    }
    return response
}

Map renameVirtualDevice(String deviceId, String label) {
    logDebug "${app.getLabel()} executing 'renameVirtualDevice()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        body: JsonOutput.toJson([ label: label ]), 
        path: "/devices/$deviceId",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpPut(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${app.getLabel()} renamed SmartThings Virtual Device '${resp.data.label}'"
        }
    } catch (e) {
        logWarn "${app.getLabel()} has renameVirtualDevice() error: $e"        
    }
    return response
}

Map deleteSmartDevice(String deviceId) {
    logDebug "${app.getLabel()} executing 'deleteSmartDevice()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/devices/$deviceId",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpDelete(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status
            logInfo "${app.getLabel()} deleted SmartThings Device with deviceId: $deviceId"
        }
    } catch (e) {
        logWarn "${app.getLabel()} has deleteSmartDevice() error: $e"        
    }
    return response
}

Map getSmartRoomList(String locationId) {
    logDebug "${app.getLabel()} executing 'getSmartRoomList($locationId)'"
    Map response = [statusCode:iHttpError]

    Map params = [
        uri: sURI,
        path: "/locations/${locationId}/rooms",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status         
        }
    } catch (e) {
        logWarn "${app.getLabel()} has getSmartRoomList() error: $e"        
    }
    return response
}

Map getSmartLocationList() {
    logDebug "${app.getLabel()} executing 'getSmartLocationList()'"
    Map response = [statusCode:iHttpError]

    Map params = [
        uri: sURI,
        path: "/locations",
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status         
        }
    } catch (e) {
        logWarn "${app.getLabel()} has getSmartLocationList() error: $e"        
    }
    return response
}

Map getSmartSceneList(String locationId) {
    logDebug "${app.getLabel()} executing 'getSmartSceneList($locationId)'"
    Map response = [statusCode:iHttpError]

    Map params = [
        uri: sURI,
        path: "/scenes",
        query: [ locationId:locationId ],
        headers: [ Authorization: "Bearer ${getAuthToken()}" ]        
    ]
    try {
        httpGet(params) { resp ->
            logDebug "response data: ${resp.data}"
            response.data = resp.data
            response.statusCode = resp.status         
        }
    } catch (e) {
        logWarn "${app.getLabel()} has getSmartSceneList() error: $e"        
    }
    return response
}

void updateRuleList(action, type) {
    Map trigger = g_mAppDeviceSettings?.hubitatAttribute
    Map command = g_mAppDeviceSettings?.smartCommand
    if(type!='hubitatTrigger') {
        trigger = g_mAppDeviceSettings?.smartAttribute
        command = g_mAppDeviceSettings?.hubitatCommand
    }
    String triggerKey = trigger?.label?.trim()
    String commandKey = command?.label?.trim()
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)    
    logDebug "${app.getLabel()} executing 'updateRuleList()' hubitatDevice:'${replicaDevice}' trigger:'${triggerKey}' command:'${commandKey}' action:'${action}'"

    Map replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules") ?: [components:[],version:1]
    Boolean allowDuplicateAttribute = pageConfigureDeviceAllowDuplicateAttribute
    Boolean muteTriggerRuleInfo = pageConfigureDeviceMuteTriggerRuleInfo
    Boolean disableStatusUpdate = pageConfigureDeviceDisableStatusUpdate
    app.updateSetting("pageConfigureDeviceAllowDuplicateAttribute", false)
    app.updateSetting("pageConfigureDeviceMuteTriggerRuleInfo", false)
    app.updateSetting("pageConfigureDeviceDisableStatusUpdate", false)
  
    if(action=='delete') {        
        replicaDeviceRules?.components?.removeAll{ it?.type==type && it?.trigger?.label?.trim()==triggerKey && it?.command?.label?.trim()==commandKey }
        replicaDeviceRules?.components?.removeAll{ it?.type==type && it?.trigger?.label?.trim()==triggerKey && commandKey==null }
        replicaDeviceRules?.components?.removeAll{ it?.type==type && it?.command?.label?.trim()==commandKey && triggerKey==null }
    }    
    else if(triggerKey && commandKey && !replicaDeviceRules?.components?.find{ it?.type==type && it?.trigger?.label?.trim()==triggerKey && it?.command?.label?.trim()==commandKey }) {
        Map newRule = [ trigger:trigger, command:command, type:type]  
        newRule?.command?.parameters?.each{ parameter -> if(parameter?.description) {  parameter.remove('description') } } //junk
        try { newRule?.command?.arguments?.each{ arguments -> if(arguments?.schema?.pattern) { arguments?.schema.remove('pattern') } } } catch(e) {} //junk
        if(newRule?.trigger?.properties?.value?.enum) newRule.trigger.properties.value.remove('enum') //junk
        if(muteTriggerRuleInfo) newRule['mute'] = true
        if(disableStatusUpdate) newRule['disableStatus'] = true

        if(action=='store' && (!replicaDeviceRules?.components?.find{ it?.type==type && it?.trigger?.label?.trim()==triggerKey } || allowDuplicateAttribute)) {
            replicaDeviceRules.components.add(newRule)
        }
    }

    setReplicaDataJsonValue(replicaDevice, "rules", replicaDeviceRules.sort{ a, b -> b.key <=> a.key })
    if(type=='hubitatTrigger') replicaDeviceSubscribe(replicaDevice)
}

void replicaDevicesRuleSection(){
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
    Map replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules" )
    
    String replicaDeviceRulesList = "<table style='width:100%;'>"
    replicaDeviceRulesList += "<tr><th>Trigger</th><th>Action</th></tr>"
    replicaDeviceRules?.components?.sort{ a,b -> a?.type <=> b?.type ?: a?.trigger?.label <=> b?.trigger?.label ?: a?.command?.label <=> b?.command?.label }?.each { rule ->    
        String muteflag = rule?.mute ? "$sLogMuteIcon" : ""
        String disableStatusFlag = rule?.disableStatus ? "$sNoStatusIcon" : ""
        String trigger = "${rule?.type=='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.trigger?.label}"
        String command = "${rule?.type!='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.command?.label} $muteflag $disableStatusFlag"
        trigger = checkTrigger(replicaDevice, rule?.type, rule?.trigger?.label) ? trigger : "<span style='color:$sColorDarkRed;'>$trigger</span>"
        command = checkCommand(replicaDevice, rule?.type, rule?.command?.label) ? command : "<span style='color:$sColorDarkRed;'>$command</span>"
        replicaDeviceRulesList += "<tr><td>$trigger</td><td>$command</td></tr>"
    }
    replicaDeviceRulesList +="</table>"
    
    if (replicaDeviceRules?.components?.size){        
        section(menuHeader("Active Rules ➢ $replicaDevice")) {    
            paragraph( replicaDeviceRulesList )
            paragraph(rawHtml: true, """<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>""")
        }
    }

    if(checkFirmwareVersion("2.3.4.132") && false) {
        section(menuHeader("Replica Handler Development")) {
            input(name: "pageConfigureDeviceFetchCapabilityFileName", type: "text", title: "Replica Capabilities Filename:", description: "Capability JSON Local Filename", width: 4, submitOnChange: true, newLineAfter:true)
            input(name: "dynamic::pageConfigureDevicefetchCapabilityButton", type: "button", title: "Fetch", width: 2, style:"width:75%;")
            input(name: "dynamic::pageConfigureDeviceStoreCapabilityButton", type: "button", title: "Store", width: 2, style:"width:75%;")
        }
    }
}

void pageConfigureDevicefetchCapabilityButton() {
    logDebug "${app.getLabel()} executing 'pageConfigureDevicefetchCapabilityButton()' $pageConfigureDeviceFetchCapabilityFileName"
    byte[] filebytes = downloadHubFile(pageConfigureDeviceFetchCapabilityFileName)
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
    if(filebytes && replicaDevice) {
        String strFile = (new String(filebytes))?.replaceAll('“','"')?.replaceAll('”','"')                         
        Map capabilities = strFile ? new JsonSlurper().parseText(strFile) : [components:[]]
        logInfo capabilities
        setReplicaDataJsonValue(replicaDevice, "capabilities", capabilities)
    }
}

void pageConfigureDeviceStoreCapabilityButton() {
    logDebug "${app.getLabel()} executing 'pageConfigureDeviceStoreCapabilityButton()' $pageConfigureDeviceFetchCapabilityFileName"
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
    Map capabilities = getReplicaDataJsonValue(replicaDevice, "capabilities")
    if(pageConfigureDeviceFetchCapabilityFileName && capabilities) {
        //logInfo capabilities
        byte[] filebytes =((String)JsonOutput.toJson(capabilities))?.getBytes()
        uploadHubFile(pageConfigureDeviceFetchCapabilityFileName, filebytes)
    }
}

String getTimestampSmartFormat() {
    return ((new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone('UTC'))).toString())
}

def naturalSort( def a, def b ) {
    def aParts = a.replaceAll(/(\d+)/, '#$1#').split('#')
    def bParts = b.replaceAll(/(\d+)/, '#$1#').split('#')

    int i = 0
    while(i < aParts.size() && i < bParts.size()) {
        if (aParts[i] != bParts[i]) {
            if (aParts[i].isNumber() && bParts[i].isNumber())
                return aParts[i].toInteger() <=> bParts[i].toInteger()
            else
                return aParts[i] <=> bParts[i]
        }
        i++
    }
    return aParts.size() <=> bParts.size()
}

def checkFirmwareVersion(versionString) { 
    def (a1,b1,c1,d1) = location.hub.firmwareVersionString.split("\\.").collect { it.toInteger() }
    def (a2,b2,c2,d2) = versionString.split("\\.").collect { it.toInteger() }    
    return (a1>=a2 || (a1>=a2 && b1>=b2) || (a1>=a2 && b1>=b2 && c1>=c2) || (a1>=a2 && b1>=b2 && c1>=c2 && d1>=d2))
}

Boolean checkTrigger(replicaDevice, type, triggerLabel) {
    Map trigger = type=='hubitatTrigger' ? getHubitatAttributeOptions(replicaDevice) : getSmartAttributeOptions(replicaDevice)
    return trigger?.get(triggerLabel)
}

Boolean checkCommand(replicaDevice, type, commandLabel) {
    Map commands = type!='hubitatTrigger' ? getHubitatCommandOptions(replicaDevice) : getSmartCommandOptions(replicaDevice)
    return commands?.get(commandLabel)
}       

def pageConfigureDevice() {
    
    List replicaDevicesSelect = []
    getAllReplicaDevices()?.sort{ a,b -> naturalSort(a.getDisplayName(),b.getDisplayName()) }.each {    
        Map device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
        replicaDevicesSelect.add(device)   
    }
   
    return dynamicPage(name: "pageConfigureDevice", uninstall: false) {
        displayHeader()
       
        section(menuHeader("Configure HubiThings Rules $sHubitatIconStatic $sSamsungIconStatic")) {
            
            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
            String deviceTitle = "&ensp;$sSamsungIcon Select HubiThings Device:"
            if(replicaDevice) {
                String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice.getId()}"
                deviceTitle = "<a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${deviceTitle}</a>"
            }
            input(name: "pageConfigureDeviceReplicaDevice", type: "enum", title: deviceTitle, description: "Choose a HubiThings device", options: replicaDevicesSelect, multiple: false, submitOnChange: true, width: 8, newLineAfter:true)
           
            if(pageConfigureDeviceShowDetail && replicaDevice) {
                def hubitatStats =  getHubitatDeviceStats(replicaDevice)
                paragraph( hubitatStats )              
            }
            input(name: "pageConfigureDevice::refreshDevice",     type: "button", title: "Refresh", width: 2, style:"width:75%;")            
            input(name: "pageConfigureDevice::clearDeviceRules",  type: "button", title: "Clear Rules", width: 2, style:"width:75%;")
            if(replicaDevice?.hasCommand('configure') && !getReplicaDataJsonValue(replicaDevice, "rules" )?.components) 
                input(name: "pageConfigureDevice::configDeviceRules",  type: "button", title: "Configure", width: 2, style:"width:75%;")
            paragraph( getFormat("line") )
            
            Map hubitatAttributeOptions = getHubitatAttributeOptions(replicaDevice)                      
            Map smartCommandOptions = getSmartCommandOptions(replicaDevice)
            
            input(name: "hubitatAttribute", type: "enum", title: "&ensp;$sHubitatIcon If Hubitat Attribute <b>TRIGGER</b> changes:", description: "Choose a Hubitat Attribute", options: hubitatAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "smartCommand", type: "enum", title: "&ensp;$sSamsungIcon Then <b>ACTION</b> SmartThings Command:", description: "Choose a SmartThings Command", options: smartCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            input(name: "pageConfigureDevice::hubitatAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::hubitatAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")

            if(pageConfigureDeviceShowDetail) {
                paragraph( hubitatAttribute ? "$sHubitatIcon $hubitatAttribute : ${JsonOutput.toJson(hubitatAttributeOptions?.get(hubitatAttribute))}" : "$sHubitatIcon No Selection" )
                paragraph( smartCommand ? "$sSamsungIcon $smartCommand : ${JsonOutput.toJson(smartCommandOptions?.get(smartCommand))}" : "$sSamsungIcon No Selection" )
            }
            paragraph( getFormat("line") )
            
            Map smartAttributeOptions = getSmartAttributeOptions(replicaDevice)         
            Map hubitatCommandOptions = getHubitatCommandOptions(replicaDevice)
            
            input(name: "smartAttribute", type: "enum", title: "&ensp;$sSamsungIcon If SmartThings Attribute <b>TRIGGER</b> changes:", description: "Choose a SmartThings Attribute", options: smartAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "hubitatCommand", type: "enum", title: "&ensp;$sHubitatIcon Then <b>ACTION</b> Hubitat Command${pageConfigureDeviceAllowActionAttribute?'/Attribute':''}:", description: "Choose a Hubitat Command", options: hubitatCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            input(name: "pageConfigureDevice::smartAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::smartAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")
            
            if(pageConfigureDeviceShowDetail) {
                paragraph( smartAttribute ? "$sSamsungIcon $smartAttribute : ${JsonOutput.toJson(smartAttributeOptions?.get(smartAttribute))}" : "$sSamsungIcon No Selection" )
                paragraph( hubitatCommand ? "$sHubitatIcon $hubitatCommand : ${JsonOutput.toJson(hubitatCommandOptions?.get(hubitatCommand))}" : "$sHubitatIcon No Selection" )
            }
            paragraph( getFormat("line") )     
            
            input(name: "pageConfigureDeviceAllowDuplicateAttribute", type: "bool", title: "Allow duplicate Attribute <b>TRIGGER</b>", defaultValue: false, submitOnChange: true, width: 3)
            input(name: "pageConfigureDeviceMuteTriggerRuleInfo", type: "bool", title: "Mute $sLogMuteIcon <b>TRIGGER</b> rule Logging", defaultValue: false, submitOnChange: true, width: 3)
            input(name: "pageConfigureDeviceDisableStatusUpdate", type: "bool", title: "Disable $sNoStatusIcon periodic device refresh", defaultValue: false, submitOnChange: true, width: 3)
            app.updateSetting("pageConfigureDeviceAllowActionAttribute", false)
            input(name: "pageConfigureDeviceShowDetail", type: "bool", title: "Show detail for attributes and commands", defaultValue: false, submitOnChange: true, width: 3, newLineAfter:true)
            
            // gather these all up so when user presses store - it uses this structure.           
            g_mAppDeviceSettings['hubitatAttribute'] = hubitatAttributeOptions?.get(hubitatAttribute) ?: null
            g_mAppDeviceSettings['smartAttribute']   = smartAttributeOptions?.get(smartAttribute) ?: null
            g_mAppDeviceSettings['smartCommand']     = smartCommandOptions?.get(smartCommand) ?: null
            g_mAppDeviceSettings['hubitatCommand']   = hubitatCommandOptions?.get(hubitatCommand) ?: null            
        }        
        replicaDevicesRuleSection()
    }
}

Map getHubitatCommandOptions(replicaDevice) {    
    // check if this is a replica DH. return if so.
    Map hubitatCommandOptions = getReplicaCommandOptions(replicaDevice)
    if(hubitatCommandOptions.size()) return hubitatCommandOptions
    
    replicaDevice?.getSupportedCommands()?.each{ command ->
        Map commandJson = new JsonSlurper().parseText(JsonOutput.toJson(command)) //could not figure out how to convert command object to json. this works.
        commandJson.remove('id')
        commandJson.remove('version')
        commandJson.remove('capability')
        commandJson["type"] = "command"
        String parameterText = "("
        commandJson?.parameters?.eachWithIndex{ parameter, index ->
            String parameterName = parameter?.name ? "${parameter?.name?.uncapitalize()}" : "${parameter?.type?.toLowerCase()}*"
            parameterText += (index ? ", $parameterName" : "$parameterName")
        }
        parameterText +=")"
        commandJson['label'] = "command: ${command?.name}$parameterText"
        if(commandJson?.arguments==null) commandJson.remove('arguments')
        if(commandJson?.parameters==null) commandJson.remove('parameters')
        if(commandJson?.values==null) commandJson.remove('values')
        hubitatCommandOptions[commandJson.label] = commandJson 
    }
    if(pageConfigureDeviceAllowActionAttribute) {
        hubitatCommandOptions += getHubitatAttributeOptions(replicaDevice)
    }
    return hubitatCommandOptions
}

Map getReplicaCommandOptions(replicaDevice) {
    Map commands = getReplicaDataJsonValue(replicaDevice, "commands")    
    Map replicaCommandOptions = [:]    
    commands?.each{ command, parameters ->
        String parameterText = "("
        parameters?.eachWithIndex{ parameter, index ->
            parameterText += (index ? ", ${parameter?.name}" : "${parameter?.name}")
        }
        parameterText +=")"
        def label = "command: ${command}$parameterText"
        replicaCommandOptions[label] = [name:command, label:label, type:'command']
        if(parameters.size()) replicaCommandOptions[label].parameters = parameters
    }      
    return replicaCommandOptions
}

Map getHubitatAttributeOptions(replicaDevice) {
    Map hubitatAttributeOptions = getReplicaTriggerOptions(replicaDevice) // might be a replica DH
   
    replicaDevice?.getSupportedAttributes()?.each{ attribute ->
        Map attributeJson = new JsonSlurper().parseText(JsonOutput.toJson(attribute))
        attributeJson.remove('possibleValueJson')
        attributeJson.remove('possibleValues')
        attributeJson.remove('id')
        attributeJson.remove('version')
        attributeJson.remove('deviceTypeId')
        attributeJson.remove('capability')
        attributeJson["type"] = "attribute"
        if(attributeJson?.dataType=="ENUM") {
            def label = "attribute: ${attributeJson?.name}.*"
            hubitatAttributeOptions[label] = attributeJson.clone()
            hubitatAttributeOptions[label].label = label
            hubitatAttributeOptions[label].remove('values')
            attributeJson?.values?.each{ enumValue ->
                label = "attribute: ${attributeJson?.name}.${enumValue}"
                hubitatAttributeOptions[label] = attributeJson.clone()
                hubitatAttributeOptions[label].remove('values')
                hubitatAttributeOptions[label].label = label
                hubitatAttributeOptions[label].value = enumValue
            }
        }
        else {
            attributeJson['label'] = "attribute: ${attributeJson?.name}.*"
            hubitatAttributeOptions[attributeJson.label] = attributeJson
            hubitatAttributeOptions[attributeJson.label].remove('values')
        }
    }
    return hubitatAttributeOptions
}

Map getReplicaTriggerOptions(replicaDevice) {
    Map triggers = getReplicaDataJsonValue(replicaDevice, "triggers")    
    Map replicaTriggerOptions = [:]    
    triggers?.each{ command, parameters ->
        String parameterText = "("
        parameters?.eachWithIndex{ parameter, index ->
            parameterText += (index ? ", ${parameter?.name}" : "${parameter?.name}")
        }
        parameterText +=")"
        def label = "command: ${command}$parameterText"
        replicaTriggerOptions[label] = [name:command, label:label, type:'command']
        if(parameters.size()) replicaTriggerOptions[label].parameters = parameters
    }      
    return replicaTriggerOptions
}

Map getSmartCommandOptions(replicaDevice) {            
    Map smartCommandOptions = [:]
    Map capabilities = getReplicaDataJsonValue(replicaDevice, "capabilities")
    capabilities?.components?.each{ capability -> 
        capability?.commands?.each{ command, value ->
            def parameterText = "("
            value?.arguments?.eachWithIndex{ parameter, index ->
                def parameterName = parameter?.optional ? "${parameter?.name?.uncapitalize()}" : "${parameter?.name?.uncapitalize()}*"
                parameterText += (index ? ", $parameterName" : "$parameterName")
            }
            parameterText +=")"
            value["type"] = "command"
            value["capability"] = capability.id        
            def label = "command: ${command}$parameterText"            
            if(smartCommandOptions[label]) { // this device has conflicting commands from different capablities. like alarm & switch
                def newLabel = "command: ${smartCommandOptions[label].capability}:${command}$parameterText"
                smartCommandOptions[newLabel] = smartCommandOptions[label]
                smartCommandOptions[newLabel].label = newLabel
                smartCommandOptions?.remove(label.toString())
                label = "command: ${capability.id}:${command}$parameterText"
            }
            value["label"] = label            
            if(value?.arguments==[]) value.remove('arguments')
            smartCommandOptions[value.label] = value
        } 
    }    
    if(getReplicaDataJsonValue(replicaDevice, "description")?.type=="VIRTUAL") smartCommandOptions+=getSmartAttributeOptions(replicaDevice)
    
    return smartCommandOptions
}

Map getSmartAttributeOptions(replicaDevice) {
    Map smartAttributeOptions = [:]
    Map capabilities = getReplicaDataJsonValue(replicaDevice, "capabilities")
    capabilities?.components?.each{ capability ->
        capability?.attributes?.each{ attribute, value -> Map schema = value?.schema ?: [:]
            schema["capability"] = capability.id
            schema["attribute"] = attribute
            schema["type"] = "attribute"
            if(schema?.properties?.value?.enum) {
                def label = "attribute: ${attribute}.*"
                if(smartAttributeOptions[label]) { label = "attribute: ${capability.id}:${attribute}.*" } // duplicate attribute. rare case like TV.
                smartAttributeOptions[label] = schema.clone()
                smartAttributeOptions[label].label = label
                schema?.properties?.value?.enum?.each{ enumValue ->
                    label = "attribute: ${attribute}.${enumValue}"
                    if(smartAttributeOptions[label]) { label = "attribute: ${capability.id}:${attribute}.${enumValue}" } // duplicate attribute. rare case like TV.
                    smartAttributeOptions[label] = schema.clone()
                    smartAttributeOptions[label].label = label
                    smartAttributeOptions[label].value = enumValue
                    smartAttributeOptions[label].dataType = "ENUM" //match hubitat
                }
            }
            else {
                def type = schema?.properties?.value?.type
                schema["label"] = "attribute: ${attribute}.*"
                if(smartAttributeOptions[schema.label]) { schema.label = "attribute: ${capability.id}:${attribute}.*" } // duplicate attribute. rare case like TV.
                smartAttributeOptions[schema.label] = schema
            }            
        }
    }
    // not sure why SmartThings treats health different. But everything reports healthStatus. So gonna make it look the same to the user configuration page.
    if(capabilities?.size()) {
        smartAttributeOptions["attribute: healthStatus.*"] = new JsonSlurper().parseText("""{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"}""")
        smartAttributeOptions["attribute: healthStatus.offline"] = new JsonSlurper().parseText("""{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","value":"offline","attribute":"healthStatus","dataType":"ENUM","label":"attribute: healthStatus.offline"}""")
        smartAttributeOptions["attribute: healthStatus.online"] =  new JsonSlurper().parseText("""{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","value":"online","attribute":"healthStatus","dataType":"ENUM","label":"attribute: healthStatus.online"}""")
    }
    return smartAttributeOptions
}

@Field volatile static Map<Long,Boolean> g_bAppButtonHandlerLock = [:]
void appButtonHandler(String btn) {
    logDebug "${app.getLabel()} executing 'appButtonHandler($btn)'"
    if(g_bAppButtonHandlerLock[app.id]) return
    appButtonHandlerLock()
   
    if(btn.contains("::")) { 
        List items = btn.tokenize("::")
        if(items && items.size() > 1 && items[1]) {
            String k = (String)items[0]
            String v = (String)items[1]
            logTrace "Button [$k] [$v] pressed"
            switch(k) {                
                case "pageConfigureDevice":
                    switch(v) {
                        case "hubitatAttributeStore":
                            updateRuleList('store','hubitatTrigger')
                            break
                        case "hubitatAttributeDelete":
                            updateRuleList('delete','hubitatTrigger')
                            break
                        case "smartAttributeStore":
                            updateRuleList('store','smartTrigger')
                            break
                        case "smartAttributeDelete":
                            updateRuleList('delete','smartTrigger')
                            break
                        case "configDeviceRules":
                            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
                            if(replicaDevice?.hasCommand('configure')) {
                                replicaDevice.configure()
                            }  
                            break                        
                        case "clearDeviceRules":
                            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
                            clearReplicaDataCache(replicaDevice, "rules", true)
                            unsubscribe(replicaDevice)
                            break
                        case "refreshDevice":
                            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
                            replicaDeviceRefresh(replicaDevice)
                            break
                    }
                    break
                case "dynamic":
                    this."$v"()
                    break
                default:
                    logInfo "Not supported"
            }
        }
    }
    appButtonHandlerUnLock()
}
void appButtonHandlerLock() {
    g_bAppButtonHandlerLock[app.id] = true
    runIn(10,appButtonHandlerUnLock)
}
void appButtonHandlerUnLock() {
    unschedule('appButtonHandlerUnLock')
    g_bAppButtonHandlerLock[app.id] = false
}

void allSmartDeviceRefresh() {
    // brute force grabbing all devices in my OAuths.
    // smartLocationQuery is async so will not be available for first refresh
    Map smartDevices = [items:[]]
    getChildApps()?.each{ 
        smartDevices.items.addAll( it.getSmartSubscribedDevices()?.items )
        it.smartLocationQuery()
    }
    setSmartDevicesMap( smartDevices )
    // check that everything is Hubitat subscribed 
    getAllReplicaDevices()?.each { replicaDevice ->
        replicaDeviceSubscribe(replicaDevice)
    }
    // lets get status on everything. should this be scheduled?
    allSmartDeviceStatus(10)
    allSmartDeviceHealth(20)
    allSmartDeviceDescription(30)    
}

void locationModeHandler(def event) {
    //subscribe(location, "mode", locationModeHandler)
    logDebug "${app.getLabel()} executing 'locationModeHandler($event.value)'"
    getAllReplicaDevices()?.each { replicaDevice ->
        if(replicaDevice?.hasCommand('setLocationMode')) {
            replicaDevice.setLocationMode(event.value,true)
        }
    }   
}

void deviceTriggerHandler(def event) {
    // called from subscribe HE device events. value is always string, but just converting anyway to be sure.
    //event.properties.each { logInfo "$it.key -> $it.value" }
    deviceTriggerHandlerPrivate(event?.getDevice(), event?.name, event?.value.toString(), event?.unit, event?.getJsonData())
}

void deviceTriggerHandler(def replicaDevice, Map event) {
    // called from replica HE drivers. value might not be a string, converting it to normalize with events above.
    Boolean result = deviceTriggerHandlerPrivate(replicaDevice, event?.name, event?.value.toString(), event?.unit, event?.data, event?.now)    
    if(event?.name == "configure") {
        clearReplicaDataCache(replicaDevice)
        replicaDeviceRefresh(replicaDevice)       
    } 
    else if(event?.name == "refresh") {
        clearReplicaDataCache(replicaDevice)
        String deviceId = getReplicaDeviceId(replicaDevice)    
        if(deviceId&&result) getSmartDeviceStatus(deviceId)
        else replicaDeviceRefresh(replicaDevice)      
    }
    else if(!result) {
        logInfo "${app.getLabel()} executing 'deviceTriggerHandler()' replicaDevice:'${replicaDevice.getDisplayName()}' event:'${event?.name}' is not rule configured"
    }
}
           
private Boolean deviceTriggerHandlerPrivate(def replicaDevice, String eventName, String eventValue, String eventUnit, Map eventJsonData, Long eventPostTime=null) {
    eventPostTime = eventPostTime ?: now()
    logDebug "${app.getLabel()} executing 'deviceTriggerHandlerPrivate()' replicaDevice:'${replicaDevice.getDisplayName()}' name:'$eventName' value:'$eventValue' unit:'$eventUnit', data:'$eventJsonData'"
    Boolean response = false
    
    getReplicaDataJsonValue(replicaDevice, "rules")?.components?.findAll{ it?.type=="hubitatTrigger" && it?.trigger?.name==eventName && (!it?.trigger?.value || it?.trigger?.value==eventValue) }?.each { rule ->            
        Map trigger = rule?.trigger
        Map command = rule?.command        

        if(trigger?.type=="command" || !deviceTriggerHandlerCache(replicaDevice, eventName, eventValue)) {
            String type = (command?.type!="attribute") ? command?.arguments?.getAt(0)?.schema?.type?.toLowerCase() : command?.properties?.value?.type?.toLowerCase()
            def arguments = null  
                    
            switch(type) {
                case 'integer': // A whole number. Limits can be defined to constrain the range of possible values.
                    arguments = [ (eventValue?.isNumber() ? (eventValue?.isFloat() ? (int)(Math.round(eventValue?.toFloat())) : eventValue?.toInteger()) : null) ]
                    break
                case 'number':  // A number that can have fractional values. Limits can be defined to constrain the range of possible values.
                    arguments = [ eventValue?.toFloat() ]
                    break
                case 'boolean': // Either true or false
                    arguments = [ eventValue?.toBoolean() ]
                    break
                case 'object':  // A map of name value pairs, where the values can be of different types.
                    try {
                        Map map = new JsonSlurper().parseText(eventValue)
                        arguments = [ map ] //updated version v1.2.10
                    } catch (e) {
                        logWarn "${app.getLabel()} deviceTriggerHandlerPrivate() received $eventValue and not type 'object' as expected"
                    }
                    break
                case 'array':   // A list of values of a single type.
                    try {
                        List list = new JsonSlurper().parseText(eventValue)
                        arguments = (command?.type!="attribute") ? list : [ list ]
                    } catch (e) {
                        logWarn "${app.getLabel()} deviceTriggerHandlerPrivate() received $eventValue and not type 'array' as expected"
                    }
                    break
                case 'string':  // enum cases
                    arguments = [ (command?.value?:eventValue) ]
                    break
                case null:      // commands with no arguments will be type null
                    arguments = []
                    break
                default:
                    logWarn "${app.getLabel()} deviceTriggerHandlerPrivate() ${trigger?.type?:"event"}:'$eventName' has unknown argument type: $type"
                    arguments = []
                break
            }
            if(trigger?.parameters) {
                // add any additonal arguments. (these should be evaluated correct type since they are not a event 'value' ^^ which is defined as string)
                arguments = arguments?.plus( trigger?.parameters?.findResults{ parameter -> parameter?.data ? eventJsonData?.get(parameter?.data) : null })
            }
            
            String deviceId = getReplicaDeviceId(replicaDevice)
            String componentId = getReplicaDataJsonValue(replicaDevice, "replica")?.componentId ?: "main" //componentId was not added until v1.2.06
            
            if(command?.type!="attribute" && arguments!=null) {
                response = setSmartDeviceCommand(deviceId, componentId, command?.capability, command?.name, arguments)?.statusCode==iHttpSuccess                        
                if(!rule?.mute) logInfo "${app.getLabel()} sending '${replicaDevice?.getDisplayName()}' ${type?:""} ● trigger:${trigger?.type=="command"?"command":"event"}:${eventName} ➣ command:${command?.name}${arguments?.size()?":"+arguments?.toString():""} ● delay:${now() - eventPostTime}ms"
            }
            else if(arguments!=null) {
                // fix some brokes
                eventUnit = eventUnit?.replaceAll('°','')
                eventJsonData?.remove("version")                
                if( command?.required?.contains("unit") && !command?.properties?.unit?.enum?.contains(eventUnit) ) logWarn "${app.getLabel()} deviceTriggerHandlerPrivate() requires unit value defined as ${command?.properties?.unit?.enum?:""} but found $eventUnit"
                
                response = setVirtualDeviceAttribute(deviceId, componentId, command?.capability, command?.attribute, arguments?.getAt(0), eventUnit, eventJsonData)?.statusCode==iHttpSuccess
                if(!rule?.mute) logInfo "${app.getLabel()} sending '${replicaDevice?.getDisplayName()}' ${type?:""} ○ trigger:${trigger?.type=="command"?"command":"event"}:${eventName} ➣ command:${command?.attribute}:${arguments?.getAt(0)?.toString()} ● delay:${now() - eventPostTime}ms"
            }
        }
    }
    return response
}

Boolean deviceTriggerHandlerCache(replicaDevice, attribute, value) {
    logDebug "${app.getLabel()} executing 'deviceTriggerHandlerCache()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Boolean response = false

    Map device = getReplicaDeviceEventsCache(replicaDevice)
    if (device) {
        String a = device?.eventCache?.get(attribute).toString()
        String b = value.toString()
        
        if(a.isBigInteger() && b.isBigInteger()) {
            response = a==b
            logTrace "a:$a == b:$b match integer is $response"
        }
        else if(a.isNumber() && b.isNumber()) {
            response = Float.valueOf(a).round(4)==Float.valueOf(b).round(4)
            logTrace "a:$a == b:$b match float is $response"
        }
        else {
            response = a==b
            logTrace "a:$a == b:$b match string is $response"            
        }
        logDebug "${app.getLabel()} cache <= ${device?.eventCache} match string:$response"
    }
    return response
}

void smartTriggerHandlerCache(replicaDevice, attribute, value) {
    logDebug "${app.getLabel()} executing 'smartTriggerHandlerCache()' replicaDevice:'${replicaDevice?.getDisplayName()}'"

    Map device = getReplicaDeviceEventsCache(replicaDevice)
    if (device!=null) {
        device?.eventCache[attribute] = value
        logDebug "${app.getLabel()} cache => ${device?.eventCache}"
    }    
}

Map smartTriggerHandler(replicaDevice, Map event, String type, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'smartTriggerHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]    
    
    Map replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules")
    event?.each { capability, attributes ->
        attributes?.each{ attribute, value ->
            logTrace "smartEvent: capability:'$capability' attribute:'$attribute' value:'$value'" 
            replicaDeviceRules?.components?.findAll{ it?.type == "smartTrigger" }?.each { rule -> 
                Map trigger = rule?.trigger
                Map command = rule?.command

                // simple enum case
                if(attribute==trigger?.attribute && value?.value==trigger?.value) {                   
                    smartTriggerHandlerCache(replicaDevice, attribute, value?.value)
                    
                    List args = []
                    String method = command?.name
                    if(hasCommand(replicaDevice, method) && !(type=="status" && rule?.disableStatus)) {
                        replicaDevice."$method"(*args)
                        if(!rule?.mute) logInfo "${app.getLabel()} received '${replicaDevice?.getDisplayName()}' $type ○ trigger:$attribute:${value?.value} ➢ command:${command?.name} ${(eventPostTime ? "● delay:${now() - eventPostTime}ms" : "")}"
                    }
                }
                // non-enum case
                else if(attribute==trigger?.attribute && !trigger?.value) {                    
                    smartTriggerHandlerCache(replicaDevice, attribute, value?.value)
                    
                    String method = command?.name
                    String argType = hasCommand(replicaDevice, method) ? hasCommandType(replicaDevice, method) : null                 
                    if(argType && !(type=="status" && rule?.disableStatus)) {
                        if(argType!="JSON_OBJECT")
                            replicaDevice."$method"(*[value.value])
                        else
                            replicaDevice."$method"(*[[value:(value.value), unit:(value?.unit?:""), data:(value?.data?:[:]), stateChange:(value?.stateChange?:false), timestamp:(value?.timestamp)]]);
                        if(!rule?.mute) logInfo "${app.getLabel()} received '${replicaDevice?.getDisplayName()}' $type ● trigger:$attribute ➢ command:${command?.name}:${value?.value} ${(eventPostTime ? "● delay:${now() - eventPostTime}ms" : "")}"
                    }
                }
            }
        }
        response.statusCode = iHttpSuccess
    }
    return [statusCode:response.statusCode]
}

Boolean hasCommand(def replicaDevice, String method) {
    Boolean response = replicaDevice.hasCommand(method)
    if(!response) {
        response = (getReplicaDataJsonValue(replicaDevice, "commands")?.keySet()?.find{ it==method } != null)
    }
    return response
}

String hasCommandType(def replicaDevice, String method) {
    String response = replicaDevice.hasCommand(method) ? "STRING" : null
    if(!response) {
        List value = (getReplicaDataJsonValue(replicaDevice, "commands")?.find{ key, value -> key==method }?.value)
        response = (value?.size() && value?.get(0)?.type) ? value?.get(0)?.type : "STRING"
        logTrace "custom command: $value -> $response"                 
    }
    return response
}

Map getReplicaDeviceEventsCache(replicaDevice) {
    String deviceId = getReplicaDeviceId(replicaDevice)
    return (deviceId ? getSmartDeviceEventsCache(deviceId) : [:])    
}

Map getSmartDeviceEventsCache(deviceId) {
    Map response = [:]
    try {
        Long appId = app.getId()
        if(g_mSmartDeviceStatusMap[appId]==null) { 
            g_mSmartDeviceStatusMap[appId] = [:]
        }
        if(!g_mSmartDeviceStatusMap[appId]?.get(deviceId)) {
            g_mSmartDeviceStatusMap[appId][deviceId] = [ eventCount:0, eventCache:[:] ]
        }
        response = g_mSmartDeviceStatusMap[appId]?.get(deviceId)
    } catch(e) { 
        //we don't care.
        //logWarn "getSmartDeviceEventsCache $e"
    }
    return response
}

String updateSmartDeviceEventsStatus(replicaDevice) {
    String value = "--"
    if(replicaDevice) {
        String healthState = getReplicaDataJsonValue(replicaDevice, "health")?.state?.toLowerCase()
        String noRules = getReplicaDataJsonValue(replicaDevice, "rules")?.components ? "" : "<span style='color:$sColorDarkRed;'>$sNoStatusIcon $sNoRules</span>"
            
        String eventCount = (getReplicaDeviceEventsCache(replicaDevice)?.eventCount ?: 0).toString()
        value = (healthState=='offline' ? "<span style='color:$sColorDarkRed;'>$sNoStatusIcon $sOffline</span>" : noRules ?: eventCount)
        if(state.pageMainLastRefresh && (state.pageMainLastRefresh + (iPageMainRefreshInterval*1000)) > now()) { //only send if someone is watching 
            sendEvent(name:'smartEvent', value:value, descriptionText: JsonOutput.toJson([ deviceNetworkId:(replicaDevice?.deviceNetworkId), debug: appLogEnable ]))
        }
    }
    return value
}

Map smartStatusHandler(replicaDevice, String deviceId, Map statusEvent, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'smartStatusHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]
    
    if(appLogEventEnable && statusEvent && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceId)) {
        log.info "Status: ${JsonOutput.toJson(statusEvent)}"
    }
    if(hasCommand(replicaDevice, 'replicaStatus')) {
        statusEvent.deviceId = deviceId
        replicaDevice.replicaStatus(app, statusEvent)
    } else {
        setReplicaDataJsonValue(replicaDevice, "status", statusEvent)
    }
    String componentId = getReplicaDataJsonValue(replicaDevice, "replica")?.componentId ?: "main" //componentId was not added until v1.2.06    
    statusEvent?.components?.get(componentId)?.each { capability, attributes ->
        response.statusCode = smartTriggerHandler(replicaDevice, [ "$capability":attributes ], "status", eventPostTime).statusCode
    }
    
    if( updateSmartDeviceEventsStatus(replicaDevice) == 'offline' ) { getSmartDeviceHealth( getReplicaDeviceId(replicaDevice) ) } 
    return [statusCode:response.statusCode]
}

Map smartEventHandler(replicaDevice, Map deviceEvent, Long eventPostTime=null){
    logDebug "${app.getLabel()} executing 'smartEventHandler()' replicaDevice:'${replicaDevice.getDisplayName()}'"
    Map response = [statusCode:iHttpSuccess]
    
    if(appLogEventEnable && deviceEvent && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceEvent?.deviceId)) {
        log.info "Event: ${JsonOutput.toJson(deviceEvent)}"
    }   
    //setReplicaDataJsonValue(replicaDevice, "event", deviceEvent)    
    try {
        // events do not carry units. so get it from status. yeah smartthings is great!
        String unit = getReplicaDataJsonValue(replicaDevice, "status")?.components?.get(deviceEvent.componentId)?.get(deviceEvent.capability)?.get(deviceEvent.attribute)?.unit
        // status    {"switchLevel":             {"level":                  {"value":30,                "unit":"%",   "timestamp":"2022-09-07T21:16:59.576Z" }}}
        Map event = [ (deviceEvent.capability): [ (deviceEvent.attribute): [ value:(deviceEvent.value), unit:(deviceEvent?.unit ?: unit), data:(deviceEvent?.data), stateChange:(deviceEvent?.stateChange), timestamp: getTimestampSmartFormat() ]]]
        logTrace JsonOutput.toJson(event)
        response.statusCode = smartTriggerHandler(replicaDevice, event, "event", eventPostTime).statusCode
        
        if( updateSmartDeviceEventsStatus(replicaDevice) == 'offline' ) { getSmartDeviceHealth( getReplicaDeviceId(replicaDevice) ) }
    } catch (e) {
        logWarn "${app.getLabel()} smartEventHandler() error: $e : $deviceEvent"
    }
    return [statusCode:response.statusCode]
}

Map smartHealthHandler(replicaDevice, String deviceId, Map healthEvent, Long eventPostTime=null){
    logDebug "${app.getLabel()} executing 'smartHealthHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]

    if(appLogEventEnable && healthEvent && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceId)) {
        log.info "Health: ${JsonOutput.toJson(healthEvent)}"
    }
    if(hasCommand(replicaDevice, 'replicaHealth')) {
        healthEvent.deviceId = deviceId
        replicaDevice.replicaHealth(app, healthEvent)
    } else {
        setReplicaDataJsonValue(replicaDevice, "health", healthEvent)
    }
    try {
        //{"deviceId":"2c80c1d7-d05e-430a-9ddb-1630ee457afb","state":"ONLINE","lastUpdatedDate":"2022-09-07T16:47:06.859Z"}
        // status    {"switchLevel":{"level":       {"value":30,                             "unit":"","timestamp":"2022-09-07T21:16:59.576Z" }}}
        Map event = [ healthCheck: [ healthStatus: [ value:(healthEvent?.state?.toLowerCase()), timestamp: healthEvent?.lastUpdatedDate, reason:(healthEvent?.reason ?: 'poll') ]]]
        logTrace JsonOutput.toJson(event)
        response.statusCode = smartTriggerHandler(replicaDevice, event, "health", eventPostTime).statusCode
        
        updateSmartDeviceEventsStatus(replicaDevice)            
    } catch (e) {
        logWarn "${app.getLabel()} smartHealthHandler() error: $e : $healthEvent"
    }    
    return [statusCode:response.statusCode]
}

Map smartDescriptionHandler(replicaDevice, Map descriptionEvent){
    logDebug "${app.getLabel()} executing 'smartDescriptionHandler()' replicaDevice:'${replicaDevice.getDisplayName()}'"
    Map response = [statusCode:iHttpError]

    setReplicaDataJsonValue(replicaDevice, "description", descriptionEvent)
    try {
        getReplicaCapabilities(replicaDevice) // This should probably be threaded since it could be a large amount of calls.
        response.statusCode = iHttpSuccess       
    } catch (e) {
        logWarn "${app.getLabel()} smartDescriptionHandler() error: $e : $descriptionEvent"
    }    
    return [statusCode:response.statusCode]
}

Map smartCapabilityHandler(replicaDevice, Map capabilityEvent){
    logDebug "${app.getLabel()} executing 'smartCapabilityHandler()' replicaDevice:'${replicaDevice.getDisplayName()}'"
    Map response = [statusCode:iHttpError]
    
    Map capabilities = getReplicaDataJsonValue(replicaDevice, "capabilities")
    try {
        if (capabilities?.components) {
            if (capabilities.components?.find { components -> components?.id==capabilityEvent.id && components?.version==capabilityEvent.version  }) {
                logInfo "${app.getLabel()} '${replicaDevice.getDisplayName()}' attribute ${capabilityEvent.id} FOUND"
            }
            else {
                logInfo "${app.getLabel()} '${replicaDevice.getDisplayName()}' attribute ${capabilityEvent.id} ADDED"
                capabilities.components.add(capabilityEvent)
                setReplicaDataJsonValue(replicaDevice, "capabilities", capabilities)
            }
        }
        else {
            // first time. So just store.
            logInfo "${app.getLabel()} '${replicaDevice.getDisplayName()}' attribute ${capabilityEvent.id} ADDED*"
            setReplicaDataJsonValue(replicaDevice, "capabilities", ([components :  [ capabilityEvent ]])) 
        }
        response.statusCode = iHttpSuccess       
    } catch (e) {
        logWarn "${app.getLabel()} smartCapabilityHandler() error: $e : $capabilityEvent"
    }    
    return [statusCode:response.statusCode]
}

Map replicaHasSmartCapability(replicaDevice, String capabilityId, Integer capabilityVersion=1) {
    Map response = [:]
    Map capability = getReplicaDataJsonValue(replicaDevice, "capabilities")
    if (capability?.components?.find { components -> components?.id == capabilityId && components?.version == capabilityVersion  }) {
        logDebug "Capability ${capabilityId} is cached"
        response = capability
    }
    return response
}

void getAllReplicaCapabilities(replicaDevice, Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all '$replicaDevice' device capabilities"
    runIn(delay<1?:1, getAllReplicaCapabilitiesHelper, [data: [deviceNetworkId:(replicaDevice.deviceNetworkId)]])
}

void getAllReplicaCapabilitiesHelper(Map data) {
    def replicaDevice = getDevice(data?.deviceNetworkId)
    getReplicaCapabilities(replicaDevice)    
}

void getReplicaCapabilities(replicaDevice) {
    logDebug "${app.getLabel()} executing 'getReplicaCapabilities($replicaDevice)'"

    String deviceId = getReplicaDeviceId(replicaDevice)
    Map description = getReplicaDataJsonValue(replicaDevice, "description")    
 
    description?.components.each { components ->
        components?.capabilities.each { capabilities ->
            if (replicaHasSmartCapability(replicaDevice, capabilities.id, capabilities.version) == [:]) {                
                getSmartDeviceCapability(deviceId, capabilities.id, capabilities.version)
                pauseExecution(250) // no need to hammer ST
            }
        }
    }
}

Map getSmartDeviceCapability(String deviceId, String capabilityId, Integer capabilityVersion=1) {
    logDebug "${app.getLabel()} executing 'getSmartDeviceCapability()'"
    Map response = [statusCode:iHttpError]    
	Map data = [ uri: sURI, path: "/capabilities/${capabilityId}/${capabilityVersion}", deviceId: deviceId, method: "getSmartDeviceCapability"	]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

void allSmartDeviceHealth(Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device health"
    runIn(delay<1?:1, getAllSmartDeviceHealth)
}

void getAllSmartDeviceHealth() {
    logDebug "${app.getLabel()} executing 'getAllSmartDeviceHealth()'"    
    runIn(60, setAllSmartDeviceHealthOffline) // if no reponse in 60 seconds, declare everything offline    
    getAllReplicaDeviceIds()?.each { deviceId ->
        getSmartDeviceHealth(deviceId)
        pauseExecution(250) // no need to hammer ST
    }
}

void setAllSmartDeviceHealthOffline() {
    logDebug "${app.getLabel()} executing 'setAllSmartDeviceHealthOffline()'"
    getAllReplicaDeviceIds()?.each { deviceId ->
        getReplicaDevices(deviceId)?.each { replicaDevice ->
            Map healthEvent = ["deviceId":deviceId, "state":"OFFLINE","lastUpdatedDate":getTimestampSmartFormat()]
            smartHealthHandler(replicaDevice, deviceId, healthEvent)
        }
    }
}

Map getSmartDeviceHealth(String deviceId) {
    logDebug "${app.getLabel()} executing 'getSmartDeviceHealth($deviceId)'"
    Map response = [statusCode:iHttpError]
	Map data = [ uri: sURI, path: "/devices/${deviceId}/health", deviceId: deviceId, method: "getSmartDeviceHealth" ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

void allSmartDeviceDescription(Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device descriptions"
    runIn(delay<1?:1, getAllDeviceDescription)
}

void getAllDeviceDescription() {
    logDebug "${app.getLabel()} executing 'getAllDeviceDescription()'"
    getAllReplicaDeviceIds()?.each { deviceId ->
        getSmartDeviceDescription(deviceId)
        pauseExecution(250) // no need to hammer ST
    }
}

Map getSmartDeviceDescription(String deviceId) {
    logDebug "${app.getLabel()} executing 'getSmartDeviceDescription($deviceId)'"
    Map response = [statusCode:iHttpError]
	Map data = [ uri: sURI, path: "/devices/${deviceId}", deviceId: deviceId, method: "getSmartDeviceDescription" ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

void allSmartDeviceStatus(Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device status"
    runIn(delay<1?:1, getAllSmartDeviceStatus)    
}

void getAllSmartDeviceStatus() {
    logDebug "${app.getLabel()} executing 'getAllSmartDeviceStatus()'"    
    getAllReplicaDeviceIds()?.each { deviceId ->
        getSmartDeviceStatus(deviceId)
        pauseExecution(250) // no need to hammer ST
    }
}

Map getSmartDeviceStatus(String deviceId) {
    logDebug "${app.getLabel()} executing 'getSmartDeviceStatus($deviceId)'"
    Map response = [statusCode:iHttpError]
	Map data = [ uri: sURI, path: "/devices/${deviceId}/status", deviceId: deviceId, method: "getSmartDeviceStatus" ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

private Map asyncHttpGet(String callbackMethod, Map data) {
    logDebug "${app.getLabel()} executing 'asyncHttpGet()'"
    Map response = [statusCode:iHttpError]
	
    Map params = [
	    uri: data.uri,
	    path: data.path,
		headers: [ Authorization: "Bearer ${getAuthToken()}" ]
    ]
	try {
	    asynchttpGet(callbackMethod, params, data)
        response.statusCode = iHttpSuccess
	} catch (e) {
	    logWarn "${app.getLabel()} asyncHttpGet() error: $e"
	}
    return response
}

void asyncHttpGetCallback(resp, data) {
    logDebug "${app.getLabel()} executing 'asyncHttpGetCallback()' status: ${resp.status} method: ${data?.method}"
    
    if (resp.status == iHttpSuccess) {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logTrace "response data: ${resp.data}"
        
        switch(data?.method) {
            case "getSmartDeviceHealth":
                def health = new JsonSlurper().parseText(resp.data)                
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartHealthHandler(replicaDevice, data.deviceId, health)  }
                unschedule('setAllSmartDeviceHealthOffline')
                health = null
                break
            case "getSmartDeviceCapability":
                def capability = new JsonSlurper().parseText(resp.data)            
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartCapabilityHandler(replicaDevice, capability) }
                capability = null
                break
            case "getSmartDeviceDescription":
                def description = new JsonSlurper().parseText(resp.data)
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartDescriptionHandler(replicaDevice, description); }
                description = null
                break
            case "getSmartDeviceStatus":
                Map status = new JsonSlurper().parseText(resp.data)        
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartStatusHandler(replicaDevice, data.deviceId, status) }
                status = null
                break      
            default:
                logWarn "${app.getLabel()} asyncHttpGetCallback() ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        logWarn("${app.getLabel()} asyncHttpGetCallback() ${data?.method} ${data?.deviceId ? getReplicaDevices(data.deviceId) : ""}  status:${resp.status} reason:${resp.errorMessage}")
    }
}

Map setSmartDeviceCommand(String deviceId, String component, String capability, String command, def arguments = []) {
    logDebug "${app.getLabel()} executing 'setSmartDeviceCommand()'"
    Map response = [statusCode:iHttpError]

    Map commands = [ commands: [[ component: component, capability: capability, command: command, arguments: arguments ]] ]
    Map params = [
        uri: sURI,
        path: "/devices/$deviceId/commands",
        body: JsonOutput.toJson(commands),
        method: "setSmartDeviceCommand",
        authToken: getAuthToken()        
    ]
    if(appLogEventEnable && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceId)) {
        log.info "Device:${getReplicaDevices(deviceId)?.each{ it?.label }} commands:<b>${JsonOutput.toJson(commands)}</b>"
    }
    response.statusCode = asyncHttpPostJson("asyncHttpPostCallback", params).statusCode
    return response
}

Map setVirtualDeviceAttribute(String deviceId, String component, String capability, String attribute, def value, String unit=null, Map data=[:]) {
    logDebug "${app.getLabel()} executing 'setVirtualDeviceAttribute()'"
    Map response = [statusCode:iHttpError]

    Map deviceEvents = [ deviceEvents: [ [ component: component, capability: capability, attribute: attribute, value: value, unit: unit, data: data ] ] ]
    Map params = [
        uri: sURI,
        path: "/virtualdevices/$deviceId/events",
        body: JsonOutput.toJson(deviceEvents),
        method: "setVirtualDeviceAttribute",
        authToken: getAuthToken()        
    ]
    if(appLogEventEnable && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceId)) {
        log.info "Device:${getReplicaDevices(deviceId)?.each{ it?.label }} deviceEvents:<b>${JsonOutput.toJson(deviceEvents)}</b>"
    }
    response.statusCode = asyncHttpPostJson("asyncHttpPostCallback", params).statusCode
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
		headers: [ Authorization: "Bearer ${getAuthToken()}" ]
    ]
	try {
	    asynchttpPost(callbackMethod, params, data)
        response.statusCode = iHttpSuccess
	} catch (e) {
	    logWarn "${app.getLabel()} asyncHttpPostJson() error: $e"
	}    
    return response
}

void asyncHttpPostCallback(resp, data) {
    logDebug "${app.getLabel()} executing 'asyncHttpPostCallback()' status: ${resp.status} method: ${data?.method}"
    
    if(resp.status==iHttpSuccess) {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logDebug "response data: ${resp.data}"
        
        switch(data?.method) {
            case "setSmartDeviceCommand":
            case "setVirtualDeviceAttribute":
                Map command = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} successful ${data?.method}:${command}"
                break
            default:
                logWarn "${app.getLabel()} asyncHttpPostCallback() ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logWarn("${app.getLabel()} asyncHttpPostCallback ${data?.method} body:${data?.body} status:${resp.status} reason:${resp.errorMessage}")
    }
}

Map oauthEventHandler(Map eventData, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'oauthEventHandler()'"
    Map response = [statusCode:iHttpSuccess]

    eventData?.events?.each { event ->
        switch(event?.eventType) {
            case 'DEVICE_EVENT':
                Map device = getSmartDeviceEventsCache(event?.deviceEvent?.deviceId)
                if(device?.eventCount!=null) device.eventCount += 1
                // this needs to be done here and not the smartEventHandler to allow for componentId support
                getReplicaDevices(event?.deviceEvent?.deviceId)?.each{ replicaDevice ->
                    if(hasCommand(replicaDevice, 'replicaEvent')) {
                        replicaDevice.replicaEvent(app, event)
                    }
                } 
                getReplicaDevices(event?.deviceEvent?.deviceId, event?.deviceEvent?.componentId)?.each{ replicaDevice ->
                    response.statusCode = smartEventHandler(replicaDevice, event?.deviceEvent, eventPostTime).statusCode
                }
                break
            case 'DEVICE_HEALTH_EVENT':
                String deviceId = event?.deviceHealthEvent?.deviceId
                Map healthEvent = [deviceId:deviceId, state:(event?.deviceHealthEvent?.status), lastUpdatedDate:(event?.eventTime), reason:'event']
                getReplicaDevices(deviceId)?.each{ replicaDevice ->
                    response.statusCode = smartHealthHandler(replicaDevice, deviceId, healthEvent, eventPostTime).statusCode
                    logInfo "${app.getLabel()} health event $replicaDevice is ${event?.deviceHealthEvent?.status.toLowerCase()}"
                }
                break
            case 'MODE_EVENT':
                logDebug "${app.getLabel()} mode event: $event"
                getAllReplicaDevices()?.each { replicaDevice ->
                    if(hasCommand(replicaDevice, 'setModeValue')) {
                        Map description = getReplicaDataJsonValue(replicaDevice, "description")
                        if(event?.modeEvent?.locationId==description?.locationId) {
                            Map device = getReplicaDeviceEventsCache(replicaDevice)
                            if(device?.eventCount!=null) device.eventCount += 1
                            updateSmartDeviceEventsStatus(replicaDevice)
                            replicaDevice.setModeValue(event?.modeEvent?.modeId)
                        }
                    }
                }            
                break
            case 'DEVICE_LIFECYCLE_EVENT':
                logTrace "${app.getLabel()} device lifecycle event: $event"
                switch(event?.deviceLifecycleEvent?.lifecycle) {
                    case 'CREATE':
                        logDebug "${app.getLabel()} CREATE locationId:${event?.deviceLifecycleEvent?.locationId} deviceId:${event?.deviceLifecycleEvent?.deviceId}"
                        getChildApps()?.findAll{ it?.getLocationId()==event?.deviceLifecycleEvent?.locationId }.each{ it?.createSmartDevice(event?.deviceLifecycleEvent?.locationId, event?.deviceLifecycleEvent?.deviceId) }
                        break
                    case 'DELETE':
                        logDebug "${app.getLabel()} DELETE locationId:${event?.deviceLifecycleEvent?.locationId} deviceId:${event?.deviceLifecycleEvent?.deviceId}"
                        getChildApps()?.findAll{ it?.getLocationId()==event?.deviceLifecycleEvent?.locationId }.each{ it?.deleteSmartDevice(event?.deviceLifecycleEvent?.locationId, event?.deviceLifecycleEvent?.deviceId) }
                        break
                    case 'UPDATE':
                        logDebug "${app.getLabel()} UPDATE locationId:${event?.deviceLifecycleEvent?.locationId} deviceId:${event?.deviceLifecycleEvent?.deviceId} update:${event?.deviceLifecycleEvent?.update}"
                        getChildApps()?.findAll{ it?.getLocationId()==event?.deviceLifecycleEvent?.locationId }.each{ it?.getSmartDeviceList() }                   
                        break
                    case 'ROOM_MOVE':
                        logDebug "${app.getLabel()} ROOM_MOVE locationId:${event?.deviceLifecycleEvent?.locationId} deviceId:${event?.deviceLifecycleEvent?.deviceId} roomMove:${event?.deviceLifecycleEvent?.roomMove}"
                        getChildApps()?.findAll{ it?.getLocationId()==event?.deviceLifecycleEvent?.locationId }.each{ it?.getSmartDeviceList() }
                        break
                    default:
                        logWarn "${app.getLabel()} oauthEventHandler() DEVICE_LIFECYCLE_EVENT did not handle $event"
                }
                break            
            default:
                logInfo "${app.getLabel()} oauthEventHandler() did not handle $event"
        }
    }
   
    return [statusCode:response.statusCode, eventData:{}]
}

// https://fontawesomeicons.com/svg/icons
@Field static final String sLogMuteIcon="""<svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"> <polygon points="1.75 5.75 1.75 10.25 4.25 10.25 8.25 13.25 8.25 2.75 4.25 5.75"/> <path d="m14.25 5.75-3.5 4.5m0-4.5 3.5 4.5"/> </svg>"""
@Field static final String sNoStatusIcon="""<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-exclamation-triangle" viewBox="0 0 16 16"> <path d="M7.938 2.016A.13.13 0 0 1 8.002 2a.13.13 0 0 1 .063.016.146.146 0 0 1 .054.057l6.857 11.667c.036.06.035.124.002.183a.163.163 0 0 1-.054.06.116.116 0 0 1-.066.017H1.146a.115.115 0 0 1-.066-.017.163.163 0 0 1-.054-.06.176.176 0 0 1 .002-.183L7.884 2.073a.147.147 0 0 1 .054-.057zm1.044-.45a1.13 1.13 0 0 0-1.96 0L.165 13.233c-.457.778.091 1.767.98 1.767h13.713c.889 0 1.438-.99.98-1.767L8.982 1.566z"/> <path d="M7.002 12a1 1 0 1 1 2 0 1 1 0 0 1-2 0zM7.1 5.995a.905.905 0 1 1 1.8 0l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 5.995z"/> </svg>"""

@Field static final String sSamsungIconStatic="""<svg style="display:none" version="2.0"><defs><symbol id="samsung-svg" viewbox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
<path fill="currentColor" d="m24.016 0.19927c-6.549 0-13.371 1.1088-18.203 5.5609-4.5279 5.1206-5.5614 11.913-5.5614 18.234v1.9451c0 6.5338 1.1094 13.417 5.5614 18.248 5.1509 4.5586 11.973 5.5614 18.324 5.5614h1.7622c6.5641 0 13.478-1.0942 18.34-5.5614 4.5586-5.1509 5.5614-11.988 5.5614-18.339v-1.7632c0-6.5638-1.0942-13.478-5.5614-18.324-5.1202-4.5431-11.897-5.5609-18.218-5.5609zm1.0077 7.2347c2.5689 0 4.6591 2.0435 4.6591 4.5553 0 2.1999-1.4161 4.0808-3.5398 4.5041v3.2794c3.0529 0.44309 5.2173 2.9536 5.2173 6.0591 0 3.416-2.8427 6.1945-6.3366 6.1945-3.4939 0-6.336-2.7785-6.336-6.1945 0-0.35031 0.03655-0.69134 0.09405-1.0258l-3.1724-1.0072c-0.81421 1.41-2.344 2.3115-4.0494 2.3115-0.4886 0-0.97399-0.0758-1.4418-0.22428-2.4433-0.77611-3.7851-3.3512-2.991-5.7402 0.62583-1.8828 2.406-3.1481 4.4302-3.1481 0.48824 0 0.97279 0.0754 1.4402 0.22427 1.1836 0.37606 2.1472 1.1805 2.712 2.265 0.42122 0.80821 0.58276 1.6995 0.47904 2.5797l3.1698 1.0072c0.90699-1.7734 2.8428-2.9998 4.9206-3.3011v-3.2794c-2.1237-0.42334-3.9145-2.3042-3.9145-4.5041 0-2.5118 2.0899-4.5553 4.6591-4.5553zm0 1.8221c-1.5413 0-2.7952 1.2261-2.7952 2.7332s1.2539 2.7326 2.7952 2.7326c1.5416 0 2.7957-1.2256 2.7957-2.7326s-1.2541-2.7332-2.7957-2.7332zm13.467 7.7417c2.0235 0 3.804 1.2655 4.4302 3.1486 0.38418 1.1568 0.28436 2.3906-0.28008 3.4747-0.5655 1.0844-1.5286 1.8889-2.7125 2.265-0.46708 0.14852-0.95146 0.22376-1.4397 0.22376-1.7053 0-3.2355-0.90075-4.0504-2.3104l-1.309 0.41651-0.57619-1.7332 1.3064-0.41496c-0.24412-2.1061 1.0506-4.1659 3.1905-4.8457 0.46814-0.14887 0.95285-0.22427 1.4407-0.22427zm-26.933 1.8221c-1.2143 0-2.2825 0.75934-2.6582 1.8893-0.47625 1.4333 0.32893 2.9781 1.7947 3.4437 0.28152 0.0896 0.57278 0.13539 0.86558 0.13539 1.2139 0 2.2818-0.75986 2.6572-1.8898 0.23107-0.69391 0.17072-1.4341-0.16795-2.0846-0.33937-0.65087-0.91663-1.1333-1.6268-1.3591-0.28152-0.0892-0.57209-0.13487-0.86455-0.13487zm26.933 0c-0.29245 0-0.58355 0.0456-0.86506 0.13487-1.4658 0.46567-2.2715 2.0109-1.7952 3.4442 0.37571 1.13 1.444 1.8888 2.6582 1.8888 0.2921 0 0.58287-0.0456 0.86403-0.13487 0.71085-0.22542 1.2883-0.7077 1.6273-1.3586 0.33867-0.65052 0.39867-1.3911 0.16795-2.0846-0.37571-1.1303-1.4436-1.8898-2.6572-1.8898zm-13.467 3.0034c-2.261 0-4.1 1.7982-4.1 4.008 0 2.2105 1.8391 4.0085 4.1 4.0085 2.2606 0 4.1-1.798 4.1-4.0085 0-2.2098-1.8394-4.008-4.1-4.008zm-5.5805 9.9746 1.509 1.0712-0.8077 1.0873c1.4651 1.5642 1.6559 3.9761 0.33228 5.7573-0.87489 1.1769-2.2862 1.879-3.775 1.879-0.98884 0-1.9356-0.30066-2.7378-0.87075-2.0796-1.4774-2.5419-4.3338-1.0309-6.3665 0.87418-1.1769 2.2852-1.8795 3.775-1.8795 0.67275 0 1.3247 0.14236 1.9265 0.41083zm11.166 0 0.80822 1.0878c0.60148-0.26846 1.2527-0.41135 1.9255-0.41135 1.488 0 2.8985 0.70228 3.7724 1.8784 1.5099 2.0327 1.0474 4.8869-1.0304 6.3629-0.80116 0.56903-1.7473 0.86972-2.7358 0.86972-1.4891 0-2.8986-0.70212-3.7724-1.8779-1.3222-1.7787-1.1316-4.1886 0.33176-5.7521l-0.80719-1.0862zm2.7337 2.4986c-0.59196 0-1.1587 0.18096-1.6402 0.52245-1.2467 0.88618-1.524 2.599-0.61805 3.8179 0.52388 0.70556 1.3702 1.1265 2.2645 1.1265 0.59196 0 1.1597-0.18063 1.6402-0.52141 1.2471-0.88583 1.5241-2.5992 0.61857-3.8184-0.52458-0.7059-1.3714-1.1271-2.265-1.1271zm-16.635 3e-3c-0.89464 0-1.7419 0.42116-2.2665 1.1271-0.90629 1.2203-0.62869 2.9339 0.61908 3.8204 0.48119 0.3422 1.0489 0.52245 1.6412 0.52245 0.89394 0 1.7414-0.42132 2.266-1.1276 0.90664-1.2203 0.62956-2.9339-0.61857-3.8204-0.48084-0.34184-1.0482-0.52193-1.6412-0.52193z">
</path></symbol></defs><use href="#samsung-svg"/></svg>"""
@Field static final String sSamsungIcon="""<svg width="1.0em" height="1.0em" version="2.0"><use href="#samsung-svg"/></svg>"""

@Field static final String sHubitatIconStatic="""<svg style="display:none" version="2.0"><defs><symbol id="hubitat-svg" viewbox="0 0 130 130" xmlns="http://www.w3.org/2000/svg">
<path fill="currentColor" d="m40.592 3.6222c-1.4074 0.031312-2.9567 0.66796-4.1641 1.0723-1.6729 0.56186-3.2257 1.44-4.7793 2.2676-3.2391 1.7251-6.4448 4.0107-9.2188 6.4102-5.7396 4.9663-10.707 10.694-14.391 17.355-4.2146 7.6219-6.8328 16.011-7.6641 24.684-0.79583 8.314 0.58216 17.529 3.1426 25.533 3.5646 11.143 11.782 29.281 37.533 40.475 19.111 8.3084 40.065 4.959 54.133-2.2676 14.629-7.512 26.684-21.062 31.73-36.793 0.0106-0.0032 0.0576-0.1198 0.21484-0.63086 2.8705-9.9146 3.2267-15.26 2.3106-24.543-0.9192-10.232-3.4611-15.992-5.5606-20.678-1.9937-4.452-4.4114-9.2086-7.666-12.887-0.40521 0.33078 2.6483 3.3871 2.5488 4.123-0.43226-0.01825-1.1165-1.4416-1.3613-1.7734-0.2244-0.30414-0.72566-0.51094-0.8418-0.70898-0.79947-1.3631-2.0565-2.6974-3.1289-3.8594-0.37815 0.13803 0.33559 0.54626 0.16211 0.63281 0.012-0.0063-2.4119-2.5439-2.5723-2.7012-0.13547 0.14321 3.1729 3.8723 3.5391 4.3359 0.29373 0.37652 2.8795 3.439 2.5879 3.7637-0.4136-0.87923-6.1644-7.4554-7.1523-8.3887-0.41147-0.38906-1.8488-2.041-2.3926-1.9238 0.55306 0.70934 1.2317 1.3052 1.8516 1.9531 0.3208 0.33294 0.6153 0.93792 0.99609 1.1836-0.25839 0.07347-1.0788-0.87933-1.3125-1.1016-0.31-0.29628-1.9434-2.1659-2.3633-1.9883-0.16866 0.47652 3.3778 3.5548 3.8008 4.0625 0.61361 0.73962 1.8546 1.6129 2.1582 2.6113-1.2009-0.85361-5.7741-6.1246-7.1699-7.334-1.376-1.189-2.7999-2.367-4.3223-3.3633-0.4896-0.32077-3.3759-2.5313-3.8535-2.2285 1.0765 1.0959 2.8324 1.9251 4.0996 2.8496 1.45 1.0588 2.8712 2.1607 4.2129 3.3555 1.5307 1.364 2.9504 2.8516 4.2852 4.4062 0.97187 1.1312 2.5503 2.58 3.1035 3.9707-0.39694-0.17598-0.66443-0.63092-0.93165-0.94727-0.62293-0.73652-4.6414-5.0809-6.1367-6.4043-2.8256-2.4991-5.9318-4.9751-9.2578-6.7734-0.02814 0.63798 3.288 2.291 3.8984 2.752 1.2073 0.912 2.5721 1.8036 3.5918 2.9297-0.28027-0.13077-1.1122-0.95164-1.3242-0.75586 0.522 0.53439 1.1364 0.99056 1.6894 1.4941 2.5532 2.3251 4.4689 4.4836 6.6465 7.2227 2.5588 3.2177 4.8833 6.6804 6.6562 10.395 3.7249 7.8109 5.6608 15.932 6.0742 24.561 0.19746 3.9968-0.15858 8.5023-1.1758 12.381-1.0656 4.0678-2.0254 8.1482-3.8144 11.98-3.3724 7.224-8.0298 14.858-14.357 19.869-3.2312 2.5577-6.313 5.0111-9.9453 7.0059-3.8025 2.088-7.8317 3.8475-11.986 5.1074-8.2923 2.5162-17.204 3.3879-25.764 1.6504-4.2604-0.86669-8.4568-1.709-12.484-3.4121-3.8161-1.6151-7.443-3.6428-10.871-5.9668-6.7484-4.5781-12.572-10.857-16.619-17.937-8.736-15.276-10.322-33.882-3.6016-50.25 3.0911-7.5292 7.8795-14.31 13.855-19.828 2.9855-2.7552 6.2349-5.2298 9.7109-7.3359 0.99693-0.6048 11.073-5.5999 10.926-6.0332-0.02293-0.06811-1.5626-0.48959-1.8184-0.40039-0.46573 0.16146-6.9184 3.3332-9.6758 4.7461 1.5713-0.80572 3.0907-1.7893 4.6152-2.6855 0.30613-0.18028 4.3375-2.776 4.3828-2.7363-0.54226-0.49213-2.6009 0.74097-3.0176 0.94922 0.6172-0.31147 1.2072-0.70483 1.7988-1.0586 0.2984-0.17866 0.63032-0.34222 0.89844-0.56445 0.01561-0.01251-0.13137-0.61428-0.24805-0.5625-1.0969 0.49157-11.565 6.1743-15.629 8.6133 1.1224-0.65412 2.1609-1.5684 3.2754-2.2617 4.1839-2.6052 8.5841-4.8702 12.996-7.0566-0.45467 0.22492-4.8364 1.6149-4.9629 1.3848 0.01506 0.027061 3.3272-1.5054 3.3359-1.4902-0.29534-0.53707-6.1584 1.9871-6.8887 2.1953 0.07453 0.029216 2.2283-1.2006 2.5059-1.3242 0.364-0.16256 2.1408-1.2509 2.498-1.125-0.4199-0.15493-0.87267-0.21161-1.3418-0.20117zm48.314 2.3438c0.94267 0.38642 1.7734 0.88288 2.6953 1.2344 0.33693 0.1276 0.75174 0.22808 0.9668 0.58789 0.0516 0.08443 1.196 0.49155 1.2559 0.47266 0.22401-0.08088 0.39012 0.04834 0.5625 0.12695 1.2395 0.5692 4.0458 2.2936 4.3203 2.3926 0-0.06057 0.0062-0.12098 0.0078-0.17969-0.0932-0.11528-0.22544-0.17939-0.3457-0.25195-2.8161-1.7344-5.8256-3.0624-8.8984-4.2676-0.16826-0.065764-0.33526-0.14925-0.56445-0.11523zm5.3203 1.4473c0.90934 0.47293 1.8178 0.94504 2.7246 1.418 0.01398-0.02494 0.02741-0.05349 0.04102-0.07617-0.8792-0.53027-1.7814-1.016-2.7656-1.3418zm8.166 4.0059c0.66258 0.48385 4.5548 3.4835 5.7637 4.6035 0.0745 0.06917 0.17375 0.22194 0.27735 0.10742 0.0995-0.11263-0.0594-0.19835-0.13867-0.26562-0.38014-0.32451-3.463-2.886-4.6641-3.7891-0.37487-0.27889-0.76159-0.54788-1.2383-0.65625zm5.748 2.5527c-0.016 0.01678-0.0358 0.03613-0.0547 0.05469 0.9068 0.84839 4.0619 3.8602 4.7363 4.5176 0.0219-0.0257 0.0438-0.04776 0.0684-0.07422-0.064-0.14778-0.19091-0.24592-0.30078-0.35742-0.5292-0.54055-1.0509-1.0914-1.5977-1.6133-0.9188-0.88029-1.8485-1.7429-2.8516-2.5273zm2.6738 0.80273c-0.0219 0.01587-0.0413 0.03579-0.0625 0.05469 0.0235 0.03651 2.7538 2.7734 4.0371 4.1641 0.0537 0.05632 0.0977 0.13753 0.22265 0.10352 0-0.1081-0.0765-0.17491-0.14062-0.24219-1.2364-1.3588-2.4809-2.704-3.8496-3.9316-0.0605-0.05632-0.13462-0.09855-0.20703-0.14844zm-0.10742 1.0605c-0.0149-0.0083-0.0315-0.0064-0.0469 0.01172-0.0344 0.03402 4e-3 0.06074 0.0391 0.07812 0.0276 0.03024 1.0764 1.1106 1.1035 1.1387 0.3724 0.37493 0.7455 0.74812 1.1152 1.123 0.0287 0.02646 2.1856 2.416 2.1856 2.416 0.0515 0.04989 0.10661 0.09937 0.1582 0.15039 0.0219-0.05783 0.0591-0.09993 0.11914-0.12109-0.64279-0.82133-1.37-1.5658-2.123-2.2871 0 0-0.10642-0.10818-0.17578-0.17578-0.0231-0.02254-0.0522-0.05557-0.0586-0.05859v-2e-3c-0.33441-0.4292-0.70386-0.82018-1.1445-1.1426-2e-3 -2e-3 -0.0713-0.06788-0.084-0.08008-0.15961-0.15308-1.0195-0.99002-1.0469-1.0117-0.0121-0.01482-0.0265-0.03094-0.041-0.03906zm-44.055 2.5215c-0.05185-0.01091-0.09832-0.0095-0.13672 0.0098-0.786 0.39281-23.224 19.66-26.447 22.463-1.6385 1.4245-3.2613 2.8666-4.5801 4.6016-0.46866 0.61455-1.1188 1.6068-0.8125 2.4219 0.23534 0.62865 0.59362 1.5561 0.9707 2.1191 0.52293 0.77813 0.3716 1.8222 1.6055 1.3613 0.86614-0.32349 6.0136-3.4432 7.7266-4.6094 3.7323-2.5416 17.33-13.594 20.539-16.408 0.27453-0.24216 0.77888-0.97611 1.1914-0.60156 0.31867 0.28694 0.66743 0.54869 0.99609 0.82422 0.82973 0.69267 21.763 17.29 23.613 18.781 0.53174 0.42973 1.9878 1.1834 1.9961 1.9512 0.01107 1.0735 0.12435 21.487 0.04102 30.623-0.0088 1.0147-3.125 0.98206-4.1875 0.98047-2.1297-3e-3 -11.14 0.13748-13.516 0.16016-2.0917 0.01825-4.1537 0.32392-6.2402 0.4082-4.3443 0.17654-21.704 1.4142-22.695 1.5352-0.64533 0.0796-1.7726-0.04032-2.2793 0.45703 1.5572 0.01701 3.1126 0.03774 4.6699 0.05664-2.2147 0.59002-4.8056 0.51081-7.0879 0.68164-2.4401 0.18123-4.9662 0.2512-7.3594 0.79492 0.7932 0.26192 1.6308 0.1482 2.459 0.22266 0.59586 0.048-0.23716 0.45396-0.28516 0.54883 0.18494 0.02268 0.35214 0.09094 0.5 0.20508-0.42867 0.3157-1.1119 0.1143-1.4473 0.56641 0.50733 0.22613 5.8137-0.19418 5.8262 0.52344-0.3244 0.1251-0.69258 0.02285-0.98633 0.25 1.0296 0.04346 2.0183 0.22877 3.0605 0.23633 0.40253 0 1.1166 0.05761 1.4863 0.17969 0.52293 0.17133-1.1513 0.58827-1.248 0.59961-0.69173 0.08655-2.4595 0.08395-2.6309 0.21094 2.2625 0.16025 4.5287-0.0031 6.7891 0.18359 2.1573 0.18123 4.3165 0.32386 6.4785 0.40625 4.2989 0.16668 30.235 0.23985 38.902 0.22852 2.1563-0.0015 4.4357-0.21042 6.5566 0.27344 0.58227 0.13236 0.89969-0.35917 1.5352-0.2832 0.52525 0.07922 0.15229-0.85472 0.56055-0.91406 0.10945-0.01466-0.36158-4.6921-0.36914-5.2285-0.0375-2.1011-0.35956-34.458-0.36523-35.674-4e-3 -0.63028 0.21192-1.8778-0.0859-2.4434-0.3708-0.70893-1.5804-1.3625-2.1934-1.875-0.25951-0.21777-9.2403-7.6819-12.693-10.43-6.3213-5.0319-12.643-10.08-18.689-15.445-0.16684-0.14862-0.80503-0.87675-1.168-0.95312zm48.371 0.74609c0.0145 0.22046 0.12749 0.29406 0.20508 0.38476 0.28653 0.34016 2.1462 2.6135 2.791 3.4141 0.0369-0.03628 0.0758-0.07158 0.11132-0.10938-0.0901-0.23494-1.6863-2.1365-2.3144-2.9141-0.22027-0.26933-0.4575-0.52292-0.79297-0.77539zm3.748 8.6816c0.0885-0.08228 0.31395 0.34306 0.29883 0.38086 9e-3 -0.03326-0.14404-0.1269-0.17578-0.17188-0.0276-0.0094-0.17411-0.15796-0.12305-0.20898z">
</path></symbol></defs><use href="#hubitat-svg"/></svg>"""       
@Field static final String sHubitatIcon="""<svg width="1.0em" height="1.0em" version="2.0"><use href="#hubitat-svg"/></svg>"""
               
@Field static final String sImgRule = """<img style="margin-Top:-6px" height="18" src='data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGlkPSJMYXllcl8xIiBkYXRhLW5hbWU9IkxheWVyIDEiIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjUxMiIgaGVpZ2h0PSI1MTIiPjxwYXRoIGQ9Ik0yMSwxMmMwLS41NC0uMDUtMS4wOC0uMTUtMS42M2wzLjA1LTEuNzUtMi45OS01LjItMy4wNSwxLjc1Yy0uODQtLjcyLTEuODEtMS4yOC0yLjg2LTEuNjVWMGgtNlYzLjUyYy0xLjA1LC4zNy0yLjAyLC45My0yLjg2LDEuNjVsLTMuMDUtMS43NUwuMSw4LjYybDMuMDUsMS43NWMtLjEsLjU0LS4xNSwxLjA5LS4xNSwxLjYzcy4wNSwxLjA4LC4xNSwxLjYzTC4xLDE1LjM4bDIuOTksNS4yLDMuMDUtMS43NWMuODQsLjcyLDEuODEsMS4yOCwyLjg2LDEuNjV2My41Mmg2di0zLjUyYzEuMDUtLjM3LDIuMDItLjkzLDIuODYtMS42NWwzLjA1LDEuNzUsMi45OS01LjItMy4wNS0xLjc1Yy4xLS41NCwuMTUtMS4wOSwuMTUtMS42M1ptLjE3LDQuMTJsLTEsMS43My0yLjYzLTEuNTEtLjU0LC41NWMtLjg5LC45MS0yLjAxLDEuNTYtMy4yNSwxLjg4bC0uNzUsLjE5djMuMDRoLTJ2LTMuMDRsLS43NS0uMTljLTEuMjQtLjMyLTIuMzYtLjk3LTMuMjUtMS44OGwtLjU0LS41NS0yLjYzLDEuNTEtMS0xLjczLDIuNjMtMS41MS0uMjEtLjc1Yy0uMTctLjYyLS4yNi0xLjI1LS4yNi0xLjg2cy4wOS0xLjIzLC4yNi0xLjg2bC4yMS0uNzUtMi42My0xLjUxLDEtMS43MywyLjYzLDEuNTEsLjU0LS41NWMuODktLjkxLDIuMDEtMS41NiwzLjI1LTEuODhsLjc1LS4xOVYyaDJ2My4wNGwuNzUsLjE5YzEuMjQsLjMyLDIuMzYsLjk3LDMuMjUsMS44OGwuNTQsLjU1LDIuNjMtMS41MSwxLDEuNzMtMi42MywxLjUxLC4yMSwuNzVjLjE3LC42MiwuMjYsMS4yNSwuMjYsMS44NnMtLjA5LDEuMjMtLjI2LDEuODZsLS4yMSwuNzUsMi42MywxLjUxWk0xMi4wMiw2LjhsMS45NiwuMzktMiwxMC0xLjk2LS4zOSwyLTEwWm0tMi4zMSwzLjU0bC0xLjcxLDEuNzEsMS43MSwxLjY5LTEuNDEsMS40MS0xLjcxLTEuNzFjLS43OC0uNzgtLjc4LTIuMDQsMC0yLjgxbDEuNzEtMS43MSwxLjQxLDEuNDFabTcuNzEsLjNjLjc4LC43OCwuNzgsMi4wNCwwLDIuODFsLTEuNzEsMS43MS0xLjQxLTEuNDEsMS43MS0xLjcxLTEuNzEtMS43LDEuNDEtMS40MSwxLjcxLDEuNzFaIi8+PC9zdmc+Cg=='/>"""
@Field static final String sImgDevv = """<img style="margin-Top:-6px;transform: scaleX(-1);" height="22" src='data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGlkPSJMYXllcl8xIiBkYXRhLW5hbWU9IkxheWVyIDEiIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjUxMiIgaGVpZ2h0PSI1MTIiPjxwYXRoIGQ9Ik0yMi43MywxOS4wNWwtLjk4LS41NWMuMTUtLjQ4LC4yNi0uOTgsLjI2LTEuNXMtLjEtMS4wMy0uMjYtMS41bC45OC0uNTVjLjQ4LS4yNywuNjUtLjg4LC4zOS0xLjM2LS4yNy0uNDgtLjg4LS42Ni0xLjM2LS4zOWwtLjk4LC41NWMtLjcxLS44Mi0xLjY3LTEuNDItMi43Ny0xLjY1di0xLjFjMC0uNTUtLjQ1LTEtMS0xcy0xLC40NS0xLDF2MS4xYy0xLjEsLjIyLTIuMDYsLjgzLTIuNzcsMS42NWwtLjk4LS41NWMtLjQ4LS4yNy0xLjA5LS4xLTEuMzYsLjM5LS4yNywuNDgtLjEsMS4wOSwuMzksMS4zNmwuOTgsLjU1Yy0uMTUsLjQ4LS4yNiwuOTgtLjI2LDEuNXMuMSwxLjAzLC4yNiwxLjVsLS45OCwuNTVjLS40OCwuMjctLjY1LC44OC0uMzksMS4zNiwuMTgsLjMzLC41MiwuNTEsLjg3LC41MSwuMTcsMCwuMzMtLjA0LC40OS0uMTNsLjk4LS41NWMuNzEsLjgyLDEuNjcsMS40MiwyLjc3LDEuNjV2MS4xYzAsLjU1LC40NSwxLDEsMXMxLS40NSwxLTF2LTEuMWMxLjEtLjIyLDIuMDYtLjgzLDIuNzctMS42NWwuOTgsLjU1Yy4xNSwuMDksLjMyLC4xMywuNDksLjEzLC4zNSwwLC42OS0uMTgsLjg3LS41MSwuMjctLjQ4LC4xLTEuMDktLjM5LTEuMzZabS01LjczLC45NWMtMS42NSwwLTMtMS4zNS0zLTNzMS4zNS0zLDMtMywzLDEuMzUsMywzLTEuMzUsMy0zLDNabS02LjIzLTkuNzVsLjk4LC41NWMuMTUsLjA5LC4zMiwuMTMsLjQ5LC4xMywuMzUsMCwuNjktLjE4LC44Ny0uNTEsLjI3LS40OCwuMS0xLjA5LS4zOS0xLjM2bC0uOTgtLjU1Yy4xNS0uNDgsLjI2LS45OCwuMjYtMS41cy0uMS0xLjAzLS4yNi0xLjVsLjk4LS41NWMuNDgtLjI3LC42NS0uODgsLjM5LTEuMzYtLjI3LS40OC0uODgtLjY2LTEuMzYtLjM5bC0uOTgsLjU1Yy0uNzEtLjgyLTEuNjctMS40Mi0yLjc3LTEuNjVWMWMwLS41NS0uNDUtMS0xLTFzLTEsLjQ1LTEsMXYxLjFjLTEuMSwuMjItMi4wNiwuODMtMi43NywxLjY1bC0uOTgtLjU1Yy0uNDgtLjI3LTEuMDktLjEtMS4zNiwuMzktLjI3LC40OC0uMSwxLjA5LC4zOSwxLjM2bC45OCwuNTVjLS4xNSwuNDgtLjI2LC45OC0uMjYsMS41cy4xLDEuMDMsLjI2LDEuNWwtLjk4LC41NWMtLjQ4LC4yNy0uNjUsLjg4LS4zOSwxLjM2LC4xOCwuMzMsLjUyLC41MSwuODcsLjUxLC4xNywwLC4zMy0uMDQsLjQ5LS4xM2wuOTgtLjU1Yy43MSwuODIsMS42NywxLjQyLDIuNzcsMS42NXYxLjFjMCwuNTUsLjQ1LDEsMSwxczEtLjQ1LDEtMXYtMS4xYzEuMS0uMjIsMi4wNi0uODMsMi43Ny0xLjY1Wm0tMy43Ny0uMjVjLTEuNjUsMC0zLTEuMzUtMy0zczEuMzUtMywzLTMsMywxLjM1LDMsMy0xLjM1LDMtMywzWiIvPjwvc3ZnPgo='/>"""
@Field static final String sImgMirr = """<img style="margin-Top:-6px" height="18" src='data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjUxMiIgaGVpZ2h0PSI1MTIiPjxnIGlkPSJfMDFfYWxpZ25fY2VudGVyIiBkYXRhLW5hbWU9IjAxIGFsaWduIGNlbnRlciI+PHBhdGggZD0iTTkuMzU2LDAsLjM3NSwxOS43NTlBMywzLDAsMCwwLDMuMTA2LDI0SDExVjEuMDQ2TDEwLjk5MywwWk05LDIySDMuMTA2YTEsMSwwLDAsMS0uOTExLTEuNDE0TDksNS42MTZaIi8+PHBhdGggZD0iTTIzLjYyNSwxOS43NTksMTQuOTMuNjI4LDE0LjYyNiwwSDEzVjI0aDcuODk0YTMsMywwLDAsMCwyLjczMS00LjI0MVoiLz48L2c+PC9zdmc+Cg=='/>"""
@Field static final String sImgDevh = """<img style="margin-Top:-6px;" height="22" src='data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGlkPSJMYXllcl8xIiBkYXRhLW5hbWU9IkxheWVyIDEiIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjUxMiIgaGVpZ2h0PSI1MTIiPjxwYXRoIGQ9Ik0yMi43MywxOS4wNWwtLjk4LS41NWMuMTUtLjQ4LC4yNi0uOTgsLjI2LTEuNXMtLjEtMS4wMy0uMjYtMS41bC45OC0uNTVjLjQ4LS4yNywuNjUtLjg4LC4zOS0xLjM2LS4yNy0uNDgtLjg4LS42Ni0xLjM2LS4zOWwtLjk4LC41NWMtLjcxLS44Mi0xLjY3LTEuNDItMi43Ny0xLjY1di0xLjFjMC0uNTUtLjQ1LTEtMS0xcy0xLC40NS0xLDF2MS4xYy0xLjEsLjIyLTIuMDYsLjgzLTIuNzcsMS42NWwtLjk4LS41NWMtLjQ4LS4yNy0xLjA5LS4xLTEuMzYsLjM5LS4yNywuNDgtLjEsMS4wOSwuMzksMS4zNmwuOTgsLjU1Yy0uMTUsLjQ4LS4yNiwuOTgtLjI2LDEuNXMuMSwxLjAzLC4yNiwxLjVsLS45OCwuNTVjLS40OCwuMjctLjY1LC44OC0uMzksMS4zNiwuMTgsLjMzLC41MiwuNTEsLjg3LC41MSwuMTcsMCwuMzMtLjA0LC40OS0uMTNsLjk4LS41NWMuNzEsLjgyLDEuNjcsMS40MiwyLjc3LDEuNjV2MS4xYzAsLjU1LC40NSwxLDEsMXMxLS40NSwxLTF2LTEuMWMxLjEtLjIyLDIuMDYtLjgzLDIuNzctMS42NWwuOTgsLjU1Yy4xNSwuMDksLjMyLC4xMywuNDksLjEzLC4zNSwwLC42OS0uMTgsLjg3LS41MSwuMjctLjQ4LC4xLTEuMDktLjM5LTEuMzZabS01LjczLC45NWMtMS42NSwwLTMtMS4zNS0zLTNzMS4zNS0zLDMtMywzLDEuMzUsMywzLTEuMzUsMy0zLDNabS02LjIzLTkuNzVsLjk4LC41NWMuMTUsLjA5LC4zMiwuMTMsLjQ5LC4xMywuMzUsMCwuNjktLjE4LC44Ny0uNTEsLjI3LS40OCwuMS0xLjA5LS4zOS0xLjM2bC0uOTgtLjU1Yy4xNS0uNDgsLjI2LS45OCwuMjYtMS41cy0uMS0xLjAzLS4yNi0xLjVsLjk4LS41NWMuNDgtLjI3LC42NS0uODgsLjM5LTEuMzYtLjI3LS40OC0uODgtLjY2LTEuMzYtLjM5bC0uOTgsLjU1Yy0uNzEtLjgyLTEuNjctMS40Mi0yLjc3LTEuNjVWMWMwLS41NS0uNDUtMS0xLTFzLTEsLjQ1LTEsMXYxLjFjLTEuMSwuMjItMi4wNiwuODMtMi43NywxLjY1bC0uOTgtLjU1Yy0uNDgtLjI3LTEuMDktLjEtMS4zNiwuMzktLjI3LC40OC0uMSwxLjA5LC4zOSwxLjM2bC45OCwuNTVjLS4xNSwuNDgtLjI2LC45OC0uMjYsMS41cy4xLDEuMDMsLjI2LDEuNWwtLjk4LC41NWMtLjQ4LC4yNy0uNjUsLjg4LS4zOSwxLjM2LC4xOCwuMzMsLjUyLC41MSwuODcsLjUxLC4xNywwLC4zMy0uMDQsLjQ5LS4xM2wuOTgtLjU1Yy43MSwuODIsMS42NywxLjQyLDIuNzcsMS42NXYxLjFjMCwuNTUsLjQ1LDEsMSwxczEtLjQ1LDEtMXYtMS4xYzEuMS0uMjIsMi4wNi0uODMsMi43Ny0xLjY1Wm0tMy43Ny0uMjVjLTEuNjUsMC0zLTEuMzUtMy0zczEuMzUtMywzLTMsMywxLjM1LDMsMy0xLjM1LDMtMywzWiIvPjwvc3ZnPgo='/>"""

/*
 * Natural Sort algorithm for Javascript - Version 0.7 - Released under MIT license
 * Author: Jim Palmer (based on chunking idea from Dave Koelle)
 * Contributors: Mike Grier (mgrier.com), Clint Priest, Kyle Adams, guillermo
 * See: http://js-naturalsort.googlecode.com/svn/trunk/naturalSort.js
 * https://datatables.net/plug-ins/sorting/natural#Plug-in-code
 */
@Field static final String naturalSortFunction = """
(function() { function naturalSort (a, b, html) {
    var re = /(^-?[0-9]+(\\.?[0-9]*)[df]?e?[0-9]?%?\$|^0x[0-9a-f]+\$|[0-9]+)/gi, sre = /(^[ ]*|[ ]*\$)/g, hre = /^0x[0-9a-f]+\$/i, ore = /^0/, htmre = /(<([^>]+)>)/ig, 
        dre = /(^([\\w ]+,?[\\w ]+)?[\\w ]+,?[\\w ]+\\d+:\\d+(:\\d+)?[\\w ]?|^\\d{1,4}[\\/\\-]\\d{1,4}[\\/\\-]\\d{1,4}|^\\w+, \\w+ \\d+, \\d{4})/,        
        x = a.toString().replace(sre, '') || '', y = b.toString().replace(sre, '') || '';
        if (!html) { x = x.replace(htmre, ''); y = y.replace(htmre, ''); }
    var xN = x.replace(re, '\0\$1\0').replace(/\0\$/,'').replace(/^\0/,'').split('\0'), yN = y.replace(re, '\0\$1\0').replace(/\0\$/,'').replace(/^\0/,'').split('\0'),
        xD = parseInt(x.match(hre), 10) || (xN.length !== 1 && x.match(dre) && Date.parse(x)),  yD = parseInt(y.match(hre), 10) || xD && y.match(dre) && Date.parse(y) || null;
    if (yD) { if ( xD < yD ) { return -1; } else if ( xD > yD ) { return 1; } } 
    for(var cLoc=0, numS=Math.max(xN.length, yN.length); cLoc < numS; cLoc++) {
        var oFxNcL = !(xN[cLoc] || '').match(ore) && parseFloat(xN[cLoc], 10) || xN[cLoc] || 0; var oFyNcL = !(yN[cLoc] || '').match(ore) && parseFloat(yN[cLoc], 10) || yN[cLoc] || 0;
        if (isNaN(oFxNcL) !== isNaN(oFyNcL)) { return (isNaN(oFxNcL)) ? 1 : -1; }
        else if (typeof oFxNcL !== typeof oFyNcL) { oFxNcL += ''; oFyNcL += ''; } if (oFxNcL < oFyNcL) { return -1; }
        if (oFxNcL > oFyNcL) { return 1; }
    }
    return 0;
} 
jQuery.extend( jQuery.fn.dataTableExt.oSort, {
    "natural-asc": function ( a, b ) { return naturalSort(a,b,true); }, 
    "natural-desc": function ( a, b ) { return naturalSort(a,b,true) * -1; }, 
    "natural-nohtml-asc": function( a, b ) { return naturalSort(a,b,false); }, 
    "natural-nohtml-desc": function( a, b ) { return naturalSort(a,b,false) * -1; }, 
    "natural-ci-asc": function( a, b ) { a = a.toString().toLowerCase(); b = b.toString().toLowerCase(); return naturalSort(a,b,true); }, 
    "natural-ci-desc": function( a, b ) { a = a.toString().toLowerCase(); b = b.toString().toLowerCase(); return naturalSort(a,b,true) * -1; }
}); 
}());
"""

// thanks to DCMeglio (Hubitat Package Manager) for a lot of formatting hints
String getFormat(type, myText="", myHyperlink="", myColor=sColorDarkBlue){   
    if(type == "line")      return "<hr style='background-color:$myColor; height: 1px; border: 0;'>"
	if(type == "title")     return "<h2 style='color:$myColor;font-weight: bold'>${myText}</h2>"
    if(type == "text")      return "<span style='color:$myColor;font-weight: bold'>${myText}</span>"
    if(type == "hyperlink") return "<a href='${myHyperlink}' target='_blank' rel='noopener noreferrer' style='color:$myColor;font-weight:bold'>${myText}</a>"
    if(type == "comments")  return "<div style='color:$myColor;font-weight:small;font-size:14px;'>${myText}</div>"
}
String errorMsg(String msg) { getFormat("text", msg, null, sColorDarkRed) }
String statusMsg(String msg) { getFormat("text", msg, null, sColorDarkBlue) }

def displayHeader() { 
    section (getFormat("title", "${app.getLabel()}${sCodeRelease?.size() ? " : $sCodeRelease" : ""}"  )) { 
        paragraph "<div style='color:${sColorDarkBlue};text-align:right;font-weight:small;font-size:9px;'>Developed by: ${author()}<br/>Current Version: v${version()} -  ${copyright()}</div>"
        paragraph( getFormat("line") ) 
    }
}

public static String paypal() { return "https://www.paypal.com/donate/?business=QHNE3ZVSRYWDA&no_recurring=1&currency_code=USD" }
def displayFooter(){
    Long day14ms = 14*86400000
    if(now()>(state.isInstalled+day14ms)) {
	    section() {
		    paragraph( getFormat("line") )
		    paragraph( rawHtml: true,  """<div style='color:{sColorDarkBlue};text-align:center;font-weight:small;font-size:11px;'>$sDefaultAppName<br><br><a href='${paypal()}' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This application took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>""")
	    }
    }
}

def menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}

private logInfo(msg)  { if(!appInfoDisable) { log.info "${msg}"  } }
private logDebug(msg) { if(appDebugEnable)  { log.debug "${msg}" } }
private logTrace(msg) { if(appTraceEnable)  { log.trace "${msg}" } }
private logWarn(msg)  { log.warn  "${msg}" } 
private logError(msg) { log.error "${msg}" }

// ******** Child and Mirror Device get Functions - Start ********
List<com.hubitat.app.DeviceWrapper> getAllDevices() {    
    List<com.hubitat.app.DeviceWrapper> devices = getChildDevices()    
    getAuthorizedDevices()?.each{ userAuthorizedDevice ->
        // don't repeat child devices that might be added
        if( !devices?.find{ it.deviceNetworkId == userAuthorizedDevice.deviceNetworkId } ) 
            devices.add(userAuthorizedDevice)
    }        
    return devices?.sort{ it.getDisplayName() }
}

List<com.hubitat.app.DeviceWrapper> getAuthorizedDevices() {    
    List<ChildDeviceWrapper> devices = []   
    if( ((Map<String,Object>)settings).find{ it.key == "userAuthorizedDevices" } ) {
        userAuthorizedDevices?.each{ userAuthorizedDevice ->
            devices.add(userAuthorizedDevice)
        }
    }    
    return devices?.sort{ it.getDisplayName() }
}

com.hubitat.app.DeviceWrapper getDevice(deviceNetworkId) {
    return getAllDevices()?.find{ it.deviceNetworkId == deviceNetworkId } // only one 
}

List<com.hubitat.app.DeviceWrapper> getAllReplicaDevices() {
    return getAllDevices()?.findAll{ getReplicaDeviceId(it) }  // more than one  
}

List<com.hubitat.app.DeviceWrapper> getReplicaDevices(deviceId, componentId = null) {
    List<com.hubitat.app.DeviceWrapper> response = getAllDevices()?.findAll{ getReplicaDeviceId(it) == deviceId } // could be more than one
    if (componentId) {
        response?.clone()?.each{ replicaDevice ->
            Map replica = getReplicaDataJsonValue(replicaDevice, "replica")
            if( replica?.componentId && replica?.componentId != componentId) //componentId was not added until v1.2.06
                response.remove(replicaDevice)
        }
    }
    return response
}

List getAllReplicaDeviceIds() {
    return getAllReplicaDevices()?.collect{ getReplicaDeviceId(it) }?.unique() ?: []
}
// ******** Child and Mirror Device get Functions - End ********

// ******** Volatile Memory Device Cache - Start ********
private String getReplicaDeviceId(def replicaDevice) {
    String deviceId = null
    Map replica = getReplicaDataJsonValue(replicaDevice, "replica")
    if(replica?.replicaId==app.getId()) { // just get MY app devices. could have a problem with mirror devices.
        deviceId = replica?.deviceId
    }
    return deviceId        
}

private void setReplicaDataJsonValue(def replicaDevice, String dataKey, Map dataValue) {
    setReplicaDataValue(replicaDevice, dataKey, JsonOutput.toJson(dataValue))
}

private void setReplicaDataValue(def replicaDevice, String dataKey, String dataValue) {
    getReplicaDataValue(replicaDevice, dataKey, dataValue) // update cache first
    replicaDevice?.updateDataValue(dataKey, dataValue) // STORE IT to device object
}

private Map getReplicaDataJsonValue(def replicaDevice, String dataKey) {
    def response = null
    try {
        def value = getReplicaDataValue(replicaDevice, dataKey)
        if (value) {                
            response = new JsonSlurper().parseText(value)
        }
    } catch (e) {
        logInfo "${app.getLabel()} '$replicaDevice' getReplicaDataJsonValue($dataKey) did not complete."
    }
    return response
}

private String getReplicaDataValue(def replicaDevice, String dataKey, String setDataValue=null) { // setDataValue will directly update the cache without fetching from the object.
    Long appId = app.getId()    
    if(g_mReplicaDeviceCache[appId]==null) {
        clearReplicaDataCache()
    }
    
    def cacheDevice = g_mReplicaDeviceCache[appId]?.get(replicaDevice?.deviceNetworkId)
    if(cacheDevice==null && replicaDevice?.deviceNetworkId) {
        cacheDevice = g_mReplicaDeviceCache[appId][replicaDevice?.deviceNetworkId] = [:]
    }
    
    String dataValue = setDataValue!=null ? null : cacheDevice?.get(dataKey) // this could be a setter, so don't grab cache if dataValue is present
    if(dataValue==null && replicaDevice?.deviceNetworkId) {        
        dataValue = setDataValue ?: replicaDevice?.getDataValue(dataKey) // Use setter value if present or FETCH IT from device object
        if(dataValue) {
            cacheDevice[dataKey] = dataValue
            if(setDataValue!=null)
                logTrace "${app.getLabel()} '${replicaDevice?.getDisplayName()}' cache updated '$dataKey'"
            else
                logDebug "${app.getLabel()} '${replicaDevice?.getDisplayName()}' cached '$dataKey'"
        }
        else {
            logInfo "${app.getLabel()} '${replicaDevice?.getDisplayName()}' cannot find '$dataKey' <b>setting cache to ignore</b>"
            cacheDevice[dataKey] = "ignore"
        }            
    }
    return (dataValue=="ignore" ? null : dataValue) 
}

private void clearReplicaDataCache() { g_mReplicaDeviceCache[app.getId()] = [:] }
private void clearReplicaDataCache(def replicaDevice) {
    Long appId = app.getId()
    if(g_mReplicaDeviceCache[appId] && replicaDevice?.deviceNetworkId ) { 
        g_mReplicaDeviceCache[appId][replicaDevice?.deviceNetworkId] = null 
        g_mReplicaDeviceCache[appId][replicaDevice?.deviceNetworkId] = [:] 
    }
}
private void clearReplicaDataCache(def replicaDevice, String dataKey, Boolean delete=false) {
    if(delete) { replicaDevice?.removeDataValue(dataKey) }
    Long appId = app.getId()
    if(g_mReplicaDeviceCache[appId] && replicaDevice?.deviceNetworkId && dataKey) { 
        if(g_mReplicaDeviceCache[appId]?.get(replicaDevice?.deviceNetworkId)?.get(dataKey)) {
            g_mReplicaDeviceCache[appId].get(replicaDevice?.deviceNetworkId).remove(dataKey)            
        }
    }
}
// ******** Volatile Memory Device Cache - End ********

// ******** Thanks to Dominick Meglio for the code below! ********
Map getDriverList() {
	def params = [
		uri: "http://127.0.0.1:8080/device/drivers",
		headers: [
			Cookie: state.cookie //shouldn't need since we are using this only in the UI after the user logs in
		]
	  ]
	Map result = [items:[]]
	try {
		httpGet(params) { resp ->
			for (driver in resp.data.drivers) {
				result.items.add([id:driver.id.toString(),name:driver.name,namespace:driver.namespace])
			}
		}
	} catch (e) {
		logWarn "Error retrieving installed drivers: ${e}"
	}
	return result
}
// ******** Thanks to Dominick Meglio for the code above! - End ********

void pageMainTestButton() {
    logWarn getAuthToken()    
    return
}
