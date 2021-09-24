/*
 * importUrl: "https://raw.githubusercontent.com/csteele-PD/Hubitat-master/master/EtherRain-8-Valves.groovy")
 *
 *	Copyright 2019 C Steele
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *	use this file except in compliance with the License. You may obtain a copy
 *	of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *	License for the specific language governing permissions and limitations
 *	under the License.
 *
 *
 */
 
 
public static String version()      {  return "v1.0.0"  }

/***********************************************************************************************************************
 * Version: 1.0.0
 *
 */

metadata 
{
	definition(name: "EtherRain 8 Valves", namespace: "csteele", author: "C Steele", importUrl: "https://raw.githubusercontent.com/csteele-PD/Hubitat-master/master/EtherRain-8-Valves.groovy")
	{
		capability "Actuator"
		capability "Refresh"

//		command "deleteChildren"			// **---** delete for Release
		command "recreateChildDevices"
	}

      preferences 
      {
		section ("EtherRain Credentials") {
			input("username", "text", title: "<b>Username</b>", description: "<i>Your EtherRain User Name:</i>", required: true)
			input("password", "password", title: "<b>Password</b>", description: "<i>Your EtherRain password:</i>",required: true)
			input("etherrainip", "text", title: "<b>Device IP</b>", description: "<i>Your EtherRain IP:</i>", required: true)
		}
		section ("EtherRain Timers") {
			input("igateDelay", "text", title: "<b>Irrigate Delay</b>", description: "<i>Delay from issuing command to when it starts, in minutes:</i>", defaultValue: 0, required: false)
		}
		section ("Debug") {	
			//standard logging options
			input name: "debugOutput",    type: "bool", title: "<b>Enable debug logging?</b>", defaultValue: true
			input name: "descTextEnable", type: "bool", title: "<b>Enable descriptionText logging?</b>", defaultValue: true
		}
    }
}


// Helpers
void refresh () {getStatus()}


/*
	updated
    
	Doesn't do much other than call initialize().
*/
void updated()
{
	initialize()
	log.trace "EtherRain: updated ran"
}

                        
/*
	parse
    
	This should never be called.
*/
void parse(String description)
{
	if (descTextEnable) log.info "EtherRain: Description is $description"
}


void valveSet(options) {	
	def params = [
	    uri: "http://$etherrainip/result.cgi?lu=${settings.username}&lp=${settings.password}$options",
		headers: [
	        'Accept': '*/*',
	        'DNT': '1',
	        'Cache' : 'false',
	        'Accept-Encoding': 'plain',
	        'Cache-Control': 'max-age=0',
	        'Accept-Language': 'en-US,en,q=0.8',
	        'Connection': 'keep-alive',
	        'Referer': 'http://$etherrainip',
	        'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
	        'Cookie': device.data.cookiess        ],
	]

	if (debugOutput) log.debug "EtherRain request: $params"
 	asynchttpGet("valveSetHandler", params) 
}


void valveSetHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		if (debugOutput) log.debug "Request was successful, $resp.status"
		//if (debugOutput) log.debug "data = $resp.data"
		// all the resp stuff gets decoded here.
		state.erOstate = (resp.data =~ "os: (..)")[0][1] // operating status
		state.erCstate = (resp.data =~ "cs: (..)")[0][1] // command status
		state.erRstate = (resp.data =~ "rz: (..)")[0][1] // result (reZult)
		//if (debugOutput) log.debug "states: $state.erOstate $state.erCstate $state.erRstate" 
		translateStatus() 
	} else { log.error "EtherRain api did not return data" }
}


void getStatus() {
	if (debugOutput) log.debug "Executing getStatus"
	if (debugOutput) log.debug "http://$etherrainip/direct.cgi?lu=${settings.username}&lp=${settings.password}&xd=-:-:-:-:-:-:-:-"

	def params = [
	    uri: "http://$etherrainip/direct.cgi?lu=${settings.username}&lp=${settings.password}&xd=-:-:-:-:-:-:-:-",
	    headers: [
	        'Accept': '*/*',
	        'DNT': '1',
	        'Cache' : 'false',
	        'Accept-Encoding': 'plain',
	        'Cache-Control': 'max-age=0',
	        'Accept-Language': 'en-US,en,q=0.8',
	        'Connection': 'keep-alive',
	        'Referer': 'http://$etherrainip',
	        'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36',
	        'Cookie': device.data.cookiess        ],
	]

	//if (debugOutput) log.debug "doing request: $params"
 	asynchttpGet("statusHandler", params) 
}


void statusHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		if (debugOutput) log.debug "Request was successful, $resp.status"
		//if (debugOutput) log.debug "data = $resp.data"
		// all the resp stuff gets decoded here.
		state.erOstate = (resp.data =~ "os: (..)")[0][1] // operating status
		state.erCstate = (resp.data =~ "cs: (..)")[0][1] // command status
		state.erRstate = (resp.data =~ "rs: (..)")[0][1] // results
		//if (debugOutput) log.debug "states: $state.erOstate $state.erCstate $state.erRstate"
		translateStatus()
	} else { log.error "EtherRain api did not return data" }
}


/*
	open
    
	Child opens the valve.
*/
void open(id, valveTimer)
{
	def childDevice = getChildDevices()?.find {it.data.componentLabel == id.toInteger()}
	if (debugOutput) log.debug "EtherRain: $description Opened valve$id, $childDevice, $valveTimer, [$igateDelay]"

	long localeMillis = new Date().getTime()
	if (state.cycleInUse) {
		// cycleInUse is non-zero therefore a cycle is active.
		//   add in the new valve time
 		state.cycleInUse = localeMillis + (valveTimer.toInteger() * 60000)
	} else {
		// cycleInUse is zero, therefore cycle is inactive. 
		//   add igateDelay and valve time to now
 		state.cycleInUse = localeMillis + ((valveTimer.toInteger() + igateDelay.toInteger()) * 60000)
	}
	log.debug "localeMillis: $localeMillis, $valveTimer, $igateDelay"


	def valveBase = [1:0, 2:0, 3:0, 4:0, 5:0, 6:0, 7:0, 8:0]
	valveBase[id as Integer] = valveTimer // update one of the valve values
	
	state.valves = "$igateDelay"
	valveBase.each { k, v -> state.valves += ":$v" }	

	valveSet("&xi=${state.valves}") // what to append to the URI
	childDevice.parse([name:"valve$id", value:"on", descriptionText:"${childDevice.displayName} was turned on"])
	sendEvent(name: childDevice, value:"on") 
}


/*
	close
    
	Child closes the valve.
*/
void close(id, valveTimer)
{
	def childDevice = getChildDevices()?.find {it.data.componentLabel == id.toInteger()}
	if (debugOutput) log.debug "EtherRain: $description Closed valve$id, $childDevice"
	state.valves = "0:0:0:0:0:0:0:0:0"
	valveSet("&xr") // what to append to the URI (reset)
	childDevice.parse([name:"valve$id", value:"off", descriptionText:"${childDevice.displayName} was turned off"])
	state.cycleInUse = 0
	sendEvent(name: childDevice, value: "off") 
}


void createChildDevices() {
	for (i in 1..8) {
		def childDevice = getChildDevices()?.find {it.data.componentLabel == i}
		if (childDevice) {
			if (descTextEnable) log.info "Valve Child [$i] already exists"
		}
		else {
			if (debugOutput) {log.debug "Creating Valve Child [${device.deviceNetworkId}-$i]"} else {if (descTextEnable) log.info "Creating Valve Child [$i]"}
			childDevice = addChildDevice("csteele", "EtherRain Device (Child)", "${device.deviceNetworkId}-valve$i",[name: "${device.displayName}", label: "${device.displayName} Valve $i", isComponent: true, componentLabel:i])
			if (debugOutput) log.debug "childDevice: $childDevice"
		}
	}
}


void recreateChildDevices() {
    if (debugOutput) log.debug "recreateChildDevices"
    deleteChildren()
    createChildDevices()
}


void deleteChildren() {
	if (descTextEnable) log.info "EtherRain Deleting children"
	def children = getChildDevices()
	children.each {child->
		deleteChildDevice(child.deviceNetworkId)
    }
}


