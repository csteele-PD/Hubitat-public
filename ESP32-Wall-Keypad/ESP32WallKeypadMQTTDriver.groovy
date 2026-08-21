/**
 * IMPORT URL: https://raw.githubusercontent.com/csteele-pd/Hubitat-public/main/ESP32-Wall-Keypad/hubitat/ESP32WallKeypadMQTTDriver.groovy
 *
 * ESP32 Wall Keypad MQTT Driver
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 * Hubitat driver for the ESP32-S3 wall keypad firmware in this repository.
 * The driver owns the retained alarm state and publishes short-lived results
 * back to the keypad after each action request.
 *
 *
 *
 * csteele: v0.1.0  Initial build
 * csteele: v0.2.5  Add paired LAN fan-out and dashboard recovery support
 *
 *
 *
 */
public static String version()	{  return "v0.2.5"  }

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
    definition(name: "ESP32 Wall Keypad MQTT", namespace: "keypad", author: "CSteele / OpenAI", importUrl: "https://raw.githubusercontent.com/csteele-pd/Hubitat-public/main/ESP32-Wall-Keypad/hubitat/ESP32WallKeypadMQTTDriver.groovy") {
        capability "Actuator"
        capability "Initialize"
        capability "Refresh"
        capability "SecurityKeypad"
        capability "Tone"
        capability "Alarm"

        attribute "mqttStatus", "string"
        attribute "keypadAvailability", "string"
        attribute "alarmState", "string"
        attribute "ready", "string"
        attribute "entryDelay", "number"
        attribute "exitDelay", "number"
        attribute "lastAction", "string"
        attribute "lastCredentialLength", "number"
        attribute "lastCodeName", "string"
        attribute "lastRequestId", "string"
        attribute "lastResult", "string"
        attribute "lastReason", "string"
        attribute "lanResultJson", "string"
        attribute "lanKeypadIp", "string"
        attribute "lanStatus", "string"
        attribute "lastLanPush", "string"
        attribute "keypadIp", "string"
        attribute "keypadFirmware", "string"
        attribute "armingIn", "string"

        command "publishState", [
            [name: "armedState*", type: "STRING", description: "disarmed, armed_away, armed_stay, arming, exit_delay, entry_delay, alarming, unknown"],
            [name: "delayRemaining", type: "NUMBER", description: "Optional delay remaining in seconds"]
        ]
        command "setReady", [[name: "ready*", type: "STRING", description: "true or false"]]
        command "acceptLastRequest"
        command "rejectLastRequest", [[name: "reason", type: "STRING", description: "invalid_code, not_ready, rejected, error"]]
        command "rejectPendingHsmRequest", [[name: "reason", type: "STRING", description: "not_ready, rejected, error"]]
        command "sendResult", [
            [name: "result*", type: "STRING", description: "accepted, rejected, invalid_code, not_ready, error"],
            [name: "reason", type: "STRING", description: "Optional machine-readable reason"],
            [name: "message", type: "STRING", description: "Optional keypad display text"]
        ]
        command "publishDashboard", [
            [name: "dashboardId*", type: "STRING", description: "Dashboard id, for example hsm"],
            [name: "dashboardJson*", type: "STRING", description: "Dashboard JSON payload"]
        ]
        command "clearDashboard", [[name: "dashboardId*", type: "STRING", description: "Dashboard id to clear retained payload for"]]
        command "publishHsmDashboard", [[name: "dashboardJson*", type: "STRING", description: "HSM dashboard JSON payload"]]
        command "processLanAction", [[name: "actionJson*", type: "STRING", description: "Keypad action JSON from the dashboard app LAN endpoint"]]
        command "configureLanPeer", [[name: "ip*", type: "STRING", description: "Keypad IP paired by the dashboard app"]]
        command "entry"
        command "off"
        command "setArmHomeDelay"
        command "setArmNightDelay"
        command "setPartialFunction"
    }

    preferences {
        input name: "brokerHost", type: "text", title: "MQTT broker host or URI", required: true, defaultValue: "192.168.7.43"
        input name: "brokerPort", type: "text", title: "MQTT broker port", required: true, defaultValue: "1883"
        input name: "mqttUsername", type: "text", title: "MQTT username", required: false
        input name: "mqttPassword", type: "password", title: "MQTT password", required: false
        input name: "topicPrefix", type: "text", title: "Keypad topic prefix", required: true, defaultValue: "keypad/front_door"
        input name: "deviceId", type: "text", title: "Keypad device_id", required: true, defaultValue: "front_door"
        input name: "validDisarmCredential", type: "password", title: "Valid disarm credential", required: false
        input name: "maxCodeSlots", type: "number", title: "Maximum managed code slots", required: true, defaultValue: 30
        input name: "requireValidDisarmCredential", type: "bool", title: "Require valid credential to disarm", required: true, defaultValue: true
        input name: "requireArmCredential", type: "bool", title: "Require any credential to arm", required: true, defaultValue: false
        input name: "exitDelaySeconds", type: "number", title: "Exit delay seconds before armed state", required: true, defaultValue: 0
        input name: "entryDelaySeconds", type: "number", title: "Entry delay seconds before alarming", required: true, defaultValue: 0
        input name: "useHubitatSafetyMonitor", type: "bool", title: "Use Hubitat Safety Monitor for arm/disarm", required: true, defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", required: true, defaultValue: false
    }
}

