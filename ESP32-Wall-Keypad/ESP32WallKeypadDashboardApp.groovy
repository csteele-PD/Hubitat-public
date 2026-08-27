/**
 * IMPORT URL: https://raw.githubusercontent.com/csteele-pd/Hubitat-public/main/ESP32-Wall-Keypad/hubitat/ESP32WallKeypadDashboardApp.groovy
 *
 * ESP32 Wall Keypad Dashboard App
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Hubitat app that publishes selected home-status summaries to an ESP32 Wall Keypad
 * dashboard and provides the paired LAN bridge endpoints for direct keypad traffic.
 *
 * csteele: v0.1.0  Initial build
 * csteele: v0.2.5  Add LAN pairing, local action/status endpoints, and reboot dashboard recovery
 * csteele: v0.2.6  Development
 */
public static String version()	{  return "v0.2.6"  }

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

definition(
    name: "ESP32 Wall Keypad Dashboard",
    namespace: "csteele",
    author: "CSteele / OpenAI",
    description: "Publishes selected Hubitat status summaries and pairs LAN keypads.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    oauth: true,
    importUrl: "https://raw.githubusercontent.com/csteele-pd/Hubitat-public/main/ESP32-Wall-Keypad/hubitat/ESP32WallKeypadDashboardApp.groovy"
)

preferences {
	page(name: "mainPage")
	page(name: "lanPairPage")
	page(name: "summaryPage")
	page(name: "controlsPage")

}

def mainPage() {
	dynamicPage(name: "mainPage", title: "ESP32 Wall Keypad Dashboard", install: true, uninstall: true) {
	    section() {
	        label title: "Name for this application", required: false, submitOnChange: true
	    }
	
	    section() {
	        input name: "keypadDevice",
	              type: "capability.securityKeypad",
	              title: "Wall keypad device",
	              required: true,
	              multiple: false
	    }
	
	    section() {
            paragraph "<b>Configuration of your Keypad: </b>"

	    	advDescription = '<i>LAN Pairing when using Hubitat Direct</i>'
	    	href "lanPairPage", title: "LAN Pairing", required: false, description: advDescription, state: "complete"
	    }

	    section() {
	    	advDescription = '<i>Summary status tiles on a Dashboard</i>'
	    	href "summaryPage", title: "Summary Dashboard", required: false, description: advDescription, state: "complete"
	    }

	    section() {
	    	advDescription = '<i>Interactive control tiles on a Dashboard</i>'
	    	href "controlsPage", title: "Controls Dashboard", required: false, description: advDescription, state: "complete"
	    }

	    section("<b>Options</b>") {
	        input name: "publishNowButton",
	              type: "button",
	              title: "Publish dashboard now"
	        input name: "logEnable",
	              type: "bool",
	              title: "Enable debug logging",
	              required: true,
	              defaultValue: false
	    }
	}
}

def lanPairPage() {
	dynamicPage(name: "lanPairPage", title: "<b>ESP32 Wall Keypad Pairing</b>", install: false, uninstall: false) {
	    section() {
	        paragraph pairingInstructions()
	        input name: "keypadIp",
	              type: "text",
	              title: "Keypad IP",
	              required: false
	        input name: "keypadPairingCode",
	              type: "text",
	              title: "Keypad pairing code",
	              required: false
	        input name: "pairKeypadButton",
	              type: "button",
	              title: "Pair keypad"
	    }
	}
}
	