void translateStatus() {
/*
 OS = Operating Status provides current the state of the controller. The states are:
 RD – READY. The controller is ready to accept a new irrigate command
 WT- WAITING. The controller has accepted an Irrigate command that had a delay associated with it and is waiting to start the command.
 BZ - BUSY. The controller is currently executed an irrigate command.
 
 CS = Command status: - The result of the last command sent to the controller. Values returned are:
 OK - if the command was accepted
 ER - if the command had a formatting error
 NA - if the command came from an unauthorized IP address
 
 RZ/RS = Result of previous irrigation command. Values returned are:
 OK - the previous I command completed with no issues
 RN - the previous I command was interrupted by the rain indicator
 SH - the previous I command was interrupted by the detection of a short in the valve wiring (RI will give a clue to the zone causing the issue)
 NC - the previous command did not complete - most likely a power outage
 */

	state.erTstate = "Results: "

	switch (state.erOstate) {
		case "RD":
			state.erTstate += "Ready"
			break
		case "WT":
			state.erTstate += "Wait"
			break
		case "BZ":
			state.erTstate += "Busy"
			break
		default:
			state.erTstate += " "
			break
	}
	switch (state.erCstate) {
		case "OK":
			state.erTstate += ", OK"
			break
		case "ER":
			state.erTstate += ", Format Error"
			break
		case "NA":
			state.erTstate += ", Not Authorized"
			break
		default:
			state.erTstate += " "
			break
	}
	switch (state.erRstate) {
		case "OK":
			state.erTstate += ", OK"
			break
		case "RN":
			state.erTstate += ", Rain Detected"
			break
		case "SH":
			state.erTstate += ", Electrical Short"
			break
		case "NC":
			state.erTstate += ", Not Complete"
			break
		default:
			state.erTstate += " "
			break
	}
	log.debug "$state.erTstate"
}


/*

	generic driver stuff

*/


/*
	installed
    
	Doesn't do much other than call initialize().
*/
void installed()
{
	initialize()
	if (descTextEnable) log.info "EtherRain: installed ran"
}



/*
	initialize
    
	reschedule everything and create Child Devices.
*/
void initialize()
{
	unschedule()
//	if (debugOutput) runIn(1800,logsOff)        // disable debug logs after 30 min
	schedule("0 0 8 ? * FRI *", updateCheck)
	runIn(20, updateCheck) 
	createChildDevices()
	state.cycleInUse = 0
	if (descTextEnable) log.info "EtherRain: initialize ran"
}


/*
	on
    
	Parent UI button turns the device on.
*/
void open()
{
	if (descTextEnable) log.info "EtherRain: $description Opened $id"
	
}


/*
	off
    
	Parent UI button turns the device off.
*/
void close()
{
	if (descTextEnable) log.info "EtherRain: $description Closed $id"
}


/*
	logsOff

	Purpose: automatically disable debug logging after 30 mins.

	Note: scheduled in Initialize()

*/
void logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}


// Check Version   ***** with great thanks and acknowledgment to Cobra (CobraVmax) for his original code ****
void updateCheck()
{    
//	def paramsUD = [uri: "https://hubitatcommunity.github.io/EtherRainValves/version2.json"]
	def paramsUD = [uri: "https://csteele-pd.github.io/Hubitat-master/version2.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}

void updateCheckHandler(resp, data) {

	state.InternalName = "EtherRain 8 Valves"

	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		// log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright}"
		// uses reformattted 'version2.json' 
		def newVer = (respUD.driver.(state.InternalName).ver.replaceAll("[.vV]", ""))
		def currentVer = version().replaceAll("[.vV]", "")                
		state.UpdateInfo = (respUD.driver.(state.InternalName).updated)
            // log.debug "updateCheck: ${respUD.driver.(state.InternalName).ver}, $state.UpdateInfo, ${respUD.author}"

		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Driver is no longer supported by ${respUD.author}  **</b>"       
			      log.warn "** This Driver is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.driver.(state.InternalName).ver})</b>"
			      log.warn "** There is a newer version of this Driver available  (Version: ${respUD.driver.(state.InternalName).ver}) **"
			      log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})</b>"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this driver"
				break
		}

 	sendEvent(name: "verUpdate", value: state.UpdateInfo)
	sendEvent(name: "verStatus", value: state.Status)
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

void getThisCopyright(){"&copy; 2019 C Steele "}
