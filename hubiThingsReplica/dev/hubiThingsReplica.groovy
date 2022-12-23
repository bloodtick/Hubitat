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
*  1.0.00 2022-10-01 First pass.
*  ...    Deleted
*  1.2.00 2022-12-20 Beta release. Namespace change. Requires OAuth 1.2.00+
*  1.2.02 2022-12-22 Hide device selection on create page, Rule alert on main page.
*  1.2.03 2022-12-22 Change timing for OAuth large datasets
*  1.2.04 2022-12-23 Check rules and display red
LINE 30 MAX */ 

public static String version() {  return "1.2.04"  }
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

@Field static final String  sDefaultAppName="HubiThings Replica"
@Field static final Integer iHttpSuccess=200
@Field static final Integer iHttpError=400
@Field static final String  sURI="https://api.smartthings.com"
@Field static final Integer iPageMainRefreshInterval=60*30
@Field static final String  sColorDarkBlue="#1A77C9"
@Field static final String  sColorLightGrey="#DDDDDD"
@Field static final String  sColorDarkGrey="#696969"
@Field static final String  sColorDarkRed="DarkRed"
@Field static final String  sCodeRelease="Beta"

// IN-MEMORY VARIABLES (Cleared on HUB REBOOT or CODE UPDATES)
@Field volatile static Map<Long,Map>   g_mSmartDeviceStatusMap = [:]
@Field volatile static Map<Long,Map>   g_mSmartDeviceListCache = [:]
@Field volatile static Map<Long,Map>   g_mReplicaDeviceCache = [:]
@Field volatile static Map<String,Map> g_mPageConfigureDevice = [:] // don't clear