def summaryPage() {
	dynamicPage(name: "summaryPage", title: "<b>ESP32 Wall Keypad Summary Dashboard</b>", install: false, uninstall: false) {
	    section() {
	        input name: "dashboardId",
	              type: "text",
	              title: "Dashboard id",
	              required: true,
	              defaultValue: "home"
	        input name: "dashboardTitle",
	              type: "text",
	              title: "Dashboard title",
	              required: true,
	              defaultValue: "Home"
	        input name: "includeHsm",
	              type: "bool",
	              title: "Include Hubitat Safety Monitor status",
	              required: true,
	              defaultValue: false
	        input name: "mirrorHsmToKeypad",
	              type: "bool",
	              title: "Mirror Hubitat Safety Monitor state and alerts to keypad",
	              description: "Enable when the keypad driver is configured to use Hubitat Safety Monitor.",
	              required: true,
	              defaultValue: false
	        input name: "contactDevices",
	              type: "capability.contactSensor",
	              title: "Door/window sensors",
	              required: false,
	              multiple: true
	        input name: "lockDevices",
	              type: "capability.lock",
	              title: "Locks",
	              required: false,
	              multiple: true
	        input name: "waterDevices",
	              type: "capability.waterSensor",
	              title: "Leak sensors",
	              required: false,
	              multiple: true
	        input name: "switchDevices",
	              type: "capability.switch",
	              title: "Lights/switches",
	              required: false,
	              multiple: true
	        input name: "temperatureDevices",
	              type: "capability.temperatureMeasurement",
	              title: "Temperature sensors",
	              required: false,
	              multiple: true
	        input name: "powerDevices",
	              type: "capability.powerMeter",
	              title: "Power meters",
	              required: false,
	              multiple: true
	        input name: "includeMode",
	              type: "bool",
	              title: "Include Hubitat mode",
	              required: true,
	              defaultValue: true
	        input name: "tileOrder",
	              type: "text",
	              title: "Tile order",
	              description: "Comma-separated: hsm,contacts,locks,leak,switches,temp,power,mode",
	              required: true,
	              defaultValue: "contacts,locks,leak,switches,temp,power,mode,hsm"
	    }
	}
}


def controlsPage() {
	dynamicPage(name: "controlsPage", title: "<b>ESP32 Wall Keypad Controls Dashboard</b>", install: false, uninstall: false) {
	    section() {
	        paragraph "Optional interactive dashboard controls. Actuators such as switches, dimmers, valves, locks, and garage doors can be assigned to these slots."
	        input name: "controlDevice1",
	              type: "capability.actuator",
	              title: "Control slot 1",
	              required: false,
	              multiple: false
	        input name: "controlDevice2",
	              type: "capability.actuator",
	              title: "Control slot 2",
	              required: false,
	              multiple: false
	        input name: "controlDevice3",
	              type: "capability.actuator",
	              title: "Control slot 3",
	              required: false,
	              multiple: false
	        input name: "controlDevice4",
	              type: "capability.actuator",
	              title: "Control slot 4",
	              required: false,
	              multiple: false
	        input name: "controlDevice5",
	              type: "capability.actuator",
	              title: "Control slot 5",
	              required: false,
	              multiple: false
	        input name: "controlDevice6",
	              type: "capability.actuator",
	              title: "Control slot 6",
	              required: false,
	              multiple: false
	    }
	}
}

void appButtonHandler(String buttonName) {
    if (buttonName == "publishNowButton") {
        // Hubitat saves changed preferences after appButtonHandler returns.
        runIn(1, "publishDashboard")
    } else if (buttonName == "pairKeypadButton") {
        pairKeypad()
    }
}

mappings {
    path("/action") { action: [POST: "actionHandler"] }
    path("/status") { action: [POST: "statusHandler"] }
    path("/ping") { action: [GET: "pingHandler"] }
}

void installed() {
    assignInitialAppLabel()
    initialize()
}

void updated() {
    unsubscribe()
    unschedule()
    assignInitialAppLabel()
    initialize()
}

void initialize() {
    ensureAccessToken()
    if (state.pairedKeypadIp) {
        keypadDevice?.configureLanPeer(state.pairedKeypadIp.toString())
    }
    if (includeHsm || mirrorHsmToKeypad) subscribe(location, "hsmStatus", "hsmStatusHandler")
    if (mirrorHsmToKeypad) subscribe(location, "hsmAlert", "hsmAlertHandler")
    if (includeMode) subscribe(location, "mode", eventHandler)
    if (keypadDevice != null) subscribe(keypadDevice, "lastDashboardActionJson", dashboardActionHandler)
    if (!selectedDevices(contactDevices).isEmpty()) subscribe(contactDevices, "contact", eventHandler)
    if (!selectedDevices(lockDevices).isEmpty()) subscribe(lockDevices, "lock", eventHandler)
    if (!selectedDevices(waterDevices).isEmpty()) subscribe(waterDevices, "water", eventHandler)
    if (!selectedDevices(switchDevices).isEmpty()) subscribe(switchDevices, "switch", eventHandler)
    selectedControlDevices().each { device ->
        if (isSwitchDevice(device)) subscribe(device, "switch", eventHandler)
        if (hasCapability(device, "SwitchLevel")) subscribe(device, "level", eventHandler)
        if (isGarageDoorDevice(device)) {
            subscribe(device, "door", eventHandler)
            subscribe(device, "contact", eventHandler)
        }
        if (isLockDevice(device)) subscribe(device, "lock", eventHandler)
        if (isValveDevice(device)) subscribe(device, "valve", eventHandler)
    }
    if (!selectedDevices(temperatureDevices).isEmpty()) subscribe(temperatureDevices, "temperature", eventHandler)
    if (!selectedDevices(powerDevices).isEmpty()) subscribe(powerDevices, "power", eventHandler)
    if (mirrorHsmToKeypad) publishHsmState(location.hsmStatus?.toString(), null)
    runIn(1, "publishDashboard")
    if (logEnable) log.debug "Dashboard appId=${app.id} paired=${state.pairedDeviceId ?: 'no'}"
}