void installed() {
    sendEvent(name: "alarmState", value: "disarmed")
    sendEvent(name: "securityKeypad", value: "disarmed")
    sendEvent(name: "alarm", value: "off")
    sendEvent(name: "ready", value: "true")
    sendEvent(name: "lastReason", value: "none")
    sendEvent(name: "lastCodeName", value: "none")
    initializeDelayState()
    initializeCodeState()
    initialize()
}

void updated() {
    unschedule()
    initializeDelayState()
    initializeCodeState()
    initialize()
}

void uninstalled() {
    try {
        interfaces.mqtt.disconnect()
    } catch (Exception e) {
        log.warn "MQTT disconnect failed during uninstall: ${e.message}"
    }
}

void initialize() {
    try {
        sendEvent(name: "mqttStatus", value: "connecting")
        interfaces.mqtt.disconnect()
    } catch (Exception ignored) {
    }

    try {
        String uri = mqttUri()
        String clientId = "hubitat-keypad-${device.id}"
        log.info "Connecting to MQTT broker at ${uri}"
        interfaces.mqtt.connect(uri, clientId, nullIfBlank(mqttUsername), nullIfBlank(mqttPassword))
        pauseExecution(1000)
        subscribeTopics()
        publishState(currentAlarmState())
    } catch (Exception e) {
        sendEvent(name: "mqttStatus", value: "error")
        log.error "MQTT initialize failed: ${e.message}"
    }
}

void refresh() {
    publishState(currentAlarmState())
}

void mqttClientStatus(String message) {
    if (logEnable) log.debug "MQTT status: ${message}"
    sendEvent(name: "mqttStatus", value: message)

    if (message?.startsWith("Status: Connection succeeded")) {
        subscribeTopics()
        publishState(currentAlarmState())
    }
}

void parse(String description) {
    Map mqtt = interfaces.mqtt.parseMessage(description)
    String topic = mqtt.topic
    String payload = mqtt.payload ?: ""
    if (logEnable) log.debug "MQTT topic=${topic}, payload=${payload}"

    if (topic == topicName("action")) {
        handleActionPayload(payload, "mqtt")
    } else if (topic == topicName("availability")) {
        sendEvent(name: "keypadAvailability", value: payload)
    } else if (topic == topicName("status")) {
        handleStatusPayload(payload)
    }
}

void publishState(String armedState, Integer delayRemaining = null) {
    String normalizedState = normalizeArmedState(armedState)
    Map payload = [
        device_id: deviceId,
        ready: currentReady(),
        armed_state: normalizedState
    ]
    if (delayRemaining != null) {
        payload.delay_remaining = delayRemaining < 0 ? 0 : delayRemaining
    }
    interfaces.mqtt.publish(topicName("state"), JsonOutput.toJson(payload), 1, true)
    postLanToKeypad("state", payload)
    sendEvent(name: "alarmState", value: normalizedState)
    sendEvent(name: "securityKeypad", value: securityKeypadStateFor(normalizedState))
    sendEvent(name: "ready", value: "${currentReady()}")
    if (logEnable) log.debug "Published state ${payload}"
}

void armAway() {
    if (usesHubitatSafetyMonitor()) {
        reflectHubitatArmCommand("armed_away", "armedAway", 0)
        return
    }
    handleArm("", "", "armed_away", true)
}

void armAway(Number delaySeconds) {
    reflectHubitatArmCommand("armed_away", "armedAway", delaySeconds)
}

void armHome() {
    if (usesHubitatSafetyMonitor()) {
        reflectHubitatArmCommand("armed_stay", "armedHome", 0)
        return
    }
    handleArm("", "", "armed_stay", true)
}

void armHome(Number delaySeconds) {
    reflectHubitatArmCommand("armed_stay", "armedHome", delaySeconds)
}

void armNight() {
    if (usesHubitatSafetyMonitor()) {
        reflectHubitatArmCommand("armed_stay", "armedNight", 0)
        return
    }
    handleArm("", "", "armed_stay", true)
}

void armNight(Number delaySeconds) {
    reflectHubitatArmCommand("armed_stay", "armedNight", delaySeconds)
}

