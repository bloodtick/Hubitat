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
*  1.0.0 2022-10-01 First pass.
*  1.0.1 2022-10-25 Allow for more than one instance. UI modes. Turn off status refresh. Bug fixes.
*  1.0.2 2022-10-25 Build refresh device method. Add 'Generic Component Dimmer' to test moving to Component types.
*
*/

public static String version() {  return "v1.0.2"  }
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

@Field static final Integer iHttpSuccess=200
@Field static final Integer iHttpError=400
@Field static final String  sURI="https://api.smartthings.com"
@Field static final String  sColorDarkBlue="#1A77C9"
@Field static final String  sColorLightGrey="#DDDDDD"
@Field static final String  sColorDarkGrey="#696969"
@Field static final String  sColorDarkRed="DarkRed"
@Field static final String sCodeRelease="Alpha Release"

// IN-MEMORY VARIABLES (Cleared only on HUB REBOOT or CODE UPDATES)
@Field volatile static Map<String,Map> g_mSmartDevices = [:]
@Field volatile static Map<String,String> g_mPageConfigureDevice = [:]

def intialize() {
    logInfo "intialize"
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
    page name:"pageCreateDevice"
    page name:"pageMirrorDevice"
    page name:"pageAddMirrorDevice"
    page name:"pageAddDevice"
    page name:"pageDeleteDevice"
    page name:"pageSubDevice"
    page name:"pageConfigureDevice"
}

def getLocalUri() {
    return getFullLocalApiServerUrl() + "/webhook?access_token=${state.accessToken}"
}

def getCloudUri() {
    return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/webhook?access_token=${state.accessToken}"
}

def getSmartDevices() {
    def appId = app.getId()
    if (!g_mSmartDevices[appId]?.items) {
        getDeviceList()
        pauseExecution(2000)
    }
    return g_mSmartDevices[appId]
}    

def mainPage(){
    if(!state.accessToken){	
        createAccessToken()	
    }
    
    //app.removeSetting("pageConfigureDeviceAllowDuplicateSmartAttribute")
    //state.remove("rule")
    
    return dynamicPage(name: "mainPage", install: true,  refreshInterval: 0){
        displayHeader()
        
        section(menuHeader("${app.getLabel()} Configuration")+"$sHubitatIconStatic $sSamsungIconStatic") {
			input(name: "mainPageAllowCloudAccess", type: "bool", title: getFormat("text","$sHubitatIcon Enable Hubitat REST API Endpoint for SmartThings Developer Workspace SmartApp"), defaultValue: false, submitOnChange: true)  
            
            if(mainPageAllowCloudAccess) {
                paragraph("<ul><strong>External</strong>: ${getFormat("hyperlink", getCloudUri(), getCloudUri())}</ul>")
                
                paragraph("<ul><b style='color:${sColorDarkRed}'>&#9888;Warning:</b> Enabling external endpoints can allow data on the local Hubitat Hub to be exposed and/or captured. "
                         +"I am not saying this is actually happening, but cloud logging of the HTML response could allow the ability to reassemble the data.</ul>")           
            }
            
            if(!pageMainPageAppLabel || !mainPageAllowConfig) { app.updateSetting( "pageMainPageAppLabel", app.getLabel()) }
            input(name: "mainPageAllowConfig", type: "bool", title: getFormat("text","$sHubitatIcon Additional Configuration"), defaultValue: false, submitOnChange: true)            
            if(mainPageAllowConfig) {
                paragraph( getFormat("line"))
                // required for authToken refresh
                input(name: "clientIdUUID", type: "text", title: getFormat("hyperlink","$sSamsungIcon SmartApp Client ID from SmartThings Developer Workspace:","https://smartthings.developer.samsung.com/workspace"), width: 6, submitOnChange: true, newLineAfter:true)
                input(name: "clientSecretUUID", type: "text", title: getFormat("hyperlink","$sSamsungIcon SmartApp Client Secret from SmartThings Developer Workspace:","https://smartthings.developer.samsung.com/workspace"), width: 6, submitOnChange: true, newLineAfter:true)
                              
                if(state.authTokenDate) {
                    paragraph( getFormat("text","$sSamsungIcon Token Expiration Date: ${state.authTokenDate}") )
                    input(name: "mainPage::refreshToken", type: "button", title: "Refresh Token", width: 3, style:"width:50%;", newLineAfter:true )
                }
                
                input(name: "pageMainPageAppLabel", type: "text", title: getFormat("text","$sHubitatIcon Change SmartApp Name:"), width: 6, submitOnChange: true, newLineAfter:true)
                input(name: "mainPage::changeName", type: "button", title: "Change Name", width: 3, style:"width:50%;", newLineAfter:true )
 
            }
            
        }
            
        section(menuHeader("HubiThings Device List")){            
            
            if (getSmartDevices() && state?.install) {
               
                def devicesTable = "<table style='width:100%;'>"
                devicesTable += "<tr><th>$sSamsungIcon SmartThings Device</th><th>$sSamsungIcon SmartThings Type</th><th>$sHubitatIcon Hubitat Device</th><th style='text-align:center;'>Status</th></tr>"
                getSmartDevices()?.items?.sort{ it.label }?.each { smartDevice -> 
                    def hubitatDevices = getSmartChildDevices(smartDevice.deviceId)
                    for (def i = 0; i ==0 || i < hubitatDevices.size(); i++) {
                        def deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${hubitatDevices[i]?.getId()}"                            
                        def deviceColor = hubitatDevices[i]?.getDataValue("statuscolor") ?: sColorLightGrey
                            
                        devicesTable += "<tr>"
                        devicesTable += "<td>${smartDevice.label}</td>"                  
                        devicesTable += "<td>${state?.install[smartDevice.deviceId]?.id?.join(', ')}</td>"
                        devicesTable += hubitatDevices[i] ? "<td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${hubitatDevices[i]?.label}</a></td>" : "<td></td>"
                        devicesTable += "<td style='text-align:center;'><div><span id='${hubitatDevices[i]?.deviceNetworkId}' style='background:${deviceColor};' class='dot'></span></div></td>"
                        devicesTable += "</tr>"
                    }
		        }                
                devicesTable +="</table>"
                paragraph("${devicesTable}")
                paragraph("<span style='color:${sColorDarkRed}' id='socketstatus'></span>")
                
                def html =  """<style>.dot{height:20px; width:20px; background:${sColorDarkBlue}; border-radius:50%; display:inline-block;}</style>"""
                    html += """<style>th,td{border-bottom:3px solid #ddd;}</style>"""
                    html += """<style>@media screen and (max-width:800px) { table th:nth-of-type(2),td:nth-of-type(2) { display: none; } }</style>"""
                    html += """<script>if(typeof websocket_start === 'undefined'){ window.websocket_start=true; console.log('websocket_start'); var ws = new WebSocket("ws://${location.hub.localIP}:80/eventsocket"); ws.onmessage=function(evt){ var e=JSON.parse(evt.data); if(e.installedAppId=="${app.getId()}") { updatedot(e); }}; ws.onclose=function(){ onclose(); delete websocket_start;};}</script>"""
                    html += """<script>function updatedot(evt) { var dt=JSON.parse(evt.descriptionText); if(dt.debug){console.log(evt);} if(evt.name=='statuscolor' && document.getElementById(dt.deviceNetworkId)){ document.getElementById(dt.deviceNetworkId).style.background = evt.value;}}</script>"""
                    html += """<script>function onclose() { console.log("Connection closed"); if(document.getElementById('socketstatus')){ document.getElementById('socketstatus').textContent = "Notice: Websocket closed. Please refresh page to restart.";}}</script>""" 
                
                paragraph( html )
            }
            
            input( name: "mainPage::list",         type: "button", width: 2, title: "Device List", style:"width:75%;" )
            input( name: "mainPage::description",  type: "button", width: 2, title: "Device Description", style:"width:75%;" )
            input( name: "mainPage::status",       type: "button", width: 2, title: "Device Status", style:"width:75%;" )
            input( name: "mainPage::health",       type: "button", width: 2, title: "Device Health", style:"width:75%;" )
            //input( name: "mainPage::test",         type: "button", width: 2, title: "Test Method", style:"width:75%;" )            
    	}
        
        section(menuHeader("HubiThings Device Creation and Control")){	
            href "pageCreateDevice", title: "Create HubiThings Device", description: "Click to show"
            //href "pageMirrorDevice", title: "Mirror HubiThings Device", description: "Click to show"
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
        }        
        
        section(menuHeader("Application Logging")) {
            input "appLogEnable", 'bool', title: "Enable debug logging", required: false, defaultValue: false, submitOnChange: true
            input "appTraceEnable", 'bool', title: "Enable trace logging", required: false, defaultValue: false, submitOnChange: true
        }
        
        displayFooter()
    }
}

