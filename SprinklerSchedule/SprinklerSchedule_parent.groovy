/* 
=============================================================================
Hubitat Elevation Application
Sprinkler Scheduler (parent application)

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
 * csteele: v1.0.1	?
 * csteele: v1.0.0	Converted from Matt Hammond's Lighting Schedules
 *                	 Converted to capability.valve from switch 
 *
 */
 
	public static String version()      {  return "v1.0.1"  }


definition(
	name: "Sprinkler Schedule Manager",
	namespace: "csteele",
	author: "C Steele, Matt Hammond",
	description: "Controls switches to a timing schedule",
	documentationLink: "https://github.com//README.md",
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
        section {
            app (name: "sprinklerTimetable",
                 appName: "Sprinkler Valve Timetable",
                 namespace: "csteele",
                 title: "Create New Sprinkler Schedule",
                 multiple: true)
        }
	  section() {
	  	input "advancedOption", "bool", title: "Display Options that become common to all Sprinkler Valve Timetables.", required: false, defaultValue: false, submitOnChange: true
	  }
	  if (advancedOption) {
		  section(menuHeader("Advanced Options Page"))
		  {
		  	href "advancedPage", title: "Advanced Options", required: false
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
	dynamicPage(name: "advancedPage", title: "<i>Advanced Options Page</i>", uninstall: false, install: false)
	{
		section(menuHeader("<b>Adjust valve timing by Month</b>"))
		{
			paragraph displayMonths()		// display Monthly percentages
			paragraph editMonths()
		}
		section(menuHeader("<b>Master: Select Days into Groups</b>"))
		{
			paragraph displayDayGroups()		// display day-of-week groups
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
	if(state.dayGroup == null) state.dayGroup = ['1': ['1':true, '2':true, '3':true, '4':true, '5':true, '6':true, '7':true] ] // initial row
	if(state.dayGroupBtn) {
		log.debug "displayDayGroups Item: $state.dayGroupBtn"
		String dgK = state.dayGroupBtn.substring(0, 1); // dayGroupBtn Key
		String dgI = state.dayGroupBtn.substring(1);   // dayGroupBtn value (mon-sun)

		state.dayGroup."$dgK"."$dgI" = state.dayGroup."$dgK"."$dgI" ? false : true // toggle the state.
		state.remove("dayGroupBtn") // only once 
		log.debug "displayDayGroups Item: $dgK.$dgI"
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
		}
	}
}


/*
-----------------------------------------------------------------------------
Standard handlers, and mode-change handler
-----------------------------------------------------------------------------
*/

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}


def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}


def initialize() {
	// nothing needed here, since the child apps will handle preferences/subscriptions
	// this just logs some messages for demo/information purposes
	log.debug "there are ${childApps.size()} child smartapps"
	childApps.each {child ->
	    log.debug "child app: ${child.label}"
	}
}


/*
-----------------------------------------------------------------------------
Helper/Handler functions
-----------------------------------------------------------------------------
*/

def getFormat(type, myText=""){            // Modified from @Stephack Code
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def displayHeader() {
	section (getFormat("title", "Sprinkler Schedules")) {
		paragraph "<div style='color:#1A77C9;text-align:right;font-weight:small;font-size:9px;'>Developed by: C Steele, Matt Hammond <br/>Current Version: ${version()} -  ${thisCopyright}</div>"
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	}
}


String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}


void appButtonHandler(btn) {
	state.remove("dispMonthBtn")
	app.removeSetting("monthPercentage") 
	if ( btn.startsWith("m"))  state.dispMonthBtn = btn.minus("m")
}


String menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}
def getThisCopyright(){"&copy; 2023 C Steele"}
