/**
 *  Copyright 2024 Bloodtick Jones
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
 *  Roborock Robot Vacuum
 *
 *  Thanks to: 'copystring' and the https://www.npmjs.com/package/iobroker.roborock project
 *             'rovo89' https://gist.github.com/rovo89/dff47ed19fca0dfdda77503e66c2b7c7#file-test-js-L166
 *             https://www.home-assistant.io/integrations/roborock/
 *
 *  Author: bloodtick
 *  Date: 2024-04-18
 */
public static String version() {return "1.1.5"}
@Field static final Boolean hubitatVersion239 = false

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.transform.CompileStatic
import groovy.transform.Field
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Random
import java.util.TimeZone
import java.text.SimpleDateFormat

// This value is stored hardcoded in librrcodec.so, encrypted by the value of "com.roborock.iotsdk.appsecret" from AndroidManifest.xml.
@Field static final String salt = "TXdfu\$jyZ#TZHsg4"
// Hours of possible useage for each consumable. These are probably different per model.
@Field static final Map life = [ main:300, side:200, filter:150, sensor:30, highSpeed:300]

metadata {
	definition (name: "Roborock Robot Vacuum", namespace: "bloodtick", author: "Hubitat", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/roborockRobotVacuum/roborockRobotVacuum.groovy")
	{
        capability "Actuator"
        capability "Battery"
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        // Special capablity to allow for Hubitat dashboarding to set commands via the Button template
        // Use Hubitat 'Button Controller' built in app to set commands to run.
        capability "PushableButton"

        command "appClean"
        command "appDock"
        command "appPause"
        command "appRoomClean", [[name: "Room IDs*", type: "STRING", description: "Accepts comma or space delmited Room IDs"],
                                 [name: "MopWater", type: "ENUM", description: "Set the room water mopping params. Default is no change of current setting. Not required.", constraints: mopWaterModeCodes.values().collect{ it.toUpperCase() }]]
        command "appRoomResume"
        command "appScene", [[name: "Scene ID*", type: "STRING", description: "Accepts single Scene ID"]]
        command "execute", [[name: "command*", type: "STRING", description: "The command to send device via mqtt"],[name: "params", type: "JSON_OBJECT", description: "Command parameters in JSON object"]]
        command "selectDevice"

        attribute "dustCollection", "enum", ["off","on"]
        attribute "dockError", "enum", dockErrorCodes.values().collect()
        attribute "name", "string"
        attribute "rooms", "JSON_OBJECT"
        attribute "scenes", "JSON_OBJECT"
        if(hubitatVersion239) {
            attribute "state", "enum", stateCodes.values().collect()   
            attribute "error", "enum", errorCodes.values().collect()
        } else {
            attribute "state", "string" // , stateCodes.values().collect() -- too long 2.3.9+ change to 1024  
            attribute "error", "string" // , errorCodes.values().collect() -- too long
        }        
        attribute "fanPower", "enum", fanPowerCodes.values().collect()
        attribute "cleanTime", "number"
        attribute "cleanArea", "number"
        attribute "cleanPercent", "number"
        attribute "remainingFilter", "number"
        attribute "remainingMainBrush", "number"
        attribute "remainingSensors", "number"
        attribute "remainingSideBrush", "number"
        attribute "remainingHighSpeedMaintBrush", "number"
        attribute "locating", "enum", ["true","false"]
        attribute "mopMode", "enum", mopModeCodes.values().collect()
        attribute "mopWaterMode", "enum", mopWaterModeCodes.values().collect() 
        //attribute "wifi", "enum", ["offline", "online"]
        attribute "healthStatus", "enum", ["offline", "online"]
	}
}

preferences {
    input(name:"username", type:"string", title: "<b>Roborock Username:</b>", required: true, width:4)
    input(name:"password", type:"password", title: "<b>Roborock Password:</b>", required: true, width:4)
    input(name:"regionUri", type:"enum", title: "<b>Account Region:</b>", options:["https://usiot.roborock.com":"US", "https://euiot.roborock.com":"EU", "https://cniot.roborock.com":"CN", "https://ruiot.roborock.com":"RU"], defaultValue: "https://usiot.roborock.com", required: true, width:4)
    input(name:"allowLogin", type:"bool", title: "<b>Authorize Account User Login:</b>", defaultValue: true, width:4, description: "<i>Enable to re/attempt intial login with username and password.</i>")
    input(name:"autoLogin", type: "enum", title: "<b>Auto Authorize Account User Login:</b>", options: [ "manual":"Manual Only", "900":"15 Minutes", "1800":"30 Minutes", "3600":"1 Hour", "10800":"3 Hours"], defaultValue: "1800", description: "<i>If device goes offline re/attempt intial login with username and password.</i>", required: true)
    input(name:"areaUnit", type:"enum", title: "<b>Device Area Unit:</b>", options:["0":"Square Foot (ft²)", "1":"Square Meter (m²)"], defaultValue: "0", required: true, width:4)
    input(name:"numberOfButtons", type: "number", title: "<b>Set Number of Buttons:</b>", range: "1...", defaultValue: 1, required: true, width:4)
    input(name:"deviceInfoDisable", type:"bool", title: "Disable Info logging:", defaultValue: false, width:4)
    input(name:"deviceDebugEnable", type:"bool", title: "Enable Debug logging:", defaultValue: false, width:4)
    //input(name:"deviceTraceEnable", type:"bool", title: "Enable Trace logging:", defaultValue: false, width:4)
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "${device.displayName} disabling debug logs"
}
Boolean autoLogsOff() { if ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) runIn(1800, "logsOff"); else unschedule('logsOff');}

def installed() {
	initialize()
}

def updated() {
	initialize()    
}

def initialize() {
    unschedule()
    autoLogsOff()
            
    if(settings?.allowLogin && settings?.username && settings?.password) {
        logInfo "${device.displayName} executing 'initialize()' allowLogin"
        // blow away all state information
        state?.keySet()?.collect()?.each{ state.remove(it) }
        state.sequence = (new Random().nextInt(2000) + 1)
        if(state?.restore) state.duid = state.restore
        clearAttributes()        
        if(login()?.msg=="success") {
            device.updateSetting("allowLogin",[value:'false',type:"bool"])
            disconnect()
            runIn(1, "getHomeDetail") //runs getHomeData()->getHomeDataCallback() async serial
        } else {
            logWarn "${device.displayName} login with username:'$username' password:'$password' failed"
        }
    } 
    else if(state?.login) {
        state.remove("autoRefresh") // removed in 1.0.4
        disconnect()
        runIn(1, "getHomeData") //runs getHomeDataCallback() async serial
    }
    sendEvent(name:"numberOfButtons", value: (settings?.numberOfButtons)?:1)
}