def pingHandler() {
    renderJson([
        ok: true,
        app: app.label ?: "ESP32 Wall Keypad Dashboard",
        version: version(),
        paired: state.pairedDeviceId != null
    ])
}

def actionHandler() {
    Map payload = requestJson()
    if (payload == null) {
        return renderJson([
            result: "rejected",
            reason: "bad_json",
            message: "Bad request"
        ], 400)
    }

    if (logEnable) log.debug "LAN action payload=${payload}"
    if (payload.action in ["dashboard_tap", "dashboard_control"]) {
        return renderJson(handleDashboardControlAction(payload))
    }

    keypadDevice?.processLanAction(JsonOutput.toJson(payload))
    pauseExecution(100)

    Map result = parseResultJson(keypadDevice?.currentValue("lanResultJson")?.toString(), payload.request_id?.toString())
    renderJson(result ?: [
        device_id: payload.device_id ?: "",
        request_id: payload.request_id ?: "",
        result: "rejected",
        reason: "error",
        message: "No keypad response"
    ])
}

private Map handleDashboardControlAction(Map payload) {
    String expectedDeviceId = keypadDevice?.currentValue("deviceId")?.toString() ?: ""
    String payloadDeviceId = payload.device_id?.toString() ?: ""
    if (expectedDeviceId && payloadDeviceId && payloadDeviceId != expectedDeviceId) {
        if (logEnable) log.debug "Ignoring dashboard action for device_id=${payloadDeviceId}; expected ${expectedDeviceId}"
        return actionResult(payload, "rejected", "wrong_device", "Wrong device")
    }

    String tileId = payload.tile_id?.toString() ?: ""
    def device = controlDeviceForTileId(tileId)
    if (device == null) {
        return actionResult(payload, "rejected", "unknown_tile", "Unknown tile")
    }

    try {
        if (payload.action == "dashboard_control") {
            String controlId = payload.control_id?.toString() ?: ""
            if (controlId == "level" && hasCapability(device, "SwitchLevel")) {
                Integer level = clampLevel(payload.value)
                runIn(1, "publishDashboardFallback")
                device.setLevel(level)
                return actionResult(payload, "accepted", "", "Set ${level}%")
            }
            return actionResult(payload, "rejected", "unsupported_control", "Unsupported")
        }

        String zone = payload.zone?.toString() ?: "primary"
        if (zone == "detail" && hasCapability(device, "SwitchLevel")) {
            return actionResult(payload, "accepted", "", "Detail")
        }

        if (isGarageDoorDevice(device)) {
            String doorValue = garageDoorValue(device)
            if (doorValue in ["opening", "closing"]) {
                return actionResult(payload, "rejected", "moving", "Moving")
            }

            runIn(1, "publishDashboardFallback")
            if (doorValue == "open") {
                device.close()
                return actionResult(payload, "accepted", "", "Closing")
            }

            device.open()
            return actionResult(payload, "accepted", "", "Opening")
        }

        if (isLockDevice(device)) {
            String currentLockValue = lockValue(device)
            if (currentLockValue in ["locking", "unlocking"]) {
                return actionResult(payload, "rejected", "moving", "Moving")
            }

            runIn(1, "publishDashboardFallback")
            if (currentLockValue == "locked") {
                device.unlock()
                return actionResult(payload, "accepted", "", "Unlocking")
            }

            device.lock()
            return actionResult(payload, "accepted", "", "Locking")
        }

        if (isValveDevice(device)) {
            String currentValveValue = valveValue(device)
            runIn(1, "publishDashboardFallback")
            if (currentValveValue == "open") {
                device.close()
                return actionResult(payload, "accepted", "", "Closing")
            }

            device.open()
            return actionResult(payload, "accepted", "", "Opening")
        }

        if (!isSwitchDevice(device)) {
            return actionResult(payload, "rejected", "unsupported_device", "Unsupported")
        }

        runIn(1, "publishDashboardFallback")
        if (device.currentValue("switch") == "on") {
            device.off()
        } else {
            device.on()
        }
        return actionResult(payload, "accepted", "", "Toggled")
    } catch (Exception e) {
        log.warn "Dashboard control failed tile=${tileId}: ${e.message}"
        return actionResult(payload, "rejected", "command_failed", "Failed")
    }
}

