/*
 * Import URL: https://raw.githubusercontent.com/csteele-PD/Hubitat-public/refs/heads/master/GenericMatterOutlet.groovy
 *
 *	Copyright 2026 C Steele
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
/// DEVELOPMENT FORK -- use "^.*///.*\n" OR "^.*///.*\n" to remove the lines starting with /// OR containing /// -- think about the impact.

	public static String version()      {  return "v1.0.0"  }


metadata {
	definition (name: "Generic Matter Outlet", namespace: "csteele", author: "C Steele") {
		capability "Actuator"
		capability "Switch"
		capability "Configuration"
		capability "Initialize"

		fingerprint endpointId:"01", inClusters:"0003,0004,0006,001D", outClusters:"", model:"Smart Plug", manufacturer:"Leedarson", controllerType:"MAT"
	}
	preferences {
		input(name:"txtEnable", type:"bool", title:"Enable descriptionText logging", defaultValue:true)
		input(name:"debugEnable", type:"bool", title:"Enable debug logging", defaultValue:false, submitOnChange: true)
		input "debugTimeout", "enum", required: false, defaultValue: "0", title: "Automatic debug Log Disable Timeout?", width: 3,  \
			options: [ "0":"None", "1800":"30 Minutes", "3600":"60 Minutes", "86400":"1 Day" ]
	}
}

//parsers
void parse(String description) {
	Map descMap = matter.parseDescriptionAsMap(description)
	logDebug {"descMap:${descMap}"}
	switch (descMap.cluster) {
		case "0006" :
			if (descMap.attrId == "0000") { //switch
			    sendSwitchEvent(descMap.value)
			}
			break
		case "0000" :
			if (descMap.attrId == "4000") { //software build
			    updateDataValue("softwareBuild",descMap.value ?: "unknown")
			}
			break
		default :
			if (debugEnable) {
			    logDebug {"skipped:${descMap}"}
			}
	}
}

//events
private void sendSwitchEvent(String rawValue) {
	String value = rawValue == "01" ? "on" : "off"
	if (device.currentValue("switch") == value) return
	String descriptionText = "${device.displayName} was turned ${value}"
	logInfo {descriptionText}
	sendEvent(name:"switch", value:value, descriptionText:descriptionText)
}

//capability commands
void on() {
	logDebug {"on()"}
	sendToDevice(matter.on())
}

void off() {
	logDebug {"off()"}
	sendToDevice(matter.off())
}

void configure() {
	logWarn {"configure..."}
	sendToDevice(subscribeCmd())
}

//lifecycle commands
void updated(){
	logInfo {"updated..."}
	logWarn {"description logging is: ${txtEnable == true}"}
	logWarn {"debug logging is: ${debugEnable == true}"}
	logWarn {"debug timeout is: $debugTimeout"}
	unschedule (logsOff)
	if (debugEnable && debugTimeout.toInteger() >0) runIn(debugTimeout.toInteger(), logsOff)
}

void initialize() {
	sendToDevice(subscribeCmd())
}

String subscribeCmd() {
	List<Map<String, String>> attributePaths = []
	attributePaths.add(matter.attributePath(0x01, 0x0006, 0x00))
	//standard 0 reporting interval is way too busy for bulbs
	String cmd = matter.subscribe(5,0xFFFF,attributePaths)
	return cmd
}

/*
-----------------------------------------------------------------------------
Logging output
-----------------------------------------------------------------------------
*/

void logDebug(Closure msg) {
    if (settings.debugEnable) { log.debug "${msg()}" }
}

def logWarn(Closure msg) { 
	log.warn "${msg()}"
}

def logInfo(Closure msg) {
    if (settings.txtEnable) { log.info "${msg()}" }
}

void logsOff(){
	logWarn {"debug logging disabled..."}
	device.updateSetting("debugEnable",[value:"false",type:"bool"])
}


/*
-----------------------------------------------------------------------------
Send Hub output
-----------------------------------------------------------------------------
*/
void sendToDevice(List<String> cmds, Integer delay = 300) {
	sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.MATTER))
}

void sendToDevice(String cmd, Integer delay = 300) {
	sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.MATTER))
}

List<String> commands(List<String> cmds, Integer delay = 300) {
	return delayBetween(cmds.collect { it }, delay)
}