def push(buttonNumber) {
    sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
}

def on() { appClean(); processEvent("switch","on") }
def off() { appDock(); processEvent("switch","off") }
def appClean() { execute("app_start") }
def appDock()  { execute("app_charge") }
def appPause() { execute("app_pause") }
def appRoomResume()  { execute("resume_segment_clean") }
def appRoomClean(String rooms, String mopWater=mopWaterModeCodes[0]) {  
    rooms = rooms.replaceAll(" +", ',')
    if(mopWater?.toUpperCase()!=mopWaterModeCodes[0].toUpperCase()) {
        Integer mopWaterCode = ( mopWaterModeCodes.find { it.value.toUpperCase() == mopWater?.toUpperCase() }?.key )        
        execute("set_water_box_custom_mode","[$mopWaterCode]")
        execute("get_water_box_custom_mode")
    }
    execute("app_segment_clean","[$rooms]")
}
def appScene(String sceneId) { setDeviceScene(sceneId) }
def selectDevice() { 
    String deviceId = findNextDevice( state?.duid )
    if(state?.duid != deviceId) {
        state?.duid = deviceId
        clearAttributes()
        initialize()
    } else {
        logInfo "${device.displayName} device id is ${getDeviceId()}"
    }
}

void clearAttributes() {
    // blow away all attribute information. not sure if this 'is the way' but it works.
    device.currentStates?.collect{ ((new groovy.json.JsonSlurper().parseText( groovy.json.JsonOutput.toJson(it) ))?.name) }?.each{ device.deleteCurrentState(it) }    
}

void getHomeDataCallback() {
    logDebug "${device.displayName} executing 'getHomeDataCallback()'"
    logDebug "${device.displayName} device id is ${getDeviceId()}"
    
    Boolean deviceOnline = !!(getHomeDataResult()?.devices?.find{ it.duid == getDeviceId() }?.online)
    //processEvent("wifi", (deviceOnline ? "online" : "offline"))
    if(!deviceOnline) {
        //logWarn "${device.displayName} wifi is offline"
        processEvent("error_code", 256)
        setHealthStatusEvent(false)
        qClear()
        unschedule()
        runIn(15*60,"getHomeData")
        return
    }
    
    if( !interfaces.mqtt.isConnected() ) {
        runIn(3, "connect")
    }
    updateHomeData()
}

void updateHomeData() {
    logDebug "${device.displayName} executing 'updateHomeData()'"    
    execute("get_room_mapping")
	if(device.currentValue("switch")!="on") execute("get_consumable") 	
	
    String name = getHomeDataResult()?.devices?.find{ it.duid == getDeviceId() }?.name ?: "unknown"
    processEvent("name", name)
}

@Field volatile static Map<Long,Long> g_mLastRefreshTime = [:]
def refresh(Map data=[type:1]) {
    logDebug "${device.displayName} executing 'refresh($data)'"

    execute("get_prop", """["get_status"]""")
    if(device.currentValue("switch")=="on") execute("get_consumable")    
    if(g_mLastRefreshTime[device.getIdAsLong()] == null) g_mLastRefreshTime[device.getIdAsLong()] = now()-120000
    if(data?.type==1 && (now() - g_mLastRefreshTime[device.getIdAsLong()]) > 120000) { 
        getHomeData()
        g_mLastRefreshTime[device.getIdAsLong()] = now()
    }
}

def execute(String command, String args=null) {
    // I have no idea if this conversion works for everything. It works for somethings... ;) 
    def param = args ? convertNumbers((new JsonSlurper().parseText(args))) : []
    logInfo "${device.displayName} executing execute(command:$command, param:$param)"
    
    Integer id = (Integer)(state.sequence++ & 0xFFFFFFFF)
    qPush([duid: getDeviceId(), command: command, param: param, id:id])
    if(qSize()<=1) executeQueue()
}  

void executeQueue() {
    if(!qIsEmpty()) {
        Map cmd = qPeek()
        runIn(15, "watchdog") // unscheduled in processMsg()
        publish(cmd.duid, cmd.command, cmd.param, cmd.id)
    } else {
        unschedule('watchdog')
    }
}

void watchdog() {
    logWarn "${device.displayName} executing 'watchdog()'"    
    disconnect()
    runIn(1, "getHomeData")
}

void scheduleRefresh(Integer delay=5) {
    runIn(delay, "refresh", [data: [type:2]])    
}

void disconnect() {
    logInfo "${device.displayName} executing 'disconnect()'"
    unsubscribe()
    interfaces.mqtt.disconnect()
    setHealthStatusEvent(false)
}

void connect() {
    logDebug "${device.displayName} executing 'connect()'"
    Map rriot = getLoginData()?.rriot
    String mqttUser = md5hex(rriot.u + ':' + rriot.k).substring(2, 10)
    String mqttPassword = md5hex(rriot.s + ':' + rriot.k).substring(16)
    
    logInfo "${device.displayName} connecting mqttUser:$mqttUser to $rriot.r.m"
    try {
        interfaces.mqtt.connect(rriot.r.m, "${device.deviceNetworkId}", mqttUser, mqttPassword, byteInterface:true)
        state.remove('restore')
        logDebug "${device.displayName} connected successfully"
    } catch (org.eclipse.paho.client.mqttv3.MqttSecurityException e) {
        // what i need to catch: org.eclipse.paho.client.mqttv3.MqttSecurityException: Not authorized to connect (method connect)
        // what i can fake:      org.eclipse.paho.client.mqttv3.MqttSecurityException: Bad user name or password (method connect)
        logError "${device.displayName} mqtt security exception: '${e.message}'"        
        if(settings?.autoLogin && settings.autoLogin!="manual") {
            processEvent("error_code", 257)
            logInfo "${device.displayName} auto scheduling 'initialize' in ${settings.autoLogin} seconds"
            unschedule()
            device.updateSetting("allowLogin",[value:'true',type:"bool"])
            state.restore = state.duid
            runIn(settings.autoLogin.toInteger(), "initialize")
        }    
    } catch (Exception e) {
        logError "${device.displayName} MQTT Connection Exception: ${e.message}"
    }    
}

