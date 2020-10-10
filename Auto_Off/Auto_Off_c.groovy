/**
 *  Hubitat Import URL: 
 */

/**
 *  Auto_Off Child 
 *
 *  Copyright 2020 C Steele, Mattias Fornander
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
	public static String version()      {  return "v0.1.3"  }


import groovy.time.*

// Set app Metadata for the Hub
definition(
	name: "Auto_Off device",
	namespace: "csteele",
	author: "Mattias Fornander, CSteele",
	description: "Automatically turn off/on devices after set amount of time on/off",
	category: "Automation",
	importUrl: "",
	    
	parent: "csteele:Auto_Off",
	
	iconUrl: "",
	iconX2Url: "",
	singleInstance: false
)


preferences {
	page (name: "mainPage")
}


/**
 * Called after app is initially installed.
 */
def installed() {
	initialize()
	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
	app.clearSetting("descTextEnable")
}


/**
 * Called after any of the configuration settings are changed.
 */
def updated() {
	unsubscribe()
	unschedule()
	initialize()
}


/**
 * Internal helper function with shared code for installed() and updated().
 */
private initialize() {
	if (debugOutput) log.debug "Initialize with settings: ${settings}"
	state.offList = [:]

	subscribe(devices, "switch", switchHandler)
	updateMyLabel()
}


/**
 * Main configuration function declares the UI shown.
 */
def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
	  updateMyLabel()
	  section("<h2>${app.label ?: app.name}</h2>"){
            paragraph '<i>Automatically turn off/on devices after set amount of time on/off.</i>'
            input name: "autoTime", type: "number", title: "Time until auto-off (minutes) [24hrs max.]", required: true
            input name: "devices", type: "capability.switch", title: "Devices", required: true, multiple: true
            input name: "invert", type: "bool", title: "Invert logic (make app Auto On)", defaultValue: false
            input name: "master", type: "capability.switch", title: "Master Switch", multiple: false
        }
	  section (title: "<b>Name/Rename</b>") {
	  	label title: "This child app's Name (optional)", required: false, submitOnChange: true
	  	if (!app.label) {
	  		app.updateLabel(app.name)
	  		atomicState.appDisplayName = app.name
	  	}
	  	if (app.label.contains('<span ')) {
	  		if (atomicState?.appDisplayName != null) {
	  			app.updateLabel(atomicState.appDisplayName)
	  		} else {
	  			String myLabel = app.label.substring(0, app.label.indexOf('<span '))
	  			atomicState.appDisplayName = myLabel
	  			app.updateLabel(myLabel)
	  		}				
	  	}
	  }
	  display()
    }
}


/**
 * Handler called when any of our devices turn on, or off.
 *
 * We use the device id of the switch turning on as key since the evt.device
 * object seems to be a proxy object that changes with each callback.  The first
 * implementation used the evt.device as key but that would create multiple
 * entries in the map for the same switch.  Using the device id instead ensures
 * that a user that turn on and off and on the same switch, will only have one
 * entry since the id stays the same and new off times replace old off times.
 */
def switchHandler(evt) {
	// Add the watched device if turning on, or off if inverted mode
	if ((evt.value == "on") ^ (invert == true)) {
	    if (autoTime > 1440) {
	        autoTime = 1440
	        app.updateSetting("autoTime", autoTime)
	    }
	    def delay = Math.floor(autoTime * 60).toInteger()
	    runIn(delay, scheduleHandler, [overwrite: false])
	    atomicState.cycleEnd = now() + autoTime * 60 * 1000
	    state.offList[evt.device.id] = now() + autoTime * 60 * 1000
	} else {
	    state.offList.remove(evt.device.id)
	}
	updateMyLabel()

	if (debugOutput) log.debug "switchHandler delay: $delay, evt.device:${evt.device}, evt.value:${evt.value}, state:${state}, " +
	    "${evt.value == "on"} ^ ${invert==true} = ${(evt.value == "on") ^ (invert == true)}"
}


/**
 * Handler called every minute to see if any devices should be turned off, or on.
 *
 * THe first pass used an optimized schedule that looked for the next switch to
 * turn off and would schedule a callback for exactly that time and then
 * reschedule the next off item, if any.  However, it seemed error-prone and
 * cumbersome since errors can happen that may interrupt the rescheduling.
 * Calling a tiny function with a quick check seemed ok to do every minute
 * so that's v1.0 for now.
 */
def scheduleHandler() {
	// Find all map entries with an off-time that is earlier than now
	def actionList = state.offList.findAll { it.value < now() }
	
	// Find all devices that match the off-entries from above
	def deviceList = devices.findAll { device -> actionList.any { it.key == device.id } }
	
	if (debugOutput) log.debug "scheduleHandler now:${now()} offList:${state.offList} actionList:${actionList} deviceList:${deviceList}"
	
	// Call off(), or on() if inverted, on all relevant devices and remove them from offList
	if (!master || master.latestValue("switch") == "on") {
	    if (invert) deviceList*.on()
	    else deviceList*.off()
	} else {
	    if (debugOutput) log.debug "Skipping actions because MasterSwitch '${master?.displayName}' is Off"
	}
	state.offList -= actionList
}


