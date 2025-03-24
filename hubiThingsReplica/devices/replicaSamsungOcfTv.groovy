/**
*  Copyright 2025 Bloodtick
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
*/
public static String version() {return "1.3.3"}

import groovy.transform.CompileStatic
import groovy.transform.Field
@Field volatile static Map<String,Boolean> g_mSecondRefreshRequired = [:]

metadata 
{
    definition(name: "Replica Samsung OCF TV", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaSamsungOcfTv.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Switch"
        capability "Switch Level" // added for Alexa
        capability "Refresh"
        capability "MediaInputSource"
        capability "AudioVolume"
        capability "TV"
        capability "Initialize"
        // Special capablity to allow for Hubitat dashboarding to set commands via the Button template
        // Use Hubitat 'Button Controller' built in app to set commands to run. Buttons below 50 are reserved.
        capability "PushableButton"
    
        attribute "mediaInputSourceName", "string"
        attribute "supportedInputSources", "JSON_OBJECT"
        
        command "setPictureMode", [[name: "mode*", type: "STRING", description: "Set the picture mode"]]        
        //attribute "pictureMode", "string" //capability "TV" is picture
        attribute "supportedPictureModes", "JSON_OBJECT"
        
        command "setSoundMode", [[name: "mode*", type: "STRING", description: "Set the sound mode"]]
        //attribute "soundMode", "string" //capability "TV" is sound
        attribute "supportedSoundModes", "JSON_OBJECT"
        
        command "setAppName", [[name: "appId*", type: "STRING", description: "The app id as used with launchApp command"],[name: "name*", type: "STRING", description: "The name to use for this app id"]]
        command "launchApp", [[name: "appId*", type: "STRING", description: "Launch application by app id"]]
        command "setChannel", [[name: "channel*", type: "NUMBER", description: "Set the digital channel"]]
        //attribute "tvChannel", "number" //capability "TV" is channel
        attribute "channelName", "string" //this will carry the app name, channel number, or input name if available

        command "remoteControl", [ //default is "PRESS_AND_RELEASED"
            [name: "keyValue*", type: "ENUM", description: "Remote Button Value", constraints: ["OK","UP","DOWN","LEFT","RIGHT","BACK","MENU","HOME","PLAY","PAUSE","REWIND","FASTFORWARD","STOP",
                                                                                                "CHANNELUP","CHANNELDOWN","VOLUMEUP","VOLUMEDOWN","MUTETOGGLE","SOUNDMODETOGGLE","PICTUREMODETOGGLE",
                                                                                                "KEYPAD_0","KEYPAD_1","KEYPAD_2","KEYPAD_3","KEYPAD_4","KEYPAD_5","KEYPAD_6","KEYPAD_7","KEYPAD_8","KEYPAD_9",
                                                                                                "KEYPAD_CLEAR","KEYPAD_ENTER"] ], 
            [name: "keyState", type: "ENUM", description: "Remote Button Action State", constraints: ["PRESS_AND_RELEASED","PRESSED","RELEASED"]]
        ]
        attribute "keypad", "string"       
        // commented out since no one has describe execute functions as of 2024-08-03
        //command "execute", [[name: "command*", type: "STRING", description: "Command to execute"],[name: "args", type: "JSON_OBJECT", description: "Raw messages to be passed to a device"]] //capability "execute" in SmartThings
        attribute "data", "JSON_OBJECT" //capability "execute" in SmartThings
        
        attribute "localPoll", "enum", ["initialize", "disabled", "enabled", "error" ]
        attribute "volumePattern", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {
        input(name:"numberOfButtons", type: "number", title: "Set Number of Buttons:", range: "1...", defaultValue: 50, required: true)
        input(name:"deviceIp", type:"text", title: "Device IP Address:", description: "Local lan IPv4 address to enable <b>fast local poll</b> for volume updates when device is on. Disable with invalid IPv4 address or 127.0.0.1", defaultValue: "127.0.0.1", required: false)
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
        input(name:"deviceDebugEnable", type: "bool", title: "Enable Debug logging:", defaultValue: false)
        input(name:"deviceDefaultReset", type: "bool", title: "Reset Device to Default State (use with &#9888; caution):", defaultValue: false)
    }
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "${device.displayName} disabling debug logs"
}
Boolean autoLogsOff() { if ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) runIn(1800, "logsOff"); else unschedule('logsOff');}

def push(buttonNumber) {
    remoteControl(buttonNumber.toString())
}

def installed() {
	initialize()
}