def mqttClientStatus(String message) {
    logInfo "${device.displayName} executing 'mqttClientStatus($message)'"
    if(message.toLowerCase().contains("connection succeeded")) {
        runIn(1, "subscribe")
    }
    else {
       disconnect() 
       runIn(60*10, "connect")
    }
}

void subscribe() {
    logDebug "${device.displayName} executing 'subscribe()'"
    if(!interfaces.mqtt.isConnected()) return
    
    Map rriot = getLoginData()?.rriot
    String mqttUser = md5hex(rriot.u + ':' + rriot.k).substring(2, 10);
    String mqttPassword = md5hex(rriot.s + ':' + rriot.k).substring(16);
    
    String topic = "rr/m/o/${rriot.u}/${mqttUser}/#"
    logInfo "${device.displayName} subscribe topic:$topic"
    interfaces.mqtt.subscribe(topic)
    
    runEvery30Minutes(refresh)
    scheduleRefresh()
    updateHomeData()
    executeQueue()
}

void unsubscribe() {
    logDebug "${device.displayName} executing 'unsubscribe()'"
    if(!interfaces.mqtt.isConnected()) return
    
    Map rriot = getLoginData()?.rriot
    String mqttUser = md5hex(rriot.u + ':' + rriot.k).substring(2, 10);
    String mqttPassword = md5hex(rriot.s + ':' + rriot.k).substring(16);
    
    String topic = "rr/m/o/${rriot.u}/${mqttUser}/#"
    logInfo "${device.displayName} unsubscribe topic:$topic"
    interfaces.mqtt.unsubscribe(topic)
}

void sendEventX(Map x) {
    if(device.currentValue(x?.name).toString() != x?.value.toString()) {
        if(x?.descriptionText) { if(x?.logLevel=="warn") logWarn (x?.descriptionText); else logInfo (x?.descriptionText); }
        sendEvent(name: x?.name, value: x?.value, unit: x?.unit, descriptionText: x?.descriptionText, isStateChange: (x?.isStateChange ?: false))
    }
}

