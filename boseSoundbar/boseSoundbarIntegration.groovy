/**
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
 *  Bose Soundbar Integration
 *
 *  Thanks to: 'cavefire' and the https://github.com/cavefire/pybose project
 *             https://github.com/cavefire/Bose-Homeassistant
 *
 *  Author: bloodtick
 *  Date: 2026-01-04
 */
public static String version() { return "1.0.00" }
public static String copyright() { return "&copy; 2026 ${author()}" }
public static String author() { return "Bloodtick Jones" }

import groovy.json.JsonOutput
import groovy.transform.Field
import java.util.Random
import java.util.regex.Pattern
import java.util.regex.Matcher
import java.security.MessageDigest
import java.net.URI
import java.net.URLDecoder
import groovyx.net.http.HttpResponseException

@Field static final String BOSE_API_KEY = "67616C617061676F732D70726F642D6D61647269642D696F73"
// Azure B2C config from BoseAuth.py
@Field static final String AZ_BASE_URL     = "https://myboseid.bose.com"
@Field static final String AZ_TENANT       = "boseprodb2c.onmicrosoft.com"
@Field static final String AZ_POLICY       = "B2C_1A_MBI_SUSI"
@Field static final String AZ_CLIENT_ID    = "e284648d-3009-47eb-8e74-670c5330ae54"
@Field static final String AZ_REDIRECT_URI = "bosemusic://auth/callback"
@Field static final String AZ_UI_LOCALES   = "de-de"
// Bose exchange endpoint from BoseAuth.py
@Field static final String BOSE_EXCHANGE_URL = "https://id.api.bose.io/id-jwt-core/idps/aad/B2C_1A_MBI_SUSI/token"
// Application specific constants
@Field static final String  sColorDarkBlue  = "#1A77C9"
@Field static final String  sColorLightGrey = "#DDDDDD"
@Field static final String  sColorDarkGrey  = "#696969"
@Field static final String  sColorDarkRed   = "DarkRed"
@Field static final String  sColorYellow    = "#8B8000"
@Field static final String  sDriverName     = "Bose Soundbar Device"

definition(
    name: "Bose Soundbar Integration",
    namespace: "bloodtick",
    author: "Hubitat",
    description: "Connect your Bose Soundbar Speakers (not SoundTouch) to Hubitat.",
    category: "Convenience",
    importUrl: "https://raw.githubusercontent.com/bloodtick/Hubitat/main/boseSoundbar/boseSoundbarIntegration.groovy",
	iconUrl: "",
    iconX2Url: "",
    singleInstance: false,
    installOnOpen: true
)

preferences {
    page(name: "pageMain")
}

Map pageMain() {
    dynamicPage(name: "pageMain", install: true, uninstall: true) {
        displayHeader()
        
        section(menuHeader("Account Configuration")) {
            input(name:"username", type:"string", title: "Bose Username", required: true, width: 3, submitOnChange: true, )
            input(name:"password", type:"password", title: "Bose Password", required: true, width: 3, submitOnChange: true, newLineAfter:true)                   
            
            input(name: "dynamic::initialize", type: "button", title: "Initialize", width: 3, style:"width:50%;")
            input(name: "dynamic::refresh", type: "button", title: "Refresh", width: 3, style:"width:50%;", newLineAfter:true)
            paragraph( "Status: " + ssrEventSpan(name:"authorization", defaultValue:"pending", newLineAfter:true) )
            paragraph( ssrEventSpan(name:"tokenStatus", newLineAfter:true) )
        }
        
        section(menuHeader("Speaker Configuration")) {
            input(name:"speakerLabel", type:"string", title: "Speaker Display Name", required: false, width: 3, submitOnChange: true, )
            input(name:"speakerIpAddress", type:"string", title: "Speaker IP Address", required: false, width: 3, submitOnChange: true, newLineAfter:true) 
            input(name: "dynamic::createDevice", type: "button", title: "Create Device", width: 3, style:"width:50%;")
            input(name: "dynamic::refreshDevices", type: "button", title: "Refresh", width: 3, style:"width:50%;", newLineAfter:true)

            String deviceList  = "<table style='width:80%;'>"
    		deviceList        += "<tr><th style='width:4%;text-align:center;'>Speaker</th><th style='width:4%;text-align:center;'>IP Address</th><th style='width:4%;text-align:center;'>Status</th><th style='width:4%;text-align:center;'>Switch</th><th style='width:4%;text-align:center;'>Volume</th></tr>"
           
            getChildDevices()?.each { device ->
                String deviceIp     = device.getSetting("deviceIp")
                String deviceUri    = "http://${location.hub.getDataValue("localIP")}/device/edit/${device.getId()}"
                String displayName  = "<a href='$deviceUri' target='_blank' rel='noopener noreferrer'>${device?.getDisplayName()} <i class='fa-regular fa-arrow-up-right-from-square' style='font-size:0.85em; position:relative; top:-2px;'></i></a>"         
        		String healthStatus = "<span class='device-current-state-${device.getId()}-healthStatus'>${device.currentValue("healthStatus")?:"unknown"}</span>"
                String volume       = "<span class='device-current-state-${device.getId()}-volume'>${device.currentValue("volume")?:"unknown"}</span>"
                String switch_      = "<span class='device-current-state-${device.getId()}-switch'>${device.currentValue("switch")?:"unknown"}</span>"
                //paragraph( "$displayName : ip=$deviceIp switch=$switch_ healthStatus=$healthStatus volume=$volume <br/>" )                
                deviceList += "<tr><td>$displayName</td><td>$deviceIp</td><td>$healthStatus</td><td>$switch_</td><td>$volume</td></tr>"
    		}           
    	    deviceList +="</table>"
            
            if(getChildDevices()) {
            	paragraph( getFormat("line") )
           		paragraph( rawHtml: true, deviceList )
            	paragraph( rawHtml: true, """<style>th,td{border-bottom:3px solid #ddd;} table{ table-layout: fixed;width: 100%;} td{ text-align:center; vertical-align:middle; } td:first-child{ text-align:left; } </style>""")
            	paragraph( rawHtml: true, """<style>@media screen and (max-width:800px) { table th:nth-of-type(2),td:nth-of-type(2),th:nth-of-type(4),td:nth-of-type(4) { display: none; } }</style>""")
            }
        }
        
         section(menuHeader("Application Logging")) {
            input(name: "appInfoDisable", type: "bool", title: "Disable info logging", required: false, defaultValue: false, submitOnChange: true)
            input(name: "appDebugEnable", type: "bool", title: "Enable debug logging", required: false, defaultValue: false, submitOnChange: true)
            //input(name: "appTraceEnable", type: "bool", title: "Enable trace logging", required: false, defaultValue: false, submitOnChange: true)
            paragraph( getFormat("line") )
        }
      
        if(appDebugEnable || appTraceEnable) {
            runIn(1800, updatePageMain)
        } else {
            unschedule('updatePageMain')
        }
    }    
}