void disarm() {
    if (usesHubitatSafetyMonitor()) {
        clearHsmEntryDelay()
        clearHsmExitDelay()
        clearPendingHsmRequest("disarm")
        publishState("disarmed")
        return
    }
    handleDisarm("", "", true)
}

void disarm(Number delaySeconds) {
    clearHsmEntryDelay()
    disarm()
}

void entry() {
    entry(configuredDelaySeconds(entryDelaySeconds))
}

void entry(Number delaySeconds) {
    Integer delay = configuredDelaySeconds(delaySeconds)
    clearHsmEntryDelay()
    state.hsmEntryDelayRemaining = delay
    publishState("entry_delay", delay)
    if (delay > 0) {
        runIn(1, "tickHsmEntryDelay")
    }
    if (logEnable) log.debug "HSM entry delay=${delay}"
}

void tickHsmEntryDelay() {
    Integer remaining = (state.hsmEntryDelayRemaining ?: 0) as Integer
    remaining -= 1
    state.hsmEntryDelayRemaining = remaining

    if (remaining > 0) {
        publishState("entry_delay", remaining)
        runIn(1, "tickHsmEntryDelay")
    } else {
        state.hsmEntryDelayRemaining = null
    }
}

void beep() {
    publishDeviceCommand("beep")
}

void both() {
    sendEvent(name: "alarm", value: "both")
    publishDeviceCommand("both")
    publishState("alarming")
}

void siren() {
    sendEvent(name: "alarm", value: "siren")
    publishDeviceCommand("siren")
    publishState("alarming")
}

void strobe() {
    sendEvent(name: "alarm", value: "strobe")
    publishDeviceCommand("strobe")
    publishState("alarming")
}

void off() {
    sendEvent(name: "alarm", value: "off")
    publishDeviceCommand("off")
    disarm()
}

void setExitDelay(Number exitdelay) {
    Integer seconds = configuredDelaySeconds(exitdelay)
    device.updateSetting("exitDelaySeconds", [value: seconds, type: "number"])
    sendEvent(name: "exitDelay", value: seconds)
    if (logEnable) log.debug "Set exit delay to ${seconds}"
}

void setExitDelay(Map delays) {
    Integer awayDelay = configuredDelaySeconds(delays?.awayDelay)
    state.hsmExitDelays = [
        awayDelay: awayDelay,
        homeDelay: configuredDelaySeconds(delays?.homeDelay),
        nightDelay: configuredDelaySeconds(delays?.nightDelay)
    ]
    sendEvent(name: "exitDelay", value: awayDelay)
    if (logEnable) log.debug "Set HSM exit delays ${state.hsmExitDelays}"
}

void setEntryDelay(Number entrancedelay) {
    Integer seconds = configuredDelaySeconds(entrancedelay)
    device.updateSetting("entryDelaySeconds", [value: seconds, type: "number"])
    sendEvent(name: "entryDelay", value: seconds)
    if (logEnable) log.debug "Set entry delay to ${seconds}"
}

void setCodeLength(Number pincodelength) {
    Integer length = configuredPositiveInteger(pincodelength, 4)
    if (length < 1 || length > 16) {
        sendEvent(name: "codeChanged", value: "failed", isStateChange: true)
        if (logEnable) log.debug "Rejected code length ${pincodelength}; expected 1-16"
        return
    }

    state.codeLength = length
    sendEvent(name: "codeLength", value: length)
    if (logEnable) log.debug "Set code length to ${length}"
}

void getCodes() {
    publishLockCodes()
    if (logEnable) log.debug "Reported ${managedCodeCount()} managed code(s)"
}

void setCode(Number codeposition, String pincode, String name) {
    String slot = codeSlotKey(codeposition)
    String suppliedPin = pincode?.toString() ?: ""
    // Lock Code Manager distributes the encrypted lockCodes value; direct calls use a numeric PIN.
    String pin = suppliedPin ==~ /^[0-9]+$/ ? suppliedPin : decryptCode(suppliedPin)
    String codeName = nullIfBlank(name) ?: "Code ${slot}"
    Map codes = managedCodes()

    if (!validCodeSlot(slot) || !validPinCode(pin)) {
        sendEvent(name: "codeChanged", value: "failed", isStateChange: true)
        if (logEnable) {
            log.debug "Rejected setCode position=${codeposition} name=${codeName} slotValid=${validCodeSlot(slot)} pinNumeric=${pin ==~ /^[0-9]+$/} pinLength=${pin.length()} allowedLength=${currentCodeLength()}"
        }
        return
    }

    String changeType = codes.containsKey(slot) ? "changed" : "added"
    codes[slot] = [
        name: codeName,
        code: encrypt(pin)
    ]
    state.lockCodes = codes
    publishLockCodes()
    sendCodeChanged(changeType, slot, codeName, codes[slot].code)
    if (logEnable) log.debug "${changeType.capitalize()} code position=${slot} name=${codeName}"
}

