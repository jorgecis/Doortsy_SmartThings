/**
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
 * Doortsy Switch
 *
 * Author: Jorge Cisneros 
 * Date: 2018-06-08
 */
 metadata {
 	definition (name: "ESP32 Switch", namespace: "jorgecis", author: "Jorge Cisneros") {
        capability "Door Control"
        capability "Refresh"
        capability "switch" 
        
        command "push1"
        command "setOffline"
    }

    // UI tile definitions
    tiles {
       standardTile("contact", "", width: 3, height: 2,  decoration: "ring") {
			state "default", icon: "st.doors.garage.garage-closed", action: "push1", label: "toggle"
		}
        valueTile("ipaddress", "device.floatAsText", width: 3, height: 1) {
        	state "inst", label: ""
        }

		main "contact"
		details(["contact", "ipaddress"])
    }
}



// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"

    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (headerString?.contains("SID: uuid:")) {
        def sid = (headerString =~ /SID: uuid:.*/) ? ( headerString =~ /SID: uuid:.*/)[0] : "0"
        sid -= "SID: uuid:".trim()

        updateDataValue("subscriptionId", sid)
 	}

    def result = []
    def bodyString = msg.body
    log.debug msg
    
    if (bodyString) {
    	unschedule("setOffline")
        def body = new XmlSlurper().parseText(bodyString)
        
        def value = body.text() == "off" ? "off" : "on"
        log.trace "Notify: BinaryState = ${value}, ${body.property.BinaryState}"
        def dispaux = device.currentValue("switch") != value
        result << createEvent(name: "switch", value: value, descriptionText: "Switch is ${value}", displayed: dispaux)
        
 	}
 result
}

def off() {
	log.debug "Power OFF Event"
    push1()
}

def on() {
	log.debug "Power ON Event"
    push1()
}


private Integer convertHexToInt(hex) {
 	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
 	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
 	def ip = getDataValue("ip")
 	def port = getDataValue("port")
 	if (!ip || !port) {
 		def parts = device.deviceNetworkId.split(":")
 		if (parts.length == 2) {
 			ip = parts[0]
 			port = parts[1]
 		} else {
 			log.warn "Can't figure out ip and port for device: ${device.id}"
		 }
 	}
 	log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    if (ip?.contains("."))
    {
 	return ip + ":" + port
    }
    else
    {
 	return convertHexToIP(ip) + ":" + convertHexToInt(port)
    }
}


def refresh() {
}

def setOffline() {
	//sendEvent(name: "currentIP", value: "Offline", displayed: false)
    sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
}



def sync(ip, port) {
    log.debug(ip)
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

def push1() {
    log.debug "Button pressed"
    def httpRequest = [
          	method:		"GET",
            path: 		"/toogle",
            headers:	[HOST: getHostAddress()]
    ]
  	def hubAction = new physicalgraph.device.HubAction(httpRequest)
   	sendHubCommand(hubAction)
}