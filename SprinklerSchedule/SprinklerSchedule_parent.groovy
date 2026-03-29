/* 
=============================================================================
Hubitat Elevation Application
Sprinkler Scheduler (parent application) Sprinkler Schedule Manager

    Inspiration: Lighting Schedules https://github.com/matt-hammond-001/hubitat-code
    Inspiration: github example from Hubitat of lightsUsage.groovy
    This fork: Sprinkler Schedules https://github.com/csteele-PD/Hubitat-public/tree/master/SprinklerSchedule

-----------------------------------------------------------------------------
This code is licensed as follows:

	Portions:
	 	Copyright (c) 2022 Hubitat, Inc.  All Rights Reserved Bruce Ravenel 

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
 * csteele: v1.0.12	Add Overlap Check
 * csteele: v1.0.11	Add Soil Type
 * csteele: v1.0.10	cosmetic remove Horz Rule
 * csteele: v1.0.9	clean up unused methods: componentInitialize()
 *				 refactored logging into using closures.
 * csteele: v1.0.8	initialize state.month2month on child creation.
 * csteele: v1.0.7	editMonths() defaultValue pre-fill corrected.
 * csteele: v1.0.6	Added multiple Rain Sensors and be integrated.
 * csteele: v1.0.5	Don't tell the children about rain or temperature devices that don't exist. 
 * csteele: v1.0.4	Added child switch option
 *				 Make collection of rainDeviceOutdoor attributes to remove duplicates
 * csteele: v1.0.3	Initial Release (end Beta)
 * csteele: v1.0.2	Add Over Temp and Rain Detection to be used as a Conditional
 * csteele: v1.0.1	Send month2month and dayGroup to child Apps
 * csteele: v1.0.0	Inspired by Matt Hammond's Lighting Schedules
 *                	 Converted to capability.valve from switch 
 *
 */

	public static String version()      {  return "v1.0.12"  }

definition(
	name: "Sprinkler Schedule Manager",
	namespace: "csteele",
	author: "C Steele",
	description: "Controls valves or switches to a timing schedule",
	importUrl: "https://raw.githubusercontent.com/csteele-PD/Hubitat-public/refs/heads/master/SprinklerSchedule/SprinklerSchedule_parent.groovy",
	documentationLink: "https://www.hubitatcommunity.com/QuikRef/sprinklerScheduleManagerInfo/index.html",
	singleInstance: true,
	iconUrl: "",
	iconX2Url: "",
)


preferences {
	page(name: "mainPage")
	page(name: "advancedPage")

}


def mainPage() {
	if (state.overlapHide == null) state.overlapHide = true
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: true, submitOnChange: true) {
		displayHeader()
		state.appInstalled = app.getInstallationState() 
		if (state.appInstalled != 'COMPLETE') return installCheck()

		section() {
      	      app (name: "sprinklerTimetable",
      	           appName: "Sprinkler Valve Timetable",
      	           namespace: "csteele",
      	           title: "Create New Sprinkler VALVE Schedule",
      	           multiple: true)
		}
	
		section() {
      	      app (name: "sprinklerTimetable",
      	           appName: "Sprinkler Switch Timetable",
      	           namespace: "csteele",
      	           title: "Create New Sprinkler SWITCH Schedule",
      	           multiple: true)
		}
	
		section() {
		  	input "advancedOption", "bool", title: "Display Options that become common to all Sprinkler Timetables.", required: false, defaultValue: false, submitOnChange: true
			input name: "quickref", type: "hidden", title:"<a href='https://www.hubitatcommunity.com/QuikRef/sprinklerScheduleManagerInfo/index.html' target='_blank'>Quick Reference ${version()}</a>"
		}

		if (advancedOption) {
			section(menuHeader("Advanced Options Page")) {
				advDescription = 'Valve Timing by Month, Master Groups, Temperature and Rain Devices'
				href "advancedPage", title: "Advanced Options", required: false, description: advDescription, state: "complete"
			}
			// Send the current Maps to each Child, exactly like an Update-from-Done would do.
			childSetStates()
		}

		section("Overlap Checker", hideable:true, hidden: state.overlapHide) {
		log.debug "Overlap: $state.overlapHide"
			input "overlapCheckBtn", "button", title: "Run Overlap Check", submitOnChange: true
			if (state.lastOverlapCheck) {
				paragraph "Last checked: ${state.lastOverlapCheck}"
			}
			if (state.overlapReport) {
				paragraph state.overlapReport
			}
		}
	}
}