def setDebug(dbg, inf) {
	app.updateSetting("debugOutput",[value:dbg, type:"bool"])
	app.updateSetting("descTextEnable",[value:inf, type:"bool"])
	if (descTextEnable) log.info "Set by Parent: debugOutput: $debugOutput, descTextEnable: $descTextEnable"
}


def display()
{
//	updateCheck()
	section {
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
		paragraph "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>Developed by: C Steele<br/>Version Status: $state.Status<br>Current Version: ${version()} -  ${thisCopyright}</div>"
    }
}


def updateMyLabel() {
	String flag = '<span '

	// Display state / status as part of the label...
	String myLabel = atomicState.appDisplayName
	if ((myLabel == null) || !app.label.startsWith(myLabel)) {
		myLabel = app.label ?: app.name
		if (!myLabel.contains(flag)) atomicState.appDisplayName = myLabel
	} 
	if (myLabel.contains(flag)) {
		// strip off any connection status tag :: retain the original display name
		myLabel = myLabel.substring(0, myLabel.indexOf(flag))
		atomicState.appDisplayName = myLabel
	}

	if (!master || master.latestValue("switch") == "off") {
	    if (devices.findAll{it.latestValue("switch") == "on"}.size) {
		    myLabel = myLabel + " <span style=\"color:Green\">" + fixDateTimeString(atomicState.cycleEnd) + " Active</span>"		
	    } else {
		myLabel = myLabel + " <span style=\"color:Green\">Idle</span>"
		atomicState.cycleEnd = -1
        }
	 } else {
		myLabel = myLabel + " <span style=\"color:Crimson\">[-]</span>"
		atomicState.cycleEnd = -1
	}

	if (app.label != myLabel) app.updateLabel(myLabel) ; log.debug "label: $myLabel"
}


String fixDateTimeString( eventDate) {
	def today = new Date(now()).clearTime()
	def target = new Date(eventDate).clearTime()
	
	String resultStr = ''
	String myDate = ''
	String myTime = ''
	boolean showTime = true
	
	if (target == today) {
		myDate = 'today'	
	} else if (target == today-1) {
		myDate = 'yesterday'
	} else if (target == today+1) {
		myDate = 'tomorrow'
	} else if (dateStr == '2035-01-01' ) {		// to Infinity
		myDate = 'a long time from now'
		showTime = false
	} else {
		myDate = 'on '+target.format('MM-dd')
	}	 
	if (showTime) {
		myTime = new Date(eventDate).format('h:mma').toLowerCase()
	}
	if (myDate || myTime) {
		resultStr = myTime ? "${myDate} at ${myTime}" : "${myDate}"
	}
	if (debugOutput) log.debug "banner: ${resultStr}"
	return resultStr
}


// Check Version   ***** with great thanks and acknowledgment to Cobra (CobraVmax) for his original code ****
def updateCheck()
{    
	def paramsUD = [uri: "https://hubitatcommunity.github.io/Auto_Off/version2.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}


def updateCheckHandler(resp, data) {
	state.InternalName = "Auto_Off_c"
	state.Status = "Unknown"
	
	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		//log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright} -- ${version()}"
		// uses reformattted 'version2.json' 
		def newVer = padVer(respUD.application.(state.InternalName).ver)
		def currentVer = padVer(version())               
		state.UpdateInfo = (respUD.application.(state.InternalName).updated)
            // log.debug "updateCheck: ${respUD.driver.(state.InternalName).ver}, $state.UpdateInfo, ${respUD.author}"
	
		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Application is no longer supported by ${respUD.author}  **</b>"       
			      log.warn "** This Application is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.application.(state.InternalName).ver})</b>"
			      log.warn "** There is a newer version of this Application available  (Version: ${respUD.application.(state.InternalName).ver}) **"
			      log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Application (Expecting: ${respUD.application.(state.InternalName).ver})</b>"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this Application"
				break
		}

	      sendEvent(name: "chkUpdate", value: state.UpdateInfo)
	      sendEvent(name: "chkStatus", value: state.Status)
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}


/*
	padVer

	Version progression of 1.4.9 to 1.4.10 would mis-compare unless each column is padded into two-digits first.

*/ 
def padVer(ver) {
	def pad = ""
	ver.replaceAll( "[vV]", "" ).split( /\./ ).each { pad += it.padLeft( 2, '0' ) }
	return pad
}

def getThisCopyright(){"&copy; 2019 C Steele "}
