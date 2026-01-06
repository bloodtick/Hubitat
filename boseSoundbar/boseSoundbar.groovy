/**
 *  NOTICE: THIS DRIVER NO LONGER FUNCTIONS. BOSE MOVED TO AZURE AUHTORIZATION AND AWAY FROM GIGYA.
 *
 *  Copyright 2026 Bloodtick Jones
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
 *  Bose Smart Ultra Soundbar
 *
 *  Thanks to: 'cavefire' and the https://github.com/cavefire/pybose project
 *             https://github.com/cavefire/Bose-Homeassistant
 *
 *  Author: bloodtick
 *  Date: 2025-12-06
 */
public static String version() {return "1.0.00"}

import groovy.json.*
import groovy.transform.CompileStatic
import groovy.transform.Field   

/* ============================================================
 *  CONSTANTS
 * ============================================================ */
@Field static final String GIGYA_KEY = "3_7PoVX7ELjlWyppFZFGia1Wf1rNGZv_mqVgtqVmYl3Js-hQxZiFIU8uHxd8G6PyNz"
@Field static final String BOSE_API_KEY = "67616C617061676F732D70726F642D6D61647269642D696F73"
@Field static final String BOSE_SOFTWARE_VERSION = "10.6.6-32768"
@Field static final String BOSE_UA = "MadridApp/10.6.6 (com.bose.bosemusic; build:32768; iOS 18.3.0) Alamofire/5.6.2"
@Field static final String BOSE_UA_GIGYA = "Bose/32768 MySSID/1568.300.101 Darwin/24.2.0"
@Field static final Integer HEALTH_OFFLINE_DELAY_SEC = 30
@Field static final Integer PRESET_COUNT = 6
@Field static final Integer NUMBER_OF_BUTTONS_SET = 42 // should match the last remoteControl case number
@Field static final Map<Integer, String> audioLevels = (-100..100).step(10).collectEntries { v -> [(v): (v > 0 ? "+${v}" : v.toString())] }

metadata {
    definition(name: "Bose Soundbar", namespace: "bloodtick", author: "Hubitat", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/boseSoundbar/boseSoundbar.groovy")
    {
        capability "Actuator"
        capability "Initialize"
        capability "Refresh"
        capability "AudioVolume"
        capability "Switch"
        capability "SwitchLevel" // added for Alexa
        capability "MusicPlayer"
        capability "PushableButton"
        
        //command "disconnect"
        command "remoteControl", [
            [name: "keyValue*",  type: "ENUM", description: "Remote Button Value", constraints: ["POWER_TOGGLE","POWER_ON","POWER_OFF","PRESET_SET","PRESET_1","PRESET_2","PRESET_3","PRESET_4","PRESET_5","PRESET_6","PLAY","PAUSE","REWIND",
                                                                                                 "FAST_FORWARD","STOP","VOLUME_SET","VOLUME_UP","VOLUME_DOWN","MUTE_TOGGLE","MUTE","UNMUTE","SELECT_TV","SELECT_OPTICAL","SELECT_AUX",
                                                                                                 "NUMBER_OF_BUTTONS_SET"]],
            [name: "keyNumeric", type: "NUMBER", description: "Optional Numeric Value for SET commands", constraints: "0..100"],
            [name: "createChild",  type: "ENUM", description: "Create a child device to trigger remote", constraints: ["NONE", "Generic Component Switch"]],// "Generic Component Dimmer"]] 
        ]
        
        command "remoteControlEq", [
            [name: "keyValue*",  type: "ENUM", description: "Remote Button Value", constraints: ["AUDIO_BASE_SET","AUDIO_BASE_UP","AUDIO_BASE_DOWN","AUDIO_CENTER_SET","AUDIO_CENTER_UP","AUDIO_CENTER_DOWN",
                                                                                                 "AUDIO_TREBLE_SET","AUDIO_TREBLE_UP","AUDIO_TREBLE_DOWN","AUDIO_HEIGHT_SET","AUDIO_HEIGHT_UP","AUDIO_HEIGHT_DOWN",
                                                                                                 "AUDIO_WOOFER_SET","AUDIO_WOOFER_UP","AUDIO_WOOFER_DOWN","AUDIO_REARS_SET","AUDIO_REARS_UP","AUDIO_REARS_DOWN"]],
            [name: "keyNumeric*", type: "ENUM", description: "Required Numeric Value for ALL commands", constraints: audioLevels.values().collect{ it } ],
            [name: "createChild",  type: "ENUM", description: "Create a child device to trigger remote", constraints: ["NONE", "Generic Component Switch"]],// "Generic Component Dimmer"]] 
        ]
        
        (1..PRESET_COUNT).each { n -> attribute "preset$n", "string" }
        attribute "trackStation", "string"
        attribute "audioFormat", "string"
        attribute "audioBass",   "enum", audioLevels.values().collect{ it }
        attribute "audioCenter", "enum", audioLevels.values().collect{ it }
        attribute "audioHeight", "enum", audioLevels.values().collect{ it }
        attribute "audioTreble", "enum", audioLevels.values().collect{ it }
        attribute "audioWoofer", "enum", audioLevels.values().collect{ it }
        attribute "audioRears",  "enum", audioLevels.values().collect{ it }
   
        attribute "volumePattern", "string"
        attribute "healthStatus", "enum", ["offline", "online"]
    }
}

preferences {
    input(name:"username", type:"string", title:"<b>Bose Username:</b>", required:true)
    input(name:"password", type:"password", title:"<b>Bose Password:</b>", required:true)
    input(name:"deviceIp", type:"text", title:"Device IP Address:", description:"Local lan IPv4 address", defaultValue:"127.0.0.1", required:true)
    input(name:"deviceInfoDisable", type:"bool", title:"Disable Info logging:", defaultValue:false)
    input(name:"deviceDebugEnable", type:"bool", title:"Enable Debug logging:", defaultValue:false)
    //input(name:"deviceTraceEnable", type:"bool", title:"Enable trace logging", defaultValue:false)
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "disabling debug logs"
}
Boolean autoLogsOff() { if ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) runIn(1800, "logsOff"); else unschedule('logsOff');}

def installed() { /*noop*/ }
def updated() { initialize() }

def refresh() {
    logInfo "executing refresh"
    if(state?.controlToken?.refresh_token) {
		Map ctoken = refreshControlToken(state.controlToken.refresh_token)        
        
        if(!ctoken?.expires_in) {
            runIn(0, "initialize")
        } else {
            state.controlToken = ctoken
            webSocketOpen("refresh")
            runIn(ctoken.expires_in.toInteger() - 3600, "refresh")
        }
    }
}