private Map actionResult(Map payload, String result, String reason, String message) {
    Map body = [
        device_id: payload.device_id ?: keypadDevice?.currentValue("deviceId") ?: "",
        request_id: payload.request_id ?: "",
        result: result
    ]
    if (reason) body.reason = reason
    if (message) body.message = message
    return body
}

def statusHandler() {
    Map payload = requestJson()
    if (payload == null) {
        return renderJson([ok: false, reason: "bad_json"], 400)
    }

    if (logEnable) log.debug "LAN status payload=${payload}"
    if (payload.ip) {
        keypadDevice?.configureLanPeer(payload.ip.toString())
    }
    publishCurrentSecurityState()
    publishDashboard()
    renderJson(currentKeypadState(payload.device_id?.toString()) + currentDashboardPayload())
}

void eventHandler(evt) {
    if (logEnable) log.debug "Dashboard source event ${evt?.name}=${evt?.value}"
    unschedule("publishDashboardFallback")
    publishDashboard()
}

void dashboardActionHandler(evt) {
    Map payload = parseJson(evt?.value?.toString())
    if (payload == null) {
        return
    }

    Map result = handleDashboardControlAction(payload)
    keypadDevice?.sendResult(result.result?.toString() ?: "accepted",
                             result.reason?.toString() ?: "",
                             result.message?.toString() ?: "")
}

void hsmStatusHandler(evt) {
    if (mirrorHsmToKeypad) {
        publishHsmState(evt?.value?.toString(), hsmDelaySeconds(evt))
    }
    eventHandler(evt)
}

void hsmAlertHandler(evt) {
    String alert = evt?.value ?: ""
    if (alert in ["intrusion", "intrusion-home", "intrusion-night", "smoke", "water", "rule"]) {
        keypadDevice.publishState("alarming")
        if (logEnable) log.debug "HSM alert=${alert}"
    } else if (alert == "arming") {
        keypadDevice.rejectPendingHsmRequest("not_ready")
        publishHsmState(location.hsmStatus?.toString(), null)
        if (logEnable) log.debug "HSM arm rejected: ${evt?.descriptionText ?: "open contact"}"
    } else if (alert == "cancel") {
        publishHsmState(location.hsmStatus?.toString(), null)
    }
}

void publishDashboard() {
    if (keypadDevice == null) {
        log.warn "Dashboard publish skipped; no keypad selected"
        return
    }

    String id = normalizeDashboardId(dashboardId) ?: "home"
    List<Map> summaryItems = buildDashboardItems()
    if (summaryItems) {
        Map payload = [
            title: dashboardTitle ?: "Home",
            items: summaryItems
        ]
        String json = JsonOutput.toJson(payload)
        keypadDevice.publishDashboard(id, json)
        if (logEnable) log.debug "Published dashboard id=${id}, payload=${json}"
    } else {
        keypadDevice.clearDashboard(id)
    }

    Map controlsPayload = buildControlsDashboardPayload()
    if (controlsPayload.items) {
        keypadDevice.publishDashboard("controls", JsonOutput.toJson(controlsPayload))
        if (logEnable) log.debug "Published controls dashboard payload=${JsonOutput.toJson(controlsPayload)}"
    } else {
        keypadDevice.clearDashboard("controls")
    }
}

void publishDashboardFallback() {
    if (logEnable) log.debug "Dashboard fallback refresh"
    publishDashboard()
}

private Map currentDashboardPayload() {
    List<Map> summaryItems = buildDashboardItems()
    return [
        dashboard_id: normalizeDashboardId(dashboardId) ?: "home",
        title: dashboardTitle ?: "Home",
        items: summaryItems,
        controls: buildControlsDashboardPayload()
    ]
}

private void assignInitialAppLabel() {
    String defaultLabel = "ESP32 Wall Keypad Dashboard"
    if (keypadDevice != null && (!app.label || app.label == defaultLabel)) {
        app.updateLabel("${keypadDevice.displayName} Dashboard")
    }
}

private List<Map> buildDashboardItems() {
    List<Map> items = []
    tileOrderIds().each { tileId ->
        Map item = dashboardItemFor(tileId)
        if (item != null) {
            items << item
        }
    }

    return items
}

