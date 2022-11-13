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
*  1.0.0  2022-10-01 First pass.
*  ...    Deleted
*  1.0.19 2022-11-08 Beginning Mode Replica support (need PAT right now), command delay measurement in logs, moved SmartThings command to async post
*  1.0.20 2022-11-09 Added r:hubs:*, Moved all subscription(s) to async post, additional logic for smarter subscriptions handling...more to come
*  1.0.21 2022-11-10 Bug fix on device creation: capabilityVersion, introduction of 'replica' data type for mirror function
*  1.0.22 2022-11-10 Initial Check-in with Mirror support. Its buggy. I know.
*  1.0.23 2022-11-11 Bug fix on device deletion crashing the UI.
*  1.0.24 2022-11-11 Bug fixes on Mirror. Ignore devices that are not configured.
*  1.0.25 2022-11-13 Bug fixes. Commented out temp code. First install checks to prevent bad install (OAUTH and Done).
*/

public static String version() {  return "v1.0.25"  }
public static String copyright() {"&copy; 2022 ${author()}"}
public static String author() { return "Bloodtick Jones" }
public static String paypal() { return "https://www.paypal.com/donate/?business=QHNE3ZVSRYWDA&no_recurring=1&currency_code=USD" }

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
@Field static final String  sURI="https://api.smartthings.com"
@Field static final String  sColorDarkBlue="#1A77C9"
@Field static final String  sColorLightGrey="#DDDDDD"
@Field static final String  sColorDarkGrey="#696969"
@Field static final String  sColorDarkRed="DarkRed"
@Field static final String  sCodeRelease="Alpha"

// IN-MEMORY VARIABLES (Cleared on HUB REBOOT or CODE UPDATES)
@Field volatile static Map<String,Map>    g_mSmartDeviceListCache = [:]
@Field volatile static Map<String,Map>    g_mInstalledAppConfig = [:]
@Field volatile static Map<String,String> g_mPageConfigureDevice = [:]
@Field volatile static Map<String,String> g_mReplicaDeviceCache = [:]

void clearAllVolatileCache() {
    def appId = app.getId()
    g_mSmartDeviceListCache[appId]=null
    g_mInstalledAppConfig[appId]=null
    g_mPageConfigureDevice[appId]=null
    g_mReplicaDeviceCache[appId]=null
}

definition(
    name: "HubiThings Replica",
    namespace: "hubitat",
    author: "bloodtick",
    description: "Hubitat Application to manage SmartThings SmartApp Webhooks",
    category: "Convenience",
    importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/hubiThingsReplica.groovy",
    iconUrl: "",
    iconX2Url: "",
    oauth: [displayName: "HubiThings Replica", displayLink: ""],
    singleInstance: false
){}

preferences {
    page name:"mainPage"
    page name:"pageAuthDevice"
    page name:"pageCreateDevice"
    page name:"pageCreateDevice2"
    page name:"pageMirrorDevice"
    page name:"pageMirrorDevice2"
    page name:"pageDeleteDevice"
    page name:"pageDeleteDevice2"
    page name:"pageConfigureDevice"
}

mappings { 
    path("/webhook") { action: [ POST: "webhookPost", GET: "webhookGet"] }
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
    unsubscribe()
    unschedule()
}

String getLocalUri() {
    return getFullLocalApiServerUrl() + "/webhook?access_token=${state.accessToken}"
}

String getCloudUri() {
    return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/webhook?access_token=${state.accessToken}"
}

Map getSmartDevicesMap() {
    def appId = app.getId()
    if (g_mSmartDeviceListCache[appId]==null) {
        if(state?.installedAppId) {
            getSmartDeviceList()
            pauseExecution(2000)
        }
        //logInfo JsonOutput.toJson(g_mSmartDeviceListCache[appId])
    }
    return g_mSmartDeviceListCache[appId]
}
void setSmartDevicesMap(Map smartDevices) {
    def appId = app.getId()
    g_mSmartDeviceListCache[appId] = smartDevices
    clearReplicaDataCache() // lets clear the cache of any stale devices
    logInfo "${app.getLabel()} caching SmartThings device list"
}

Map getInstallSmartDevicesMap() {
    def appId = app.getId()
    if (g_mInstalledAppConfig[appId]==null) {
        Map deviceMap = [:]
        if(state?.installedAppConfig) {            
            state.installedAppConfig?.each{ id, items ->
                items.each { device ->            
                    if ( device.valueType == "DEVICE" ) {
                        if (deviceMap[device.deviceConfig.deviceId]) {
                            logDebug "${app.getLabel()} SmartThings Automation SmartApp selected device:'${device.deviceConfig.deviceId}' more than once"
                            deviceMap[device.deviceConfig.deviceId].id.add(id)                        
                        } else {                    
                            deviceMap[device.deviceConfig.deviceId] = [ id:[id], valueType:device.valueType, componentId:device.deviceConfig.componentId, eventCount:0, eventCache:[:] ]
                        }            
                    }
                }
            }
            //state.remove('install')
        } /*
        else if(state?.install) {
            logWarn "${app.getLabel()} requests you update your installed SmartThings Automation SmartApp selected devices"
            state?.install?.each{ deviceId, deviceItems ->
                deviceItems['eventCount'] = 0
                deviceItems['eventCache'] = [:]                
                deviceMap[deviceId] = deviceItems
            } 
        } */
        g_mInstalledAppConfig[appId]=deviceMap?.sort()
        logInfo "${app.getLabel()} caching SmartThings installed devices"
        //logInfo JsonOutput.toJson(g_mInstalledAppConfig[appId])
    }
    return g_mInstalledAppConfig[appId]
}

