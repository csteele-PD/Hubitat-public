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

public static String version()      {  return "v1.0.2"  }

/***********************************************************************************************************************
 *         v1.0.2     removed standalone version check (allow HPM to check.)
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
void parse(Map evt) { sendEvent(evt) }  // report an Event in Parent, report Valve event in child.

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
	unschedule()
	log.trace "EtherRain Child Initialize"
}

void getThisCopyright(){"&copy; 2019 C Steele "}