private Map buildControlsDashboardPayload() {
    return [
        title: "Controls",
        items: selectedControlSlots().collect { slot ->
            controlDashboardItem(slot.device, slot.index)
        }
    ]
}

private Map controlDashboardItem(device, Integer slot) {
    if (device == null) return null

    String tileId = "slot_${slot}"
    if (isGarageDoorDevice(device)) {
        String doorValue = garageDoorValue(device)
        return [
            id: tileId,
            label: device.displayName?.toString() ?: "Slot ${slot}",
            value: displayGarageDoorValue(doorValue),
            state: dashboardStateForGarageDoor(doorValue),
            kind: "barrier",
            primary: !(doorValue in ["opening", "closing"]),
            detail: false
        ]
    }

    if (isLockDevice(device)) {
        String value = lockValue(device)
        return [
            id: tileId,
            label: device.displayName?.toString() ?: "Slot ${slot}",
            value: displayLockValue(value),
            state: dashboardStateForLock(value),
            kind: "barrier",
            primary: !(value in ["locking", "unlocking"]),
            detail: false
        ]
    }

    if (isValveDevice(device)) {
        String value = valveValue(device)
        return [
            id: tileId,
            label: device.displayName?.toString() ?: "Slot ${slot}",
            value: displayValveValue(value),
            state: value == "open" ? "info" : "ok",
            kind: "switch",
            primary: true,
            detail: false
        ]
    }

    if (!isSwitchDevice(device)) {
        return [
            id: tileId,
            label: device.displayName?.toString() ?: "Slot ${slot}",
            value: "Unsupported",
            state: "offline",
            kind: "unsupported",
            primary: false,
            detail: false
        ]
    }

    String switchValue = device.currentValue("switch")?.toString() ?: "off"
    Boolean dimmer = hasCapability(device, "SwitchLevel")
    Integer level = dimmer ? clampLevel(device.currentValue("level")) : null
    String value = dimmer && switchValue == "on" ? "${level}%" : displaySwitchValue(switchValue)

    Map item = [
        id: tileId,
        label: device.displayName?.toString() ?: "Slot ${slot}",
        value: value,
        state: switchValue == "on" ? "info" : "ok",
        kind: dimmer ? "dimmer" : "switch",
        primary: true,
        detail: dimmer
    ]
    if (dimmer) item.level = level
    return item
}

private List selectedControlDevices() {
    [controlDevice1, controlDevice2, controlDevice3, controlDevice4, controlDevice5, controlDevice6]
        .findAll { it != null }
}

private List<Map> selectedControlSlots() {
    [
        [index: 1, device: controlDevice1],
        [index: 2, device: controlDevice2],
        [index: 3, device: controlDevice3],
        [index: 4, device: controlDevice4],
        [index: 5, device: controlDevice5],
        [index: 6, device: controlDevice6]
    ].findAll { it.device != null }
}

private def controlDeviceForTileId(String tileId) {
    if (!(tileId ==~ /^slot_[1-6]$/)) {
        return null
    }
    Integer index = (tileId.substring(5) as Integer) - 1
    List devices = [controlDevice1, controlDevice2, controlDevice3, controlDevice4, controlDevice5, controlDevice6]
    return index >= 0 && index < devices.size() ? devices[index] : null
}

private Boolean hasCapability(device, String capabilityName) {
    try {
        return device?.hasCapability(capabilityName)
    } catch (Exception ignored) {
        return false
    }
}

private Boolean isGarageDoorDevice(device) {
    return device != null && (
        hasCapability(device, "GarageDoorControl") ||
        hasCapability(device, "Garage Door Control") ||
        currentDeviceValue(device, "door") != null
    )
}

private Boolean isLockDevice(device) {
    return device != null && (
        hasCapability(device, "Lock") ||
        currentDeviceValue(device, "lock") != null
    )
}

private Boolean isValveDevice(device) {
    return device != null && (
        hasCapability(device, "Valve") ||
        currentDeviceValue(device, "valve") != null
    )
}

private Boolean isSwitchDevice(device) {
    return device != null && (
        hasCapability(device, "Switch") ||
        currentDeviceValue(device, "switch") != null
    )
}