void updatePageMain() {
    logInfo "${app.getLabel()} disabling debug and trace logs"
    app.updateSetting("appDebugEnable", false)
    app.updateSetting("appTraceEnable", false)    
}

void setEventText(String name, String descriptionText=null) {
    if(!state?.event) state.event = [:]
    state.event[name] = descriptionText ?: ""
    sendEvent(name: name, value: now().toString(), type:"TEXT", descriptionText:descriptionText)
}
void setEventText(Map p) { setEventText(p.name, p?.descriptionText) }

String getEventText(String name, String defaultValue = null) {
    return (state.event?.containsKey(name) ? state.event[name] : defaultValue ?: "")
}
String getEventText(Map p) { return getEventText(p.name, p?.defaultValue) }

String ssrEventSpan(String name, String defaultValue, String style=null, Boolean newLineAfter=false) {
     return "<span class='ssr-app-state-${app.getId()}-$name' ${style?"style='$style'":""}>${getEventText(name:name, defaultValue:defaultValue)}</span>${newLineAfter?"<br/>":""}"
}
String ssrEventSpan(Map p) { return ssrEventSpan(p.name, p?.defaultValue, p?.style, p?.newLineAfter) }

String processServerSideRender(Map event) {
    event?.each{ k,v -> if(v=="null") { event[k]=null } } //lets normailize nulls, some show as text
    logTrace "processServerSideRender: $event"

    String response = event?.value ?: ""
    if(event?.type?.toUpperCase()?.contains("TEXT")) {
        response = event?.descriptionText ?: ""
    }
        
    return response    
}

void appButtonHandler(String btn) {
    def (k, v, a) = btn.tokenize("::")
    switch(k) {                
        case "dynamic":
            if(a) this."$v"(a); else this."$v"(); break
        default:
            logWarn "$btn not supported"
    }    
}

def initialize() {
    if(state?.initializeRun && state.initializeRun + 30*1000 > now()) {
    	logWarn "You need to wait at least 30 seconds between authorization attempts"
        setEventText(name:"authorization", descriptionText:"You need to wait at least 30 seconds between authorization attempts")
        return
    }
    state.initializeRun = now()
    setEventText(name:"authorization", descriptionText:"executing initialize version=${version()}")
    runIn(1,'initializeRun')
}

def refresh() {
    setEventText(name:"authorization", descriptionText:"executing refresh version=${version()}")
    runIn(1,'refreshRun')
}