def mainPage(){
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
                paragraph("Problem with OAUTH installation! Please delete $sHubitatIcon ${app.getLabel()} SmartApp and authorize OAUTH in Apps Code source code and reinstall")

            }
        }
    }
    state.isInstalled = true // TEMP: remove someday. Just for Alpha
        
    //app.removeSetting("pageConfigureDeviceHubitatDevice")
    //state.remove("rule")
    
    Map smartDevices = getSmartDevicesMap()
    Map installDevices = getInstallSmartDevicesMap()
    Integer deviceAuthCount = getAuthorizedDevices()?.size() ?: 0
    
    if(!pageMainPageAppLabel || !mainPageShowConfig) { app.updateSetting( "pageMainPageAppLabel", app.getLabel()) }
    
    return dynamicPage(name: "mainPage", install: true,  refreshInterval: 0){
        displayHeader()
        
        section(menuHeader("${app.getLabel()} Configuration $sHubitatIconStatic $sSamsungIconStatic")) {
            
            input(name: "mainPageShowConfig", type: "bool", title: getFormat("text","$sHubitatIcon Show Configuration"), defaultValue: false, submitOnChange: true)
            paragraph( getFormat("line") )
            
            if(mainPageShowConfig) {
                
			    input(name: "mainPageAllowCloudAccess", type: "bool", title: getFormat("text","$sHubitatIcon Enable Hubitat REST API Endpoint for SmartThings Developer Workspace SmartApp"), defaultValue: false, submitOnChange: true)  
                if(mainPageAllowCloudAccess) {
                    paragraph("<ul><strong>External</strong>: ${getFormat("hyperlink", getCloudUri(), getCloudUri())}</ul>")                
               }                       
            
                paragraph( getFormat("line") )
                // required for authToken refresh
                input(name: "clientIdUUID", type: "text", title: getFormat("hyperlink","$sSamsungIcon SmartApp Client ID from SmartThings Developer Workspace:","https://smartthings.developer.samsung.com/workspace"), width: 6, submitOnChange: true, newLineAfter:true)
                input(name: "clientSecretUUID", type: "text", title: getFormat("hyperlink","$sSamsungIcon SmartApp Client Secret from SmartThings Developer Workspace:","https://smartthings.developer.samsung.com/workspace"), width: 6, submitOnChange: true, newLineAfter:true)
                input(name: "clientSecretPAT", type: "text", title: getFormat("hyperlink","$sSamsungIcon SmartThings Personal Access Token (Mode Changes Only):","https://account.smartthings.com/tokens"), width: 6, submitOnChange: true, newLineAfter:true)
                if(state?.authTokenDate) {
                    paragraph( getFormat("text","$sSamsungIcon Token Expiration Date: ${state.authTokenDate}") )
                    input(name: "mainPage::refreshToken", type: "button", title: "Refresh Token", width: 3, style:"width:50%;", newLineAfter:true)
                }
                paragraph( getFormat("line") )
               
                def deviceText = (deviceAuthCount<1 ? ": (Select to Authorize Devices to Mirror)" : (deviceAuthCount==1 ?  ": ($deviceAuthCount Device Authorized)" : ": ($deviceAuthCount Devices Authorized)"))
                href "pageAuthDevice", title: getFormat("text","$sHubitatIcon Authorize Hubitat Devices $deviceText"), description: "Click to show"
                
                input(name: "pageMainPageAppLabel", type: "text", title: getFormat("text","$sHubitatIcon Change SmartApp Name:"), width: 6, submitOnChange: true, newLineAfter:true)
                input(name: "mainPage::changeAppName", type: "button", title: "Change Name", width: 3, style:"width:50%;", newLineAfter:true)
            }
            else {
                if(state?.user=="bloodtick") { input( name: "mainPage::testButton", type: "button", width: 2, title: "Test Button" ) }
                
                def devicesTable = "<table style='width:100%;'>"
                //devicesTable += "<tr><th>$sSamsungIcon Device</th><th>$sSamsungIcon Value</th><th>$sHubitatIcon Device</th><th>$sHubitatIcon Value</th></tr>"
                devicesTable += "<tr><td>$sSamsungIcon Device Count</td><td style='text-align:center;'><div><span id='subscriptionCount'></span>#</div></td>"
                devicesTable += "    <td>$sSamsungIcon Hub Status</td><td style='text-align:center;'><div><span id='hubStatus' style='background:${sColorDarkGrey};' class='dot'></span></div></td></tr>"
                
                devicesTable += "<tr><td>$sSamsungIcon Device Subscription Count</td><td style='text-align:center;'><div><span id='subscriptionCount'></span>#</div></td>"
                devicesTable += "    <td>$sSamsungIcon Device Subscription Status</td><td style='text-align:center;'><div><span id='subscriptionStatus' style='background:${sColorDarkGrey};' class='dot'></span></div></td></tr>"
                
                devicesTable += "<tr><td>$sSamsungIcon Health Subscription Count</td><td style='text-align:center;'><div><span id='healthCount'></span>#</div></td>"
                devicesTable += "    <td>$sSamsungIcon Health Subscription Status</td><td style='text-align:center;'><div><span id='healthStatus' style='background:${sColorDarkGrey};' class='dot'></span></div></td></tr>"
                 
                devicesTable += "<tr><td>$sSamsungIcon Current Mode</td><td style='text-align:center;'><div><span id='modeCount'></span>word</div></td>"
                devicesTable += "    <td>$sSamsungIcon Mode Subscription Status</td><td style='text-align:center;'><div><span id='modeStatus' style='background:${sColorDarkGrey};' class='dot'></span></div></td></tr>"
                devicesTable +="</table>"
                //paragraph("${devicesTable}")
            }                
        }
            
        section(menuHeader("HubiThings Device List")){            
            
            if (smartDevices && installDevices) {               
                def devicesTable = "<table style='width:100%;'>"
                devicesTable += "<tr><th>$sSamsungIcon Device</th><th>$sSamsungIcon Type</th><th>$sHubitatIcon Device</th><th style='text-align:center;'>$sSamsungIcon Events</th></tr>"
                smartDevices?.items?.sort{ it.label }?.each { smartDevice -> 
                    def hubitatDevices = getReplicaDevices(smartDevice.deviceId)
                    for (def i = 0; i ==0 || i < hubitatDevices.size(); i++) {
                        def deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${hubitatDevices[i]?.getId()}"                            
                        //def deviceColor = hubitatDevices[i]?.getDataValue("statuscolor") ?: sColorLightGrey
                            
                        devicesTable += "<tr>"
                        devicesTable += "<td>${smartDevice?.label}</td>"                  
                        devicesTable += "<td>${installDevices?.get(smartDevice.deviceId)?.id?.join(', ')}</td>"
                        devicesTable += hubitatDevices[i] ? "<td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${hubitatDevices[i]?.getDisplayName()}</a></td>" : "<td></td>"
                        //devicesTable += "<td style='text-align:center;'><div><span id='healthStatus' style='background:${sColorDarkGrey};' class='dot'></span></div></td>"
                        devicesTable += "<td style='text-align:center;' id='${hubitatDevices[i]?.deviceNetworkId}'>${getSmartDeviceEventsStatus(hubitatDevices[i])}</td>"
                        devicesTable += "</tr>"
                    }
		        }                
                devicesTable +="</table>"
                paragraph("${devicesTable}")
                paragraph("<span style='color:${sColorDarkRed}' id='socketstatus'></span>")
                
                def html =  """<style>.dot{height:20px; width:20px; background:${sColorDarkBlue}; border-radius:50%; display:inline-block;}</style>"""
                    html += """<style>th,td{border-bottom:3px solid #ddd;}</style>"""                
                    html += """<style>table{ table-layout: fixed;width: 100%;}</style>"""
                    html += """<style>@media screen and (max-width:800px) { table th:nth-of-type(2),td:nth-of-type(2) { display: none; } }</style>"""
                    html += """<script>if(typeof websocket_start === 'undefined'){ window.websocket_start=true; console.log('websocket_start'); var ws = new WebSocket("ws://${location.hub.localIP}:80/eventsocket"); ws.onmessage=function(evt){ var e=JSON.parse(evt.data); if(e.installedAppId=="${app.getId()}") { updatedot(e); }}; ws.onclose=function(){ onclose(); delete websocket_start;};}</script>"""
                    //html += """<script>function updatedot(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='statuscolor' && document.getElementById(dt.deviceNetworkId)){ document.getElementById(dt.deviceNetworkId).style.background = evt.value;}}</script>"""
                    html += """<script>function updatedot(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='smartEvent' && document.getElementById(dt.deviceNetworkId)){ document.getElementById(dt.deviceNetworkId).innerText = evt.value; }}</script>"""
                    html += """<script>function onclose() { console.log("Connection closed"); if(document.getElementById('socketstatus')){ document.getElementById('socketstatus').textContent = "Notice: Websocket closed. Please refresh page to restart.";}}</script>""" 
                
                paragraph( html )
            }
            
            input(name: "mainPage::list",          type: "button", width: 2, title: "$sSamsungIcon List", style:"width:75%;")
            input(name: "mainPage::status",        type: "button", width: 2, title: "$sSamsungIcon Status", style:"width:75%;")
            input(name: "mainPage::description",   type: "button", width: 2, title: "$sSamsungIcon Description", style:"width:75%;")
            input(name: "mainPage::health",        type: "button", width: 2, title: "$sSamsungIcon Health", style:"width:75%;")
            input(name: "mainPage::subscriptions", type: "button", width: 2, title: "$sSamsungIcon Subscriptions", style:"width:75%;")    
    	}
        
        section(menuHeader("HubiThings Device Creation and Control")){	
            href "pageCreateDevice", title: "Create HubiThings Device", description: "Click to show"
            if(deviceAuthCount>0) href "pageMirrorDevice", title: "Mirror HubiThings Device", description: "Click to show"
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
        }        
        
        section(menuHeader("Application Logging")) {
            input(name: "appLogEventEnable", type: "bool", title: "Enable Event and Status Info logging", required: false, defaultValue: false, submitOnChange: true)
            input(name: "appLogEnable", type: "bool", title: "Enable debug logging", required: false, defaultValue: false, submitOnChange: true)
            input(name: "appTraceEnable", type: "bool", title: "Enable trace logging", required: false, defaultValue: false, submitOnChange: true)
        }
        if(mainPageShowConfig || appLogEnable || appTraceEnable) {
            runIn(30*60, updateMainPage)
        } else {
            unschedule('updateMainPage')
        }
        
        displayFooter()
    }    
}

void updateMainPage() {
    logInfo "${app.getLabel()} disabling debug and trace logs"
    app.updateSetting("mainPageShowConfig", false)
    app.updateSetting("appLogEnable", false)
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
                href "pageMirrorDevice", title: "Mirror HubiThings Device", description: "Click to show"
            }    
        }
    }
}