/*
-----------------------------------------------------------------------------
Advanced Page handlers
-----------------------------------------------------------------------------
*/

def advancedPage() {
	def subTitle = getFormat("title", "Sprinkler Schedules")
	dynamicPage(name: "advancedPage", title: subTitle, uninstall: false, install: false) {
		displaySubHeader()
		
		section(menuHeader("<b>Master: Adjust valve timing by Month</b>"))
		{
			paragraph displayMonths()		// display Monthly percentages
			  editMonths()
		}

		section(menuHeader("<b>Master: Select Days into Groups</b>"))
		{
			paragraph "<i>Groups defined here will appear as un-editable groups in every Timetable (child). </i>"
			paragraph displayDayGroups()		// display day-of-week groups
		}

		section(menuHeader("<b>Master: Select Temperature Device</b>"))
		{
			paragraph "<i>Choose a temperature device and set the maximum value. Timetables can be conditional on the temperature exceeding the maximum.</i>"
			  selectTemperatureDevice()		// are there days that are very hot ?
		}

		section(menuHeader("<b>Master: Select Rain detection Device</b>"))
		{
			paragraph "<i>Choose a Water Sensor device that will supply rain data. Timetables can be conditional on the rain values.</i>"
			  selectRainDevice()		// are there days that are wet enough ?
		}
	}
}


String displayMonths() {	// display Monthly percentages
	if (state.month2month == null) state.month2month = ["1":"100", "2":"100", "3":"100", "4":"100", "5":"100", "6":"100", "7":"100", "8":"100", "9":"100", "10":"100", "11":"100", "12":"100"]
	
	String str = "<i>Assume that Valve Duration (as set in the Child) is 100% and adjust that timing by these percentages, monthly. Valve Duration is reduced to the percentage defined for the month in which it runs. (20 seconds is the valve's minimum duration.) "
	str += "Example: If a Valve is set to be 15 minutes, and the current month has a value of 30%, the valve will run for 5 minutes each day of the month its scheduled to run.</i><p>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'>" +
		"<th>Jan</th>" +
		"<th>Feb</th>" +
		"<th>Mar</th>" +
		"<th>Apr</th>" +
		"<th>May</th>" +
		"<th>Jun</th>" +
		"<th>Jul</th>" +
		"<th>Aug</th>" +
		"<th>Sep</th>" +
		"<th>Oct</th>" +
		"<th>Nov</th>" +
		"<th>Dec</th>" +
		"</tr></thead>"

	str += "<tr style='color:black'border = 1>" 
	state.month2month.keySet().sort { it.toInteger() }.each { key ->
		String mCol = buttonLink("m${key}", "${state.month2month[key]}", "purple")
		str += "<th>$mCol</th>"
	}
	str += "</tr></table></div>"
	str
}