private String garageDoorValue(device) {
    String doorValue = currentDeviceValue(device, "door")?.toString()?.toLowerCase()
    if (doorValue) return doorValue

    String contactValue = currentDeviceValue(device, "contact")?.toString()?.toLowerCase()
    if (contactValue == "open") return "open"
    if (contactValue == "closed") return "closed"

    String switchValue = currentDeviceValue(device, "switch")?.toString()?.toLowerCase()
    return switchValue == "on" ? "open" : "closed"
}

private String lockValue(device) {
    return currentDeviceValue(device, "lock")?.toString()?.toLowerCase() ?: "unknown"
}

private String valveValue(device) {
    String valveValue = currentDeviceValue(device, "valve")?.toString()?.toLowerCase()
    if (valveValue) return valveValue

    String switchValue = currentDeviceValue(device, "switch")?.toString()?.toLowerCase()
    return switchValue == "on" ? "open" : "closed"
}

private String displayGarageDoorValue(String value) {
    switch ((value ?: "unknown").toLowerCase()) {
        case "open":
            return "Open"
        case "closed":
            return "Closed"
        case "opening":
            return "Opening"
        case "closing":
            return "Closing"
        default:
            return "Unknown"
    }
}

private String displayLockValue(String value) {
    switch ((value ?: "unknown").toLowerCase()) {
        case "locked":
            return "Locked"
        case "unlocked":
            return "Unlocked"
        case "locking":
            return "Locking"
        case "unlocking":
            return "Unlocking"
        default:
            return "Unknown"
    }
}

private String displayValveValue(String value) {
    switch ((value ?: "unknown").toLowerCase()) {
        case "open":
            return "Open"
        case "closed":
            return "Closed"
        default:
            return "Unknown"
    }
}

private String dashboardStateForGarageDoor(String value) {
    switch ((value ?: "unknown").toLowerCase()) {
        case "closed":
            return "ok"
        case "open":
            return "alert"
        case "opening":
        case "closing":
            return "info"
        default:
            return "offline"
    }
}

private String dashboardStateForLock(String value) {
    switch ((value ?: "unknown").toLowerCase()) {
        case "locked":
            return "ok"
        case "unlocked":
            return "alert"
        case "locking":
        case "unlocking":
            return "info"
        default:
            return "offline"
    }
}

private def currentDeviceValue(device, String attributeName) {
    try {
        return device?.currentValue(attributeName)
    } catch (Exception ignored) {
        return null
    }
}

private Integer clampLevel(value) {
    Integer level
    try {
        level = value == null ? 0 : value as Integer
    } catch (Exception ignored) {
        level = 0
    }
    return Math.max(0, Math.min(100, level))
}

private String displaySwitchValue(String value) {
    return value == "on" ? "On" : "Off"
}

private List<String> tileOrderIds() {
    List<String> defaults = ["contacts", "locks", "leak", "switches", "temp", "power", "mode", "hsm"]
    List<String> requested = (tileOrder ?: "")
        .split(",")
        .collect { it.trim().toLowerCase() }
        .findAll { it }
    List<String> known = []

    (requested + defaults).each { tileId ->
        if (defaults.contains(tileId) && !known.contains(tileId)) {
            known << tileId
        }
    }
    return known
}

private Map dashboardItemFor(String tileId) {
    switch (tileId) {
        case "hsm":
            return includeHsm ? securityItem() : null
        case "contacts":
            return contactsItem()
        case "locks":
            return locksItem()
        case "leak":
            return leaksItem()
        case "switches":
            return switchesItem()
        case "temp":
            return temperaturesItem()
        case "power":
            return powerItem()
        case "mode":
            return includeMode ? [label: "Mode", value: location.mode?.toString() ?: "Unknown", state: "info"] : null
        default:
            return null
    }
}

private Map securityItem() {
    String raw = currentHsmStatus()
    return [
        label: "Security",
        value: displaySecurityStatus(raw),
        state: dashboardStateForSecurity(raw)
    ]
}

private String currentHsmStatus() {
    try {
        return location.hsmStatus?.toString() ?: "unknown"
    } catch (Exception ignored) {
        return "unknown"
    }
}

private void publishHsmState(String hsmStatus, Integer delaySeconds) {
    if (keypadDevice == null) return

    String keypadState = keypadStateForHsmStatus(hsmStatus)
    if (keypadState == null) return

    // The driver owns the initial exit countdown. Do not overwrite it with
    // HSM's unnumbered arming status event.
    if (keypadState == "exit_delay" && delaySeconds == null) return

    if (delaySeconds != null) {
        keypadDevice.publishState(keypadState, delaySeconds)
    } else {
        keypadDevice.publishState(keypadState)
    }
    if (logEnable) log.debug "HSM status=${hsmStatus} keypad_state=${keypadState}${delaySeconds != null ? " delay_remaining=${delaySeconds}" : ""}"
}

