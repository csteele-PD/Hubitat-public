/* 
=============================================================================
Hubitat Elevation Application
Sprinkler Schedule (child application)

    Inspiration: Lighting Schedules https://github.com/matt-hammond-001/hubitat-code
    This fork: Sprinkler Schedules https://github.com/csteele-PD/Hubitat-public/tree/master/SprinklerSchedule

-----------------------------------------------------------------------------
This code is licensed as follows:

	BSD 3-Clause License
	
	Copyright (c) 2023, C Steele
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
 * csteele: v1.0.1	? Added month2month and dayGroupMaster from Parent
 * csteele: v1.0.0	Inspired by Matt Hammond's Lighting Schedule (child)
 *                	 Converted to capability.valve from switch 
 *
 */
 
	public static String version()      {  return "v1.0.1"  }


definition(
	name: "Sprinkler Valve Timetable",
	namespace: "csteele",
	parent: "csteele:Sprinkler Schedule Manager",
	author: "C Steele",
	description: "Controls valves to a timing schedule",
	documentationLink: "https://github.com//README.md",
	iconUrl: "",
	iconX2Url: "",
)

preferences {
	page(name: "main")
}

def main(){
	init() 	// pre-populate any empty elements
	dynamicPage(name: "main", title: "", uninstall: true, install: true){
	  updateMyLabel()
	  displayHeader()
	  section("<h1 style='font-size:1.5em; font-style: italic;'>General</h1>") {
		label title: "<b>Name for this application</b>", required: false, submitOnChange: true
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

		paragraph "\n<b>Valve Select</b>"
		input "valves",
                "capability.valve",
                title: "Control which valves?",
                multiple: true,
                required: false,
                submitOnChange: true

 		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
        }

        // provide some feedback on which valves are On
        String str = ""
        valves?.each{ dev ->
        	def ID = dev.deviceId
        	def isOn = dev.currentValue('valve', true) == 'open'
        	str += isOn ? "$dev.label is On, " : "$dev.label is Off, "
        }
        section(menuHeader("Valve Status")) {
        	paragraph str
        }
        
	  if (state.month2month) {
		section(menuHeader("Parent - Advanced Options")) {
		  	paragraph "Adjust valve timing by Month is active"
			paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
		}
	  }

	
	  if (valves) {
	  	section("<h1 style='font-size:1.5em; font-style: italic;'>Schedule</h1>") {
	  
	  		paragraph "<b>Select Days into Groups</b>"
	  		paragraph displayDayGroups()		// display day-of-week groups - Section I

	  		paragraph "<b>Select Period Settings by Group</b>"
	  		paragraph displayTable()		// display groups for scheduling - Section II
	  		  displayDuration()
	  		  displayStartTime()
	  
	  		paragraph "<b>Select Valves into Day Groups</b>"
	  		paragraph displayGrpSched()		// display mapping of Valve to DayGroup - Section III
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
            enaDis = atomicState.paused ? "Disabled" : "Enabled" 
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
		String dgK = state.dayGroupBtn.substring(0, 1); // dayGroupBtn Key
		String dgI = state.dayGroupBtn.substring(1);   // dayGroupBtn value (mon-sun)

		state.dayGroup."$dgK"."$dgI" = state.dayGroup."$dgK"."$dgI" ? false : true // toggle the state.
		state.remove("dayGroupBtn") // only once 
		logDebug "displayDayGroups Item: $dgK.$dgI"
	}

	// Master + Local dayGroups get combined using a "deep copy"
	if (state.dayGroupMaster) {
		incDayGroup = state.dayGroup.collectEntries { k, v -> [(k.toInteger() + incM).toString(), v] } // renumber the entries in the local dayGroup
		// Merge incremented dayGroup 
		state.dayGroupMerge = new HashMap<>(state.dayGroupMaster) // make a deep copy
		state.dayGroupMerge.putAll(incDayGroup)  // "append" local to master
	} else {
	    state.dayGroupMerge = new HashMap<>(state.dayGroup) // make a deep copy
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
	rowCount = 1
	state.dayGroupMaster.each {
	     k, dg -> 
	        str += strRows
	        str += "<th>$rowCount</th>"
	        for (int r = 1; r < 8; r++) { 
			String dayBoxN = buttonLink("w$rowCount$r", O, "#49a37d", "")
			String dayBoxY = buttonLink("w$rowCount$r", X,   "#49a37d", "")
	        	str += (dg."$r") ? "<th>$dayBoxY</th>" : "<th>$dayBoxN</th>" 
	        }
	        // no delete button on Master dayGroup rows.
		  str += "<th>&nbsp;</th>"
		  strRows = "</tr><tr>" 
		  rowCount++
	}
	state.dayGroup.each {
	     k, dg -> 
	        str += strRows
	        str += "<th>$rowCount</th>"
	        for (int r = 1; r < 8; r++) { 
			String dayBoxN = buttonLink("w$rowCount$r", O, "#1A77C9", "")
			String dayBoxY = buttonLink("w$rowCount$r", X,   "#1A77C9", "")
	        	str += (dg."$r") ? "<th>$dayBoxY</th>" : "<th>$dayBoxN</th>" 
	        }
		  String remDayGroupBtn = buttonLink("rem$rowCount", "<i style=\"font-size:1.125rem\" class=\"material-icons he-bin\"></i>", "#1A77C9", "")
		  str += "<th>$remDayGroupBtn</th>"
		  strRows = "</tr><tr>" 
		  rowCount++
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

	state.dayGroupMerge.each {
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
		  dx = state.valves[dev.id].dayGroupMerge
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
		input "DoneTime$state.startTimeBtn",  "button", title: "  Done with time  ", width: 2, newLineAfter: true
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
		List vars = state.dayGroupMerge.keySet().collect() 

		input "DayGroup", "enum", title: "Sprinkler Group", submitOnChange: true, width: 4, options: vars, newLineAfter: true, multiple: true
		if(DayGroup) {
			state.valves[state.dayGrpBtn].dayGroup = DayGroup
			state.remove("dayGrpBtn")
			app.removeSetting("DayGroup")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
	}
}

def addDayGroup(evt = null) {
	dayGroupTemplate = ['1':false, '2':false, '3':false, '4':false, '5':false, '6':false, '7':false] // new rows are all empty

	dayGroupSize = state.dayGroup.keySet().size()
	s = dayGroupSize as int
	s++
	logDebug "adding another dayGroup map: $s"
	state.dayGroup += ["$s":dayGroupTemplate] 
	state.dayGroupSettings += ["$s":['duraTime':0, 'startTime':0]] // 
}


def remDayGroup(evt = null) {
	dayGroupSize = state.dayGroupMerge.keySet().size()
	dGTemp = [:]
	dGSetTemp =[:]
	localDGitem = (evt.toInteger() - state.dayGroupMaster.size().toInteger()).toString()
	logDebug "remove another dayGroup map: $dayGroupSize, $localDGitem"
	if (dayGroupSize >= 2) {
		state.dayGroup.each {
		    if (it.key < localDGitem) {
		        dGTemp[it.key]=it.value
		    }
		    else if (it.key > localDGitem) {
		        k = it.key as Integer
		        k--
		        dGTemp[k]=it.value
		    }
		}
		state.dayGroupSettings.each {
		    if (it.key < localDGitem) {
		        dGSetTemp[it.key]=it.value
		    }
		    else if (it.key > localDGitem) {
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
	atomicState.paused = atomicState.paused ? false : true
 logDebug "toggle: $atomicState.paused"
}


/*
	.containsKey("xx")
	.containsValue("xx")
*/


String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
	// only one button can be pressed, remove their states, since "btn" contains the only valid one.
	state.remove("duraTimeBtn") 
	state.remove("dayGrpBtn")
	state.remove("startTimeBtn")
	state.remove("dayGroupBtn")
	state.remove("doneTime")
	state.remove("eraseTime")
		app.removeSetting("StartTime") 
		app.removeSetting("DuraTime") 

	if(btn == "reset") resetTimers()
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
	if (settings.debugEnable) { log.debug msg }
}

def logWarn(msg) { 
	log.warn msg
}

def logInfo(msg) {
    if (settings.infoEnable) { log.info msg }
}


/*
-----------------------------------------------------------------------------
Standard handlers, and mode-change handler
-----------------------------------------------------------------------------
*/

def initialize() {		// unused?
	logDebug "initialize()"
	unsubscribe()
	init()
	update()
}


def installed() {
	logDebug "installed()"
	update()
}


def updated() {
	logDebug "updated()"
	update()
}


def uninstalled() {
	logDebug "uninstalled()"
}


def set2Month(monthIn) { 
	state.month2month = monthIn
	logInfo "MonthIn update from Parent."
}


def set2DayGroup(dayGroupIn) { 
	state.dayGroupMaster = dayGroupIn
	logInfo "DayGroup update from Parent."
}


def init() {
	logDebug "Init()"
	if(state.valves == null) state.valves = [:] 
	if(atomicState.paused == null) atomicState.paused = false
	if(state.inCycle == null) state.inCycle = false
	if(state.dayGroupSettings == null) state.dayGroupSettings = ['1':['duraTime':0, 'startTime':0]]
	if(state.dayGroup == null) state.dayGroup = ['1': ['1':true, '2':true, '3':true, '4':true, '5':true, '6':true, '7':true] ] // initial row
	if(state.month2month == null) state.month2month = [:]
	valves.each { dev -> if(!state.valves["$dev.id"]) { state.valves["$dev.id"] = ['dayGroup':['1']] } }
}


/*
---------------    UI code above here, background valve on/off code below.    --------------------------------------------------------------

   Whenever there is a change/update
-----------------------------------------------------------------------------
*/

def update() {
	def pauseText = "";
	if (settings.paused) {
	    pauseText = ' <span style="color: red;">(Paused)</span>'
	}
	if (settings.appLabel) {
	    app.updateLabel("${settings.appLabel}${pauseText}")
	} else {
	    app.updateLabel("Schedule${pauseText}")
	}
	logDebug "update() - paused=${paused}"
	def isActive = !paused 

	if (paused) {
	    return
	}

	scheduleNext()
}


def scheduleNext() {
	hasZero = state.dayGroupSettings.any { key, value -> value.any { it.value.toString() == "0" } } || state.valves?.isEmpty()
	if (hasZero) {
		log.warn "Please set Time and Duration"
		return
	}
	
	log.info "Checking $appName Schedule"
	Calendar calendar = Calendar.getInstance();
	def cronDay = calendar.get(Calendar.DAY_OF_WEEK);

	timings = buildTimings(cronDay)
	logDebug "Timings - DayOfWeek: $cronDay, $timings"
	if (!timings) {
		logWarn "Nothing scheduled for Today."
		return
	}

	unschedule(schedHandler)
	schedule('0 7 0 ? * *', scheduleNext) // reschedule the midnight run to schedule that day's work.
	Date date = new Date()
	String akaNow = date.format("HH:mm")
	hasSched = false

	for (timN in timings) {
	    logDebug "scheduleNext - DayOfWeek: $timN.key, HubTime: $akaNow, StartTime: $timN.startTime"
	    sk = timN.key
	    (sth, stm) = timN.startTime.split(':')
	    if (akaNow.replace(':', '') > timN.startTime.replace(':', '')) continue
	    hasSched = true
	    break;	// quit the for loop on a schedule of first startTime that's in the future.
	}
	logDebug "schedule('0 $stm $sth ? * *', schedHandler, [data: ['dKey': $sk]]), hasSched: $hasSched"
	if (hasSched) { schedule("0 ${stm} ${sth} ? * *", schedHandler, [data: ["dKey":"$sk"]])  }
}


/*
-----------------------------------------------------------------------------
Helper/Handler functions
-----------------------------------------------------------------------------
*/

def schedHandler(data) {
	unschedule(schedHandler)	// don't repeat this day after day.
	logInfo "Running $appName Schedule"
	cd = data["dKey"] as String
	valve2start = state.valves.findAll { it.value.dayGroup.contains(cd) }.keySet()
	logDebug "schedHandler: $cd, $state.dayGroupSettings, valve2start: $valve2start"

	vk = valve2start[0] as String
	if (vk != null) {
		valve2start = valve2start.tail()
	}

  // some valves need turning on for their duration.
	valves.find { it.id == "$vk" }?.open()
	logInfo "valve $vk open"
	state.inCycle = true
	atomicState.cycleStart = now()
	updateMyLabel()
    
	duraT = state.dayGroupSettings."$cd".duraTime
	currentMonth = new Date().format("M") 	// Get the current month as a number (1-12)
	percentage = month2month ? month2month[currentMonth].toDouble() / 100 : 1  // Lookup the percent in month2month or 1 
	dura = 60 * duraT * percentage		// duraTime is in minutes, runIn is in seconds
	duraSeconds = dura.toInteger()
    logDebug "runIn($duraSeconds, scheduleDurationHandler, [vKey: $vk, dS: $duraSeconds, dV: $valve2start])"

 	runIn(duraSeconds, scheduleDurationHandler, [data: [vKey: "$vk", dS: "$duraSeconds", dV: "$valve2start"]]) 
}


def scheduleDurationHandler(data) {
	unschedule(scheduleDurationHandler)	// don't repeat this day after day.
	cd = data.vKey as String
	duraSeconds = data.dS.toInteger()
	valve2start = data.dV as String
	logDebug "schedDurHandler: valveStop: $data.vKey, in Duration: $duraSeconds, next: $valve2start"

	// stop the valve and start the next, if any.
	valves.find { it.id == "$cd" }?.close()
	logInfo "Valve $cd close"

	//valves*.close()	// close all the valves
    
	// add an interstitial delay to allow the valves to close and recover before opening next.


	if (valve2start != '[]') {
		valve2start = valve2start.replaceAll(/\[|\]/, '').split(',').collect { it.trim().toInteger() }
		vk = valve2start[0] as String
		if (vk != null) {
			valve2start = valve2start.tail()
			valves.find { it.id == "$vk" }?.open()
			logInfo "valve $vk open"

			runIn(duraSeconds, scheduleDurationHandler, [data: [vKey: "$vk", dS: "$duraSeconds", dV: "$valve2start"]])
		}
	} else {
		state.inCycle = false
		atomicState.cycleEnd = now()
		runIn(30, scheduleNext)			// find and then schedule the next startTime for today
		updateMyLabel()
	}
}


def buildTimings(cronDayOf) {
	def aWeek = [1:'7', 2:'1', 3:'2', 4:'3', 5:'4', 6:'5', 7:'6']
	// cronDayOf week is 1-7 where 1 = sunday and 7 = saturday. BUT this app uses 1 as Monday, sunday is 7
	def result = state.dayGroupMerge.findAll { key, value -> value[aWeek[cronDayOf]] == true }.keySet()
	// timings = result.collectEntries { key -> [(key): state.dayGrouptimings[key]]
	def results = result.collect { key -> [key: key, duraTime: state.dayGroupSettings[key]?.duraTime, startTime: state.dayGroupSettings[key]?.startTime]}.findAll { it.startTime != null }.sort { it.startTime } // Sort by startTime
	// [[key:2, duraTime:5.0, startTime:06:00]]
}

void updateMyLabel() {
	String flag = '<span '
	
	// Display Ecobee connection status as part of the label...
	String myLabel = atomicState.appDisplayName
	if ((myLabel == null) || !app.label.startsWith(myLabel)) {
		myLabel = app.label ?: app.name
		if (!myLabel.contains(flag)) atomicState.appDisplayName = myLabel
	} 
	if (myLabel.contains(flag)) {
		// strip off any connection status tag
		myLabel = myLabel.substring(0, myLabel.indexOf(flag))
		atomicState.appDisplayName = myLabel
	}
	String newLabel
	if (atomicState.paused) {
		newLabel = myLabel + '<span style="color:Crimson"> (paused)</span>'
	} else if (atomicState.inCycle) {
		String beganAt = atomicState.cycleStart ? "started " + fixDateTimeString(atomicState.cycleStart) : 'running'
		newLabel = myLabel + "<span style=\"color:Green\"> (${beganAt})</span>"
	} else if ((atomicState.inCycle != null) && (atomicState.inCycle == false)) {
		String endedAt = atomicState.cycleEnd ? "finished " + fixDateTimeString(atomicState.cycleEnd) : 'idle'
		newLabel = myLabel + "<span style=\"color:Green\"> (${endedAt})</span>"
	} else {
		newLabel = myLabel
	}
	if (app.label != newLabel) app.updateLabel(newLabel)
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
	if (debugOutput) { logDebug "banner: ${resultStr}"}
	return resultStr
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

String menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}
def getThisCopyright(){"&copy; 2023 C Steele"}
