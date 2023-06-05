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
    definition(name: "Replica Development Base", namespace: "replica", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/dev/replicaDevelopmentBase.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {   
    }
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
}

def configure() {
    log.info "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ "replicaEvent":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]], "replicaStatus":[[name:"parent*",type:"OBJECT"],[name:"event*",type:"JSON_OBJECT"]], "replicaHealth":[[name:"parent*",type:"OBJECT"],[name:"health*",type:"JSON_OBJECT"]], 
              "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

void replicaEvent(def parent=null, Map event=null) {
    log.info "replicaEvent"
    if(parent) log.info parent?.getLabel()
    if(event) log.info event   
}

void replicaStatus(def parent=null, Map status=null) {
    log.info "replicaStatus"
    if(parent) log.info parent?.getLabel()
    if(status) log.info status   
}

void replicaHealth(def parent=null, Map health=null) {
    log.info "replicaHealth"
    if(parent) log.info parent?.getLabel()
    if(health) log.info health   
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true}]}"""
}
