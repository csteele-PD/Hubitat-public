/*
 * Import URL: https://raw.githubusercontent.com/csteele-PD/Hubitat-public/refs/heads/master/SprinklerSchedule/hybridRainCheck.groovy
 *
 *	Copyright 2025 C Steele
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *	use this file except in compliance with the License. You may obtain a copy
 *	of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *	License for the specific language governing permissions and limitations
 *	under the License.
 *
 *
 *
 *
 * csteele: v1.0.0	Initial version
 * 
 *
 */

	public static String version()      {  return "v1.0.0"  }


metadata 
{
	definition(name: "hybridRainCheck", namespace: "csteele", author: "C Steele", importUrl: "https://raw.githubusercontent.com/csteele-PD/Hubitat-public/refs/heads/master/SprinklerSchedule/hybridRainCheck.groovy")
	{
 		capability "Sensor"
 		capability "Actuator"
 		capability "Switch"
		capability "WaterSensor"
		
//		attribute "water", "enum" // water is an attribute of WaterSensor ENUM ["wet", "dry"]
		command 	"wet"
		command	"dry"
	}

      preferences 
      {

      }
}



/*
-----------------------------------------------------------------------------

	driver command response

-----------------------------------------------------------------------------
*/

/*
	on
    
	Turns the device on.
*/
void wet() { on() }
void on()
{
	// The server will update on/off status
	log.trace "$device ON"
	sendEvent(name: "switch"      , value: 'on')
	sendEvent(name: "water"       , value: 'wet')
}


/*
	off
    
	Turns the device off.
*/
void dry() { off() }
void off()
{
	// The server will update on/off status
	log.trace "$device OFF"
	sendEvent(name: "switch"      , value: 'off')
	sendEvent(name: "water"       , value: 'dry')
}


/*
-----------------------------------------------------------------------------

	generic driver stuff
	
-----------------------------------------------------------------------------
*/
/*
	parse
    
	In a virtual world this should never be called.
*/
void parse(String description)
{
	log.trace "Description is $description"
}


/*
	updated
    
	Doesn't do much other than call initialize().
*/
void updated()
{
	initialize()
	log.trace "$device updated ran"
}


/*
	installed
    
	Doesn't do much other than call initialize().
*/
void installed()
{
	initialize()
	log.trace "$device installed ran"
}


/*
	uninstalled
    
	When the device is removed to allow for any necessary cleanup.
*/
void uninstalled()
{
	log.trace "$device uninstalled ran"
}


/*
	initialize
    
	Doesn't do anything.
*/
void initialize()
{
	log.trace "$device initialize ran"
}