def updateDeviceTableStatus(childDevice, color=sColorLightGrey) {
    logDebug "${app.getLabel()} executing 'updateDeviceTableStatus()' childDevice:'${childDevice?.getLabel()}'"
    color = isChildDeviceOnline(childDevice) ? color : sColorDarkRed
    childDevice?.updateDataValue("statuscolor", color)
    sendEvent(name:'statuscolor', value:color, descriptionText: JsonOutput.toJson([ deviceNetworkId:(childDevice?.deviceNetworkId), debug: appLogEnable ]))    
}

def pageCreateDevice(){
    
    def smartDevices = getSmartDevices()
    
    def smartDevicesSelect = []
    smartDevices?.items.each {    
        def device = [ "${it.deviceId}" : "${it.label} [${it.deviceId}]" ]
        smartDevicesSelect.add(device)   
    }

    dynamicPage(name: "pageCreateDevice", uninstall: false) {
        displayHeader()
        
        def smartAttributes = []
        smartDevices?.items?.find{it.deviceId == pageCreateDeviceSmartDevice}?.components.each { components ->
            components?.capabilities?.each { capabilities ->
                smartAttributes.add(capabilities.id)
            }
        }
        def smartDeviceType   = smartDevices?.items?.find{it.deviceId == pageCreateDeviceSmartDevice}?.deviceTypeName ?: (smartDevices?.items?.find{it.deviceId == pageCreateDeviceSmartDevice}?.name ?: "UNKNOWN")
        def smartCapabilities = smartAttributes?.sort()?.join(', ')
        def hubitatDeviceTypes = ["Virtual Switch", "Virtual Dimmer", "Virtual Contact Sensor", "Virtual Motion Sensor", "Virtual Temperature Sensor", "Virtual Humidity Sensor", "Virtual Presence", "Generic Component Dimmer"]
        app.updateSetting( "pageCreateDeviceLabel", smartDevices?.items?.find{it.deviceId == pageCreateDeviceSmartDevice}?.label ?: "" )

        section(menuHeader("Create HubiThings Device")+"$sHubitatIconStatic $sSamsungIconStatic") {
 
            input(name: "pageCreateDeviceSmartDevice", type: "enum", title: "$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 6)
            paragraph("Device Type: ${smartDeviceType ?: ""}<br>Capabilities: ${smartCapabilities ?: ""}")
            paragraph( getFormat("line"))
            
            input(name: "pageCreateDeviceType", type: "enum", title: "$sHubitatIcon Create Hubitat Device Type:", description: "Choose a Hubitat device type", options: hubitatDeviceTypes, required: false, submitOnChange:true, width: 6, newLineAfter:true)
            input(name: "pageCreateDeviceLabel", type: "text", title: "$sHubitatIcon Create Hubitat Device Label:", submitOnChange: false, width: 6)
            paragraph( getFormat("line"))
            
            if (pageCreateDeviceSmartDevice && pageCreateDeviceType) {
                href "pageAddDevice", title: "Click to create Hubitat device", description: "Device will be created based on the parameters above"            
                paragraph( getFormat("line"))
            }
            
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
        }

        childDevicesSection()
    }
}

def pageMirrorDevice(){
    
    def smartDevices = getSmartDevices()
    
    def smartDevicesSelect = []
    smartDevices?.items.each {    
        def device = [ "${it.deviceId}" : "${it.label} [${it.deviceId}]" ]
        smartDevicesSelect.add(device)   
    }

    dynamicPage(name: "pageMirrorDevice", uninstall: false) {
        displayHeader()
        
        def smartAttributes = []
        smartDevices?.items?.find{it.deviceId == pageMirrorDeviceSmartDevice}?.components.each { components ->
            components?.capabilities?.each { capabilities ->
                smartAttributes.add(capabilities.id)
            }
        }
        def smartDeviceType   = smartDevices?.items?.find{it.deviceId == pageMirrorDeviceSmartDevice}?.deviceTypeName ?: (smartDevices?.items?.find{it.deviceId == pageMirrorDeviceSmartDevice}?.name ?: "UNKNOWN")
        def smartCapabilities = smartAttributes?.sort()?.join(', ')
      
        section(menuHeader("Mirror HubiThings Device")+"$sHubitatIconStatic $sSamsungIconStatic") {
            input(name: "pageMirrorDeviceSmartDevice", type: "enum", title: "$sSamsungIcon Select SmartThings Device:", description: "Choose a SmartThings device", options: smartDevicesSelect, required: false, submitOnChange:true, width: 4)
            paragraph("Device Type: ${smartDeviceType ?: ""}<br>Capabilities: ${smartCapabilities ?: ""}")
            paragraph( getFormat("line"))
            
            input(name: "pageMirrorDeviceHubitatDevice", type: "capability.*", title: "Hubitat Device:", description: "Choose a Hubitat device", multiple: true, submitOnChange: true)
            paragraph( getFormat("line"))
            
            if (pageMirrorDeviceSmartDevice && pageMirrorDeviceHubitatDevice) {
                href "pageAddMirrorDevice", title: "Click to mirror Hubitat device", description: "Device will be mirrored based on the parameters above"
                paragraph( getFormat("line"))
            }            
            
            href "pageConfigureDevice", title: "Configure HubiThings Rules", description: "Click to show"
            href "pageDeleteDevice", title: "Delete HubiThings Device", description: "Click to show"
        }

        childDevicesSection()
    }
}

def pageAddMirrorDevice() {
    
    def smartDevices = getSmartDevices()
    
    dynamicPage(name: "pageAddMirrorDevice", uninstall: false) {
        displayHeader()
        
        def label  = (smartDevices?.items?.find{it?.deviceId == pageMirrorDeviceSmartDevice}?.label)
        
        def response
        if (getChildDevices().find{it.label == label}){
            response = "There is already a device labled '${label}'. Go back and change the label name."
        }
        else {
            response = !pageMirrorDeviceSmartDevice || !pageMirrorDeviceHubitatDevice ? "Device label name or type not specified. Go back and enter the device information" : "MAKE DONUTS" // addChildDevices()
        }
        section (menuHeader("Mirror HubiThings Device")) {paragraph response}
    }
}

def childDevicesSection(){
    
    def childDeviceList = "<span><table style='width:100%;'>"
    childDeviceList += "<tr><th>$sHubitatIcon Hubitat Device</th><th>$sHubitatIcon Hubitat Type</th></tr>"
    getChildDevices().sort{ it.label }.each {
        //example: "http://192.168.1.160/device/edit/1430"
        def deviceUrl = "http://${location.hub.getDataValue("localIP")}/device/edit/${it.getId()}"
        childDeviceList += "<tr><td><a href='${deviceUrl}' target='_blank' rel='noopener noreferrer'>${it.label}</a></td><td>${it.typeName}</td></tr>"
    }
    childDeviceList +="</table>"
    
    if (getChildDevices().size){        
        section(menuHeader("Hubitat Devices")) {
            paragraph( childDeviceList )
            paragraph("<style>th,td{border-bottom:3px solid #ddd;}</style>")            
        }
    }
}

def pageAddDevice() {
    
    def smartDevices = getSmartDevices()
    
    dynamicPage(name: "pageAddDevice", uninstall: false) {
        displayHeader()
        
        def label  = pageCreateDeviceLabel

        def response
        if (getChildDevices().find{it.label == label}){
            response = "There is already a device labled '${label}'. Go back and change the label name."
        }
        else {
            response = !pageCreateDeviceSmartDevice || !pageCreateDeviceType ? "Device label name or type not specified. Go back and enter the device information" :  addChildDevices()
        }
        section (menuHeader("Create Devices")) {paragraph response}
    }
}

def addChildDevices(){
    
    def smartDevices = getSmartDevices()
    
    def deviceNetworkId = "${UUID.randomUUID().toString()}"
    def nameSpace = "hubitat"
    def label = pageCreateDeviceLabel
    def name  = (smartDevices?.items?.find{it.deviceId == pageCreateDeviceSmartDevice}?.name)
    def deviceId = pageCreateDeviceSmartDevice
    
    def response = "A '${pageCreateDeviceType}' named '${label}' could not be created. Ensure you have the correct Hubitat Drivers Code."
    try {
        def childDevice = addChildDevice(nameSpace, pageCreateDeviceType, deviceNetworkId, null, [name: name, label: label, completedSetup: true])
        // the deviceId makes this a hubiThing
        childDevice?.updateDataValue("deviceId", deviceId)
        smartDeviceRefresh(childDevice)

        logInfo "${app.getLabel()} created device '${childDevice.label}' with network id: ${childDevice.deviceNetworkId}"            
        
        response  = "A '${pageCreateDeviceType}' named '${childDevice.label}' has been created.\n\n"
        response += "Commands: ${childDevice.getSupportedCommands()?.sort()?.join(', ')}\n"
        response += "Capabilities: ${childDevice.getCapabilities()?.sort()?.join(', ')}\n"
        response += "Attributes: ${childDevice.getSupportedAttributes()?.sort()?.join(', ')}"
        
    } catch (e) {
        logWarn "Error creating device: ${e}"        
    }
    return response   
}

def smartDeviceRefresh(childDevice) {
    
    def deviceId = childDevice?.getDataValue("deviceId")    
    if(deviceId) {
        // for now, subscribe to everything. need todo this better.
        childDevice?.getSupportedAttributes()?.each { attributes ->
            logInfo "${childDevice.label} subscribed to $attributes"
            subscribe(childDevice, "${attributes}", deviceTriggerHandler)
        }
        getDeviceHealth(deviceId)
        getDeviceDescription(deviceId)
        getDeviceStatus(deviceId)
    } 
    else if(childDevice) {
        unsubscribe(childDevice)
    }
}

def pageDeleteDevice(){
    dynamicPage(name: "pageDeleteDevice", uninstall: false) {
        displayHeader()

        section(menuHeader("Delete HubiThings Device")+"$sHubitatIconStatic $sSamsungIconStatic") {
            def childDeviceList = []
            getChildDevices().sort{ it.label }.sort{ it.typeName }.each {
                childDeviceList.add("${it.label}")
            }
           input(name: "pageDeleteDeviceName", type: "enum",  title: "Delete Hubitat Device:", description: "Choose a Hubitat device", multiple: false, options: childDeviceList, submitOnChange: true)
           if (pageDeleteDeviceName) href "pageSubDevice", title: "Click to delete device", description: "Device '$pageDeleteDeviceName' will be deleted"            
           //input "btnDelDevice", "button", title: "<b style='color:Red'>Delete</b>", width: 3
        }
        
        childDevicesSection()
    }
}

def pageSubDevice() {
    dynamicPage(name: "pageSubDevice", uninstall: false) {
        displayHeader()
        
        def response = "Error attempting to delete device '$pageDeleteDeviceName'."
        getChildDevices().find{ 
            if(it.label == pageDeleteDeviceName) {
                try {
                    deleteChildDevice(it.deviceNetworkId)
                    app.removeSetting("pageDeleteDeviceName")
                    logInfo "${app.getLabel()} deleted device ${it.label} with network id: ${it.deviceNetworkId}"
                    response = "The device labeled '${it.label}' was deleted."
                } catch (e) {
                    logWarn "Error deleting device: ${e}"
                }
            }
        }
        section (menuHeader("Delete Devices")) {paragraph response}
    }
}

def updateRuleList(action, type) {
    def trigger = g_mPageConfigureDevice?.hubitatAttribute
    def command = g_mPageConfigureDevice?.smartCommand
    if(type!='hubitatTrigger') {
        trigger = g_mPageConfigureDevice?.smartAttribute
        command = g_mPageConfigureDevice?.hubitatCommand
    }
    def triggerKey = trigger?.keySet()?.getAt(0)
    def commandKey = command?.keySet()?.getAt(0)
    logDebug "${app.getLabel()} executing 'updateRuleList()' hubitatDevice:'${pageConfigureDeviceHubitatDevice}' trigger:'${triggerKey}' command:'${commandKey}' action:'${action}'" 

    def childDeviceRules = getChildDeviceDataJson(pageConfigureDeviceHubitatDevice, "rules") ?: []
    def allowDuplicateAttribute = pageConfigureDeviceAllowDuplicateAttribute
    app.updateSetting("pageConfigureDeviceAllowDuplicateAttribute", false)
  
    if(action=='delete') {
        childDeviceRules?.removeAll{ it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey && it?.command?.keySet()?.getAt(0)==commandKey }
        def attribute = trigger?.values()?.getAt(0)?.name
        logInfo "Detaching '$pageConfigureDeviceHubitatDevice' to attribute:'$attribute'"
        unsubscribe(pageConfigureDeviceHubitatDevice, attribute)
    }
    else if(triggerKey && commandKey && !childDeviceRules?.find{ it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey && it?.command?.keySet()?.getAt(0)==commandKey }) {
        def newRule = [ trigger:trigger, command:command, type:type ]

        if(action=='replace'){
            childDeviceRules = childDeviceRules?.collect{
                return  (it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey) ? newRule : it
            }
        }
        
        if(action=='store' && (!childDeviceRules?.find{ it?.type==type && it?.trigger?.keySet()?.getAt(0)==triggerKey } || allowDuplicateAttribute)) {
            if(type=='hubitatTrigger') {
                def attribute = trigger?.values()?.getAt(0)?.name
                logInfo "Attaching '$pageConfigureDeviceHubitatDevice' to attribute:'$attribute'"
                subscribe(pageConfigureDeviceHubitatDevice, attribute, deviceTriggerHandler)
            }
            childDeviceRules.add(newRule)
        }
    }
  
    pageConfigureDeviceHubitatDevice.updateDataValue("rules", JsonOutput.toJson(childDeviceRules))
}

def childDevicesRuleSection(){
    
    def childDeviceRules = getChildDeviceDataJson(pageConfigureDeviceHubitatDevice, "rules")
    
    def childDeviceRulesList = "<span><table style='width:100%;'>"
    childDeviceRulesList += "<tr><th>Trigger</th><th>Action</th></tr>"
    childDeviceRules?.sort{ it?.type }?.each { rule ->  
        childDeviceRulesList += "<tr><td>${rule?.type=='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.trigger?.keySet()?.getAt(0)}</td><td>${rule?.type!='hubitatTrigger' ? sHubitatIcon : sSamsungIcon} ${rule?.command?.keySet()?.getAt(0)}</td></tr>"
    }
    childDeviceRulesList +="</table>"
    
    if (childDeviceRules?.size){        
        section(menuHeader("[ $pageConfigureDeviceHubitatDevice ] Active Rules")) {    
            paragraph( childDeviceRulesList )
            paragraph("<style>th,td{border-bottom:3px solid #ddd;}</style>")
        }
    }
}

def deviceTriggerHandler(event) {    
    logDebug "${app.getLabel()} executing 'deviceTriggerHandler()' displayName:'${event?.getDisplayName()}' name:'${event?.name}' value:'${event?.value}' unit:'${event?.unit}'"
    //event.properties.each { logInfo "$it.key -> $it.value" }
    def childDevice = event?.getDevice() 
    def deviceId = event?.getDevice()?.getDataValue("deviceId")    
    
    def childDeviceRules = getChildDeviceDataJson(childDevice, "rules")
    def childDeviceEvent = getChildDeviceDataJson(childDevice, "event")
    childDeviceRules?.findAll{ it.type == "hubitatTrigger" }?.each { rule ->            
        def trigger = rule?.trigger.values()?.getAt(0) ?: []
        def command = rule?.command.values()?.getAt(0) ?: []          
           
        // simple enum case
        if(event.name==trigger?.name && event.value==trigger?.value ) {
            if(childDeviceEvent?.attribute==trigger?.name && childDeviceEvent?.value?.toString()==event.value?.toString()) {
                logDebug "EVENT CACHE BLOCKED: attribute:${childDeviceEvent?.attribute} value:${childDeviceEvent?.value.toString()}"
            }
            else {  
                logInfo "Sending SmartThings '${childDevice?.getLabel()}' enum command:${command?.name}()"                
                commandDevice(deviceId, command?.capability, command?.name)
            }
        }
        // non-enum case https://developer-preview.smartthings.com/docs/devices/capabilities/capabilities
        else if(event.name==trigger?.name && !trigger?.value) {
            if(childDeviceEvent?.attribute==trigger?.name && childDeviceEvent?.value?.toString()==event.value?.toString()) {
                logDebug "EVENT CACHE BLOCKED: attribute:${childDeviceEvent?.attribute} value:${childDeviceEvent?.value.toString()}"
            }
            else {
                def type = command?.arguments?.getAt(0)?.schema?.type?.toLowerCase()
                logInfo "Sending SmartThings '${childDevice?.getLabel()}' $type command:${command?.name}(${event?.value})"
                
                switch(type) {
                    case 'integer': // A whole number. Limits can be defined to constrain the range of possible values.
                        commandDevice(deviceId, command?.capability, command?.name, [ event?.value.toInteger() ])
                        break
                    case 'number':  // A number that can have fractional values. Limits can be defined to constrain the range of possible values.
                        commandDevice(deviceId, command?.capability, command?.name, [ event?.value.toFloat() ])
                        break
                    case 'boolean': // Either true or false
                        commandDevice(deviceId, command?.capability, command?.name, [ event?.value.toBoolean() ])
                        break
                    case 'object':  // A map of name value pairs, where the values can be of different types.
                        def map = new JsonSlurper().parseText(event?.value)
                        commandDevice(deviceId, command?.capability, command?.name, map)
                        break
                    case 'array':   // A list of values of a single type.
                        def list = new JsonSlurper().parseText(event?.value)
                        commandDevice(deviceId, command?.capability, command?.name, list)
                        break
                    default:
                        commandDevice(deviceId, command?.capability, command?.name, [ event?.value ])
                        break
                }
            }
        }      
    }            
}

def smartTriggerHandler(childDevice, event) {
    logDebug "${app.getLabel()} executing 'smartTriggerHandler()' childDevice:'${childDevice?.getLabel()}'"
    def response = [statusCode:iHttpError]    
    //logInfo JsonOutput.toJson(event)
    
    def childDeviceRules = getChildDeviceDataJson(childDevice, "rules")
    event?.each { capability, attributes ->
        attributes.each{ attribute, value ->
            logTrace "smartEvent: capability:'$capability' attribute:'$attribute' value:'$value'" 
            childDeviceRules?.findAll{ it.type == "smartTrigger" }?.each { rule -> 
                def trigger = rule?.trigger.values()?.getAt(0) ?: []
                def command = rule?.command.values()?.getAt(0) ?: []
                    
                // simple enum case
                if(attribute==trigger?.attribute && value?.value==trigger?.value ) {    
                    logInfo "Executing Hubitat '${childDevice?.getLabel()}' enum command:${command?.name}()"                  
                    def args = []
                    def method = command?.name
                    if(childDevice.hasCommand(method)) {
                        childDevice."$method"(*args)
                    }
                }
                // non-enum case
                else if(attribute==trigger?.attribute && !trigger?.value) {
                    logInfo "Executing Hubitat '${childDevice?.getLabel()}' command:${command?.name}(${value?.value})"
                    def args = [value.value]
                    def method = command?.name
                    if(childDevice.hasCommand(method)) {
                        childDevice."$method"(*args)
                    }
                }
            }
        }
        response.statusCode = iHttpSuccess
    }
    return [statusCode:response.statusCode]
}

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
    smartDeviceRefresh(device)
}

