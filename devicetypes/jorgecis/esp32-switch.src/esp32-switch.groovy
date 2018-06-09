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
        capability "Actuator"
        capability "Switch"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"

        attribute "currentIP", "string"

        command "subscribe"
        command "resubscribe"
        command "unsubscribe"
        command "setOffline"
 }

 // simulator metadata
 simulator {}

 // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"rich-control", type: "switch", canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ff0000"
 			}
            tileAttribute ("currentIP", key: "SECONDARY_CONTROL") {
             	 attributeState "currentIP", label: ''
 			}
        }

        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
            state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
            state "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#79b821", nextState:"turningOff"
            state "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
            state "offline", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ff0000"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["rich-control", "refresh"])
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

private getCallBackAddress() {
 	device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
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

def on() {
log.debug "Executing 'on'"
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/on",
        headers: [
            HOST: getHostAddress()
        ]
    )
    return result
}

def off() {
log.debug "Executing 'off'"
    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/off",
        headers: [
            HOST: getHostAddress()
        ]
    )
    log.debug result
    return result
}

private subscribeAction(path, callbackPath="") {
    log.trace "subscribe($path, $callbackPath)"
    def address = getCallBackAddress()
    def ip = getHostAddress()

    def result = new physicalgraph.device.HubAction(
        method: "SUBSCRIBE",
        path: path,
        headers: [
            HOST: ip,
            CALLBACK: "<http://${address}/notify$callbackPath>",
            NT: "upnp:event",
            TIMEOUT: "Second-28800"
        ]
    )

    log.trace "SUBSCRIBE $path"

    return result
}

private unsubscribeAction(path, callbackPath="") {
    log.trace "unsubscribe($path, $callbackPath)"
    def address = getCallBackAddress()
    def ip = getHostAddress()

    def result = new physicalgraph.device.HubAction(
        method: "UNSUBSCRIBE",
        path: path,
        headers: [
            HOST: ip,
            CALLBACK: "<http://${address}/notify$callbackPath>",
            NT: "upnp:event",
            TIMEOUT: "Second-28800"
        ]
    )

    log.trace "SUBSCRIBE $path"

    return result
}

def subscribe() {
	subscribeAction("/on")
	subscribeAction("/off")
	subscribeAction("/poll")
}

def refresh() {
 	log.debug "Executing ESP8266 Switch 'subscribe', then 'poll'"
 	[subscribe(), poll()]
}

def subscribe(ip, port) {
    def existingIp = getDataValue("ip")
    def existingPort = getDataValue("port")
    if (ip && ip != existingIp) {
         log.debug "Updating ip from $existingIp to $ip"    
    	 updateDataValue("ip", ip)
    	 def ipvalue = convertHexToIP(getDataValue("ip"))
         sendEvent(name: "currentIP", value: ipvalue, descriptionText: "IP changed to ${ipvalue}")
    }
 	if (port && port != existingPort) {
 		log.debug "Updating port from $existingPort to $port"
 		updateDataValue("port", port)
	}
    
 	log.debug "Subscribe ${ip}:${port}"
	subscribe("${ip}:${port}")
}

def resubscribe() {
    log.debug "Executing 'resubscribe()'"
    subscribe()
}


def unsubscribe() {
	unsubscribeAction("/on")
	unsubscribeAction("/off")
	unsubscribeAction("/poll")
}


//TODO: Use UTC Timezone


def setOffline() {
	//sendEvent(name: "currentIP", value: "Offline", displayed: false)
    sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
}

def poll() {
log.debug "Executing 'poll'"
if (device.currentValue("currentIP") != "Offline")
    runIn(30, setOffline)

    def result = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/poll",
        headers: [
            HOST: getHostAddress()
        ]
    )
    
}