private void initializeRun() {
    logInfo "executing initialize version=${version()}"    
    state.remove('boseToken')
    state.remove('azureToken')

    try {
        Map result = doAzureAndBoseAuth()
        if (result?.ok) {
            logInfo "AUTH OK (bosePersonId=${result?.bosePersonId})"
            setEventText(name:"authorization", descriptionText:"Authorization Initialize Successful at ${localtime(now())}")
            setEventText(name:"tokenStatus",   descriptionText:"Token Expires: ${localtime( state.boseToken.timestamp.toLong() + (state.boseToken.expires_in.toLong() * 1000) )}")
        } else {
            logError "AUTH FAILED (error=${result?.error ?: 'unknown'})"
            setEventText(name:"authorization", descriptionText:"Authorization Initialize Failed (error=${result?.error ?: 'unknown'})")
            setEventText(name:"tokenStatus",   descriptionText:"")
        }
    } catch (Throwable t) {
        // Never throw out of initialize
        logError "initialize() exception: ${t}"
    }
}

private void refreshRun() {
    logInfo "executing refresh version=${version()}"
   
    try {
        Map result = doAzureAndBoseRefresh()
        if (result?.ok) {
            logInfo "REFRESH OK (bosePersonId=${result?.bosePersonId})"
            setEventText(name:"authorization", descriptionText:"Authorization Refresh Successful at ${localtime(now())}")
            setEventText(name:"tokenStatus",   descriptionText:"Token Expires: ${localtime( state.boseToken.timestamp.toLong() + (state.boseToken.expires_in.toLong() * 1000) )}")
        } else {
            logError "REFRESH FAILED (error=${result?.error ?: 'unknown'})"
            setEventText(name:"authorization", descriptionText:"Authorization Refresh Failed (error=${result?.error ?: 'unknown'})")
            setEventText(name:"tokenStatus",   descriptionText:"")
        }
    } catch (Throwable t) {
        logError "refresh() exception: ${t}"
    }
}

private void createDevice() {
    logInfo "executing create device: $speakerLabel ($speakerIpAddress)"
    
    String deviceNetworkId = "${UUID.randomUUID().toString()}"
	def device = getChildDevice(dnid) ?: addChildDevice( "bloodtick", sDriverName, deviceNetworkId, [name: sDriverName, label: speakerLabel, isComponent: true] )    
    device.updateSetting("deviceIp", [type:"string", value:speakerIpAddress])
    device.updated()    
}

private void refreshDevices() {
    /* noop */
}

public String getAuthToken() {
    // refresh the token when we are at 80% lifespan. It seems to be 8 hours now, so every 6.4 hours is our targer refresh
    if(state.boseToken && ((state.boseToken.timestamp.toLong() + (state.boseToken.expires_in.toLong()*1000*0.8) as Long) < now())) {
        logInfo "refreshing expired authToken"
        refreshRun()
    }        
    return (state?.boseToken?.access_token ?: null)    
}

/* --------------------------
 * App Format Helpers
 * -------------------------- */
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
    section (getFormat("title", "${app.getLabel()}"  )) { 
        paragraph "<div style='color:${sColorDarkBlue};text-align:right;font-weight:small;font-size:9px;'>Developed by: ${author()}<br/>Current Version: v${version()} -  ${copyright()}</div>"
        paragraph( getFormat("line") ) 
    }
}

def displayFooter(){
	section() {
		paragraph( getFormat("line") )
		paragraph "<div style='color:{sColorDarkBlue};text-align:center;font-weight:small;font-size:11px;'>${getDefaultLabel()}<br><br><a href='${paypal()}' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This application took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}       
}

def menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}

String localtime(currentTimeMillis) {
    def localDate = new Date(currentTimeMillis)  // Converts to local time automatically
 	return localDate.format("yyyy-MM-dd hh:mm:ss a", location.timeZone)
}

/* --------------------------
 * Logging
 * -------------------------- */
private logInfo(msg)  { if(appInfoDisable != true) { log.info  "${app.getLabel()} ${msg}" } }
private logDebug(msg) { if(appDebugEnable == true) { log.debug "${app.getLabel()} ${msg}" } }
private logTrace(msg) { if(appTraceEnable == true) { log.trace "${app.getLabel()} ${msg}" } }
private logWarn(msg)  { log.warn "${app.getLabel()} ${msg}" }
private logError(msg) { log.error "${app.getLabel()} ${msg}" }

/* ============================================================
 *  MAIN AUTH FLOW (Hubitat Driver OR App Safe Code Space)
 * ============================================================ */
