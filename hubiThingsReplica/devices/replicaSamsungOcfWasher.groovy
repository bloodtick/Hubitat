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
*/
public static String version() {return "1.3.0"}

metadata 
{
    definition(name: "Replica Samsung OCF Washer", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaSamsungOcfWasher.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Switch"
        capability "Refresh"
        // Special capablity to allow for Hubitat dashboarding to set commands via the Button template
        // Use Hubitat 'Button Controller' built in app to set commands to run. Defaults to 10 commands increased or decreased by setNumberOfButtons.
        capability "PushableButton"
        command "setNumberOfButtons", [[name: "numberOfButtons*", type: "NUMBER", description: "Set the numberOfButtons this device support"]]
        
        attribute "data", "JSON_OBJECT" //capability "execute" in SmartThings
        attribute "powerConsumption", "JSON_OBJECT" //capability "powerConsumptionReport" in SmartThings
        attribute "remoteControlEnabled", "enum", ["false", "true"] //capability "remoteControlStatus" in SmartThings
        attribute "completionTime", "string" //capability "washerOperatingState" in SmartThings
        attribute "machineState", "enum", ["pause", "run", "stop"] //capability "washerOperatingState" in SmartThings
        attribute "supportedMachineStates", "JSON_OBJECT" //capability "washerOperatingState" in SmartThings
        attribute "washerJobState", "enum", ["airWash", "aIRinse", "aISpin", "aIWash", "cooling", "delayWash", "drying", "finish", "none", "preWash", "rinse", "spin", "wash", "weightSensing", "wrinklePrevent", "freezeProtection"] //capability "washerOperatingState" in SmartThings
        attribute "disabledCapabilities", "JSON_OBJECT" //capability "custom.disabledCapabilities"
        attribute "dryerDryLevel", "enum", ["none","damp","less","normal","more","very","0","1","2","3","4","5","cupboard","extra","shirt","delicate","30","60","90","120","150","180","210","240","270"] //capability "custom.dryerDryLevel"
        attribute "supportedDryerDryLevel", "enum", ["none","damp","less","normal","more","very","0","1","2","3","4","5","cupboard","extra","shirt","delicate","30","60","90","120","150","180","210","240","270"] //capability "custom.dryerDryLevel"
        attribute "energySavingLevel", "number" //capability "custom.energyType"
        attribute "jobBeginningStatus", "enum", ["None", "ReadyToRun"] //capability "custom.jobBeginningStatus"
        attribute "washerAutoDetergent", "enum", ["on","off","notSupported","notUsed"] //capability "custom.washerAutoDetergent"
        attribute "washerAutoSoftener", "enum", ["on","off","notSupported","notUsed"] //capability "custom.washerAutoSoftener"
        attribute "supportedWasherRinseCycles", "JSON_OBJECT" //capability "custom.washerRinseCycles"
        attribute "washerRinseCycles", "enum", ["0","1","2","3","4","5"] //capability "custom.washerRinseCycles"
        attribute "supportedWasherSoilLevel", "JSON_OBJECT" //capability "custom.washerSoilLevel"
        attribute "washerSoilLevel", "enum", ["none","heavy","normal","light","extraLight","extraHeavy","up","down"] //capability "custom.washerSoilLevel"
        attribute "supportedWasherSpinLevel", "JSON_OBJECT" //capability "custom.washerSpinLevel"
        attribute "washerSpinLevel", "enum", ["none","rinseHold","noSpin","low","extraLow","delicate","medium","high","extraHigh","200","400","600","800","1000","1200","1400","1600"] //capability "custom.washerSpinLevel"
        attribute "supportedWasherWaterTemperature", "JSON_OBJECT" //capability "custom.washerWaterTemperature"
        attribute "washerWaterTemperature", "enum", ["none","20","30","40","50","60","65","70","75","80","90","95","tapCold","cold","cool","ecoWarm","warm","semiHot","hot","extraHot","extraLow","low","mediumLow","medium","high"] //capability "custom.washerWaterTemperature"     
        attribute "autoDispenseDetergentRemainingAmount", "enum", ["empty","less","normal","unknown"] //capability "samsungce.autoDispenseDetergent"
        attribute "autoDispenseDetergentAmount", "enum", ["none","less","standard","extra"] //capability "samsungce.autoDispenseDetergent"
        attribute "autoDispenseDetergentSupportedDensity", "JSON_OBJECT" //capability "samsungce.autoDispenseDetergent"
        attribute "autoDispenseDetergentDensity", "enum", ["none","normal","high","extraHigh"] //capability "samsungce.autoDispenseDetergent"
        attribute "autoDispenseDetergentSupportedAmount", "JSON_OBJECT" //capability "samsungce.autoDispenseDetergent"
        attribute "autoDispenseSoftenerRemainingAmount", "enum", ["empty","less","normal","unknown"] //capability "samsungce.autoDispenseSoftener"
        attribute "autoDispenseSoftenerAmount", "enum", ["none","less","standard","extra"] //capability "samsungce.autoDispenseSoftener"
        attribute "autoDispenseSoftenerSupportedDensity", "JSON_OBJECT" //capability "samsungce.autoDispenseSoftener"
        attribute "autoDispenseSoftenerDensity", "enum", ["none","normal","high","extraHigh"] //capability "samsungce.autoDispenseSoftener"
        attribute "autoDispenseSoftenerSupportedAmount", "JSON_OBJECT" //capability "samsungce.autoDispenseSoftener"        
        attribute "modelName", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "serialNumber", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "serialNumberExtra", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "modelClassificationCode", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "description", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "lockState", "enum", ["locked","unlocked","paused"] //capability "samsungce.kidsLock"
        attribute "washerBubbleSoakStatus", "enum", ["on","off"] //capability "samsungce.washerBubbleSoak"
        attribute "washerCycle", "string" //capability "samsungce.washerCycle"        
        attribute "sceWasherJobState", "enum", ["airWash","delayWash","drying","finished","none","preWash","rinse","spin","wash","weightSensing","aIWash","aIRinse","aISpin","freezeProtection"] //capability "samsungce.washerOperatingState"
        attribute "sceOperatingState", "enum", ["ready","running","paused"] //capability "samsungce.washerOperatingState"
        attribute "sceScheduledJobs", "JSON_OBJECT" //capability "samsungce.washerOperatingState"
        attribute "sceProgress", "number" //capability "samsungce.washerOperatingState"
        attribute "sceRemainingTimeString", "string" //capability "samsungce.washerOperatingState"
        attribute "sceOperationTime", "number" //capability "samsungce.washerOperatingState"
        attribute "sceRemainingTime", "number" //capability "samsungce.washerOperatingState"
        
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "execute", [[name: "command*", type: "STRING", description: "Command to execute"],[name: "args", type: "JSON_OBJECT", description: "Raw messages to be passed to a device"]] //capability "execute" in SmartThings
        command "overrideDrlcAction", [[name: "value*", type: "ENUM", description: "Override the requested load reduction request", constraints: ["false", "true"]]] //capability "demandResponseLoadControl"
        command "requestDrlcAction", [[name: "drlcType*", type: "NUMBER", description: "Type of load reduction request. One of the following: integer [0..1]"],[name: "drlcLevel*", type: "NUMBER", description: "integer [-1..4]"],[name: "start*", type: "STRING", description: "The Iso8601Date date & time at which load reduction is requested to start"]
                                     ,[name: "duration*", type: "NUMBER", description: "The length of time load reduction should take place, in minutes"],[name: "reportingPeriod", type: "NUMBER", description: "Optional reporting period in minutes. If specified the device will generate a drlcStatus event periodically while load reduction is in effect. If not specified then events will only be generated when the DRLC status changes."]]
        command "setMachineState", [[name: "state*", type: "ENUM", description: "Set the washer machine state to 'pause', 'run', or 'stop' state", constraints: ["pause", "run", "stop"]]] //capability "washerOperatingState"
        command "setDryerDryLevel", [[name: "dryLevel*", type: "ENUM", description: "Set dryer dry level", constraints: ["none","damp","less","normal","more","very","0","1","2","3","4","5","cupboard","extra","shirt","delicate","30","60","90","120","150","180","210","240","270"]]] //capability "custom.dryerDryLevel" 
        command "setEnergySavingLevel", [[name: "energySavingLevel*", type: "NUMBER", description: "Set Energy Saving Level"]] //capability "custom.energyType"
        command "setWasherAutoDetergent", [[name: "washerAutoDetergent*", type: "ENUM", description: "Set Washer Auto Detergent", constraints: ["on","off"]]] //capability "custom.washerAutoDetergent"
        command "setWasherAutoSoftener", [[name: "washerAutoSoftener*", type: "ENUM", description: "Set Washer Auto Softener", constraints: ["on","off"]]] //capability "custom.washerAutoSoftener"
        command "setWasherRinseCycles", [[name: "cycle*", type: "ENUM", description: "Set Washer Rinse Cycles", constraints: ["0","1","2","3","4","5"]]] //capability "custom.washerRinseCycles"
        command "setWasherSoilLevel", [[name: "soilLevel*", type: "ENUM", description: "Set Washer Soil Level", constraints: ["none","heavy","normal","light","extraLight","extraHeavy","up","down"]]] //capability "custom.washerSoilLevel"
        command "setWasherSpinLevel", [[name: "spinLevel*", type: "ENUM", description: "Set Washer Spin Level", constraints: ["none","rinseHold","noSpin","low","extraLow","delicate","medium","high","extraHigh","200","400","600","800","1000","1200","1400","1600"]]] //capability "custom.washerSpinLevel"
        command "setWasherWaterTemperature", [[name: "temperature*", type: "ENUM", description: "Set Washer Water Temperature", constraints: ["none","20","30","40","50","60","65","70","75","80","90","95","tapCold","cold","cool","ecoWarm","warm","semiHot","hot","extraHot","extraLow","low","mediumLow","medium","high"]]] //capability "custom.washerWaterTemperature"
        command "setAutoDispenseDetergentAmount", [[name: "amount*", type: "ENUM", description: "Set Auto Dispense Detergent Amount", constraints: ["none","less","standard","extra"]]] //samsungce.autoDispenseDetergent"
        command "setAutoDispenseDetergentDensity", [[name: "density*", type: "ENUM", description: "Set Auto Dispense Detergent Density", constraints: ["none","normal","high","extraHigh"]]] //samsungce.autoDispenseDetergent"        
        command "setAutoDispenseSoftenerAmount", [[name: "amount*", type: "ENUM", description: "Set Auto Dispense Softener Amount", constraints: ["none","less","standard","extra"]]] //samsungce.autoDispenseSoftener"
        command "setAutoDispenseSoftenerDensity", [[name: "density*", type: "ENUM", description: "Set Auto Dispense Softener Density", constraints: ["none","normal","high","extraHigh"]]] //samsungce.autoDispenseSoftener"
        command "washerBubbleSoakOn" //capability "samsungce.washerBubbleSoak"
        command "washerBubbleSoakOff" //capability "samsungce.washerBubbleSoak"        
        command "washerResume" //capability "samsungce.washerOperatingState"
        command "washerCancel" //capability "samsungce.washerOperatingState"
        command "washerStart" //capability "samsungce.washerOperatingState"
        command "washerPause" //capability "samsungce.washerOperatingState"
        
        
    }
    preferences {
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
    }
}

