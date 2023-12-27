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
public static String version() {return "1.3.1"}

metadata 
{
    definition(name: "Replica Samsung OCF AirCon", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/devices/replicaSamsungOcfAirCon.groovy")
    {
        capability "Actuator"
        capability "AirQuality"
        capability "Configuration"
        capability "RelativeHumidityMeasurement"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "ThermostatCoolingSetpoint"
        capability "Refresh"
        // Special capablity to allow for Hubitat dashboarding to set commands via the Button template
        // Use Hubitat 'Button Controller' built in app to set commands to run. Defaults to 10 commands increased or decreased by setNumberOfButtons.
        capability "PushableButton"
        command "setNumberOfButtons", [[name: "numberOfButtons*", type: "NUMBER", description: "Set the numberOfButtons this device support"]]
        
        attribute "airConditionerMode", "string" //capability "airConditionerMode" in SmartThings
        attribute "supportedAcModes", "JSON_OBJECT" //capability "airConditionerMode" in SmartThings
        attribute "fanMode", "string" //capability "airConditionerFanMode" in SmartThings
        attribute "supportedAcFanModes", "JSON_OBJECT" //capability "airConditionerFanMode" in SmartThings
        attribute "fanOscillationMode", "enum", ["off", "individual", "fixed", "vertical", "horizontal", "all", "indirect", "direct", "fixedCenter", "fixedLeft", "fixedRight", "far", "wide", "mid", "spot", "swing"] //capability "fanOscillationMode" in SmartThings
        attribute "supportedFanOscillationModes", "JSON_OBJECT" //capability "fanOscillationMode" in SmartThings
        attribute "dustLevel", "number" //capability "dustSensor" in SmartThings
        attribute "fineDustLevel", "number" //capability "dustSensor" in SmartThings
        attribute "veryFineDustLevel", "number" //capability "veryFineDustSensor" in SmartThings
        attribute "remoteControlEnabled", "enum", ["false", "true"] //capability "remoteControlStatus" in SmartThings
        attribute "powerConsumption", "JSON_OBJECT" //capability "powerConsumptionReport" in SmartThings
        attribute "drlcStatus", "JSON_OBJECT" //capability "demandResponseLoadControl" in SmartThings
        attribute "energySavingLevel", "number" //capability "custom.energyType" in SmartThings        
        attribute "volume", "number" //capability "audioVolume" in SmartThings
        attribute "data", "JSON_OBJECT" //capability "execute" in SmartThings        
        attribute "spiMode", "enum", ["off", "on"] //capability "custom.spiMode" in SmartThings
        attribute "minimumSetpoint", "number" //capability "custom.thermostatSetpointControl" in SmartThings
        attribute "maximumSetpoint", "number" //capability "custom.thermostatSetpointControl" in SmartThings
        attribute "acOptionalMode", "enum", ["off", "energySaving", "windFree", "sleep", "windFreeSleep", "speed", "smart", "quiet", "twoStep", "comfort", "dlightCool", "cubePurify", "longWind", "motionIndirect", "motionDirect"] //capability "custom.airConditionerOptionalMode" in SmartThings
        attribute "supportedAcOptionalMode", "JSON_OBJECT" //capability "custom.airConditionerOptionalMode" in SmartThings
        attribute "acTropicalNightModeLevel", "number" //capability "custom.airConditionerTropicalNightMode" in SmartThings
        attribute "autoCleaningMode", "enum", ["off", "speedClean", "quietClean", "on"] //capability "custom.autoCleaningMode" in SmartThings
        attribute "dustFilterUsageStep", "number" //capability "custom.dustFilter" in SmartThings
        attribute "dustFilterUsage", "number" //capability "custom.dustFilter" in SmartThings
        attribute "dustFilterLastResetDate", "string" //capability "custom.dustFilter" in SmartThings
        attribute "dustFilterStatus", "enum", ["normal", "replace", "wash", "notused"] //capability "custom.dustFilter" in SmartThings
        attribute "dustFilterCapacity", "number" //capability "custom.dustFilter" in SmartThings
        attribute "dustFilterResetType", "enum", ["replaceable", "washable"] //capability "custom.dustFilter" in SmartThings
        attribute "airConditionerOdorControllerProgress", "number" //capability "custom.airConditionerOdorController" in SmartThings
        attribute "airConditionerOdorControllerState", "enum", ["off", "on"] //capability "custom.airConditionerOdorController" in SmartThings        
        attribute "deodorFilterUsageStep", "number" //capability "custom.deodorFilter" in SmartThings
        attribute "deodorFilterUsage", "number" //capability "custom.deodorFilter" in SmartThings
        attribute "deodorFilterLastResetDate", "string" //capability "custom.deodorFilter" in SmartThings
        attribute "deodorFilterStatus", "enum", ["normal", "replace", "wash", "notused"] //capability "custom.deodorFilter" in SmartThings
        attribute "deodorFilterCapacity", "number" //capability "custom.deodorFilter" in SmartThings
        attribute "deodorFilterResetType", "enum", ["replaceable", "washable", "notresetable"] //capability "custom.deodorFilter" in SmartThings
        attribute "modelName", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "serialNumber", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "serialNumberExtra", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "modelClassificationCode", "string" //capability "samsungce.deviceIdentification" in SmartThings
        attribute "description", "string" //capability "samsungce.deviceIdentification" in SmartThings        
        attribute "healthStatus", "enum", ["offline", "online"]
        
        command "setAirConditionerMode", [[name: "mode*", type: "STRING", description: "Set the air conditioner mode"]] //capability "airConditionerMode" in SmartThings
        command "setFanMode", [[name: "fanMode*", type: "STRING", description: "Set the fan mode"]] //capability "airConditionerFanMode" in SmartThings
        command "setFanOscillationMode", [[name: "fanOscillationMode*", type: "ENUM", description: "Set oscillation mode", constraints: ["off", "individual", "fixed", "vertical", "horizontal", "all", "indirect", "direct", "fixedCenter", "fixedLeft", "fixedRight", "far", "wide", "mid", "spot", "swing"]]] //capability "fanOscillationMode" in SmartThings
        command "overrideDrlcAction", [[name: "value*", type: "ENUM", description: "Override the requested load reduction request", constraints: ["false", "true"]]] //capability "demandResponseLoadControl"
        command "requestDrlcAction", [[name: "drlcType*", type: "NUMBER", description: "Type of load reduction request. One of the following: integer [0..1]"],[name: "drlcLevel*", type: "NUMBER", description: "integer [-1..4]"],[name: "start*", type: "STRING", description: "The Iso8601Date date & time at which load reduction is requested to start"]
                                     ,[name: "duration*", type: "NUMBER", description: "The length of time load reduction should take place, in minutes"],[name: "reportingPeriod", type: "NUMBER", description: "Optional reporting period in minutes. If specified the device will generate a drlcStatus event periodically while load reduction is in effect. If not specified then events will only be generated when the DRLC status changes."]]
        command "setEnergySavingLevel", [[name: "energySavingLevel*", type: "NUMBER", description: "Set Energy Saving Level"]] //capability "custom.energyType"
        command "volumeDown" //capability "audioVolume"
        command "volumeUp" //capability "audioVolume"
        command "setVolume", [[name: "volume*", type: "NUMBER", description: "Set the audio volume level in %"]] //capability "audioVolume"
        command "execute", [[name: "command*", type: "STRING", description: "Command to execute"],[name: "args", type: "JSON_OBJECT", description: "Raw messages to be passed to a device"]] //capability "execute" in SmartThings
        command "setSpiMode", [[name: "mode*", type: "ENUM", description: "Set SPi (Super-Plasma Ion) mode", constraints: ["off", "on"]]] //capability "custom.spiMode" in SmartThings
        command "raiseSetpoint" //capability "custom.thermostatSetpointControl"
        command "lowerSetpoint" //capability "custom.thermostatSetpointControl"
        command "setAcOptionalMode", [[name: "mode*", type: "ENUM", description: "Set the air conditioner optional mode", constraints: ["off", "energySaving", "windFree", "sleep", "windFreeSleep", "speed", "smart", "quiet", "twoStep", "comfort", "dlightCool", "cubePurify", "longWind", "motionIndirect", "motionDirect"]]] //capability "custom.airConditionerOptionalMode" in SmartThings
        command "setAcTropicalNightModeLevel", [[name: "hours*", type: "NUMBER", description: "Set Tropical Night Mode level in hours 0-35"]] //capability "custom.airConditionerTropicalNightMode"
        command "setAutoCleaningMode", [[name: "mode*", type: "ENUM", description: "Set Auto Cleaning Mode", constraints: ["off", "speedClean", "quietClean", "on"]]] //capability "custom.autoCleaningMode" in SmartThings
        command "resetDustFilter" //capability "custom.dustFilter"
        command "setAirConditionerOdorControllerState", [[name: "state*", type: "ENUM", description: "Set Air Conditioner Odor Controller State", constraints: ["off", "on"]]] //capability "custom.airConditionerOdorController" in SmartThings
        command "resetDeodorFilter" //capability "custom.deodorFilter"
        
        // uses execute command and polls 'data' element for status. Not part of normal ST API.
        command "liteOff"
        command "liteOn"
        attribute "lite", "enum", ["on", "off"]
        command "beepOff"
        command "beepOn"
        attribute "beep", "enum", ["on", "off"]  
        
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
    return ([ "setSwitchValue":[[name:"switch*",type:"ENUM"]], "setSwitchOff":[], "setSwitchOn":[], "setAirConditionerModeValue":[[name:"airConditionerMode*",type:"STRING"]], "setSupportedAcModesValue":[[name:"supportedAcModes*",type:"JSON_OBJECT"]], 
              "setFanModeValue":[[name:"fanMode*",type:"STRING"]], "setSupportedAcFanModesValue":[[name:"supportedAcFanModes*",type:"JSON_OBJECT"]], "setFanOscillationModeValue":[[name:"fanOscillationMode*",type:"ENUM"]], "setSupportedFanOscillationModesValue":[[name:"supportedFanOscillationModes*",type:"JSON_OBJECT"]], 
              "setAirQualityValue":[[name:"airQuality*",type:"NUMBER"]], "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], "setCoolingSetpointValue":[[name:"coolingSetpoint*",type:"NUMBER"]], "setHumidityValue":[[name:"humidity*",type:"NUMBER"]], 
              "setDustLevelValue":[[name:"dustLevel*",type:"NUMBER"]], "setFineDustLevelValue":[[name:"fineDustLevel*",type:"NUMBER"]], "setVeryFineDustLevelValue":[[name:"veryFineDustLevel*",type:"NUMBER"]], "setRemoteControlEnabledValue":[[name:"remoteControlEnabled*",type:"ENUM"]], 
              "setPowerConsumptionValue":[[name:"powerConsumption*",type:"JSON_OBJECT"]], "setDrlcStatusValue":[[name:"drlcStatus*",type:"JSON_OBJECT"]], "setVolumeValue":[[name:"volume*",type:"NUMBER"]], "setDataValue":[[name:"data*",type:"JSON_OBJECT"]], "setSpiModeValue":[[name:"spiMode*",type:"ENUM"]], "setSpiModeOff":[], "setSpiModeOn":[], 
              "setMinimumSetpointValue":[[name:"minimumSetpoint*",type:"NUMBER"]],  "setMaximumSetpointValue":[[name:"maximumSetpoint*",type:"NUMBER"]], "setAcOptionalModeValue":[[name:"acOptionalMode*",type:"ENUM"]], "setSupportedAcOptionalModeValue":[[name:"supportedAcOptionalMode*",type:"JSON_OBJECT"]],
              "setAcTropicalNightModeLevelValue":[[name:"acTropicalNightModeLevel*",type:"NUMBER"]], "setAutoCleaningModeValue":[[name:"autoCleaningMode*",type:"ENUM"]], "setDustFilterUsageStepValue":[[name:"dustFilterUsageStep*",type:"NUMBER"]], "setDustFilterUsageValue":[[name:"dustFilterUsage*",type:"NUMBER"]],
              "setDustFilterLastResetDateValue":[[name:"dustFilterLastResetDate*",type:"STRING"]], "setDustFilterStatusValue":[[name:"dustFilterStatus*",type:"ENUM"]], "setDustFilterCapacityValue":[[name:"dustFilterCapacity*",type:"NUMBER"]], "setDustFilterResetTypeValue":[[name:"dustFilterResetType*",type:"ENUM"]],
              "setAirConditionerOdorControllerProgressValue":[[name:"airConditionerOdorControllerProgress*",type:"NUMBER"]], "setAirConditionerOdorControllerStateValue":[[name:"airConditionerOdorControllerState*",type:"ENUM"]], "setDeodorFilterUsageStepValue":[[name:"deodorFilterUsageStep*",type:"NUMBER"]], 
              "setDeodorFilterUsageValue":[[name:"deodorFilterUsage*",type:"NUMBER"]], "setDeodorFilterLastResetDateValue":[[name:"deodorFilterLastResetDate*",type:"STRING"]], "setDeodorFilterStatusValue":[[name:"deodorFilterStatus*",type:"ENUM"]], "setDeodorFilterCapacityValue":[[name:"deodorFilterCapacity*",type:"NUMBER"]], 
              "setDeodorFilterResetTypeValue":[[name:"deodorFilterResetType*",type:"ENUM"]], "setModelNameValue":[[name:"modelName*",type:"STRING"]], "setSerialNumberValue":[[name:"serialNumber*",type:"STRING"]], "setSerialNumberExtraValue":[[name:"serialNumberExtra*",type:"STRING"]], 
              "setModelClassificationCodeValue":[[name:"modelClassificationCode*",type:"STRING"]], "setDescriptionValue":[[name:"description*",type:"STRING"]], "setEnergySavingLevelValue":[[name:"energySavingLevel*",type:"NUMBER"]], "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
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

//capability "airConditionerMode"
def setAirConditionerModeValue(value) {
    String descriptionText = "${device.displayName} Air Conditioner mode is $value"
    sendEvent(name: "airConditionerMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedAcModesValue(value) {
    String descriptionText = "${device.displayName} supported Air Conditioner modes are $value"
    sendEvent(name: "supportedAcModes", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "airConditionerFanMode"
def setFanModeValue(value) {
    String descriptionText = "${device.displayName} fan mode is $value"
    sendEvent(name: "fanMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedAcFanModesValue(value) {
    String descriptionText = "${device.displayName} supported fan modes are $value"
    sendEvent(name: "supportedAcFanModes", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "fanOscillationMode"
def setFanOscillationModeValue(value) {
    String descriptionText = "${device.displayName} fan oscillation mode is $value"
    sendEvent(name: "fanOscillationMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedFanOscillationModesValue(value) {
    String descriptionText = "${device.displayName} supported fan oscillation modes are $value"
    sendEvent(name: "supportedFanOscillationModes", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "AirQuality". Note attribute is airQualityIndex in Hubitat
def setAirQualityValue(value) {
    String descriptionText = "${device.displayName} air quality is $value CAQI"
    sendEvent(name: "airQualityIndex", value: value, unit: "CAQI", descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "TemperatureMeasurement"
def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "ThermostatCoolingSetpoint"
def setCoolingSetpointValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} cooling set point is $value $unit"
    sendEvent(name: "coolingSetpoint", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "RelativeHumidityMeasurement"
def setHumidityValue(value) {
    String unit = "%rh"
    String descriptionText = "${device.displayName} humidity is $value $unit"
    sendEvent(name: "humidity", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "dustLevel"
def setDustLevelValue(value) {
    String unit = "µg/m3"
    String descriptionText = "${device.displayName} dust level is $value $unit"
    sendEvent(name: "dustLevel", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setFineDustLevelValue(value) {
    String unit = "µg/m3"
    String descriptionText = "${device.displayName} fine dust level is $value $unit"
    sendEvent(name: "fineDustLevel", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "veryFineDustSensor"
def setVeryFineDustLevelValue(value) {
    String unit = "µg/m3"
    String descriptionText = "${device.displayName} very fine dust level is $value $unit"
    sendEvent(name: "veryFineDustLevel", value: value, unit: unit, descriptionText: descriptionText)
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

//capability "demandResponseLoadControl"
def setDrlcStatusValue(value) {
    String descriptionText = "${device.displayName} demand response load control status is $value"
    sendEvent(name: "drlcStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.energyType" in SmartThings
def setEnergySavingLevelValue(value) {
    String descriptionText = "${device.displayName} energy saving level is $value"
    sendEvent(name: "energySavingLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "audioVolume"
def setVolumeValue(value) {
    String unit = "%"
    String descriptionText = "${device.displayName} volume is $value $unit"
    sendEvent(name: "volume", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "execute"
def setDataValue(value) {
    String descriptionText = "${device.displayName} execute command data is $value"
    executeDataParse(value)  
    sendEvent(name: "data", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.spiMode"
def setSpiModeValue(value) {
    String descriptionText = "${device.displayName} SPI Mode was turned $value"
    sendEvent(name: "spiMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSpiModeOff() {
    setSpiModeValue("off")
}

def setSpiModeOn() {
    setSpiModeValue("on")    
}

//capability "custom.thermostatSetpointControl"
def setMinimumSetpointValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} minimum set point is $value $unit"
    sendEvent(name: "minimumSetpoint", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMaximumSetpointValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} maximum set point is $value $unit"
    sendEvent(name: "maximumSetpoint", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.airConditionerOptionalMode"
def setAcOptionalModeValue(value) {
    String descriptionText = "${device.displayName} Air Conditioner optional mode is $value"
    sendEvent(name: "acOptionalMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSupportedAcOptionalModeValue(value) {
    String descriptionText = "${device.displayName} supported Air Conditioner optional modes are $value"
    sendEvent(name: "supportedAcOptionalMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.airConditionerTropicalNightMode"
def setAcTropicalNightModeLevelValue(value) {
    String descriptionText = "${device.displayName} Air Conditioner tropical night mode level is $value"
    sendEvent(name: "acTropicalNightModeLevel", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.autoCleaningMode"
def setAutoCleaningModeValue(value) {
    String descriptionText = "${device.displayName} auto cleaning mode is $value"
    sendEvent(name: "autoCleaningMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.dustFilter"
def setDustFilterUsageStepValue(value) {
    String descriptionText = "${device.displayName} dust Filter Usage Step is $value"
    sendEvent(name: "dustFilterUsageStep", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDustFilterUsageValue(value) {
    String descriptionText = "${device.displayName} dust Filter Usage is $value"
    sendEvent(name: "dustFilterUsage", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDustFilterLastResetDateValue(value) {
    String descriptionText = "${device.displayName} dust Filter Last Reset Date is $value"
    sendEvent(name: "dustFilterLastResetDate", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDustFilterStatusValue(value) {
    String descriptionText = "${device.displayName} dust Filter Status is $value"
    sendEvent(name: "dustFilterStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDustFilterCapacityValue(value) {
    String descriptionText = "${device.displayName} dust Filter Capacity is $value"
    sendEvent(name: "dustFilterCapacity", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDustFilterResetTypeValue(value) {
    String descriptionText = "${device.displayName} dust Filter Reset Type is $value"
    sendEvent(name: "dustFilterResetType", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAirConditionerOdorControllerProgressValue(value) {
    String descriptionText = "${device.displayName} air Conditioner Odor Controller Progress is $value"
    sendEvent(name: "airConditionerOdorControllerProgress", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAirConditionerOdorControllerStateValue(value) {
    String descriptionText = "${device.displayName} air Conditioner Odor Controller State is $value"
    sendEvent(name: "airConditionerOdorControllerState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability "custom.deodorFilter"
def setDeodorFilterUsageStepValue(value) {
    String descriptionText = "${device.displayName} deodor Filter Usage Step is $value"
    sendEvent(name: "deodorFilterUsageStep", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDeodorFilterUsageValue(value) {
    String descriptionText = "${device.displayName} deodor Filter Usage is $value"
    sendEvent(name: "deodorFilterUsage", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDeodorFilterLastResetDateValue(value) {
    String descriptionText = "${device.displayName} deodor Filter Last Reset Date is $value"
    sendEvent(name: "deodorFilterLastResetDate", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDeodorFilterStatusValue(value) {
    String descriptionText = "${device.displayName} deodor Filter Status is $value"
    sendEvent(name: "deodorFilterStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDeodorFilterCapacityValue(value) {
    String descriptionText = "${device.displayName} deodor Filter Capacity is $value"
    sendEvent(name: "deodorFilterCapacity", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setDeodorFilterResetTypeValue(value) {
    String descriptionText = "${device.displayName} deodor Filter Reset Type is $value"
    sendEvent(name: "deodorFilterResetType", value: value, descriptionText: descriptionText)
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
    return ([ "off":[] , "on":[], "setAirConditionerMode":[[name:"mode*",type:"STRING"]], "setFanMode":[[name:"fanMode*",type:"STRING"]], "setFanOscillationMode":[[name:"fanOscillationMode*",type:"ENUM"]], "overrideDrlcAction":[[name:"value*",type:"ENUM"]], "setCoolingSetpoint":[[name:"setpoint*",type:"NUMBER"]], 
              "volumeDown":[] , "volumeUp":[], "setVolume":[[name:"volume*",type:"NUMBER"]], "execute":[[name:"command*",type:"STRING"],[name:"args",type:"JSON_OBJECT",data:"args"]], "setSpiMode":[[name:"mode*",type:"ENUM"]], "raiseSetpoint":[] , "lowerSetpoint":[], 
              "setAcOptionalMode":[[name:"mode*",type:"ENUM"]], "setAcTropicalNightModeLevel":[[name:"hours*",type:"NUMBER"]], "setAutoCleaningMode":[[name:"mode*",type:"ENUM"]], "resetDustFilter":[], "setAirConditionerOdorControllerState":[[name:"state*",type:"ENUM"]], 
              "resetDeodorFilter":[], "requestDrlcAction":[[name:"drlcType*",type:"NUMBER"],[name:"drlcLevel*",type:"NUMBER",data:"drlcLevel"],[name:"start*",type:"STRING",data:"start"],[name:"duration*",type:"NUMBER",data:"duration"],[name:"reportingPeriod",type:"NUMBER",data:"reportingPeriod"]], 
              "setEnergySavingLevel":[[name:"energySavingLevel*",type:"NUMBER"]], "refresh":[]])
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

//capability "airConditionerMode"
def setAirConditionerMode(mode) {
    sendCommand("setAirConditionerMode", mode)    
}

//capability "airConditionerFanMode"
def setFanMode(fanMode) {
    sendCommand("setFanMode", fanMode)    
}

//capability "fanOscillationMode"
def setFanOscillationMode(fanOscillationMode) {
    sendCommand("setFanOscillationMode", fanOscillationMode)    
}

//capability "demandResponseLoadControl"
def overrideDrlcAction(value) {
    sendCommand("overrideDrlcAction", value.toBoolean())    
}

def requestDrlcAction(drlcType, drlcLevel, start, duration, reportingPeriod=null) {
    sendCommand("requestDrlcAction", drlcType, null, [drlcLevel:drlcLevel, start:start, duration:duration, reportingPeriod:reportingPeriod] )    
}

//capability "custom.energyType"
def setEnergySavingLevel(energySavingLevel) {
    sendCommand("setEnergySavingLevel", energySavingLevel)    
}

//capability "ThermostatCoolingSetpoint"
def setCoolingSetpoint(setpoint) {
    sendCommand("setCoolingSetpoint", setpoint, getTemperatureScale())    
}

//capability "audioVolume"
def volumeDown() { 
    sendCommand("volumeDown")
}

def volumeUp() {
    sendCommand("volumeUp")
}

def setVolume(volume) {
    sendCommand("setVolume", volume, "%")    
}

//https://community.smartthings.com/t/turn-off-the-beeping-sound-in-samsung-airconditioner-with-atribute-command-in-custom-app/203614
//https://github.com/DaveGut/HubitatActive/blob/master/SamsungAppliances/Samsung_HVAC.groovy
def liteOff() {
    execute("mode/vs/0",[ "x.com.samsung.da.options": [ "Light_On" ] ])
}

def liteOn() {
    execute("mode/vs/0",[ "x.com.samsung.da.options": [ "Light_Off" ] ])
}

def beepOff() {
    execute("mode/vs/0",[ "x.com.samsung.da.options": [ "Volume_Mute" ] ])
}

def beepOn() {
    execute("mode/vs/0",[ "x.com.samsung.da.options": [ "Volume_100" ] ])
}

def executeDataParse(jsonData) {    
    String lite = jsonData?.value?.payload["x.com.samsung.da.options"]?.contains("Light_On") ? "off" : "on"
    sendEvent(name: "lite", value: lite)
    String beep = jsonData?.value?.payload["x.com.samsung.da.options"]?.contains("Volume_Mute") ? "off" : "on"
    sendEvent(name: "beep", value: beep)    
}

//capability "execute"
def execute(command, args=null) {
    //sendCommand("execute", command, null, [args:args])
    sendCommand("execute", command, null, [args: args ? (args[0]=="{"?(new groovy.json.JsonSlurper().parseText(args)) : args) : [:]])
    runIn(5, refresh)
}

//capability "custom.spiMode"
def setSpiMode(mode) {
    sendCommand("setSpiMode", mode)    
}

//capability "custom.thermostatSetpointControl"
def raiseSetpoint() { 
    sendCommand("raiseSetpoint")
}

def lowerSetpoint() {
    sendCommand("lowerSetpoint")
}

//capability "custom.airConditionerOptionalMode"
def setAcOptionalMode(mode) {
    sendCommand("setAcOptionalMode", mode)    
}

//capability "custom.airConditionerTropicalNightMode"
def setAcTropicalNightModeLevel(hours) {
    sendCommand("setAcTropicalNightModeLevel", hours)    
}

//capability "custom.autoCleaningMode"
def setAutoCleaningMode(mode) {
    sendCommand("setAutoCleaningMode", mode)    
}

//capability "custom.dustFilter"
def resetDustFilter() { 
    sendCommand("resetDustFilter")
}

//capability "custom.airConditionerOdorController"
def setAirConditionerOdorControllerState(state) {
    sendCommand("setAirConditionerOdorControllerState", state)    
}

//capability "custom.deodorFilter"
def resetDeodorFilter() { 
    sendCommand("resetDeodorFilter")
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"name":"setAirConditionerMode","label":"command: setAirConditionerMode(mode*)","type":"command","parameters":[{"name":"mode*","type":"STRING"}]},"command":{"name":"setAirConditionerMode","arguments":[{"name":"mode","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],"type":"command","capability":"airConditionerMode","label":"command: setAirConditionerMode(mode*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"airConditionerMode","attribute":"airConditionerMode","label":"attribute: airConditionerMode.*"},"command":{"name":"setAirConditionerModeValue","label":"command: setAirConditionerModeValue(airConditionerMode*)","type":"command","parameters":[{"name":"airConditionerMode*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"type":"string"},"type":"array"}},"additionalProperties":false,"required":[],"capability":"airConditionerMode","attribute":"supportedAcModes","label":"attribute: supportedAcModes.*"},"command":{"name":"setSupportedAcModesValue","label":"command: setSupportedAcModesValue(supportedAcModes*)","type":"command","parameters":[{"name":"supportedAcModes*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setFanMode","label":"command: setFanMode(fanMode*)","type":"command","parameters":[{"name":"fanMode*","type":"STRING"}]},"command":{"name":"setFanMode","arguments":[{"name":"fanMode","optional":false,"schema":{"maxLength":255,"title":"String","type":"string"}}],"type":"command","capability":"airConditionerFanMode","label":"command: setFanMode(fanMode*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"type":"string"},"type":"array"}},"additionalProperties":false,"required":[],"capability":"airConditionerFanMode","attribute":"supportedAcFanModes","label":"attribute: supportedAcFanModes.*"},"command":{"name":"setSupportedAcFanModesValue","label":"command: setSupportedAcFanModesValue(supportedAcFanModes*)","type":"command","parameters":[{"name":"supportedAcFanModes*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"maxLength":255,"title":"String","type":"string"}},"additionalProperties":false,"required":[],"capability":"airConditionerFanMode","attribute":"fanMode","label":"attribute: fanMode.*"},"command":{"name":"setFanModeValue","label":"command: setFanModeValue(fanMode*)","type":"command","parameters":[{"name":"fanMode*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"name":"setFanOscillationMode","label":"command: setFanOscillationMode(fanOscillationMode*)","type":"command","parameters":[{"name":"fanOscillationMode*","type":"ENUM"}]},"command":{"name":"setFanOscillationMode","arguments":[{"name":"fanOscillationMode","optional":false,"schema":{"title":"FanOscillationMode","type":"string","enum":["off","individual","fixed","vertical","horizontal","all","indirect","direct","fixedCenter","fixedLeft","fixedRight","far","wide","mid","spot","swing"]}}],"type":"command","capability":"fanOscillationMode","label":"command: setFanOscillationMode(fanOscillationMode*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"FanOscillationMode","type":"string"}},"additionalProperties":false,"required":[],"capability":"fanOscillationMode","attribute":"fanOscillationMode","label":"attribute: fanOscillationMode.*"},"command":{"name":"setFanOscillationModeValue","label":"command: setFanOscillationModeValue(fanOscillationMode*)","type":"command","parameters":[{"name":"fanOscillationMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"title":"FanOscillationMode","type":"string","enum":["off","individual","fixed","vertical","horizontal","all","indirect","direct","fixedCenter","fixedLeft","fixedRight","far","wide","mid","spot","swing"]},"type":"array"}},"additionalProperties":false,"required":["value"],"capability":"fanOscillationMode","attribute":"supportedFanOscillationModes","label":"attribute: supportedFanOscillationModes.*"},"command":{"name":"setSupportedFanOscillationModesValue","label":"command: setSupportedFanOscillationModesValue(supportedFanOscillationModes*)","type":"command","parameters":[{"name":"supportedFanOscillationModes*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"setCoolingSetpoint","label":"command: setCoolingSetpoint(setpoint*)","type":"command","parameters":[{"name":"setpoint*","type":"NUMBER"}]},"command":{"name":"setCoolingSetpoint","arguments":[{"name":"setpoint","optional":false,"schema":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000}}],"type":"command","capability":"thermostatCoolingSetpoint","label":"command: setCoolingSetpoint(setpoint*)"},"type":"hubitatTrigger"},{"trigger":{"title":"Temperature","type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"thermostatCoolingSetpoint","attribute":"coolingSetpoint","label":"attribute: coolingSetpoint.*"},"command":{"name":"setCoolingSetpointValue","label":"command: setCoolingSetpointValue(coolingSetpoint*)","type":"command","parameters":[{"name":"coolingSetpoint*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["CAQI"],"default":"CAQI"}},"additionalProperties":false,"required":["value"],"capability":"airQualitySensor","attribute":"airQuality","label":"attribute: airQuality.*"},"command":{"name":"setAirQualityValue","label":"command: setAirQualityValue(airQuality*)","type":"command","parameters":[{"name":"airQuality*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"title":"Percent","type":"attribute","properties":{"value":{"type":"number","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"relativeHumidityMeasurement","attribute":"humidity","label":"attribute: humidity.*"},"command":{"name":"setHumidityValue","label":"command: setHumidityValue(humidity*)","type":"command","parameters":[{"name":"humidity*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0},"unit":{"type":"string","enum":["\u03bcg/m^3"],"default":"\u03bcg/m^3"}},"additionalProperties":false,"required":["value"],"capability":"dustSensor","attribute":"dustLevel","label":"attribute: dustLevel.*"},"command":{"name":"setDustLevelValue","label":"command: setDustLevelValue(dustLevel*)","type":"command","parameters":[{"name":"dustLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0},"unit":{"type":"string","enum":["\u03bcg/m^3"],"default":"\u03bcg/m^3"}},"additionalProperties":false,"required":["value"],"capability":"dustSensor","attribute":"fineDustLevel","label":"attribute: fineDustLevel.*"},"command":{"name":"setFineDustLevelValue","label":"command: setFineDustLevelValue(fineDustLevel*)","type":"command","parameters":[{"name":"fineDustLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"PositiveInteger","type":"integer","minimum":0},"unit":{"type":"string","enum":["\u03bcg/m^3"],"default":"\u03bcg/m^3"}},"additionalProperties":false,"required":["value"],"capability":"veryFineDustSensor","attribute":"veryFineDustLevel","label":"attribute: veryFineDustLevel.*"},"command":{"name":"setVeryFineDustLevelValue","label":"command: setVeryFineDustLevelValue(veryFineDustLevel*)","type":"command","parameters":[{"name":"veryFineDustLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"remoteControlStatus","attribute":"remoteControlEnabled","label":"attribute: remoteControlEnabled.*"},"command":{"name":"setRemoteControlEnabledValue","label":"command: setRemoteControlEnabledValue(remoteControlEnabled*)","type":"command","parameters":[{"name":"remoteControlEnabled*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"properties":{"deltaEnergy":{"type":"number"},"end":{"pattern":"removed","title":"Iso8601Date","type":"string"},"start":{"pattern":"removed","title":"Iso8601Date","type":"string"},"energySaved":{"type":"number"},"persistedSavedEnergy":{"type":"number"},"energy":{"type":"number"},"power":{"type":"number"},"powerEnergy":{"type":"number"},"persistedEnergy":{"type":"number"}},"additionalProperties":false,"title":"PowerConsumption","type":"object"}},"additionalProperties":false,"required":["value"],"capability":"powerConsumptionReport","attribute":"powerConsumption","label":"attribute: powerConsumption.*"},"command":{"name":"setPowerConsumptionValue","label":"command: setPowerConsumptionValue(powerConsumption*)","type":"command","parameters":[{"name":"powerConsumption*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setVolume","label":"command: setVolume(volume*)","type":"command","parameters":[{"name":"volume*","type":"NUMBER"}]},"command":{"name":"setVolume","arguments":[{"name":"volume","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioVolume","label":"command: setVolume(volume*)"},"type":"hubitatTrigger"},{"trigger":{"name":"volumeDown","label":"command: volumeDown()","type":"command"},"command":{"name":"volumeDown","type":"command","capability":"audioVolume","label":"command: volumeDown()"},"type":"hubitatTrigger"},{"trigger":{"name":"volumeUp","label":"command: volumeUp()","type":"command"},"command":{"name":"volumeUp","type":"command","capability":"audioVolume","label":"command: volumeUp()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.spiMode","attribute":"spiMode","label":"attribute: spiMode.*"},"command":{"name":"setSpiModeValue","label":"command: setSpiModeValue(spiMode*)","type":"command","parameters":[{"name":"spiMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"raiseSetpoint","label":"command: raiseSetpoint()","type":"command"},"command":{"name":"raiseSetpoint","type":"command","capability":"custom.thermostatSetpointControl","label":"command: raiseSetpoint()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"number"},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value"],"capability":"custom.thermostatSetpointControl","attribute":"maximumSetpoint","label":"attribute: maximumSetpoint.*"},"command":{"name":"setMaximumSetpointValue","label":"command: setMaximumSetpointValue(maximumSetpoint*)","type":"command","parameters":[{"name":"maximumSetpoint*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"number"},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value"],"capability":"custom.thermostatSetpointControl","attribute":"minimumSetpoint","label":"attribute: minimumSetpoint.*"},"command":{"name":"setMinimumSetpointValue","label":"command: setMinimumSetpointValue(minimumSetpoint*)","type":"command","parameters":[{"name":"minimumSetpoint*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"setAcOptionalMode","label":"command: setAcOptionalMode(mode*)","type":"command","parameters":[{"name":"mode*","type":"ENUM"}]},"command":{"name":"setAcOptionalMode","arguments":[{"name":"mode","optional":false,"schema":{"type":"string","enum":["off","energySaving","windFree","sleep","windFreeSleep","speed","smart","quiet","twoStep","comfort","dlightCool","cubePurify","longWind","motionIndirect","motionDirect"]}}],"type":"command","capability":"custom.airConditionerOptionalMode","label":"command: setAcOptionalMode(mode*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.airConditionerOptionalMode","attribute":"acOptionalMode","label":"attribute: acOptionalMode.*"},"command":{"name":"setAcOptionalModeValue","label":"command: setAcOptionalModeValue(acOptionalMode*)","type":"command","parameters":[{"name":"acOptionalMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"type":"string","enum":["off","energySaving","windFree","sleep","windFreeSleep","speed","smart","quiet","twoStep","comfort","dlightCool","cubePurify","longWind","motionIndirect","motionDirect"]}}},"additionalProperties":false,"required":["value"],"capability":"custom.airConditionerOptionalMode","attribute":"supportedAcOptionalMode","label":"attribute: supportedAcOptionalMode.*"},"command":{"name":"setSupportedAcOptionalModeValue","label":"command: setSupportedAcOptionalModeValue(supportedAcOptionalMode*)","type":"command","parameters":[{"name":"supportedAcOptionalMode*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"setAcTropicalNightModeLevel","label":"command: setAcTropicalNightModeLevel(hours*)","type":"command","parameters":[{"name":"hours*","type":"NUMBER"}]},"command":{"name":"setAcTropicalNightModeLevel","arguments":[{"name":"hours","optional":false,"schema":{"minimum":0,"type":"integer","maximum":35}}],"type":"command","capability":"custom.airConditionerTropicalNightMode","label":"command: setAcTropicalNightModeLevel(hours*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"minimum":0,"type":"integer","maximum":35}},"additionalProperties":false,"required":["value"],"capability":"custom.airConditionerTropicalNightMode","attribute":"acTropicalNightModeLevel","label":"attribute: acTropicalNightModeLevel.*"},"command":{"name":"setAcTropicalNightModeLevelValue","label":"command: setAcTropicalNightModeLevelValue(acTropicalNightModeLevel*)","type":"command","parameters":[{"name":"acTropicalNightModeLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"setAutoCleaningMode","label":"command: setAutoCleaningMode(mode*)","type":"command","parameters":[{"name":"mode*","type":"ENUM"}]},"command":{"name":"setAutoCleaningMode","arguments":[{"name":"mode","optional":false,"schema":{"type":"string","enum":["on","speedClean","quietClean","off"]}}],"type":"command","capability":"custom.autoCleaningMode","label":"command: setAutoCleaningMode(mode*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.autoCleaningMode","attribute":"autoCleaningMode","label":"attribute: autoCleaningMode.*"},"command":{"name":"setAutoCleaningModeValue","label":"command: setAutoCleaningModeValue(autoCleaningMode*)","type":"command","parameters":[{"name":"autoCleaningMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"resetDustFilter","label":"command: resetDustFilter()","type":"command"},"command":{"name":"resetDustFilter","type":"command","capability":"custom.dustFilter","label":"command: resetDustFilter()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer"},"unit":{"type":"string","enum":["CC","Cycle","Gallon","Hour","Month"]}},"additionalProperties":false,"required":["value","unit"],"capability":"custom.dustFilter","attribute":"dustFilterCapacity","label":"attribute: dustFilterCapacity.*"},"command":{"name":"setDustFilterCapacityValue","label":"command: setDustFilterCapacityValue(dustFilterCapacity*)","type":"command","parameters":[{"name":"dustFilterCapacity*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"pattern":"removed","type":"string"}},"additionalProperties":false,"required":[],"capability":"custom.dustFilter","attribute":"dustFilterLastResetDate","label":"attribute: dustFilterLastResetDate.*"},"command":{"name":"setDustFilterLastResetDateValue","label":"command: setDustFilterLastResetDateValue(dustFilterLastResetDate*)","type":"command","parameters":[{"name":"dustFilterLastResetDate*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.dustFilter","attribute":"dustFilterStatus","label":"attribute: dustFilterStatus.*"},"command":{"name":"setDustFilterStatusValue","label":"command: setDustFilterStatusValue(dustFilterStatus*)","type":"command","parameters":[{"name":"dustFilterStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"min":1,"type":"integer","max":100}},"additionalProperties":false,"required":["value"],"capability":"custom.dustFilter","attribute":"dustFilterUsageStep","label":"attribute: dustFilterUsageStep.*"},"command":{"name":"setDustFilterUsageStepValue","label":"command: setDustFilterUsageStepValue(dustFilterUsageStep*)","type":"command","parameters":[{"name":"dustFilterUsageStep*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"min":0,"type":"integer","max":100}},"additionalProperties":false,"required":["value"],"capability":"custom.dustFilter","attribute":"dustFilterUsage","label":"attribute: dustFilterUsage.*"},"command":{"name":"setDustFilterUsageValue","label":"command: setDustFilterUsageValue(dustFilterUsage*)","type":"command","parameters":[{"name":"dustFilterUsage*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"items":{"type":"string","enum":["replaceable","washable"]},"type":"array"}},"additionalProperties":false,"required":["value"],"capability":"custom.dustFilter","attribute":"dustFilterResetType","label":"attribute: dustFilterResetType.*"},"command":{"name":"setDustFilterResetTypeValue","label":"command: setDustFilterResetTypeValue(dustFilterResetType*)","type":"command","parameters":[{"name":"dustFilterResetType*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"setAirConditionerOdorControllerState","label":"command: setAirConditionerOdorControllerState(state*)","type":"command","parameters":[{"name":"state*","type":"ENUM"}]},"command":{"name":"setAirConditionerOdorControllerState","arguments":[{"name":"state","optional":false,"schema":{"type":"string","enum":["on","off"]}}],"type":"command","capability":"custom.airConditionerOdorController","label":"command: setAirConditionerOdorControllerState(state*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer","max":100,"min":0}},"additionalProperties":false,"required":["value"],"capability":"custom.airConditionerOdorController","attribute":"airConditionerOdorControllerProgress","label":"attribute: airConditionerOdorControllerProgress.*"},"command":{"name":"setAirConditionerOdorControllerProgressValue","label":"command: setAirConditionerOdorControllerProgressValue(airConditionerOdorControllerProgress*)","type":"command","parameters":[{"name":"airConditionerOdorControllerProgress*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.airConditionerOdorController","attribute":"airConditionerOdorControllerState","label":"attribute: airConditionerOdorControllerState.*"},"command":{"name":"setAirConditionerOdorControllerStateValue","label":"command: setAirConditionerOdorControllerStateValue(airConditionerOdorControllerState*)","type":"command","parameters":[{"name":"airConditionerOdorControllerState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"resetDeodorFilter","label":"command: resetDeodorFilter()","type":"command"},"command":{"name":"resetDeodorFilter","type":"command","capability":"custom.deodorFilter","label":"command: resetDeodorFilter()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"pattern":"removed","type":"string"}},"additionalProperties":false,"required":[],"capability":"custom.deodorFilter","attribute":"deodorFilterLastResetDate","label":"attribute: deodorFilterLastResetDate.*"},"command":{"name":"setDeodorFilterLastResetDateValue","label":"command: setDeodorFilterLastResetDateValue(deodorFilterLastResetDate*)","type":"command","parameters":[{"name":"deodorFilterLastResetDate*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"custom.deodorFilter","attribute":"deodorFilterStatus","label":"attribute: deodorFilterStatus.*"},"command":{"name":"setDeodorFilterStatusValue","label":"command: setDeodorFilterStatusValue(deodorFilterStatus*)","type":"command","parameters":[{"name":"deodorFilterStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"min":0,"type":"integer","max":100}},"additionalProperties":false,"required":["value"],"capability":"custom.deodorFilter","attribute":"deodorFilterUsage","label":"attribute: deodorFilterUsage.*"},"command":{"name":"setDeodorFilterUsageValue","label":"command: setDeodorFilterUsageValue(deodorFilterUsage*)","type":"command","parameters":[{"name":"deodorFilterUsage*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"modelName","label":"attribute: modelName.*"},"command":{"name":"setModelNameValue","label":"command: setModelNameValue(modelName*)","type":"command","parameters":[{"name":"modelName*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"serialNumber","label":"attribute: serialNumber.*"},"command":{"name":"setSerialNumberValue","label":"command: setSerialNumberValue(serialNumber*)","type":"command","parameters":[{"name":"serialNumber*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"serialNumberExtra","label":"attribute: serialNumberExtra.*"},"command":{"name":"setSerialNumberExtraValue","label":"command: setSerialNumberExtraValue(serialNumberExtra*)","type":"command","parameters":[{"name":"serialNumberExtra*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","pattern":"removed"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"modelClassificationCode","label":"attribute: modelClassificationCode.*"},"command":{"name":"setModelClassificationCodeValue","label":"command: setModelClassificationCodeValue(modelClassificationCode*)","type":"command","parameters":[{"name":"modelClassificationCode*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"samsungce.deviceIdentification","attribute":"description","label":"attribute: description.*"},"command":{"name":"setDescriptionValue","label":"command: setDescriptionValue(description*)","type":"command","parameters":[{"name":"description*","type":"STRING"}]},"type":"smartTrigger","mute":true,"disableStatus":true},{"trigger":{"name":"lowerSetpoint","label":"command: lowerSetpoint()","type":"command"},"command":{"name":"lowerSetpoint","type":"command","capability":"custom.thermostatSetpointControl","label":"command: lowerSetpoint()"},"type":"hubitatTrigger"},{"trigger":{"name":"execute","label":"command: execute(command*, args)","type":"command","parameters":[{"name":"command*","type":"STRING"},{"name":"args","type":"JSON_OBJECT","data":"args"}]},"command":{"name":"execute","arguments":[{"name":"command","optional":false,"schema":{"title":"String","type":"string","maxLength":255}},{"name":"args","optional":true,"schema":{"title":"JsonObject","type":"object"}}],"type":"command","capability":"execute","label":"command: execute(command*, args)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"JsonObject","type":"object"},"data":{"type":"object","additionalProperties":true,"required":[]}},"additionalProperties":false,"required":["value"],"capability":"execute","attribute":"data","label":"attribute: data.*"},"command":{"name":"setDataValue","label":"command: setDataValue(data*)","type":"command","parameters":[{"name":"data*","type":"JSON_OBJECT"}]},"type":"smartTrigger","disableStatus":false},{"trigger":{"name":"setEnergySavingLevel","label":"command: setEnergySavingLevel(energySavingLevel*)","type":"command","parameters":[{"name":"energySavingLevel*","type":"NUMBER"}]},"command":{"name":"setEnergySavingLevel","arguments":[{"name":"energySavingLevel","optional":false,"schema":{"type":"integer"}}],"type":"command","capability":"custom.energyType","label":"command: setEnergySavingLevel(energySavingLevel*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer"}},"additionalProperties":false,"required":["value"],"capability":"custom.energyType","attribute":"energySavingLevel","label":"attribute: energySavingLevel.*"},"command":{"name":"setEnergySavingLevelValue","label":"command: setEnergySavingLevelValue(energySavingLevel*)","type":"command","parameters":[{"name":"energySavingLevel*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.on","value":"on","dataType":"ENUM"},"command":{"name":"setSwitchOn","label":"command: setSwitchOn()","type":"command"},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.off","value":"off","dataType":"ENUM"},"command":{"name":"setSwitchOff","label":"command: setSwitchOff()","type":"command"},"type":"smartTrigger"},{"trigger":{"name":"requestDrlcAction","label":"command: requestDrlcAction(drlcType*, drlcLevel*, start*, duration*, reportingPeriod)","type":"command","parameters":[{"name":"drlcType*","type":"NUMBER"},{"name":"drlcLevel*","type":"NUMBER","data":"drlcLevel"},{"name":"start*","type":"STRING","data":"start"},{"name":"duration*","type":"NUMBER","data":"duration"},{"name":"reportingPeriod","type":"NUMBER","data":"reportingPeriod"}]},"command":{"name":"requestDrlcAction","arguments":[{"name":"drlcType","optional":false,"schema":{"title":"DrlcType","type":"integer","minimum":0,"maximum":1}},{"name":"drlcLevel","optional":false,"schema":{"title":"DrlcLevel","type":"integer","minimum":-1,"maximum":4}},{"name":"start","optional":false,"schema":{"title":"Iso8601Date","type":"string"}},{"name":"duration","optional":false,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}},{"name":"reportingPeriod","optional":true,"schema":{"title":"PositiveInteger","type":"integer","minimum":0}}],"type":"command","capability":"demandResponseLoadControl","label":"command: requestDrlcAction(drlcType*, drlcLevel*, start*, duration*, reportingPeriod)"},"type":"hubitatTrigger"},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