void processEvent(String name, def value) {
    logTrace "${device.displayName} executing 'processEvent($name, $value)'"
    String descriptionText = null    
    switch(name) {
    case "get_water_box_custom_mode":
        String valueEnum = mopWaterModeCodes[value?.toInteger()]?.toLowerCase() ?: value
        sendEventX(name: "mopWaterMode", value: valueEnum, descriptionText: "${device.displayName} mop water mode is $valueEnum ($value)")        
        break
    case "switch":    
        sendEventX(name: "switch", value: value, descriptionText: "${device.displayName} switch is $value")        
        break
    case "name":
        sendEventX(name: "name", value: value, descriptionText: "${device.displayName} name set to $value")
        break    
    case "healthStatus":
        sendEventX(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value", logLevel:(value=="online"?"info":"warn"))
        break
    case "wifi":
        //sendEventX(name: "wifi", value: value, descriptionText: "${device.displayName} wifi set to $value")
        break    
    case "rooms":
        sendEventX(name: "rooms", value: JsonOutput.toJson(value), descriptionText: "${device.displayName} rooms set to $value")
        break
    case "scenes":
        sendEventX(name: "scenes", value: JsonOutput.toJson(value), descriptionText: "${device.displayName} scenes set to $value")
        break
    case "rpc_request":
        break
    case "rpc_response":
        break
    case "error_code":
        String valueEnum = errorCodes[value?.toInteger()]?.toLowerCase() ?: value
        sendEventX(name: "error", value: valueEnum, descriptionText: "${device.displayName} error is $valueEnum ($value)", logLevel:(value==0?"info":"warn"))
        break
    case "state":
        String valueEnum = stateCodes[value?.toInteger()]?.toLowerCase() ?: value
        sendEventX(name: "state", value: valueEnum, descriptionText: "${device.displayName} state is $valueEnum ($value)")
        break
    case "battery": 
        sendEventX(name: "battery", value: value.toInteger(), unit: "%", descriptionText: "${device.displayName} battery level is $value%")
        break
    case "fan_power":
        String valueEnum = fanPowerCodes[value?.toInteger()]?.toLowerCase() ?: value
        sendEventX(name: "fanPower", value: valueEnum, descriptionText: "${device.displayName} fan power is $valueEnum ($value)")
        break
    case "water_box_mode":
        break
    case "main_brush_life": 
        break
    case "main_brush_work_time":
        Integer percentAvail = Math.max(0, (100 - Math.floor((value.toInteger() / (life.main * 60 * 60)) * 100).toInteger()))
        sendEventX(name: "remainingMainBrush", value: percentAvail, unit: "%", descriptionText: "${device.displayName} main brush time remaining is $percentAvail%")
        break
    case "side_brush_life":
        break
    case "side_brush_work_time":
        Integer percentAvail = Math.max(0, (100 - Math.floor((value.toInteger() / (life.side * 60 * 60)) * 100).toInteger()))
        sendEventX(name: "remainingSideBrush", value: percentAvail, unit: "%", descriptionText: "${device.displayName} side brush time remaining is $percentAvail%")
        break
    case "cleaning_brush_work_times":
        Integer percentAvail = Math.max(0, (100 - Math.floor((value.toInteger() / life.highSpeed) * 100).toInteger()))
        sendEventX(name: "remainingHighSpeedMaintBrush", value: percentAvail, unit: "%", descriptionText: "${device.displayName} high-speed maintenance brush remaining life is $percentAvail%")
        break
    case "filter_life":
        break
    case "filter_work_time":
        break
    case "additional_props":
        break
    case "task_complete":
        break
    case "task_cancel_low_power":
        break
    case "task_cancel_in_motion":
        break
    case "charge_status":
        break
    case "drying_status":
        break
    case "sensor_dirty_time":
        Integer percentAvail = Math.max(0, (100 - Math.floor((value.toInteger() / (life.sensor * 60 * 60)) * 100).toInteger()))
        sendEventX(name: "remainingSensors", value: percentAvail, unit: "%", descriptionText: "${device.displayName} sensor time remaining is $percentAvail%")
        break
    case "filter_element_work_time":
        break
    case "dust_collection_work_times":
        break
    case "msg_ver":
        break
    case "msg_seq":
        break
    case "clean_time":
        Integer totalMinutes = Math.ceil(value.toInteger()/60).toInteger()
        sendEventX(name: "cleanTime", value: totalMinutes, unit: "min", descriptionText: "${device.displayName} clean time is $totalMinutes ${totalMinutes==1?"minute":"minutes"}")
        break
    case "clean_area":
        String unit = (areaUnit==null || areaUnit=="0") ? "ft²" : "m²"
        Integer area = (unit=="ft²") ? value.toInteger() / 92903.04 : value.toInteger() / 1000000
        sendEventX(name: "cleanArea", value: area, unit: unit, descriptionText: "${device.displayName} clean area is $area $unit")
        break
    case "map_present":
        break
    case "in_cleaning":
        break
    case "in_returning":
        break
    case "in_fresh_state":
        break
    case "lab_status":
        break
    case "water_box_status":
        break
    case "dnd_enabled":
        break
    case "map_status":
        break
    case "is_locating":
        String locatingString = (value==0 ? "false" : "true")
        sendEventX(name: "locating", value: locatingString, descriptionText: "${device.displayName} locating value is $locatingString ($value)")        
        break
    case "lock_status":
        break
    case "water_box_carriage_status":
        break
    case "mop_forbidden_enable":
        break
    case "camera_status":
        break
    case "is_exploring":
        break
    case "adbumper_status":
        break
    case "water_shortage_status":
        break
    case "dock_type":
        break
    case "dust_collection_status": 
        String dustCollectionString = (value==0 ? "off" : "on")
        sendEventX(name: "dustCollection", value: dustCollectionString, descriptionText: "${device.displayName} dust collection is $dustCollectionString ($value)") 
        break
    case "auto_dust_collection":
        break
    case "avoid_count":
        break
    case "mop_mode": 
        String valueEnum = mopModeCodes[value?.toInteger()]?.toLowerCase() ?: value
        sendEventX(name: "mopMode", value: valueEnum, descriptionText: "${device.displayName} mop mode is $valueEnum ($value)")
        break
    case "debug_mode":
        break
    case "collision_avoid_status":
        break
    case "switch_map_mode":
        break
    case "dock_error_status": 
        String valueEnum = dockErrorCodes[value?.toInteger()]?.toLowerCase() ?: value
        sendEventX(name: "dockError", value: valueEnum, descriptionText: "${device.displayName} dock error is $valueEnum ($value)", logLevel:(value==0?"info":"warn"))
        break
    case "unsave_map_reason":
        break
    case "unsave_map_flag":
        break
    case "clean_percent":
        sendEventX(name: "cleanPercent", value: value.toInteger(), unit: "%", descriptionText: "${device.displayName} percent completed is $value%")        
        break
    case "rss":
    case "dss":
    case "events":
    case "switch_status":
    case "distance_off":
    case "home_sec_status":
    case "home_sec_enable_password":
        break
    case "strainer_work_times":  // start reported by Q Revo
		Integer percentAvail = Math.max(0, (100 - Math.floor((value.toInteger() / life.filter) * 100 ).toInteger()))
        sendEventX(name: "remainingFilter", value: percentAvail, unit: "%", descriptionText: "${device.displayName} filter life remaining is $percentAvail%")
        break
    case "wash_status":
    case "wash_ready":
    case "wash_phase":
    case "rdt":
    case "last_clean_t":
    case "kct":
    case "in_warmup":
    case "dry_status":
    case "corner_clean_mode":
    case "common_status":
    case "back_type":  // end reported by Q Revo
        break
    default:
        logDebug "${device.displayName} did not process name:$name with value:$value"     
    }
    if(descriptionText) logInfo descriptionText
}

void processMsg(Map message) {
    logDebug "${device.displayName} executing 'processMsg($message)'"
    // we have good connection to device since we got a message back from it.
    setHealthStatusEvent(true)

    message?.dps?.each { key,value ->        
        // look up id and find the 'code' that was mapped in the home data. duid is used find the productID. 
        Map home = getHomeDataResult()
        String duid = getDeviceId()        
        String productId = home?.devices?.find{ it.duid == duid }?.productId
        String code = home?.products?.find{ it.id == productId }?.schema?.find { it.id == key }?.code
        
        if(code=="rpc_response") {
            
            def jsonValue = null
            try {
                jsonValue = (new JsonSlurper()).parseText( value )
            } catch(e) {
                logWarn "${device.displayName} message not json: message:$message value:$value"
            }
            
            if(qPeek()?.id?.toInteger() != jsonValue?.id?.toInteger()) {
                logDebug "${device.displayName} message unknown: command:$cmd result:$jsonValue"
                return
            }           
            // lets get our command that sent this request and we can start the queue up again.
            Map cmd = qPop()
            executeQueue()
       
            if((cmd?.command=="get_prop" && cmd?.param==["get_status"]) || cmd?.command=="get_consumable") {
                logDebug "${device.displayName} command '$cmd.command' was accepted"
                jsonValue?.result?.each{ result ->
                    if(cmd?.param==["get_status"]) {
                        result.switch=(result?.in_cleaning?.toInteger()!=0 || result?.is_locating?.toInteger()!=0 || result?.is_exploring?.toInteger()!=0) ? "on" : "off"
                        if(result?.battery?.toInteger()==100 && result?.state?.toInteger()==8) result.state=100
                        if(result?.clean_percent?.toInteger()==0 && result?.clean_area?.toInteger()>1) result.clean_percent=100
                        if(!stateDoNotRefreshCodes.contains(result.state)) { scheduleRefresh(60) } // some units don't send real time dps events
                    }
                    logDebug "${device.displayName} processing $result"
                    result?.each{ c,v -> processEvent(c,v) }
                }                
            }
            else if(cmd?.command=="get_room_mapping") {
                logDebug "${device.displayName} command '$cmd.command' was accepted"
                setRoomsValue(jsonValue)
            }
            else if(cmd?.command=="get_water_box_custom_mode" && (jsonValue?.result?.water_box_mode)) {
                logDebug "${device.displayName} command '$cmd.command' was accepted"
                processEvent(cmd?.command, jsonValue.result.water_box_mode)
            }
            else if(jsonValue?.result==["ok"] || jsonValue?.result==["OK"]) {
                logInfo "${device.displayName} command '$cmd.command' was accepted"
                scheduleRefresh()
            }                
            else {
                logWarn "${device.displayName} message not handled: command:$cmd result:$jsonValue"
            }
        }
        else {            
            processEvent(code,value)
            scheduleRefresh()
        }         
    } 
}

void setRoomsValue(Map get_room_mapping) {
    logDebug "${device.displayName} executing 'setRoomsValue()'"
    Map roomsMap = getHomeDataResult()?.rooms?.collectEntries { [(it.id.toString()): it.name] }
    if(roomsMap && get_room_mapping) {
        Map rooms = get_room_mapping?.result.collectEntries { mapping ->
            String roomId = mapping[1].toString()
            String roomName = roomsMap[roomId]
            return [(mapping[0].toString()):roomName]
        }
        processEvent("rooms", rooms?.sort())
    }
}

void setHealthStatusEvent(Boolean mqttClientStatus) {
    Boolean deviceOnline = getHomeDataResult()?.devices?.find{ it.duid == getDeviceId() }?.online
    String healthStatus = mqttClientStatus && deviceOnline ? "online" : "offline"
    processEvent("healthStatus", healthStatus)
}

def parse(String message) {
    logDebug "${device.displayName} executing 'parse()'"
    Map mqttMessage = interfaces.mqtt.parseMessage(message)
    parse( mqttMessage.topic, mqttMessage.payload.decodeHex() )
}          

def parse(String topic, byte[] message) {
    String deviceId = topic.split('/')[-1]
    if(deviceId!=state.duid) {
        logDebug "${device.displayName} parse message rejected: I am ${state.duid} and this was for $deviceId"
        return
    }
    String localKey = getLocalKey(deviceId)
    logDebug "${device.displayName} parse deviceId:$deviceId, localKey:$localKey, topic:$topic"
    //  .endianess('big')
    //  .string('version', {length: 3})
    //  .uint32('seq')
    //  .uint32('random')
    //  .uint32('timestamp')
    //  .uint16('protocol')
    //  .uint16('payloadLen')
    //  .buffer('payload', {length: 'payloadLen'})
    //  .uint32('crc32');
    // Extract version as string
    String version = bytesToString(message, 0, 3)
    // Do some checks
    if (version!="1.0") {// && version!="A01") {
	    logWarn "${device.displayName} parse was not version as expected:$version, Message: ${message.encodeHex()}"
	    return
	}
    Integer crc32 = CRC32(message, message.length - 4)
	Integer expectedCrc32 = readInt32BE(message, message.length - 4)
	if (crc32 != expectedCrc32) {
        logWarn "${device.displayName} parse was not crc32:${(crc32 & 0xFFFFFFFFL)} as expected:${(expectedCrc32 & 0xFFFFFFFFL)}, Message: ${message.encodeHex()}"
        return
	}    

    Integer sequence   = readInt32BE(message, 3)
    Integer random     = readInt32BE(message, 7)
    Integer timestamp  = readInt32BE(message, 11)
    Integer protocol   = readInt16BE(message, 15)
    if(protocol!=102) return // WE DONT HANDLE IMAGES YET
    Integer payloadLen = readInt16BE(message, 17)
    byte[] payload = message[19..(19+payloadLen-1)]    
    logTrace "payloadLen:$payloadLen, payload:${payload.length}, byte0:${ String.format("%02x", payload[0] & 0xFF) }"
    logTrace "payload: ${payload.encodeHex()}"    
    logDebug "${device.displayName} parsed message deviceId:$deviceId, version:${version}, sequence:${sequence}, random:${random}, timestamp:${timestamp}, protocol:${protocol}, payloadLen:${payloadLen}, crc32:${Integer.toHexString(crc32)}"
    
    String key = encodeTimestamp(timestamp) + localKey + salt   
    byte[] result = decrypt(payload, key)
    
    Map jsonObject = [:]
    if(protocol==102) {
        try {
             jsonObject = (new JsonSlurper()).parseText( new String(result, "UTF-8") )            
        } catch(e) {
            logWarn "${device.displayName} payload was not json. protocol:$protocol, length:${result.length}"
        }
    } else {
        logDebug "${device.displayName} payload protocol:$protocol, length:${result.length}"
    }
    if(!jsonObject.isEmpty()) {
       processMsg( jsonObject )
    }    
}

Integer publish(String deviceId, method, params, Integer id) {
    logDebug "${device.displayName} executing 'publish($deviceId, $method, $params)'" 
    
    Integer timestamp = (Integer)(now() / 1000)
    Integer protocol = 101    
 
    Map inner = [id:id, method:method, params:params]
    String payload = JsonOutput.toJson( [t:timestamp, dps:["$protocol": JsonOutput.toJson(inner)]] )

    byte[] message = build(deviceId, protocol, timestamp, payload.getBytes("UTF-8"))
    
    Map rriot = getLoginData()?.rriot
    String mqttUser = md5hex(rriot.u + ':' + rriot.k).substring(2, 10);
    
    String topic = "rr/m/i/${rriot.u}/${mqttUser}/${deviceId}"
    logDebug "${device.displayName} publishing topic:'$topic'"
    interfaces.mqtt.publish(topic, message.encodeHex().toString())
    
    return requestId
}

byte[] build(String deviceId, Integer protocol, Integer timestamp, byte[] payload) {
    
    String localKey = getLocalKey(deviceId)
    String key = encodeTimestamp(timestamp) + localKey + salt   
    byte[] encrypted = encrypt(payload, key)
    
    Random random = new Random()
    Integer randomInt = random.nextInt(900000) + 100000
    
    int totalLength = 23 + encrypted.length
    byte[] msg = new byte[totalLength]
    // Writing fixed string '1.0'
    msg[0] = 49 // ASCII for '1'
    msg[1] = 46 // ASCII for '.'
    msg[2] = 48 // ASCII for '0'
    writeInt32BE(msg, (Integer)(state.sequence & 0xFFFFFFFF), 3)
    writeInt32BE(msg, (Integer)(randomInt & 0xFFFFFFFF), 7)
    writeInt32BE(msg, timestamp, 11)
    writeInt16BE(msg, protocol, 15)
    writeInt16BE(msg, encrypted.length, 17)
    // Manually copying encrypted data into msg
    for (Integer i = 0; i < encrypted.length; i++) {
        msg[19 + i] = encrypted[i]
    }
    Integer crc32 = CRC32(msg, msg.length - 4)
    writeInt32BE(msg, crc32, msg.length - 4)

    return msg
}

byte[] decrypt(byte[] payload, String key) { 
    byte[] aesKeyBytes = md5bin(key);    
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES")
	cipher.init(Cipher.DECRYPT_MODE, keySpec)
    return cipher.doFinal(payload)
}

byte[] encrypt(byte[] payload, String key) {
    byte[] aesKeyBytes = md5bin(key)
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    return cipher.doFinal(payload)
}

Integer CRC32(bytes, length) {
    def crc = 0xFFFFFFFF
    for (int i = 0; i < length; i++) {
        def b = bytes[i] & 0xFF // Make sure the byte is treated as unsigned
        crc = crc ^ b
        for (int j = 7; j >= 0; j--) {
            def mask = -(crc & 1)
            crc = (crc >>> 1) ^ (0xEDB88320 & mask) // Use unsigned right shift
        }
    }
    return (crc ^ 0xFFFFFFFFL)
}

String bytesToString(byte[] data, Integer start, Integer length) {
    return (new String( (byte[])(data[start..<start+length]), "UTF-8"))
}

Integer readInt32BE(byte[] data, Integer start) {
    return (((data[start] & 0xFF) << 24) | ((data[start+1] & 0xFF) << 16) | ((data[start+2] & 0xFF) << 8) | (data[start+3] & 0xFF))
}

Integer readInt16BE(byte[] data, Integer start) {
    return (((data[start] & 0xFF) << 8) | (data[start+1] & 0xFF))
}

void writeInt32BE(byte[] msg, Integer value, Integer start) {
    msg[start + 0] = (byte) ((value >> 24) & 0xFF)
    msg[start + 1] = (byte) ((value >> 16) & 0xFF)
    msg[start + 2] = (byte) ((value >> 8) & 0xFF)
    msg[start + 3] = (byte) (value & 0xFF)
}

void writeInt16BE(byte[] msg, Integer value, Integer start) {
    msg[start + 0] = (byte) ((value >> 8) & 0xFF)
    msg[start + 1] = (byte) (value & 0xFF)
}

byte[] md5bin(String input) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    return md.digest(input.getBytes("UTF-8"))
}

