/**
 *  Hubitat Import URL: https://raw.githubusercontent.com/HubitatCommunity/AppTemplate/master/AppTemplate_Child.groovy
 */

/**
 *  AppTemplate Child 
 *
 *  Copyright 2020 C Steele
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
	public static String version()      {  return "v0.1.0"  }


import groovy.time.*

definition(
	name: "AppTemplate - Child",
	namespace: "csteele",
	author: "CSteele",
	description: "Child: This is where the work is done.",
	category: "Green Living",
	    
	parent: "csteele:App_Template",
	
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: ""
)


preferences {
//	page (name: "mainPage", title: "", install: true, uninstall: true, submitOnChange: true)    
	page (name: "mainPage")
	page (name: "sensorPage")
	page (name: "thresholdPage")
	page (name: "informPage")
}


def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
		section("<h2>${app.label ?: app.name}</h2>"){}
		section("-= <b>Main Menu</b> =-") 
		{
			input (name: "deviceType", title: "Type of Device", type: "enum", options: [a:"AAA", b:"BBB"], required:true, submitOnChange:true)
		}
		section (title: "<b>Name/Rename</b>") {
			label title: "This child app's Name (optional)", required: false
		}
		display()
	}
}


def installed() {
	initialize()
	log.info "Installed with settings: ${settings}"
}


def updated() {
	initialize()
	log.info "Updated with settings: ${settings}"
}


def initialize() {
	unsubscribe()
}


def display()
{
	updateCheck()
	section {
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
		paragraph "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>Developed by: C Steele<br/>Version Status: $state.Status<br>Current Version: ${version()} -  ${thisCopyright}</div>"
    }
}

def getThisCopyright(){"&copy; 2019 C Steele "}