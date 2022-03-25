/**
 *  Parent App Template
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


definition(
    name: "AppTemplate",
    namespace: "csteele",
    author: " CSteele",
    description: "Does very little. The work is in the Child.",
    category: "Automation",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true // ,submitOnChange: true      
} 


def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}


def updated() {
	log.debug "Updated with settings: ${settings}"
	unschedule()
	unsubscribe()
	initialize()
}

def initialize() {
	log.info "There are ${childApps.size()} child smartapps"
	childApps.each {child ->
		log.info "Child app: ${child.label}"
	}
}

def mainPage() {
	dynamicPage(name: "mainPage") {
		section {    
			paragraph title: "Title",
			"<b>This parent app is a container for all:</b><br> Parent - Child apps"
		}
      	section (){app(name: "BlMpSw", appName: "AppTemplate", namespace: "csteele", title: "New AppTemplate", multiple: true)}    
      	  
      	section (title: "<b>Name/Rename</b>") {label title: "Enter a name for this parent app (optional)", required: false}
	
		section ("Other preferences") {
			input "debugOutput",   "bool", title: "<b>Enable debug logging?</b>", defaultValue: true
			input "descTextEnable","bool", title: "<b>Enable descriptionText logging?</b>", defaultValue: true
		}
      	display()
	} 
}


def display() {
	updateCheck() 
	section{
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
		paragraph "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>Developed by: C Steele<br/>Version Status: $state.Status<br>Current Version: ${version()} -  ${thisCopyright}</div>"
	}
}
        
def getThisCopyright(){"&copy; 2019 C Steele "}