void deleteCode(Number codeposition) {
    String slot = codeSlotKey(codeposition)
    Map codes = managedCodes()

    if (!validCodeSlot(slot) || !codes.containsKey(slot)) {
        sendEvent(name: "codeChanged", value: "failed", isStateChange: true)
        if (logEnable) log.debug "Rejected deleteCode position=${codeposition}"
        return
    }

    String codeName = codes[slot]?.name ?: "Code ${slot}"
    codes.remove(slot)
    state.lockCodes = codes
    publishLockCodes()
    sendCodeChanged("deleted", slot, codeName, "")
    if (logEnable) log.debug "Deleted code position=${slot} name=${codeName}"
}

void setArmHomeDelay(Number seconds = null) {
    if (seconds != null) {
        setExitDelay(seconds)
    }
    if (logEnable) log.debug "Arm-home delay uses exitDelaySeconds=${configuredExitDelaySeconds()}"
}

void setArmNightDelay(Number seconds = null) {
    if (seconds != null) {
        setExitDelay(seconds)
    }
    if (logEnable) log.debug "Arm-night delay uses exitDelaySeconds=${configuredExitDelaySeconds()}"
}

void setPartialFunction() {
    if (logEnable) log.debug "Partial function is fixed to armHome()/arm_stay"
}

void setPartialFunction(String partialFunction) {
    state.hsmPartialFunction = nullIfBlank(partialFunction) ?: "armHome"
    if (logEnable) log.debug "Set HSM partial function=${state.hsmPartialFunction}"
}

void setReady(String value) {
    boolean readyValue = value?.toString()?.toLowerCase() in ["true", "1", "yes", "on"]
    sendEvent(name: "ready", value: "${readyValue}")
    publishState(currentAlarmState())
}

void acceptLastRequest() {
    sendResult("accepted", "", "Accepted")
}

void rejectLastRequest(String reason = "rejected") {
    String normalizedReason = nullIfBlank(reason) ?: "rejected"
    sendResult("rejected", normalizedReason, resultMessageForReason(normalizedReason))
}

void rejectPendingHsmRequest(String reason = "not_ready") {
    String requestId = state.pendingHsmRequestId ?: ""
    if (!requestId) {
        return
    }

    String normalizedReason = nullIfBlank(reason) ?: "not_ready"
    publishResultForAction(requestId, "rejected", normalizedReason, resultMessageForReason(normalizedReason))
    clearPendingHsmRequest()
}

void sendResult(String result, String reason = "", String message = "") {
    Map payload = [
        device_id: deviceId,
        request_id: state.lastRequestId ?: "",
        result: nullIfBlank(result) ?: "accepted"
    ]

    if (nullIfBlank(reason) != null) {
        payload.reason = reason
    }
    if (nullIfBlank(message) != null) {
        payload.message = message
    }

    interfaces.mqtt.publish(topicName("result"), JsonOutput.toJson(payload), 1, false)
    postLanToKeypad("result", payload)
    sendEvent(name: "lastResult", value: payload.result)
    sendEvent(name: "lastReason", value: payload.reason ?: "none")
    if (logEnable) log.debug "Published result ${payload}"
}

void publishHsmDashboard(String dashboardJson) {
    publishDashboard("hsm", dashboardJson)
}

void processLanAction(String actionJson) {
    state.lanResultPayload = null
    handleActionPayload(actionJson ?: "", "lan")
    Map result = state.lanResultPayload instanceof Map ? state.lanResultPayload as Map : null
    state.lanResultPayload = null
    sendEvent(name: "lanResultJson", value: JsonOutput.toJson(result ?: [
        device_id: deviceId,
        request_id: "",
        result: "rejected",
        reason: "error",
        message: "No response"
    ]), isStateChange: true)
}

void publishDashboard(String dashboardId, String dashboardJson) {
    String normalizedDashboardId = normalizeDashboardId(dashboardId)
    if (normalizedDashboardId == null) {
        if (logEnable) log.debug "Rejected dashboard publish; invalid dashboardId=${dashboardId}"
        return
    }

    Map payload = parseJson(dashboardJson ?: "")
    if (payload == null) {
        if (logEnable) log.debug "Rejected ${normalizedDashboardId} dashboard publish; invalid JSON"
        return
    }

    if (payload.device_id && payload.device_id != deviceId) {
        if (logEnable) log.debug "Rejected ${normalizedDashboardId} dashboard publish for device_id=${payload.device_id}; expected ${deviceId}"
        return
    }

    payload.device_id = deviceId
    interfaces.mqtt.publish(topicName("dashboard/${normalizedDashboardId}"), JsonOutput.toJson(payload), 1, true)
    payload.dashboard_id = normalizedDashboardId
    postLanToKeypad("dashboard", payload)
    if (logEnable) log.debug "Published ${normalizedDashboardId} dashboard ${payload}"
}