void clearAllVolatileCache() {
    Long appId = app.getId()
    g_mSmartDeviceStatusMap[appId]=null
    g_mSmartDeviceListCache[appId]=null
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
    page name:"pageCreateDevice"
    page name:"pageCreateDevice2"
    page name:"pageMirrorDevice"
    page name:"pageMirrorDevice2"
    page name:"pageDeleteDevice"
    page name:"pageDeleteDevice2"
    page name:"pageConfigureDevice"
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

public void childSubscriptionListChanged( childApp ) {
    logDebug "${app.getLabel()} executing 'childSubscriptionListChanged($childApp.id)'"
    runIn(10, allSmartDeviceRefresh)
    runIn(5, updateLocationSubscriptionSettings) // not the best place for this. not sure where is the best place.
}

public List childGetOtherSubscribedDeviceIds( childApp ) {
    logDebug "${app.getLabel()} executing 'childGetOtherSubscribedDeviceIds($childApp.id)'"
    List devices = []
    getChildApps()?.each{ ouathApp -> 
        if(ouathApp.getId() != childApp.getId()) {
            devices = devices + ouathApp?.getSmartSubscribedDevices()?.items?.collect{ it?.deviceId }
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
            else if (oauthStatus!="FAILURE" && it?.getAuthStatus()=="PENDING")
                oauthStatus = "PENDING"            
            else if(oauthStatus!="FAILURE" && oauthStatus!="PENDING" && it?.getAuthStatus()=="AUTHORIZED")
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
    
    Map smartDevices = getSmartDevicesMap()
    Integer deviceAuthCount = getAuthorizedDevices()?.size() ?: 0
    
    return dynamicPage(name: "pageMain", install: true,  refreshInterval:iPageMainRefreshInterval) {
        displayHeader()
        state.pageMainLastRefresh = now()
        
        section(menuHeader("${app.getLabel()} Configuration $sHubitatIconStatic $sSamsungIconStatic")) {
            
            input(name: "pageMainShowConfig", type: "bool", title: getFormat("text","$sHubitatIcon Show Configuration"), defaultValue: true, submitOnChange: true)
            paragraph( getFormat("line") )            
            if(pageMainShowConfig) {
                String comments = "This application utilizes the SmartThings Cloud API to create, delete and query devices. <b>You must supply a SmartThings Personal Access Token (PAT) with all permissions to enable functionality</b>. "
                       comments+= "A PAT is valid for 50 years from creation date. Click the ${sSamsungIcon} link below to be directed to the SmartThings website."
                paragraph( getFormat("comments",comments,null,"Gray") )
                
                input(name: "userSmartThingsPAT", type: "password", title: getFormat("hyperlink","$sSamsungIcon SmartThings Personal Access Token:","https://account.smartthings.com/tokens"), description: "SmartThings UUID Token", width: 6, submitOnChange: true, newLineAfter:true)
                paragraph("") 
                
                if(userSmartThingsPAT) {
                    app(name: "oauthChildApps", appName: "HubiThings OAuth", namespace: "replica", title: getFormat("text","$sSamsungIcon Authorize SmartThings Devices (Create OAuth Apps)"), multiple: true)                
                    paragraph( getFormat("line") )
  
                    input(name: "pageMainShowAdvanceConfiguration", type: "bool", title: getFormat("text","$sHubitatIcon Advanced Configuration"), defaultValue: false, submitOnChange: true)                
                    if(pageMainShowAdvanceConfiguration) {
                        def deviceText = (deviceAuthCount<1 ? ": (Select to Authorize Devices to Mirror)" : (deviceAuthCount==1 ?  ": ($deviceAuthCount Device Authorized)" : ": ($deviceAuthCount Devices Authorized)"))
                        href "pageAuthDevice", title: getFormat("text","$sHubitatIcon Authorize Hubitat Devices $deviceText"), description: "Click to show"                
                        paragraph("")
   
                        input(name: "pageMainPageAppLabel", type: "text", title: getFormat("text","$sHubitatIcon Change SmartApp Name:"), width: 6, submitOnChange: true, newLineAfter:true)
                        input(name: "pageMain::changeAppName", type: "button", title: "Change Name", width: 3, style:"width:50%;", newLineAfter:true)                
                    }
                }
            }
        }
            
        if(pageMainShowConfig && pageMainShowAdvanceConfiguration) {  
            section(menuHeader("Application Logging")) {
                input(name: "appLogEventEnable", type: "bool", title: "Enable Event and Status Info logging", required: false, defaultValue: false, submitOnChange: true)
                if(appLogEventEnable) {
                    List smartDevicesSelect = []
                    smartDevices?.items?.sort{ it.label }?.each {    
                        def device = [ "${it.deviceId}" : "${it.label} &ensp; (deviceId: ${it.deviceId})" ]
                        smartDevicesSelect.add(device)   
                    }
                    input(name: "appLogEventEnableDevice", type: "enum", title: getFormat("text","$sSamsungIcon Selective SmartThings Info Logging:"), description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
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
            section(menuHeader("Application Development")) {
                input(name: "pageMain::testButton",    type: "button", width: 2, title: "$sHubitatIcon Test", style:"width:75%;")
                input(name: "pageMain::status",        type: "button", width: 2, title: "$sSamsungIcon Status", style:"width:75%;")
                input(name: "pageMain::description",   type: "button", width: 2, title: "$sSamsungIcon Description", style:"width:75%;")
                input(name: "pageMain::health",        type: "button", width: 2, title: "$sSamsungIcon Health", style:"width:75%;")
            }
        }               
            
        section(menuHeader("HubiThings Device List")){           
            if (smartDevices) {
                String devicesTable = "<table style='width:100%;'>"
                devicesTable += "<tr><th>$sSamsungIcon Device</th><th>$sHubitatIcon Device</th><th style='text-align:center;'>$sHubitatIcon OAuth ID</th><th style='text-align:center;'>$sSamsungIcon Events</th></tr>" 
                smartDevices?.items?.sort{ it.label }?.each { smartDevice -> 
                    List hubitatDevices = getReplicaDevices(smartDevice.deviceId)
                    for (def i = 0; i ==0 || i < hubitatDevices.size(); i++) {
                        def replicaDevice = hubitatDevices[i]?:null
                        String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice?.getId()}"                        
                        String appUrl = "http://${location.hub.getDataValue("localIP")}/installedapp/configure/${smartDevice?.appId}"
                        String noRules = getReplicaDataJsonValue(replicaDevice, "rules")?.components ? "" : "<span style='color:$sColorDarkRed;'> ${sNoStatusIcon}Rules</span>"                     
                        devicesTable += "<tr>"
                        devicesTable += smartDevice?.label   ? "<td>${smartDevice?.label}</td>" : "<td>--</td>"                  
                        devicesTable += replicaDevice        ? "<td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${replicaDevice?.getDisplayName()}$noRules</a></td>" : "<td>--</td>"
                        devicesTable += smartDevice?.oauthId ? "<td style='text-align:center;'><a href='${appUrl}'>${smartDevice?.oauthId}</a></td>" : "<td>--</td>"
                        devicesTable += replicaDevice        ? "<td style='text-align:center;' id='${replicaDevice?.deviceNetworkId}'>${updateSmartDeviceEventsStatus(replicaDevice)}</td>" : "<td style='text-align:center;'>--</td>"
                        devicesTable += "</tr>"
                    }
		        }                
                devicesTable +="</table>"
                paragraph("${devicesTable}")
                paragraph("<span style='color:${sColorDarkRed}' id='socketstatus'></span>")
                
                def html =  """<style>.dot{height:20px; width:20px; background:${sColorDarkBlue}; border-radius:50%; display:inline-block;}</style>"""
                    html += """<style>th,td{border-bottom:3px solid #ddd;}</style>"""                
                    html += """<style>table{ table-layout: fixed;width: 100%;}</style>"""
                    //html += """<style>@media screen and (max-width:800px) { table th:nth-of-type(2),td:nth-of-type(2),th:nth-of-type(5),td:nth-of-type(5) { display: none; } }</style>"""
                    html += """<style>@media screen and (max-width:800px) { table th:nth-of-type(3),td:nth-of-type(3) { display: none; } }</style>"""
                    html += """<script>if(typeof websocket_start === 'undefined'){ window.websocket_start=true; console.log('websocket_start'); var ws = new WebSocket("ws://${location.hub.localIP}:80/eventsocket"); ws.onmessage=function(evt){ var e=JSON.parse(evt.data); if(e.installedAppId=="${app.getId()}") { updatedot(e); }}; ws.onclose=function(){ onclose(); delete websocket_start;};}</script>"""
                    //html += """<script>function updatedot(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='statuscolor' && document.getElementById(dt.deviceNetworkId)){ document.getElementById(dt.deviceNetworkId).style.background = evt.value;}}</script>"""
                    html += """<script>function updatedot(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='smartEvent' && document.getElementById(dt.deviceNetworkId)){ document.getElementById(dt.deviceNetworkId).innerText = evt.value; }}</script>"""
                    html += """<script>function onclose() { console.log("Connection closed"); if(document.getElementById('socketstatus')){ document.getElementById('socketstatus').textContent = "Notice: Websocket closed. Please refresh page to restart.";}}</script>""" 
                paragraph( html )
            }            
            input(name: "pageMain::refresh",  type: "button", width: 2, title: "$sSamsungIcon Refresh", style:"width:75%;")
    	}
        
        section(menuHeader("HubiThings Device Creation and Control")){	
            href "pageCreateDevice", title: "Create HubiThings Device", description: "Click to show"            
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
            if(deviceAuthCount>0) href "pageMirrorDevice", title: "Mirror Hubitat Device (Advanced)", description: "Click to show"
        }
        
        if(pageMainShowConfig || appDebugEnable || appTraceEnable) {
            runIn(1800, updatePageMain)
        } else {
            unschedule('updatePageMain')
        }
       
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

void appRemoveSettings() {
    app.removeSetting("pageConfigureDeviceReplicaDevice")
    app.removeSetting("pageCreateDeviceSmartDevice")
    app.removeSetting("pageCreateDeviceType")    
    app.removeSetting("pageMirrorDeviceHubitatDevice")
    app.removeSetting("pageMirrorDeviceSmartDevice")
    app.removeSetting("pageDeleteDeviceHubitatDevice")
    app.removeSetting("smartAttribute")
    app.removeSetting("smartCommand")
    app.removeSetting("hubitatAttribute")
    app.removeSetting("hubitatCommand")
    app.removeSetting("smartCommand")
}

def pageAuthDevice(){    
    Integer deviceAuthCount = getAuthorizedDevices()?.size() ?: 0    
    return dynamicPage(name: "pageAuthDevice", uninstall: false) {
        displayHeader()

        section(menuHeader("Authorize Hubitat Devices $sHubitatIconStatic $sSamsungIconStatic")) {
          input(name: "userAuthorizedDevices", type: "capability.*", title: "Hubitat Devices:", description: "Choose a Hubitat devices", multiple: true, submitOnChange: true)
            if(deviceAuthCount>0) {
                paragraph( getFormat("line") )
                href "pageMirrorDevice", title: "Mirror Hubitat Device (Advanced)", description: "Click to show"
            }    
        }
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

def pageCreateDevice(){    
    Map smartDevices = getSmartDevicesMap()
    def smartDeviceId = pageCreateDeviceSmartDevice
    
    List smartDevicesSelect = []
    smartDevices?.items?.sort{ it.label }?.each {    
        Map device = [ "${it.deviceId}" : "${it.label} &ensp; (deviceId: ${it.deviceId})" ]
        if(pageCreateDeviceShowAllDevices || !getReplicaDevices(it.deviceId))
            smartDevicesSelect.add(device)   
    }   
     
    List hubitatDeviceTypes = []
    getDeviceHandlers()?.items?.sort{ a,b -> b?.namespace <=> a?.namespace ?: a?.name <=> b?.name }?.each {    
        Map handler = [ "${it.id}" : "${it.name} &ensp; (namespace: ${it.namespace})" ]
        hubitatDeviceTypes.add(handler)   
    }
    
    String smartStats = getSmartDeviceStats(smartDeviceId)
    app.updateSetting( "pageCreateDeviceLabel", smartDevices?.items?.find{it.deviceId == smartDeviceId}?.label ?: "" )

    return dynamicPage(name: "pageCreateDevice", uninstall: false) {
        displayHeader()        

        section(menuHeader("Create HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) {
 
            input(name: "pageCreateDeviceSmartDevice", type: "enum", title: "$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
            paragraph( smartStats )
            input(name: "pageCreateDeviceShowAllDevices", type: "bool", title: "Show All Authorized SmartThings Devices", defaultValue: false, submitOnChange: true, width: 3, newLineAfter:true)
            paragraph( getFormat("line") )
            
            input(name: "pageCreateDeviceType", type: "enum", title: "$sHubitatIcon Create Hubitat Device Type:", description: "Choose a Hubitat device type", options: hubitatDeviceTypes, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            input(name: "pageCreateDeviceLabel", type: "text", title: "$sHubitatIcon Create Hubitat Device Label:", submitOnChange: false, width: 6)
            paragraph( getFormat("line") )
            
            if (pageCreateDeviceSmartDevice && pageCreateDeviceType) {
                href "pageCreateDevice2", title: "➢ Click to create $sHubitatIcon Hubitat device", description: "Device will be created based on the parameters above"            
                paragraph( getFormat("line") )
            }
            
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
        }

        replicaDevicesSection()
    }
}

def replicaDevicesSection(){
    
    String childDeviceList = "<span><table style='width:100%;'>"
    childDeviceList += "<tr><th>$sHubitatIcon Hubitat Device</th><th>$sHubitatIcon Hubitat Type</th><th style='text-align:center;'>$sHubitatIcon Configuration</th></tr>"
    getAllReplicaDevices()?.sort{ it.getDisplayName() }.each { replicaDevice ->
        Boolean isChildDevice = (getChildDevice( replicaDevice?.deviceNetworkId ) != null)
        //example: "http://192.168.1.160/device/edit/1430"
        String deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice.getId()}"
        childDeviceList += "<tr><td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${replicaDevice.getDisplayName()}</a></td>"
        childDeviceList += "<td>${replicaDevice.typeName}</td><td style='text-align:center;'>${isChildDevice?'Child':'Mirror'}</td></tr>"
    }
    childDeviceList +="</table>"
    
    if (getAllReplicaDevices().size){        
        section(menuHeader("HubiThings Devices")) {
            paragraph( childDeviceList )
            paragraph("<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>")            
        }
    }
}

def pageCreateDevice2() {
    
    return dynamicPage(name: "pageCreateDevice2", uninstall: false) {
        displayHeader()
        
        String label = pageCreateDeviceLabel
        String response = ""
        if (getChildDevices()?.find{it.getDisplayName() == label}){
            response = "There is already a device labled '${label}'. Go back and change the label name."
        }
        else {
            response = !pageCreateDeviceSmartDevice || !pageCreateDeviceType ? "Device label name or type not specified. Go back and enter the device information" :  createChildDevices()
        }
        section (menuHeader("Create HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) {
            paragraph( response )
        }
    }
}

def createChildDevices(){    
    Map smartDevices = getSmartDevicesMap()    
    Map deviceType = getDeviceHandlers()?.items?.find{ it?.id==pageCreateDeviceType }
    
    String deviceNetworkId = "${UUID.randomUUID().toString()}"    
    String name  = (smartDevices?.items?.find{it?.deviceId == pageCreateDeviceSmartDevice}?.name)
    String deviceId = pageCreateDeviceSmartDevice
    String label = pageCreateDeviceLabel
    String response = ""
    try {
        def replicaDevice = addChildDevice(deviceType.namespace, deviceType.name, deviceNetworkId, null, [name: name, label: label, completedSetup: true])
        // the deviceId makes this a hubiThing
        // Needed for mirror function to prevent two SmartApps talking to same device.
        Map replica = [ deviceId:deviceId, replicaId:(app.getId()), type:'child']
        setReplicaDataJsonValue(replicaDevice, "replica", replica)
        if(replicaDevice?.hasCommand('configure')) replicaDevice.configure()
        replicaDeviceRefresh(replicaDevice)

        logInfo "${app.getLabel()} created device '${replicaDevice.getDisplayName()}' with network id: ${replicaDevice.deviceNetworkId}"  
        response  = "Child device '${replicaDevice.getDisplayName()}' has been created\n\n"
        response += getHubitatDeviceStats(replicaDevice)
        
        app.updateSetting( "pageConfigureDeviceReplicaDevice", [type:"enum", value: replicaDevice.deviceNetworkId] )
        
    } catch (e) {
        response = "A '${deviceType?.name}' named '${label}' could not be created. Ensure you have the correct Hubitat Driver Handler."
        logWarn "Error creating device: ${e}"
        logInfo pageCreateDeviceType
        logInfo getDeviceHandlers()
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

String getSmartDeviceStats(smartDeviceId) {
    String smartStats =  ""
    if(smartDeviceId) {
        Map smartDevices = getSmartDevicesMap()
        
        List smartCapabilities = []
        smartDevices?.items?.find{it.deviceId == smartDeviceId}?.components.each { components ->
            components?.capabilities?.each { capability ->
                smartCapabilities.add(capability.id)
            }
        }        
        
        smartStats += "Device Type: ${smartDevices?.items?.find{it.deviceId == smartDeviceId}?.deviceTypeName ?: (smartDevices?.items?.find{it.deviceId == smartDeviceId}?.name ?: "UNKNOWN")}\n"
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
    def smartDeviceId = pageMirrorDeviceSmartDevice
    
    List smartDevicesSelect = []
    smartDevices?.items?.sort{ it.label }?.each {    
        Map device = [ "${it.deviceId}" : "${it.label} &ensp; (deviceId: ${it.deviceId})" ]
        smartDevicesSelect.add(device)   
    }
    
    List hubitatDevicesSelect = []
    getMirrorDevices(pageMirrorDeviceShowAllDevices?false:true)?.sort{ it.getDisplayName() }?.each {
        Map device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
        hubitatDevicesSelect.add(device)   
    }

    def hubitatDevice = getDevice( pageMirrorDeviceHubitatDevice )
    String hubitatStats =  getHubitatDeviceStats(hubitatDevice)
    String smartStats = getSmartDeviceStats(smartDeviceId)

    return dynamicPage(name: "pageMirrorDevice", uninstall: false) {
        displayHeader()        

        section(menuHeader("Mirror HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) {
 
            input(name: "pageMirrorDeviceSmartDevice", type: "enum", title: "$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
            paragraph( smartStats )
            paragraph( getFormat("line") )
            
            input(name: "pageMirrorDeviceHubitatDevice", type: "enum", title: "$sHubitatIcon Select Hubitat Device:", description: "Choose a Hubitat device", options: hubitatDevicesSelect, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            paragraph( hubitatStats )
            input(name: "pageMirrorDeviceShowAllDevices", type: "bool", title: "Show All Authorized Hubitat Devices", defaultValue: false, submitOnChange: true, width: 3, newLineAfter:true)
            paragraph( getFormat("line") )
            
            if (pageMirrorDeviceSmartDevice && pageMirrorDeviceHubitatDevice) {
                href "pageMirrorDevice2", title: "➢ Click to mirror $sSamsungIcon SmartThings and $sHubitatIcon Hubitat devices", description: "Devices will be mirrored based on the parameters above"            
                paragraph( getFormat("line") )
            }
            
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
        }
        replicaDevicesSection()
    }
}

def pageMirrorDevice2() {    
    Map smartDevices = getSmartDevicesMap()
    def deviceId = pageMirrorDeviceSmartDevice
    
    return dynamicPage(name: "pageMirrorDevice2", uninstall: false) {
        displayHeader()
        
        section (menuHeader("Mirror HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) {
            def replicaDevice = getDevice(pageMirrorDeviceHubitatDevice)
            String smartLabel = smartDevices?.items?.find{ it.deviceId == deviceId }?.label 
            
            Map replica = [ deviceId:deviceId, replicaId:(app.getId()), type:( getChildDevice( replicaDevice?.deviceNetworkId )!=null ? 'child' : 'mirror')]
            setReplicaDataJsonValue(replicaDevice, "replica", replica)
            replicaDeviceRefresh(replicaDevice)
            
            logInfo "${app.getLabel()} mirrored a device SmartThings '$smartLabel' with Hubitat '${replicaDevice.getDisplayName()}'"                    
            String response  = "$sSamsungIcon SmartThings '$smartLabel' and $sHubitatIcon Hubitat '${replicaDevice.getDisplayName()}' devices have been mirrored\n\n"
            response += getHubitatDeviceStats(replicaDevice)
            paragraph( response )        
            app.updateSetting( "pageConfigureDeviceReplicaDevice", [type:"enum", value: replicaDevice.deviceNetworkId] )            
        }
    }
}

def pageDeleteDevice(){    
    List hubitatDevicesSelect = []
    getAllReplicaDevices()?.sort{ it.getDisplayName() }?.each {
        Map device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
        hubitatDevicesSelect.add(device)   
    }
    
    return dynamicPage(name: "pageDeleteDevice", uninstall: false) {
        displayHeader()

        section(menuHeader("Delete HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) {
           input(name: "pageDeleteDeviceHubitatDevice", type: "enum",  title: "Delete HubiThings Device:", description: "Choose a HubiThings device", multiple: false, options: hubitatDevicesSelect, submitOnChange: true)
           def replicaDevice = getDevice( pageDeleteDeviceHubitatDevice )
           if(replicaDevice) {
               Boolean isChild = getChildDevice( replicaDevice?.deviceNetworkId )
               String title = (isChild ? "➢ Click to delete $sHubitatIcon Hubitat device" : "➢ Click to detach $sSamsungIcon SmartThings from $sHubitatIcon Hubitat device")
               href "pageDeleteDevice2", title: title, description: "Device '$replicaDevice' will ${isChild ? 'be deleted' : 'not be deleted'}" 
           }
        }        
        replicaDevicesSection()
    }
}

def pageDeleteDevice2() {
    
    def replicaDevice = getDevice( pageDeleteDeviceHubitatDevice )    
    String response = "Error attempting to delete '${replicaDevice?.getDisplayName()}'."
    if(replicaDevice) {
        try {
            Boolean isChild = getChildDevice( replicaDevice?.deviceNetworkId )            
            app.removeSetting("pageDeleteDeviceHubitatDevice")
            
            if(isChild) { 
                unsubscribe(replicaDevice)
                deleteChildDevice(replicaDevice?.deviceNetworkId)
                logInfo "${app.getLabel()} deleted '${replicaDevice?.getDisplayName()}' with network id: ${replicaDevice?.deviceNetworkId}"
                response = "The device '${replicaDevice?.getDisplayName()}' was deleted"
            }
            else {           
                unsubscribe(replicaDevice)
                clearReplicaDataCache(replicaDevice, "capabilities", true)
                clearReplicaDataCache(replicaDevice, "description", true)
                clearReplicaDataCache(replicaDevice, "health", true)
                clearReplicaDataCache(replicaDevice, "replica", true)
                clearReplicaDataCache(replicaDevice, "rules", true)
                clearReplicaDataCache(replicaDevice, "status", true)
                clearReplicaDataCache(replicaDevice, "subscription", true)
                logInfo "${app.getLabel()} detached '${replicaDevice?.getDisplayName()}' with network id: ${replicaDevice?.deviceNetworkId}"
                response = "The device '${replicaDevice?.getDisplayName()}' was detached from SmartThings"
            }            
        } catch (e) {
            logWarn "Error deleting $replicaDevice: ${e}"
        }
    }
    
    return dynamicPage(name: "pageDeleteDevice2", uninstall: false) {
        displayHeader()        

        section (menuHeader("Delete HubiThings Device")) {
            paragraph( response )
        }        
        replicaDevicesSection()
    }
}

void updateRuleList(action, type) {
    def trigger = g_mPageConfigureDevice?.hubitatAttribute
    def command = g_mPageConfigureDevice?.smartCommand
    if(type!='hubitatTrigger') {
        trigger = g_mPageConfigureDevice?.smartAttribute
        command = g_mPageConfigureDevice?.hubitatCommand
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
        if(newRule?.trigger?.properties?.value?.enum) newRule.trigger.properties.value.remove('enum') //junk
        if(muteTriggerRuleInfo) newRule['mute'] = true
        if(disableStatusUpdate) newRule['disableStatus'] = true

        if(action=='store' && (!replicaDeviceRules?.components?.find{ it?.type==type && it?.trigger?.label?.trim()==triggerKey } || allowDuplicateAttribute)) {
            replicaDeviceRules.components.add(newRule)
        }
    }
    //logInfo replicaDeviceRules
    setReplicaDataJsonValue(replicaDevice, "rules", replicaDeviceRules.sort{ a, b -> b.key <=> a.key })
    if(type=='hubitatTrigger') replicaDeviceSubscribe(replicaDevice)
}

void replicaDevicesRuleSection(){
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
    Map replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules" )
    
    String replicaDeviceRulesList = "<span><table style='width:100%;'>"
    replicaDeviceRulesList += "<tr><th>Trigger</th><th>Action</th></tr>"
    replicaDeviceRules?.components?.sort{ a,b -> a?.type <=> b?.type ?: a?.trigger?.label <=> b?.trigger?.label ?: a?.command?.label <=> b?.command?.label }?.each { rule ->    
        String muteflag = rule?.mute ? "$sLogMuteIcon" : ""
        String disableStatusFlag = rule?.disableStatus ? "$sNoStatusIcon" : ""
        String trigger = "${rule?.type=='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.trigger?.label}"
        String command = "${rule?.type!='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.command?.label} $muteflag $disableStatusFlag"
        trigger = checkTrigger(replicaDevice, rule?.type, rule?.trigger?.label) ? trigger : "<span style='color:$sColorDarkRed;'>$trigger</span>"
        command = checkTrigger(replicaDevice, rule?.type, rule?.trigger?.label) ? command : "<span style='color:$sColorDarkRed;'>$command</span>"
        replicaDeviceRulesList += "<tr><td>$trigger</td><td>$command</td></tr>"
    }
    replicaDeviceRulesList +="</table>"
    
    if (replicaDeviceRules?.components?.size){        
        section(menuHeader("Active Rules ➢ $replicaDevice")) {    
            paragraph( replicaDeviceRulesList )
            paragraph("<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>")
        }
    }
    
    //section(menuHeader("Replica Handler Development")) {
        app.removeSetting('pageConfigureDeviceStoreCapabilityText')
        //input(name: "pageConfigureDeviceStoreCapabilityText", type: "textarea", rows: 10, width:50, title: "Replica Capability Loader:", description: "Load Capability JSON Here", submitOnChange: true, newLineAfter:true)
        //input(name: "pageConfigureDevice::storeCapability",     type: "button", title: "Store", width: 2, style:"width:75%;")
    //}
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
    getAllReplicaDevices()?.sort{ it.getDisplayName() }.each {    
        Map device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
        replicaDevicesSelect.add(device)   
    }
   
    return dynamicPage(name: "pageConfigureDevice", uninstall: false) {
        displayHeader()
       
        section(menuHeader("Configure HubiThings Rules $sHubitatIconStatic $sSamsungIconStatic")) {
            
            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
            String deviceTitle = "Select HubiThings Device:"
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
            if(replicaDevice?.hasCommand('configure')) input(name: "pageConfigureDevice::configDeviceRules",  type: "button", title: "Configure", width: 2, style:"width:75%;")
            paragraph( getFormat("line") )
            
            Map hubitatAttributeOptions = getHubitatAttributeOptions(replicaDevice)                      
            Map smartCommandOptions = getSmartCommandOptions(replicaDevice)
            
            input(name: "hubitatAttribute", type: "enum", title: "$sHubitatIcon If Hubitat Attribute <b>TRIGGER</b> changes:", description: "Choose a Hubitat Attribute", options: hubitatAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "smartCommand", type: "enum", title: "$sSamsungIcon Then <b>ACTION</b> SmartThings Command:", description: "Choose a SmartThings Command", options: smartCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            input(name: "pageConfigureDevice::hubitatAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::hubitatAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")

            if(pageConfigureDeviceShowDetail) {
                paragraph( hubitatAttribute ? "$sHubitatIcon $hubitatAttribute : ${JsonOutput.toJson(hubitatAttributeOptions?.get(hubitatAttribute))}" : "$sHubitatIcon No Selection" )
                paragraph( smartCommand ? "$sSamsungIcon $smartCommand : ${JsonOutput.toJson(smartCommandOptions?.get(smartCommand))}" : "$sSamsungIcon No Selection" )
            }
            paragraph( getFormat("line") )
            
            Map smartAttributeOptions = getSmartAttributeOptions(replicaDevice)         
            Map hubitatCommandOptions = getHubitatCommandOptions(replicaDevice)
            
            input(name: "smartAttribute", type: "enum", title: "$sSamsungIcon If SmartThings Attribute <b>TRIGGER</b> changes:", description: "Choose a SmartThings Attribute", options: smartAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "hubitatCommand", type: "enum", title: "$sHubitatIcon Then <b>ACTION</b> Hubitat Command${pageConfigureDeviceAllowActionAttribute?'/Attribute':''}:", description: "Choose a Hubitat Command", options: hubitatCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
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
            g_mPageConfigureDevice['hubitatAttribute'] = hubitatAttributeOptions?.get(hubitatAttribute) ?: null
            g_mPageConfigureDevice['smartAttribute']   = smartAttributeOptions?.get(smartAttribute) ?: null
            g_mPageConfigureDevice['smartCommand']     = smartCommandOptions?.get(smartCommand) ?: null
            g_mPageConfigureDevice['hubitatCommand']   = hubitatCommandOptions?.get(hubitatCommand) ?: null
            
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
            //hubitatAttributeOptions[label].value = "*"
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
            //attributeJson['label'] = "attribute: ${attributeJson?.name}.[${attributeJson?.dataType.toLowerCase()}]"
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
            if(smartCommandOptions[label]) { // this device has conflicting commands from different capablities.
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
                smartAttributeOptions[label] = schema.clone()
                smartAttributeOptions[label].label = label
                //smartAttributeOptions[label].value = "*"
                schema?.properties?.value?.enum?.each{ enumValue ->
                    label = "attribute: ${attribute}.${enumValue}"
                    smartAttributeOptions[label] = schema.clone()
                    smartAttributeOptions[label].label = label
                    smartAttributeOptions[label].value = enumValue
                    smartAttributeOptions[label].dataType = "ENUM" //match hubitat
                }
            }
            else {
                def type = schema?.properties?.value?.type
                //schema["label"] = "attribute: ${attribute}.[${type}]"
                schema["label"] = "attribute: ${attribute}.*"
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

void appButtonHandler(String btn) {
    logDebug "${app.getLabel()} executing 'appButtonHandler($btn)'"
   
    if(btn.contains("::")) { 
        List items = btn.tokenize("::")
        if(items && items.size() > 1 && items[1]) {
            String k = (String)items[0]
            String v = (String)items[1]
            logTrace "Button [$k] [$v] pressed"
            switch(k) {                
                case "pageMain":                    
                    switch(v) {
                        case "refresh":
                            allSmartDeviceRefresh()
                            break
                        case "description":
                            allSmartDeviceDescription()
                            break                        
                        case "status":
                            allSmartDeviceStatus()
                            break
                        case "health":
                            allSmartDeviceHealth()
                            break                     
                        case "testButton":
                            testButton()
                            break
                        case "changeAppName":
                            if(pageMainPageAppLabel && pageMainPageAppLabel!=app.getLabel()) {
                                logInfo "Changing Hubitat SmartApp from ${app.getLabel()} to $pageMainPageAppLabel"
                                app.updateLabel( pageMainPageAppLabel )
                            } 
                            break
                    }                            
                    break
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
                        case "storeCapability":
                            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
                            String value =  pageConfigureDeviceStoreCapabilityText?.replaceAll('“','"')?.replaceAll('”','"')
                            Map capabilities = value ? new JsonSlurper().parseText(value) : [components:[]]
                            logInfo capabilities
                            setReplicaDataJsonValue(replicaDevice, "capabilities", capabilities)
                            break
                    }
                    break                    
                default:
                    logInfo "Not supported"
            }
        }
    }    
}

void allSmartDeviceRefresh() {
    // brute force grabbing all devices in my OAuths
    Map smartDevices = [items:[]]
    getChildApps()?.each{ 
        smartDevices.items.addAll( it.getSmartSubscribedDevices()?.items )
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
    //event.properties.each { logInfo "$it.key -> $it.value" }
    deviceTriggerHandler(event?.getDevice(), event?.name, event?.value, event?.unit, event?.getJsonData())
}

void deviceTriggerHandler(def replicaDevice, Map event) {
    if(event?.name == "configure" || event?.name == "refresh") {
        clearReplicaDataCache(replicaDevice)
        replicaDeviceRefresh(replicaDevice)        
    }
    else {    
        deviceTriggerHandler(replicaDevice, event?.name, event?.value, event?.unit, event?.data, event?.now)
    }     
}
           
void deviceTriggerHandler(def replicaDevice, String eventName, def eventValue, String eventUnit, Map eventJsonData, Long eventPostTime=null) {
    eventPostTime = eventPostTime ?: now()
    logDebug "${app.getLabel()} executing 'deviceTriggerHandler()' replicaDevice:'${replicaDevice.getDisplayName()}' name:'$eventName' value:'$eventValue' unit:'$eventUnit', data:'$eventJsonData'"
    String deviceId = getReplicaDeviceId(replicaDevice)

    Map replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules")
    replicaDeviceRules?.components?.findAll{ it?.type == "hubitatTrigger" }?.each { rule ->            
        Map trigger = rule?.trigger
        Map command = rule?.command
 
        if(eventName==trigger?.name) {
            //logInfo "trigger:'$trigger' eventValue:'$eventValue'"
            // simple enum case
            if(trigger?.type=="command" && trigger?.parameters==null) {
                // check if this was from ST and should not be sent back
                if(trigger?.type=="command" || !deviceTriggerHandlerCache(replicaDevice, eventName, eventValue)) { 
                    setSmartDeviceCommand(deviceId, command?.capability, command?.name)
                    if(!rule?.mute) logInfo "${app.getLabel()} sending '${replicaDevice?.getDisplayName()}' ● trigger:${trigger?.type=="command"?"command":eventName}:${trigger?.type=="command"?eventName:eventValue} ➣ command:${command?.name} ● delay:${now() - eventPostTime}ms"
                }
            }
            // non-enum case https://developer-preview.smartthings.com/docs/devices/capabilities/capabilities
            else if((trigger?.type=="command" && trigger?.parameters) || !trigger?.value) {
                String evtName = eventName
                String evtValue = eventValue.toString()            
                logTrace "evtName:${evtName} evtValue:${evtValue}"
                // check if this was from ST and should not be sent back
                if(trigger?.type=="command" || !deviceTriggerHandlerCache(replicaDevice, evtName, evtValue)) {
                    String type = command?.arguments?.getAt(0)?.schema?.type?.toLowerCase()
                    def arguments = null
               
                    switch(type) {
                        case 'integer': // A whole number. Limits can be defined to constrain the range of possible values.
                            arguments = [ (evtValue?.isNumber() ? (evtValue?.isFloat() ? (int)(Math.round(evtValue?.toFloat())) : evtValue?.toInteger()) : null) ]
                            break
                        case 'number':  // A number that can have fractional values. Limits can be defined to constrain the range of possible values.
                            arguments = [ evtValue?.toFloat() ]
                            break
                        case 'boolean': // Either true or false
                            arguments = [ evtValue?.toBoolean() ]
                            break
                        case 'object':  // A map of name value pairs, where the values can be of different types.
                            Map map = new JsonSlurper().parseText(eventValue)
                            arguments = map
                            break
                        case 'array':   // A list of values of a single type.
                            List list = new JsonSlurper().parseText(eventValue)
                            arguments = list
                            break
                        default:
                            arguments = [ evtValue ]
                            break
                    }           
                    // add any additonal arguments. (these should be evaluated correct type since they are not a event 'value' ^^ which is defined as string)            
                    arguments = arguments?.plus( trigger?.parameters?.findResults{ parameter -> parameter?.data ? eventJsonData?.get(parameter?.data) : null })
                    
                    setSmartDeviceCommand(deviceId, command?.capability, command?.name, arguments)
                    if(!rule?.mute) logInfo "${app.getLabel()} sending '${replicaDevice?.getDisplayName()}' $type ● trigger:${evtName} ➣ command:${command?.name}:${arguments?.toString()} ● delay:${now() - eventPostTime}ms"
                }
            }
        }
    }            
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
                    
                    List args = [value.value]
                    String method = command?.name
                    if(hasCommand(replicaDevice, method) && !(type=="status" && rule?.disableStatus)) {
                        replicaDevice."$method"(*args)  
                        if(!rule?.mute) logInfo "${app.getLabel()} received '${replicaDevice?.getDisplayName()}' $type ○ trigger:$attribute ➢ command:${command?.name}:${value?.value} ${(eventPostTime ? "● delay:${now() - eventPostTime}ms" : "")}"
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
            
        String eventCount = (getReplicaDeviceEventsCache(replicaDevice)?.eventCount ?: 0).toString()
        value = (healthState=='offline' ? healthState : eventCount)
        if(state.pageMainLastRefresh && (state.pageMainLastRefresh + (iPageMainRefreshInterval*1000)) > now()) { //only send if someone is watching 
            sendEvent(name:'smartEvent', value:value, descriptionText: JsonOutput.toJson([ deviceNetworkId:(replicaDevice?.deviceNetworkId), debug: appLogEnable ]))
        }
    }
    return value
}

Map smartStatusHandler(replicaDevice, Map statusEvent, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'smartStatusHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]
    
    if(appLogEventEnable && statusEvent && (!appLogEventEnableDevice || appLogEventEnableDevice==statusEvent?.deviceId)) {
        logInfo "Status: ${JsonOutput.toJson(statusEvent)}"
    }    
    setReplicaDataJsonValue(replicaDevice, "status", statusEvent)
    statusEvent?.components?.main?.each { capability, attributes ->
        response.statusCode = smartTriggerHandler(replicaDevice, [ "$capability":attributes ], "status", eventPostTime).statusCode
    }
    
    if( updateSmartDeviceEventsStatus(replicaDevice) == 'offline' ) { getSmartDeviceHealth( getReplicaDeviceId(replicaDevice) ) } 
    return [statusCode:response.statusCode]
}

Map smartEventHandler(replicaDevice, Map deviceEvent, Long eventPostTime=null){
    logDebug "${app.getLabel()} executing 'smartEventHandler()' replicaDevice:'${replicaDevice.getDisplayName()}'"
    Map response = [statusCode:iHttpSuccess]
    
    if(appLogEventEnable && deviceEvent && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceEvent?.deviceId)) {
        logInfo "Event: ${JsonOutput.toJson(deviceEvent)}"
    }    
    //setReplicaDataJsonValue(replicaDevice, "event", deviceEvent)    
    try {
        // events do not carry units. so get it from status. yeah smartthings is great!
        String unit = getReplicaDataJsonValue(replicaDevice, "status")?.components?.get(deviceEvent.componentId)?.get(deviceEvent.capability)?.get(deviceEvent.attribute)?.unit
        // status    {"switchLevel":             {"level":                  {"value":30,                "unit":"%",   "timestamp":"2022-09-07T21:16:59.576Z" }}}
        Map event = [ (deviceEvent.capability): [ (deviceEvent.attribute): [ value:(deviceEvent.value), unit:(deviceEvent?.unit ?: unit), timestamp: getTimestampSmartFormat() ]]]
        response.statusCode = smartTriggerHandler(replicaDevice, event, "event", eventPostTime).statusCode
        
        if( updateSmartDeviceEventsStatus(replicaDevice) == 'offline' ) { getSmartDeviceHealth( getReplicaDeviceId(replicaDevice) ) }
    } catch (e) {
        logWarn "${app.getLabel()} smartEventHandler error: $e : $deviceEvent"
    }
    return [statusCode:response.statusCode]
}

Map smartHealthHandler(replicaDevice, Map healthEvent, Long eventPostTime=null){
    logDebug "${app.getLabel()} executing 'smartHealthHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]

    setReplicaDataJsonValue(replicaDevice, "health", healthEvent)
    try {
        //{"deviceId":"2c80c1d7-d05e-430a-9ddb-1630ee457afb","state":"ONLINE","lastUpdatedDate":"2022-09-07T16:47:06.859Z"}
        // status    {"switchLevel":{"level":       {"value":30,                             "unit":"","timestamp":"2022-09-07T21:16:59.576Z" }}}
        def event = [ healthCheck: [ healthStatus: [ value:(healthEvent?.state?.toLowerCase()), timestamp: healthEvent?.lastUpdatedDate, reason:(healthEvent?.reason ?: 'poll') ]]]
        logTrace JsonOutput.toJson(event)
        response.statusCode = smartTriggerHandler(replicaDevice, event, "health", eventPostTime).statusCode
        
        updateSmartDeviceEventsStatus(replicaDevice)            
    } catch (e) {
        logWarn "${app.getLabel()} smartHealthHandler error: $e : $healthEvent"
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
        logWarn "${app.getLabel()} smartDescriptionHandler error: $e : $descriptionEvent"
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
        logWarn "${app.getLabel()} smartCapabilityHandler error: $e : $capabilityEvent"
    }    
    return [statusCode:response.statusCode]
}

String getTimestampSmartFormat() {
    return ((new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")).toString())
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
            smartHealthHandler(replicaDevice, healthEvent)
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
	    logWarn "${app.getLabel()} asyncHttpGet error: $e"
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
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartHealthHandler(replicaDevice, health)  }
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
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartStatusHandler(replicaDevice, status) }
                status = null
                break      
            default:
                logWarn "${app.getLabel()} asyncHttpGetCallback ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        logWarn("${app.getLabel()} asyncHttpGetCallback ${data?.method} status:${resp.status} reason:${resp.errorMessage}")
    }
}

Map setSmartDeviceCommand(deviceId, capability, command, arguments = []) {
    logDebug "${app.getLabel()} executing 'setSmartDeviceCommand()'"
    Map response = [statusCode:iHttpError]

    Map commands = [ commands: [[ component: "main", capability: capability, command: command, arguments: arguments ]] ]
    Map data = [
        uri: sURI,
        path: "/devices/${deviceId}/commands",
        body: JsonOutput.toJson(commands),
        method: "setSmartDeviceCommand",
        authToken: getAuthToken()        
    ]
    if(appLogEventEnable && (!appLogEventEnableDevice || appLogEventEnableDevice==deviceId)) {
        logInfo "Device:${getReplicaDevices(deviceId)?.each{ it?.label }} commands:<b>${JsonOutput.toJson(commands)}</b>"
    }
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
		headers: [ Authorization: "Bearer ${getAuthToken()}" ]
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

Map oauthEventHandler(Map eventData, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'oauthEventHandler()'"
    Map response = [statusCode:iHttpSuccess]

    eventData?.events?.each { event ->
        switch(event?.eventType) {
            case 'DEVICE_EVENT':
                Map device = getSmartDeviceEventsCache(event?.deviceEvent?.deviceId)
                if(device?.eventCount!=null) device.eventCount += 1
                getReplicaDevices(event?.deviceEvent?.deviceId)?.each{ replicaDevice ->
                    response.statusCode = smartEventHandler(replicaDevice, event?.deviceEvent, eventPostTime).statusCode
                }
                break
            case 'DEVICE_HEALTH_EVENT':
                String deviceId = event?.deviceHealthEvent?.deviceId
                Map healthEvent = [deviceId:deviceId, state:(event?.deviceHealthEvent?.status), lastUpdatedDate:(event?.eventTime), reason:'event']
                getReplicaDevices(deviceId)?.each{ replicaDevice ->
                    response.statusCode = smartHealthHandler(replicaDevice, healthEvent, eventPostTime).statusCode
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
            default:
                logInfo "${app.getLabel()} 'oauthEventHandler()' did not handle $event"
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

// thanks to DCMeglio (Hubitat Package Manager) for a lot of formatting hints
def getFormat(type, myText="", myHyperlink="", myColor=sColorDarkBlue){   
    if(type == "line")      return "<hr style='background-color:$myColor; height: 1px; border: 0;'>"
	if(type == "title")     return "<h2 style='color:$myColor;font-weight: bold'>${myText}</h2>"
    if(type == "text")      return "<span style='color:$myColor;font-weight: bold'>${myText}</span>"
    if(type == "hyperlink") return "<a href='${myHyperlink}' target='_blank' rel='noopener noreferrer' style='color:$myColor;font-weight:bold'>${myText}</a>"
    if(type == "comments")  return "<div style='color:$myColor;font-weight:small;font-size:14px;'>${myText}</div>"
}

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
		    paragraph "<div style='color:{sColorDarkBlue};text-align:center;font-weight:small;font-size:11px;'>${app.getLabel()}<br><br><a href='${paypal()}' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This application took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
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
        if( !devices?.find{ it.deviceNetworkId == userAuthorizedDevice.deviceNetworkId } ) devices.add(userAuthorizedDevice)
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

List<com.hubitat.app.DeviceWrapper> getMirrorDevices(hideInUse=true) {    
    List<ChildDeviceWrapper> devices = getAuthorizedDevices()   
    getAuthorizedDevices()?.each{ userAuthorizedDevice ->
        if( getReplicaDeviceId(userAuthorizedDevice, hideInUse)!=null ) {
            devices.remove(userAuthorizedDevice)
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

List<com.hubitat.app.DeviceWrapper> getReplicaDevices(deviceId) {
    return getAllDevices()?.findAll{ getReplicaDeviceId(it) == deviceId } // could be more than one
}

List getAllReplicaDeviceIds() {
    return getAllReplicaDevices()?.collect{ getReplicaDeviceId(it) }?.unique()
}
// ******** Child and Mirror Device get Functions - End ********

// ******** Volatile Memory Device Cache - Start ********
private String getReplicaDeviceId(def replicaDevice, Boolean overrideAppId=false) {
    String deviceId = null
    Map replica = getReplicaDataJsonValue(replicaDevice, "replica")
    if(overrideAppId || replica?.replicaId==app.getId()) { // this is for mirror devices. just get MY app devices.
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

void testButton() {
    logWarn getAuthToken()    
    return
}
