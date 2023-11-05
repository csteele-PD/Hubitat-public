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
 
 
public static String version()      {  return "v1.0.3"  }

/***********************************************************************************************************************
 *         v1.0.3     use descTextEnable / log.info to provide at least one status when debug is off.
 *                    matched valve name for childDevice.parse 
 *                    removed null $description from status info message
 *         v1.0.2     use debugOutput to filter logs
 *                    fixed typo for closed 
 * Version: 1.0.0
 *
 */

metadata 
{
	definition(name: "EtherRain 8 Valves", namespace: "csteele", author: "C Steele", importUrl: "https://raw.githubusercontent.com/csteele-PD/Hubitat-master/master/EtherRain-8-Valves.groovy")
	{
		capability "Actuator"
		capability "Refresh"

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


/*
	---=---=---=---=---=---=---=---=---
	Parent code section
	---=---=---=---=---=---=---=---=---

*/
void valveSet(options) {	
	def params = [
	    uri: "http://$etherrainip/result.cgi?lu=${settings.username}&lp=${settings.password}$options",
		headers: [
	        'Accept': '*/*', // */
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
	} else { log.error "EtherRain api did not return data. Check Username, PW and IP address." }
}


/*
	getStatus
    
	get status codes from EtherRain and decode/translate them to human readable.
*/
void getStatus() {
	if (debugOutput) log.debug "Executing getStatus"
	if (debugOutput) log.debug "http://$etherrainip/direct.cgi?lu=${settings.username}&lp=${settings.password}&xd=-:-:-:-:-:-:-:-"

	def params = [
	    uri: "http://$etherrainip/direct.cgi?lu=${settings.username}&lp=${settings.password}&xd=-:-:-:-:-:-:-:-",
	    headers: [
	        'Accept': '*/*', // */
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
	---=---=---=---=---=---=---=---=---
	Child code section
	---=---=---=---=---=---=---=---=---

*/

/*
	open
    
	Child opens the valve.
*/
void open(id, valveTimer)
{
	def childDevice = getChildDevices()?.find {it.data.componentLabel == id.toInteger()}
	if (descTextEnable) log.info "EtherRain: Opened $childDevice (valve$id), $valveTimer, [$igateDelay]"

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
	if (debugOutput) log.debug "localeMillis: $localeMillis, $valveTimer, $igateDelay"


	def valveBase = [1:0, 2:0, 3:0, 4:0, 5:0, 6:0, 7:0, 8:0]
	valveBase[id as Integer] = valveTimer // update one of the valve values
	
	state.valves = "$igateDelay"
	valveBase.each { k, v -> state.valves += ":$v" }	

	valveSet("&xi=${state.valves}") // what to append to the URI
	childDevice.parse([name:"valve", value:"open", descriptionText:"${childDevice.displayName} was Opened"])
	sendEvent(name: childDevice, value:"open")    // report an Event in Parent, report Valve event in child.
}


/*
	close
    
	Child closes the valve.
*/
void close(id, valveTimer)
{
	def childDevice = getChildDevices()?.find {it.data.componentLabel == id.toInteger()}
	if (descTextEnable) log.info "EtherRain: Closed $childDevice (valve$id)"
	state.valves = "0:0:0:0:0:0:0:0:0"
	valveSet("&xr") // what to append to the URI (reset)
	childDevice.parse([name:"valve", value:"closed", descriptionText:"${childDevice.displayName} was Closed"])
	state.cycleInUse = 0
	sendEvent(name: childDevice, value: "closed")   // report an Event in Parent, report Valve event in child.
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
 WT - WAITING. The controller has accepted an Irrigate command that had a delay associated with it and is waiting to start the command.
 BZ - BUSY. The controller is currently executing an irrigate command.
 
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
	if (debugOutput) log.debug "$state.erTstate"
}


/*
	---=---=---=---=---=---=---=---=---
	generic driver stuff
	---=---=---=---=---=---=---=---=---
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
	if (debugOutput) runIn(1800,logsOff)        // disable debug logs after 30 min
	createChildDevices()
	state.cycleInUse = 0
	if (descTextEnable) log.info "EtherRain: initialize ran"
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

void getThisCopyright(){"&copy; 2019 C Steele "}