private Map parseResultJson(String json, String requestId) {
    if (!json) {
        return null
    }

    try {
        Map result = new JsonSlurper().parseText(json) as Map
        return result.request_id == requestId ? result : null
    } catch (Exception e) {
        if (logEnable) log.debug "LAN result JSON parse failed: ${e.message}"
        return null
    }
}

private Map requestJson() {
    try {
        if (request?.JSON instanceof Map) {
            return request.JSON as Map
        }
    } catch (Exception ignored) {
    }

    try {
        String body = request?.body ?: ""
        return body ? new JsonSlurper().parseText(body) as Map : null
    } catch (Exception e) {
        if (logEnable) log.debug "LAN JSON parse failed: ${e.message}"
        return null
    }
}

private Map parseJson(String json) {
    try {
        return json ? new JsonSlurper().parseText(json) as Map : null
    } catch (Exception e) {
        if (logEnable) log.debug "JSON parse failed: ${e.message}; payload=${json}"
        return null
    }
}

private void renderJson(Map payload, Integer status = 200) {
    render status: status, contentType: "application/json", data: JsonOutput.toJson(payload)
}

private Map currentKeypadState(String requestedDeviceId = null) {
    String deviceId = requestedDeviceId ?: keypadDevice?.currentValue("deviceId")?.toString() ?: ""
    String hsmState = currentSecurityKeypadState()
    String readyValue = keypadDevice?.currentValue("ready")?.toString()
    String armedState = hsmState ?: keypadDevice?.currentValue("alarmState")?.toString() ?: "disarmed"
    Boolean ready = hsmState != null ? true : readyValue == null ? true : readyValue.toBoolean()

    return [
        ok: true,
        device_id: deviceId,
        ready: ready,
        armed_state: armedState
    ]
}

private String lanActionUrl() {
    ensureAccessToken()
    return "${getFullLocalApiServerUrl()}/action?access_token=${state.accessToken}"
}

private String pairingInstructions() {
    return "After keypad setup, open the keypad Network page. Enter its IP and pairing code here, then tap Pair keypad."
}

private void ensureAccessToken() {
    try {
        if (!state.accessToken) {
            createAccessToken()
        }
    } catch (Exception e) {
        log.warn "Could not create access token. Enable OAuth for this app code, then open the app again. ${e.message}"
    }
}

private void pairKeypad() {
    ensureAccessToken()

    String ip = keypadIp?.trim()
    String code = keypadPairingCode?.trim()
    if (!ip || !code) {
        log.warn "Pairing skipped; keypad IP and pairing code are required"
        return
    }

    Map body = [
        device_id: keypadDevice?.currentValue("deviceId") ?: "",
        pairing_code: code,
        action_url: lanActionUrl(),
        protocol: 1
    ]

    try {
        httpPost([
            uri: "http://${ip}/pair",
            requestContentType: "application/json",
            contentType: "application/json",
            body: JsonOutput.toJson(body),
            timeout: 5
        ]) { resp ->
            if (resp.status >= 200 && resp.status < 300) {
                state.pairedDeviceId = body.device_id ?: "keypad"
                state.pairedKeypadIp = ip
                state.pairedAt = now()
                keypadDevice?.configureLanPeer(ip)
                publishCurrentSecurityState()
                runIn(1, "publishDashboard")
                log.info "LAN paired keypadIp=${ip}"
            } else {
                log.warn "LAN pair failed status=${resp.status}"
            }
        }
    } catch (Exception e) {
        log.warn "LAN pair failed: ${e.message}"
    }
}

private void publishCurrentSecurityState() {
    String keypadState = currentSecurityKeypadState()
    if (keypadState != null) {
        keypadDevice?.publishState(keypadState)
    } else {
        keypadDevice?.refresh()
    }
}

private Integer hsmDelaySeconds(evt) {
    try {
        def seconds = evt?.data instanceof Map ? evt.data.seconds : null
        return seconds == null ? null : Math.max(0, seconds as Integer)
    } catch (Exception ignored) {
        return null
    }
}

private String currentSecurityKeypadState() {
    return mirrorHsmToKeypad ? keypadStateForHsmStatus(location.hsmStatus?.toString()) : null
}

