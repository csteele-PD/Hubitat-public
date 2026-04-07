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
 
public static String version()      {  return "v2.0.0"  }

/***********************************************************************************************************************
 * Version: 2.0.0     Refactor to use login in parent and protect data input.
 *
 *         v1.0.4     add Rain Sensor status.
 *                    removed State variables from standalone version check.
 *                    expose version & copyright.
 *         v1.0.3     use descTextEnable / log.info to provide at least one status when debug is off.
 *                    matched valve name for childDevice.parse 
 *                    removed null $description from status info message
 *         v1.0.2     use debugEnable to filter logs
 *                    removed standalone version check (allow HPM to check.)
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

		attribute "rainSensor", "string"

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
			input("igateDelay", "number", title: "<b>Irrigate Delay</b>", description: "<i>Delay from issuing command to when it starts, in minutes:</i>", defaultValue: 0, range: "0..249", required: false)
		}
		section ("Debug") {	
			//standard logging options
			input name: "descTextEnable", type: "bool", title: "<b>Enable descriptionText logging?</b>", defaultValue: true
			input name: "debugEnable",    type: "bool", title: "<b>Enable debug logging?</b>", defaultValue: true
			if (debugEnable) {
				input "debugTimeout", "enum", defaultValue: "0", title: "Automatic debug Log Disable Timeout?", width: 3,  \
				    	options: [ "0":"None", "1800":"30 Minutes", "3600":"60 Minutes", "86400":"1 Day" ]
			}
		}
    }
}


// Helpers
void refresh () {getStatus()}


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

Full Reset: http://etherrain.local:80?m=r
Get Config: http://etherrain.local:80/ergetcfg.cgi?lu=admin&lp=ice12pol
Get Status: http://etherrain.local/result.cgi?xs
Clear running: http://etherrain.local/result.cgi?xr

Irrigate with 9 params, first is the delay, then 8 valve durations, in minutes.
example (0 mins each) 
	http://etherrain.local/result.cgi?xi=0:0:0:0:0:0:0:0:0

*/

void valveSet(options) {
	etherainLogin(6)	// retry 6 times
	if (state.login == 'admin') {
		def params = [
			uri: "http://$etherrainip/result.cgi?$options",
			timeout: 5
		]

		if (debugEnable) log.debug "EtherRain request: $params"
	 	asynchttpGet("valveSetHandler", params) 
 	}
}


void valveSetHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		if (debugEnable) log.debug "Request was successful, $resp.status"
		// all the resp stuff gets decoded here.
		state.erOstate = parseField(resp?.data, "os", 2) // operating status
		state.erCstate = parseField(resp?.data, "cs", 2) // command status
		state.erRstate = parseField(resp?.data, "rz", 2) // result (reZult)
		state.erGstate = parseField(resp?.data, "rn", 1) // rain sensor
		translateStatus() 
	} else { log.error "EtherRain api did not return data. Check Username, PW and IP address." }
}


/*
	getStatus
    
	get status codes from EtherRain and decode/translate them to human readable.
*/
void getStatus() {
	if (debugEnable) log.debug "Executing getStatus"
	if (debugEnable) log.debug "http://$etherrainip/result.cgi?xs"
	etherainLogin(6)	// retry 6 times
	if (state.login == 'admin') {
		def params = [
			uri: "http://$etherrainip/result.cgi?xs",
			timeout: 5
		]

	 	asynchttpGet("statusHandler", params)
 	}
}


void statusHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		if (debugEnable) log.debug "Request was successful, $resp.status"
		// all the resp stuff gets decoded here.
		state.erOstate = parseField(resp?.data, "os", 2) // operating status
		state.erCstate = parseField(resp?.data, "cs", 2) // command status
		state.erRstate = parseField(resp?.data, "rz", 2) // results
		state.erGstate = parseField(resp?.data, "rn", 1) // rain sensor
		translateStatus()
		sendEvent(name: "rainSensor", value:state.erGstate, descriptionText:"")    
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
	etherainLogin
    
	EtherRain device requires a login before sending commands. "ur:" will equal 'admin' if successful.