private Map doAzureAndBoseAuth() {
    Map cookieJar = [:]  // session cookie jar just for this run
    cookieJarClear(cookieJar)

    logDebug "Starting Azure AD B2C authentication flow"

    Map pkce = generatePkce()
    String codeVerifier  = pkce.verifier
    String codeChallenge = pkce.challenge
    logDebug "PKCE verifier len=${codeVerifier?.length()} challenge len=${codeChallenge?.length()}"

    // Step 1: GET authorize (follow redirects)
    String authUrl = "${AZ_BASE_URL}/${AZ_TENANT}/oauth2/v2.0/authorize"

    Map authParams = [
        p: AZ_POLICY,
        response_type: "code",
        client_id: AZ_CLIENT_ID,
        scope: azureScope(),
        code_challenge_method: "S256",
        code_challenge: codeChallenge,
        redirect_uri: AZ_REDIRECT_URI,
        ui_locales: AZ_UI_LOCALES
    ]

    Map step1 = httpGetFollowRedirects("STEP1 authorize", authUrl, authParams, [:], cookieJar, 10)
    if ((step1?.status ?: 0) != 200) return fail("azure_authorize_get_failed", step1)

    String step1Body = safeBodyToString(step1?.body)

    String csrfToken = extractCsrf(step1Body, cookieJar)
    String txParam   = extractTx(step1Body) ?: extractTx(step1?.finalUrl ?: "")
    if (!csrfToken || !txParam) {
        logError "Failed to extract CSRF token or tx parameter"
        return fail("azure_extract_csrf_tx_failed", [status: step1?.status, body: snippet(step1Body)])
    }
    logDebug "CSRF Token: ${maskMid(csrfToken)}"
    logDebug "TX Parameter: ${maskMid(txParam)}"

    // Step 2: POST email
    String emailUrl = "${AZ_BASE_URL}/${AZ_TENANT}/${AZ_POLICY}/SelfAsserted"
    Map emailQuery = [tx: txParam, p: AZ_POLICY]
    Map emailForm  = [request_type: "RESPONSE", email: (username ?: "")]
    Map emailHeaders = [
        "X-CSRF-TOKEN": csrfToken,
        "X-Requested-With": "XMLHttpRequest",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Origin": AZ_BASE_URL,
        "Referer": (step1?.finalUrl ?: authUrl)
    ]

    Map step2 = httpPostForm("STEP2 email", emailUrl, emailQuery, emailHeaders, emailForm, cookieJar, true)
    if ((step2?.status ?: 0) != 200) return fail("azure_submit_email_failed", step2)

    // Step 3: GET confirm email page
    String confirmUrl = "${AZ_BASE_URL}/${AZ_TENANT}/${AZ_POLICY}/api/CombinedSigninAndSignup/confirmed"
    Map confirmQuery = [
        rememberMe: "false",
        csrf_token: csrfToken,
        tx: txParam,
        p: AZ_POLICY,
        diags: JsonOutput.toJson([pageViewId:"test", pageId:"CombinedSigninAndSignup", trace:[]])
    ]

    Map step3 = httpGet("STEP3 confirm email", confirmUrl, confirmQuery, [:], cookieJar, true)
    if ((step3?.status ?: 0) != 200) return fail("azure_confirm_email_failed", step3)

    String step3Body = safeBodyToString(step3?.body)

    // Update CSRF + TX if present
    csrfToken = extractCsrf(step3Body, cookieJar) ?: csrfToken
    txParam   = extractTx(step3Body) ?: txParam

    // Step 4: POST password
    Map pwQuery = [tx: txParam, p: AZ_POLICY]
    Map pwForm  = [readonlyEmail: (username ?: ""), password: (password ?: ""), request_type: "RESPONSE"]
    Map pwHeaders = [
        "X-CSRF-TOKEN": csrfToken,
        "X-Requested-With": "XMLHttpRequest",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Origin": AZ_BASE_URL,
        "Referer": confirmUrl
    ]

    Map step4 = httpPostForm("STEP4 password", emailUrl, pwQuery, pwHeaders, pwForm, cookieJar, true)
    if ((step4?.status ?: 0) != 200) return fail("azure_submit_password_failed", step4)

    // Step 5: GET confirm password page, expect 302 with bosemusic:// callback + code
    String confirm2Url = "${AZ_BASE_URL}/${AZ_TENANT}/${AZ_POLICY}/api/SelfAsserted/confirmed"
    Map confirm2Query = [
        csrf_token: csrfToken,
        tx: txParam,
        p: AZ_POLICY,
        diags: JsonOutput.toJson([pageViewId:"test2", pageId:"SelfAsserted", trace:[]])
    ]

    Map step5 = httpGet("STEP5 confirm2", confirm2Url, confirm2Query, [:], cookieJar, false)
    if ((step5?.status ?: 0) != 302) {
        logError "Expected redirect, got: ${step5?.status}"
        return fail("azure_confirm2_expected_302_failed", step5)
    }

    String location = step5?.location ?: ""
    String authCode = extractCodeFromLocation(location)
    if (!authCode) return fail("azure_no_auth_code_in_redirect", step5)
    logDebug "Authorization code received len=${authCode?.length() ?: 0}"

    // Step 6: Exchange code for tokens
    String tokenUrl = "${AZ_BASE_URL}/${AZ_TENANT}/oauth2/v2.0/token"
    Map tokenQuery = [p: AZ_POLICY]
    Map tokenForm = [
        client_id: AZ_CLIENT_ID,
        code_verifier: codeVerifier,
        grant_type: "authorization_code",
        scope: azureScope(),
        redirect_uri: AZ_REDIRECT_URI,
        code: authCode
    ]
    Map tokenHeaders = [
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": "https://www.bose.de",
        "Referer": "https://www.bose.de/"
    ]

    Map step6 = httpPostForm("STEP6 token exchange", tokenUrl, tokenQuery, tokenHeaders, tokenForm, cookieJar, false)
    if ((step6?.status ?: 0) != 200) return fail("azure_token_exchange_failed", step6)

    Map azureTokens = step6?.body
    azureTokens.timestamp = now()
    String azureIdToken      = azureTokens?.id_token
    String azureRefreshToken = azureTokens?.refresh_token

    logDebug "Azure tokens id_token len=${azureIdToken?.length() ?: 0} refresh_token len=${azureRefreshToken?.length() ?: 0}"

    // Step 7: Exchange Azure id_token for Bose tokens (match BoseAuth.py headers/payload)
    if (!azureIdToken) return fail("azure_missing_id_token", azureTokens)

    Map bosePayload = new LinkedHashMap()
    bosePayload.put("grant_type", "id_token")
    bosePayload.put("id_token", azureIdToken)
    bosePayload.put("client_id", AZ_CLIENT_ID)
    bosePayload.put("scope", azureScope())

    Map boseHeaders = [
        "Content-Type": "application/json",
        "X-ApiKey": BOSE_API_KEY,
        "X-Api-Version": "1",
        "X-Software-Version": "1",
        "X-Library-Version": "1",
        "User-Agent": "Bose/37362 CFNetwork/3860.200.71 Darwin/25.1.0",
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br"
    ]

    Map step7 = httpPostJson("STEP7 bose exchange", BOSE_EXCHANGE_URL, [:], boseHeaders, bosePayload, cookieJar, true)
    if (!((step7?.status ?: 0) in [200, 201])) {
        logError "Bose token exchange failed: ${step7?.status ?: 0}"
        logError "STEP7 bodySnippet=${snippet(safeBodyToString(step7?.body))}"
        return fail("bose_exchange_failed", step7)
    }

    Map boseTokens = step7?.body
    boseTokens.timestamp = now()
    String boseAccessToken  = boseTokens?.access_token
    String boseRefreshToken = boseTokens?.refresh_token
    String bosePersonID     = boseTokens?.bosePersonID

    logDebug "Bose tokens access_token len=${boseAccessToken?.length() ?: 0} refresh_token len=${boseRefreshToken?.length() ?: 0} bosePersonID present=${!!bosePersonID}"
	
    // store for later use
    state.boseToken = boseTokens
    state.azureToken = azureTokens

    return [ok:true, bosePersonId: bosePersonID]
}