private String keypadStateForHsmStatus(String hsmStatus) {
    switch (hsmStatus) {
        case "armedAway":
            return "armed_away"
        case "armingAway":
        case "armingHome":
        case "armingNight":
            return "exit_delay"
        case "armedHome":
        case "armedNight":
            return "armed_stay"
        case "disarmed":
        case "allDisarmed":
            return "disarmed"
        default:
            return null
    }
}

private Map contactsItem() {
    List devices = selectedDevices(contactDevices)
    if (devices.isEmpty()) return null

    Integer openCount = devices.count { it.currentValue("contact") == "open" }
    if (openCount > 0) {
        return [label: "Doors", value: "${openCount} open", state: "alert"]
    }
    return [label: "Doors", value: "All closed", state: "ok"]
}

private Map locksItem() {
    List devices = selectedDevices(lockDevices)
    if (devices.isEmpty()) return null

    Integer unlockedCount = devices.count { it.currentValue("lock") != "locked" }
    if (unlockedCount > 0) {
        return [label: "Locks", value: "${unlockedCount} unlocked", state: "warn"]
    }
    return [label: "Locks", value: "All locked", state: "ok"]
}

private Map leaksItem() {
    List devices = selectedDevices(waterDevices)
    if (devices.isEmpty()) return null

    Integer wetCount = devices.count { it.currentValue("water") == "wet" }
    if (wetCount > 0) {
        return [label: "Leak", value: "${wetCount} wet", state: "alert"]
    }
    return [label: "Leak", value: "Dry", state: "ok"]
}

private Map switchesItem() {
    List devices = selectedDevices(switchDevices)
    if (devices.isEmpty()) return null

    Integer onCount = devices.count { it.currentValue("switch") == "on" }
    if (onCount > 0) {
        return [label: "Lights", value: "${onCount} on", state: "info"]
    }
    return [label: "Lights", value: "All off", state: "ok"]
}

private Map temperaturesItem() {
    List values = selectedDevices(temperatureDevices)
        .collect { numericValue(it.currentValue("temperature")) }
        .findAll { it != null }
    if (values.isEmpty()) return null

    BigDecimal average = (values.sum() / values.size()).setScale(0, BigDecimal.ROUND_HALF_UP)
    return [label: "Temp", value: "${average}°F", state: "info"]
}

private Map powerItem() {
    List values = selectedDevices(powerDevices)
        .collect { numericValue(it.currentValue("power")) }
        .findAll { it != null }
    if (values.isEmpty()) return null

    BigDecimal total = values.sum().setScale(0, BigDecimal.ROUND_HALF_UP)
    return [label: "Power", value: "${total}W", state: total > 0 ? "info" : "ok"]
}

private List selectedDevices(devices) {
    if (devices == null) return []
    if (devices instanceof Collection) return devices as List
    return [devices]
}

private BigDecimal numericValue(value) {
    try {
        return value == null ? null : value as BigDecimal
    } catch (Exception ignored) {
        return null
    }
}

private String displaySecurityStatus(String status) {
    switch ((status ?: "unknown").toLowerCase()) {
        case "disarmed":
            return "Disarmed"
        case "armedhome":
        case "armed home":
        case "armed_home":
            return "Armed Home"
        case "armednight":
        case "armed night":
        case "armed_night":
            return "Armed Night"
        case "armedaway":
        case "armed away":
        case "armed_away":
            return "Armed Away"
        case "arminghome":
        case "armingaway":
        case "armingnight":
        case "arming":
            return "Arming"
        case "entrydelay":
        case "entry delay":
        case "entry_delay":
            return "Entry Delay"
        case "intrusion":
        case "alarming":
        case "alarm":
            return "Alarm"
        default:
            return "Unknown"
    }
}

private String dashboardStateForSecurity(String status) {
    switch ((status ?: "unknown").toLowerCase()) {
        case "disarmed":
            return "ok"
        case "armedhome":
        case "armed home":
        case "armed_home":
        case "armednight":
        case "armed night":
        case "armed_night":
            return "info"
        case "armedaway":
        case "armed away":
        case "armed_away":
            return "alert"
        case "arminghome":
        case "armingaway":
        case "armingnight":
        case "arming":
        case "entrydelay":
        case "entry delay":
        case "entry_delay":
            return "warn"
        case "intrusion":
        case "alarming":
        case "alarm":
            return "alert"
        default:
            return "offline"
    }
}

private String normalizeDashboardId(String value) {
    String id = value?.toString()?.trim()?.toLowerCase()
    return id ==~ /^[a-z0-9_-]+$/ ? id : null
}