String displayDayGroups() {	// display day-of-week groups
	if(state.dayGroup == null) state.dayGroup = ['1': ['1':true, '2':true, '3':true, '4':true, '5':true, '6':true, '7':true, "s": "P", "name": "", "ot": false, "ra": false, "duraTime": null, "startTime": null ] ] // initial row
	if(state.dayGroupBtn) {
		String dgK = state.dayGroupBtn.substring(0, 1) // dayGroupBtn Key (row)
		String dgI = state.dayGroupBtn.substring(1)    // dayGroupBtn value (mon-sun)

		state.dayGroup."$dgK"."$dgI" = state.dayGroup."$dgK"."$dgI" ? false : true // toggle the state.
		state.remove("dayGroupBtn") // only once 
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
	String addDayGroupBtn = buttonLink("addDGBtn", Plus, "#49a37d", "")

	strRows = ""
	state.dayGroup.each {
	     k, dg -> 
	        str += strRows
	        str += "<th>$k</th>"
	        for (int r = 1; r < 8; r++) { 
			String dayBoxN = buttonLink("w$k$r", O, "#49a37d", "")
			String dayBoxY = buttonLink("w$k$r", X,   "#49a37d", "")
	        	str += (dg."$r") ? "<th>$dayBoxY</th>" : "<th>$dayBoxN</th>" 
	        }
		  String remDayGroupBtn = buttonLink("rem$k", "<i style=\"font-size:1.125rem\" class=\"material-icons he-bin\"></i>", "#49a37d", "")
		  str += "<th>$remDayGroupBtn</th>"
		  strRows = "</tr><tr>" 
	}
	str += "</tr><tr>"
	str += "<th>$addDayGroupBtn</th><th colspan=4> <- Add new Day Group</th>"
	str += "</tr></table></div>"
	str
}


def selectTemperatureDevice() {
	paragraph "\n<b>Temperature Device Select</b>"
			input "outdoorTempDevice", "capability.temperatureMeasurement",
      	        title: "Select which device?",
      	        multiple: false,
      	        required: false,
      	        submitOnChange: true

	paragraph "\n<b>Maximum Temperature</b>"
			input "maxOutdoorTemp", "number",
      	        title: "<i>Enter the Maximum temperature, beyond which, conditional Timetables will be invoked.</i>",
      	        defaultValue: maxOutdoorTemp,
      	        multiple: false,
      	        required: false,
      	        submitOnChange: true
}


def selectRainDevice() {
	paragraph "\n<b>Rain Device Selection</b>"
			input "rainDeviceOutdoor", "capability.waterSensor",
      	        title: "Select which device?",
      	        multiple: true,
      	        required: false,
      	        submitOnChange: true

	if (rainDeviceOutdoor) {
		def vars = [:]
		def c1=1
		atts = rainDeviceOutdoor?.collectMany { c2 -> 
			c2.supportedAttributes.collect { it?.toString()?.toLowerCase() } }?.toSet()?.sort()

		atts.each { v ->
			vars[c1++] = "$v"
		}

		attributeList = vars
		paragraph "\n<b>Rain Attribute</b>"
			input "selectRainAttribute", "enum", options: attributeList,
			  title: "<i>Which Attribute indicates there was enough rain to skip a cycle?</i>",
			  defaultValue: selectRainAttribute,
			  multiple: false,
			  required: false,
			  submitOnChange: true

		state.currentRainAttribute = attributeList[selectRainAttribute as Integer]
	}	
}


/*
-----------------------------------------------------------------------------
Soil handlers
-----------------------------------------------------------------------------
*/

def getSoilTypeFromUSDA() {
	state.geo=state.geo?:[:]
	if(state.geo.lat==null||state.geo.lon==null) {
		state.geo.lat=location?.latitude
		state.geo.lon=location?.longitude
		logInfo {"Using lat/lon from hub location"}
	}
	def lat=state.geo?.lat
	def lon=state.geo?.lon
	if(!lat||!lon){
		state.usdaSoilMsg="Hub coordinates unavailable."
		logWarn {"${state.usdaSoilMsg}"}
		return
	}
	def sda=getSoilData(lat,lon)
	if(!sda||!sda.textureMapped){
		state.usdaSoilMsg="No USDA data returned for (${lat},${lon})"
		logWarn {"${state.usdaSoilMsg}"}
		return
	}
	def mapped=sda.textureMapped
	def raw=sda.textureRaw?:"n/a"
	def hyd=sda.hydgrp?:"n/a"
	def awc=sda.awc?:etAwcForSoil(mapped)
	state.defaultSoilType=mapped
	state.defaultAwc=awc
	state.defaultHydgrp=hyd
	state.usdaSoilMsg="USDA soil detected: ${mapped} (USDA: ${raw}, Group ${hyd}, AWC=${awc})"
	logInfo {"Soil: ${state.usdaSoilMsg}"}
}


def getSoilData(lat, lon) {
	def d=0.0001
	def poly="POLYGON((${lon-d} ${lat-d},${lon+d} ${lat-d},${lon+d} ${lat+d},${lon-d} ${lat+d},${lon-d} ${lat-d}))"
	def query = """
		SELECT TOP 1
		  mapunit.musym,
		  mapunit.muname,
		  component.compname,
		  component.hydgrp,
		  chtexturegrp.texdesc
		FROM mapunit
		  INNER JOIN component ON component.mukey = mapunit.mukey
		  INNER JOIN chorizon ON chorizon.cokey = component.cokey
		  INNER JOIN chtexturegrp ON chtexturegrp.chkey = chorizon.chkey
		WHERE mapunit.mukey IN (
		  SELECT mukey FROM SDA_Get_Mukey_from_intersection_with_WktWgs84('${poly}')
		)
	"""
	def params=[uri: "https://sdmdataaccess.nrcs.usda.gov/tabular/post.rest",
		contentType: "application/x-www-form-urlencoded",
		body: [query: query],timeout: 20]

	try {
		def respText=''
		httpPost(params){
			r->
			   def t=r?.data
			   def tn=t?.class?.name?:''
			respText=(tn.contains('InputStream')||tn.contains('Reader'))?t?.getText('UTF-8'):t?.toString()
		}
		if (!respText) {
			logWarn {"getSoilData(): empty SDA response for (${lat},${lon})"}
			return null
		}
		// Trim garbage and extract only the <Table> element
		respText=respText.substring(respText.indexOf("<"))
		def m=respText=~ /(?s)<Table>(.*?)<\/Table>/
		if (!m.find()) {
			logWarn {"getSoilData(): no <Table> block in SDA response"}
			return null
		}
		def tableXML="<Table>${m.group(1)}</Table>"
		tableXML=tableXML.replaceAll(/&(?![a-zA-Z]+;|#\d+;)/, '&amp;')
		def xml=new XmlSlurper(false, false).parseText(tableXML)
		def musym=xml?.musym?.text()?:''
		def muname=xml?.muname?.text()?:''
		def compname=xml?.compname?.text()?:''
		def hydgrp=xml?.hydgrp?.text()?:''
		def texdesc=xml?.texdesc?.text()?:''
		if (!texdesc&&!muname){
			logWarn {"getSoilData(): no soil data for (${lat},${lon})"}
			return null
		}
		return [musym:musym,muname:muname,compname:compname,hydgrp:hydgrp,textureRaw:texdesc,textureMapped:mapSoilTextureToClass(texdesc)]
	} catch(e) { logWarn {"getSoilData() SDA error: ${e.message}"}; return null }
}


def mapSoilTextureToClass(String texture){
    if(!texture)return"Loam"
    def t=texture.toLowerCase().trim()
    switch(true){
        case{t=="sand"||t.contains("coarse sand")||t.contains("fine sand")}:return"Sand"
        case{t.contains("loamy sand")||t.contains("sandy loam")||t.contains("fine sandy loam")}:return"Loamy Sand"
        case{t.contains("loam")||t.contains("silt loam")||t.contains("silty clay loam")||t.contains("sandy clay loam")}:return"Loam"
        case{t.contains("clay loam")||t.contains("silty clay")||t.contains("sandy clay")}:return"Clay Loam"
        case{t=="clay"||t.contains("heavy clay")||t.contains("vertisol")}:return"Clay"
        default:return"Loam"
    }
}


/*
-----------------------------------------------------------------------------
Display level handlers
-----------------------------------------------------------------------------
*/

def editMonths() {
	if (state.dispMonthBtn) {
		input "monthPercentage", "decimal", title: "Monthly Percentage", submitOnChange: true, width: 4, range: "1..100", defaultValue: state.month2month[state.dispMonthBtn]
		if(monthPercentage) {
			state.month2month[state.dispMonthBtn] = monthPercentage
			state.remove("dispMonthBtn")
			app.removeSetting("monthPercentage")
			paragraph "<script>{changeSubmit(this)}</script>"
		}
	}
}


def addDayGroup(evt = null) {
    def dayGroupTemplate = [
        '1': false, '2': false, '3': false, '4': false, '5': false, '6': false, '7': false, 
        "s": "P", "name": "", "ot": false, "ra": false, "duraTime": null, "startTime": null
    ] // new rows are all empty

    def dayGroupSize = state.dayGroup.size() // More efficient
    def newIndex = (dayGroupSize + 1).toString() // Ensures key consistency

    state.dayGroup[newIndex] = dayGroupTemplate.clone() // Clone to avoid reference issues
}


def remDayGroup(evt = null) {  	// remove a Local dayGroup & dayGroupSettings
	dayGroupSize = (state.dayGroup ?: [:]).keySet().size()
	if (dayGroupSize > 1) {
		// Determine the key to delete
		keyToDelete = (evt.toInteger() - (state.dayGroupMaster ?: [:]).size()).toString()		
		if (state.dayGroup.containsKey(keyToDelete)) { state.dayGroup.remove(keyToDelete) } 
		// Re-map keys to be sequential
		def dayGrpReOrder = [:]
		def counter = 1
		state.dayGroup.sort { it.key.toInteger() }.each { k, v ->
		    dayGrpReOrder["${counter++}"] = v
		}
		state.dayGroup = dayGrpReOrder
	}
}


/*
-----------------------------------------------------------------------------
Standard Hubitat App methods
-----------------------------------------------------------------------------
*/

def installed() {
	logInfo {"Installed with settings."}//": ${settings}"}
	initialize()
}


def updated() {
	logInfo {"Updated with settings."}//": ${settings}"}
	childSetStates()
	state.overlapHide = true
}


def initialize() {
	// nothing needed here, since the child apps will handle preferences/subscriptions
	// this just logs some messages for demo/information purposes
	logInfo {"there are ${childApps.size()} child smartapps"}
	childApps.each {child ->
		logInfo {"child app: ${child.label}"}
	}
}


/*
-----------------------------------------------------------------------------
Helper/Handler functions
-----------------------------------------------------------------------------
*/
def installCheck(){      
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to install '${app.label}'"}
	}
	else{
		logInfo {"$app.name is Installed Correctly"}
	}
}

def getFormat(type, myText=""){            // Modified from @Stephack Code
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
	if(type == "subTitle") return "<p style='color:#1A77C9;font-weight: bold; font-size: 1.4em;'>${myText}</p>"
}

def displayHeader() {
	section (getFormat("title", "Sprinkler Schedules")) {
		paragraph "<div style='color:#1A77C9;text-align:right;font-weight:small;font-size:9px;'>Developed by: C Steele, Matt Hammond <br/>Current Version: ${version()} -  ${thisCopyright}</div>"
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	}
}

def displaySubHeader() {
	section (getFormat("subTitle", "Advanced Options")) {
		paragraph "<div style='color:#1A77C9;text-align:right;font-weight:small;font-size:9px;'>Developed by: C Steele, Matt Hammond <br/>Current Version: ${version()} -  ${thisCopyright}</div>"
	}
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}


void appButtonHandler(btn) {
	state.remove("dispMonthBtn")
	state.remove("dayGroupBtn")
	app.removeSetting("monthPercentage") // duplicate but required.
	if ( btn.startsWith("m"))  state.dispMonthBtn = btn.minus("m")
	else if ( btn == "addDGBtn")            addDayGroup()
	else if ( btn.startsWith("rem")      )  remDayGroup(btn.minus("rem")) 
	else if ( btn.startsWith("w")        )  state.dayGroupBtn = btn.minus("w")
	else if ( btn == "overlapCheckBtn"   )  runOverlapCheck()
}


void childSetStates() {
	getSoilTypeFromUSDA()
	childApps.each {child ->
		child.set2Month(state.month2month) 
		child.set2DayGroup(state.dayGroup) 
		child.setSoilType(state.defaultSoilType) 
		if (outdoorTempDevice) { child.setOutdoorTemp(outdoorTempDevice, maxOutdoorTemp) }
		if (rainDeviceOutdoor) { child.setOutdoorRain(rainDeviceOutdoor, state.currentRainAttribute) }
	}
}


def runOverlapCheck() {
	state.overlapHide = false
	def dayNames = [1:"Mon", 2:"Tue", 3:"Wed", 4:"Thu", 5:"Fri", 6:"Sat", 7:"Sun"]
	def allWindows = []
	def missing = []

	childApps.each { child ->
		try {
			def w = child.getScheduleWindows() ?: []
			allWindows.addAll(w)
		} catch (MissingMethodException e) {
			missing << normalizeLabel(child?.label)
		}
	}

	def report = new StringBuilder()
	def nowStr = new Date().format("yyyy-MM-dd HH:mm")
	state.lastOverlapCheck = nowStr
	report << "<b>Overlap Check</b> (${nowStr})<br>"

	if (missing) {
		report << "Warning: missing schedule data from: ${missing.join(', ')}<br>"
	}

	if (!allWindows) {
		report << "No schedules found to analyze.<br>"
		state.overlapReport = report.toString()
		return
	}

	def byDay = [:].withDefault { [] }
	def rollover = []

	allWindows.each { w ->
		def startSec = w.startSec as Integer
		def endSec = startSec + (w.totalSec as Integer)
		def dayIndex = w.dayIndex as Integer
		if (endSec <= 86400) {
			byDay[dayIndex] << w + [endSec: endSec]
		} else {
			rollover << w
			byDay[dayIndex] << w + [endSec: 86400, rollover: true]
			def nextDay = (dayIndex % 7) + 1
			byDay[nextDay] << w + [startSec: 0, endSec: endSec - 86400, rollover: true]
		}
	}

	if (rollover) {
		report << "Warning: ${rollover.size()} schedule(s) cross midnight and were split for analysis.<br>"
	}

	def anyOverlap = false
	(1..7).each { dayIdx ->
		def dayList = byDay[dayIdx]?.sort { a, b ->
			a.startSec <=> b.startSec ?: (a.timeline <=> b.timeline)
		} ?: []
		if (!dayList) return

		def active = []
		def dayIssues = []

		dayList.each { w ->
			active = active.findAll { it.endSec > w.startSec }
			if (active) {
				def overlapsWith = active.collect()
				def latestEnd = overlapsWith.collect { it.endSec }.max()
				def shiftSec = Math.max(0, latestEnd - w.startSec)
				dayIssues << [win: w, overlaps: overlapsWith, shiftSec: shiftSec]
			}
			active << w
		}

		if (dayIssues) {
			anyOverlap = true
			report << "<br><b>${dayNames[dayIdx]}</b><br>"
			dayIssues.each { issue ->
				def w = issue.win
				def overlapNames = issue.overlaps.collect { ow ->
					"${ow.timeline} (DG ${ow.dayGroup} @ ${formatTime(ow.startSec)})"
				}
				def shiftStr = issue.shiftSec > 0 ? formatDuration(issue.shiftSec) : "0m"
				report << "${w.timeline} (DG ${w.dayGroup} @ ${formatTime(w.startSec)}, total ${formatDuration(w.totalSec)}, valves ${w.valveCount}) overlaps with ${overlapNames.join(' and ')}. "
				report << "Moving ${w.timeline} (DG ${w.dayGroup}) forward ${shiftStr} would resolve.<br>"
			}
		}
	}

	if (!anyOverlap) {
		report << "No overlaps found across all days.<br>"
	}

	state.overlapReport = report.toString()
}

/*
-----------------------------------------------------------------------------
Logging output (Parent has no logging options)
-----------------------------------------------------------------------------
*/

void logDebug(Closure msg) {
    log.debug "${msg()}"
}

void logInfo(Closure msg) {
    log.info "${msg()}"
}

void logWarn(Closure msg) {
    log.warn "${msg()}"
}

private String formatTime(Integer seconds) {
	int s = seconds ?: 0
	int h = (int)(s / 3600)
	int m = (int)((s % 3600) / 60)
	int sec = s % 60
	if (sec == 0) return String.format("%02d:%02d", h, m)
	return String.format("%02d:%02d:%02d", h, m, sec)
}

private String formatDuration(Integer seconds) {
	int s = seconds ?: 0
	int m = (int)(s / 60)
	int sec = s % 60
	if (sec == 0) return "${m}m"
	return "${m}m ${sec}s"
}

private String normalizeLabel(String label) {
	if (!label) return ""
	def flag = '<span '
	return label.contains(flag) ? label.substring(0, label.indexOf(flag)) : label
}


BigDecimal etAwcForSoil(String soil){switch(soil?.trim()){case"Sand":return 0.05G;case"Loamy Sand":return 0.07G;case"Sandy Loam":return 0.10G;case"Loam":return 0.17G;case"Clay Loam":return 0.20G;case"Silty Clay":return 0.18G;case"Clay":return 0.21G;default:return 0.17G}}
String menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}
def getThisCopyright(){"&copy; 2023 C Steele"}