String md5hex(String input) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    return md.digest(input.getBytes("UTF-8")).encodeHex()
}

String datetimestring() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return sdf.format(new Date())
}

String encodeTimestamp(int timestamp) {
    // Convert the timestamp to a hexadecimal string and pad it to ensure it's at least 8 characters
    String hex = new BigInteger(Long.toString(timestamp)).toString(16).padLeft(8, '0')
    List<String> hexChars = hex.toList()
    // Define the order in which to rearrange the hexadecimal characters
    int[] order = [5, 6, 3, 7, 1, 2, 0, 4]
    String result = order.collect { hexChars[it] }.join('')
    return result
}

// Helper method to check if a string is numeric
String.metaClass.isNumber = {
    delegate ==~ /-?\d+(\.\d+)?/
}

def convertNumbers(element) {
    if (element instanceof List) {
        // Element is a List; recursively convert each item in the list
        return element.collect { convertNumbers(it) }
    } else if (element instanceof Map) {
        // Element is a Map; recursively convert each value in the map
        return element.collectEntries { key, value -> [(key): convertNumbers(value)] }
    } else if (element instanceof String) {
        // Element is a String; attempt to convert to a number if possible
        if (element.isNumber()) {
            return element.contains('.') ? element.toFloat() : element.toInteger()
        } else {
            // Keep as String
            return element
        }
    } else {
        // For all other types, return the element as is
        return element
    }
}

