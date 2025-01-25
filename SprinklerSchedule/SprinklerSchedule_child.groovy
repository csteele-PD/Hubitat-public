/* 
=============================================================================
Hubitat Elevation Application
Sprinkler Schedule (child application)

    Original: Lighting Schedules https://github.com/matt-hammond-001/hubitat-code
    This fork: Sprinkler Schedules https://github.com/csteele-PD/Hubitat-public/tree/master/SprinklerSchedule

-----------------------------------------------------------------------------
This code is licensed as follows:

	BSD 3-Clause License
	
	Copyright (c) 2020, Matt Hammond
	All rights reserved.
	
	Redistribution and use in source and binary forms, with or without
	modification, are permitted provided that the following conditions are met:
	
	1. Redistributions of source code must retain the above copyright notice, this
	   list of conditions and the following disclaimer.
	
	2. Redistributions in binary form must reproduce the above copyright notice,
	   this list of conditions and the following disclaimer in the documentation
	   and/or other materials provided with the distribution.
	
	3. Neither the name of the copyright holder nor the names of its
	   contributors may be used to endorse or promote products derived from
	   this software without specific prior written permission.
	
	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
	AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
	IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
	DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
	FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
	DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
	SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
	CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
	OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
	OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
	-----------------------------------------------------------------------------
 *
 *
 *
 * csteele: v1.0.0	Converted from Matt Hammond's Lighting Schedule (child)
 *                	 Converted to capability.valve from switch 
 *
 */
 
	public static String version()      {  return "v1.0.1"  }


	import groovy.transform.Field

definition(
	name: "Sprinkler Valve Timetable",
	namespace: "csteele",
	parent: "csteele:Sprinkler Schedule Manager",
	author: "C Steele, Matt Hammond",
	description: "Controls valves to a timing schedule",
	documentationLink: "https://github.com//README.md",
	iconUrl: "",
	iconX2Url: "",
)

preferences {
	page(name: "main")
}

def main(){
	state.dayGroupTemplate = ['1':false, '2':false, '3':false, '4':false, '5':false, '6':false, '7':false] // new rows are all empty
	if(state.valves == null) state.valves = [:] 
	if(state.paused == null) state.paused = false
	if(state.dayGroupSettings == null) state.dayGroupSettings = ['1':['duraTime':0, 'startTime':0]]
	if(state.dayGroup == null) state.dayGroup = ['1': ['1':true, '2':true, '3':true, '4':true, '5':true, '6':true, '7':true] ] // initial row
	valves.each {
		   dev ->
			if(!state.valves["$dev.id"]) {
				state.valves["$dev.id"] = ['dayGroup':['1']]
			}
		}

	dynamicPage(name: "main", title: "", uninstall: true, install: true){
	  displayHeader()
	  section("<h1 style='font-size:1.5em; font-style: italic;'>General</h1>") {
		input "appLabel",
		    "text",
		    title: "Name for this application",
		    multiple: false,
		    required: true,
		    submitOnChange: true

		input "valves",
                "capability.valve",
                title: "Control which valves?",
                multiple: true,
                required: false,
                submitOnChange: true

		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
        }

	  if (valves) {
	  	section("<h1 style='font-size:1.5em; font-style: italic;'>Schedule</h1>") {
	  
	  		paragraph "<b>Select Days into Groups</b>"
	  		paragraph displayDayGroups()		// display day-of-week groups
	  		paragraph "<b>Select Period Settings by Group</b>"
	  		paragraph displayTable()		// display groups for scheduling
	  
	  		paragraph "<b>Select Valves into Day Groups</b>"
	  		paragraph displayGrpSched()		// display mapping of Valve to DayGroup
	  	
	  		displayDuration()
	  		displayStartTime()
	  		selectDayGroup()
	  		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	      }
	  }
	  section("<h1 style='font-size:1.5em; font-style: italic;'>Logging</h1>") {
            input "infoEnable",
                "bool",
                title: "Enable activity logging",
                required: false,
                defaultValue: true
        }

	  section("<h1 style='font-size:1.5em; font-style: italic;'>Debugging</h1>") {
            input "debugEnable",
                "bool",
                title: "Enable debug logging", 
                required: false,
                defaultValue: false
       	 paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
        }

	  if (true) {
            enaDis = state.paused ? "Disabled" : "Enabled" 
            section("<h1 style='font-size:1.5em; font-style: italic;'>Enable</h1>") {
		    input "btnSchEna", "button", title: "Schedule $enaDis", width: 3
       	    paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	    }
	  }
    }
}