def push(buttonNumber) {
    sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
}

def setNumberOfButtons(buttonNumber) {
    sendEvent(name: "numberOfButtons", value: buttonNumber)
}

def installed() {
	initialize()
    setNumberOfButtons(10)
}

def updated() {
	initialize()    
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], "setDataValue":[[name:"data*",type:"JSON_OBJECT"]], "setPowerConsumptionValue":[[name:"powerConsumption*",type:"JSON_OBJECT"]],
              "setRemoteControlEnabledValue":[[name:"remoteControlEnabled*",type:"ENUM"]], "setDrlcStatusValue":[[name:"drlcStatus*",type:"JSON_OBJECT"]], "setCompletionTimeValue":[[name:"completionTime*",type:"STRING"]], "setMachineStateValue":[[name:"machineState*",type:"ENUM"]],
              "setSupportedMachineStatesValue":[[name:"supportedMachineStates*",type:"JSON_OBJECT"]], "setWasherJobStateValue":[[name:"washerJobState*",type:"ENUM"]], "setDisabledCapabilitiesValue":[[name:"disabledCapabilities*",type:"JSON_OBJECT"]],
              "setDryerDryLevelValue":[[name:"dryerDryLevel*",type:"ENUM"]], "setSupportedDryerDryLevelValue":[[name:"supportedDryerDryLevel*",type:"ENUM"]], "setEnergySavingLevelValue":[[name:"energySavingLevel*",type:"NUMBER"]], "setJobBeginningStatusValue":[[name:"jobBeginningStatus*",type:"ENUM"]],
              "setWasherAutoDetergentValue":[[name:"washerAutoDetergent*",type:"ENUM"]], "setWasherAutoSoftenerValue":[[name:"washerAutoSoftener*",type:"ENUM"]], "setSupportedWasherRinseCyclesValue":[[name:"supportedWasherRinseCycles*",type:"JSON_OBJECT"]], 
              "setWasherRinseCyclesValue":[[name:"washerRinseCycles*",type:"ENUM"]], "setSupportedWasherSoilLevelValue":[[name:"supportedWasherSoilLevel*",type:"JSON_OBJECT"]], "setWasherSoilLevelValue":[[name:"washerSoilLevel*",type:"ENUM"]],
              "setSupportedWasherSpinLevelValue":[[name:"supportedWasherSpinLevel*",type:"JSON_OBJECT"]], "setWasherSpinLevelValue":[[name:"washerSpinLevel*",type:"ENUM"]], "setSupportedWasherWaterTemperatureValue":[[name:"supportedWasherWaterTemperature*",type:"JSON_OBJECT"]], 
              "setWasherWaterTemperatureValue":[[name:"washerWaterTemperature*",type:"ENUM"]], "setAutoDispenseDetergentRemainingAmountValue":[[name:"autoDispenseDetergentRemainingAmount*",type:"ENUM"]], "setAutoDispenseDetergentAmountValue":[[name:"autoDispenseDetergentAmount*",type:"ENUM"]],
              "setAutoDispenseDetergentSupportedDensityValue":[[name:"autoDispenseDetergentSupportedDensity*",type:"JSON_OBJECT"]], "setAutoDispenseDetergentDensityValue":[[name:"autoDispenseDetergentDensity*",type:"ENUM"]], "setAutoDispenseDetergentSupportedAmountValue":[[name:"autoDispenseDetergentSupportedAmount*",type:"JSON_OBJECT"]],             
              "setAutoDispenseSoftenerRemainingAmountValue":[[name:"autoDispenseSoftenerRemainingAmount*",type:"ENUM"]], "setAutoDispenseSoftenerAmountValue":[[name:"autoDispenseSoftenerAmount*",type:"ENUM"]], "setAutoDispenseSoftenerSupportedDensityValue":[[name:"autoDispenseSoftenerSupportedDensity*",type:"JSON_OBJECT"]], 
              "setAutoDispenseSoftenerDensityValue":[[name:"autoDispenseSoftenerDensity*",type:"ENUM"]], "setAutoDispenseSoftenerSupportedAmountValue":[[name:"autoDispenseSoftenerSupportedAmount*",type:"JSON_OBJECT"]], "setLockStateValue":[[name:"lockState*",type:"ENUM"]],
              "setWasherBubbleSoakStatusValue":[[name:"washerBubbleSoakStatus*",type:"ENUM"]], "setWasherCycleValue":[[name:"washerCycle*",type:"STRING"]], "setSceWasherJobStateValue":[[name:"sceWasherJobState*",type:"ENUM"]], "setSceOperatingStateValue":[[name:"sceOperatingState*",type:"ENUM"]],
              "setSceScheduledJobsValue":[[name:"sceScheduledJobs*",type:"JSON_OBJECT"]], "setSceProgressValue":[[name:"sceProgress*",type:"NUMBER"]], "setSceRemainingTimeStringValue":[[name:"sceRemainingTimeString*",type:"STRING"]], "setSceOperationTimeValue":[[name:"sceOperationTime*",type:"NUMBER"]],
              "setSceRemainingTimeValue":[[name:"sceRemainingTime*",type:"NUMBER"]],
             
              "setModelNameValue":[[name:"modelName*",type:"STRING"]], "setSerialNumberValue":[[name:"serialNumber*",type:"STRING"]], "setSerialNumberExtraValue":[[name:"serialNumberExtra*",type:"STRING"]], 
              "setModelClassificationCodeValue":[[name:"modelClassificationCode*",type:"STRING"]], "setDescriptionValue":[[name:"description*",type:"STRING"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

//capability "Switch"
def setSwitchValue(value) {
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

//capability "samsungce.washerOperatingState"
def setSceWasherJobStateValue(value) {
    String descriptionText = "${device.displayName} samsung ce washer job state is $value"
    sendEvent(name: "sceWasherJobState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSceOperatingStateValue(value) {
    String descriptionText = "${device.displayName} samsung ce operating state is $value"
    sendEvent(name: "sceOperatingState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSceScheduledJobsValue(value) {
    String descriptionText = "${device.displayName} samsung ce scheduled jobs are $value"
    sendEvent(name: "sceScheduledJobs", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSceProgressValue(value) {
    String descriptionText = "${device.displayName} samsung ce progress is $value%"
    sendEvent(name: "sceProgress", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setSceRemainingTimeStringValue(value) {
    String descriptionText = "${device.displayName} samsung ce remaining time is $value"
    sendEvent(name: "sceRemainingTimeString", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSceOperationTimeValue(value) {
    String descriptionText = "${device.displayName} samsung ce operation time is $value% min"
    sendEvent(name: "sceOperationTime", value: value, unit: "min", descriptionText: descriptionText)
    logInfo descriptionText
}

def setSceRemainingTimeValue(value) {
    String descriptionText = "${device.displayName} samsung ce remaining time is $value% min"
    sendEvent(name: "sceRemainingTime", value: value, unit: "min", descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.washerCycle"
def setWasherCycleValue(value) {
    String descriptionText = "${device.displayName} washer cycle is $value"
    sendEvent(name: "washerCycle", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.washerBubbleSoak"
def setWasherBubbleSoakStatusValue(value) {
    String descriptionText = "${device.displayName} washer bubble soak status is $value"
    sendEvent(name: "washerBubbleSoakStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.kidsLock"
def setLockStateValue(value) {
    String descriptionText = "${device.displayName} lock state is $value"
    sendEvent(name: "lockState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.autoDispenseSoftener"
def setAutoDispenseSoftenerRemainingAmountValue(value) {
    String descriptionText = "${device.displayName} auto dispense softener remaining amount is $value"
    sendEvent(name: "autoDispenseSoftenerRemainingAmount", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseSoftenerAmountValue(value) {
    String descriptionText = "${device.displayName} auto dispense softener amount is $value"
    sendEvent(name: "autoDispenseSoftenerAmount", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseSoftenerSupportedDensityValue(value) {
    String descriptionText = "${device.displayName} auto dispense softener supported density is $value"
    sendEvent(name: "autoDispenseSoftenerSupportedDensity", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseSoftenerDensityValue(value) {
    String descriptionText = "${device.displayName} auto dispense softener density is $value"
    sendEvent(name: "autoDispenseSoftenerDensity", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseSoftenerSupportedAmountValue(value) {
    String descriptionText = "${device.displayName} auto dispense softener supported amount is $value"
    sendEvent(name: "autoDispenseSoftenerSupportedAmount", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.autoDispenseDetergent"
def setAutoDispenseDetergentRemainingAmountValue(value) {
    String descriptionText = "${device.displayName} auto dispense detergent remaining amount is $value"
    sendEvent(name: "autoDispenseDetergentRemainingAmount", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseDetergentAmountValue(value) {
    String descriptionText = "${device.displayName} auto dispense detergent amount is $value"
    sendEvent(name: "autoDispenseDetergentAmount", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseDetergentSupportedDensityValue(value) {
    String descriptionText = "${device.displayName} auto dispense detergent supported density is $value"
    sendEvent(name: "autoDispenseDetergentSupportedDensity", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseDetergentDensityValue(value) {
    String descriptionText = "${device.displayName} auto dispense detergent density is $value"
    sendEvent(name: "autoDispenseDetergentDensity", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAutoDispenseDetergentSupportedAmountValue(value) {
    String descriptionText = "${device.displayName} auto dispense detergent supported amount is $value"
    sendEvent(name: "autoDispenseDetergentSupportedAmount", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.washerWaterTemperature"
def setSupportedWasherWaterTemperatureValue(value) {
    String descriptionText = "${device.displayName} supported washer water temperatures are $value"
    sendEvent(name: "supportedWasherWaterTemperature", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setWasherWaterTemperatureValue(value) {
    String descriptionText = "${device.displayName} washer water temperature is $value"
    sendEvent(name: "washerWaterTemperature", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.washerSpinLevel"
def setSupportedWasherSpinLevelValue(value) {
    String descriptionText = "${device.displayName} supported washer spin levels are $value"
    sendEvent(name: "supportedWasherSpinLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setWasherSpinLevelValue(value) {
    String descriptionText = "${device.displayName} washer spin level is $value"
    sendEvent(name: "washerSpinLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.washerSoilLevel"
def setSupportedWasherSoilLevelValue(value) {
    String descriptionText = "${device.displayName} supported washer soil levels are $value"
    sendEvent(name: "supportedWasherSoilLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setWasherSoilLevelValue(value) {
    String descriptionText = "${device.displayName} washer soil level is $value"
    sendEvent(name: "washerSoilLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.washerAutoSoftener"
def setSupportedWasherRinseCyclesValue(value) {
    String descriptionText = "${device.displayName} supported washer rinse cycles are $value"
    sendEvent(name: "supportedWasherRinseCycles", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setWasherRinseCyclesValue(value) {
    String descriptionText = "${device.displayName} washer rinse cycles is $value"
    sendEvent(name: "washerRinseCycles", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.washerAutoSoftener"
def setWasherAutoSoftenerValue(value) {
    String descriptionText = "${device.displayName} washer auto softener is $value"
    sendEvent(name: "washerAutoSoftener", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.washerAutoDetergent"
def setWasherAutoDetergentValue(value) {
    String descriptionText = "${device.displayName} washer auto detergent is $value"
    sendEvent(name: "washerAutoDetergent", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.jobBeginningStatus"
def setJobBeginningStatusValue(value) {
    String descriptionText = "${device.displayName} job beginning status is $value"
    sendEvent(name: "jobBeginningStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.energyType"
def setEnergySavingLevelValue(value) {
    String descriptionText = "${device.displayName} energy saving level is $value"
    sendEvent(name: "energySavingLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.dryerDryLevel"
def setDryerDryLevelValue(value) {
    String descriptionText = "${device.displayName} dryer dry level is $value"
    sendEvent(name: "dryerDryLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedDryerDryLevelValue(value) {
    String descriptionText = "${device.displayName} supported dryer dry levels are $value"
    sendEvent(name: "supportedDryerDryLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.disabledCapabilities"
def setDisabledCapabilitiesValue(value) {
    String descriptionText = "${device.displayName} disabled capabilities are $value"
    sendEvent(name: "disabledCapabilities", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "washerOperatingState"
def setCompletionTimeValue(value) {
    String descriptionText = "${device.displayName} completion time is $value"
    sendEvent(name: "completionTime", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMachineStateValue(value) {
    String descriptionText = "${device.displayName} machine state is $value"
    sendEvent(name: "machineState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedMachineStatesValue(value) {
    String descriptionText = "${device.displayName} supported machine states are $value"
    sendEvent(name: "supportedMachineStates", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setWasherJobStateValue(value) {
    String descriptionText = "${device.displayName} washer job state is $value"
    sendEvent(name: "washerJobState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "demandResponseLoadControl"
def setDrlcStatusValue(value) {
    String descriptionText = "${device.displayName} demand response load control status is $value"
    sendEvent(name: "drlcStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "remoteControlStatus"
def setRemoteControlEnabledValue(value) {
    String descriptionText = "${device.displayName} remote control enabled is $value"
    sendEvent(name: "remoteControlEnabled", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "powerConsumptionReport"
def setPowerConsumptionValue(value) {
    String descriptionText = "${device.displayName} power consumption report is $value"
    sendEvent(name: "powerConsumption", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "execute"
def setDataValue(value) {
    String descriptionText = "${device.displayName} execute command data is $value"
    sendEvent(name: "data", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "samsungce.deviceIdentification"
def setModelNameValue(value) {
    String descriptionText = "${device.displayName} model name is $value"
    sendEvent(name: "modelName", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSerialNumberValue(value) {
    String descriptionText = "${device.displayName} serial number is $value"
    sendEvent(name: "serialNumber", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSerialNumberExtraValue(value) {
    String descriptionText = "${device.displayName} serial number extra is $value"
    sendEvent(name: "serialNumberExtra", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setModelClassificationCodeValue(value) {
    String descriptionText = "${device.displayName} model classification code is $value"
    sendEvent(name: "modelClassificationCode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDescriptionValue(value) {
    String descriptionText = "${device.displayName} description is $value"
    sendEvent(name: "description", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "off":[] , "on":[], "execute":[[name:"command*",type:"STRING"],[name:"args",type:"JSON_OBJECT",data:"args"]], "overrideDrlcAction":[[name:"value*",type:"ENUM"]], "setMachineState":[[name:"state*",type:"ENUM"]], "setDryerDryLevel":[[name:"dryLevel*",type:"ENUM"]],
              "requestDrlcAction":[[name:"drlcType*",type:"NUMBER"],[name:"drlcLevel*",type:"NUMBER",data:"drlcLevel"],[name:"start*",type:"STRING",data:"start"],[name:"duration*",type:"NUMBER",data:"duration"],[name:"reportingPeriod",type:"NUMBER",data:"reportingPeriod"]], 
              "setEnergySavingLevel":[[name:"energySavingLevel*",type:"NUMBER"]], "setWasherAutoDetergent":[[name:"washerAutoDetergent*",type:"ENUM"]], "setWasherAutoSoftener":[[name:"setWasherAutoSoftener*",type:"ENUM"]], "setWasherRinseCycles":[[name:"cycle*",type:"ENUM"]],
              "setWasherSoilLevel":[[name:"soilLevel*",type:"ENUM"]], "setWasherSpinLevel":[[name:"spinLevel*",type:"ENUM"]], "setWasherWaterTemperature":[[name:"temperature*",type:"ENUM"]], "setAutoDispenseDetergentAmount":[[name:"amount*",type:"ENUM"]],
              "setAutoDispenseDetergentDensity":[[name:"density*",type:"ENUM"]], "setAutoDispenseSoftenerAmount":[[name:"amount*",type:"ENUM"]], "setAutoDispenseSoftenerDensity":[[name:"density*",type:"ENUM"]], "washerBubbleSoakOff":[] , "washerBubbleSoakOn":[],
              "washerResume":[], "washerCancel":[], "washerStart":[], "washerPause":[],
              "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

//capability "Switch"
def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

//capability "samsungce.washerOperatingState"
def washerResume() { 
    sendCommand("washerResume")
}

def washerCancel() { 
    sendCommand("washerCancel")
}

def washerStart() { 
    sendCommand("washerStart")
}

def washerPause() { 
    sendCommand("washerPause")
}

//capability "samsungce.washerBubbleSoak"
def washerBubbleSoakOff() { 
    sendCommand("washerBubbleSoakOff")
}

def washerBubbleSoakOn() {
    sendCommand("washerBubbleSoakOn")
}

//capability "samsungce.autoDispenseSoftener"
def setAutoDispenseSoftenerAmount(amount) {
    sendCommand("setAutoDispenseSoftenerAmount", amount)    
}

def setAutoDispenseSoftenerDensity(density) {
    sendCommand("setAutoDispenseSoftenerDensity", density)    
}

//capability "samsungce.autoDispenseDetergent"
def setAutoDispenseDetergentAmount(amount) {
    sendCommand("setAutoDispenseDetergentAmount", amount)    
}

def setAutoDispenseDetergentDensity(density) {
    sendCommand("setAutoDispenseDetergentDensity", density)    
}

//capability "custom.washerWaterTemperature"
def setWasherWaterTemperature(temperature) {
    sendCommand("setWasherWaterTemperature", temperature)    
}

//capability "custom.washerSpinLevel"
def setWasherSpinLevel(spinLevel) {
    sendCommand("setWasherSpinLevel", spinLevel)    
}

//capability "custom.washerSoilLevel"
def setWasherSoilLevel(soilLevel) {
    sendCommand("setWasherSoilLevel", soilLevel)    
}

//capability "custom.washerRinseCycles"
def setWasherRinseCycles(cycle) {
    sendCommand("setWasherRinseCycles", cycle)    
}

//capability "custom.washerAutoSoftener"
def setWasherAutoSoftener(washerAutoSoftener) {
    sendCommand("setWasherAutoSoftener", washerAutoSoftener)    
}

//capability "custom.washerAutoDetergent"
def setWasherAutoDetergent(washerAutoDetergent) {
    sendCommand("setWasherAutoDetergent", washerAutoDetergent)    
}

//capability "custom.energyType"
def setEnergySavingLevel(energySavingLevel) {
    sendCommand("setEnergySavingLevel", energySavingLevel)    
}

//capability "custom.dryerDryLevel"
def setDryerDryLevel(dryLevel) {
    sendCommand("setDryerDryLevel", dryLevel)    
}

//capability "washerOperatingState"
def setMachineState(state) {
    sendCommand("setMachineState", state)    
}

//capability "demandResponseLoadControl"
def overrideDrlcAction(value) {
    sendCommand("overrideDrlcAction", value.toBoolean())    
}

def requestDrlcAction(drlcType, drlcLevel, start, duration, reportingPeriod=null) {
    sendCommand("requestDrlcAction", drlcType, null, [drlcLevel:drlcLevel, start:start, duration:duration, reportingPeriod:reportingPeriod] )    
}

//capability "execute"
def execute(command, args=null) {
    sendCommand("execute", command, null, [args:args])
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: switch:off()"},"type":"hubitatTrigger"},{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: switch:on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseSoftener","attribute":"amount","label":"attribute: samsungce.autoDispenseSoftener:amount.*"},"command":{"name":"setAutoDispenseSoftenerAmountValue","label":"command: setAutoDispenseSoftenerAmountValue(autoDispenseSoftenerAmount*)","type":"command","parameters":[{"name":"autoDispenseSoftenerAmount*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"dryerDryLevel","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.dryerDryLevel","attribute":"dryerDryLevel","label":"attribute: dryerDryLevel.*"},"command":{"name":"setDryerDryLevelValue","label":"command: setDryerDryLevelValue(dryerDryLevel*)","type":"command","parameters":[{"name":"dryerDryLevel*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer"}},"additionalProperties":false,"required":["value"],"capability":"custom.energyType","attribute":"energySavingLevel","label":"attribute: energySavingLevel.*"},"command":{"name":"setEnergySavingLevelValue","label":"command: setEnergySavingLevelValue(energySavingLevel*)","type":"command","parameters":[{"name":"energySavingLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"JsonObject","type":"object"},"data":{"type":"object","additionalProperties":true,"required":[]}},"additionalProperties":false,"required":["value"],"capability":"execute","attribute":"data","label":"attribute: data.*"},"command":{"name":"setDataValue","label":"command: setDataValue(data*)","type":"command","parameters":[{"name":"data*","type":"JSON_OBJECT"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"properties":{"deltaEnergy":{"type":"number"},"end":{"pattern":"removed","title":"Iso8601Date","type":"string"},"start":{"pattern":"removed","title":"Iso8601Date","type":"string"},"energySaved":{"type":"number"},"persistedSavedEnergy":{"type":"number"},"energy":{"type":"number"},"power":{"type":"number"},"powerEnergy":{"type":"number"},"persistedEnergy":{"type":"number"}},"additionalProperties":false,"title":"PowerConsumption","type":"object"}},"additionalProperties":false,"required":["value"],"capability":"powerConsumptionReport","attribute":"powerConsumption","label":"attribute: powerConsumption.*"},"command":{"name":"setPowerConsumptionValue","label":"command: setPowerConsumptionValue(powerConsumption*)","type":"command","parameters":[{"name":"powerConsumption*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"remoteControlStatus","attribute":"remoteControlEnabled","label":"attribute: remoteControlEnabled.*"},"command":{"name":"setRemoteControlEnabledValue","label":"command: setRemoteControlEnabledValue(remoteControlEnabled*)","type":"command","parameters":[{"name":"remoteControlEnabled*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"pattern":"removed","title":"Iso8601Date","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"washerOperatingState","attribute":"completionTime","label":"attribute: completionTime.*"},"command":{"name":"setCompletionTimeValue","label":"command: setCompletionTimeValue(completionTime*)","type":"command","parameters":[{"name":"completionTime*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"MachineState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"washerOperatingState","attribute":"machineState","label":"attribute: machineState.*"},"command":{"name":"setMachineStateValue","label":"command: setMachineStateValue(machineState*)","type":"command","parameters":[{"name":"machineState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"title":"MachineState","enum":["pause","run","stop"],"type":"string"},"type":"array"}},"additionalProperties":false,"required":[],"capability":"washerOperatingState","attribute":"supportedMachineStates","label":"attribute: supportedMachineStates.*"},"command":{"name":"setSupportedMachineStatesValue","label":"command: setSupportedMachineStatesValue(supportedMachineStates*)","type":"command","parameters":[{"name":"supportedMachineStates*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"washerOperatingState","attribute":"washerJobState","label":"attribute: washerJobState.*"},"command":{"name":"setWasherJobStateValue","label":"command: setWasherJobStateValue(washerJobState*)","type":"command","parameters":[{"name":"washerJobState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string"}}},"additionalProperties":false,"required":["value"],"capability":"custom.disabledCapabilities","attribute":"disabledCapabilities","label":"attribute: disabledCapabilities.*"},"command":{"name":"setDisabledCapabilitiesValue","label":"command: setDisabledCapabilitiesValue(disabledCapabilities*)","type":"command","parameters":[{"name":"disabledCapabilities*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"supportedDryerDryLevel","type":"attribute","properties":{"value":{"type":"array","items":{"enum":["none","damp","less","normal","more","very","0","1","2","3","4","5","cupboard","extra","shirt","delicate","30","60","90","120","150","180","210","240","270"],"type":"string"}}},"additionalProperties":false,"required":["value"],"capability":"custom.dryerDryLevel","attribute":"supportedDryerDryLevel","label":"attribute: supportedDryerDryLevel.*"},"command":{"name":"setSupportedDryerDryLevelValue","label":"command: setSupportedDryerDryLevelValue(supportedDryerDryLevel*)","type":"command","parameters":[{"name":"supportedDryerDryLevel*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.jobBeginningStatus","attribute":"jobBeginningStatus","label":"attribute: jobBeginningStatus.*"},"command":{"name":"setJobBeginningStatusValue","label":"command: setJobBeginningStatusValue(jobBeginningStatus*)","type":"command","parameters":[{"name":"jobBeginningStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"washerAutoDetergent","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerAutoDetergent","attribute":"washerAutoDetergent","label":"attribute: washerAutoDetergent.*"},"command":{"name":"setWasherAutoDetergentValue","label":"command: setWasherAutoDetergentValue(washerAutoDetergent*)","type":"command","parameters":[{"name":"washerAutoDetergent*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"washerAutoSoftener","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerAutoSoftener","attribute":"washerAutoSoftener","label":"attribute: washerAutoSoftener.*"},"command":{"name":"setWasherAutoSoftenerValue","label":"command: setWasherAutoSoftenerValue(washerAutoSoftener*)","type":"command","parameters":[{"name":"washerAutoSoftener*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"supportedWasherRinseCycles","type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["0","1","2","3","4","5"],"title":"rinseCycles"}}},"additionalProperties":false,"required":["value"],"capability":"custom.washerRinseCycles","attribute":"supportedWasherRinseCycles","label":"attribute: supportedWasherRinseCycles.*"},"command":{"name":"setSupportedWasherRinseCyclesValue","label":"command: setSupportedWasherRinseCyclesValue(supportedWasherRinseCycles*)","type":"command","parameters":[{"name":"supportedWasherRinseCycles*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"washerRinseCycles","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerRinseCycles","attribute":"washerRinseCycles","label":"attribute: washerRinseCycles.*"},"command":{"name":"setWasherRinseCyclesValue","label":"command: setWasherRinseCyclesValue(washerRinseCycles*)","type":"command","parameters":[{"name":"washerRinseCycles*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"supportedWasherSoilLevel","type":"attribute","properties":{"value":{"items":{"enum":["none","heavy","normal","light","extraLight","extraHeavy","up","down"],"title":"soilLevel","type":"string"},"type":"array"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerSoilLevel","attribute":"supportedWasherSoilLevel","label":"attribute: supportedWasherSoilLevel.*"},"command":{"name":"setSupportedWasherSoilLevelValue","label":"command: setSupportedWasherSoilLevelValue(supportedWasherSoilLevel*)","type":"command","parameters":[{"name":"supportedWasherSoilLevel*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"washerSoilLevel","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerSoilLevel","attribute":"washerSoilLevel","label":"attribute: washerSoilLevel.*"},"command":{"name":"setWasherSoilLevelValue","label":"command: setWasherSoilLevelValue(washerSoilLevel*)","type":"command","parameters":[{"name":"washerSoilLevel*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"supportedWasherSpinLevel","type":"attribute","properties":{"value":{"type":"array","items":{"enum":["none","rinseHold","noSpin","low","extraLow","delicate","medium","high","extraHigh","200","400","600","800","1000","1200","1400","1600"],"title":"spinLevel","type":"string"}}},"additionalProperties":false,"required":["value"],"capability":"custom.washerSpinLevel","attribute":"supportedWasherSpinLevel","label":"attribute: supportedWasherSpinLevel.*"},"command":{"name":"setSupportedWasherSpinLevelValue","label":"command: setSupportedWasherSpinLevelValue(supportedWasherSpinLevel*)","type":"command","parameters":[{"name":"supportedWasherSpinLevel*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"washerSpinLevel","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerSpinLevel","attribute":"washerSpinLevel","label":"attribute: washerSpinLevel.*"},"command":{"name":"setWasherSpinLevelValue","label":"command: setWasherSpinLevelValue(washerSpinLevel*)","type":"command","parameters":[{"name":"washerSpinLevel*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"supportedWasherWaterTemperature","type":"attribute","properties":{"value":{"type":"array","items":{"enum":["none","20","30","40","50","60","65","70","75","80","90","95","tapCold","cold","cool","ecoWarm","warm","semiHot","hot","extraHot","extraLow","low","mediumLow","medium","high"],"type":"string","title":"waterTemperature"}}},"additionalProperties":false,"required":["value"],"capability":"custom.washerWaterTemperature","attribute":"supportedWasherWaterTemperature","label":"attribute: supportedWasherWaterTemperature.*"},"command":{"name":"setSupportedWasherWaterTemperatureValue","label":"command: setSupportedWasherWaterTemperatureValue(supportedWasherWaterTemperature*)","type":"command","parameters":[{"name":"supportedWasherWaterTemperature*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"washerWaterTemperature","type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.washerWaterTemperature","attribute":"washerWaterTemperature","label":"attribute: washerWaterTemperature.*"},"command":{"name":"setWasherWaterTemperatureValue","label":"command: setWasherWaterTemperatureValue(washerWaterTemperature*)","type":"command","parameters":[{"name":"washerWaterTemperature*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"execute","label":"command: execute(command*, args)","type":"command","parameters":[{"name":"command*","type":"STRING"},{"name":"args","type":"JSON_OBJECT","data":"args"}]},"command":{"name":"execute","arguments":[{"name":"command","optional":false,"schema":{"title":"String","type":"string","maxLength":255}},{"name":"args","optional":true,"schema":{"title":"JsonObject","type":"object"}}],"type":"command","capability":"execute","label":"command: execute(command*, args)"},"type":"hubitatTrigger"},{"trigger":{"name":"overrideDrlcAction","label":"command: overrideDrlcAction(value*)","type":"command","parameters":[{"name":"value*","type":"ENUM"}]},"command":{"name":"overrideDrlcAction","arguments":[{"name":"value","optional":false,"schema":{"type":"boolean"}}],"type":"command","capability":"demandResponseLoadControl","label":"command: overrideDrlcAction(value*)"},"type":"hubitatTrigger"},{"trigger":{"name":"requestDrlcAction","label":"command: requestDrlcAction(drlcType*, drlcLevel*, start*, duration*, reportingPeriod)","type":"command","parameters":[{"name":"drlcType*","type":"NUMBER"},{"name":"drlcLevel*","type":"NUMBER","data":"drlcLevel"},{"name":"start*","type":"STRING","data":"start"},{"name":"duration*","type":"NUMBER","data":"duration"},{"name":"reportingPeriod","type":"NUMBER","data":"reportingPeriod"}]},"command":{"name":"requestDrlcAction","arguments":[{"name":"drlcType","optional":false,"schema":{"title":"DrlcType","type":"integer","minimum":0,"maximum":1}},{"name":"drlcLevel","optional":false,"schema":{"title":"DrlcLevel","type":"integer","minimum":-1,"maximum":4}},{"name":"start","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"duration","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}},{"name":"reportingPeriod","optional":true,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"demandResponseLoadControl","label":"command: requestDrlcAction(drlcType*, drlcLevel*, start*, duration*, reportingPeriod)"},"type":"hubitatTrigger"},{"trigger":{"name":"setMachineState","label":"command: setMachineState(state*)","type":"command","parameters":[{"name":"state*","type":"ENUM"}]},"command":{"name":"setMachineState","arguments":[{"name":"state","optional":false,"schema":{"title":"MachineState","enum":["pause","run","stop"],"type":"string"}}],"type":"command","capability":"washerOperatingState","label":"command: setMachineState(state*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setDryerDryLevel","label":"command: setDryerDryLevel(dryLevel*)","type":"command","parameters":[{"name":"dryLevel*","type":"ENUM"}]},"command":{"name":"setDryerDryLevel","arguments":[{"name":"dryLevel","optional":false,"schema":{"enum":["none","damp","less","normal","more","very","0","1","2","3","4","5","cupboard","extra","shirt","delicate","30","60","90","120","150","180","210","240","270"],"type":"string"}}],"type":"command","capability":"custom.dryerDryLevel","label":"command: setDryerDryLevel(dryLevel*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setEnergySavingLevel","label":"command: setEnergySavingLevel(energySavingLevel*)","type":"command","parameters":[{"name":"energySavingLevel*","type":"NUMBER"}]},"command":{"name":"setEnergySavingLevel","arguments":[{"name":"energySavingLevel","optional":false,"schema":{"type":"integer"}}],"type":"command","capability":"custom.energyType","label":"command: setEnergySavingLevel(energySavingLevel*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setWasherAutoDetergent","label":"command: setWasherAutoDetergent(washerAutoDetergent*)","type":"command","parameters":[{"name":"washerAutoDetergent*","type":"ENUM"}]},"command":{"name":"setWasherAutoDetergent","arguments":[{"name":"washerAutoDetergent","optional":false,"schema":{"type":"string","enum":["on","off"]}}],"type":"command","capability":"custom.washerAutoDetergent","label":"command: setWasherAutoDetergent(washerAutoDetergent*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setWasherAutoSoftener","label":"command: setWasherAutoSoftener(setWasherAutoSoftener*)","type":"command","parameters":[{"name":"setWasherAutoSoftener*","type":"ENUM"}]},"command":{"name":"setWasherAutoSoftener","arguments":[{"name":"washerAutoSoftener","optional":false,"schema":{"type":"string","enum":["on","off"]}}],"type":"command","capability":"custom.washerAutoSoftener","label":"command: setWasherAutoSoftener(washerAutoSoftener*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setWasherRinseCycles","label":"command: setWasherRinseCycles(cycle*)","type":"command","parameters":[{"name":"cycle*","type":"ENUM"}]},"command":{"name":"setWasherRinseCycles","arguments":[{"name":"cycle","optional":false,"schema":{"type":"string","enum":["0","1","2","3","4","5"]}}],"type":"command","capability":"custom.washerRinseCycles","label":"command: setWasherRinseCycles(cycle*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setWasherSoilLevel","label":"command: setWasherSoilLevel(soilLevel*)","type":"command","parameters":[{"name":"soilLevel*","type":"ENUM"}]},"command":{"name":"setWasherSoilLevel","arguments":[{"name":"soilLevel","optional":false,"schema":{"enum":["none","heavy","normal","light","extraLight","extraHeavy","up","down"],"type":"string"}}],"type":"command","capability":"custom.washerSoilLevel","label":"command: setWasherSoilLevel(soilLevel*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setWasherSpinLevel","label":"command: setWasherSpinLevel(spinLevel*)","type":"command","parameters":[{"name":"spinLevel*","type":"ENUM"}]},"command":{"name":"setWasherSpinLevel","arguments":[{"name":"spinLevel","optional":false,"schema":{"enum":["none","rinseHold","noSpin","low","extraLow","delicate","medium","high","extraHigh","200","400","600","800","1000","1200","1400","1600"],"type":"string"}}],"type":"command","capability":"custom.washerSpinLevel","label":"command: setWasherSpinLevel(spinLevel*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setWasherWaterTemperature","label":"command: setWasherWaterTemperature(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"ENUM"}]},"command":{"name":"setWasherWaterTemperature","arguments":[{"name":"temperature","optional":false,"schema":{"enum":["none","20","30","40","50","60","65","70","75","80","90","95","tapCold","cold","cool","ecoWarm","warm","semiHot","hot","extraHot","extraLow","low","mediumLow","medium","high"],"type":"string"}}],"type":"command","capability":"custom.washerWaterTemperature","label":"command: setWasherWaterTemperature(temperature*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseDetergent","attribute":"amount","label":"attribute: amount.*"},"command":{"name":"setAutoDispenseDetergentAmountValue","label":"command: setAutoDispenseDetergentAmountValue(autoDispenseDetergentAmount*)","type":"command","parameters":[{"name":"autoDispenseDetergentAmount*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseDetergent","attribute":"density","label":"attribute: density.*"},"command":{"name":"setAutoDispenseDetergentDensityValue","label":"command: setAutoDispenseDetergentDensityValue(autoDispenseDetergentDensity*)","type":"command","parameters":[{"name":"autoDispenseDetergentDensity*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseDetergent","attribute":"remainingAmount","label":"attribute: remainingAmount.*"},"command":{"name":"setAutoDispenseDetergentRemainingAmountValue","label":"command: setAutoDispenseDetergentRemainingAmountValue(autoDispenseDetergentRemainingAmount*)","type":"command","parameters":[{"name":"autoDispenseDetergentRemainingAmount*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["none","normal","high","extraHigh"]}}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseDetergent","attribute":"supportedDensity","label":"attribute: supportedDensity.*"},"command":{"name":"setAutoDispenseDetergentSupportedDensityValue","label":"command: setAutoDispenseDetergentSupportedDensityValue(autoDispenseDetergentSupportedDensity*)","type":"command","parameters":[{"name":"autoDispenseDetergentSupportedDensity*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["none","less","standard","extra"]}}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseDetergent","attribute":"supportedAmount","label":"attribute: supportedAmount.*"},"command":{"name":"setAutoDispenseDetergentSupportedAmountValue","label":"command: setAutoDispenseDetergentSupportedAmountValue(autoDispenseDetergentSupportedAmount*)","type":"command","parameters":[{"name":"autoDispenseDetergentSupportedAmount*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseSoftener","attribute":"remainingAmount","label":"attribute: samsungce.autoDispenseSoftener:remainingAmount.*"},"command":{"name":"setAutoDispenseSoftenerRemainingAmountValue","label":"command: setAutoDispenseSoftenerRemainingAmountValue(autoDispenseSoftenerRemainingAmount*)","type":"command","parameters":[{"name":"autoDispenseSoftenerRemainingAmount*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["none","normal","high","extraHigh"]}}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseSoftener","attribute":"supportedDensity","label":"attribute: samsungce.autoDispenseSoftener:supportedDensity.*"},"command":{"name":"setAutoDispenseSoftenerSupportedDensityValue","label":"command: setAutoDispenseSoftenerSupportedDensityValue(autoDispenseSoftenerSupportedDensity*)","type":"command","parameters":[{"name":"autoDispenseSoftenerSupportedDensity*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseSoftener","attribute":"density","label":"attribute: samsungce.autoDispenseSoftener:density.*"},"command":{"name":"setAutoDispenseSoftenerDensityValue","label":"command: setAutoDispenseSoftenerDensityValue(autoDispenseSoftenerDensity*)","type":"command","parameters":[{"name":"autoDispenseSoftenerDensity*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["none","less","standard","extra"]}}},"additionalProperties":false,"required":["value"],"capability":"samsungce.autoDispenseSoftener","attribute":"supportedAmount","label":"attribute: samsungce.autoDispenseSoftener:supportedAmount.*"},"command":{"name":"setAutoDispenseSoftenerSupportedAmountValue","label":"command: setAutoDispenseSoftenerSupportedAmountValue(autoDispenseSoftenerSupportedAmount*)","type":"command","parameters":[{"name":"autoDispenseSoftenerSupportedAmount*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"modelName","label":"attribute: modelName.*"},"command":{"name":"setModelNameValue","label":"command: setModelNameValue(modelName*)","type":"command","parameters":[{"name":"modelName*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"serialNumber","label":"attribute: serialNumber.*"},"command":{"name":"setSerialNumberValue","label":"command: setSerialNumberValue(serialNumber*)","type":"command","parameters":[{"name":"serialNumber*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.kidsLock","attribute":"lockState","label":"attribute: lockState.*"},"command":{"name":"setLockStateValue","label":"command: setLockStateValue(lockState*)","type":"command","parameters":[{"name":"lockState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.washerBubbleSoak","attribute":"status","label":"attribute: samsungce.washerBubbleSoak:status.*"},"command":{"name":"setWasherBubbleSoakStatusValue","label":"command: setWasherBubbleSoakStatusValue(washerBubbleSoakStatus*)","type":"command","parameters":[{"name":"washerBubbleSoakStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.washerCycle","attribute":"washerCycle","label":"attribute: washerCycle.*"},"command":{"name":"setWasherCycleValue","label":"command: setWasherCycleValue(washerCycle*)","type":"command","parameters":[{"name":"washerCycle*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"name":"setAutoDispenseDetergentAmount","label":"command: setAutoDispenseDetergentAmount(amount*)","type":"command","parameters":[{"name":"amount*","type":"ENUM"}]},"command":{"name":"setAmount","arguments":[{"name":"amount","optional":false,"schema":{"type":"string","enum":["none","less","standard","extra"]}}],"type":"command","capability":"samsungce.autoDispenseDetergent","label":"command: samsungce.autoDispenseDetergent:setAmount(amount*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setAutoDispenseDetergentDensity","label":"command: setAutoDispenseDetergentDensity(density*)","type":"command","parameters":[{"name":"density*","type":"ENUM"}]},"command":{"name":"setDensity","arguments":[{"name":"density","optional":false,"schema":{"type":"string","enum":["none","normal","high","extraHigh"]}}],"type":"command","capability":"samsungce.autoDispenseDetergent","label":"command: samsungce.autoDispenseDetergent:setDensity(density*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setAutoDispenseSoftenerAmount","label":"command: setAutoDispenseSoftenerAmount(amount*)","type":"command","parameters":[{"name":"amount*","type":"ENUM"}]},"command":{"name":"setAmount","arguments":[{"name":"amount","optional":false,"schema":{"type":"string","enum":["none","less","standard","extra"]}}],"type":"command","capability":"samsungce.autoDispenseSoftener","label":"command: samsungce.autoDispenseSoftener:setAmount(amount*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setAutoDispenseSoftenerDensity","label":"command: setAutoDispenseSoftenerDensity(density*)","type":"command","parameters":[{"name":"density*","type":"ENUM"}]},"command":{"name":"setDensity","arguments":[{"name":"density","optional":false,"schema":{"type":"string","enum":["none","normal","high","extraHigh"]}}],"type":"command","capability":"samsungce.autoDispenseSoftener","label":"command: samsungce.autoDispenseSoftener:setDensity(density*)"},"type":"hubitatTrigger"},{"trigger":{"name":"washerBubbleSoakOn","label":"command: washerBubbleSoakOn()","type":"command"},"command":{"name":"on","type":"command","capability":"samsungce.washerBubbleSoak","label":"command: samsungce.washerBubbleSoak:on()"},"type":"hubitatTrigger"},{"trigger":{"name":"washerBubbleSoakOff","label":"command: washerBubbleSoakOff()","type":"command"},"command":{"name":"off","type":"command","capability":"samsungce.washerBubbleSoak","label":"command: samsungce.washerBubbleSoak:off()"},"type":"hubitatTrigger"},{"trigger":{"name":"washerResume","label":"command: washerResume()","type":"command"},"command":{"name":"resume","type":"command","capability":"samsungce.washerOperatingState","label":"command: resume()"},"type":"hubitatTrigger"},{"trigger":{"name":"washerCancel","label":"command: washerCancel()","type":"command"},"command":{"name":"cancel","type":"command","capability":"samsungce.washerOperatingState","label":"command: cancel()"},"type":"hubitatTrigger"},{"trigger":{"name":"washerStart","label":"command: washerStart()","type":"command"},"command":{"name":"start","type":"command","capability":"samsungce.washerOperatingState","label":"command: start()"},"type":"hubitatTrigger"},{"trigger":{"name":"washerPause","label":"command: washerPause()","type":"command"},"command":{"name":"pause","type":"command","capability":"samsungce.washerOperatingState","label":"command: pause()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.washerOperatingState","attribute":"washerJobState","label":"attribute: samsungce.washerOperatingState:washerJobState.*"},"command":{"name":"setSceWasherJobStateValue","label":"command: setSceWasherJobStateValue(sceWasherJobState*)","type":"command","parameters":[{"name":"sceWasherJobState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.washerOperatingState","attribute":"operatingState","label":"attribute: samsungce.washerOperatingState:operatingState.*"},"command":{"name":"setSceOperatingStateValue","label":"command: setSceOperatingStateValue(sceOperatingState*)","type":"command","parameters":[{"name":"sceOperatingState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"object","properties":{"jobName":{"type":"string","enum":["airWash","delayWash","drying","finished","none","preWash","rinse","spin","wash","weightSensing","aIWash","aIRinse","aISpin","freezeProtection"]},"timeInMin":{"type":"integer"}},"required":["jobName","timeInMin"]}}},"additionalProperties":false,"required":["value"],"capability":"samsungce.washerOperatingState","attribute":"scheduledJobs","label":"attribute: scheduledJobs.*"},"command":{"name":"setSceScheduledJobsValue","label":"command: setSceScheduledJobsValue(sceScheduledJobs*)","type":"command","parameters":[{"name":"sceScheduledJobs*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value","unit"],"capability":"samsungce.washerOperatingState","attribute":"progress","label":"attribute: progress.*"},"command":{"name":"setSceProgressValue","label":"command: setSceProgressValue(sceProgress*)","type":"command","parameters":[{"name":"sceProgress*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","pattern":"remove"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.washerOperatingState","attribute":"remainingTimeStr","label":"attribute: remainingTimeStr.*"},"command":{"name":"setSceRemainingTimeStringValue","label":"command: setSceRemainingTimeStringValue(sceRemainingTimeString*)","type":"command","parameters":[{"name":"sceRemainingTimeString*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":1440},"unit":{"type":"string","enum":["min"],"default":"min"}},"additionalProperties":false,"required":["value","unit"],"capability":"samsungce.washerOperatingState","attribute":"operationTime","label":"attribute: operationTime.*"},"command":{"name":"setSceOperationTimeValue","label":"command: setSceOperationTimeValue(sceOperationTime*)","type":"command","parameters":[{"name":"sceOperationTime*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":1440},"unit":{"type":"string","enum":["min"],"default":"min"}},"additionalProperties":false,"required":["value","unit"],"capability":"samsungce.washerOperatingState","attribute":"remainingTime","label":"attribute: samsungce.washerOperatingState:remainingTime.*"},"command":{"name":"setSceRemainingTimeValue","label":"command: setSceRemainingTimeValue(sceRemainingTime*)","type":"command","parameters":[{"name":"sceRemainingTime*","type":"NUMBER"}]},"type":"smartTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