String getHawkAuthentication(String id, String secret, String key, String path) {
    Integer timestamp = now() / 1000
    String nonce = UUID.randomUUID().toString().replaceAll('-', '').take(8)
    String prestr = "$id:$secret:${nonce}:${timestamp}:${md5hex(path)}::"
    
    Mac mac = Mac.getInstance("HmacSHA256")
    SecretKeySpec secretKeySpec = new SecretKeySpec(key?.getBytes("UTF-8"), "HmacSHA256")
    mac.init(secretKeySpec)
    byte[] macBytes = mac.doFinal(prestr.getBytes("UTF-8"))
    String macString = macBytes.encodeBase64().toString()

    return "Hawk id=\"${id}\", s=\"${secret}\", ts=\"${timestamp}\", nonce=\"${nonce}\", mac=\"${macString}\""
}

String generateHash(String username) {
    MessageDigest md = MessageDigest.getInstance("MD5")
    md.update(username.bytes)
    md.update(device.deviceNetworkId.bytes)
    byte[] finalHash = md.digest()
    return finalHash.encodeBase64().toString()
}

String getBaseURL() {
    String uri = settings.regionUri
    String path = "/api/v1/getUrlByEmail"
    String queryString = "email=${URLEncoder.encode(settings.username, 'UTF-8')}"

    String response = null
    httpPostJson(uri:uri, path:path, queryString:queryString) { resp ->
        if(resp.status == 200) {
            response = resp.data?.data?.url
            if(response && response!=settings.regionUri) {
                logWarn "${device.displayName} found username:'${settings.username}' base url:'$response'"
                state.base = response
            }                
        } else {
            logWarn "${device.displayName} 'getBaseURL()' failure. Status code:${response.getStatus()}"
        }
    }
    return response
}

Map login() {   
    String uri = getBaseURL() ?: settings.regionUri
    String path = "/api/v1/login"
    String queryString = "username=${URLEncoder.encode(settings.username, 'UTF-8')}&" + "password=${URLEncoder.encode(settings.password, 'UTF-8')}&" + "needtwostepauth=${URLEncoder.encode('false', 'UTF-8')}"
    // Hash the username with MD5 and encode it to Base64 for the client ID header
    String headerClientId = generateHash(settings.username)
    Map headers = ['header_clientid':headerClientId]   
    
    Map response = [:]
    httpPostJson(uri:uri, path:path, queryString:queryString, headers:headers) { resp ->
        if(resp.status == 200) {
			logDebug "${device.displayName} login results (<b>*** DO NOT SHARE ***</b>): ${resp?.data}"
			if(resp.data?.msg != "success") { logWarn "${device.displayName} login failure. Driver only supports Roborock (not Xiaomi) integrations"; return response; }
            storeJsonState( "login", datetimestring(), resp.data )
            response = resp.data			
        } else {
            logWarn "${device.displayName} 'login()' failure. Status code:${response.getStatus()}"
        }
    }
    g_mGetLoginData[device.getIdAsLong()]?.clear()
    g_mGetLoginData[device.getIdAsLong()] = null
    return response
}

