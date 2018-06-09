/**
 *  Copyright 2018 Jorge Cisneros
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Author: Jorge Cisneros
 *  Date: 2018-06-08
 */
definition(
    name: "Doortsy App Manager",
    namespace: "jorgecis",
    author: "Jorge Cisneros",
    description: "Manager to discovery and add the Doortsy",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/remotsy/logo256.png",
    iconX2Url: "https://s3.amazonaws.com/remotsy/logo512.png",
    singleInstance: true
)

preferences {
	page(name:"firstPage", title:"ESP8266 Device Setup", content:"firstPage")
}

private discoverAllespTypes()
{
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery", physicalgraph.device.Protocol.LAN))
}

private getFriendlyName(String deviceNetworkId) {
	sendHubCommand(new physicalgraph.device.HubAction("""GET /esp8266.xml HTTP/1.1
HOST: ${deviceNetworkId}

""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

private getXML(String deviceNetworkId) {
	sendHubCommand(new physicalgraph.device.HubAction("""GET /esp8266.xml HTTP/1.1
HOST: ${deviceNetworkId}

""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

private verifyDevices() {
	def switches = getespSwitches().findAll { it?.value?.verified != true }
	def devices = switches
	devices.each {
		getFriendlyName((it.value.ip + ":" + it.value.port))
	}
}

def firstPage()
{
	if(canInstallLabs())
	{
		int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
		state.refreshCount = refreshCount + 1
		def refreshInterval = 5

		log.debug "REFRESH COUNT :: ${refreshCount}"

		subscribe(location, null, locationHandler, [filterEvents:false])

		//ssdp request every 25 seconds
		//if((refreshCount % 5) == 0) {
			discoverAllespTypes()
		//}

		//setup.xml request every 5 seconds except on discoveries
		if(((refreshCount % 1) == 0) && ((refreshCount % 5) != 0)) {
			verifyDevices()
		}

		def switchesDiscovered = switchesDiscovered()

		return dynamicPage(name:"firstPage", title:"Discovery Started!", nextPage:"", refreshInterval: refreshInterval, install:true, uninstall: true) {
			section("Select a device...") {
				input "selectedSwitches", "enum", required:false, title:"Select ESP8266 Device \n(${switchesDiscovered.size() ?: 0} found)", multiple:true, options:switchesDiscovered
			}
		}
	}
	else
	{
		def upgradeNeeded = """To use SmartThings Labs, your Hub should be completely up to date.

To update your Hub, access Location Settings in the Main Menu (tap the gear next to your location name), select your Hub, and choose "Update Hub"."""

		return dynamicPage(name:"firstPage", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
			section("Upgrade") {
				paragraph "$upgradeNeeded"
			}
		}
	}
}

def devicesDiscovered() {
	def switches = getespSwitches()
	def devices = switches
	def list = []

	list = devices?.collect{ [app.id, it.ssdpUSN].join('.') }
}

def switchesDiscovered() {
	def switches = getespSwitches().findAll { it?.value?.verified == true }
	def map = [:]
	switches.each {
		def value = it.value.name ?: "ESP8622 Switch ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map["${key}"] = value
	}
	map
}

def getespSwitches()
{
	if (!state.switches) { state.switches = [:] }
	state.switches
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	initialize()
}

def initialize() {
	unsubscribe()
    unschedule()
	subscribe(location, null, locationHandler, [filterEvents:false])

	if (selectedSwitches)
		addSwitches()

	runIn(5, "subscribeToDevices") //initial subscriptions delayed by 5 seconds
	runIn(10, "refreshDevices") //refresh devices, delayed by 10 seconds
    runEvery5Minutes("refresh")
}

def resubscribe() {
	log.debug "Resubscribe called, delegating to refresh()"
	refresh()
}

def refresh() {
	log.debug "refresh() called"
    doDeviceSync()
	refreshDevices()
}

def refreshDevices() {
	log.debug "refreshDevices() called"
	def devices = getAllChildDevices()
	devices.each { d ->
		log.debug "Calling refresh() on device: ${d.id}"
		d.refresh()
	}
}

def subscribeToDevices() {
	log.debug "subscribeToDevices() called"
	def devices = getAllChildDevices()
	devices.each { d ->
		d.subscribe()
	}
}

def addSwitches() {
	def switches = getespSwitches()

	selectedSwitches.each { dni ->
		def selectedSwitch = switches.find { it.value.mac == dni } ?: switches.find { "${it.value.ip}:${it.value.port}" == dni }
		def d
		if (selectedSwitch) {
			d = getChildDevices()?.find {
				it.dni == selectedSwitch.value.mac || it.device.getDataValue("mac") == selectedSwitch.value.mac
			}
		}

		if (!d) {
        
			log.debug "Creating ESP..."
			log.debug "Creating esp Switch with name: ${selectedSwitch.value.name}"
			log.debug "Creating esp Switch with dni: ${selectedSwitch.value.mac}"
            
			d = addChildDevice("schreiverj", "ESP8266 Switch", selectedSwitch.value.mac, selectedSwitch?.value.hub, [
					"label": selectedSwitch?.value?.name ?: "ESP8266 Switch",
					"data": [
							"mac": selectedSwitch.value.mac,
							"ip": selectedSwitch.value.ip,
							"port": selectedSwitch.value.port
					]
			])
      def ipvalue = convertHexToIP(selectedSwitch.value.ip)
			d.sendEvent(name: "currentIP", value: ipvalue, descriptionText: "IP is ${ipvalue}")
			log.debug "Created ${d.displayName} with id: ${d.id}, dni: ${d.deviceNetworkId}"
		} else {
			log.debug "found ${d.displayName} with id $dni already exists"
		}
	}
}


def locationHandler(evt) {
	def description = evt.description
	def hub = evt?.hubId
	def parsedEvent = parseDiscoveryMessage(description)
	parsedEvent << ["hub":hub]
    log.debug description
    
     if (parsedEvent.headers && parsedEvent.body) {
		String headerString = new String(parsedEvent.headers.decodeBase64())?.toLowerCase() 	        	
		log.debug headerString      	
		if (headerString != null && (headerString.contains('text/xml') || headerString.contains('application/xml'))) {
			def body = parseXmlBody(parsedEvent.body)
            log.debug parsedEvent.body
			
            def switches = getespSwitches()
            def espSwitch = switches.find {it?.key?.contains(body?.device?.UDN?.text())}
            if (espSwitch)
            {
                espSwitch.value << [name:body?.device?.friendlyName?.text(), verified: true]
            }
            else
            {
                log.error "/esp8266.xml returned a esp8266 device that didn't exist"
            }

		}
	}
	else if (parsedEvent?.ssdpPath?.contains("esp8266")) {
        log.debug parsedEvent.ssdpUSN.toString()
		def switches = getespSwitches()
		if (!(switches."${parsedEvent.ssdpUSN.toString()}")) {
        	//if it doesn't already exist
			switches << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		} else {
			log.debug "Device was already found in state..."
			def d = switches."${parsedEvent.ssdpUSN.toString()}"
			boolean deviceChangedValues = false
			log.debug "$d.ip <==> $parsedEvent.ip"
			if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
				d.ip = parsedEvent.ip
				d.port = parsedEvent.port
				deviceChangedValues = true
				log.debug "Device's port or ip changed..."
                def child = getChildDevice(parsedEvent.mac)
				child.subscribe(parsedEvent.ip, parsedEvent.port)
                child.poll()
			}
		}
	}
	
}

private def parseXmlBody(def body) {
	def decodedBytes = body.decodeBase64()
	def bodyString
	try {
		bodyString = new String(decodedBytes)
	} catch (Exception e) {
		// Keep this log for debugging StringIndexOutOfBoundsException issue
		log.error("Exception decoding bytes in sonos connect: ${decodedBytes}")
		throw e
	}
	return new XmlSlurper().parseText(bodyString)
}

private def parseDiscoveryMessage(String description) {
	def device = [:]
	def parts = description.split(',')
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			device.devicetype = valueString
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.mac = valueString
			}
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.ip = valueString
			}
		}
		else if (part.startsWith('deviceAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.port = valueString
			}
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				device.ssdpPath = valueString
			}
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				device.ssdpUSN = valueString
			}
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				device.ssdpTerm = valueString
			}
		}
		else if (part.startsWith('headers')) {
			part -= "headers:"
			def valueString = part.trim()
			if (valueString) {
				device.headers = valueString
			}
		}
		else if (part.startsWith('body')) {
			part -= "body:"
			def valueString = part.trim()
			if (valueString) {
				device.body = valueString
			}
		}
	}
	device
}

def doDeviceSync(){
	log.debug "Doing Device Sync!"
	discoverAllespTypes()
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private Boolean canInstallLabs() {
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware) {
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions() {
	return location.hubs*.firmwareVersionString.findAll { it }
}