void clearDashboard(String dashboardId) {
    String normalizedDashboardId = normalizeDashboardId(dashboardId)
    if (normalizedDashboardId == null) {
        if (logEnable) log.debug "Rejected dashboard clear; invalid dashboardId=${dashboardId}"
        return
    }

    interfaces.mqtt.publish(topicName("dashboard/${normalizedDashboardId}"), "", 1, true)
    postLanToKeypad("dashboard", [
        device_id: deviceId,
        dashboard_id: normalizedDashboardId,
        clear: true
    ])
    if (logEnable) log.debug "Cleared retained ${normalizedDashboardId} dashboard"
}

private void handleActionPayload(String payload, String source = "mqtt") {
    if (!payload?.trim()) {
        if (logEnable) log.debug "Ignoring empty action payload"
        return
    }

    Map action = parseJson(payload)
    if (action == null) {
        publishResultForAction("", "rejected", "bad_json", "Bad request", source)
        return
    }

    if (action.device_id && action.device_id != deviceId) {
        if (logEnable) log.debug "Ignoring action for device_id=${action.device_id}; expected ${deviceId}"
        if (source == "lan") {
            publishResultForAction(action.request_id ?: "", "rejected", "wrong_device", "Wrong device", source)
        }
        return
    }

    String requestId = action.request_id ?: ""
    String actionName = action.action ?: ""
    String credential = action.credential ?: ""
    Integer credentialLength = credential.length()

    if (requestId && atomicState.lastProcessedActionRequestId == requestId) {
        if (source == "lan" && atomicState.lastProcessedActionResult instanceof Map) {
            state.lanResultPayload = atomicState.lastProcessedActionResult as Map
        }
        if (logEnable) log.debug "Ignoring duplicate ${source} action request_id=${requestId}"
        return
    }

    state.lastRequestId = requestId
    sendEvent(name: "lastRequestId", value: requestId)
    sendEvent(name: "lastAction", value: actionName)
    sendEvent(name: "lastCredentialLength", value: credentialLength)

    switch (actionName) {
        case "disarm":
            handleDisarm(requestId, credential, false, source)
            break
        case "arm_away":
            handleArm(requestId, credential, "armed_away", false, source)
            break
        case "arm_stay":
            handleArm(requestId, credential, "armed_stay", false, source)
            break
        default:
            publishResultForAction(requestId, "rejected", "unknown_action", "Unknown action", source)
            break
    }
}

private void handleDisarm(String requestId, String credential, boolean trustedHubCommand = false, String source = "mqtt") {
    Map match = trustedHubCommand ? [valid: true, name: "Hubitat"] : credentialMatch(credential)

    if (!trustedHubCommand && requireValidDisarmCredential && !match.valid && credential.length() > 0) {
        if (!usesHubitatSafetyMonitor()) startEntryDelayIfArmed()
        publishResultForAction(requestId, "rejected", "invalid_code", "Invalid code", source)
        return
    }

    if (!trustedHubCommand && requireValidDisarmCredential && !match.valid && credential.length() == 0) {
        if (!usesHubitatSafetyMonitor()) startEntryDelayIfArmed()
        publishResultForAction(requestId, "rejected", "credential_required", "Code required", source)
        return
    }

    sendEvent(name: "lastCodeName", value: match.name ?: "none")
    publishResultForAction(requestId, "accepted", "", "Disarmed", source)

    if (usesHubitatSafetyMonitor()) {
        requestHubitatSafetyMonitor("disarm")
        return
    }

    unschedule("tickArmDelay")
    unschedule("tickEntryDelay")
    state.pendingArmedState = null
    state.exitDelayRemaining = null
    state.entryDelayRemaining = null
    publishState("disarmed")
}

private void handleArm(String requestId, String credential, String armedState, boolean trustedHubCommand = false, String source = "mqtt") {
    if (!trustedHubCommand && requireArmCredential && credential.length() == 0) {
        publishResultForAction(requestId, "rejected", "credential_required", "Code required", source)
        return
    }

    publishResultForAction(requestId, "accepted", "", armedState == "armed_stay" ? "Partial" : "Armed", source)
    if (usesHubitatSafetyMonitor()) {
        requestHubitatSafetyMonitor(armedState == "armed_stay" ? hsmPartialArmCommand() : "armAway")
        return
    }

    Integer delaySeconds = configuredExitDelaySeconds()
    if (delaySeconds > 0) {
        unschedule("tickArmDelay")
        state.pendingArmedState = armedState
        state.exitDelayRemaining = delaySeconds
        publishState("exit_delay", delaySeconds)
        runIn(1, "tickArmDelay")
    } else {
        state.pendingArmedState = null
        state.exitDelayRemaining = null
        publishState(armedState)
    }
}