def push(def buttonNumber) {
	remoteControl(buttonNumber as String)  
}

void setHealthStatusEvent(Map args = [:]) {
    unschedule('setHealthStatusEvent')
    String healthStatus = args.healthStatus ?: "offline" 
    logDebug "setHealthStatusEvent($healthStatus)"      
         
    if (healthStatus == "online") state.remove('webSocketOpenDelay')
    sendEventX(name:"healthStatus", value:healthStatus, descriptionText:"healthStatus set to $healthStatus", logLevel:(healthStatus=="online"?"info":"warn"))
}

/* ============================================================
 *  MAIN FLOW
 * ============================================================ */
def initialize() {
    logInfo "executing initialize"
    unschedule()
    autoLogsOff()
    if((device.currentValue("numberOfButtons")?.toInteger() ?: 0) < NUMBER_OF_BUTTONS_SET) {
        remoteControl("NUMBER_OF_BUTTONS_SET", NUMBER_OF_BUTTONS_SET)
    }
    
    state.remove('webSocketOpenDelay')
    runIn(HEALTH_OFFLINE_DELAY_SEC, "setHealthStatusEvent")
    
    try {
        def ids = getIds()
        if (!ids) throw new IllegalStateException("getIds null")
        logDebug "IDS OK gmid=${ids.gmid} ucid=${ids.ucid}"

        def login = gigyaLogin(ids.gmid, ids.ucid)
        if (!login) throw new IllegalStateException("Gigya login null")
        logDebug "Gigya login OK UID=${login.uid}"

        def idToken = getGigyaJwt(login, ids.gmid, ids.ucid)
        if (!ids) throw new IllegalStateException("Gigya JWT null")
        logDebug "Gigya JWT OK Prefix=${idToken.substring(0,16)}..."

        def ctoken = fetchControlToken(idToken, login.signatureTimestamp, login.uid, login.UIDSignature)
        if (!ctoken) throw new IllegalStateException("controlToken null")
        state.controlToken = ctoken
        
    } catch (e) {
        if(!state.controlToken || now() > state.controlToken.timestamp.toInteger() + (state.controlToken.expires_in.toInteger()*1000)) {
            logError "fatel initialize exception ${e}"
            return
        } else {
            logWarn "initialize exception ${e}"
        }
    }
    
    // Open WS and kick off a first volume request
    if(deviceIp && state.controlToken) {
        logDebug "controlToken OK bosePersonId=${state.controlToken.bosePersonId}"
        webSocketOpen("initialize")
        runIn(state.controlToken.expires_in.toInteger() - 3600,"refresh")
    } else {
        logWarn "initialize(): ${deviceIp?"":"deviceIp not set"} ${ctoken?"":"controlToken not set"} skipping websocket"
	}
}

/* ============================================================
 *  1) getIds() 
 * ============================================================ */
private Map getIds() {
    Map body = [
        apikey          : GIGYA_KEY,
        format          : "json",
        httpStatusCodes : "false",
        include         : "permissions,ids,appIds",
        sdk             : "ios_swift_1.0.8",
        targetEnv       : "mobile"
    ]

    Map params = [
        uri                : "https://socialize.us1.gigya.com/socialize.getSDKConfig",
        contentType        : "application/json",
        requestContentType : "application/x-www-form-urlencoded",
        body               : body
    ]

    Map result = null

    try {
        logDebug "getIds() request body=${body}"
        httpPost(params) { resp ->
            def raw = resp?.data
            def parsed = (raw instanceof String) ? new JsonSlurper().parseText(raw) : raw
            logDebug "getIds() raw response=${parsed}"

            if (parsed instanceof List) parsed = parsed ? parsed[0] : null
            if (!(parsed instanceof Map)) return

            def ids = parsed.ids
            if (!ids?.gmid || !ids?.ucid) return
            result = [gmid: ids.gmid, ucid: ids.ucid]
            logDebug "getIds() parsed ids=${result}"
        }
    } catch (e) {
        logError "getIds(): exception ${e}"
    }

    return result
}

/* ============================================================
 *  2) gigyaLogin()
 * ============================================================ */
private Map gigyaLogin(String gmid, String ucid) {
    logDebug "gigyaLogin(): gmid:$gmid, ucid:$ucid"
    Map body = [
        apikey             : GIGYA_KEY,
        format             : "json",
        gmid               : gmid,
        httpStatusCodes    : "false",
        include            : "profile,data,emails,subscriptions,preferences,",
        includeUserInfo    : "true",
        lang               : "de",
        loginID            : username,
        loginMode          : "standard",
        password           : password,
        sdk                : "ios_swift_1.0.8",
        sessionExpiration  : "0",
        source             : "showScreenSet",
        targetEnv          : "mobile",
        ucid               : ucid
    ]

    Map params = [
        uri                : "https://accounts.us1.gigya.com/accounts.login",
        contentType        : "application/json",
        requestContentType : "application/x-www-form-urlencoded",
        body               : body
    ]

    Map out = null

    try {
        Map headers = [
            "Host"            : "accounts.us1.gigya.com",
            "Connection"      : "keep-alive",
            "Accept"          : "*/*",
            "User-Agent"      : BOSE_UA_GIGYA,
            "Accept-Language" : "de-DE,de;q=0.9",
            "Content-Type"    : "application/x-www-form-urlencoded"
        ]
        logDebug "gigyaLogin() request headers=${headers}"
        logDebug "gigyaLogin() request body=${body}"

        // Hubitat ignores headers map on httpPost, but logging them for wire compare
        httpPost(params) { resp ->
            def raw = resp?.data
            def p = (raw instanceof String) ? new JsonSlurper().parseText(raw) : raw
            logDebug "gigyaLogin() raw response=${p}"

            if (!(p instanceof Map)) return
            if (p.errorCode && p.errorCode != 0) return
            if (!p.sessionInfo || !p.userInfo) return

            out = [
                sessionToken      : p.sessionInfo.sessionToken,
                sessionSecret     : p.sessionInfo.sessionSecret,
                uid               : p.userInfo.UID,
                signatureTimestamp: p.userInfo.signatureTimestamp,
                UIDSignature      : p.userInfo.UIDSignature
            ]
            logDebug "gigyaLogin() parsed sessionToken=${out.sessionToken} uid=${out.uid}"
        }
    } catch (e) {
        logError "gigyaLogin(): exception ${e}"
    }
    return out
}

/* ============================================================
 *  3) getGigyaJwt()
 * ============================================================ */