void getHomeDetail() {
    Map params = [
        uri:  state?.base ?: settings.regionUri,
        path: "/api/v1/getHomeDetail",
        headers: ['header_clientid':(md5hex(settings.username).bytes.encodeBase64().toString()), 'Authorization': (getLoginData()?.token) ]
    ]
    try {
	    asynchttpGet("asyncHttpCallback", params, [method: "getHomeDetail", store: "homeDetail"])
	} catch (e) {
	    logWarn "${device.displayName} 'getHomeDetail()' asynchttpGet() error: $e"
	}
}

void getHomeData() {
    Map rriot = getLoginData()?.rriot
    String rrHomeId = getHomeDetailData()?.rrHomeId
    String path = "/v2/user/homes/$rrHomeId" // or "/user/homes/$rrHomeId",
    Map params = [
        uri: rriot?.r?.a,
        path: path,
        headers: [ 'Authorization': getHawkAuthentication(rriot?.u, rriot?.s, rriot?.h, path) ]
    ]
    try {
	    asynchttpGet("asyncHttpCallback", params, [method: "getHomeData", store: "homeData"])
	} catch (e) {
	    logWarn "${device.displayName} 'getHomeData()' asynchttpGet() error: $e"
	}
}

void getHomeRooms() {
    Map rriot = getLoginData()?.rriot
    String rrHomeId = getHomeDetailData()?.rrHomeId
    String path   = "/user/homes/$rrHomeId/rooms"
    Map params = [
        uri: rriot?.r?.a,
        path: path,
        headers: [ 'Authorization': getHawkAuthentication(rriot?.u, rriot?.s, rriot?.h, path) ]
    ]
    try {
	    asynchttpGet("asyncHttpCallback", params, [method: "getHomeRooms", store: "homeRooms"])
	} catch (e) {
	    logWarn "${device.displayName} 'getHomeRooms()' asynchttpGet() error: $e"
	}
}

void getDeviceScenes() {
    Map rriot = getLoginData()?.rriot
    String path =  "/user/scene/device/${getDeviceId()}"
    Map params = [
        uri: rriot?.r?.a,
        path: path,
        headers: [ 'Authorization': getHawkAuthentication(rriot?.u, rriot?.s, rriot?.h, path) ]
    ]
    try {
	    asynchttpGet("asyncHttpCallback", params, [method: "getDeviceScenes"])
	} catch (e) {
	    logWarn "${device.displayName} 'getDeviceScenes()' asynchttpGet() error: $e"
	}
}

void setDeviceScene(String sceneId) {
    Map rriot = getLoginData()?.rriot
    String path = "/user/scene/$sceneId/execute"
    Map params = [
        uri: rriot?.r?.a,
        path: path,
        headers: [ 'Authorization': getHawkAuthentication(rriot?.u, rriot?.s, rriot?.h, path) ],
        contentType: "application/json",
        body: [ sceneId: sceneId ]
    ]
    try {
	    asynchttpPost("asyncHttpCallback", params, [method: "setDeviceScene", sceneId: sceneId])
	} catch (e) {
	    logWarn "${device.displayName} 'setDeviceScene()' asynchttpPost() error: $e"
	}
}

void asyncHttpCallback(resp, data) {
    logDebug "${device.displayName} executing 'asyncHttpCallback()' status: ${resp.status} method: ${data?.method}"
    
    if (resp.status == 200) {
        resp.headers.each { logTrace "${it.key} : ${it.value}" }
        logTrace "response data: ${resp.data}"
        Map respJson = new JsonSlurper().parseText(resp.data)
        respJson.timestamp = now() // not used for anything yet.
        switch(data?.method) { 
            case "getHomeDetail":               
                storeJsonState( data?.store, datetimestring(), respJson )
                g_mGetHomeDetail[device.getIdAsLong()]?.clear()
                g_mGetHomeDetail[device.getIdAsLong()] = respJson
                getHomeData()
                break
            case "getHomeData":
                synchronized (this) {
                    storeJsonState( data?.store, datetimestring(), respJson )
                    g_mGetHomeData[device.getIdAsLong()]?.clear()
                    g_mGetHomeData[device.getIdAsLong()] = respJson
                }
                getHomeDataCallback()
                getDeviceScenes()
                break
            case "getHomeRooms": // not used
                //storeJsonState( data?.store, datetimestring(), respJson )
                break
            case "getDeviceScenes":
                respJson?.result?.each { logTrace it }
                Map scenes = respJson?.result?.collectEntries{ [(it.id.toString()): it.name] }
                processEvent("scenes", scenes?.sort())
                break
            case "setDeviceScene":
                logInfo "${device.displayName} ${respJson?.status=="ok"?"accepted":"rejected"} sceneId:$data.sceneId"
                break
            default:
                logWarn "${device.displayName} asyncHttpGetCallback() ${data?.method} not supported"
                if (resp?.data) { logInfo resp.data }
        }
    }
    else {
        logWarn("${device.displayName} asyncHttpGetCallback() ${data?.method} status:${resp.status}")
    }
}

void storeJsonState(String name, String visible, Map hidden) {
    String encoded64 = JsonOutput.toJson(hidden).getBytes("UTF-8").encodeBase64().toString()
    state[name] = """<root><span class="visible-data">[ date: ${visible}, size: ${encoded64.size()} ]</span><span class="hidden-data" style="display:none;" data-hidden="${encoded64}">${name} placeholder</span></root>"""
}

Map fetchJsonState(String name) {
    if(state?."$name"==null) return [:]
    def slurper = new XmlSlurper().parseText((state[name]))
    //String visibleText = slurper.span.find { it.@class == 'visible-data' }?.text()
    String encodedData = slurper?.span?.find { it.@class == 'hidden-data' }?.@'data-hidden'
    return parseJsonFromBase64( encodedData )
}

