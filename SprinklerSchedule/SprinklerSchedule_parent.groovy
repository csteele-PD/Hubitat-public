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
 * csteele: v1.0.0	Converted from Matt Hammond's Lighting Schedules
 *                	 Converted to capability.valve from switch 
 *
 */
 
	public static String version()      {  return "v1.0.0"  }

	import groovy.transform.Field

definition(
	name: "Sprinkler Schedules",
	namespace: "csteele",
	author: "C Steele, Matt Hammond",
	description: "Controls switches to a timing schedule",
	documentationLink: "https://github.com//README.md",
	singleInstance: true,
	iconUrl: "",
	iconX2Url: "",
)

preferences {
	page(name: "mainPage", title: "", install: true, uninstall: true,submitOnChange: true) {
	  displayHeader()
        section {
            app (name: "sprinklerSchedule",
                 appName: "Sprinkler Schedule",
                 namespace: "csteele",
                 title: "Create New Sprinkler Schedule",
                 multiple: true)
        }
    }
}

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

def getFormat(type, myText=""){            // Modified from @Stephack Code
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def displayHeader() {
	section (getFormat("title", "Sprinkler Schedules")) {
		paragraph "<div style='color:#1A77C9;text-align:right;font-weight:small;font-size:9px;'>Developed by: Matt Hammond, C Steele<br/>Current Version: ${version()} -  ${thisCopyright}</div>"
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	}
}

def getThisCopyright(){"&copy; 2023 C Steele"}