private String getGigyaJwt(Map login, String gmid, String ucid) {
	logDebug "getGigyaJwt(): gmid:$gmid, ucid:$ucid"
    long ts = (new Date().time / 1000L) as long
    String nonce = "${ts}_1637928129"

    Map body = [
        apikey          : GIGYA_KEY,
        format          : "json",
        gmid            : gmid,
        httpStatusCodes : "false",
        sdk             : "ios_swift_1.0.8",
        targetEnv       : "mobile",
        ucid            : ucid,
        timestamp       : ts.toString(),
        nonce           : nonce,
        oauth_token     : login.sessionToken
    ]

    String url = "https://accounts.us1.gigya.com/accounts.getJWT"

    String base = buildGigyaBaseString(url, body)
    logDebug "getGigyaJwt() baseString=${base}"

    String sig  = signOAuthBaseString(base, login.sessionSecret)
    logDebug "getGigyaJwt() sig=${sig}"
    body.sig = sig

    Map headers = [
        "Host"            : "accounts.us1.gigya.com",
        "Connection"      : "keep-alive",
        "Accept"          : "*/*",
        "User-Agent"      : BOSE_UA_GIGYA,
        "Accept-Language" : "de-DE,de;q=0.9",
        "Content-Type"    : "application/x-www-form-urlencoded"
    ]

    Map params = [
        uri                : url,
        headers            : headers,
        requestContentType : "application/x-www-form-urlencoded",
        contentType        : "application/json",
        body               : body
    ]

    String token = null

    try {
        logDebug "getGigyaJwt() request headers=${headers}"
        logDebug "getGigyaJwt() request body=${body}"

        httpPost(params) { resp ->
            def raw = resp?.data
            def p = (raw instanceof String) ? new JsonSlurper().parseText(raw) : raw
            logDebug "getGigyaJwt() raw response=${p}"

            if (!(p instanceof Map)) return
            if (p.errorCode && p.errorCode != 0) return
            if (!p.id_token) return

            token = p.id_token
        }
    } catch (e) {
        logError "getGigyaJwt(): exception ${e}"
    }

    return token
}

private String buildGigyaBaseString(String url, Map params) {

    String normalized = "https://accounts.us1.gigya.com/accounts.getJWT"

    def sorted = params.keySet().sort()
    List<String> pairs = []

    sorted.each { k ->
        def v = params[k]
        if (v != null)
            pairs << "${k}=${gigyaUrlEncode(v.toString())}"
    }

    String qs = pairs.join("&")
    String base =
        "POST" +
        "&" + gigyaUrlEncode(normalized) +
        "&" + gigyaUrlEncode(qs)

    return base
}

private String gigyaUrlEncode(String s) {
    String e = java.net.URLEncoder.encode(s, "UTF-8")
    e = e.replace("+","%20")
    e = e.replace("%7E","~")
    return e
}

private String signOAuthBaseString(String base, String secretB64) {
    try {
        byte[] keyBytes = secretB64.decodeBase64()

        def mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA1"))

        byte[] raw = mac.doFinal(base.getBytes("UTF-8"))
        return raw.encodeBase64().toString()

    } catch (e) {
        logError "signOAuthBaseString(): exception ${e}"
        return null
    }
}

/* ============================================================
 *  4) fetchControlToken()
 * ============================================================ */
private Map fetchControlToken(String idToken, String ts, String uid, String sig) {

    Map headers = [
        "X-ApiKey"          : BOSE_API_KEY,
        "X-Software-Version": BOSE_SOFTWARE_VERSION,
        "X-Api-Version"     : "1",
        "User-Agent"        : BOSE_UA,
        "Pragma"            : "no-cache",
        "Cache-Control"     : "no-cache"
    ]

    Map body = [
        id_token           : idToken,
        scope              : "openid",
        grant_type         : "id_token",
        signature_timestamp: ts,
        uid_signature      : sig,
        uid                : uid,
        client_id          : BOSE_API_KEY
    ]

    Map params = [
        uri                : "https://id.api.bose.io/id-jwt-core/token",
        contentType        : "application/json",
        requestContentType : "application/json",
        headers            : headers,
        body               : body
    ]

    Map out = null

    try {
        logDebug "fetchControlToken() request headers=${headers}"
        logDebug "fetchControlToken() request body=${body}"

        httpPost(params) { resp ->
            def p = resp?.data
            if (p instanceof String)
                p = new JsonSlurper().parseText(p)

            logDebug "fetchControlToken() raw response=${p}"
            if (!(p instanceof Map) || !p.access_token || !p.refresh_token || !p.bosePersonID)
            	throw new IllegalStateException("fetchControlToken ${p}")
            
            out = [
                access_token : p.access_token,
                refresh_token: p.refresh_token,
                bosePersonId : p.bosePersonID,
                expires_in   : p.expires_in,
                timestamp    : now()
            ]
        }
    } catch (e) {
        logError "fetchControlToken(): exception ${e}"
    }

    if (out) logDebug "fetchControlToken() parsed=${out}"
    return out
}

private Map refreshControlToken(String refreshToken) {

    Map headers = [
        "X-ApiKey"          : BOSE_API_KEY,
        "X-Software-Version": BOSE_SOFTWARE_VERSION,
        "X-Api-Version"     : "1",
        "User-Agent"        : BOSE_UA,
        "Pragma"            : "no-cache",
        "Cache-Control"     : "no-cache"
    ]

    Map body = [
        scope        : "openid",
        grant_type   : "refresh_token",
        refresh_token: refreshToken,
        client_id    : BOSE_API_KEY
    ]

    Map params = [
        uri                : "https://id.api.bose.io/id-jwt-core/token",
        contentType        : "application/json",
        requestContentType : "application/json",
        headers            : headers,
        body               : body
    ]

    Map out = null

    try {
        logDebug "refreshControlToken() request body=${body}"

        httpPost(params) { resp ->
            def p = resp?.data
            if (p instanceof String)
                p = new JsonSlurper().parseText(p)

            logDebug "refreshControlToken() raw response=${p}"
            if (!(p instanceof Map) || !p.access_token || /*!p.refresh_token ||*/ !p.bosePersonID)
            	throw new IllegalStateException("refreshControlToken ${p}")

            out = [
                access_token : p.access_token,
                refresh_token: p?.refresh_token ?: refreshToken,
                bosePersonId : p.bosePersonID,
                expires_in   : p.expires_in,
                timestamp    : now()
            ]
        }
    } catch (e) {
        logError "refreshControlToken(): exception ${e}"
    }

    if (out) logDebug "refreshControlToken() parsed=${out}"
    return out
}

