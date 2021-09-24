/**
 *  Copyright 2019 C Steele
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

public static String version()      {  return "v1.0.0"  }

/***********************************************************************************************************************
 * Version: 1.0.0
 *
 */

metadata {
	definition (name: "EtherRain Device (Child)", namespace: "csteele", author: "C Steele", component: true, importUrl: "https://raw.githubusercontent.com/csteele-PD/Hubitat-master/master/EtherRain-Child-Device.groovy") 
	{
		capability "Valve"
		capability "Actuator"
	}
      preferences 
      {
		input("valveTimer", "text", title: "<b>Valve Timer</b>", description: "<i>On time of this Valve?</i>", defaultValue: 5, required: false)
	}
}


void open() { 
	def id = getDataValue("componentLabel")
	parent.open(id, valveTimer) 
}
void close() {
	def id = getDataValue("componentLabel")
	parent.close(id, valveTimer) 
}


void parse(String description) { log.warn "parse(String description) not implemented" }
void parse(List description) { log.warn "parse(List description) not implemented" }
void parse(Map evt) { sendEvent(evt) }

/*
	updated
    
	Doesn't do much other than call initialize().
*/
void updated()
{
	initialize()
	log.trace "EtherRain Child Updated: $device, $device.deviceNetworkId, ${getDataValue("componentLabel")}" 
}


/*
	installed
    
	Doesn't do much other than call initialize().
*/
void installed()
{
	initialize()
	log.trace "EtherRain Child Installed"
}



/*
	initialize
    
	Doesn't do anything.
*/
void initialize()
{
	schedule("0 0 8 ? * FRI *", updateCheck)
	runIn(20, updateCheck) 
	log.trace "EtherRain Child Initialize"
}


// Check Version   ***** with great thanks and acknowledgment to Cobra (CobraVmax) for his original code ****
void updateCheck()
{    
//	def paramsUD = [uri: "https://hubitatcommunity.github.io/EtherRainValves/version2.json"]
	def paramsUD = [uri: "https://csteele-pd.github.io/Hubitat-master/version2.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}

void updateCheckHandler(resp, data) {

	state.InternalName = "EtherRain Device (Child)"

	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		// log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright}"
		// uses reformattted 'version2.json' 
		def newVer = (respUD.driver.(state.InternalName).ver.replaceAll("[.vV]", ""))
		def currentVer = version().replaceAll("[.vV]", "")                
		state.UpdateInfo = (respUD.driver.(state.InternalName).updated)
            // log.debug "updateCheck: ${respUD.driver.(state.InternalName).ver}, $state.UpdateInfo, ${respUD.author}"

		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Driver is no longer supported by ${respUD.author}  **</b>"       
			      log.warn "** This Driver is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.driver.(state.InternalName).ver})</b>"
			      log.warn "** There is a newer version of this Driver available  (Version: ${respUD.driver.(state.InternalName).ver}) **"
			      log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})</b>"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this driver"
				break
		}

 	sendEvent(name: "verUpdate", value: state.UpdateInfo)
	sendEvent(name: "verStatus", value: state.Status)
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

void getThisCopyright(){"&copy; 2019 C Steele "}