// Function to find the 'next' device given a 'duid'
String findNextDevice(String duid=null) {
    List sortedDevices = getHomeDataResult()?.devices?.sort{ a, b -> a.duid <=> b.duid }
    sortedDevices?.result?.products.sort { it.id }?.result?.devices.sort { it.duid }
    Integer currentIndex = -1    
    // Check if duid is not null
    if(duid != null) {
        // Attempt to find the index of the device with the given duid
        currentIndex = sortedDevices.findIndexOf { it.duid == duid }
    }    
    // If duid is null or the device is not found, return the first device
    if(duid == null || currentIndex == -1) {
        return sortedDevices[0]?.duid
    }    
    // Calculate the index of the next device, wrapping around if necessary
    Integer nextIndex = (currentIndex + 1) % sortedDevices.size()    
    // Retrieve and return the next device
    return sortedDevices[nextIndex]?.duid
}

String getDeviceId() {
    state.duid = state?.duid ?: findNextDevice()
    return state.duid    
}

String getLocalKey(String deviceId) {
   return getHomeDataResult()?.devices?.find { it.duid == deviceId }?.localKey
}

@Field volatile static Map<Long,Map> g_mGetLoginData = [:]
Map getLoginData() {
    if(g_mGetLoginData[device.getIdAsLong()] == null) {
        logDebug "${device.displayName} executing 'getLoginData()' cache"
        g_mGetLoginData[device.getIdAsLong()] = fetchJsonState("login")
    } 
    return g_mGetLoginData[device.getIdAsLong()]?.data ?: [:]
}

@Field volatile static Map<Long,Map> g_mGetHomeDetail = [:]
Map getHomeDetailData() {
    if(g_mGetHomeDetail[device.getIdAsLong()] == null) {
        logDebug "${device.displayName} executing 'getHomeDetailData()' cache"
        g_mGetHomeDetail[device.getIdAsLong()] = fetchJsonState("homeDetail")
    }
    return g_mGetHomeDetail[device.getIdAsLong()]?.data ?: [:]
}

@Field volatile static Map<Long,Map> g_mGetHomeData = [:]
Map getHomeDataResult() {
    if(g_mGetHomeData[device.getIdAsLong()] == null) {
        synchronized (this) {
            logDebug "${device.displayName} executing 'getHomeDataResult()' cache"
            g_mGetHomeData[device.getIdAsLong()] = fetchJsonState("homeData")
        }
    } 
    return g_mGetHomeData[device.getIdAsLong()]?.result ?: [:]
}

// needed a queue to manage publish messages, otherwise the broker will toss them. 
@Field volatile static Map<Long,Map> qQueue = [:]
private List qGet() {
    if(!qQueue[device.getIdAsLong()]) qQueue[device.getIdAsLong()] = []
    return qQueue[device.getIdAsLong()]
}
    
void qPush(Map map) {
    qGet().removeIf { now() > it?.ts + 30000 } //remove anything older than 30 seconds
    map.ts = now() // Add timestamp
    qGet() << map  // Append map to the end of the list
}

void qClear() {
    qGet().clear()
}

Map qPop() {
    if(qGet().size() > 0) {
        return qGet().remove(0)  // Remove and return the first element
    }
    return null
}

Map qPeek() {
    return qGet().isEmpty() ? null : qGet()[0]
}

Boolean qIsEmpty() {
    return qGet().isEmpty()
}

Integer qSize() {
    return qGet().size()
}

//https://github.com/copystring/ioBroker.roborock/blob/621351f58c6ef6c2d6cd2b9d7525cb8ca763ede8/lib/deviceFeatures.js
@Field static final Map errorCodes = [
	0: "No error",
	1: "Laser sensor fault",
	2: "Collision sensor fault",
	3: "Wheel floating",
	4: "Cliff sensor fault",
	5: "Main brush blocked",
	6: "Side brush blocked",
	7: "Wheel blocked",
	8: "Device stuck",
	9: "Dust bin missing",
	10: "Filter blocked",
	11: "Magnetic field detected",
	12: "Low battery",
	13: "Charging problem",
	14: "Battery failure",
	15: "Wall sensor fault",
	16: "Uneven surface",
	17: "Side brush failure",
	18: "Suction fan failure",
	19: "Unpowered charging station",
	20: "Unknown Error",
	21: "Laser pressure sensor problem",
	22: "Charge sensor problem",
	23: "Dock problem",
	24: "No-go zone or invisible wall detected",
	254: "Bin full",
	255: "Internal error",
    256: "Wifi Offline",  // added 1.1.2 and deprecated wifi attribute
    257: "Authorization error", // added 1.1.5
]

@Field static final List stateDoNotRefreshCodes = [ 0,1,2,3,9,10,12,14,100 ]
@Field static final Map stateCodes  = [
	0: "Unknown",
	1: "Initiating",
	2: "Sleeping",
	3: "Idle",
	4: "Remote Control",
	5: "Cleaning",
	6: "Returning Dock",
	7: "Manual Mode",
	8: "Charging",
	9: "Charging Error",
	10: "Paused",
	11: "Spot Cleaning",
	12: "In Error",
	13: "Shutting Down",
	14: "Updating",
	15: "Docking",
	16: "Go To",
	17: "Zone Clean",
	18: "Room Clean",
	22: "Emptying Dust Bin",
	23: "Washing the mop",
	26: "Going to wash the mop",
	28: "In call",
	29: "Mapping",
	100: "Charged",
]

@Field static final Map fanPowerCodes  = [
	101: "Quiet",
	102: "Balanced",
	103: "Turbo",
	104: "Max",
    105: "Off",
    106: "Auto",
    108: "Max+",
]

@Field static final Map mopModeCodes  = [
	300: "Standard",
	301: "Deep",
    302: "Custom",
	303: "Deep+",
    304: "Fast",
]

// https://github.com/marcelrv/XiaomiRobotVacuumProtocol/blob/master/water_box_custom_mode.md
@Field static final Map mopWaterModeCodes  = [
    0: "Default",
	200: "Off",
	201: "Low",
    202: "Medium",
	203: "High",
    204: "Auto",
    207: "Custom",
]

//https://github.com/humbertogontijo/python-roborock/blob/main/roborock/code_mappings.py
@Field static final Map dockErrorCodes  = [
    0: "No error",
    34: "Duct Blockage",
    38: "Water Empty",
    39: "Waste Water Tank Full",
    44: "Dirty Tank Latch Open",
    46: "No Dust Bin",
    53: "Cleaning Tank Full Blocked",
]

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