private Map doAzureAndBoseRefresh() {
    String azureRefreshToken = null
    try { azureRefreshToken = state?.azureToken?.refresh_token } catch (Throwable ignored) { azureRefreshToken = null }
    if (!azureRefreshToken) return fail("azure_missing_refresh_token", [have:false])

    String refreshUrl = "${AZ_BASE_URL}/${AZ_TENANT}/${AZ_POLICY}/oauth2/v2.0/token"

    Map refreshHeaders = [
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "User-Agent": "Bose/37362 CFNetwork/3860.200.71 Darwin/25.1.0",
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br",
        "Pragma": "no-cache",
        "Cache-Control": "no-cache"
    ]

    Map refreshForm = [
        "refresh_token": azureRefreshToken,
        "client_id": AZ_CLIENT_ID,
        "grant_type": "refresh_token"
    ]

    Map cookieJar = [:]
    cookieJarClear(cookieJar)

    Map r1 = httpPostForm("REFRESH1 azure token refresh", refreshUrl, [:], refreshHeaders, refreshForm, cookieJar, true)
    if ((r1?.status ?: 0) != 200) return fail("azure_refresh_failed", r1)

    Map azureTokens = r1?.body
    azureTokens.timestamp = now()
    String azureIdToken = azureTokens?.id_token
    String newAzureRefreshToken = azureTokens?.refresh_token
    if (newAzureRefreshToken) azureRefreshToken = newAzureRefreshToken

    if (!azureIdToken) return fail("azure_missing_id_token_after_refresh", azureTokens)

    Map bosePayload = new LinkedHashMap()
    bosePayload.put("grant_type", "id_token")
    bosePayload.put("id_token", azureIdToken)
    bosePayload.put("client_id", AZ_CLIENT_ID)
    bosePayload.put("scope", azureScope())

    Map boseHeaders = [
        "Content-Type": "application/json",
        "X-ApiKey": BOSE_API_KEY,
        "X-Api-Version": "1",
        "X-Software-Version": "1",
        "X-Library-Version": "1",
        "User-Agent": "Bose/37362 CFNetwork/3860.200.71 Darwin/25.1.0",
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br"
    ]

    Map r2 = httpPostJson("REFRESH2 bose exchange", BOSE_EXCHANGE_URL, [:], boseHeaders, bosePayload, cookieJar, true)
    if (!((r2?.status ?: 0) in [200, 201])) {
        logError "Bose token exchange failed: ${r2?.status ?: 0}"
        logError "REFRESH2 bodySnippet=${snippet(safeBodyToString(r2?.body))}"
        return fail("bose_exchange_failed", r2)
    }

    Map boseTokens = r2?.body
    boseTokens.timestamp = now()
    String bosePersonID = boseTokens?.bosePersonID

    // store for later use
    state.boseToken = boseTokens
    state.azureToken = azureTokens

    return [ok:true, bosePersonId: bosePersonID]
}