/* ============================================================
 *  WEBSOCKET
 * ============================================================ */
private void connect() {
    logDebug "connect()"   
    runIn(HEALTH_OFFLINE_DELAY_SEC, "setHealthStatusEvent")
    if(!deviceIp || deviceIp=="127.0.0.1") {
        logWarn "connect(): device IP Address not set"
        return
    }   

    String url = "wss://${deviceIp}:8082/?product=Madrid-iOS:31019F02-F01F-4E73-B495-B96D33AD3664"
    Map headers = [ "Sec-WebSocket-Protocol": "eco2" ]

    try {
        logInfo "connecting to ${url}"
        interfaces.webSocket.connect([ignoreSSLIssues: true, headers: headers], url)
    } catch (e) {
        logError "connect(): exception ${e}"
    }
}

void disconnect() {
    logDebug "disconnect()"   
    runIn(HEALTH_OFFLINE_DELAY_SEC, "setHealthStatusEvent")
    
    try {
        interfaces.webSocket.close()
    } catch (e) {
        logError "disconnect(): exception ${e}"
    }
}

def webSocketStatus(String status) {
    logDebug "webSocketStatus(${!!state.webSocketOpen})"
    runIn(HEALTH_OFFLINE_DELAY_SEC, "setHealthStatusEvent")

    if (status.startsWith("status: open")) {
        logDebug "webSocket connected"
        state.nextRequestId = 1
    } else if (status.startsWith("status: closing") || status.startsWith("status: closed")) {
        logDebug "webSocket $status"        
        if(!state.webSocketOpen) webSocketOpen(status)
    } else if (status.startsWith("failure:")) {
        logWarn "webSocket $status"
        if(!state.webSocketOpen) webSocketOpen(status)
    } else logError "webSocket unknown $status"
    state.remove('webSocketOpen')
}

void webSocketOpen(String reason) {
    logDebug "webSocketOpen(reason:$reason)"
    state.webSocketOpen = true
    runIn(0,"disconnect")
    
	state.webSocketOpenDelay = state?.webSocketOpenDelay ? Math.min( state.webSocketOpenDelay * 2, 600 ) : 2
	if (state?.webSocketOpenDelay>2) logWarn "delaying websocket retry in $state.webSocketOpenDelay seconds"
	runIn(state.webSocketOpenDelay, "connect")
}

/* ============================================================
 *  COMMAND VIRTUAL DEVICE CREATION
 * ============================================================ */
def createVirtualDevice(String childType, String keyValue, String keyNumeric=null ) {
    
    //String label = "$keyValue${keyNumeric ? "_$keyNumeric" : ""}"
    //String dni =  "${device.deviceNetworkId}-$label"

    String label = "${keyValue}${keyNumeric != null ? (keyNumeric.toInteger() >= 0 ? "_P${Math.abs(keyNumeric.toInteger())}" : "_M${Math.abs(keyNumeric.toInteger())}") : ""}"
	String dni   = "${device.deviceNetworkId}-${label}"
    
    def child = getChildDevice(dni)
    if(child) return

    try {
    	child = addChildDevice( "hubitat", childType, dni, [label: label, isComponent: true])
    } catch (e) {
    	logWarn "could not create child device: $label"
        return
    }        
    child.updateDataValue("keyValue", keyValue)
    if(keyNumeric!=null) child.updateDataValue("keyNumeric", keyNumeric)
    child.sendEvent(name: "switch", value: "off")
    
    logInfo "Created child device: $label (${dni})"
}

def componentEvent(Map data) {
    logDebug "componentEvent: $data"
    getChildDevice(data?.deviceNetworkId)?.sendEvent(data.event)
}

def componentOff(child) {
    logDebug "componentOff: $child.displayName"
    child.sendEvent(name: "switch", value: "off")
}

def componentOn(child) {
    logDebug "componentOn: $child.displayName"
    child.sendEvent(name: "switch", value: "on")
    String keyValue = child.getDataValue("keyValue")
    String keyNumeric = child.getDataValue("keyNumeric") ?: null
    remoteControl(keyValue, keyNumeric)
    runInMillis(500, "componentEvent", [data:[deviceNetworkId: (child.deviceNetworkId as String), event:[name: "switch", value: "off"]]])
}

def componentRefresh(child) {
    logDebug "componentRefresh: $child.displayName"
}

/* ============================================================
 *  COMMANDS
 * ============================================================ */
def remoteControlEq(String keyValue, def keyNumeric=null, String createChild=null) {
    remoteControl(keyValue, keyNumeric, createChild)
}