def pageConfigureDevice() {
    
    dynamicPage(name: "pageConfigureDevice", uninstall: false) {
        displayHeader()
       
        section(menuHeader("Configure HubiThings Rules")+"$sHubitatIconStatic $sSamsungIconStatic") {            
                        
            input(name: "pageConfigureDeviceHubitatDevice", type: "capability.*", title: "Hubitat Device:", description: "Choose a Hubitat device", multiple: false, submitOnChange: true)
            if(pageConfigureDeviceShowDetail) {
                def hubitatDescription = ""
                hubitatDescription += "Device Type: ${pageConfigureDeviceHubitatDevice?.getTypeName() ?: ""}\n"
                hubitatDescription += "Capabilities: ${pageConfigureDeviceHubitatDevice?.getCapabilities()?.sort()?.join(', ')}\n"
                hubitatDescription += "Attributes: ${pageConfigureDeviceHubitatDevice?.getSupportedAttributes()?.collect { it.toString() }?.sort()?.join(', ')}\n"
                hubitatDescription += "Commands: ${pageConfigureDeviceHubitatDevice?.getSupportedCommands()?.sort()?.join(', ')}"
                paragraph( hubitatDescription )
                input(name: "pageConfigureDevice::refreshDevice",  type: "button", title: "Refresh", width: 2, style:"width:75%;")
                input(name: "pageConfigureDevice::clearDeviceRules",  type: "button", title: "Clear Rules", width: 2, style:"width:75%;")
                paragraph( getFormat("line"))
            }
            
            def hubitatAttributeOptions = getHubitatAttributeOptions(pageConfigureDeviceHubitatDevice)                       
            def smartCommandOptions = getSmartCommandOptions(pageConfigureDeviceHubitatDevice)
            
            input(name: "hubitatAttribute", type: "enum", title: "$sHubitatIcon If Hubitat Attribute <b>TRIGGER</b> changes:", description: "Choose a Hubitat Attribute", options: hubitatAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "smartCommand", type: "enum", title: "$sSamsungIcon Then <b>ACTION</b> SmartThings Command:", description: "Choose a SmartThings Command", options: smartCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            //input( name: "pageConfigureDevice::hubitatAttributeReplace",  type: "button", title: "Replace Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::hubitatAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::hubitatAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")

            if(pageConfigureDeviceShowDetail) {
                paragraph( hubitatAttribute ? "$sHubitatIcon $hubitatAttribute : ${JsonOutput.toJson(hubitatAttributeOptions?.find{ key,value -> key==hubitatAttribute }?.value)}" : "$sHubitatIcon No Selection" )
                paragraph( smartCommand ? "$sSamsungIcon $smartCommand : ${JsonOutput.toJson(smartCommandOptions?.find{ key,value -> key==smartCommand }?.value)}" : "$sSamsungIcon No Selection" )
            }
            paragraph( getFormat("line"))
            
            def smartAttributeOptions = getSmartAttributeOptions(pageConfigureDeviceHubitatDevice)         
            def hubitatCommandOptions = getHubitatCommandOptions(pageConfigureDeviceHubitatDevice)
            
            input(name: "smartAttribute", type: "enum", title: "$sSamsungIcon If SmartThings Attribute <b>TRIGGER</b> changes:", description: "Choose a SmartThings Attribute", options: smartAttributeOptions.keySet().sort(), required: false, submitOnChange:true, width: 4)
            input(name: "hubitatCommand", type: "enum", title: "$sHubitatIcon Then <b>ACTION</b> Hubitat Command${pageConfigureDeviceAllowActionAttribute?'/Attribute':''}:", description: "Choose a Hubitat Command", options: hubitatCommandOptions.keySet().sort(), required: false, submitOnChange:true, width: 4, newLineAfter:true)
            //input( name: "pageConfigureDevice::smartAttributeReplace",  type: "button", title: "Replace Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::smartAttributeStore",  type: "button", title: "Store Rule", width: 2, style:"width:75%;")
            input(name: "pageConfigureDevice::smartAttributeDelete",  type: "button", title: "Delete Rule", width: 2, style:"width:75%;")
            
            if(pageConfigureDeviceShowDetail) {
                paragraph( smartAttribute ? "$sSamsungIcon $smartAttribute : ${JsonOutput.toJson(smartAttributeOptions?.find{ key,value -> key==smartAttribute }?.value)}" : "$sSamsungIcon No Selection" )
                paragraph( hubitatCommand ? "$sHubitatIcon $hubitatCommand : ${JsonOutput.toJson(hubitatCommandOptions?.find{ key,value -> key==hubitatCommand }?.value)}" : "$sHubitatIcon No Selection" )
            }
            paragraph( getFormat("line"))     
            
            input(name: "pageConfigureDeviceAllowDuplicateAttribute", type: "bool", title: "Allow duplicate Attribute <b>TRIGGER</b>", defaultValue: false, submitOnChange: true, width: 3)
            input(name: "pageConfigureDeviceAllowActionAttribute", type: "bool", title: "Allow <b>ACTION</b> to update Hubitat Attributes", defaultValue: false, submitOnChange: true, width: 3)
            input(name: "pageConfigureDeviceShowDetail", type: "bool", title: "Show detail for attributes and commands", defaultValue: false, submitOnChange: true, width: 3)
            
            // gather these all up so when user presses store - it uses this structure.
            g_mPageConfigureDevice['hubitatAttribute'] = ["$hubitatAttribute": hubitatAttributeOptions?.get(hubitatAttribute)] ?: null
            g_mPageConfigureDevice['smartAttribute']   = ["$smartAttribute": smartAttributeOptions?.get(smartAttribute)] ?: null
            g_mPageConfigureDevice['smartCommand']     = ["$smartCommand": smartCommandOptions?.get(smartCommand)] ?: null
            g_mPageConfigureDevice['hubitatCommand']   = ["$hubitatCommand": hubitatCommandOptions?.get(hubitatCommand)] ?: null
            
            input( name: "mainPage::test",         type: "button", width: 2, title: "Test Method" ) 
        }
        
        childDevicesRuleSection()
    }
}

def getHubitatCommandOptions(childDevice) {    
            
    def hubitatCommandOptions = [:]
    childDevice?.getSupportedCommands()?.each{ command ->
        //{"arguments":["NUMBER","NUMBER"],"parameters":[{"name":"Level*","description":"Level to set (0 to 100)","type":"NUMBER","constraints":["NUMBER"]},{"name":"Duration","description":"Transition duration in seconds","type":"NUMBER","constraints":["NUMBER"]}],"capability":true,"id":10,"version":1,"name":"setLevel"}
        //{"arguments":null,"parameters":null,"capability":true,"id":1,"version":1,"name":"on"}
        //{"arguments":["NUMBER"],"parameters":[{"type":"NUMBER"}],"capability":false,"id":234,"version":1,"name":"setTemperature"}
        commandJson = new JsonSlurper().parseText(JsonOutput.toJson(command)) //could not figure out how to convert command object to json. this works.
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
        hubitatCommandOptions += getHubitatAttributeOptions(childDevice)
    }
    return hubitatCommandOptions
}

def getHubitatAttributeOptions(childDevice) {

    def hubitatAttributeOptions = [:]
    childDevice?.getSupportedAttributes()?.each{ attribute ->
        //{"dataType":"NUMBER","values":null,"possibleValueJson":"null","capability":true,"id":12,"version":1,"possibleValues":null,"deviceTypeId":60,"name":"level"}
        //{"dataType":"ENUM","values":["on","off"],"possibleValueJson":"[\"on\",\"off\"]","capability":true,"id":4,"version":1,"possibleValues":["on","off"],"deviceTypeId":60,"name":"switch"}   
        //{"dataType":"NUMBER","values":null,"possibleValueJson":"null","capability":true,"id":2,"version":1,"possibleValues":null,"deviceTypeId":68,"name":"temperature"}
        attributeJson = new JsonSlurper().parseText(JsonOutput.toJson(attribute))
        attributeJson.remove('possibleValueJson')
        attributeJson.remove('possibleValues')
        attributeJson.remove('id')
        attributeJson.remove('version')
        attributeJson.remove('deviceTypeId')
        if(attributeJson?.dataType=="ENUM") {
            attributeJson?.values?.each{ enumValue ->
                def label = "attribute: ${attributeJson?.name}.${enumValue}"
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

def getSmartCommandOptions(childDevice) {
            
    def smartCommandOptions = [:]
    def capabilities = getChildDeviceDataJson(childDevice, "capabilities")
    capabilities?.components?.each{ capability -> 
        capability?.commands?.each{ command, value ->
            //{"name":"setLevel","arguments":[{"name":"level","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}},{"name":"rate","optional":true,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"capability":"switchLevel"}
            //{"name":"on","arguments":[],"capability":"switch"}
            //{"name":"setvTemp","arguments":[{"name":"temp","optional":false,"schema":{"type":"number","minimum":-460,"maximum":10000}}],"capability":"partyvoice23922.vtempset"}
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

def getSmartAttributeOptions(childDevice) {

    def smartAttributeOptions = [:]
    def capabilities = getChildDeviceDataJson(childDevice, "capabilities")
    capabilities?.components?.each{ capability ->
        capability?.attributes?.each{ attribute, value -> def schema = value?.schema ?: [:]
            //{"schema":{"title":"IntegerPercent","type":"object","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"]},"setter":"setLevel","enumCommands":[],"capability":"switchLevel"}
            //{"schema":{"type":"object","properties":{"value":{"title":"SwitchState","type":"string","enum":["on","off"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[{"command":"on","value":"on"},{"command":"off","value":"off"}],"capability":"switch"}
            //{"schema":{"type":"object","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"]},"enumCommands":[],"capability":"temperatureMeasurement"}
            schema["capability"] = capability.id
            schema["attribute"] = attribute
            schema?.remove('type')
            if(schema?.properties?.value?.enum) {
                schema?.properties?.value?.enum?.each{ enumValue ->
                    def label = "attribute: ${attribute}.${enumValue}"
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
        smartAttributeOptions["attribute: healthStatus.offline"] = new JsonSlurper().parseText("""{"schema":{"type":"object","properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","value":"offline","label":"attribute: healthStatus.offline "}""")
        smartAttributeOptions["attribute: healthStatus.online"] =  new JsonSlurper().parseText("""{"schema":{"type":"object","properties":{"value":{"title":"HealthState","type":"string","enum":["offline","online"]}},"additionalProperties":false,"required":["value"]},"enumCommands":[],"capability":"healthCheck","value":"online","label":"attribute: healthStatus.online "}""")
    }
    return smartAttributeOptions
}

def smartStatusHandler(childDevice, status) {
    logDebug "${app.getLabel()} executing 'smartStatusHandler()' childDevice:'${childDevice?.getLabel()}'"
    def response = [statusCode:iHttpError]
    
    childDevice.updateDataValue("status", JsonOutput.toJson(status))
    status?.components?.main?.each { capability, attributes ->
        response.statusCode = smartTriggerHandler(childDevice, [ "$capability":attributes ]).statusCode
    }
    return [statusCode:response.statusCode]
}

def smartEventHandler(childDevice, deviceEvent){
    logDebug "${app.getLabel()} executing 'smartEventHandler()' childDevice:'${childDevice.getLabel()}'"
    def response = [statusCode:iHttpError]    

    childDevice.updateDataValue("event", JsonOutput.toJson(deviceEvent)) 
    try {
        // events do not carry units. so get it from status. yeah smartthings is great!
        def unit = getChildDeviceDataJson(childDevice, "status")?.components[deviceEvent.componentId][deviceEvent.capability][deviceEvent.attribute]?.unit
        // status    {"switchLevel":             {"level":                  {"value":30,                "unit":"%",   "timestamp":"2022-09-07T21:16:59.576Z" }}}
        def event = [ (deviceEvent.capability): [ (deviceEvent.attribute): [ value:(deviceEvent.value), unit:(deviceEvent?.unit ?: unit), timestamp: null ]]]
        logTrace JsonOutput.toJson(event)
        response.statusCode = smartTriggerHandler(childDevice, event).statusCode
    } catch (e) {
        logWarn "${app.getLabel()} smartEventHandler error: $e : $deviceEvent"
    }
    return [statusCode:response.statusCode]
}

def smartHealthHandler(childDevice, healthEvent){
    logDebug "${app.getLabel()} executing 'smartHealthHandler()' childDevice:'${childDevice.getLabel()}'"
    def response = [statusCode:iHttpError]

    childDevice.updateDataValue("health", JsonOutput.toJson(healthEvent))
    try {
        //{"deviceId":"2c80c1d7-d05e-430a-9ddb-1630ee457afb","state":"ONLINE","lastUpdatedDate":"2022-09-07T16:47:06.859Z"}
        // status    {"switchLevel":{"level":       {"value":30,                             "unit":"","timestamp":"2022-09-07T21:16:59.576Z" }}}
        def event = [ healthCheck: [ healthStatus: [ value:(healthEvent.state.toLowerCase()), timestamp: healthEvent?.lastUpdatedDate ]]]
        logTrace JsonOutput.toJson(event)
        response.statusCode = smartTriggerHandler(childDevice, event).statusCode
    } catch (e) {
        logWarn "${app.getLabel()} smartHealthHandler error: $e : $healthEvent"
    }    
    return [statusCode:response.statusCode]
}

def smartDescriptionHandler(childDevice, descriptionEvent){
    logDebug "${app.getLabel()} executing 'smartDescriptionHandler()' childDevice:'${childDevice.getLabel()}'"
    def response = [statusCode:iHttpError]

    childDevice.updateDataValue("description", JsonOutput.toJson(descriptionEvent))
    try {
        descriptionEvent?.components.each { components ->
            components?.capabilities.each { capabilities ->
                if (hasCapability(childDevice, capabilities.id, capabilities.version) == [:]) {
                    getCapability(childDevice.getDataValue("deviceId"), capabilities.id, capabilities.version)                  
                }
            }
        }
        response.statusCode = iHttpSuccess       
    } catch (e) {
        logWarn "${app.getLabel()} smartDescriptionHandler error: $e : $descriptionEvent"
    }    
    return [statusCode:response.statusCode]
}

mappings { 
    path("/webhook") { action: [ POST: "webhookPost", GET: "webhookGet"] }
}

def installed() {
    initialize()
}

def updated()
{
    initialize()
}

def initialize() {
    logInfo "${app.getLabel()} executing 'initialize()'"
    unsubscribe()
    unschedule()
}

def uninstalled()
{
    unsubscribe()
    unschedule()
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
                            deviceList()
                            break
                        case "description":
                            allDeviceDescription()
                            break                        
                        case "status":
                            allDeviceStatus()
                            break
                        case "health":
                            allDeviceHealth()
                            break
                        case "test":
                            testit()
                            break
                        case "refreshToken":
                            handleTokenRefresh()                       
                            break
                        case "changeName":
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
                            pageConfigureDeviceHubitatDevice?.removeDataValue("rules")
                            unsubscribe(pageConfigureDeviceHubitatDevice)
                            break
                        case "refreshDevice":
                            smartDeviceRefresh(pageConfigureDeviceHubitatDevice)
                            break
                    }
                    break                    
                default:
                    logInfo "Not supported"
            }
        }
    }    
}

def testit() {

    logInfo "TESTIT"
    
    return
    
    //logInfo JsonOutput.toJson(g_mPageConfigureDevice)
    
    
    logInfo getCommonTimeFormat()
    logInfo getCommonTimeFormat("2022-09-17T01:38:31.653Z")
    logInfo getCommonTimeFormat("2022-09-16T12:35:40.104Z")
    logInfo getCommonTimeFormat(null)
    
    def addthis = "86399".toInteger()
    Date expirationDate = new Date(new Date().toInstant().toEpochMilli() + (addthis * 1000))
    logInfo expirationDate.format("YYYY-MM-dd h:mm:ss a z")
 
    getCapability(null, "partyvoice23922.vtempset")
    getCapability(null, "switch")
}

def getCommonTimeFormat(time=null) {
    def response
    if (time) {
        time = time+" UTC"
        response = (new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z' Z", time)).format("YYYY-MM-dd h:mm:ss a z")
    }
    else {
        response = (new Date()).format("YYYY-MM-dd h:mm:ss a z") 
    }
}

def setChildDeviceDataJson(childDevice, key, value) {
    def response = false
    try {
        childDevice?.updateDataValue(key, JsonOutput.toJson(value))
        response = true
    } catch (e) {
        logWarn "${app.getLabel()} setChildDeviceData $childDevice $key error: $e"
    }
    return response
}

def getChildDeviceDataJson(childDevice, key) {
    def response = null
    try {
        def value = childDevice?.getDataValue(key)
        if (value) {                
            response = new JsonSlurper().parseText(value)
        }
    } catch (e) {
        logWarn "${app.getLabel()} getChildDeviceDataJson $childDevice $key error: $e"
    }
    return response
}

def hasCapability(childDevice, capabilityId, capabilityVersion="1") {
    def reponse = [:]
    def capability = getChildDeviceDataJson(childDevice, "capabilities")
    if (capability?.components?.find { components -> components?.id == capabilityId && components?.version == capabilityVersion  }) {
        logDebug "Capability ${capabilityId} is cached"
        reponse = capability
    }
    return reponse
}

def isChildDeviceOnline(childDevice) {
    return (getChildDeviceDataJson(childDevice, "health")?.state?.toLowerCase() != "offline")
}

def getSmartChildDevices(deviceId) {
    return getChildDevices()?.findAll{ childDevice -> childDevice?.getDataValue("deviceId") == deviceId }
}

def allDeviceHealth() {
    logInfo "${app.getLabel()} refreshing all devices health"
    runIn(1, getAllDeviceHealth)
    runEvery10Minutes(getAllDeviceHealth)
}

def getAllDeviceHealth() {
    logDebug "executing 'getAllDeviceHealth()'"
    state?.install?.keySet().each { deviceId ->
        if ( getSmartChildDevices(deviceId) ) { //only ping devices mirrored
            getDeviceHealth(deviceId)
            pauseExecution(250) // no need to hammer ST
        }
    }
}

def getDeviceHealth(deviceId) {
    logDebug "executing 'getDeviceHealth($deviceId)'"

	def data = [
        uri: sURI,
        path: "/devices/${deviceId}/health",
		method: "getDeviceHealth",
        authToken: state?.authToken,
        deviceId: deviceId
		]
	asyncHttpGet("asyncHttpGetCallback", data)
}

def allDeviceDescription() {
    logInfo "${app.getLabel()} refreshing all devices descriptions"
    runIn(1, getAllDeviceDescription)
    runEvery3Hours(getAllDeviceDescription)
}

def getAllDeviceDescription() {
    logDebug "executing 'getAllDeviceDescription()'"
    state?.install?.keySet().each { deviceId ->
        if ( getSmartChildDevices(deviceId) ) { //only ping devices mirrored
            getDeviceDescription(deviceId)
            pauseExecution(250) // no need to hammer ST
        }
    }
}

def getDeviceDescription(deviceId) {
    logDebug "executing 'getDeviceDescription($deviceId)'"

	def data = [
        uri: sURI,
        path: "/devices/${deviceId}",
		method: "getDeviceDescription",
        authToken: state?.authToken,
        deviceId: deviceId
		]
	asyncHttpGet("asyncHttpGetCallback", data)
}

def getCapability(deviceId, capabilityId, capabilityVersion="1") {
    logDebug "executing 'getCapability()'"
    //https://api.smartthings.com/v1/capabilities/{capabilityId}/{capabilityVersion}  
	def data = [
        uri: sURI,
		path: "/capabilities/${capabilityId}/${capabilityVersion}",
		method: "getCapability",
        authToken: state?.authToken,
        deviceId: deviceId
		]
	asyncHttpGet("asyncHttpGetCallback", data)
}

def allDeviceStatus() {
    logInfo "${app.getLabel()} refreshing all devices status"
    unschedule("getAllDeviceStatus")
    runIn(1, getAllDeviceStatus)    
    //runEvery3Hours(getAllDeviceStatus)
}

def getAllDeviceStatus() {
    logDebug "executing 'getAllDeviceStatus()'"    
    state?.install?.keySet().each { deviceId ->
        if ( getSmartChildDevices(deviceId) ) { //only ping devices mirrored
            getDeviceStatus(deviceId)
            pauseExecution(250) // no need to hammer ST
        }
    }
}

def getDeviceStatus(deviceId) {
    logDebug "executing 'getDeviceStatus($deviceId)'"

	def data = [
        uri: sURI,
        path: "/devices/${deviceId}/status",
		method: "getDeviceStatus",
        authToken: state?.authToken,
        deviceId: deviceId
		]
	asyncHttpGet("asyncHttpGetCallback", data)
}

def deviceList() {
    runIn(1, getDeviceList)    
}

def getDeviceList() {
    logDebug "executing 'getDeviceList()'"
   
	def data = [
        uri: sURI,
		path: "/devices",
		method: "getDeviceList",
        authToken: state?.authToken
		]
	asyncHttpGet("asyncHttpGetCallback", data)
}

private asyncHttpGet(callbackMethod, data) {
    logDebug "${app.getLabel()} executing 'asyncHttpGet()'"
	
    def params = [
	    uri: data.uri,
	    path: data.path,
		headers: [ Authorization: "Bearer ${data.authToken}" ]
    ]
	try {
	    asynchttpGet(callbackMethod, params, data)
	} catch (e) {
	    logWarn "${app.getLabel()} asyncHttpGet error: $e"
	}	
}

def asyncHttpGetCallback(resp, data) {
    logDebug "executing 'asyncHttpGetCallback()' status: ${resp.status} method: ${data?.method}"
    def response = [statusCode:iHttpError]
    
    if (resp.status == iHttpSuccess) {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logTrace "response data: ${resp.data}"
        response.statusCode = resp.status
        
        switch(data?.method) {
            case "getDeviceHealth":
                def health = new JsonSlurper().parseText(resp.data)
                
                getSmartChildDevices(data.deviceId)?.each { childDevice ->                    
                    if(childDevice.hasCommand("smartHealthHandler")) {
                        childDevice.smartHealthHandler(health)
                    } 
                    else if (health?.deviceId) {                            
                        smartHealthHandler(childDevice, health)                        
                    }
                }          
                break
            case "getCapability":
                def capability = new JsonSlurper().parseText(resp.data)
            
                getSmartChildDevices(data.deviceId)?.each { childDevice ->
                    if(childDevice.hasCommand("smartCapabilityHandler")) {
                        childDevice.smartCapabilityHandler(capability)
                    } 
                    else if (capability?.id) {
                        //smartCapabilityHandler(childDevice, capability)
                        def current = getChildDeviceDataJson(childDevice, "capabilities")
                        if (current?.components) {
                            if (current.components?.find { components -> components?.id == capability.id && components?.version == capability.version  }) {
                                logInfo "${childDevice.getLabel()} attribute ${capability.id} FOUND"
                            }
                            else {
                                logInfo "${childDevice.getLabel()} attribute ${capability.id} ADDED"
                                current.components.add(capability)
                                childDevice.updateDataValue("capabilities", JsonOutput.toJson(current))
                            }
                        }
                        else {
                            childDevice.updateDataValue("capabilities", JsonOutput.toJson([components :  [ capability ]])) 
                        }

                    }
                }
                break
            case "getDeviceDescription":
                def description = new JsonSlurper().parseText(resp.data)
        
                getSmartChildDevices(data.deviceId)?.each { childDevice ->
                    if(childDevice.hasCommand("smartDescriptionHandler")) {
                        childDevice.smartDescriptionHandler(description)
                    } 
                    else if (description?.deviceId) {
                        smartDescriptionHandler(childDevice, description)                        
                    }
                }
                break
            case "getDeviceStatus":
                def status = new JsonSlurper().parseText(resp.data)
        
                getSmartChildDevices(data.deviceId)?.each { childDevice ->
                    if(childDevice.hasCommand("smartStatusHandler")) {
                        childDevice.smartStatusHandler(status)
                    } 
                    else if (status?.components) {
                        smartStatusHandler(childDevice, status)
                    }
                }
                break
            case "getDeviceList":
            
                //state.devices = new JsonSlurper().parseText(resp.data)
                def smartDevices = g_mSmartDevices[app.getId()] = new JsonSlurper().parseText(resp.data)
                  
                getChildDevices()?.each { childDevice ->         
                    //def device = state?.devices?.items?.find{it.deviceId == childDevice.getDataValue("deviceId")}
                    def device = smartDevices?.items?.find{it.deviceId == childDevice.getDataValue("deviceId")}
                    
                    //setChildDeviceDataJson(childDevice, "device", JsonOutput.toJson(device ?: "{}"))
                    //childDevice.updateDataValue("device", JsonOutput.toJson(device ?: "{}"))
                }
                logInfo "${app.getLabel()} retrieved SmartThings device list"
                break
            default:
                logWarn "${app.getLabel()} asyncHttpGetCallback ${data?.method} not supported"
                if (resp?.data) logInfo resp.data
        }
    }
    else {
        logWarn("asyncHttpGetCallback: ${resp.status}")
    }
    
    return response
}

def commandDevice(deviceId, capability, command, arguments = []) {
    logDebug "${app.getLabel()} executing 'commandDevice()'"
    def response = [statusCode:iHttpError]

    def commands = [ commands: [[ component: "main", capability: capability, command: command, arguments: arguments ]] ]
    logTrace "commandDevice commands: "+JsonOutput.toJson(commands)
    
    def params = [
        uri: sURI,
        path: "/devices/${deviceId}/commands",
        body: JsonOutput.toJson(commands),
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpPostJson(params) { resp ->
            //resp.headers.each { logTrace "${it.name} : ${it.value}" }
            //logTrace "response contentType: ${resp.contentType}"
            logTrace "response data: ${resp.data}"
            logTrace "response status: ${resp.status}"
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "${app.getLabel()} subscription error: $e"
    }
    return response
}

def deleteSmartSubscriptions() {
    logDebug "${app.getLabel()} executing 'deleteSubscription()'"
    def response = [statusCode:iHttpError]
    
    def params = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpDelete(params) { resp ->
            //resp.headers.each { logTrace "${it.name} : ${it.value}" }
            //logTrace "response contentType: ${resp.contentType}"            
            logTrace "response data: ${resp.data}"
            logTrace "response status: ${resp.status}"
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "${app.getLabel()} delete subscription error: $e"
    }    
    return response
}

def createSmartSubscriptions() {    
    logDebug "${app.getLabel()} executing 'createSmartSubscriptions()'"
    def response = [statusCode:iHttpError]    
    
    state?.install.each { deviceId, value ->
        response = createSmartSubscription(deviceId)
    }       
        
    return [statusCode:response.statusCode]
}

def createSmartSubscription(deviceId) {
    logDebug "${app.getLabel()} executing 'createSmartSubscription($deviceId)'"
    def response = [statusCode:iHttpError]
    
    def subscription = [
        sourceType: "DEVICE",
        device: [
            deviceId: deviceId,
            componentId: "main",
            capability: "*",
            attribute: "*",
            stateChangeOnly: true,
            subscriptionName: deviceId,
            value: "*"
      ]
    ]

    def params = [
        uri: sURI,
        path: "/installedapps/${state?.installedAppId}/subscriptions",
        body: JsonOutput.toJson(subscription),
        headers: [ Authorization: "Bearer ${state?.authToken}" ]        
    ]
    try {
        httpPostJson(params) { resp ->
            //resp.headers.each { logTrace "${it.name} : ${it.value}" }
            //logTrace "response contentType: ${resp.contentType}"
            logTrace "response data: ${resp.data}"
            logTrace "response status: ${resp.status}"
            response.statusCode = resp.status
        }
    } catch (e) {
        logWarn "${app.getLabel()} ${subscription.device.deviceId} subscription error: $e"
    }
    return [statusCode:response.statusCode]
}

def installDevices(data) {
    logDebug "${app.getLabel()} executing 'installDevices()'"
    def response = [statusCode:iHttpError]    
    
    try {
        def deviceList = [:]
        data.installedApp.config.keySet().each { id ->
            data.installedApp.config[id].each { device ->            
                if ( device.valueType == "DEVICE" ) {
                    if (deviceList[device.deviceConfig.deviceId]) {
                        logInfo "${app.getLabel()} installDevices device:${device.deviceConfig.deviceId} was selected more than once"
                        deviceList[device.deviceConfig.deviceId].id.add(id)                        
                    } else {                    
                        deviceList[device.deviceConfig.deviceId] = [ id:[id], valueType:device.valueType, componentId:device.deviceConfig.componentId ]
                    }
                }
            }
        }
        state.install = deviceList
        logInfo "${app.getLabel()} installed ${deviceList.size()} SmartThings devices"
        response.statusCode = iHttpSuccess
    } catch (e) {
        logWarn "${app.getLabel()} installDevices error: $e"
    }
    return [statusCode:response.statusCode]
}

def refreshTokens(def data) {
    logDebug "${app.getLabel()} executing 'refreshTokens()'"
    if (data.authToken != state?.authToken) {    
        state.authToken = data?.authToken ?: state?.authToken
        state.refreshToken = data?.refreshToken ?: state?.refreshToken 
        state.installedAppId = data?.installedApp?.installedAppId ?: state?.installedAppId
        
        def expiration = data?.expiration?.toInteger() ?: 24*60*60
        Date expirationDate = new Date(new Date().toInstant().toEpochMilli() + (expiration * 1000))
        state.authTokenDate = expirationDate.format("YYYY-MM-dd h:mm:ss a z")
                       
        runIn((expiration/2).toInteger(), handleTokenRefresh) // good for 24 hours, lets refresh every 12.        
        logInfo "${app.getLabel()} authToken updated at ${state.authTokenDate}"
        
        allDeviceStatus()
    }
}

def handleTokenRefresh() {
    logDebug "${app.getLabel()} executing 'handleTokenRefresh()'"
    def response = [statusCode:iHttpError]
    
    def refreshToken = state.refreshToken // captured from INSTALL or UPDATE lifecycle first time. Good for 24hrs.
    def clientId = clientIdUUID // user input from SmartThings Developer SmartApp configuration
    def clientSecret = clientSecretUUID // user input from SmartThings Developer SmartApp configuration   
  
    def params = [
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

            refreshTokens( [ authToken:respJson.access_token, refreshToken:respJson.refresh_token, expiration:respJson.expires_in ] )
            response.statusCode = resp.status
         }
    } catch (e) {
        logWarn "${app.getLabel()} handleTokenRefresh error: $e"
        state.authTokenDate = "Error"
        runIn(1*60*60, handleTokenRefresh) // good for 24 hours, but we need to panic and do it every hour
    }
    
    return [statusCode:response.statusCode]
}

def handleUninstall(uninstallData) {
    logDebug "${app.getLabel()} executing 'handleUninstall()'"
    def response = [statusCode:iHttpError]
    
    // All subscriptions and schedules for the installed app will be automatically deleted by SmartThings.
    response.statusCode = iHttpSuccess
    state.remove('installedAppId')
    state.remove('authToken')
    state.remove('authTokenDate')
    state.remove('refreshToken')
    state.remove('devices')
    state.remove('install')
    state.remove('counter')
    unschedule('handleTokenRefresh')
    
    return [statusCode:response.statusCode, uninstallData:{}]
}

def handleEvent(eventData) {
    logDebug "${app.getLabel()} executing 'eventData()'"
    def response = [statusCode:iHttpSuccess]
    
    eventData?.events?.each { event ->  
        getSmartChildDevices(event?.deviceEvent?.deviceId)?.each { childDevice ->
            if (event?.deviceEvent) {                           
                response.statusCode = smartEventHandler(childDevice, event.deviceEvent).statusCode
            }
        }
    }    
    
    return [statusCode:response.statusCode, eventData:{}]
}

def handleUpdate(updateData) {
    logDebug "${app.getLabel()} executing 'updateData()'"
    def response = [statusCode:iHttpSuccess]
    
    refreshTokens(updateData)
    response.statusCode = installDevices(updateData).statusCode
    getDeviceList()
    deleteSmartSubscriptions()
    runIn(1, createSmartSubscriptions)
    //createSmartSubscriptions()

    return [statusCode:response.statusCode, updateData:{}]
}

def handleInstall(installData) {
    logDebug "${app.getLabel()} executing 'handleInstall()'"
    def response = [statusCode:iHttpError]
    
    refreshTokens(installData)
    response.statusCode = installDevices(installData).statusCode
    getDeviceList()
    runIn(1, createSmartSubscriptions)
    //createSmartSubscriptions()

    
    return [statusCode:response.statusCode, installData:{}]
}

def handleConfig(configurationData) {
    logDebug "${app.getLabel()} executing 'handleConfig()'"
    def response = [statusCode:iHttpError]
    
    switch(configurationData?.phase) {
        case 'INITIALIZE':    
            response = [statusCode:iHttpSuccess, configurationData:[initialize:[firstPageId:"mainPage", permissions:[], disableCustomDisplayName:false, disableRemoveApp:false]]]
            break;
        case 'PAGE':    
            response = getMainPage()
            break;
        default:
            logWarn "${app.getLabel()} configuration phase ${configurationData.phase} not supported"
    }   
    return response
}

def handleConfirm(confirmationData) {
    logDebug "${app.getLabel()} executing 'handleConfirm()' url:${confirmationData?.confirmationUrl}"
    //return [statusCode:200]    
    def response = [statusCode:iHttpError]
    
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
    logDebug "${app.getLabel()} executing 'webhookPost()'"
    logDebug "REQUEST: ${request.body.replaceAll(/(?<=(?:authToken\":\"|refreshToken\":\"))(.*?)(?=\")/, "<b>removed</b>")}"    

    if (!mainPageAllowCloudAccess) {
        logWarn "${app.getLabel()} endpoint disabled"
        return render(contentType: "text/html", data: getHtmlResponse(false), status: 404)
    }
    
    def evt = new JsonSlurper().parseText(request.body)
    def response = [statusCode:iHttpError]

    switch(evt?.lifecycle) {
        case 'PING': 
	        //responder.respond({statusCode: 200, pingData: {challenge: evt.pingData.challenge}})
            response = [statusCode:200, pingData: [challenge: evt.pingData.challenge]]
		    break;
        case 'CONFIRMATION':            
            response = handleConfirm(evt?.confirmationData)
            break;
        case 'CONFIGURE': //not sure this still exists
        case 'CONFIGURATION':
            response = handleConfig(evt?.configurationData)       
            break;
        case 'INSTALL':
            response = handleInstall(evt?.installData)
            break;
        case 'UPDATE':
            response = handleUpdate(evt?.updateData)
            break;
        case 'UNINSTALL':
            response = handleUninstall(evt?.uninstallData)
            break;
        case 'EVENT':
            response = handleEvent(evt?.eventData)
            break;        
        default:
          logWarn "${app.getLabel()} lifecycle ${evt?.lifecycle} not supported"
    }
    
    logDebug "RESPONSE: ${JsonOutput.toJson(response)}"
    return render(status:response.statusCode, data:JsonOutput.toJson(response))    
}

def getMainPage() {   
    def response = [
        statusCode:iHttpSuccess, 
        configurationData:[       
            page:[
                name:"${app.getLabel()}", 
                complete:true, 
                pageId:'mainPage', 
                nextPageId:null, 
                previousPageId:null,
                sections:[
                     [
                        //name:'Select Dimmers',
                        settings:[
                            [
                                id:'dimmer',
                                required:false, 
                                type:'DEVICE', 
                                name:'Select Dimmers', 
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['switchLevel'], 
                                permissions:['r', 'x']
                            ]
                        ]
                     ],
                     [
                        //name:'Select Switches',
                        settings:[
                            [
                                id:'switch',
                                required:false, 
                                type:'DEVICE', 
                                name:'Select Switches', 
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['switch'], 
                                permissions:['r', 'x']
                            ]
                        ]
                    ],
                    [
                        //name:'Select Sensors',
                        settings:[
                            [
                                id:'contactSensor', 
                                required:false, 
                                type:'DEVICE',
                                name:'Select Contact Sensors',
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['contactSensor'], 
                                permissions:['r', 'x']
                            ]
                        ]
                    ],
                    [
                        //name:'Select Sensors',
                        settings:[
                            [
                                id:'motionSensor', 
                                required:false, 
                                type:'DEVICE',
                                name:'Select Motion Sensors',
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['motionSensor'], 
                                permissions:['r', 'x']
                            ]
                        ]
                    ],
                    [
                        //name:'Select Sensors',
                        settings:[
                            [
                                id:'temperatureMeasurement', 
                                required:false, 
                                type:'DEVICE',
                                name:'Select Temperature Sensors',
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['temperatureMeasurement'], 
                                permissions:['r', 'x']
                            ]
                        ]
                    ],
                    [
                        //name:'Select Sensors',
                        settings:[
                            [
                                id:'relativeHumidityMeasurement', 
                                required:false, 
                                type:'DEVICE',
                                name:'Select Humidity Sensors',
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['relativeHumidityMeasurement'], 
                                permissions:['r', 'x']
                            ]
                        ]
                    ],
                    [
                        //name:'Select Sensors',
                        settings:[
                            [
                                id:'presenceSensor', 
                                required:false, 
                                type:'DEVICE',
                                name:'Select Presence Sensors',
                                description:'Tap to set', 
                                multiple:true, 
                                capabilities:['presenceSensor'], 
                                permissions:['r', 'x']
                            ]
                        ]
                    ]
 
                ]                
            ]
        ]
    ]
    return response
}

def getHtmlResponse(success = false) {
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
    if(type == "line")      return  "<hr style='background-color:${sColorDarkBlue}; height: 1px; border: 0;'>"
	if(type == "title")     return "<h2 style='color:${sColorDarkBlue};font-weight: bold'>${myText}</h2>"
    if(type == "text")      return  "<div style='color:${sColorDarkBlue};font-weight: bold'>${myText}</div>"
    if(type == "hyperlink") return  "<a href='${myHyperlink}' target='_blank' rel='noopener noreferrer' style='color:${sColorDarkBlue};font-weight:bold'>${myText}</a>"
}

def displayHeader() { 
    section (getFormat("title", "${app.getLabel()}${sCodeRelease?.size() ? " : [ $sCodeRelease ]" : ""}"  )) { 
        paragraph "<div style='color:${sColorDarkBlue};text-align:right;font-weight:small;font-size:9px;'>Developed by: ${author()}<br/>Current Version: ${version()} -  ${copyright()}</div>"
        paragraph getFormat("line") 
    }
}

def displayFooter(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:{sColorDarkBlue};text-align:center;font-weight:small;font-size:11px;'>${app.getLabel()}<br><br><a href='${paypal()}' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This application took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}       
}

def menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}

private logInfo(msg)  { log.info "${msg}" }
private logDebug(msg) { if(appLogEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(appTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn  "${msg}" } 
private logError(msg) { log.error  "${msg}" }