*/
def etherainLogin(val, quickVal = null) {
	// login retry strategy: a few fast retries, then fall back to long waits
	state.loginRetry = (val != null) ? val : (state.loginRetry ?: 0)
	state.loginQuickRemaining = (quickVal != null) ? quickVal : (state.loginQuickRemaining ?: 3)
	state.login = "wait" // set to 'waiting'
	def params = [
		uri: "http://$etherrainip:80/ergetcfg.cgi?lu=${settings.username}&lp=${settings.password}",
		timeout: 5
	]

	if (debugEnable) log.debug "EtherRainLogin request: $params"
	try {
		httpGet(params) { resp ->
			if (descTextEnable) log.info "EtherRainLogin returned: $resp.status"

			if(resp.getStatus() == 200 || resp.getStatus() == 207) {
				if (debugEnable) log.debug "EtherRainLogin was successful, $resp.status"

				state.login = parseField(resp?.data, "ur", 5) // username, should be "admin"
				state.loginRetry = 0
				state.loginQuickRemaining = 0
			} 
			else {
				log.warn "Login failed: $state.login"
				state.login = "fail" // set to 'failed'
				scheduleLoginRetry()
			}
		}
	}
	catch (e) {
		if (descTextEnable) log.info "Login failed ($state.login) will retry: $e"
		state.login = "fail" // set to 'failed'
		scheduleLoginRetry()
	}
}


def scheduleLoginRetry() {
	if (state.loginRetry-- > 0) {
		if ((state.loginQuickRemaining ?: 0) > 0) {
			state.loginQuickRemaining = (state.loginQuickRemaining ?: 0) - 1
			runIn(5, "refreshLogin")
		} else {
			runIn(300, "refreshLogin")
		}
	}
}


def refreshLogin() {
	etherainLogin(state.loginRetry, state.loginQuickRemaining)
}


private String parseField(def data, String key, int len) {
	if (data == null) return null
	String text = data.toString()
	def matcher = (text =~ "${key}: (.{${len}})")
	return matcher.find() ? matcher.group(1) : null
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
	if (debugEnable) log.debug "localeMillis: $localeMillis, $valveTimer, $igateDelay"


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
			if (debugEnable) {log.debug "Creating Valve Child [${device.deviceNetworkId}-$i]"} else {if (descTextEnable) log.info "Creating Valve Child [$i]"}
			childDevice = addChildDevice("csteele", "EtherRain Device (Child)", "${device.deviceNetworkId}-valve$i",[name: "${device.displayName}", label: "${device.displayName} Valve $i", isComponent: true, componentLabel:i])
			if (debugEnable) log.debug "childDevice: $childDevice"
		}
	}
}


void recreateChildDevices() {
    if (debugEnable) log.debug "recreateChildDevices"
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

	switch (state.erOstate) {	// Operating State
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
	switch (state.erCstate) {	// Command State
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
	switch (state.erRstate) {	// Result State		
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
	switch (state.erGstate) {	// Rain Sensor State
		case "0":
			state.erTstate += ", Rain Sensor Not Detected"
			break
		case "1":
			state.erTstate += ", Rain Sensor Detected Rain"
			break
	}

	if (debugEnable) log.debug "$state.erTstate"
}


/*
	---=---=---=---=---=---=---=---=---
	generic driver stuff
	---=---=---=---=---=---=---=---=---
*/

/*
	updated
    
	Doesn't do much other than call initialize().
*/
void updated()
{
	initialize()
	log.trace "EtherRain: updated ran"
	state.Version = "${version()} - ${thisCopyright}"
	if (debugEnable && debugTimeout.toInteger() >0) runIn(debugTimeout.toInteger(), logsOff)

}

                        
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
	createChildDevices()
	state.cycleInUse = 0
	if (descTextEnable) log.info "EtherRain: initialize ran"
	// remove State variables from standalone version check
	state.remove("Copyright")
	state.remove("Status")
	state.remove("InternalName")
	state.remove("UpdateInfo")
}


/*
	logsOff

	Purpose: automatically disable debug logging after 30 mins.

	Note: scheduled in Initialize()

*/
void logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugEnable",[value:"false",type:"bool"])
}


def getThisCopyright(){"&copy; 2019 C Steele "}