def remoteControl(String keyValue, def keyNumeric=null, String createChild=null) {
    logDebug "executing 'remoteControl($keyValue, $keyNumeric, $createChild)'"
    if(createChild!=null && createChild!="NONE") { 
        createVirtualDevice(createChild as String, keyValue as String, keyNumeric as String)
        return
    }
   
    switch(keyValue) {
        case  "0": case "POWER_TOGGLE": (device.currentValue("switch")=="on") ? off() : on(); break
        case  "1": case "PRESET_1": setPreset(1); break
        case  "2": case "PRESET_2": setPreset(2); break
        case  "3": case "PRESET_3": setPreset(3); break
        case  "4": case "PRESET_4": setPreset(4); break
        case  "5": case "PRESET_5": setPreset(5); break
        case  "6": case "PRESET_6": setPreset(6); break
        case  "7": case "POWER_ON": on(); break
        case  "8": case "POWER_OFF": off(); break
        case  "9": case "PLAY": play(); break
        case "10": case "PAUSE": pause(); break
        case "11": case "REWIND": previousTrack(); break
        case "12": case "FAST_FORWARD": nextTrack(); break
        case "13": case "STOP": stop(); break
        case "14": case "VOLUME_SET": setVolume(keyNumeric); break
        case "15": case "VOLUME_UP": volumeUp(); break
        case "16": case "VOLUME_DOWN": volumeDown(); break
        case "17": case "MUTE_TOGGLE": (device.currentValue("mute")=="muted") ? unmute() : mute(); break; break
        case "18": case "MUTE":   mute(); break
        case "19": case "UNMUTE":   unmute(); break
        case "20": case "SELECT_OPTICAL":  setSelectOptical(); break
        case "21": case "SELECT_TV": setSelectTV(); break
        case "22": case "SELECT_AUX":   setSelectAux(); break    
        case "23": case "AUDIO_BASE_SET":    setAudioBass(keyNumeric); break
        case "24": case "AUDIO_BASE_UP":     setAudioBass((device.currentValue("audioBass")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "25": case "AUDIO_BASE_DOWN":   setAudioBass((device.currentValue("audioBass")?.toInteger()?:0)-keyNumeric.toInteger()?:10); break
        case "26": case "AUDIO_CENTER_SET":  setAudioCenter(keyNumeric); break
        case "27": case "AUDIO_CENTER_UP":   setAudioCenter((device.currentValue("audioCenter")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "28": case "AUDIO_CENTER_DOWN": setAudioCenter((device.currentValue("audioCenter")?.toInteger()?:0)-keyNumeric.toInteger()?:10); break
        case "29": case "AUDIO_TREBLE_SET":  setAudioTreble(keyNumeric); break
        case "30": case "AUDIO_TREBLE_UP":   setAudioTreble((device.currentValue("audioTreble")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "31": case "AUDIO_TREBLE_DOWN": setAudioTreble((device.currentValue("audioTreble")?.toInteger()?:0)-keyNumeric.toInteger()?:10); break
        case "32": case "AUDIO_HEIGHT_SET":  setAudioHeight(keyNumeric); break
        case "33": case "AUDIO_HEIGHT_UP":   setAudioHeight((device.currentValue("audioHeight")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "34": case "AUDIO_HEIGHT_DOWN": setAudioHeight((device.currentValue("audioHeight")?.toInteger()?:0)-keyNumeric.toInteger()?:10); break
        case "35": case "AUDIO_WOOFER_SET":  setAudioWoofer(keyNumeric); break
        case "36": case "AUDIO_WOOFER_UP":   setAudioWoofer((device.currentValue("audioWoofer")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "37": case "AUDIO_WOOFER_DOWN": setAudioWoofer((device.currentValue("audioWoofer")?.toInteger()?:0)-keyNumeric.toInteger()?:10); break
        case "38": case "AUDIO_REARS_SET":   setAudioRears(keyNumeric); break
        case "39": case "AUDIO_REARS_UP":    setAudioRears((device.currentValue("audioRears")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "40": case "AUDIO_REARS_DOWN":  setAudioRears((device.currentValue("audioRears")?.toInteger()?:0)+keyNumeric.toInteger()?:10); break
        case "41": case "PRESET_SET": setPreset(keyNumeric); break       
        case "$NUMBER_OF_BUTTONS_SET": case "NUMBER_OF_BUTTONS_SET": sendEventX(name: "numberOfButtons", value: keyNumeric as Integer); break  
        // reserved up to 49 for future internal use
        default: 
            Integer reserveButtons = 50
            Integer numberOfButtons = (device.currentValue("numberOfButtons")?.toInteger() ?: 0)
            Integer keyValueInt = 0        
            try { 
                keyValueInt = keyValue.toInteger()
            } catch (NumberFormatException e) {
                logWarn "could not convert $keyValue to a number"
            }            
            if(keyValueInt>=reserveButtons && numberOfButtons>=keyValueInt) {               
                sendEventX(name: "pushed", value: keyValueInt, isStateChange: true, descriptionText: "pushed button $keyValueInt")
            }
            else {
                logWarn "${keyValueInt<reserveButtons ? "pushed $keyValueInt but buttons below $reserveButtons are reserved" : "setNumberOfButtons $numberOfButtons is less than $keyValue. Please update via setNumberOfButtons and resubmit."}"
            }
            break
    }
}

def setLevel(def level,def duration=0) { setVolume(level, "setLevel") }
def setVolume(def level, String label="setVolume") { 
    sendCommand("setVolume", "PUT", SUBSCRIBE_RESOURCES.volume.resource, [value: level as Integer])
}
def volumeDown() {
    Integer current = state.lastVolume?.toInteger() ?: 0 //(device.currentValue("volume")?.toInteger() ?: 0)
    setVolume(Math.max(current - 1, 0) as Integer, "volumeDown")
}
def volumeUp() {
    Integer current = state.lastVolume?.toInteger() ?: 0 //(device.currentValue("volume")?.toInteger() ?: 0)
    setVolume(Math.min(current + 1, 100) as Integer, "volumeUp")
}
def setSelectTV() { sendCommand("setSelectTV", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [source: "PRODUCT", sourceAccount: "TV"]) }
def setSelectAux() { sendCommand("setSelectAux", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [source: "PRODUCT", sourceAccount: "AUX_ANALOG"]) }
def setSelectOptical() { sendCommand("setSelectOptical", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [source: "PRODUCT", sourceAccount: "AUX_DIGITAL"]) }
def setAudioBass(def value)   { sendCommand("setAudioBass",   "PUT", SUBSCRIBE_RESOURCES.audioBass.resource,   [value: value as Integer]) }
def setAudioCenter(def value) { sendCommand("setAudioCenter", "PUT", SUBSCRIBE_RESOURCES.audioCenter.resource, [value: value as Integer]) }
def setAudioTreble(def value) { sendCommand("setAudioTreble", "PUT", SUBSCRIBE_RESOURCES.audioTreble.resource, [value: value as Integer]) }
def setAudioHeight(def value) { sendCommand("setAudioHeight", "PUT", SUBSCRIBE_RESOURCES.audioHeight.resource, [value: value as Integer]) }
def setAudioWoofer(def value) { sendCommand("setAudioWoofer", "PUT", SUBSCRIBE_RESOURCES.audioWoofer.resource, [value: value as Integer]) }
def setAudioRears(def value)  { sendCommand("setAudioRears",  "PUT", SUBSCRIBE_RESOURCES.audioRears.resource,  [value: value as Integer]) }
def off() { sendCommand("off", "POST", SUBSCRIBE_RESOURCES.switch.resource, [power: "OFF"]) }
def on() { sendCommand("on", "POST", SUBSCRIBE_RESOURCES.switch.resource, [power: "ON"]) }
def mute() { sendCommand("mute", "PUT", SUBSCRIBE_RESOURCES.volume.resource, [muted: true]) }
def unmute() { sendCommand("unmute", "PUT", SUBSCRIBE_RESOURCES.volume.resource, [muted: false]) }
def play() { sendCommand("play", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [state: "PLAY"]) }
def stop() { pause() }
def pause() { sendCommand("pause", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [state: "PAUSE"]) }
def previousTrack() { sendCommand("previousTrack", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [state: "SKIPPREVIOUS"]) }
def nextTrack() { sendCommand("nextTrack", "PUT", SUBSCRIBE_RESOURCES.mediaPlayer.resource, [state: "SKIPNEXT"]) }
def playTrack(uri) { unsupported("playTrack") }
def setTrack(value) { unsupported("setTrack") }
def resumeTrack(value) { unsupported("resumeTrack") }
def restoreTrack(value) { unsupported("restoreTrack") }
def playText(value) { unsupported("playText") }
def unsupported(func) { logWarn "does not support ${func}" }

def setPreset(def preset) {
    Map contentItem = state["preset$preset"]
    if(!contentItem) {
        logInfo "preset$preset not found"
        return
    }

    Map body = [
        source       : contentItem.source,
        initiatorID  : "initiator123", // WHAT IS THIS?
        sourceAccount: contentItem.sourceAccount,
        preset       : [
            location    : contentItem.location,
            name        : contentItem.name,
            containerArt: contentItem.containerArt,
            presetable  : contentItem.presetable,
            type        : contentItem.type
        ]
    ]    
    sendCommand("setPreset", "POST", SUBSCRIBE_RESOURCES.setPreset.resource, body)
}

private void sendCommand(String label, String method, String resource, Map body = [:], Integer version = 1) {
    if(device.currentValue("healthStatus")!="online") {
        logWarn "${label} WebSocket is ${device.currentValue("healthStatus")}"
        webSocketOpen("sendCommand")
        return
    }

    String token = state.controlToken?.access_token
    if(!token) {
        logWarn "${label} no access_token; run initialize() first"
        return
    }
    
    if (!state.boseDeviceId) {
        logWarn "${label} no boseDeviceId yet; retry after we learn it"
        return
    }

    Integer reqId = (state.nextRequestId ?: 1) as Integer
    state.nextRequestId++

    Map header = [
        device   : state.boseDeviceId,
        method   : method,
        msgtype  : "REQUEST",
        reqID    : reqId,
        resource : resource,
        status   : 200,
        token    : token,
        version  : version
    ]

    Map msg = [ header: header, body: body ?: [:] ]

    String json = JsonOutput.toJson(msg)
    logDebug "${label} sending ${json?.replaceAll(/\"token\":\"[^\"]+\"/, '\"token\":\"hidden\"')}"

    try {
        interfaces.webSocket.sendMessage(json)
    } catch (e) {
        logError "sendCommand(): ${label} send exception ${e}"
    }
}

/* ============================================================
 *  WEBSOCKET PARSE EVENTS
 * ============================================================ */
void parseVolume(Map data) {
    logDebug "parseVolume(): $data"
    Integer volume = (data.volume ?: data.value ?: data.level) as Integer
    String mute   = data.muted == null ? "unknown" : (data.muted ? "muted" : "unmuted")
    sendEventX(name: "mute", value: mute, descriptionText: "mute is ${mute}")

    Integer prev = (state.lastVolume as Integer) ?: volume
    String direction = volume > prev ? "+" : volume < prev ? "-" : "!"
    state.lastVolume = volume

    if (direction) {
        state.volumePattern = ((state.volumePattern ?: "") + direction)
        runIn(1, "parseVolumePattern")   // last call wins within 1 second
    }
}
void parseVolumePattern() {
    Integer volume = state.lastVolume as Integer
    sendEventX(name: "level",  value: volume, unit: "%")
    sendEventX(name: "volume", value: volume, unit: "%", descriptionText: "volume is ${volume}%")

    String pattern = state.volumePattern ?: ""
    if (pattern.length() > 3) {
        sendEvent(name: "volumePattern", value: pattern, isStateChange: true)
    }
    state.remove("volumePattern")
}

void parseAudioFormat(Map data) {
    logDebug "parseAudioFormat(): $data"
    // {"channels" : "","format" : "Dolby Atmos","type" : "AUDIO_FORMAT_MAT_ATMOS"}}, {"channels" : "2.0","format" : "LPCM","type" : "AUDIO_FORMAT_PCM"}}
    def audioFormat = "${data?.format?:"Format Unknown"} ${data?.channels?:""}".toString().trim()
    sendEventX(name: "audioFormat", value: audioFormat as String, descriptionText: "audioFormat is $audioFormat")
}

void parseAudioBase(Map data) {
    logDebug "parseAudioBase(): $data"
    // {"persistence":"GLOBAL","properties":{"max":100,"min":-100,"step":10,"supportedPersistence":["SESSION","GLOBAL","CONTENT_ITEM"]},"value":0},"header":{"device":"93eb7bda-692a-d36e-9d06-576b53936ac1","method":"GET","msgtype":"RESPONSE","reqID":7,
    // "resource":"/audio/bass","status":200,"targetGuid":"","token":"hidden","version":1.000000}
    def audioBass = data?.value
    sendEventX(name: "audioBass", value: audioBass as String, descriptionText: "audioBass is $audioBass")
}

void parseAudioCenter(Map data) {
    logDebug "parseAudioCenter(): $data"
    def audioCenter = data?.value
    sendEventX(name: "audioCenter", value: audioCenter as String, descriptionText: "audioCenter is $audioCenter")
}

void parseAudioHeight(Map data) {
    logDebug "parseAudioHeight(): $data"
    def audioHeight = data?.value
    sendEventX(name: "audioHeight", value: audioHeight as String, descriptionText: "audioHeight is $audioHeight")
} 

void parseAudioTreble(Map data) {
    logDebug "parseAudioTreble(): $data"
    def audioTreble = data?.value
    sendEventX(name: "audioTreble", value: audioTreble as String, descriptionText: "audioTreble is $audioTreble")
}

void parseAudioWoofer(Map data) {
    logDebug "parseAudioWoofer(): $data"
    def audioWoofer = data?.value
    sendEventX(name: "audioWoofer", value: audioWoofer as String, descriptionText: "audioWoofer is $audioWoofer")
}

void parseAudioRears(Map data) {
    logDebug "parseAudioRears(): $data"
    def audioRears = data?.value
    sendEventX(name: "audioRears", value: audioRears as String, descriptionText: "audioRears is $audioRears")
}

void parseSwitch(Map data) {
    logDebug "parseSwitch(): $data"
	def value = data?.power=="ON"?"on":"off"
    sendEventX(name: "switch", value: value as String, descriptionText: "switch is $value")
}

void parsePresets(Map data) {
    logDebug "parsePresets(): $data"
    (1..PRESET_COUNT).each { n ->
        def p = data?.presets?.presets?."${n}"?.actions?.getAt(0)?.payload?.contentItem
        String preset = "preset$n"
        if(p && state[preset]?.sort()!= p.sort()) {
            state[preset] = p.sort()
            logInfo "stored $preset: ${p.name}"
            sendEventX(name: preset, value: p.name as String)
        } else if(!p) {
            if(state[preset]) {
            	state.remove(preset)
            	logInfo "removed $preset"
            }
            sendEventX(name: preset, value: "Empty")
        }
    }
}

void parseNowPlaying(Map data) {
    logDebug "parseNowPlaying(): $data"
    String trackDesc = "unknown"
    Map trackData = [:]

    trackData["mediaSource"] = data?.container?.contentItem?.source //xmlData.@source.text()
	trackData["sourceAccount"] =  data?.container?.contentItem?.sourceAccount //xmlData.@sourceAccount.text()
              
    switch (trackData.mediaSource) {
        case "PRODUCT":
        	trackData["station"] = trackData["title"] = trackDesc = trackData.sourceAccount        
        	//trackData["albumArtUrl"] = "https://cdn.pixabay.com/photo/2018/12/22/03/27/smart-tv-3889141_1280.png"
            break
        case "STANDBY":
        case "INVALID_SOURCE":
            trackData["station"] = trackData["title"] =  trackDesc = "Standby"
            trackData.remove("mediaSource")
            trackData.remove("sourceAccount")
            break
        case "AUX":
            trackData["station"] = trackData["title"] = trackDesc = "Aux Input"
            break
        case "AIRPLAY":
            trackData["station"] = trackData["title"] = trackDesc = "AirPlay"
        case "ALEXA":
        	trackData["station"] = trackData["title"] = trackDesc = trackData?.station ?: data?.source?.sourceDisplayName
        case "SPOTIFY":
        case "DEEZER":
        case "PANDORA":
        case "IHEART":
        case "STORED_MUSIC":
        case "AMAZON":
        case "SIRIUSXM_EVEREST":
        case "TUNEIN":
            trackData["artist"]  = data?.metadata?.artist ?: "" //xmlData.artist ? "${xmlData.artist.text()}" : ""
            trackData["title"]   = data?.metadata?.trackName ?: trackData.title ?: "" //xmlData.track  ? "${xmlData.track.text()}"  : ""
            trackData["station"] = trackData?.station ?: data?.metadata?.containerName ?: "" //xmlData.stationName ? "${xmlData.stationName.text()}" : trackData.mediaSource
            trackData["album"]   = data?.metadata?.album //xmlData.album ? "${xmlData.album.text()}" : ""
            trackData["albumArtUrl"] = data?.track?.contentItem?.containerArt ?: "" //xmlData.art && xmlData.art.@artImageStatus.text()=="IMAGE_PRESENT" ? "${xmlData.art.text()}" : "${xmlData.ContentItem.containerArt.text()}"
            trackDesc = "${trackData?.artist?:''} ${trackData?.artist && trackData?.title ? '–' : ''} ${trackData.title?:''}"?.trim()
        	break
        default:
            trackData["station"] = trackData["title"] = trackDesc = trackData.mediaSource ?: "unknown"
        	break
    }
     
    String status = data?.state?.status=="PLAY" ? "playing" : data?.state?.status=="STOP" ? "stopped" : data?.state?.status=="PAUSE" ? "paused"
    			  : device.currentValue("switch")=="off" ? "stopped" : data?.state?.status?.toLowerCase() ?: "unknown" 
    String descriptionText = "is $status ${status=="playing" ? trackData.station : ""}"
    if(status=="buffering" && device.currentValue("trackStation")==trackData.station) {
    	runIn(5,"parseNowPlayingBuffering")
    } else {
        unschedule('parseNowPlayingBuffering')
        sendEventX(name: "status", value: status, descriptionText: descriptionText?.trim())
    }   
    sendEventX(name: "trackStation", value: trackData.station ?:" ")
    sendEventX(name: "trackDescription", value: trackDesc ?: " ")
    sendEventX(name: "trackData", value: JsonOutput.toJson(trackData?.sort()))
}
def parseNowPlayingBuffering() {
	sendEventX(name: "status",  value: "buffering", descriptionText: "is buffering")
}

void sendEventX(Map x) {
    if(x?.value!=null  && !x?.eventDisable && (device.currentValue(x?.name).toString() != x?.value.toString() || x?.isStateChange)) {
        if(x?.descriptionText) { if(x?.logLevel=="warn") logWarn (x?.descriptionText); else logInfo (x?.descriptionText); }
        sendEvent(name: x?.name, value: x?.value, unit: x?.unit, descriptionText: x?.descriptionText, isStateChange: (x?.isStateChange ?: false))
    }
}

def parse(String message) {
    logTrace "parse() raw: ${message}"

    Map json
    try {
        json = new JsonSlurper().parseText(message)
    } catch (e) {
        logWarn "parse() non-JSON message: ${message?.replaceAll(/\"token\":\"[^\"]+\"/, '\"token\":\"hidden\"')}"
        return
    }
    
    if(json?.error) {
        logWarn "parse() Error message: ${message?.replaceAll(/\"token\":\"[^\"]+\"/, '\"token\":\"hidden\"')}"
        return
    }

    def header = json.header ?: [:]
    def body   = json.body ?: [:]
    // learn device id as soon as we see it
    if(header?.device && state?.boseDeviceId!=header.device) {
        state.boseDeviceId = header.device
        logInfo "Bose Device Id is ${state.boseDeviceId}"
    }
    
    logDebug "parse recieved ${JsonOutput.toJson(json)?.replaceAll(/\"token\":\"[^\"]+\"/, '\"token\":\"hidden\"')}"
    if(header?.method=="GET" || header?.method=="NOTIFY" ) {        
        Map cfg = SUBSCRIBE_RESOURCES.find { k, v -> v.resource == header?.resource }?.value
		if (cfg?.handler instanceof Closure && body instanceof Map) {
            cfg.handler.call(this, body)
        } else if(header?.method!="NOTIFY" && !deviceDebugEnable) logInfo "parse not handled ${JsonOutput.toJson(json)?.replaceAll(/\"token\":\"[^\"]+\"/, '\"token\":\"hidden\"')}"        
    }  
}

void parseSocketReady(Map data) {
    logInfo "connection ready"
    setHealthStatusEvent([healthStatus:"online"])           
    runIn(0,"subscribe") // schedule this to allow for the above setHealthStatusEvent call to be set online before
}

private void subscribe() {
    SUBSCRIBE_RESOURCES.each{ key, item ->
        Boolean enable = deviceTraceEnable || item?.subscribe
    	if( enable ) sendCommand("subscribe:$key", "PUT", "/subscription", [ notifications: [[resource: item.resource, version: 1 ]]], 2)
    }
    runIn(0,"status")
}

private void status() {   
    SUBSCRIBE_RESOURCES.each{ key, item ->
        Boolean enable = (item.status != null ? item.status : deviceTraceEnable)
    	if( enable ) sendCommand("status:$key", "GET", item.resource)
    }      
}

@Field private static Map SUBSCRIBE_RESOURCES = [
  socketReady: [resource: "/connectionReady", subscribe:false, status:false, handler:{ drv, body -> drv.parseSocketReady(body) }],
  resource001: [resource: "/accessories", subscribe:true, status:true, handler:{ drv, body -> drv.state.boseAccessories=body?.sort(); return null }], //oddly you must return null on a handler,
  resource002: [resource: "/accessories/playTones", status:false],
  resource003: [resource: "/adaptiq"],
  resource004: [resource: "/audio/autoVolume"],
  resource005: [resource: "/audio/avSync"],
  audioBass:   [resource: "/audio/bass", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioBase(body) }],
  audioCenter: [resource: "/audio/center", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioCenter(body) }],
  resource008: [resource: "/audio/dualMonoSelect"],
  resource009: [resource: "/audio/eqSelect"],
  audioFormat: [resource: "/audio/format", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioFormat(body) }],
  audioHeight: [resource: "/audio/height", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioHeight(body) }],
  resource012: [resource: "/audio/mode"],
  resource013: [resource: "/audio/rebroadcastLatency/mode"],
  audioWoofer: [resource: "/audio/subwooferGain", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioWoofer(body) }],
  audioRears:  [resource: "/audio/surround", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioRears(body) }],
  audioTreble: [resource: "/audio/treble", subscribe:true, status:true, handler:{ drv, body -> drv.parseAudioTreble(body) }],
  volume:      [resource: "/audio/volume", subscribe:true, status:true, handler:{ drv, body -> drv.parseVolume(body) }],
  resource018: [resource: "/audio/volume/decrement", status:false],
  resource019: [resource: "/audio/volume/increment", status:false],
  resource020: [resource: "/audio/zone"],
  resource021: [resource: "/bluetooth/sink/connect", status:false],
  resource022: [resource: "/bluetooth/sink/disconnect", status:false],
  resource023: [resource: "/bluetooth/sink/list"],
  resource024: [resource: "/bluetooth/sink/macAddr"],
  resource025: [resource: "/bluetooth/sink/pairable", status:false],
  resource026: [resource: "/bluetooth/sink/remove", status:false],
  resource027: [resource: "/bluetooth/sink/status"],
  resource028: [resource: "/bluetooth/source/capability", status:false],
  resource029: [resource: "/bluetooth/source/capabilitySettings", status:false],
  resource030: [resource: "/bluetooth/source/connect"],
  resource031: [resource: "/bluetooth/source/disconnect", status:false],
  resource032: [resource: "/bluetooth/source/list"],
  resource033: [resource: "/bluetooth/source/pair", status:false],
  resource034: [resource: "/bluetooth/source/pairStatus", status:false],
  resource035: [resource: "/bluetooth/source/remove", status:false],
  resource036: [resource: "/bluetooth/source/scan", status:false],
  resource037: [resource: "/bluetooth/source/scanResult", status:false],
  resource038: [resource: "/bluetooth/source/status"],
  resource039: [resource: "/bluetooth/source/stopScan", status:false],
  resource040: [resource: "/bluetooth/source/volume", status:false],
  resource041: [resource: "/cast/settings"],
  resource042: [resource: "/cast/setup", status:false],
  resource043: [resource: "/cast/teardown", status:false],
  resource044: [resource: "/cec"],
  resource045: [resource: "/cloudSync", status:false],
  nowPlaying:  [resource: "/content/nowPlaying", subscribe:true, status:true, handler:{ drv, body -> drv.parseNowPlaying(body) }],
  resource047: [resource: "/content/nowPlaying/favorite"],
  resource048: [resource: "/content/nowPlaying/rating"],
  resource049: [resource: "/content/nowPlaying/repeat"],
  resource050: [resource: "/content/nowPlaying/shuffle"],
  setPreset:   [resource: "/content/playbackRequest", status:false],
  mediaPlayer: [resource: "/content/transportControl", status:false],
  resource053: [resource: "/device/assumed/TVs"],
  resource054: [resource: "/device/configure", status:false],
  resource055: [resource: "/device/configuredDevices"],
  resource056: [resource: "/device/setup", status:false],
  resource057: [resource: "/grouping/activeGroups"],
  resource058: [resource: "/homekit/info"],
  resource059: [resource: "/network/status"],
  resource060: [resource: "/network/wifi/profile"],
  resource061: [resource: "/network/wifi/siteScan", status:false],
  resource062: [resource: "/network/wifi/status"],
  resource063: [resource: "/remote/integration"],
  resource064: [resource: "/remote/integration/brandList", status:false],
  resource065: [resource: "/remote/integration/directEntry", status:false],
  resource066: [resource: "/subscription"],
  resource067: [resource: "/system/activated"],
  resource068: [resource: "/system/capabilities"],
  resource069: [resource: "/system/challenge"],
  deviceInfo:  [resource: "/system/info", subscribe:true, status:true, handler:{ drv, body -> drv.state.boseDeviceInfo=body?.sort(); return null }], //oddly you must return null on a handler
  switch:      [resource: "/system/power/control", subscribe:true, status:true, handler:{ drv, body -> drv.parseSwitch(body) }],
  resource072: [resource: "/system/power/macro"],
  resource073: [resource: "/system/power/mode/opticalAutoWake"],
  resource074: [resource: "/system/power/timeouts"],
  presets:     [resource: "/system/productSettings", subscribe:true, status:true, handler:{ drv, body -> drv.parsePresets(body) }], 
  resource076: [resource: "/system/reset"],
  resource077: [resource: "/system/setup"],
  resource078: [resource: "/system/sources"],
  resource079: [resource: "/system/sources/status", status:false],
  resource080: [resource: "/system/update/start", status:false],
  resource081: [resource: "/system/update/status"],
  resource082: [resource: "/voice/settings"],
  resource083: [resource: "/voice/setup/start", status:false],
]

/* ============================================================
 *  LOG HELPERS
 * ============================================================ */
def logInfo(msg)  { if(!deviceInfoDisable) log.info "${device.displayName} ${msg}" }
def logDebug(msg) { if(deviceDebugEnable)  log.debug "${device.displayName} ${msg}" }
def logTrace(msg) { if(deviceTraceEnable)  log.trace "${device.displayName} ${msg}" }
def logWarn(msg)  { log.warn "${device.displayName} ${msg}" }
def logError(msg) { log.error "${device.displayName} ${msg}" }