/*
-----------------------------------------------------------------------------
Main Page handlers
-----------------------------------------------------------------------------
*/

String displayDayGroups() {	// display day-of-week groups - Section I
	if(state.dayGroupBtn) {
		logDebug "displayDayGroups Item: $state.dayGroupBtn"
		String dgK = state.dayGroupBtn.substring(0, 1); // dayGroupBtn Key
		String dgI = state.dayGroupBtn.substring(1);   // dayGroupBtn value (mon-sun)

		state.dayGroup."$dgK"."$dgI" = state.dayGroup."$dgK"."$dgI" ? false : true // toggle the state.
		state.remove("dayGroupBtn") // only once 
		logDebug "displayDayGroups Item: $dgK.$dgI"
	}

	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'>" +
		"<th style='border-right:2px solid black'>Day Group</th>" +
		"<th>Mon</th>" +
		"<th>Tue</th>" +
		"<th>Wed</th>" +
		"<th>Thu</th>" +
		"<th>Fri</th>" +
		"<th>Sat</th>" +
		"<th>Sun</th>" +
		"<th>&nbsp;&nbsp;</th>" +
		"</tr></thead>"

	str += "<tr style='color:black'border = 1>" 
	String X = "<i class='he-checkbox-checked'></i>"
	String O = "<i class='he-checkbox-unchecked'></i>"
	String Plus = "<i class='ic--sharp-plus'>+</i>"
	String Minus = "<i class='trashcan'>-</i>"
	String addDayGroupBtn = buttonLink("addDGBtn", Plus, "#1A77C9", "")

	strRows = ""
	state.dayGroup.each {
	     k, dg -> 
	        str += strRows
	        str += "<th>$k</th>"
	        for (int r = 1; r < 8; r++) { 
			String dayBoxN = buttonLink("w$k$r", O, "#1A77C9", "")
			String dayBoxY = buttonLink("w$k$r", X,   "#1A77C9", "")
	        	str += (dg."$r") ? "<th>$dayBoxY</th>" : "<th>$dayBoxN</th>" 
	        }
		  String remDayGroupBtn = buttonLink("rem$k", "<i style=\"font-size:1.125rem\" class=\"material-icons he-bin\"></i>", "#1A77C9", "")
		  str += "<th>$remDayGroupBtn</th>"
		  strRows = "</tr><tr>" 
	}
	str += "</tr><tr>"
	str += "<th>$addDayGroupBtn</th><th colspan=4> <- Add new Day Group</th>"
	str += "</tr></table></div>"
	str
}


String displayTable() { 	// display groups for scheduling - Section II
	if (state.reset) {
		state.dayGroupSettings[state.reset].startTime = 0
		state.dayGroupSettings[state.reset].duraTime = 0
		state.remove("reset")
	}

	state.dayGroupSettings.reverseEach {
		k, dg ->
		if (state.dayGroup[k]) {
			next
		} else {
			state.dayGroupSettings.remove(k)
 		}
	}

	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'>" +
		"<th style='border-right:2px solid black'>Day Group</th>" +
		"<th>Start Time</th>" +
		"<th>Duration</th>" +
		"<th>Reset</th>" +
		"</tr></thead>"

	state.dayGroup.each {
	     k, dg -> 
		  String dayGroupNamed = "Group $k"
		  String sTime    = state.dayGroupSettings[k]?.startTime ? buttonLink("t$k", state.dayGroupSettings[k].startTime, "black") : buttonLink("t$k", "Set Time", "green")
		  String dTime    = state.dayGroupSettings[k]?.duraTime
		  String duraTime = dTime ?  buttonLink("n$k", dTime, "purple") : buttonLink("n$k", "Select", "green")
		  String devLink  = "<a href='/device/edit/$k' target='_blank' title='Open Device Page for $dev'>$dev"
		  String reset    = buttonLink("x$k", "<iconify-icon icon='bx:reset'></iconify-icon>", "black", "20px")
		  str += "<tr style='color:black'>" +
		  	"<td style='border-right:2px solid black'>$dayGroupNamed</td>" +
		  	"<td>$sTime</td>" +
		  	"<td>$duraTime</td>" + 
		  	"<td title='Reset $k' style='padding:0px 0px'>$reset</td>" +
		  	"</tr>"
	}  
	str += "</table></div>"
	str
}


