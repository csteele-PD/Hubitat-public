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
 * csteele: v1.0.5	Don't tell the children about rain or temperature devices that don't exist. 
 * csteele: v1.0.4	Added child switch option
 *				 Make collection of outdoorRainDevice attributes to remove duplicates
 * csteele: v1.0.3	Initial Release (end Beta)
 * csteele: v1.0.2	Add Over Temp and Rain Detection to be used as a Conditional
 * csteele: v1.0.1	Send month2month and dayGroup to child Apps
 * csteele: v1.0.0	Inspired by Matt Hammond's Lighting Schedules
 *                	 Converted to capability.valve from switch 
 *
 */
 
	public static String version()      {  return "v1.0.5"  }

definition(
	name: "Sprinkler Schedule Manager",
	namespace: "csteele",
	author: "C Steele",
	description: "Controls switches to a timing schedule",
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
			  section(menuHeader("Advanced Options Page"))
			  {
			  	href "advancedPage", title: "Advanced Options", required: false
			  }
			  // Send the current Maps to each Child, exactly like an Update-from-Done would do.
			  childApps.each {child ->
			  	child.set2Month(state.month2month) 
			  	child.set2DayGroup(state.dayGroup) 
				if (outdoorTempDevice) { child.setOutdoorTemp(outdoorTempDevice, maxOutdoorTemp) }
				if (outdoorRainDevice) { child.setOutdoorRain(outdoorRainDevice, state.currentRainAttribute) }
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
	if(state.month2month == null) state.month2month = ["1":"100", "2":"100", "3":"100", "4":"100", "5":"100", "6":"100", "7":"100", "8":"100", "9":"100", "10":"100", "11":"100", "12":"100"]
	
	String str = "<i>Assume that Valve Timing is 100% and adjust that timing by the percentages, monthly. Valve Duration is reduced to the percentage defined for the month in which it runs.<br>"
	str += "Example: If a Valve is set to be 15 minutes, and the current month has a value of 30%, the valve will run for 5 minutes each day of the month.</i><p>"
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
		String dgK = state.dayGroupBtn.substring(0, 1); // dayGroupBtn Key (row)
		String dgI = state.dayGroupBtn.substring(1);   // dayGroupBtn value (mon-sun)

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
      	        title: "<i>Enter the Maximum temperature beyond which conditional Timetables will be skipped.</i>",
      	        defaultValue: maxOutdoorTemp,
      	        multiple: false,
      	        required: false,
      	        submitOnChange: true
}


def selectRainDevice() {

	paragraph "\n<b>Rain Device Selection</b>"
			input "outdoorRainDevice", "capability.waterSensor",
      	        title: "Select which device?",
      	        multiple: false,
      	        required: false,
      	        submitOnChange: true

	if (outdoorRainDevice) {
		def vars = [:]
		def c1=1
		atts = outdoorRainDevice?.supportedAttributes.collect { it?.toString()?.toLowerCase() }.toSet().sort()
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
Display level handlers
-----------------------------------------------------------------------------
*/

def editMonths() {
	if (state.dispMonthBtn) {
		input "monthPercentage", "decimal", title: "Monthly Percentage", submitOnChange: true, width: 4, range: "1..100", defaultValue: state.month2month[state.dispMonthBtn], newLineAfter: true
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
	logInfo "Installed with settings."//": ${settings}"
	initialize()
}


def updated() {
	logInfo "Updated with settings."//": ${settings}"
	childApps.each {child ->
		child.set2Month(state.month2month) 
		child.set2DayGroup(state.dayGroup) 
		if (outdoorTempDevice) { child.setOutdoorTemp(outdoorTempDevice, maxOutdoorTemp) }
		if (outdoorRainDevice) { child.setOutdoorRain(outdoorRainDevice, state.currentRainAttribute) }
	}
}


def initialize() {
	// nothing needed here, since the child apps will handle preferences/subscriptions
	// this just logs some messages for demo/information purposes
	logInfo "there are ${childApps.size()} child smartapps"
	childApps.each {child ->
		logInfo "child app: ${child.label}"
	}
}


/*
-----------------------------------------------------------------------------
Helper/Handler functions
-----------------------------------------------------------------------------
*/

// called by each child when it wants an update of these values.
def componentInitialize(cd) { 
	if (!advancedOption) return

	cd.set2Month(state.month2month) 
	cd.set2DayGroup(state.dayGroup) 
	if (outdoorTempDevice) { child.setOutdoorTemp(outdoorTempDevice, maxOutdoorTemp) }
	if (outdoorRainDevice) { child.setOutdoorRain(outdoorRainDevice, state.currentRainAttribute) }
}


def installCheck(){         
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to install '${app.label}'"}
	}
	else{
		logInfo "$app.name is Installed Correctly"
	}
}


def logDebug(msg) { // allows either log.debug or logDebug like the Child code.
	log.debug msg
}

def logInfo(msg) { // allows either log.info or logInfo like the Child code.
	log.info msg
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
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	}
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}


void appButtonHandler(btn) {
	state.remove("dispMonthBtn")
	state.remove("dayGroupBtn")
	app.removeSetting("monthPercentage") 
	if ( btn.startsWith("m"))  state.dispMonthBtn = btn.minus("m")
	else if ( btn == "addDGBtn")            addDayGroup()
	else if ( btn.startsWith("rem")      )  remDayGroup(btn.minus("rem")) 
	else if ( btn.startsWith("w")        )  state.dayGroupBtn = btn.minus("w")
}


String menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}
def getThisCopyright(){"&copy; 2023 C Steele"}