/* ============================================================
 *  HTTP HELPERS (cookie jar + verbose logging)
 * ============================================================ */
private Map httpGet(String label, String url, Map query, Map headers, Map cookieJar, boolean followRedirects) {
    logInfo "HTTP GET ${label}"

    String cookieOut = cookieHeader(cookieJar)

    Map params = [
        uri: url,
        query: (query ?: [:]),
        headers: buildHeaders(headers, cookieOut),
        contentType: "text/plain",
        requestContentType: "application/x-www-form-urlencoded",
        followRedirects: followRedirects
    ]

    Map out = [status:0, location:null, contentType:null, body:null]

    try {
        httpGet(params) { resp ->
            out.status = resp?.status ?: 0
            out.contentType = resp?.contentType
            out.location = headerValue(resp, "Location")
            out.body = resp?.data

            updateCookieJarFromResponse(resp, cookieJar)

            logDebug "HTTP GET ${label} <- status=${out.status}"
            logDebug "contentType=${out.contentType}"
        }
    } catch (HttpResponseException e) {
        out.status = safeStatusCode(e)
        out.body = safeErrorBody(e)
        out.location = safeErrorHeader(e, "Location")
        updateCookieJarFromException(e, cookieJar)
        logWarn "HTTP GET ${label} exception: status code: ${out.status}, reason phrase: ${e?.message}"
    } catch (Throwable t) {
        out.status = 0
        out.body = "${t}"
        logError "HTTP GET ${label} exception: ${t}"
    }

    return out
}

private Map httpGetFollowRedirects(String label, String url, Map query, Map headers, Map cookieJar, int maxHops) {
    String currentUrl = url
    Map currentQuery = (query ?: [:])
    String finalUrl = currentUrl

    int hops = 0
    Map last = null

    while (hops <= maxHops) {
        last = httpGet("${label}${hops==0?'':' (hop '+hops+')'}", currentUrl, currentQuery, headers, cookieJar, false)
        finalUrl = currentUrl

        int st = (last?.status ?: 0) as int
        String loc = last?.location

        if (st in [301,302,303,307,308] && loc) {
            logDebug "redirect hop=${hops} status=${st} -> ${loc}"
            currentUrl = absolutizeUrl(currentUrl, loc)
            currentQuery = [:]  // after redirect, Location already contains query if needed
            hops++
            continue
        }

        break
    }

    if (last == null) last = [status:0]
    last.finalUrl = finalUrl
    return last
}

private Map httpPostForm(String label, String url, Map query, Map headers, Map form, Map cookieJar, boolean followRedirects) {
    logInfo "HTTP POST FORM ${label}"

    String cookieOut = cookieHeader(cookieJar)

    Map params = [
        uri: url,
        query: (query ?: [:]),
        headers: buildHeaders(headers, cookieOut),
        requestContentType: "application/x-www-form-urlencoded",
        //contentType: "text/plain",
        body: (form ?: [:]),
        followRedirects: followRedirects
    ]

    Map out = [status:0, location:null, contentType:null, body:null]

    try {
        httpPost(params) { resp ->
            out.status = resp?.status ?: 0
            out.contentType = resp?.contentType
            out.location = headerValue(resp, "Location")
            out.body = resp?.data
            updateCookieJarFromResponse(resp, cookieJar)

            logDebug "HTTP POST FORM ${label} <- status=${out.status}"
            logDebug "contentType=${out.contentType}"
        }
    } catch (HttpResponseException e) {
        out.status = safeStatusCode(e)
        out.body = safeErrorBody(e)
        out.location = safeErrorHeader(e, "Location")
        updateCookieJarFromException(e, cookieJar)
        logWarn "HTTP POST FORM ${label} exception: status code: ${out.status}, reason phrase: ${e?.message}"
    } catch (Throwable t) {
        out.status = 0
        out.body = "${t}"
        logError "HTTP POST FORM ${label} exception: ${t}"
    }

    return out
}