String displayGrpSched() {	// display mapping of Valve to DayGroup - Section III
	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'>" +
		"<th style='border-right:2px solid black'>Valve</th>" +
		"<th>Day Group</th>" +
		"</tr></thead>"

	valves?.sort{it.displayName.toLowerCase()}.each {
	     dev ->
		  dx = state.valves[dev.id].dayGroup
		  String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
		  String myDG = state.valves[dev.id].dayGroup
		  String myDayGroup = myDG ? buttonLink("r$dev.id", myDG, "purple") : buttonLink("r$dev.id", "Select", "green")
		  str += "<tr style='color:black'>" +
		  	"<td style='border-right:2px solid black'>$devLink</td>" +
			"<td title='${myDG ? "Deselect $myDG" : "Select String Hub Variable"}'>$myDayGroup</td></tr>"
		  	"</tr>"
	}  
	str += "</table></div>"
	str
}

/*
-----------------------------------------------------------------------------
Display level handlers
-----------------------------------------------------------------------------
*/
def displayStartTime() {
 	if(state.startTimeBtn) {
		input "StartTime", "time",   title: "At This Time", submitOnChange: true, width: 4, defaultValue: state.startTimeBtn, newLineAfter: false
		input "DoneTime$state.startTimeBtn",  "button", title: "  Done with time  ", width: 2, newLineAfter: false
		input "EraseTime$state.startTimeBtn", "button", title: "  Erase Time  ", width: 2, newLineAfter: true
		if(state.doneTime) {
			state.dayGroupSettings[state.startTimeBtn].startTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", StartTime).format('HH:mm')
			state.remove("startTimeBtn")
			app.removeSetting("DoneTime")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
		else if(state.eraseTime) {
			state.dayGroupSettings[state.startTimeBtn].startTime = 0
			state.remove("startTimeBtn")
			app.removeSetting("EraseTime")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
		else if(StartTime) {
			state.dayGroupSettings[state.startTimeBtn].startTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", StartTime).format('HH:mm')
			state.remove("startTimeBtn")
			app.removeSetting("StartTime")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
	}
}

def displayDuration() {
 	if(state.duraTimeBtn) {
		input "DuraTime", "decimal", title: "Sprinkler Duration", submitOnChange: true, width: 4, range: "1..60", defaultValue: state.dayGroupSettings[state.duraTimeBtn].duraTime, newLineAfter: true
		if(DuraTime) {
			state.dayGroupSettings[state.duraTimeBtn].duraTime = DuraTime //validateTimings(DuraTime)
			state.remove("duraTimeBtn")
			app.removeSetting("DuraTime")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
    }
}

def selectDayGroup() {
 	if(state.dayGrpBtn) {
		List vars = state.dayGroup.keySet().collect() 

		input "DayGroup", "enum", title: "Sprinkler Group", submitOnChange: true, width: 4, options: state.dayGroup, newLineAfter: true, multiple: true
		if(DayGroup) {
			state.valves[state.dayGrpBtn].dayGroup = DayGroup
			state.remove("dayGrpBtn")
			app.removeSetting("DayGroup")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
	}
}

def addDayGroup(evt = null) {
	dayGroupSize = state.dayGroup.keySet().size()
	s = dayGroupSize as int
	s++
	logDebug "adding another dayGroup map: $s"
	state.dayGroup += ["$s":state.dayGroupTemplate] 
	state.dayGroupSettings += ["$s":['duraTime':0, 'startTime':0]] 
}


def remDayGroup(evt = null) {
	dayGroupSize = state.dayGroup.keySet().size()
	dGTemp = [:]
	dGSetTemp =[:]
	logDebug "remove another dayGroup map: $dayGroupSize, $evt"
	if (dayGroupSize >= 2) {
		state.dayGroup.each {
		    if (it.key < evt) {
		        dGTemp[it.key]=it.value
		    }
		    else if (it.key > evt) {
		        k = it.key as Integer
		        k--
		        dGTemp[k]=it.value
		    }
		}
		state.dayGroupSettings.each {
		    if (it.key < evt) {
		        dGSetTemp[it.key]=it.value
		    }
		    else if (it.key > evt) {
		        k = it.key as Integer
		        k--
		        dGSetTemp[k]=it.value
		    }
		}
	state.dayGroup = dGTemp
	state.dayGroupSettings = dGSetTemp
	}
}


def toggleEnaSchBtn(evt) {
	state.paused = state.paused ? false : true
 logDebug "toggle: $state.paused"
}


/*
	.containsKey("xx")
	.containsValue("xx")
*/


String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
	state.remove("duraTimeBtn")
	state.remove("dayGrpBtn")
	state.remove("startTimeBtn")
	state.remove("dayGroupBtn")
	state.remove("doneTime")
	state.remove("eraseTime")
		app.removeSetting("StartTime")
		app.removeSetting("DuraTime") 

	if(btn == "reset") resetTimers()
	else if(btn == "refresh") state.valves.each{k, v ->
		def dev = valves.find{"$it.id" == k}
		if(dev.currentValve == "open") {
	////		state.dayGroupSettings[k].startTime += now() - state.valves[k].start
		}
	}
	else if ( btn == "btnSchEna")           toggleEnaSchBtn()
	else if ( btn == "addDGBtn")            addDayGroup()
	else if ( btn.startsWith("rem")      )  remDayGroup(btn.minus("rem")) 
	else if ( btn.startsWith("n")        )  state.duraTimeBtn = btn.minus("n")
	else if ( btn.startsWith("r")        )  state.dayGrpBtn = btn.minus("r")
	else if ( btn.startsWith("t")        )  state.startTimeBtn = btn.minus("t")
	else if ( btn.startsWith("w")        )  state.dayGroupBtn = btn.minus("w")
	else if ( btn.startsWith("doneTime") )  state.doneTime = btn.minus("doneTime")
	else if ( btn.startsWith("eraseTime"))  state.eraseTime = btn.minus("eraseTime")
	else state.reset = btn.minus("x")

}


/*
-----------------------------------------------------------------------------
Logging output
-----------------------------------------------------------------------------
*/

def logDebug(msg) {
	if (settings.debugEnable) {
        log.debug msg
	}
}

def logInfo(msg) {
    if (settings.infoEnable) {
        log.info msg
    }
}

/*
-----------------------------------------------------------------------------
Regex for matching time period patterns
-----------------------------------------------------------------------------

Matches: TIMESPEC-TIMESPEC REMAINDER

	TIMESPEC = any of:
	             HHMM
	             HH:MM
	             sunrise
	             sunset
	             sunrise[+-]OFFSET
	             sunset[+-]OFFSET
	HH     := [0-2][0-9]
	MM     := [0-5][0-9]
	OFFSET := 0|(?:[1-9][0-9]{0,2})    .... between 1 and 3 digits, always starting with a non zero, or just zero

  restricting format of OFFSET in this way means it will never match the 4 digit pattern or HHMM or HH:MM ... hopefully
*/

@Field static def timeRangePattern = ~/^[;, ]*(?:(?:([0-2][0-9])[:.]?([0-5][0-9]))|(?:(sunrise|sunset)( *[+-] *(?:0|(?:[1-9][0-9]{0,2})))?)) *(?:-|to) *(?:(?:([0-2][0-9])[:.]?([0-5][0-9]))|(?:(sunrise|sunset)( *[+-] *(?:0|(?:[1-9][0-9]{0,2})))?))[;, ]*(.*)$/

/*
-----------------------------------------------------------------------------
Standard handlers, and mode-change handler
-----------------------------------------------------------------------------
*/

def installed() {
	logDebug "installed()"
	state.wasInPeriod = [:]
	state.wasActive = null
	initialize()
}


def updated() {
	logDebug "updated()"
	unsubscribe()
	initialize()
}


def initialize() {
	logDebug "initialize()"
	subscribe location, "mode", modeChangeHandler
	update(true)
}

def uninstalled() {
	logDebug "uninstalled()"
}


def modeChangeHandler(evt) {
	update(true)
}

/**
 * if a valve becomes unselected by the user, the dynamically created settings entry is not automatically deleted by hubitat
 * so this method cleans them up, both permanently by calling app.removeSeting, and also immediately for the subsequent update() code
 * by also removing from the temporary current settings object
 */
def scrubUnusedSettings() {
	settings.findAll {
	    it -> it.key =~ /devTimings-[0-9]+/
	}.findAll {
	    it -> ! valves.any {
	        dev -> it.key == "devTimings-$dev.id"
	    }
	}.each {
	    it ->
	        app.removeSetting it.key
	        settings.remove it.key
	}
}

/*
-----------------------------------------------------------------------------
Whenever there is a change/update
-----------------------------------------------------------------------------
*/

def update(modeChanged=false) {
	def pauseText = "";
	if (settings.paused) {
	    pauseText = ' <span style="color: red;">(Paused)</span>'
	}
	if (settings.appLabel) {
	    app.updateLabel("${settings.appLabel}${pauseText}")
	} else {
	    app.updateLabel("Schedule${pauseText}")
	}
	scrubUnusedSettings()

	logDebug "update() - paused=${paused} activeAlways=${activeAlways} mode=${location.mode} modeChanged=${modeChanged}"
	def isActive = !paused && (activeAlways || activeModes.any {v -> v == location.mode})
	
	if (isActive) {
	    logDebug "Active in mode $location.mode"
	    if (modeChanged) {
	        runEvery1Minute(updateValves)
	    }
	} else {
	    logDebug "Inactive in mode $location.mode"
	    unschedule(updateValves)
	}

	updateValves()
}

/*
-----------------------------------------------------------------------------
Determine and make changes
-----------------------------------------------------------------------------
*/

def updateValves() {
    if (paused) {
        return
    }
    def isActive = activeAlways || activeModes.any {v -> v == location.mode}
    def activeChanged = isActive != state.wasActive

    def timings = buildTimings()
    def now = Calendar.getInstance()
    
    def nowInPeriod = [:]
    
    valves?.each{ dev ->
        def ID = ""+dev.deviceId

        def inPeriod = isInPeriod(timings[ID], now)
        def isOn = dev.currentValue('valve', true) == 'on'
        def isOff = !isOn

        def wasInPeriod;
        if (state.wasInPeriod == null) {
            wasInPeriod = inPeriod
        } else {
            wasInPeriod = state.wasInPeriod[ID]
        }

        nowInPeriod[ID] = inPeriod
        
        logDebug "Checking for device ID $ID ... isOn: $isOn, inPeriod: $inPeriod, wasInPeriod: $wasInPeriod"
        
        if (activeChanged) {
            if (isActive) {  // has been activated
                if (inPeriod) {
                    switch (settings.periodBehaviour) {
                        case "duringOn":
                        case "entryOn":
                            if (isOff && settings.activateBehaviour == "both" || settings.activateBehaviour == "on") {
                                logInfo "Turning ON $dev.deviceId : $dev.name / $dev.label (activation during period)"
                                dev.open();
                            }
                            break;
                        case "duringOff":
                        case "entryOff":
                            if (isOn && settings.activateBehaviour == "both" || settings.activateBehaviour == "off") {
                                logInfo "Turning OFF $dev.deviceId : $dev.name / $dev.label (activation during period)"
                                dev.close();
                            }
                            break;
                        case "ignore":
                            break;
                    }
                } else { // not in period
                    switch (settings.noPeriodBehaviour) {
                        case "duringOn":
                        case "entryOn":
                            if (isOff && settings.activateBehaviour == "both" || settings.activateBehaviour == "on") {
                                logInfo "Turning ON $dev.deviceId : $dev.name / $dev.label (activation outside period)"
                                dev.open();
                            }
                            break;
                        case "duringOff":
                        case "entryOff":
                            if (isOn && settings.activateBehaviour == "both" || settings.activateBehaviour == "off") {
                                logInfo "Turning OFF $dev.deviceId : $dev.name / $dev.label (activation outside period)"
                                dev.close();
                            }
                            break;
                        case "ignore":
                            break;
                    }
                }
            } else { // has been deactivated
                switch (settings.deactivateBehaviour) {
                    case "allOn":
                        logInfo "Turning ON $dev.deviceId : $dev.name / $dev.label (deactivation)"
                        dev.open();
                        break;
                    case "allOff":
                        logInfo "Turning OFF $dev.deviceId : $dev.name / $dev.label (deactivation)"
                        dev.close();
                        break;
                    case "nothing":
                        break;
                }
            }
        } else { // no change of activation state
            if (isActive) {
                if (inPeriod) {
                    if (wasInPeriod) {
                        // during period
                        if (settings.periodBehaviour == "duringOn" && isOff) {
                            logInfo "Turning ON $dev.deviceId : $dev.name / $dev.label (during period)"
                            dev.open()
                        } else if (settings.periodBehaviour == "duringOff" && isOn) {
                            logInfo "Turning OFF $dev.deviceId : $dev.name / $dev.label (during period)"
                            dev.close()
                        }
                    } else {
                        // transitioned into period
                        if ((settings.periodBehaviour == "entryOn" || settings.periodBehaviour == "duringOn") && isOff) {
                            logInfo "Turning ON $dev.deviceId : $dev.name ?: $dev.label (entering into period)"
                            dev.open()
                        } else if ((settings.periodBehaviour == "entryOff" || settings.periodBehaviour == "duringOff") && isOn) {
                            logInfo "Turning OFF $dev.deviceId : {$dev.name ?: $dev.label} (entering into period)"
                            dev.close()
                        }
                    }
                } else {
                    if (wasInPeriod) {
                        // transitioned out of period
                        if ((settings.noPeriodBehaviour == "entryOn" || settings.noPeriodBehaviour == "duringOn") && isOff) {
                            logInfo "Turning ON $dev.deviceId : $dev.name / $dev.label (entering out of period)"
                            dev.open()
                        } else if ((settings.noPeriodBehaviour == "entryOff" || settings.noPeriodBehaviour == "duringOff") && isOn) {
                            logInfo "Turning OFF $dev.deviceId : $dev.name / $dev.label (entering out of period)"
                            dev.close()
                        }
                    } else {
                        // during out of period
                        if (settings.noPeriodBehaviour == "duringOn" && isOff) {
                            logInfo "Turning ON $dev.deviceId : $dev.name / $dev.label (during out of period)"
                            dev.open()
                        } else if (settings.noPeriodBehaviour == "duringOff" && isOn) {
                            logInfo "Turning OFF $dev.deviceId : $dev.name / $dev.label (during out of period)"
                            dev.close()
                        }
                    }
                }
            } else {
                // inactive. do nothing
            }
        }
    }
    state.wasInPeriod = nowInPeriod
    state.wasActive = isActive
}

/*
-----------------------------------------------------------------------------
Helper functions
-----------------------------------------------------------------------------
*/

/* given array of [start,end] timings, and time now, check if should be on
*/
def isInPeriod(timings, now) {
    return true == timings.any{ start,end -> 
        start.before(now) && end.after(now)
    }
}


/* return a map of device IDs to parsed timings (where timings is a list of [start,end] timings */
def buildTimings() {
    def timings = [:]
    
     // build timings map from settings entries
    getDeviceTimingStrings { devId, key, value ->
        try {
            timings[devId] = parseTimings(value)
        } catch (e) {
            logDebug "Failure parsing: $value : "+e.getMessage()
            timings[devId] = []
        }
    }
    return timings
}

/* Get time periods string for each device, calling callpack with the device ID, the settings key, and the string */
def getDeviceTimingStrings(cb) {
    for (entry in settings) {
        def match = entry.key =~ /^devTimings-([0-9]+)$/
        if (match) {
           def devId = "" + match[0][1]
           cb(devId, entry.key, entry.value)
        }
    }
}



def validateTimings(timingString) {
    while (timingString) {
        def match = timingString =~ timeRangePattern
        if (!match) {
            return false
        }
        timingString = match[0][9]
    }
    return true
}

def parseTimings(timingString) {
    def timings = []
    
    def srss = getSunriseAndSunset()
    today = {}
    today.now = Calendar.getInstance()
    today.midday = today.now.clone()
    today.midday.set(Calendar.HOUR_OF_DAY, 12)
    today.midday.set(Calendar.MINUTE, 0)
    today.midday.set(Calendar.SECOND, 0)
    today.sunrise = Calendar.getInstance()
    today.sunrise.setTime(srss.sunrise)
    today.sunset = Calendar.getInstance()
    today.sunset.setTime(srss.sunset)
    
    while (timingString) {
        def match = timingString =~ timeRangePattern
        if (match) {
            (_, sH, sM, sR, sO, eH, eM, eR, eO, remainder) = match[0]
            (startTime, endTime) = calcStartEndTimes(today, sH, sM, sR, sO, eH, eM, eR, eO)
            
            if (startTime.before(endTime)) {
                timings += [[ startTime, endTime ]]
            }
            timingString = remainder

        } else {
            timingString = ""
        }
    }
    timings.sort()
    return timings
}

@Field static def ABS = 0
@Field static def SUNRISE = 1
@Field static def SUNSET = 2
    

def calcStartEndTimes(today, sH, sM, sR, sO, eH, eM, eR, eO) {
    (start, sType, refStart) = calcTime(today, sH, sM, sR, sO)
    (end, eType, refEnd)     = calcTime(today, eH, eM, eR, eO)

    // intelligently handle edge cases when one timespec is relative to sunrise or sunset
    if (sType == ABS && eType == SUNRISE) {
        if (start.after(today.midday)) {
            start.add(Calendar.DAY_OF_YEAR, -1)
        }
    } else if (sType == SUNRISE && eType == ABS) {
        // no changes
    } else if (sType == ABS && eType == SUNSET) {
        // no changes
    } else if (sType == SUNSET && eType == ABS) {
        if (start.before(today.midday)) {
            start.add(Calendar.DAY_OF_YEAR, +1)
        }
    }
    
    // if user specified end was specified as being before start time
    // and wasn't due to user not anticipating variability in sunrise/sunset,
    // then compensate by shuffling end time to be the next day
    if (refEnd.before(refStart) && end.before(start)) {        
        end.add(Calendar.DAY_OF_YEAR,1)
    }

    // if it was in the past, make it the future
    if (end.before(today.now)) {
        start.add(Calendar.DAY_OF_YEAR, 1)
        end.add(Calendar.DAY_OF_YEAR, 1)
    }

    return [start,end]
}
            
def calcTime(today, hrs, mins, relTo, offsetMins) {
    def t = Calendar.getInstance()
    def type = ABS
    def ref_t
    
    if (hrs != null && mins != null) {
        t.set(Calendar.HOUR_OF_DAY, hrs.toInteger())
        t.set(Calendar.MINUTE, mins.toInteger())
        t.set(Calendar.SECOND, 0)

    } else if (relTo != null) {
        switch (relTo) {
            case "sunrise":
                type=SUNRISE
                break
            case "sunset":
                type=SUNSET
                break
            default:
                throw new Exception("Unrecognised relative-to timespec")
        }

    } else {
        throw new Exception("Timespec not valid")
    }

    if (type != ABS) {
        t = today[relTo].clone()
        ref_t = t.clone()
        if (offsetMins != null && offsetMins != "") {
            t.add(Calendar.MINUTE, offsetMins.toInteger())
        }
    } else{
        ref_t = t.clone()
    }
    
    // ref_t is the time before offset is applied. Give indication as to users' thinking

    return [t, type, ref_t]
}

void resetTimers(evt = null) {
	state.valves.each{k, v ->
		def dev = valves.find{"$it.id" == k}
		state.dayGroupSettings[k].startTime = 0
		state.dayGroupSettings[k].duraTime = 0
	}
}


def getFormat(type, myText=""){            // Modified from @Stephack Code
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def displayHeader() {
	section (getFormat("title", "Sprinkler Schedule")) {
		paragraph "<div style='color:#1A77C9;text-align:right;font-weight:small;font-size:9px;'>Developed by: C Steele, Matt Hammond<br/>Current Version: ${version()} -  ${thisCopyright}</div>"
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	}
}

def getThisCopyright(){"&copy; 2023 C Steele"}
