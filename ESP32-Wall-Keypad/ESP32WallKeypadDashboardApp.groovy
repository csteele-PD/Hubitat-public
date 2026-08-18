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
 * dashboard through the keypad MQTT driver.
 *
 * csteele: v0.1.0  Initial build
 */
/// DEVELOPMENT FORK -- use "^.*///.*\n" OR "^.*///.*\n" to remove the lines starting with /// OR containing /// -- think about the impact.

public static String version()	{  return "v0.1.0"  }

import groovy.json.JsonOutput

definition(
    name: "ESP32 Wall Keypad Dashboard",
    namespace: "keypad",
    author: "John / OpenAI",
    description: "Publishes selected Hubitat status summaries to an ESP32 wall keypad dashboard.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/csteele-pd/Hubitat-public/main/ESP32-Wall-Keypad/hubitat/ESP32WallKeypadDashboardApp.groovy"
)

preferences {
    page(name: "mainPage", title: "ESP32 Wall Keypad Dashboard", install: true, uninstall: true) {
        section("General") {
            label title: "Name for this application", required: false, submitOnChange: true
        }

        section("Keypad") {
            input name: "keypadDevice",
                  type: "capability.securityKeypad",
                  title: "Wall keypad device",
                  required: true,
                  multiple: false
        }

        section("Dashboard") {
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

        section("Options") {
            input name: "publishNowButton",
                  type: "button",
                  title: "Publish dashboard now"
            input name: "logEnable",
                  type: "bool",
                  title: "Enable debug logging",
                  required: true,
                  defaultValue: true
        }
    }
}

void appButtonHandler(String buttonName) {
    if (buttonName == "publishNowButton") {
        // Hubitat saves changed preferences after appButtonHandler returns.
        runIn(1, "publishDashboard")
    }
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
    if (includeHsm || mirrorHsmToKeypad) subscribe(location, "hsmStatus", "hsmStatusHandler")
    if (mirrorHsmToKeypad) subscribe(location, "hsmAlert", "hsmAlertHandler")
    if (includeMode) subscribe(location, "mode", eventHandler)
    if (!selectedDevices(contactDevices).isEmpty()) subscribe(contactDevices, "contact", eventHandler)
    if (!selectedDevices(lockDevices).isEmpty()) subscribe(lockDevices, "lock", eventHandler)
    if (!selectedDevices(waterDevices).isEmpty()) subscribe(waterDevices, "water", eventHandler)
    if (!selectedDevices(switchDevices).isEmpty()) subscribe(switchDevices, "switch", eventHandler)
    if (!selectedDevices(temperatureDevices).isEmpty()) subscribe(temperatureDevices, "temperature", eventHandler)
    if (!selectedDevices(powerDevices).isEmpty()) subscribe(powerDevices, "power", eventHandler)
    if (mirrorHsmToKeypad) publishHsmState(location.hsmStatus?.toString(), null)
    runIn(1, "publishDashboard")
}

void eventHandler(evt) {
    if (logEnable) log.debug "Dashboard source event ${evt?.name}=${evt?.value}"
    publishDashboard()
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

    Map payload = [
        title: dashboardTitle ?: "Home",
        items: buildDashboardItems()
    ]

    String id = normalizeDashboardId(dashboardId) ?: "home"
    String json = JsonOutput.toJson(payload)
    keypadDevice.publishDashboard(id, json)
    if (logEnable) log.debug "Published dashboard id=${id}, payload=${json}"
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

    if (items.isEmpty()) {
        items << [label: "Dashboard", value: "No items", state: "offline"]
    }

    return items
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

private Integer hsmDelaySeconds(evt) {
    try {
        def seconds = evt?.data instanceof Map ? evt.data.seconds : null
        return seconds == null ? null : Math.max(0, seconds as Integer)
    } catch (Exception ignored) {
        return null
    }
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