void tickArmDelay() {
    Integer remaining = (state.exitDelayRemaining ?: 0) as Integer
    remaining -= 1
    state.exitDelayRemaining = remaining

    if (remaining > 0) {
        publishState("exit_delay", remaining)
        runIn(1, "tickArmDelay")
    } else {
        finalizeArmDelay()
    }
}

void finalizeArmDelay() {
    String armedState = state.pendingArmedState ?: "armed_away"
    state.pendingArmedState = null
    state.exitDelayRemaining = null
    publishState(armedState)
}

void finalizeEntryDelay() {
    state.entryDelayRemaining = null
    publishState("alarming")
}

private void startEntryDelayIfArmed() {
    if (!isArmedState(currentAlarmState())) {
        return
    }

    Integer delaySeconds = configuredDelaySeconds(entryDelaySeconds)
    if (delaySeconds <= 0) {
        return
    }

    state.entryDelayRemaining = delaySeconds
    publishState("entry_delay", delaySeconds)
    unschedule("tickEntryDelay")
    runIn(1, "tickEntryDelay")
}

void tickEntryDelay() {
    Integer remaining = (state.entryDelayRemaining ?: 0) as Integer
    remaining -= 1
    state.entryDelayRemaining = remaining

    if (remaining > 0) {
        publishState("entry_delay", remaining)
        runIn(1, "tickEntryDelay")
    } else {
        finalizeEntryDelay()
    }
}

private void publishResultForAction(String requestId, String result, String reason, String message, String source = "mqtt") {
    state.lastRequestId = requestId ?: state.lastRequestId
    Map payload = [
        device_id: deviceId,
        request_id: requestId ?: "",
        result: result
    ]

    if (nullIfBlank(reason) != null) {
        payload.reason = reason
    }
    if (nullIfBlank(message) != null) {
        payload.message = message
    }

    if (source == "lan") {
        state.lanResultPayload = payload
    } else {
        interfaces.mqtt.publish(topicName("result"), JsonOutput.toJson(payload), 1, false)
        postLanToKeypad("result", payload)
    }
    if (requestId) {
        atomicState.lastProcessedActionRequestId = requestId
        atomicState.lastProcessedActionResult = payload
    }
    sendEvent(name: "lastResult", value: result)
    sendEvent(name: "lastReason", value: reason ?: "none")
    if (logEnable) log.debug "${source == 'lan' ? 'Returned LAN' : 'Published'} action result ${payload}"
}

private void publishDeviceCommand(String commandName) {
    Map payload = [
        device_id: deviceId,
        command: commandName
    ]

    interfaces.mqtt.publish(topicName("command"), JsonOutput.toJson(payload), 1, false)
    postLanToKeypad("command", payload)
    if (logEnable) log.debug "Published device command ${payload}"
}

void configureLanPeer(String ip) {
    String normalizedIp = ip?.trim()
    if (!normalizedIp) {
        return
    }

    state.lanKeypadIp = normalizedIp
    sendEvent(name: "lanKeypadIp", value: normalizedIp)
    sendEvent(name: "lanStatus", value: "paired")
    if (logEnable) log.debug "Configured LAN keypad IP ${normalizedIp}"
}

private void initializeDelayState() {
    sendEvent(name: "entryDelay", value: configuredDelaySeconds(entryDelaySeconds))
    sendEvent(name: "exitDelay", value: configuredExitDelaySeconds())
}

private void initializeCodeState() {
    if (!(state.lockCodes instanceof Map)) {
        state.lockCodes = [:]
    }
    if (state.codeLength == null) {
        state.codeLength = 4
    }
    sendEvent(name: "codeLength", value: currentCodeLength())
    sendEvent(name: "maxCodes", value: maxManagedCodes())
    publishLockCodes()
}

private Map managedCodes() {
    if (!(state.lockCodes instanceof Map)) {
        state.lockCodes = [:]
    }
    return state.lockCodes as Map
}

private Integer managedCodeCount() {
    return managedCodes().size()
}

private Integer currentCodeLength() {
    return configuredPositiveInteger(state.codeLength, 4)
}

private Integer maxManagedCodes() {
    Integer slots = configuredPositiveInteger(maxCodeSlots, 30)
    return slots < 1 ? 1 : slots
}

private String codeSlotKey(Number codeposition) {
    return "${configuredPositiveInteger(codeposition, 0)}"
}

private boolean validCodeSlot(String slot) {
    try {
        Integer slotNumber = slot as Integer
        return slotNumber >= 1 && slotNumber <= maxManagedCodes()
    } catch (Exception ignored) {
        return false
    }
}

private boolean validPinCode(String pin) {
    return pin ==~ /^[0-9]+$/ && pin.length() <= currentCodeLength()
}