def pageCreateDevice(){    
    Map smartDevices = getSmartDevicesMap()
    def smartDeviceId = pageCreateDeviceSmartDevice
    
    List smartDevicesSelect = []
    smartDevices?.items?.sort{ it.label }?.each {    
        def device = [ "${it.deviceId}" : "${it.label} &ensp; (deviceId: ${it.deviceId})" ]
        smartDevicesSelect.add(device)   
    }

    String smartStats = getSmartDeviceStats(smartDeviceId)
        
    def hubitatDeviceTypes = ["Virtual Switch", "Virtual Contact Sensor", "Virtual Motion Sensor", "Virtual Temperature Sensor", "Virtual Humidity Sensor", "Virtual Presence", "Virtual Shade", "Generic Component Dimmer", "Generic Component Button Controller"]
    app.updateSetting( "pageCreateDeviceLabel", smartDevices?.items?.find{it.deviceId == smartDeviceId}?.label ?: "" )

    return dynamicPage(name: "pageCreateDevice", uninstall: false) {
        displayHeader()        

        section(menuHeader("Create HubiThings Device $sHubitatIconStatic $sSamsungIconStatic")) {
 
            input(name: "pageCreateDeviceSmartDevice", type: "enum", title: "$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
            paragraph( smartStats )
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
    
    def childDeviceList = "<span><table style='width:100%;'>"
    childDeviceList += "<tr><th>$sHubitatIcon Hubitat Device</th><th>$sHubitatIcon Hubitat Type</th><th style='text-align:center;'>$sHubitatIcon Configuration</th></tr>"
    getAllReplicaDevices()?.sort{ it.getDisplayName() }.each { replicaDevice ->
        Boolean isChildDevice = (getChildDevice( replicaDevice?.deviceNetworkId ) != null)
        //example: "http://192.168.1.160/device/edit/1430"
        def deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${replicaDevice.getId()}"
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
        
        def label  = pageCreateDeviceLabel

        def response = ""
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
    
    def deviceNetworkId = "${UUID.randomUUID().toString()}"
    def nameSpace = "hubitat"
    def label = pageCreateDeviceLabel
    def name  = (smartDevices?.items?.find{it.deviceId == pageCreateDeviceSmartDevice}?.name)
    def deviceId = pageCreateDeviceSmartDevice
    
    def response = "A '${pageCreateDeviceType}' named '${label}' could not be created. Ensure you have the correct Hubitat Drivers Code."
    try {
        def replicaDevice = addChildDevice(nameSpace, pageCreateDeviceType, deviceNetworkId, null, [name: name, label: label, completedSetup: true])
        // the deviceId makes this a hubiThing
        // Needed for mirror function to prevent two SmartApps talking to same device.
        def replica = [ deviceId:deviceId, replicaId:(app.getId()), type:'child']
        setReplicaDataJsonValue(replicaDevice, "replica", replica)        
        replicaDeviceRefresh(replicaDevice)

        logInfo "${app.getLabel()} created device '${replicaDevice.getDisplayName()}' with network id: ${replicaDevice.deviceNetworkId}"  
        response  = "Child device '${replicaDevice.getDisplayName()}' has been created\n\n"
        response += getHubitatDeviceStats(replicaDevice)
        
        app.updateSetting( "pageConfigureDeviceReplicaDevice", [type:"enum", value: replicaDevice.deviceNetworkId] )
        
    } catch (e) {
        logWarn "Error creating device: ${e}"        
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
        //smartStats += "Commands: ${hubitatDevice?.getSupportedCommands()?.sort()?.join(', ')}\n"
        smartStats += "Capabilities: ${smartCapabilities?.sort()?.join(', ')}"
        //smartStats += "Attributes: ${smartAttributes?.sort()?.join(', ')}"
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
        // first unsubscribe and then get ST status. This will prevent any reflection back to ST
        unsubscribe(replicaDevice)
        getSmartDeviceStatus(deviceId)
        pauseExecution(500)
        replicaDeviceSubscribe(replicaDevice)
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
        List replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules")
        List attributes = replicaDeviceRules?.findAll{ it.type == "hubitatTrigger" }?.collect{ rule -> rule?.trigger.values()?.getAt(0)?.name }?.unique()
        unsubscribe(replicaDevice)
        attributes?.each{ attribute ->
            logInfo "${app.getLabel()} '$replicaDevice' subscribed to $attribute"
            subscribe(replicaDevice, "${attribute}", deviceTriggerHandler)
        }
    }
}

def pageMirrorDevice(){    
    Map smartDevices = getSmartDevicesMap()
    def smartDeviceId = pageMirrorDeviceSmartDevice
    
    List smartDevicesSelect = []
    smartDevices?.items?.sort{ it.label }?.each {    
        def device = [ "${it.deviceId}" : "${it.label} &ensp; (deviceId: ${it.deviceId})" ]
        smartDevicesSelect.add(device)   
    }
    
    List hubitatDevicesSelect = []
    getMirrorDevices(pageMirrorDeviceShowAllDevices?false:true)?.sort{ it.getDisplayName() }?.each {
        def device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
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
            
            def replica = [ deviceId:deviceId, replicaId:(app.getId()), type:( getChildDevice( replicaDevice?.deviceNetworkId )!=null ? 'child' : 'mirror')]
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
    
    def hubitatDevicesSelect = []
    getAllReplicaDevices()?.sort{ it.getDisplayName() }?.each {
        def device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
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
    def triggerKey = trigger?.keySet()?.getAt(0)
    def commandKey = command?.keySet()?.getAt(0)
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)    
    logDebug "${app.getLabel()} executing 'updateRuleList()' hubitatDevice:'${replicaDevice}' trigger:'${triggerKey}' command:'${commandKey}' action:'${action}'"
    //quick checks. weirdness on the null I just didn't want to figure out.
    if(triggerKey==null || commandKey==null || triggerKey?.toString()=='null'  || commandKey?.toString()=='null') return

    List replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules") ?: []
    def allowDuplicateAttribute = pageConfigureDeviceAllowDuplicateAttribute
    def muteTriggerRuleInfo = pageConfigureDeviceMuteTriggerRuleInfo
    app.updateSetting("pageConfigureDeviceAllowDuplicateAttribute", false)
    app.updateSetting("pageConfigureDeviceMuteTriggerRuleInfo", false)
  
    if(action=='delete') {
        replicaDeviceRules?.removeAll{ it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey && it?.command?.keySet()?.getAt(0)==commandKey }
       
    }
    else if(triggerKey && commandKey && !replicaDeviceRules?.find{ it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey && it?.command?.keySet()?.getAt(0)==commandKey }) {
        def newRule = [ trigger:trigger, command:command, type:type]
        if(muteTriggerRuleInfo) newRule['mute'] = true

        if(action=='replace'){
            replicaDeviceRules = replicaDeviceRules?.collect{
                return  (it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey) ? newRule : it
            }
        }
        
        if(action=='store' && (!replicaDeviceRules?.find{ it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey } || allowDuplicateAttribute)) {
            replicaDeviceRules.add(newRule)
        }
    }
  
    setReplicaDataJsonValue(replicaDevice, "rules", replicaDeviceRules)
    if(type=='hubitatTrigger') replicaDeviceSubscribe(replicaDevice)
}

void replicaDevicesRuleSection(){
    def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
    List replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules" )
    
    def replicaDeviceRulesList = "<span><table style='width:100%;'>"
    replicaDeviceRulesList += "<tr><th>Trigger</th><th>Action</th></tr>"
    replicaDeviceRules?.sort{ a,b -> a?.type <=> b?.type ?: a?.trigger?.keySet()?.getAt(0) <=> b?.trigger?.keySet()?.getAt(0) ?: a?.command?.keySet()?.getAt(0) <=> b?.command?.keySet()?.getAt(0) }?.each { rule ->
        def muteflag = rule?.mute ? " [muted]" : ""
        def trigger = "${rule?.type=='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.trigger?.keySet()?.getAt(0)}"
        def command = "${rule?.type!='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.command?.keySet()?.getAt(0)}$muteflag"
        replicaDeviceRulesList += "<tr><td>$trigger</td><td>$command</td></tr>"
    }
    replicaDeviceRulesList +="</table>"
    
    if (replicaDeviceRules?.size){        
        section(menuHeader("Active Rules ➢ $replicaDevice")) {    
            paragraph( replicaDeviceRulesList )
            paragraph("<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;}</style>")
        }
    }
}

def pageConfigureDevice() {
    
    def replicaDevicesSelect = []
    getAllReplicaDevices()?.sort{ it.getDisplayName() }.each {    
        def device = [ "${it.deviceNetworkId}" : "${it.getDisplayName()} &ensp; (deviceNetworkId: ${it.deviceNetworkId})" ]
        replicaDevicesSelect.add(device)   
    }
    
    return dynamicPage(name: "pageConfigureDevice", uninstall: false) {
        displayHeader()
       
        section(menuHeader("Configure HubiThings Rules $sHubitatIconStatic $sSamsungIconStatic")) {
            
            input(name: "pageConfigureDeviceReplicaDevice", type: "enum", title: "HubiThings Device:", description: "Choose a HubiThings device", options: replicaDevicesSelect, multiple: false, submitOnChange: true, width: 8, newLineAfter:true)
            def replicaDevice = getDevice(pageConfigureDeviceReplicaDevice)
           
            if(pageConfigureDeviceShowDetail && replicaDevice) {
                def hubitatStats =  getHubitatDeviceStats(replicaDevice)
                paragraph( hubitatStats )              
            }
            input(name: "pageConfigureDevice::refreshDevice",  type: "button", title: "Refresh", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::clearDeviceRules",  type: "button", title: "Clear Rules", width: 2, style:"width:75%;") 
            paragraph( getFormat("line") )
            
            def hubitatAttributeOptions = getHubitatAttributeOptions(replicaDevice)                      
            def smartCommandOptions = getSmartCommandOptions(replicaDevice)
            
            input(name: "hubitatAttribute", type: "enum", title: "$sHubitatIcon If Hubitat Attribute <b>TRIGGER</b> changes:", description: "Choose a Hubitat Attribute", options: hubitatAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "smartCommand", type: "enum", title: "$sSamsungIcon Then <b>ACTION</b> SmartThings Command:", description: "Choose a SmartThings Command", options: smartCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            //input( name: "pageConfigureDevice::hubitatAttributeReplace",  type: "button", title: "Replace Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::hubitatAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::hubitatAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")

            if(pageConfigureDeviceShowDetail) {
                paragraph( hubitatAttribute ? "$sHubitatIcon $hubitatAttribute : ${JsonOutput.toJson(hubitatAttributeOptions?.find{ key,value -> key==hubitatAttribute }?.value)}" : "$sHubitatIcon No Selection" )
                paragraph( smartCommand ? "$sSamsungIcon $smartCommand : ${JsonOutput.toJson(smartCommandOptions?.find{ key,value -> key==smartCommand }?.value)}" : "$sSamsungIcon No Selection" )
            }
            paragraph( getFormat("line") )
            
            def smartAttributeOptions = getSmartAttributeOptions(replicaDevice)         
            def hubitatCommandOptions = getHubitatCommandOptions(replicaDevice)
            
            input(name: "smartAttribute", type: "enum", title: "$sSamsungIcon If SmartThings Attribute <b>TRIGGER</b> changes:", description: "Choose a SmartThings Attribute", options: smartAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "hubitatCommand", type: "enum", title: "$sHubitatIcon Then <b>ACTION</b> Hubitat Command${pageConfigureDeviceAllowActionAttribute?'/Attribute':''}:", description: "Choose a Hubitat Command", options: hubitatCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            //input( name: "pageConfigureDevice::smartAttributeReplace",  type: "button", title: "Replace Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::smartAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::smartAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")
            
            if(pageConfigureDeviceShowDetail) {
                paragraph( smartAttribute ? "$sSamsungIcon $smartAttribute : ${JsonOutput.toJson(smartAttributeOptions?.find{ key,value -> key==smartAttribute }?.value)}" : "$sSamsungIcon No Selection" )
                paragraph( hubitatCommand ? "$sHubitatIcon $hubitatCommand : ${JsonOutput.toJson(hubitatCommandOptions?.find{ key,value -> key==hubitatCommand }?.value)}" : "$sHubitatIcon No Selection" )
            }
            paragraph( getFormat("line") )     
            
            input(name: "pageConfigureDeviceAllowDuplicateAttribute", type: "bool", title: "Allow duplicate Attribute <b>TRIGGER</b>", defaultValue: false, submitOnChange: true, width: 3)
            input(name: "pageConfigureDeviceMuteTriggerRuleInfo", type: "bool", title: "Mute <b>TRIGGER</b> rule Info output", defaultValue: false, submitOnChange: true, width: 3)
            //input(name: "pageConfigureDeviceAllowActionAttribute", type: "bool", title: "Allow <b>ACTION</b> to update Hubitat Attributes", defaultValue: false, submitOnChange: true, width: 3)
            app.updateSetting("pageConfigureDeviceAllowActionAttribute", false)
            input(name: "pageConfigureDeviceShowDetail", type: "bool", title: "Show detail for attributes and commands", defaultValue: false, submitOnChange: true, width: 3, newLineAfter:true)
            
            // gather these all up so when user presses store - it uses this structure.
            g_mPageConfigureDevice['hubitatAttribute'] = ["$hubitatAttribute": hubitatAttributeOptions?.get(hubitatAttribute)] ?: null
            g_mPageConfigureDevice['smartAttribute']   = ["$smartAttribute": smartAttributeOptions?.get(smartAttribute)] ?: null
            g_mPageConfigureDevice['smartCommand']     = ["$smartCommand": smartCommandOptions?.get(smartCommand)] ?: null
            g_mPageConfigureDevice['hubitatCommand']   = ["$hubitatCommand": hubitatCommandOptions?.get(hubitatCommand)] ?: null
        }
        
        replicaDevicesRuleSection()
    }
}

Map getHubitatCommandOptions(replicaDevice) {    
            
    Map hubitatCommandOptions = [:]
    replicaDevice?.getSupportedCommands()?.each{ command ->
        def commandJson = new JsonSlurper().parseText(JsonOutput.toJson(command)) //could not figure out how to convert command object to json. this works.
        commandJson.remove('id')
        commandJson.remove('version')
        def parameterText = "("
        commandJson?.parameters?.eachWithIndex{ parameter, index ->
            def parameterName = parameter?.name ? "${parameter?.name?.uncapitalize()}" : "${parameter?.type?.toLowerCase()}*"
            parameterText += (index ? ", $parameterName" : "$parameterName")
        }
        parameterText +=")"
        commandJson['label'] = "command: ${command?.name}$parameterText"
        hubitatCommandOptions[commandJson.label] = commandJson 
    }
    if(pageConfigureDeviceAllowActionAttribute) {
        hubitatCommandOptions += getHubitatAttributeOptions(replicaDevice)
    }
    return hubitatCommandOptions
}

Map getHubitatAttributeOptions(replicaDevice) {

    Map hubitatAttributeOptions = [:]
    replicaDevice?.getSupportedAttributes()?.each{ attribute ->
        def attributeJson = new JsonSlurper().parseText(JsonOutput.toJson(attribute))
        attributeJson.remove('possibleValueJson')
        attributeJson.remove('possibleValues')
        attributeJson.remove('id')
        attributeJson.remove('version')
        attributeJson.remove('deviceTypeId')
        if(attributeJson?.dataType=="ENUM") {
            def label = "attribute: ${attributeJson?.name}.*"
            hubitatAttributeOptions[label] = attributeJson.clone()
            hubitatAttributeOptions[label].label = label
            //hubitatAttributeOptions[label].value = "*"            
            attributeJson?.values?.each{ enumValue ->
                label = "attribute: ${attributeJson?.name}.${enumValue}"
                hubitatAttributeOptions[label] = attributeJson.clone()
                hubitatAttributeOptions[label].label = label
                hubitatAttributeOptions[label].value = enumValue
            }
        }
        else {
            attributeJson['label'] = "attribute: ${attributeJson?.name}.[${attributeJson?.dataType.toLowerCase()}]"
            hubitatAttributeOptions[attributeJson.label] = attributeJson 
        }
    }

    return hubitatAttributeOptions
}

Map getSmartCommandOptions(replicaDevice) {
            
    Map smartCommandOptions = [:]
    def capabilities = getReplicaDataJsonValue(replicaDevice, "capabilities")
    capabilities?.components?.each{ capability -> 
        capability?.commands?.each{ command, value ->
            def parameterText = "("
            value?.arguments?.eachWithIndex{ parameter, index ->
                def parameterName = parameter?.optional ? "${parameter?.name?.uncapitalize()}" : "${parameter?.name?.uncapitalize()}*"
                parameterText += (index ? ", $parameterName" : "$parameterName")
            }
            parameterText +=")"
            value["capability"] = capability.id
            value["label"] = "command: ${command}$parameterText"
            smartCommandOptions[value.label] = value
        } 
    }
    return smartCommandOptions
}

Map getSmartAttributeOptions(replicaDevice) {

    Map smartAttributeOptions = [:]
    def capabilities = getReplicaDataJsonValue(replicaDevice, "capabilities")
    capabilities?.components?.each{ capability ->
        capability?.attributes?.each{ attribute, value -> Map schema = value?.schema ?: [:]
            schema["capability"] = capability.id
            schema["attribute"] = attribute
            schema?.remove('type')
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
                }
            }
            else {
                def type = schema?.properties?.value?.type
                schema["label"] = "attribute: ${attribute}.[${type}]"
                smartAttributeOptions[schema.label] = schema
            }            
        }
    }
    // not sure why SmartThings treats health different. But everything reports healthStatus. So gonna make it look the same to the user configuration page. 'fancy'.
    if(smartAttributeOptions.size()) {
        smartAttributeOptions["attribute: healthStatus.*"] = new JsonSlurper().parseText("""{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.* "}""")
        smartAttributeOptions["attribute: healthStatus.offline"] = new JsonSlurper().parseText("""{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","value":"offline","attribute":"healthStatus","label":"attribute: healthStatus.offline "}""")
        smartAttributeOptions["attribute: healthStatus.online"] =  new JsonSlurper().parseText("""{"schema":{"properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","value":"online","attribute":"healthStatus","label":"attribute: healthStatus.online "}""")
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
                case "mainPage":                    
                    switch(v) {
                        case "list":
                            allSmartDeviceList()
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
                        case "subscriptions":
                            allSmartDeviceSubscriptions()
                            break                        
                        case "testButton":
                            testButton()
                            break
                        case "refreshToken":
                            allSmartTokenRefresh()                       
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
                        case "hubitatAttributeReplace":
                            updateRuleList('replace','hubitatTrigger')
                            break
                        case "hubitatAttributeStore":
                            updateRuleList('store','hubitatTrigger')
                            break
                        case "hubitatAttributeDelete":
                            updateRuleList('delete','hubitatTrigger')
                            break
                        case "smartAttributeReplace":
                            updateRuleList('replace','smartTrigger')
                            break
                        case "smartAttributeStore":
                            updateRuleList('store','smartTrigger')
                            break
                        case "smartAttributeDelete":
                            updateRuleList('delete','smartTrigger')
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
                default:
                    logInfo "Not supported"
            }
        }
    }    
}

void deviceTriggerHandler(event) {
    Long eventPostTime =  now()
    logDebug "${app.getLabel()} executing 'deviceTriggerHandler()' displayName:'${event?.getDisplayName()}' name:'${event?.name}' value:'${event?.value}' unit:'${event?.unit}'"
    //event.properties.each { logInfo "$it.key -> $it.value" }
    def replicaDevice = event?.getDevice()
    String deviceId = getReplicaDeviceId(replicaDevice)

    List replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules")
    replicaDeviceRules?.findAll{ it.type == "hubitatTrigger" }?.each { rule ->            
        Map trigger = rule?.trigger.values()?.getAt(0)
        Map command = rule?.command.values()?.getAt(0)          
           
        // simple enum case
        if(event.name==trigger?.name && event.value==trigger?.value) {
            // check if this was from ST and should not be sent back
            if(!deviceTriggerHandlerCache(replicaDevice, event.name, event.value)) { 
                setSmartDeviceCommand(deviceId, command?.capability, command?.name)
                if(!rule?.mute) logInfo "${app.getLabel()} sending '${replicaDevice?.getDisplayName()}' ● trigger:${event.name}:${event.value} ➣ command:${command?.name} ● delay:${now() - eventPostTime}ms"
            }
        }
        // non-enum case https://developer-preview.smartthings.com/docs/devices/capabilities/capabilities
        else if(event.name==trigger?.name && !trigger?.value) {
            // check if this was from ST and should not be sent back
            if(!deviceTriggerHandlerCache(replicaDevice, event.name, event.value)) {
                String type = command?.arguments?.getAt(0)?.schema?.type?.toLowerCase()                
               
                switch(type) {
                    case 'integer': // A whole number. Limits can be defined to constrain the range of possible values.
                        def value = event?.value.isNumber() ? (event?.value.isFloat() ? (int)(Math.round(event?.value.toFloat())) : event?.value.toInteger()) : null
                        setSmartDeviceCommand(deviceId, command?.capability, command?.name, [ value ])
                        break
                    case 'number':  // A number that can have fractional values. Limits can be defined to constrain the range of possible values.
                        setSmartDeviceCommand(deviceId, command?.capability, command?.name, [ event?.value.toFloat() ])
                        break
                    case 'boolean': // Either true or false
                        setSmartDeviceCommand(deviceId, command?.capability, command?.name, [ event?.value.toBoolean() ])
                        break
                    case 'object':  // A map of name value pairs, where the values can be of different types.
                        def map = new JsonSlurper().parseText(event?.value)
                        setSmartDeviceCommand(deviceId, command?.capability, command?.name, map)
                        break
                    case 'array':   // A list of values of a single type.
                        def list = new JsonSlurper().parseText(event?.value)
                        setSmartDeviceCommand(deviceId, command?.capability, command?.name, list)
                        break
                    default:
                        setSmartDeviceCommand(deviceId, command?.capability, command?.name, [ event?.value ])
                        break
                }
                if(!rule?.mute) logInfo "${app.getLabel()} sending '${replicaDevice?.getDisplayName()}' $type ● trigger:${event.name} ➣ command:${command?.name}:${event?.value} ● delay:${now() - eventPostTime}ms"
            }
        }      
    }            
}

Boolean deviceTriggerHandlerCache(replicaDevice, attribute, value) {
    logDebug "${app.getLabel()} executing 'deviceTriggerHandlerCache()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Boolean response = false

    String deviceId = getReplicaDeviceId(replicaDevice)
    Map device = getInstallSmartDevicesMap()?.get(deviceId)
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

    String deviceId = getReplicaDeviceId(replicaDevice)
    Map device = getInstallSmartDevicesMap()?.get(deviceId)
    if (device!=null) {
        device?.eventCache[attribute] = value
        logDebug "${app.getLabel()} cache => ${device?.eventCache}"
    }    
}

Map smartTriggerHandler(replicaDevice, Map event, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'smartTriggerHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]    
    //logInfo JsonOutput.toJson(event)
    
    List replicaDeviceRules = getReplicaDataJsonValue(replicaDevice, "rules")
    event?.each { capability, attributes ->
        attributes?.each{ attribute, value ->
            logTrace "smartEvent: capability:'$capability' attribute:'$attribute' value:'$value'" 
            replicaDeviceRules?.findAll{ it.type == "smartTrigger" }?.each { rule -> 
                Map trigger = rule?.trigger?.values()?.getAt(0)
                Map command = rule?.command?.values()?.getAt(0)

                // simple enum case
                if(attribute==trigger?.attribute && value?.value==trigger?.value) {                   
                    smartTriggerHandlerCache(replicaDevice, attribute, value?.value)
                    
                    def args = []
                    def method = command?.name
                    if(replicaDevice.hasCommand(method)) {
                        replicaDevice."$method"(*args)
                        if(!rule?.mute) logInfo "${app.getLabel()} received '${replicaDevice?.getDisplayName()}' event ○ trigger:$attribute:${value?.value} ➢ command:${command?.name} ${(eventPostTime ? "● delay:${now() - eventPostTime}ms" : "")}"
                    }
                }
                // non-enum case
                else if(attribute==trigger?.attribute && !trigger?.value) {                    
                    smartTriggerHandlerCache(replicaDevice, attribute, value?.value)
                    
                    def args = [value.value]
                    def method = command?.name
                    if(replicaDevice.hasCommand(method)) {
                        replicaDevice."$method"(*args)  
                        if(!rule?.mute) logInfo "${app.getLabel()} received '${replicaDevice?.getDisplayName()}' event ○ trigger:$attribute ➢ command:${command?.name}:${value?.value} ${(eventPostTime ? "● delay:${now() - eventPostTime}ms" : "")}"
                    }
                }
            }
        }
        response.statusCode = iHttpSuccess
    }
    return [statusCode:response.statusCode]
}

String getSmartDeviceEventsStatus(replicaDevice) {
    String value = "--"
    if(replicaDevice) {
        String healthState = getReplicaDataJsonValue(replicaDevice, "health")?.state?.toLowerCase()
        String deviceId = getReplicaDeviceId(replicaDevice)    
    
        String eventCount = (getInstallSmartDevicesMap()?.get(deviceId)?.eventCount ?: 0).toString()
        value = (healthState=='offline' ? healthState : eventCount)
        sendEvent(name:'smartEvent', value:value, descriptionText: JsonOutput.toJson([ deviceNetworkId:(replicaDevice?.deviceNetworkId), debug: appLogEnable ]))
    }
    return value
}

Map smartStatusHandler(replicaDevice, Map statusEvent, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'smartStatusHandler()' replicaDevice:'${replicaDevice?.getDisplayName()}'"
    Map response = [statusCode:iHttpError]
    
    if(appLogEventEnable && statusEvent) {
        logInfo "Status: ${JsonOutput.toJson(statusEvent)}"
    }    
    setReplicaDataJsonValue(replicaDevice, "status", statusEvent)
    statusEvent?.components?.main?.each { capability, attributes ->
        response.statusCode = smartTriggerHandler(replicaDevice, [ "$capability":attributes ], eventPostTime).statusCode
    }
    
    if( getSmartDeviceEventsStatus(replicaDevice) == 'offline' ) { getSmartDeviceHealth( getReplicaDeviceId(replicaDevice) ) } 
    return [statusCode:response.statusCode]
}

Map smartEventHandler(replicaDevice, Map deviceEvent, Long eventPostTime=null){
    logDebug "${app.getLabel()} executing 'smartEventHandler()' replicaDevice:'${replicaDevice.getDisplayName()}'"
    Map response = [statusCode:iHttpSuccess]
    
    if(appLogEventEnable && deviceEvent) {
        logInfo "Event: ${JsonOutput.toJson(deviceEvent)}"
    }    
    //setReplicaDataJsonValue(replicaDevice, "event", deviceEvent)    
    try {
        // events do not carry units. so get it from status. yeah smartthings is great!
        String unit = getReplicaDataJsonValue(replicaDevice, "status")?.components?.get(deviceEvent.componentId)?.get(deviceEvent.capability)?.get(deviceEvent.attribute)?.unit
        // status    {"switchLevel":             {"level":                  {"value":30,                "unit":"%",   "timestamp":"2022-09-07T21:16:59.576Z" }}}
        Map event = [ (deviceEvent.capability): [ (deviceEvent.attribute): [ value:(deviceEvent.value), unit:(deviceEvent?.unit ?: unit), timestamp: getTimestampSmartFormat() ]]]
        response.statusCode = smartTriggerHandler(replicaDevice, event, eventPostTime).statusCode
        
        if( getSmartDeviceEventsStatus(replicaDevice) == 'offline' ) { getSmartDeviceHealth( getReplicaDeviceId(replicaDevice) ) }
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
        response.statusCode = smartTriggerHandler(replicaDevice, event, eventPostTime).statusCode
        
        getSmartDeviceEventsStatus(replicaDevice)            
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
        getAllReplicaCapabilities(replicaDevice)
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
    def capability = getReplicaDataJsonValue(replicaDevice, "capabilities")
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
    Map descriptionEvent = getReplicaDataJsonValue(replicaDevice, "description")    
 
    descriptionEvent?.components.each { components ->
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
	Map data = [ uri: sURI, path: "/capabilities/${capabilityId}/${capabilityVersion}", deviceId: deviceId, method: "getSmartDeviceCapability", authToken: state?.authToken	]
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
	Map data = [ uri: sURI, path: "/devices/${deviceId}/health", deviceId: deviceId, method: "getSmartDeviceHealth", authToken: state?.authToken ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

void allSmartDeviceDescription(Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device descriptions"
    runIn(delay<1?:1, getAllDeviceDescription)
}

void getAllDeviceDescription() {
    logDebug "${app.getLabel()} executing 'getAllDeviceDescription()'"
    getInstallSmartDevicesMap()?.each{ deviceId, items ->
        if ( getReplicaDevices(deviceId) ) { //only ping devices mirrored
            getSmartDeviceDescription(deviceId)
            pauseExecution(250) // no need to hammer ST
        }
    }
}

Map getSmartDeviceDescription(String deviceId) {
    logDebug "${app.getLabel()} executing 'getSmartDeviceDescription($deviceId)'"
    Map response = [statusCode:iHttpError]
	Map data = [ uri: sURI, path: "/devices/${deviceId}", deviceId: deviceId, method: "getSmartDeviceDescription", authToken: state?.authToken ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

void allSmartDeviceStatus(Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device status"
    runIn(delay, getAllSmartDeviceStatus)    
}

void getAllSmartDeviceStatus() {
    logDebug "${app.getLabel()} executing 'getAllSmartDeviceStatus()'"    
    getInstallSmartDevicesMap()?.each{ deviceId, items ->
        if ( getReplicaDevices(deviceId) ) { //only ping devices mirrored
            getSmartDeviceStatus(deviceId)
            pauseExecution(250) // no need to hammer ST
        }
    }
}

Map getSmartDeviceStatus(String deviceId) {
    logDebug "${app.getLabel()} executing 'getSmartDeviceStatus($deviceId)'"
    Map response = [statusCode:iHttpError]
	Map data = [ uri: sURI, path: "/devices/${deviceId}/status", deviceId: deviceId, method: "getSmartDeviceStatus", authToken: state?.authToken ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

Map getSmartModeList() {
    logDebug "${app.getLabel()} executing 'getSmartModeList()'"
    Map response = [statusCode:iHttpError]    
    Map data = [ uri: sURI, path: "/locations/${state?.locationId}/modes", method: "getSmartModeList", authToken: state?.authToken ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

Map getSmartSubscriptionList() {
    logDebug "${app.getLabel()} executing 'getSmartSubscriptionList()'"
    Map response = [statusCode:iHttpError]    
    Map data = [ uri: sURI, path: "/installedapps/${state?.installedAppId}/subscriptions", method: "getSmartSubscriptionList", authToken: state?.authToken ]
	response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

void allSmartDeviceList(Integer delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device list"
    runIn(delay<1?:1, getSmartDeviceList)    
}

Map getSmartDeviceList() {
    logDebug "${app.getLabel()} executing 'getSmartDeviceList()'"
    Map response = [statusCode:iHttpError]   
	Map data = [ uri: sURI, path: "/devices", method: "getSmartDeviceList", authToken: state?.authToken	]
    response.statusCode = asyncHttpGet("asyncHttpGetCallback", data).statusCode
    return response
}

private Map asyncHttpGet(String callbackMethod, Map data) {
    logDebug "${app.getLabel()} executing 'asyncHttpGet()'"
    Map response = [statusCode:iHttpError]
	
    Map params = [
	    uri: data.uri,
	    path: data.path,
		headers: [ Authorization: "Bearer ${data.authToken}" ]
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
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartDescriptionHandler(replicaDevice, description) }
                description = null
                break
            case "getSmartDeviceStatus":
                Map status = new JsonSlurper().parseText(resp.data)        
                getReplicaDevices(data.deviceId)?.each { replicaDevice -> smartStatusHandler(replicaDevice, status) }
                status = null
                break
            case "getSmartModeList":
                Map modeList = new JsonSlurper().parseText(resp.data)
                logInfo "${app.getLabel()} successful ${data?.method}: ${modeList}"
                break           
            case "getSmartSubscriptionList":
                Map subscriptionList = new JsonSlurper().parseText(resp.data)
                logInfo "${app.getLabel()} successful ${data?.method}: ${subscriptionList}"
                break            
            case "getSmartDeviceList":            
                Map deviceList = new JsonSlurper().parseText(resp.data)
                logTrace "${app.getLabel()} successful ${data?.method}: ${deviceList}"
                // With permissions:['r:devices:*'] set to SmartThings we will get all location devices.
                Map smartDevices = [ items:[] ]
                getInstallSmartDevicesMap()?.each{ deviceId, items ->
                     smartDevices.items.add( deviceList?.items?.find{ it.deviceId==deviceId } )
                }
                setSmartDevicesMap(smartDevices)
                /*
                // TEMP this is a work in progress. need to move away from the deviceId to allow for device mirror
                getAllReplicaDeviceIds()?.each { deviceId ->
                    getReplicaDevices(deviceId)?.each { replicaDevice ->
                        if(getReplicaDataJsonValue(replicaDevice, "replica")?.type == null) {
                            def replica = [ deviceId:deviceId, replicaId:(app.getId()), type:( getChildDevice( replicaDevice?.deviceNetworkId )!=null ? 'child' : 'mirror')]
                            //def replica = [ deviceId:deviceId, replicaId:(app.getId())]
                            logInfo "${app.getLabel()} '$replicaDevice' updating replica:$replica" 
                            setReplicaDataJsonValue(replicaDevice, "replica", replica)
                        } else {
                            //logInfo "${app.getLabel()} '$replicaDevice' clearing deviceId"
                            clearReplicaDataCache(replicaDevice, "deviceId", true)
                        }
                    }
                }
                // *** END TEMP
                */
                break
            default:
                logWarn "${app.getLabel()} asyncHttpGetCallback ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        logWarn("${app.getLabel()} asyncHttpGetCallback ${data?.method} error: ${resp.status}")
    }
}

void allSmartDeviceSubscriptions(delay=1) {
    logInfo "${app.getLabel()} refreshing all SmartThings device subscriptions"
    runIn(delay<1?:1, createSmartSubscriptions)    
}

Map createSmartSubscriptions() {    
    logDebug "${app.getLabel()} executing 'createSmartSubscriptions()'"
    Map response = [statusCode:iHttpError]
    
    deleteSmartSubscriptions() //clears all device subscriptions for both hubitat and smartthings
    pauseExecution(250) // no need to hammer ST
    // TEMP: Need to not subscrib to everyone. Waste.
    getInstallSmartDevicesMap().each{ deviceId, items ->
        response.statusCode = setSmartDeviceSubscription(deviceId).statusCode
        pauseExecution(250) // no need to hammer ST        
    }
    if(state?.locationId) {
        response.statusCode = setSmartHealthSubscription()
        response.statusCode = setSmartModeSubscription()
    }        
    return response
}

Map deleteSmartSubscriptions() {
    logDebug "${app.getLabel()} executing 'deleteSmartSubscriptions()'"
    Map response = [statusCode:iHttpError]
    //temp location todo this
    getAllReplicaDeviceIds()?.each { deviceId ->
        getReplicaDevices(deviceId)?.each { replicaDevice ->
            unsubscribe(replicaDevice)
            clearReplicaDataCache(replicaDevice, 'subscription', true)
        }
    }
    
    Map params = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpDelete(params) { resp ->
            //resp.headers.each { logTrace "${it.name} : ${it.value}" }
            logTrace "response data: ${resp.data}"
            logTrace "response status: ${resp.status}"
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "${app.getLabel()} delete subscription error: $e"
    }    
    return response
}

Map setSmartDeviceCommand(deviceId, capability, command, arguments = []) {
    logDebug "${app.getLabel()} executing 'setSmartDeviceCommand()'"
    Map response = [statusCode:iHttpError]

    Map commands = [ commands: [[ component: "main", capability: capability, command: command, arguments: arguments ]] ]
    Map data = [
        uri: sURI,
        path: "/devices/${deviceId}/commands",
        method: "setSmartDeviceCommand",
        body: JsonOutput.toJson(commands),
        contentType: "application/json",
        requestContentType: "application/json",
        authToken: state?.authToken        
    ]
    response.statusCode = asyncHttpPost("asyncHttpPostCallback", data).statusCode
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
        contentType: "application/json",
        requestContentType: "application/json",
        method: "setSmartModeSubscription",
        authToken: state?.authToken        
    ]
    response.statusCode = asyncHttpPost("asyncHttpPostCallback", data).statusCode    
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
        contentType: "application/json",
        requestContentType: "application/json",
        method: "setSmartHealthSubscription",
        authToken: state?.authToken        
    ]
    response.statusCode = asyncHttpPost("asyncHttpPostCallback", data).statusCode    
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
        contentType: "application/json",
        requestContentType: "application/json",
        method: "setSmartDeviceSubscription",
        authToken: state?.authToken        
    ]
    response.statusCode = asyncHttpPost("asyncHttpPostCallback", data).statusCode    
    return response
}

private Map asyncHttpPost(String callbackMethod, Map data) {
    logDebug "${app.getLabel()} executing 'asyncHttpPost()'"
    Map response = [statusCode:iHttpError]
	
    Map params = [
	    uri: data.uri,
	    path: data.path,
        body: data.body,
        contentType: data.contentType,
        requestContentType: data.requestContentType,
		headers: [ Authorization: "Bearer ${data.authToken}" ]
    ]
	try {
	    asynchttpPost(callbackMethod, params, data)
        response.statusCode = iHttpSuccess
	} catch (e) {
	    logWarn "${app.getLabel()} asyncHttpPost error: $e"
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
                logDebug "${app.getLabel()} successful ${data?.method}: ${deviceSubscription}"
                getReplicaDevices(deviceSubscription?.device?.deviceId)?.each { replicaDevice ->
                    deviceSubscription.device['timestamp'] = getTimestampSmartFormat()                        
                    setReplicaDataJsonValue(replicaDevice, "subscription", deviceSubscription)
                    logInfo "${app.getLabel()} '$replicaDevice' subscription response code was ${resp.status}" 
                    getReplicaDeviceRefresh(replicaDevice)
                }          
                break            
            case "setSmartHealthSubscription":
                Map healthSubscription = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} successful ${data?.method}: ${healthSubscription}"
                logInfo "${app.getLabel()} location:${healthSubscription?.deviceHealth?.locationId} subscription health code was ${resp.status}"
                break
            case "setSmartModeSubscription":
                Map modeSubscription = new JsonSlurper().parseText(resp.data)
                logDebug "${app.getLabel()} successful ${data?.method}: ${modeSubscription}"
                logInfo "${app.getLabel()} location:${modeSubscription?.mode?.locationId} subscription mode code was ${resp.status}"
                break
            default:
                logWarn "${app.getLabel()} asyncHttpPostCallback ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logWarn("${app.getLabel()} asyncHttpPostCallback ${data?.method} status:${resp.status}")
    }
}

Map setSmartMode(String modeId) {
    logDebug "${app.getLabel()} executing 'setSmartMode($modeId)'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/locations/${state?.locationId}/modes/current",
        body: JsonOutput.toJson([modeId:modeId]),
        contentType: "application/json",
        requestContentType: "application/json",
        headers: [ Authorization: "Bearer ${clientSecretPAT}" ]        
    ]
    try {
        httpPut(params) { resp ->
            resp.headers.each { logInfo "${it.name} : ${it.value}" }
            logTrace "response contentType: ${resp.contentType}"
            logInfo "response data: ${resp.data}"
            logInfo "response status: ${resp.status}"
            //response.data = resp?.data?.items ? [ items: resp.data.items ] : null
            response.statusCode = resp.status            
        }
    } catch (e) {
        logWarn "${app.getLabel()} has setSmartMode($modeId) error: $e"        
    }
    return [statusCode:response.statusCode, modes:response.data]
}

Map getSmartCurrentMode() {
    logDebug "${app.getLabel()} executing 'getSmartCurrentMode()'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/locations/${state?.locationId}/modes/current",
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpGet(params) { resp ->
            resp.headers.each { logTrace "${it.name} : ${it.value}" }
            logTrace "response contentType: ${resp.contentType}"
            logDebug "response data: ${resp.data}"
            logDebug "response status: ${resp.status}"
            response.data = resp.data
            response.statusCode = resp.status            
        }
    } catch (e) {
        logWarn "${app.getLabel()} has getSmartCurrentMode() error: $e"        
    }
    return [statusCode:response.statusCode, mode:response.data]
}

Map getSmartSubscription(String subscriptionId) {
    logDebug "${app.getLabel()} executing 'getSmartSubscription($subscriptionId)'"
    Map response = [statusCode:iHttpError]
    
    Map params = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions/${subscriptionId}",
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpGet(params) { resp ->
            resp.headers.each { logTrace "${it.name} : ${it.value}" }
            logTrace "response contentType: ${resp.contentType}"
            logDebug "response data: ${resp.data}"
            logDebug "response status: ${resp.status}"
            response.data = resp.data
            response.statusCode = resp.status            
        }
    } catch (e) {
        logWarn "${app.getLabel()} has getSmartSubscriptions() error: $e"        
    }
    return [statusCode:response.statusCode, subscription:response.data]
}

Map installSmartDevices(Map data) {
    logDebug "${app.getLabel()} executing 'installSmartDevices()'"
    Map response = [statusCode:iHttpSuccess]    
    
    state.installedAppConfig = data.installedApp.config
    clearAllVolatileCache()       
    logInfo "${app.getLabel()} installed ${getInstallSmartDevicesMap().size()} SmartThings devices"
    
    appRemoveSettings() // remove setting to ensure UI doesn't have stale tokens
    setSmartTokens(data) // new token for install, sometimes same token for update   
    
    runEvery30Minutes('allSmartDeviceHealth')
    String crontab = "${Calendar.instance[ Calendar.SECOND ]} ${Calendar.instance[ Calendar.MINUTE ]} */8 * * ?" // every 8 hours from midnight+min+sec
    schedule(crontab, 'allSmartTokenRefresh') // tokens are good for 24 hours
        
    return [statusCode:response.statusCode]
}

void allSmartTokenRefresh(Integer delay=0) {
    logInfo "${app.getLabel()} refreshing all SmartThings authTokens"
    if(delay) {
        runIn(delay, handleTokenRefresh)
    } else { 
        handleTokenRefresh()
    }
}

void setSmartTokens(Map data) {
    logDebug "${app.getLabel()} executing 'setSmartTokens()'"
    
    if (data.authToken != state?.authToken) {    
        state.authToken = data?.authToken ?: state?.authToken
        state.refreshToken = data?.refreshToken ?: state?.refreshToken
        if(state?.installedAppId && data?.installedApp?.installedAppId && state.installedAppId != data?.installedApp?.installedAppId) {
            logWarn "${app.getLabel()} already has attached to a ST SmartApp and is now moving to a new ST SmartApp installedAppId:${data?.installedApp?.installedAppId}"
        }
        state.installedAppId = data?.installedApp?.installedAppId ?: state?.installedAppId
        state.locationId = data?.installedApp?.locationId ?: state?.locationId

        def expiration = data?.expiration?.toInteger() ?: 24*60*60
        Date expirationDate = new Date(new Date().toInstant().toEpochMilli() + (expiration * 1000))
        state.authTokenDate = expirationDate.format("YYYY-MM-dd h:mm:ss a z")
        logInfo "${app.getLabel()} authToken updated at ${state.authTokenDate}"                      
    }
    allSmartDeviceList(1)
    allSmartDeviceSubscriptions(5)
}

Map handleTokenRefresh() {
    logDebug "${app.getLabel()} executing 'handleTokenRefresh()'"
    Map response = [statusCode:iHttpError]
    
    def refreshToken = state.refreshToken // captured from INSTALL or UPDATE lifecycle first time. Good for 24hrs.
    def clientId = clientIdUUID // user input from SmartThings Developer SmartApp configuration
    def clientSecret = clientSecretUUID // user input from SmartThings Developer SmartApp configuration   
  
    Map params = [
        uri: "https://auth-global.api.smartthings.com/oauth/token", 
        query: [ grant_type:"refresh_token", client_id:"${clientId}", refresh_token:"${refreshToken}" ],
        contentType: "application/x-www-form-urlencoded",
        requestContentType: "application/json",
		headers: [ Authorization: "Basic ${("${clientId}:${clientSecret}").bytes.encodeBase64().toString()}" ]
    ] 
    
    try {
        httpPost(params) { resp ->
            resp.headers.each { logTrace "${it.name} : ${it.value}" }
            //logTrace "response contentType: ${resp.contentType}"
            logTrace "response data: ${resp.data.toString().replaceAll(/(?<=(?:refresh_token\":\"|access_token\":\"))(.*?)(?=\")/, "<b>removed</b>")}"
            logTrace "response status: ${resp.status}"
            // strange json'y response. this works good enough to solve. 
            def respStr = resp.data.toString().replace("[{","{").replace("}:null]","}")
            def respJson = new JsonSlurper().parseText(respStr)
            //logTrace "response json: ${respJson}" // this will show tokens in the logs

            setSmartTokens( [ authToken:respJson.access_token, refreshToken:respJson.refresh_token, expiration:respJson.expires_in ] )
            response.statusCode = resp.status
         }
    } catch (e) {
        logWarn "${app.getLabel()} handleTokenRefresh error: $e"
        state.authTokenDate = "Error"
        runIn(1*60*60, handleTokenRefresh) // problem. lets try every hour.
    }
    
    return [statusCode:response.statusCode]
}

Map handleUninstall(Map uninstallData) {
    logInfo "${app.getLabel()} executing uninstall"
    Map response = [statusCode:iHttpSuccess]
    
    // All subscriptions and schedules for the installed app will be automatically deleted by SmartThings.
    appRemoveSettings()
    state.remove('authToken')
    state.remove('authTokenDate')
    state.remove('install')
    state.remove('installedAppId')
    state.remove('installedAppConfig')
    state.remove('locationId')
    state.remove('refreshToken')
    
    unsubscribe()
    unschedule()
    clearAllVolatileCache()
    
    return [statusCode:response.statusCode, uninstallData:{}]
}

Map handleEvent(Map eventData, Long eventPostTime=null) {
    logDebug "${app.getLabel()} executing 'handleEvent()'"
    Map response = [statusCode:iHttpSuccess]
    
    eventData?.events?.each { event ->
        switch(event?.eventType) {
            case 'DEVICE_EVENT':
                String deviceId = event?.deviceEvent?.deviceId
                Map device = getInstallSmartDevicesMap()?.get(deviceId)
                if(device?.eventCount!=null) device.eventCount += 1 
                getReplicaDevices(deviceId)?.each{ replicaDevice ->
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
                logInfo event
                break
            default:
                logInfo "${app.getLabel()} 'handleEvent()' did not handle $event"
        }
    }
   
    return [statusCode:response.statusCode, eventData:{}]
}

Map handleUpdate(Map updateData) {
    logDebug "${app.getLabel()} executing 'handleUpdate()'"
    Map response = [statusCode:iHttpError]        
    response.statusCode = installSmartDevices(updateData).statusCode

    return [statusCode:response.statusCode, updateData:{}]
}

Map handleInstall(Map installData) {
    logDebug "${app.getLabel()} executing 'handleInstall()'"
    Map response = [statusCode:iHttpError]    
    response.statusCode = installSmartDevices(installData).statusCode
    
    return [statusCode:response.statusCode, installData:{}]
}

Map handleConfig(Map configurationData) {
    logDebug "${app.getLabel()} executing 'handleConfig()'"
    Map response = [statusCode:iHttpError]
    
    switch(configurationData?.phase) {
        case 'INITIALIZE':
            List permissions = ['r:devices:*', 'r:locations:*', 'r:hubs:*', 'r:scenes:*', 'x:scenes:*','i:deviceprofiles:*']
            response = [statusCode:iHttpSuccess, configurationData:[initialize:[id: "hubitat_appid_${app.getId()}", firstPageId:"mainPage", permissions:permissions, disableCustomDisplayName:false, disableRemoveApp:false]]]
            break;
        case 'PAGE':
            switch(configurationData?.pageId) {
                case 'mainPage':
                    response = getMainPage(configurationData)
                    break;
                case 'pageTwo':
                    response = getPageTwo(configurationData)
                    break;
                default:
                    logWarn "${app.getLabel()} configuration page ${configurationData.pageId} not supported"
            }
            break                    
        default:
            logWarn "${app.getLabel()} configuration phase ${configurationData.phase} not supported"
    }   
    return response
}

Map handleConfirm(Map confirmationData) {
    logDebug "${app.getLabel()} executing 'handleConfirm()' url:${confirmationData?.confirmationUrl}"
    Map response = [statusCode:iHttpError]
    
    try {
        httpGet(confirmationData?.confirmationUrl) { resp ->
            logInfo "response data: ${resp?.data}"      
            if (resp?.data?.targetUrl == getCloudUri()) {
                logInfo "${app.getLabel()} Confirmation success"
            }
            else {
                logWarn "${app.getLabel()} Confirmation failure with url:${resp?.data?.targetUrl}"
            }
            response.statusCode = resp.status
            response['targetUrl'] = resp.data.targetUrl
            }
        
    } catch (e) {
        logWarn "${app.getLabel()} Confirmation error: $e"     
    }
    return response
}

// Web browser uses Get
def webhookGet() {
    logDebug "${app.getLabel()} executing 'webhookGet()'"
    logDebug "REQUEST: ${request}"
    logTrace "Params: $params"
    if (!mainPageAllowCloudAccess) {
        logWarn "${app.getLabel()} endpoint disabled"
        return render(contentType: "text/html", data: getHtmlResponse(false), status: 404)
    }
    return render(contentType: "text/html", data: getHtmlResponse(true), status: iHttpSuccess)
}

// SmartThings uses Post
def webhookPost() {
    Long eventPostTime =  now()    
    logDebug "${app.getLabel()} executing 'webhookPost()'"
    logDebug "REQUEST: ${request.body.replaceAll(/(?<=(?:authToken\":\"|refreshToken\":\"))(.*?)(?=\")/, "<b>removed</b>")}"    

    if (!mainPageAllowCloudAccess) {
        logWarn "${app.getLabel()} endpoint disabled"
        return render(contentType: "text/html", data: getHtmlResponse(false), status: 404)
    }
    
    def event = new JsonSlurper().parseText(request.body)
    Map response = [statusCode:iHttpError]    
    
    switch(event?.lifecycle) {
        case 'PING': 
	        //responder.respond({statusCode: 200, pingData: {challenge: evt.pingData.challenge}})
            response = [statusCode:200, pingData: [challenge: event?.pingData.challenge]]
		    break;
        case 'CONFIRMATION':            
            response = handleConfirm(event?.confirmationData)
            break;
        case 'CONFIGURATION':
            response = handleConfig(event?.configurationData)       
            break;
        case 'INSTALL':
            response = handleInstall(event?.installData)
            break;
        case 'UPDATE':
            response = handleUpdate(event?.updateData)
            break;
        case 'UNINSTALL':
            response = handleUninstall(event?.uninstallData)
            break;
        case 'EVENT':
            response = handleEvent(event?.eventData, eventPostTime)
            break;        
        default:
          logWarn "${app.getLabel()} lifecycle ${evt?.lifecycle} not supported"
    }    
    event = null
    
    logDebug "RESPONSE: ${JsonOutput.toJson(response)}"
    return render(status:response.statusCode, data:JsonOutput.toJson(response))    
}

Map getMainPage(Map configurationData) {
    logInfo "${app.getLabel()} fetching ${configurationData?.pageId}"
    logDebug "${app.getLabel()} configurationData: ${configurationData}"
    
    return [
        statusCode:iHttpSuccess, 
        configurationData:[       
            page:[
                name:"${app.getLabel()}", 
                complete:false, 
                pageId:'mainPage', 
                nextPageId:'pageTwo', 
                previousPageId:null,
                sections:[
                    [ settings:[[ id:'dimmer', required:false, type:'DEVICE', name:'Select Dimmers', description:'Tap to set', multiple:true, capabilities:['switchLevel'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'switch', required:false, type:'DEVICE', name:'Select Switches', description:'Tap to set', multiple:true, capabilities:['switch'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'button', required:false, type:'DEVICE', name:'Select Button Devices', description:'Tap to set', multiple:true, capabilities:['button'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'contactSensor', required:false, type:'DEVICE', name:'Select Contact Sensors', description:'Tap to set', multiple:true, capabilities:['contactSensor'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'motionSensor', required:false, type:'DEVICE', name:'Select Motion Sensors', description:'Tap to set', multiple:true, capabilities:['motionSensor'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'temperatureMeasurement', required:false, type:'DEVICE', name:'Select Temperature Sensors', description:'Tap to set', multiple:true, capabilities:['temperatureMeasurement'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'relativeHumidityMeasurement', required:false, type:'DEVICE', name:'Select Humidity Sensors', description:'Tap to set', multiple:true, capabilities:['relativeHumidityMeasurement'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'presenceSensor', required:false, type:'DEVICE', name:'Select Presence Sensors', description:'Tap to set', multiple:true, capabilities:['presenceSensor'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'alarm', required:false, type:'DEVICE', name:'Select Alarm Devices', description:'Tap to set', multiple:true, capabilities:['alarm'], permissions:['r', 'x']]]],
                    [ settings:[[ id:'windowShade', required:false, type:'DEVICE', name:'Select Window Shade Devices', description:'Tap to set', multiple:true, capabilities:['windowShade'], permissions:['r', 'x']]]]
               ]                
            ]
        ]
    ]
}

Map getPageTwo(Map configurationData) { 
    logInfo "${app.getLabel()} fetching ${configurationData?.pageId}"
    logDebug "${app.getLabel()} configurationData: ${configurationData}"
    
    List deviceList = []
    configurationData?.config?.each{ id, devices -> 
        devices?.each{ device ->
            if(device?.valueType=='DEVICE') deviceList.add( [ id:id, deviceId: device?.deviceConfig?.deviceId ] )           
        } 
    }
    def deviceCountTotal = deviceList?.size() ?: 0
    def deviceCountUnique = deviceList?.unique{ a, b -> a.deviceId <=> b.deviceId }?.size() ?: 0
    def deviceCountMax = iSmartAppDeviceLimit
    
    String description =  "\n• $deviceCountTotal total device${deviceCountTotal==1 ? '' : 's'}"
           description += (deviceCountTotal>1 ? "\n• $deviceCountUnique total unique devices" : "")
           description += "\n\n Note: You are allowed $deviceCountMax unique devices per SmartThings SmartApp instance\n"
    
    List sections = [ [ settings:[[ id: "pageTwoSection1", name: "Devices Selected:", description:description, type: "PARAGRAPH", defaultValue: "" ]]] ]
    
    if(deviceCountMax < deviceCountUnique) {
        sections.add( [ settings:[[ id: "pageTwoSection2", 
                                   name: "Maximum number of SmartThings SmartApp unique devices exceeded\n\nPlease return and remove ${deviceCountUnique-deviceCountMax} device${deviceCountUnique-deviceCountMax > 1 ? 's' : ''}", 
                                   description: """\nThis limit is based upon the SmartThings guardrail of $deviceCountMax subscriptions per SmartThings SmartApp instance.\n\
                                                   \nThe Hubitat SmartApp does allow additional unique applications (with an additional corresponding unique SmartThings SmartApp) to work beyond this limit.""",
                                   type: "PARAGRAPH", 
                                   defaultValue: "" // this value will be pushed back to app from install. Not sure why.
                                  ]]] )
    }       
    
    return [
        statusCode:iHttpSuccess, 
        configurationData:[       
            page:[
                name:"${app.getLabel()}", 
                complete:(deviceCountMax >= deviceCountUnique), 
                pageId:'pageTwo', 
                nextPageId:null, 
                previousPageId:'mainPage',
                sections:sections                
            ]
        ]
    ]
}

def getHtmlResponse(Boolean success=false) {
"""
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <link rel="icon" href="data:;base64,iVBORw0KGgo=">
  <title>${app.getLabel()}</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    * { line-height: 1.2; margin: 0;}
    html { color: #888; display: table; font-family: sans-serif; height: 100%; text-align: center; width: 100%; }
    body { display: table-cell; vertical-align: middle; margin: 2em auto; }
    h1 { color: #555; font-size: 2em; font-weight: 400; }
    p { margin: 0 auto; width: 280px; }
    @media only screen and (max-width: 280px) { body, p { width: 95%; } h1 { font-size: 1.5em; margin: 0 0 0.3em; } }
  </style>
</head>
<body>
  <h1>${success ? "200 OK" : "404 Page Not Found"}</h1>
  <p>${success ? "Successfully reached cloud endpoint." : "Sorry, but the page you were trying to view does not exist."}</p>
</body>
</html>
"""
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
private String getReplicaDeviceId(replicaDevice, overrideAppId=false) {
    String deviceId = null
    Map replica = getReplicaDataJsonValue(replicaDevice, "replica")
    /*if(replica==null) { 
        deviceId = getReplicaDataValue(replicaDevice, "deviceId")
    }
    else*/ if(overrideAppId || replica?.replicaId==app.getId()) { // this is for mirror devices
        deviceId = replica?.deviceId
    }
    return deviceId        
}

private void setReplicaDataJsonValue(replicaDevice, dataKey, dataValue) {
    setReplicaDataValue(replicaDevice, dataKey, JsonOutput.toJson(dataValue))
}

private void setReplicaDataValue(replicaDevice, dataKey, dataValue) {
    getReplicaDataValue(replicaDevice, dataKey, dataValue) // update cache first
    replicaDevice?.updateDataValue(dataKey, dataValue) // STORE IT to device object
}

private def getReplicaDataJsonValue(replicaDevice, dataKey) {
    def response = null
    try {
        def value = getReplicaDataValue(replicaDevice, dataKey)
        if (value) {                
            response = new JsonSlurper().parseText(value)
        }
    } catch (e) {
        logWarn "${app.getLabel()} getReplicaDataJsonValue $replicaDevice $key error: $e"
    }
    return response
}

private def getReplicaDataValue(replicaDevice, dataKey, setDataValue=null) { // setDataValue will directly update the cache without fetching from the object.
    def appId = app.getId()    
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
private void clearReplicaDataCache(replicaDevice) { 
    if(g_mReplicaDeviceCache[app.getId()] && replicaDevice?.deviceNetworkId ) { 
        g_mReplicaDeviceCache[app.getId()][replicaDevice?.deviceNetworkId] = null 
        g_mReplicaDeviceCache[app.getId()][replicaDevice?.deviceNetworkId] = [:] 
    }
}
private void clearReplicaDataCache(replicaDevice, dataKey, delete=false) {
    if(delete) { replicaDevice?.removeDataValue(dataKey) }
    if(g_mReplicaDeviceCache[app.getId()] && replicaDevice?.deviceNetworkId && dataKey) { 
        if(g_mReplicaDeviceCache[app.getId()]?.get(replicaDevice?.deviceNetworkId)?.get(dataKey)) {
            g_mReplicaDeviceCache[app.getId()].get(replicaDevice?.deviceNetworkId).remove(dataKey)            
        }
    }
}
// ******** Volatile Memory Device Cache - End ********

// ******** Child Device Component Handlers - Start ********
void componentOn(device) {
    logDebug "componentOn device:$device"
    getChildDevice(device.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${device.displayName} was turned on", data:[appId:app.getId()]]])
}

void componentOff(device) {
    logDebug "componentOff device:$device"
    getChildDevice(device.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${device.displayName} was turned off", data:[appId:app.getId()]]])
}

void componentSetLevel(device, level, ramp=null) {
    logDebug "componentSetLevel device:$device $level $ramp"
    getChildDevice(device.deviceNetworkId).parse([[name:"level", value:level, descriptionText:"${device.displayName} level was set to ${level}%", unit: "%", data:[appId:app.getId()]]])
}

void componentStartLevelChange(device, direction) {
    logWarn "componentStartLevelChange device:$device $direction : No Operation"
}

void componentStopLevelChange(device) {
    logWarn "componentStopLevelChange device:$device : No Operation"
}

void componentRefresh(device) {
    logDebug "componentRefresh device:$device"
    replicaDeviceRefresh(device)
}

void componentPush(device, buttonNumber) {
    logDebug "componentPush device:$device buttonNumber:$buttonNumber"
    getChildDevice(device.deviceNetworkId).parse([[name:"pushed", value:(buttonNumber?:1), descriptionText:"${device.displayName} button number ${(buttonNumber?:1)} was pushed", isStateChange:true, data:[appId:app.getId()]]])
}

void componentDoubleTap(device, buttonNumber) {
    logDebug "componentDoubleTap device:$device buttonNumber:$buttonNumber"
    getChildDevice(device.deviceNetworkId).parse([[name:"doubleTapped", value:(buttonNumber?:1), descriptionText:"${device.displayName} button number ${(buttonNumber?:1)} was double tapped", isStateChange:true, data:[appId:app.getId()]]])
}


void componentHold(device, buttonNumber) {
    logDebug "componentHold device:$device buttonNumber:$buttonNumber"
    getChildDevice(device.deviceNetworkId).parse([[name:"held", value:(buttonNumber?:1), descriptionText:"${device.displayName} button number ${(buttonNumber?:1)} was held", isStateChange:true, data:[appId:app.getId()]]])
}

void componentRelease(device, buttonNumber) {
    logDebug "componentRelease device:$device buttonNumber:$buttonNumber"
    getChildDevice(device.deviceNetworkId).parse([[name:"released", value:(buttonNumber?:1), descriptionText:"${device.displayName} button number ${(buttonNumber?:1)} was released", isStateChange:true, data:[appId:app.getId()]]])
}
// ******** Child Device Component Handlers - End ********

void testButton() {
    
    logInfo getSmartSubscription('a3053aae-ecba-455e-9ba8-6d34ef72b428')
    logInfo getSmartSubscriptionList()
    logInfo getSmartModeList()
    return
    //logInfo "setSmartMode ${JsonOutput.toJson(setSmartMode('1181870c-78e0-4ca6-98b5-ca8c048c0273'))}"
    logInfo "getSmartCurrentMode: ${JsonOutput.toJson(getSmartCurrentMode())}"    
    logInfo "getSmartModes: ${JsonOutput.toJson(getSmartModes())}"     
    logInfo "getSmartSubscriptions: ${JsonOutput.toJson(getSmartSubscriptions())}"
}
