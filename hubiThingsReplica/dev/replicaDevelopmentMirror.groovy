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
metadata 
{
    definition(name: "Replica Development Mirror", namespace: "mirror", author: "bloodtick", importUrl:"https://raw.githubusercontent.com/bloodtick/Hubitat/main/hubiThingsReplica/dev/replicaDevelopmentMirror.groovy")
    {
        capability "Actuator"
        
        command "replicaEvent"
        command "replicaStatus"
        command "replicaHealth"
        command "setHealthStatusValue", [[name: "healthStatus*", type: "ENUM", description: "Set device health", constraints: ["offline", "online"]]]
        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {   
    }
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