private void publishLockCodes() {
    Map publishedCodes = managedCodes().collectEntries { slot, code ->
        [(slot.toString()): [
            name: code.name ?: "Code ${slot}",
            code: code.code ?: ""
        ]]
    }

    sendEvent(name: "codeLength", value: currentCodeLength())
    sendEvent(name: "maxCodes", value: maxManagedCodes())
    sendEvent(name: "lockCodes", value: JsonOutput.toJson(publishedCodes), isStateChange: true)
}

private void sendCodeChanged(String changeType, String slot, String codeName, String encryptedCode) {
    Map eventData = [
        (slot): [
            name: codeName,
            code: encryptedCode ?: ""
        ]
    ]
    sendEvent(name: "codeChanged", value: changeType, data: JsonOutput.toJson(eventData), isStateChange: true)
}

private Map credentialMatch(String credential) {
    String entered = credential ?: ""
    if (!entered) {
        return [valid: false, name: ""]
    }

    Map codes = managedCodes()
    for (entry in codes) {
        String storedCode = entry.value?.code ?: ""
        String decoded = decryptCode(storedCode)
        if (decoded == entered) {
            return [valid: true, name: entry.value?.name ?: "Code ${entry.key}"]
        }
    }

    String fallback = nullIfBlank(validDisarmCredential)
    if (codes.isEmpty() && fallback != null && entered == fallback) {
        return [valid: true, name: "Preference code"]
    }

    return [valid: false, name: ""]
}

private String decryptCode(String value) {
    try {
        return decrypt(value) ?: value
    } catch (Exception ignored) {
        return value
    }
}

private void handleStatusPayload(String payload) {
    Map status = parseJson(payload)
    if (status == null) {
        return
    }

    if (status.device_id && status.device_id != deviceId) {
        return
    }

    if (status.ip) sendEvent(name: "keypadIp", value: status.ip)
    if (status.firmware) sendEvent(name: "keypadFirmware", value: status.firmware)
    if (status.last_request_id) sendEvent(name: "lastRequestId", value: status.last_request_id)
}

private void postLanToKeypad(String path, Map payload) {
    String ip = state.lanKeypadIp ?: device.currentValue("lanKeypadIp")?.toString()
    if (!ip) {
        return
    }

    try {
        httpPost([
            uri: "http://${ip}/${path}",
            requestContentType: "application/json",
            contentType: "application/json",
            body: JsonOutput.toJson(payload),
            timeout: 3
        ]) { resp ->
            sendEvent(name: "lastLanPush", value: "${path}:${resp.status}", isStateChange: true)
            if (logEnable) log.debug "LAN push ${path} status=${resp.status}"
            if (resp.status >= 200 && resp.status < 300) {
                sendEvent(name: "lanStatus", value: "ok")
            } else {
                sendEvent(name: "lanStatus", value: "error")
            }
        }
    } catch (Exception e) {
        sendEvent(name: "lastLanPush", value: "${path}:failed", isStateChange: true)
        sendEvent(name: "lanStatus", value: "error")
        if (logEnable) log.debug "LAN push ${path} failed: ${e.message}"
    }
}

private void subscribeTopics() {
    try {
        interfaces.mqtt.subscribe(topicName("action"), 1)
        interfaces.mqtt.subscribe(topicName("availability"), 1)
        interfaces.mqtt.subscribe(topicName("status"), 1)
        sendEvent(name: "mqttStatus", value: "connected")
        if (logEnable) log.debug "Subscribed to ${topicName('action')}, ${topicName('availability')}, ${topicName('status')}"
    } catch (Exception e) {
        log.warn "MQTT subscribe failed: ${e.message}"
    }
}

private String mqttUri() {
    String host = brokerHost?.trim()
    String port = brokerPort?.toString()?.replaceAll(",", "")?.trim() ?: "1883"
    if (host?.startsWith("mqtt://")) {
        return "tcp://${host.substring(7)}"
    }
    if (host?.startsWith("tcp://") || host?.startsWith("ssl://")) {
        return host
    }
    if (host?.contains(":")) {
        return "tcp://${host}"
    }
    return "tcp://${host}:${port}"
}

private String topicName(String suffix) {
    return "${topicPrefix?.trim()?.replaceAll('/+$', '')}/${suffix}"
}

private Map parseJson(String payload) {
    try {
        return new JsonSlurper().parseText(payload) as Map
    } catch (Exception e) {
        log.warn "JSON parse failed: ${e.message}; payload=${payload}"
        return null
    }
}

private String normalizeArmedState(String armedState) {
    String value = armedState ?: "unknown"
    List known = ["disarmed", "armed_away", "armed_stay", "arming", "exit_delay", "entry_delay", "alarming", "unknown"]
    return known.contains(value) ? value : "unknown"
}

private String normalizeDashboardId(String dashboardId) {
    String value = nullIfBlank(dashboardId)?.toLowerCase()
    return value ==~ /^[a-z0-9_-]+$/ ? value : null
}