private Map httpPostJson(String label, String url, Map query, Map headers, Map jsonBody, Map cookieJar, boolean followRedirects) {
    logInfo "HTTP POST JSON ${label}"

    String cookieOut = cookieHeader(cookieJar)

    Map params = [
        uri: url,
        query: (query ?: [:]),
        headers: buildHeaders(headers, cookieOut),
        requestContentType: "application/json",
        contentType: "application/json",
        body: (jsonBody ?: [:]),
        followRedirects: followRedirects
    ]

    Map out = [status:0, location:null, contentType:null, body:null]

    try {
        httpPost(params) { resp ->
            out.status = resp?.status ?: 0
            out.contentType = resp?.contentType
            out.location = headerValue(resp, "Location")
            out.body = resp?.data

            updateCookieJarFromResponse(resp, cookieJar)

            logDebug "HTTP POST JSON ${label} <- status=${out.status}"
            logDebug "contentType=${out.contentType}"
        }
    } catch (HttpResponseException e) {
        out.status = safeStatusCode(e)
        out.body = safeErrorBody(e)
        out.location = safeErrorHeader(e, "Location")
        updateCookieJarFromException(e, cookieJar)
        logWarn "HTTP POST JSON ${label} exception: status code: ${out.status}, reason phrase: ${e?.message}"
    } catch (Throwable t) {
        out.status = 0
        out.body = "${t}"
        logError "HTTP POST JSON ${label} exception: ${t}"
    }

    return out
}

/* ============================================================
 *  COOKIE JAR
 * ============================================================ */
private void cookieJarClear(Map jar) {
    jar?.clear()
}

private void updateCookieJarFromResponse(resp, Map jar) {
    try {
        List setCookies = headerValues(resp, "Set-Cookie")
        if (setCookies && jar != null) {
            setCookies.each { String sc ->
                Map kv = parseSetCookie(sc)
                if (kv?.k) jar[kv.k] = kv.v
            }
        }
    } catch (Throwable t) {
        logWarn "cookie update exception: ${t}"
    }
}

private void updateCookieJarFromException(HttpResponseException e, Map jar) {
    try {
        def resp = e?.response
        if (resp) updateCookieJarFromResponse(resp, jar)
    } catch (Throwable t) { /* ignore */ }
}

private Map parseSetCookie(String setCookieValue) {
    if (!setCookieValue) return null
    String first = setCookieValue.split(";")[0]
    int idx = first.indexOf("=")
    if (idx <= 0) return null
    String k = first.substring(0, idx)
    String v = first.substring(idx + 1)
    return [k:k, v:v]
}

private String cookieHeader(Map jar) {
    if (!jar || jar.isEmpty()) return null
    List parts = []
    jar.each { k, v ->
        if (k != null && v != null) parts << "${k}=${v}"
    }
    return parts.join("; ")
}

/* ============================================================
 *  EXTRACTION
 * ============================================================ */
private String extractCsrf(String html, Map cookieJar) {
    List patterns = [
        'x-ms-cpim-csrf["\\s]*[:=]["\\s]*([^";\\s]+)',
        '"csrf"["\\s]*:["\\s]*"([^"]+)"',
        'csrf_token["\\s]*[:=]["\\s]*"?([^";\\s]+)"?',
        'X-CSRF-TOKEN["\\s]*[:=]["\\s]*"?([^";\\s]+)"?'
    ]
    String token = extractByPatterns(html ?: "", patterns, true)
    if (token) return token

    // Try from cookies
    try {
        cookieJar?.each { k, v ->
            if ((k ?: "").toLowerCase().contains("csrf")) {
                logDebug "Found CSRF token in cookie: ${k}"
                return v
            }
        }
    } catch (Throwable t) { /* ignore */ }

    logDebug "No CSRF token found"
    return null
}

private String extractTx(String textOrUrl) {
    List patterns = [
        '[?&]tx=([^&"\\\']+)',
        '"tx"["\\s]*:["\\s]*"([^"]+)"',
        'StateProperties=([^&"\\\']+)'
    ]
    String tx = extractByPatterns(textOrUrl ?: "", patterns, false)
    if (!tx) logDebug "No tx parameter found"
    return tx
}

private String extractByPatterns(String input, List patterns, boolean ignoreCase) {
    if (!input) return null
    for (String p : patterns) {
        try {
            Pattern pat = ignoreCase ? Pattern.compile(p, Pattern.CASE_INSENSITIVE) : Pattern.compile(p)
            Matcher m = pat.matcher(input)
            if (m.find()) {
                logDebug "regex matched pattern=${p}"
                return m.group(1)
            }
        } catch (Throwable t) { /* ignore */ }
    }
    return null
}

private String extractCodeFromLocation(String location) {
    if (!location) return null
    try {
        URI u = new URI(location)
        Map q = parseQuery(u?.getQuery() ?: "")
        return q?.code
    } catch (Throwable t) {
        String s = (location ?: "")
        int i = s.indexOf("code=")
        if (i >= 0) {
            String tail = s.substring(i + 5)
            int amp = tail.indexOf("&")
            String code = (amp >= 0) ? tail.substring(0, amp) : tail
            return urlDecode(code)
        }
    }
    return null
}

private Map parseQuery(String query) {
    Map out = [:]
    if (!query) return out
    query.split("&").each { kv ->
        int idx = kv.indexOf("=")
        if (idx > 0) {
            String k = urlDecode(kv.substring(0, idx))
            String v = urlDecode(kv.substring(idx + 1))
            out[k] = v
        }
    }
    return out
}