def updated() {
	initialize()    
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
    
    if(settings?.deviceDefaultReset == true) {
        device.updateSetting("deviceDefaultReset",[value:'false',type:"bool"])
        // blow away all state information
        state?.keySet()?.collect()?.each{ state.remove(it) }
        // blow away all data information except for replica controller
        device.data.keySet()?.collect()?.each{ if(it!="replica") device.removeDataValue(it) }
        // blow away all attribute information. not sure if this 'is the way' but it worked.
        device.currentStates?.collect{ ((new groovy.json.JsonSlurper().parseText( groovy.json.JsonOutput.toJson(it) ))?.name) }?.each{ device.deleteCurrentState(it) }        
        runIn(1, configure)
   } else runIn(1, refresh)
    autoLogsOff()
    sendEvent(name:"numberOfButtons", value: (settings?.numberOfButtons)?:50)
    // Special localPoll for more-realtime volume changes. I understand this is how Home Assistant works too.
    runIn(1, "initPollVolume")
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ "setTvChannelValue":[[name:"tvChannel*",type:"STRING"]], "setTvChannelNameValue":[[name:"tvChannelName*",type:"STRING"]], "setDataValue":[[name:"data*",type:"JSON_OBJECT"]],
              "setSupportedSoundModesValue":[[name:"supportedSoundModes*",type:"STRING"]], "setSoundModeValue":[[name:"soundMode*",type:"STRING"]],
              "setSupportedPictureModesValue":[[name:"supportedPictureModes*",type:"STRING"]], "setPictureModeValue":[[name:"pictureMode*",type:"STRING"]],  
              "setSupportedInputSourcesValue":[[name:"supportedInputSources*",type:"STRING"]], "setSupportedInputSourcesMapValue":[[name:"supportedInputSourcesMap*",type:"STRING"]], "setMediaInputSourceValue":[[name:"mediaInputSource*",type:"JSON_OBJECT"]], 
              "setMuteValue":[[name:"mute*",type:"ENUM"]], "setVolumeValue":[[name:"volume*",type:"NUMBER"]], "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], 
              "setSupportsPowerOnByOcf":[[name:"supportsPowerOnByOcf*",type:"ENUM"]] , "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setTvChannelValue(value) {
    logDebug "${device.displayName} executing 'setTvChannelValue($value)' second refresh:${g_mSecondRefreshRequired[device.getId()]}"
    value = (device.currentValue("switch")=="off") ? 0 : (value ? value?.toInteger() : 0)
    if(g_mSecondRefreshRequired[device.getId()] || value==device.currentValue("channel")) return
    
    String descriptionText = "${device.displayName} channel is $value"
    sendEvent(name: "channel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setTvChannelNameValue(value) {
    logDebug "${device.displayName} executing 'setTvChannelNameValue($value)' second refresh:${g_mSecondRefreshRequired[device.getId()]}"
    value = getAppName( value ?: (device.currentValue("mediaInputSourceName")) ?: "not available" )
    if(g_mSecondRefreshRequired[device.getId()] || value==device.currentValue("channelName")) return    
    
    String descriptionText = "${device.displayName} channel name is $value"
    sendEvent(name: "channelName", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

String getAppName(String value) {
    logDebug "${device.displayName} executing 'getAppName($value)'"
    if(device.currentValue("switch")=="off") return "{name}"
    //this should be an add if contains a '.' and has no spaces. not sure if this logic holds for everything. 
    if(!value.isEmpty() && !value.contains(' ') && (value =~ /\w+\.\w+/)) {
        String id = value
        String name = value.substring(value.lastIndexOf('.') + 1)        
        // Parse the existing list or initialize a new one
        List list = state?.supportedAppSourcesMap ? (new groovy.json.JsonSlurper().parseText(state.supportedAppSourcesMap)) : []
        // Attempt to find the element with the 'id'
        Map existingElement = list.find { it['id'] == id }        
        // If found, return its 'name'
        if (existingElement) {
            return existingElement['name']
        } else {
            // If not found, add the new id-name pair to the list and update the state
            list.add(['id': id, 'name': name])
            state.supportedAppSourcesMap = groovy.json.JsonOutput.toJson(list)
            return name
        }
    } else {
        return value
    }
}

def setAppName(String id, String name) {
    logDebug "${device.displayName} executing 'setAppName($id,$name)'"
    List list = state?.supportedAppSourcesMap ? (new groovy.json.JsonSlurper().parseText(state.supportedAppSourcesMap)) : []    
    // Find the map with the matching 'id'
    Map item = list.find { it['id'] == id }    
    // If found, update the 'name'
    if(item) {        
        item['name'] = name
        state.supportedAppSourcesMap=groovy.json.JsonOutput.toJson(list)
        log.info "${device.displayName} updated appId '$id' to name '$name'"
        runIn(1, refresh)
    }    
}

def setSupportedSoundModesValue(value) {
    List supportedSoundModes = (device.currentValue("supportedSoundModes")!=null) ? (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedSoundModes"))) : []
    if(value==null || value.sort()==supportedSoundModes.sort()) return
    
    String descriptionText = "${device.displayName} supported sound modes are $value"
    sendEvent(name: "supportedSoundModes", value: groovy.json.JsonOutput.toJson(value), descriptionText: descriptionText)
    logInfo descriptionText
}

def setSoundModeValue(value) {
    if(g_mSecondRefreshRequired[device.getId()] || value==device.currentValue("sound")) return
    
    String descriptionText = "${device.displayName} sound is $value"
    sendEvent(name: "sound", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def toggleSoundMode() {
    List supportedSoundModes = (device.currentValue("supportedSoundModes")!=null) ? (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedSoundModes"))) : []
    String sound = device.currentValue("sound")    
    setSoundMode(supportedSoundModes.isEmpty() ? sound : supportedSoundModes[ ((supportedSoundModes.indexOf(sound)+1) % supportedSoundModes.size()) ])
} 

def setSupportedPictureModesValue(value) {
    List supportedPictureModes = (device.currentValue("supportedPictureModes")!=null) ? (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedPictureModes"))) : []
    if(value==null || value.sort()==supportedPictureModes.sort()) return
    
    String descriptionText = "${device.displayName} supported picture modes are $value"
    sendEvent(name: "supportedPictureModes", value: groovy.json.JsonOutput.toJson(value), descriptionText: descriptionText)
    logInfo descriptionText
}

def setPictureModeValue(value) {
    if(g_mSecondRefreshRequired[device.getId()] || value==device.currentValue("picture")) return
    
    String descriptionText = "${device.displayName} picture mode is $value"
    sendEvent(name: "picture", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def togglePictureMode() {
    List supportedPictureModes = (device.currentValue("supportedPictureModes")!=null) ? (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedPictureModes"))) : []
    String picture = device.currentValue("picture")    
    setPictureMode(supportedPictureModes.isEmpty() ? picture : supportedPictureModes[ ((supportedPictureModes.indexOf(picture)+1) % supportedPictureModes.size()) ])
}    

def setSupportedInputSourcesMapValue(value) {
    logDebug "${device.displayName} executing 'setSupportedInputSourcesMapValue($value)' second refresh:${g_mSecondRefreshRequired[device.getId()]}"
    List supportedInputSourcesMap = state?.supportedInputSourcesMap ? (new groovy.json.JsonSlurper().parseText(state.supportedInputSourcesMap)) : []
    if(value==null || value.sort()==supportedInputSourcesMap.sort()) return 
    
    state.supportedInputSourcesMap=groovy.json.JsonOutput.toJson(value)
    logInfo "${device.displayName} supported input sources map is $value"
}

def setSupportedInputSourcesValue(value) {
    // old school vs new school. See setMediaInputSourceValue for explanation. mediaInputSource:inputSource was an enum shown here.
    if(state?.mediaInputSource!="samsungvd.mediaInputSource" && value?.size()==0) value=["AM","CD","FM","HDMI","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","digitalTv","USB",
                                                                                         "YouTube","aux","bluetooth","digital","melon","wifi","network","optical","coaxial","analog1","analog2","analog3","phono"]    
    List supportedInputSourcesMap = state?.supportedInputSourcesMap ? (new groovy.json.JsonSlurper().parseText(state.supportedInputSourcesMap)) : []
    if(supportedInputSourcesMap.size()) value = supportedInputSourcesMap?.collect { it.id }?.flatten().sort{ a,b -> b <=> a }
    List supportedInputSources = (device.currentValue("supportedInputSources")!=null) ? (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedInputSources"))) : []
    logDebug "${device.displayName} executing 'setSupportedInputSourcesValue($value)'"
    if(value==null || value.sort()==supportedInputSources.sort()) return  

    String descriptionText = "${device.displayName} supported input sources are $value"
    sendEvent(name: "supportedInputSources", value: groovy.json.JsonOutput.toJson(value), descriptionText: descriptionText)
    logInfo descriptionText
}

def setMediaInputSourceValue(jsonValue) { //JSON_OBJECT is set so need to handle different
    if(jsonValue==null) return    
    // so it looks like ST changed from mediaInputSource:inputSource to samsungvd.mediaInputSource:inputSource, but then sometimes they don't match. Let the samsungvd.mediaInputSource:inputSource to truth
    if(state?.mediaInputSource=="samsungvd.mediaInputSource" && jsonValue?.capability!="samsungvd.mediaInputSource") return
    else if(jsonValue?.capability=="samsungvd.mediaInputSource" && state?.mediaInputSource!="samsungvd.mediaInputSource") state.mediaInputSource="samsungvd.mediaInputSource"
    
    List supportedInputSourcesMap = state?.supportedInputSourcesMap ? (new groovy.json.JsonSlurper().parseText(state.supportedInputSourcesMap)) : []
    String mediaInputSourceName = supportedInputSourcesMap?.find{ it.id == jsonValue?.value }?.name
    if(mediaInputSourceName) sendEvent(name: "mediaInputSourceName", value: mediaInputSourceName, descriptionText: "${device.displayName} media input source name is $mediaInputSourceName")
    if(jsonValue?.value==device.currentValue("mediaInputSource")) return
    
    String descriptionText = "${device.displayName} media input source is ${jsonValue?.value}"
    sendEvent(name: "mediaInputSource", value: jsonValue?.value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMuteValue(value) {
    if(value==device.currentValue("mute")) return
    
    String descriptionText = "${device.displayName} mute is $value"
    sendEvent(name: "mute", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setVolumeValue(value) {
    if(value==device.currentValue("volume")) return
    
    String descriptionText = "${device.displayName} volume is $value%"
    sendEvent(name: "volume", value: value, unit:"%", descriptionText: descriptionText)
    sendEvent(name: "level", value: value, unit:"%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchValue(value) {
    logDebug "${device.displayName} executing 'setSwitchValue($value)' second refresh:${g_mSecondRefreshRequired[device.getId()]}"
    if(value==device.currentValue("switch")) return    
    
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)   
    logInfo descriptionText    
    runIn(1, refresh)
    // will turn on when set on & proper ip is set, turn off when set is off.
    runIn(value=="off" ? 1 : 5, "initPollVolume")
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

def setSupportsPowerOnByOcf(value) {
    if(value==null || value.toBoolean()==state?.supportsPowerOnByOcf) return
    
    state?.supportsPowerOnByOcf = value.toBoolean()
    logInfo "${device.displayName} supports power on by OCF is $value"
}

//capability "execute"
def setDataValue(jsonValue) { //JSON_OBJECT is set so need to handle different
    if(jsonValue==null) return
    
    String descriptionText = "${device.displayName} execute command data is is ${jsonValue?.value}"
    sendEvent(name: "data", value: jsonValue?.value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "off":[] , "on":[], "refresh":[], "mute":[], "unmute":[], "setInputSourceId":[[name:"id*",type:"STRING"]], "setInputSourceMode":[[name:"mode*",type:"STRING"]], "setVolume":[[name:"volume*",type:"NUMBER"]], "volumeDown":[] , "volumeUp":[],
              "channelDown":[] , "channelUp":[], "setChannel":[[name:"channel*",type:"NUMBER"]], "setPictureMode":[[name:"mode*",type:"STRING"]], "setSoundMode":[[name:"mode*",type:"STRING"]],
              "send":[[name:"keyValue*",type:"ENUM"],[name:"keyState",type:"ENUM",data:"keyState"]], "launchApp":[[name:"appId*",type:"STRING"]],
              "stop":[] , "play":[], "pause":[], "rewind":[], "fastForward":[], "execute":[[name:"command*",type:"STRING"],[name:"args",type:"JSON_OBJECT",data:"args"]] ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
    
    if(!(name ==~ /^(off|on|refresh)$/)) runIn(2, refresh) // if "on/off" we will start the refresh on the callback from smartthings
}

//capability "execute"
def execute(command, args=null) {
    sendCommand("execute", command, null, [args: args ? (args[0]=="{"?(new groovy.json.JsonSlurper().parseText(args)) : args) : null])
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

def mute()  {
    sendCommand("mute")
}

def unmute()  {
    sendCommand("unmute")
}

def setInputSource(id) {
    if(state?.mediaInputSource=="samsungvd.mediaInputSource")
        sendCommand("setInputSourceId", id)
    else
        sendCommand("setInputSourceMode", id) //["AM","CD","FM","HDMI","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","digitalTv","USB","YouTube","aux","bluetooth","digital","melon","wifi","network","optical","coaxial","analog1","analog2","analog3","phono"]
}

def setLevel(level,duration=0) { setVolume(level) }
def setVolume(volume) {
    sendCommand("setVolume", volume, "%")
}

def volumeDown() {
    sendCommand("volumeDown")
}

def volumeUp()  {
    sendCommand("volumeUp")
}

def setChannel(channel) {
    sendCommand("setChannel", channel)
}

def channelDown()  {
    sendCommand("channelDown")
}

def channelUp()  {
    sendCommand("channelUp")
}

def setPictureMode(mode) {
    sendCommand("setPictureMode", mode)
}

def setSoundMode(mode) {
    sendCommand("setSoundMode", mode)
}

def launchApp(appId) {
    sendCommand("launchApp", appId)
}

def keypad(String value) {
    logDebug "${device.displayName} executing 'keyPad($value)'"
    if(value=="KEYPAD_CLEAR") {
        sendEvent(name: "keypad", value: "{keypad}")
        unschedule('keypadClear')
    }
    else if(value=="KEYPAD_ENTER") {
        String channel = device.currentValue("keypad") ?: ""
        setChannel(channel.replaceAll("[^0-9]",""))
        sendEvent(name: "keypad", value: "{keypad}")
        unschedule('keypadClear')
    }
    else {
        value = (((device.currentValue("keypad") ?: "").replaceAll("[^0-9]","")) + value).toInteger().toString() //remove all non-numbers and fix if has zeros.
        value = value.length() <= 5 ? value : value.substring(value.length() - 5) //no numbers larger than 5 digits
        sendEvent(name: "keypad", value: "#$value")
        runIn(10, "keypadClear")
    }
}

def keypadClear() {
    keypad("KEYPAD_CLEAR")
}

def remoteControl(String keyValue, String keyState="PRESS_AND_RELEASED") {
    logDebug "${device.displayName} executing 'remoteControl($keyValue,$keyState)'"
    switch(keyValue) {
        case  "0": case "KEYPAD_0": keypad("0"); break
        case  "1": case "KEYPAD_1": keypad("1"); break
        case  "2": case "KEYPAD_2": keypad("2"); break
        case  "3": case "KEYPAD_3": keypad("3"); break
        case  "4": case "KEYPAD_4": keypad("4"); break
        case  "5": case "KEYPAD_5": keypad("5"); break
        case  "6": case "KEYPAD_6": keypad("6"); break
        case  "7": case "KEYPAD_7": keypad("7"); break
        case  "8": case "KEYPAD_8": keypad("8"); break
        case  "9": case "KEYPAD_9": keypad("9"); break
        case "10": case "KEYPAD_0": keypad("0"); break
        case "11": case "KEYPAD_*": keypad("*"); break
        case "12": case "KEYPAD_SPACE": keypad("KEYPAD_SPACE"); break
        case "13": case "KEYPAD_CLEAR": keypad("KEYPAD_CLEAR"); break
        case "14": case "KEYPAD_ENTER": keypad("KEYPAD_ENTER"); break
        case "15": case "OK":     sendCommand("send", "OK",   null, [keyState:keyState]); break
        case "16": case "UP":     sendCommand("send", "UP",   null, [keyState:keyState]); break
        case "17": case "DOWN":   sendCommand("send", "DOWN", null, [keyState:keyState]); break
        case "18": case "LEFT":   sendCommand("send", "LEFT", null, [keyState:keyState]); break
        case "19": case "RIGHT":  sendCommand("send", "RIGHT",null, [keyState:keyState]); break
        case "20": case "BACK":   sendCommand("send", "BACK", null, [keyState:keyState]); break
        case "21": case "MENU":   sendCommand("send", "MENU", null, [keyState:keyState]); break
        case "22": case "HOME":   sendCommand("send", "HOME", null, [keyState:keyState]); break        
        case "23": case "PAUSE":  sendCommand("pause"); break
        case "24": case "PLAY":   sendCommand("play"); break
        case "25": case "STOP":   sendCommand("stop"); break
        case "26": case "REWIND": sendCommand("rewind"); break
        case "27": case "FASTFORWARD": sendCommand("fastForward"); break
        case "28": case "CHANNELUP":   sendCommand("channelUp"); break
        case "29": case "CHANNELDOWN": sendCommand("channelDown"); break
        case "30": case "VOLUMEUP": sendCommand("volumeUp"); break
        case "31": case "VOLUMEDOWN": sendCommand("volumeDown"); break
        case "32": case "MUTETOGGLE": sendCommand( (device.currentValue("mute")=="unmuted") ? "mute" : "unmute" );  break
        case "33": case "SOUNDMODETOGGLE": toggleSoundMode(); break
        case "34": case "PICTUREMODETOGGLE": togglePictureMode(); break        
        // reserved up to 49 for future internal use
        default:
            Integer reserveButtons = 50
            Integer numberOfButtons = (device.currentValue("numberOfButtons")?.toInteger() ?: 0)
            Integer keyValueInt = 0        
            try { 
                keyValueInt = keyValue.toInteger()
            } catch (NumberFormatException e) {
                logWarn "${device.displayName} could not convert $keyValue to a number"
            }            
            if(keyValueInt>=reserveButtons && numberOfButtons>=keyValueInt) {               
                sendEvent(name: "pushed", value: keyValue, isStateChange: true)
            }
            else {
                logWarn "${device.displayName} ${keyValueInt<reserveButtons ? "pushed $keyValueInt but buttons below $reserveButtons are reserved" : "setNumberOfButtons $numberOfButtons is less than $keyValue. Please update via setNumberOfButtons and resubmit."}"
            }
            break
    }
}

void refresh() {
    if(g_mSecondRefreshRequired[device.getId()]) {
        g_mSecondRefreshRequired[device.getId()] = false
        if(device.currentValue("switch")=="on") runIn(300, refresh)
     } else {
        g_mSecondRefreshRequired[device.getId()] = true
        runIn(4, refresh)
    }    
    sendCommand("refresh") 
    logDebug "${device.displayName} completed 'refresh()' second refresh:${g_mSecondRefreshRequired[device.getId()]}"
}

def initPollVolume() {
    unschedule("pollVolume")
    unschedule("pollVolumeWatchdog")
    if(device.currentValue("switch")=="on" && validIp(deviceIp) && !deviceIp?.startsWith("127.")) {
        sendEvent(name: "localPoll", value: "initialize")
        pollVolume(true)
    } else if(device.currentValue("localPoll")!="disabled") {
        sendEvent(name: "localPoll", value: "disabled")
        if(!validIp(deviceIp) || deviceIp?.startsWith("127.")) device.deleteCurrentState("volumePattern")
        logInfo "${device.displayName} stopping fast volume polling"
    }
}

@Field static final Integer VOLUME_POLL_DELAY = 1000
@Field static final Integer VOLUME_POLL_INACTIVITY_DELAY = 5000
@Field volatile static Map<Long,Map> g_cachePollVolumeMap = [:]
void pollVolume(Boolean initialize=false) {
    Long devId = device.getIdAsLong()   
    if (g_cachePollVolumeMap[devId]==null || initialize) {
        String host = "${deviceIp}:9197"
        String urn = "urn:schemas-upnp-org:service:RenderingControl:1"
        String action = "GetVolume"
        String body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body><u:${action} xmlns:u="${urn}"><InstanceID>0</InstanceID><Channel>Master</Channel></u:${action}></s:Body>
            </s:Envelope>
        """.trim()

        Map headers = [
            "HOST": host,
            "SOAPACTION": "\"${urn}#${action}\"",
            "CONTENT-TYPE": "text/xml; charset=\"utf-8\"",
            "CONTENT-LENGTH": "${body.length()}"
        ]

        // Create the full map
        g_cachePollVolumeMap[devId] = [
            runInMillis: VOLUME_POLL_DELAY,
            body: body,
            headers: headers,
            lastVolume: -1,
            lastChangeTimestamp: now(),
            timestamp: now()            
        ]
        if(initialize) logInfo "${device.displayName} initialized fast volume polling to $deviceIp"
    }
    Map pollData = g_cachePollVolumeMap[devId]
    pollData.timestamp = now()

    // Use sendHubCommand because it is faster and lower overhead then normal http stack    
    def hubAction = new hubitat.device.HubAction([
        method: "POST",
        path: "/upnp/control/AVTransport1",
        body: pollData.body,
        headers: pollData.headers
    ], null, [callback: pollVolumeCallback])
    // There is no error try/catch with sendHubCommand. So use a watchdog to restart if callback doesn't happen.
    sendHubCommand(hubAction)
    runInMillis((VOLUME_POLL_INACTIVITY_DELAY + 2000) as Integer, "pollVolumeWatchdog")    
}

void pollVolumeWatchdog(String descriptionText="watchdog timer expired") {
    unschedule("pollVolumeWatchdog")
    state.localPollDelay = state?.localPollDelay ? Math.min( state.localPollDelay * 2, 600 ) : 2
    if(state?.localPollDelay>2) log.warn "${device.displayName} delaying poll volume retry by $state.localPollDelay seconds with reason: $descriptionText"
    sendEvent(name: "localPoll", value: "error", descriptionText: descriptionText)
    runIn(state.localPollDelay, "initPollVolume")
}    

void pollVolumeCallback(hubResponse) {
    Long devId = device.getIdAsLong()
    if (g_cachePollVolumeMap[devId]==null) {
        pollVolume()
        return
    }
    Map pollData = g_cachePollVolumeMap[devId]

    try {
        Map msg = parseLanMessage(hubResponse.description)
        logDebug "${device.displayName} pollVolumeCallback $msg"

        if(!msg?.data || !msg.data.text().isInteger()) {
            throw new IllegalArgumentException("Invalid data from UPnP query: $msg")
        }

        Integer newVolume = msg.data.toInteger()
        Integer lastVolume = pollData.lastVolume

        if(newVolume != lastVolume) {
            setVolumeValue(newVolume)
            sendEvent(name: "localPoll", value: "enabled")
            if(state.containsKey('localPollDelay')) state.remove("localPollDelay")
            // speed up the check for while
            pollData.runInMillis = (VOLUME_POLL_DELAY / 2) as Integer
            pollData.lastChangeTimestamp = now()
			// the entire reason I built this poll volume was for the volumePattern matcher. :) 
            String direction = (newVolume > lastVolume) ? "+" : "-"
            pollData.lastVolume = newVolume
            state.volumePattern = (state?.volumePattern ?: "") + direction
            runInMillis(VOLUME_POLL_DELAY * 1.5 as Integer, volumePattern)
        } else if(now() > pollData.lastChangeTimestamp + 10*60*1000) { // slow down after 10 min of no activity
            pollData.runInMillis = VOLUME_POLL_INACTIVITY_DELAY
        } else if(now() > pollData.lastChangeTimestamp + 10*1000) { // go normal after 10 seconds
            pollData.runInMillis = VOLUME_POLL_DELAY
        }

    } catch (Exception e) {    
        pollVolumeWatchdog(e.message)
        return
    }
	// remove our function delay, and never allow lower than 250ms.
	Integer runInMillisDelay = Math.max((pollData?.runInMillis ?: VOLUME_POLL_DELAY) - (now() - (pollData?.timestamp ?: 0)), 250)
	logDebug "${device.displayName} scheduling pollVolume in $runInMillisDelay ms"
	runInMillis(runInMillisDelay as Integer, "pollVolume")
}

void volumePattern() {
    if(state?.volumePattern?.size()>3) sendEvent(name: "volumePattern", value: state.volumePattern, isStateChange: true)
    state.remove('volumePattern')
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"command":{"capability":"switch","label":"command: off()","name":"off","type":"command"},"trigger":{"label":"command: off()","name":"off","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"switch","label":"command: on()","name":"on","type":"command"},"trigger":{"label":"command: on()","name":"on","type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setSwitchValue(switch*)","name":"setSwitchValue","parameters":[{"name":"switch*","type":"ENUM"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"switch","capability":"switch","label":"attribute: switch.*","properties":{"value":{"title":"SwitchState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setHealthStatusValue(healthStatus*)","name":"setHealthStatusValue","parameters":[{"name":"healthStatus*","type":"ENUM"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"healthStatus","capability":"healthCheck","label":"attribute: healthStatus.*","properties":{"value":{"title":"HealthState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"capability":"audioMute","label":"command: unmute()","name":"unmute","type":"command"},"trigger":{"label":"command: unmute()","name":"unmute","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"audioMute","label":"command: mute()","name":"mute","type":"command"},"trigger":{"label":"command: mute()","name":"mute","type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"volume","optional":false,"schema":{"maximum":100,"minimum":0,"type":"integer"}}],"capability":"audioVolume","label":"command: setVolume(volume*)","name":"setVolume","type":"command"},"trigger":{"label":"command: setVolume(volume*)","name":"setVolume","parameters":[{"name":"volume*","type":"NUMBER"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setMuteValue(mute*)","name":"setMuteValue","parameters":[{"name":"mute*","type":"ENUM"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"mute","capability":"audioMute","label":"attribute: mute.*","properties":{"value":{"title":"MuteState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setVolumeValue(volume*)","name":"setVolumeValue","parameters":[{"name":"volume*","type":"NUMBER"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"volume","capability":"audioVolume","label":"attribute: volume.*","properties":{"unit":{"default":"%","enum":["%"],"type":"string"},"value":{"maximum":100,"minimum":0,"type":"integer"}},"required":["value"],"title":"IntegerPercent","type":"attribute"},"type":"smartTrigger"},{"command":{"capability":"audioVolume","label":"command: volumeDown()","name":"volumeDown","type":"command"},"trigger":{"label":"command: volumeDown()","name":"volumeDown","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"refresh","label":"command: refresh:refresh()","name":"refresh","type":"command"},"trigger":{"label":"command: refresh()","name":"refresh","type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setSupportedInputSourcesValue(supportedInputSources*)","name":"setSupportedInputSourcesValue","parameters":[{"name":"supportedInputSources*","type":"ENUM"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"supportedInputSources","capability":"mediaInputSource","label":"attribute: supportedInputSources.*","properties":{"value":{"items":{"enum":["AM","CD","FM","HDMI","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","digitalTv","USB","YouTube","aux","bluetooth","digital","melon","wifi"],"title":"MediaSource","type":"string"},"type":"array"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setSupportedInputSourcesMapValue(supportedInputSourcesMap*)","name":"setSupportedInputSourcesMapValue","parameters":[{"name":"supportedInputSourcesMap*","type":"ENUM"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"supportedInputSourcesMap","capability":"samsungvd.mediaInputSource","label":"attribute: supportedInputSourcesMap.*","properties":{"value":{"items":{"properties":{"id":{"type":"string"},"name":{"type":"string"}},"type":"object"},"required":[],"type":"array"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setSupportedPictureModesValue(supportedPictureModes*)","name":"setSupportedPictureModesValue","parameters":[{"name":"supportedPictureModes*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"supportedPictureModes","capability":"custom.picturemode","label":"attribute: supportedPictureModes.*","properties":{"value":{"type":"array"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setPictureModeValue(pictureMode*)","name":"setPictureModeValue","parameters":[{"name":"pictureMode*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"pictureMode","capability":"custom.picturemode","label":"attribute: pictureMode.*","properties":{"value":{"type":"string"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setSoundModeValue(soundMode*)","name":"setSoundModeValue","parameters":[{"name":"soundMode*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"soundMode","capability":"custom.soundmode","label":"attribute: soundMode.*","properties":{"value":{"type":"string"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setSupportedSoundModesValue(supportedSoundModes*)","name":"setSupportedSoundModesValue","parameters":[{"name":"supportedSoundModes*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"supportedSoundModes","capability":"custom.soundmode","label":"attribute: supportedSoundModes.*","properties":{"value":{"items":{"type":"string"},"type":"array"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setTvChannelValue(tvChannel*)","name":"setTvChannelValue","parameters":[{"name":"tvChannel*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"tvChannel","capability":"tvChannel","label":"attribute: tvChannel.*","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setTvChannelNameValue(tvChannelName*)","name":"setTvChannelNameValue","parameters":[{"name":"tvChannelName*","type":"STRING"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"tvChannelName","capability":"tvChannel","label":"attribute: tvChannelName.*","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"required":[],"type":"attribute"},"type":"smartTrigger"},{"command":{"capability":"tvChannel","label":"command: channelUp()","name":"channelUp","type":"command"},"trigger":{"label":"command: channelUp()","name":"channelUp","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"tvChannel","label":"command: channelDown()","name":"channelDown","type":"command"},"trigger":{"label":"command: channelDown()","name":"channelDown","type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"tvChannel","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],"capability":"tvChannel","label":"command: setTvChannel(tvChannel*)","name":"setTvChannel","type":"command"},"trigger":{"label":"command: setChannel(channel*)","name":"setChannel","parameters":[{"name":"channel*","type":"NUMBER"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setMediaInputSourceValue(mediaInputSource*)","name":"setMediaInputSourceValue","parameters":[{"name":"mediaInputSource*","type":"JSON_OBJECT"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"inputSource","capability":"samsungvd.mediaInputSource","label":"attribute: inputSource.*","properties":{"value":{"type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setMediaInputSourceValue(mediaInputSource*)","name":"setMediaInputSourceValue","parameters":[{"name":"mediaInputSource*","type":"JSON_OBJECT"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"inputSource","capability":"mediaInputSource","label":"attribute: mediaInputSource:inputSource.*","properties":{"value":{"title":"MediaSource","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"arguments":[{"name":"keyValue","optional":false,"schema":{"enum":["UP","DOWN","LEFT","RIGHT","OK","BACK","MENU","HOME"],"type":"string"}},{"name":"keyState","optional":true,"schema":{"enum":["PRESSED","RELEASED","PRESS_AND_RELEASED"],"type":"string"}}],"capability":"samsungvd.remoteControl","label":"command: send(keyValue*, keyState)","name":"send","type":"command"},"trigger":{"label":"command: send(keyValue*, keyState)","name":"send","parameters":[{"name":"keyValue*","type":"ENUM"},{"data":"keyState","name":"keyState","type":"ENUM"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setSupportsPowerOnByOcf(supportsPowerOnByOcf*)","name":"setSupportsPowerOnByOcf","parameters":[{"name":"supportsPowerOnByOcf*","type":"ENUM"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"supportsPowerOnByOcf","capability":"samsungvd.supportsPowerOnByOcf","label":"attribute: supportsPowerOnByOcf.*","properties":{"value":{"type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"arguments":[{"name":"mode","optional":false,"schema":{"type":"string"}}],"capability":"custom.picturemode","label":"command: setPictureMode(mode*)","name":"setPictureMode","type":"command"},"trigger":{"label":"command: setPictureMode(mode*)","name":"setPictureMode","parameters":[{"name":"mode*","type":"STRING"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"mode","optional":false,"schema":{"type":"string"}}],"capability":"custom.soundmode","label":"command: setSoundMode(mode*)","name":"setSoundMode","type":"command"},"trigger":{"label":"command: setSoundMode(mode*)","name":"setSoundMode","parameters":[{"name":"mode*","type":"STRING"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"appId","optional":true,"schema":{"type":"string"}},{"name":"appName","optional":true,"schema":{"type":"string"}}],"capability":"custom.launchapp","label":"command: launchApp(appId, appName)","name":"launchApp","type":"command"},"trigger":{"label":"command: launchApp(appId*)","name":"launchApp","parameters":[{"name":"appId*","type":"STRING"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"mediaPlayback","label":"command: stop()","name":"stop","type":"command"},"trigger":{"label":"command: stop()","name":"stop","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"mediaPlayback","label":"command: rewind()","name":"rewind","type":"command"},"trigger":{"label":"command: rewind()","name":"rewind","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"mediaPlayback","label":"command: fastForward()","name":"fastForward","type":"command"},"trigger":{"label":"command: fastForward()","name":"fastForward","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"mediaPlayback","label":"command: pause()","name":"pause","type":"command"},"trigger":{"label":"command: pause()","name":"pause","type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"mediaPlayback","label":"command: play()","name":"play","type":"command"},"trigger":{"label":"command: play()","name":"play","type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"id","optional":false,"schema":{"type":"string"}}],"capability":"samsungvd.mediaInputSource","label":"command: setInputSource(id*)","name":"setInputSource","type":"command"},"trigger":{"label":"command: setInputSourceId(id*)","name":"setInputSourceId","parameters":[{"name":"id*","type":"STRING"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"arguments":[{"name":"mode","optional":false,"schema":{"enum":["AM","CD","FM","HDMI","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","digitalTv","USB","YouTube","aux","bluetooth","digital","melon","wifi","network","optical","coaxial","analog1","analog2","analog3","phono"],"title":"MediaSource","type":"string"}}],"capability":"mediaInputSource","label":"command: setInputSource(mode*)","name":"setInputSource","type":"command"},"trigger":{"label":"command: setInputSourceMode(mode*)","name":"setInputSourceMode","parameters":[{"name":"mode*","type":"STRING"}],"type":"command"},"type":"hubitatTrigger"},{"command":{"capability":"audioVolume","label":"command: volumeUp()","name":"volumeUp","type":"command"},"mute":true,"trigger":{"label":"command: volumeUp()","name":"volumeUp","type":"command"},"type":"hubitatTrigger"},{"command":{"label":"command: setDataValue(data*)","name":"setDataValue","parameters":[{"name":"data*","type":"JSON_OBJECT"}],"type":"command"},"mute":true,"trigger":{"additionalProperties":false,"attribute":"data","capability":"execute","label":"attribute: data.*","properties":{"data":{"additionalProperties":true,"required":[],"type":"object"},"value":{"title":"JsonObject","type":"object"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"trigger":{"name":"execute","label":"command: execute(command*, args)","type":"command","parameters":[{"name":"command*","type":"STRING"},{"data":"args","name":"args","type":"JSON_OBJECT"}]},"command":{"arguments":[{"name":"command","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}},{"name":"args","optional":true,"schema":{"title":"JsonObject","type":"object"}}],"capability":"execute","label":"command: execute(command*, args)","name":"execute","type":"command"},"type":"hubitatTrigger"}]}"""
}

Boolean validIp(String ip) {
    return (ip && ip ==~ /\b(?:\d{1,3}\.){3}\d{1,3}\b/)
}

String escapeXmlForLog(String input) {
    return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
}
    
private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