private String currentAlarmState() {
    return device.currentValue("alarmState") ?: "disarmed"
}

private void requestHubitatSafetyMonitor(String command) {
    state.pendingHsmRequestId = state.lastRequestId ?: ""
    state.pendingHsmAction = command
    sendEvent(name: "armingIn", value: command, data: [armCmd: command], isStateChange: true)
    if (logEnable) log.debug "Requested HSM command=${command} through keypad protocol"
}

private void reflectHubitatArmCommand(String armedState, String expectedHsmStatus, Number delaySeconds) {
    clearHsmEntryDelay()
    String completedCommand = expectedHsmStatus == "armedNight" ? "armNight" :
                              expectedHsmStatus == "armedHome" ? "armHome" : "armAway"
    clearPendingHsmRequest(completedCommand)
    if (!usesHubitatSafetyMonitor()) {
        clearHsmExitDelay()
        handleArm("", "", armedState, true)
        return
    }

    state.pendingHsmArmedState = armedState
    state.pendingHsmExpectedStatus = expectedHsmStatus
    state.pendingHsmArmDelay = configuredDelaySeconds(delaySeconds)
    runInMillis(1, "applyHsmArmCommand", [overwrite: true])
}

void applyHsmArmCommand() {
    String armedState = state.pendingHsmArmedState ?: "armed_away"
    String expectedHsmStatus = state.pendingHsmExpectedStatus ?: "armedAway"
    Integer delay = configuredDelaySeconds(state.pendingHsmArmDelay)
    state.pendingHsmArmedState = null
    state.pendingHsmExpectedStatus = null
    state.pendingHsmArmDelay = null

    clearHsmExitDelay()
    if (location.hsmStatus == expectedHsmStatus) {
        publishState(armedState)
        if (logEnable) log.debug "HSM keypad command completed state=${armedState}"
        return
    }

    if (delay > 0) {
        state.hsmExitDelayRemaining = delay
        publishState("exit_delay", delay)
        runIn(1, "tickHsmExitDelay")
    } else {
        publishState(armedState)
    }
    if (logEnable) log.debug "HSM keypad command state=${armedState} delay=${delay}"
}

private void clearHsmEntryDelay() {
    unschedule("tickHsmEntryDelay")
    state.hsmEntryDelayRemaining = null
}

void tickHsmExitDelay() {
    Integer remaining = (state.hsmExitDelayRemaining ?: 0) as Integer
    remaining -= 1
    state.hsmExitDelayRemaining = remaining

    if (remaining > 0) {
        publishState("exit_delay", remaining)
        runIn(1, "tickHsmExitDelay")
    } else {
        state.hsmExitDelayRemaining = null
    }
}

private void clearHsmExitDelay() {
    unschedule("tickHsmExitDelay")
    state.hsmExitDelayRemaining = null
}

private void clearPendingHsmRequest(String completedCommand = null) {
    if (completedCommand != null && state.pendingHsmAction != null && state.pendingHsmAction != completedCommand) {
        return
    }
    state.pendingHsmRequestId = null
    state.pendingHsmAction = null
}

private boolean usesHubitatSafetyMonitor() {
    return useHubitatSafetyMonitor?.toString()?.toLowerCase() == "true"
}

private String hsmPartialArmCommand() {
    return state.hsmPartialFunction == "armNight" ? "armNight" : "armHome"
}

private String securityKeypadStateFor(String armedState) {
    switch (armedState) {
        case "disarmed":
            return "disarmed"
        case "armed_stay":
            return "armed home"
        case "armed_away":
        case "arming":
        case "exit_delay":
        case "entry_delay":
        case "alarming":
            return "armed away"
        default:
            return "unknown"
    }
}

private boolean currentReady() {
    return (device.currentValue("ready") ?: "true").toString() == "true"
}

private Integer configuredExitDelaySeconds() {
    return configuredDelaySeconds(exitDelaySeconds)
}

private Integer configuredDelaySeconds(value) {
    try {
        Integer seconds = (value ?: 0) as Integer
        return seconds < 0 ? 0 : seconds
    } catch (Exception ignored) {
        return 0
    }
}

private Integer configuredPositiveInteger(value, Integer fallback) {
    try {
        Integer number = value as Integer
        return number < 0 ? fallback : number
    } catch (Exception ignored) {
        return fallback
    }
}

private boolean isArmedState(String armedState) {
    return armedState in ["armed_away", "armed_stay", "entry_delay"]
}

private String nullIfBlank(value) {
    String text = value?.toString()
    return text?.trim() ? text : null
}

private String resultMessageForReason(String reason) {
    switch (reason) {
        case "invalid_code":
            return "Invalid code"
        case "not_ready":
            return "Not ready"
        case "credential_required":
            return "Code required"
        case "error":
            return "Error"
        default:
            return "Rejected"
    }
}