/* ============================================================
 *  PKCE
 * ============================================================ */
private Map generatePkce() {
    byte[] bytes = new byte[32]
    new Random().nextBytes(bytes)

    String verifier = base64UrlNoPad(bytes)

    byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("UTF-8"))
    String challenge = base64UrlNoPad(digest)

    return [verifier: verifier, challenge: challenge]
}

private String base64UrlNoPad(byte[] b) {
    String s = b.encodeBase64().toString()
    s = s.replace("+", "-").replace("/", "_").replace("=", "")
    return s
}

/* ============================================================
 *  UTIL
 * ============================================================ */
private String azureScope() {
    return "openid email profile offline_access ${AZ_CLIENT_ID}"
}

private Map buildHeaders(Map headers, String cookieOut) {
    Map h = [:]
    if (headers) h.putAll(headers)
    if (cookieOut) h["Cookie"] = cookieOut
    return h
}

private String headerValue(resp, String name) { 
    try {
        List vals = headerValues(resp, name)
        return vals ? (vals[0] as String) : null
    } catch (Throwable t) { return null }
}

private List headerValues(resp, String name) {
    List out = []
    try {
        def hs = resp?.headers
        if (hs) {
            hs.each { h ->
                try {
                    String hn = h?.name
                    if (hn && hn.equalsIgnoreCase(name)) out << (h?.value as String)
                } catch (Throwable t2) { /* ignore */ }
            }
        }
    } catch (Throwable t) { /* ignore */ }
    return out
}

private int safeStatusCode(HttpResponseException e) {
    try { return (e?.statusCode ?: 0) as int } catch (Throwable t) { return 0 }
}

private String safeErrorBody(HttpResponseException e) {
    try {
        def data = e?.response?.data
        return safeBodyToString(data)
    } catch (Throwable t) {
        return ""
    }
}

private String safeErrorHeader(HttpResponseException e, String name) {
    try {
        def resp = e?.response
        if (resp) return headerValue(resp, name)
    } catch (Throwable t) { /* ignore */ }
    return null
}

private String safeBodyToString(def data) {
    if (data == null) return ""
    if (data instanceof String) return (String)data

    String fromReader = readAllIfReadable(data)
    if (fromReader != null) return fromReader

    try {
        def t = data?.text
        if (t != null) return t.toString()
    } catch (Throwable ignored) { }

    try {
        return data.toString()
    } catch (Throwable ignored2) {
        return ""
    }
}

// Hubitat-safe "duck typing" reader drain (no instanceof Reader, no ClassExpression, no getClass)
private String readAllIfReadable(def obj) {
    try {
        char[] buf = new char[4096]
        StringBuilder sb = new StringBuilder()
        while (true) {
            def nObj = obj.read(buf)
            if (nObj == null) break
            int n = (nObj as int)
            if (n <= 0) {
                if (n == -1) break
                break
            }
            sb.append(buf, 0, n)
        }
        try { obj.close() } catch (Throwable ignoredClose) { }
        String s = sb.toString()
        return (s != null && s.length() > 0) ? s : null
    } catch (Throwable ignored) {
        return null
    }
}

private String snippet(String s, int maxLen=250) {
    if (!s) return "(empty)"
    String x = s
    x = x.replaceAll(/(?i)\"password\"\s*:\s*\"[^\"]+\"/, '"password":"*****"')
    x = x.replaceAll(/(?i)password=[^&\s]+/, 'password=*****')
    x = x.replaceAll(/(?i)\"id_token\"\s*:\s*\"[^\"]+\"/, '"id_token":"hidden"')
    x = x.replaceAll(/(?i)\"access_token\"\s*:\s*\"[^\"]+\"/, '"access_token":"hidden"')
    x = x.replaceAll(/(?i)\"refresh_token\"\s*:\s*\"[^\"]+\"/, '"refresh_token":"hidden"')
    if (x.length() > maxLen) return x.substring(0, maxLen) + "...(len=" + x.length() + ")"
    return x
}

private String maskMid(String s) {
    if (!s) return "(null)"
    if (s.length() <= 16) return s
    return s.substring(0, 10) + "..." + s.substring(s.length()-6)
}

private String urlDecode(String s) {
    try { return URLDecoder.decode(s ?: "", "UTF-8") } catch (Throwable t) { return s }
}

private String absolutizeUrl(String base, String loc) {
    try {
        URI b = new URI(base)
        URI u = new URI(loc)
        if (u.isAbsolute()) return loc
        URI r = b.resolve(u)
        return r.toString()
    } catch (Throwable t) {
        return loc
    }
}

private Map fail(String code, Map detail) {
    logError "${code}"
    if (detail != null) {
        try {
            // keep the same detail serialization behavior as before (used in your debug)
            logDebug "detail=${JsonOutput.toJson(detail)}"
        } catch (Throwable ignored) {
            logDebug "detail=(unserializable)"
        }
    }
    return [ok:false, error:code]
}
