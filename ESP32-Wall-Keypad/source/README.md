# ESP32-S3 Wall Keypad

Prototype firmware for a modern wall-mounted keypad/access-control interface on the Waveshare ESP32-S3 3.5inch LCD Touch Display Development Board.

The Iris 3405 keypad is only a physical and ergonomic reference. This project does not electrically or mechanically emulate the Iris device.

## Target Hardware

- Waveshare ESP32-S3 3.5inch LCD Touch Display Development Board
- ESP32-S3
- 320x480 IPS display, portrait orientation
- Capacitive touch
- USB-C programming/power
- 8 MB PSRAM
- 16 MB flash
- KBE-1511B speaker through the board audio codec

Board-specific Waveshare examples/docs are the hardware authority for pins and initialization. Do not replace these with generic ESP32-S3 display assumptions.

## Current Firmware

The current firmware is an ESP-IDF project. It has been tested from VS Code/terminal on macOS using ESP-IDF 6.0.2.

Implemented so far:

- ST7796 display initialization
- FT6336 capacitive touch initialization
- LVGL keypad UI
- Home summary screen
- Swipe navigation between Home, Keypad, and Dashboard screens
- Conventional numeric credential entry
- ARM and PARTIAL action buttons
- Hamburger menu shell with auto-close
- ES8311/I2S key-click audio feedback
- Wi-Fi station connection
- MQTT action publishing
- MQTT retained state subscription
- MQTT retained dashboard subscription through `dashboard/+`
- Backlight dim after inactivity and touch-to-wake
- Initial Hubitat driver
- Initial Hubitat dashboard app scaffold

The firmware supports two-slot OTA updates with boot confirmation and an on-device Firmware screen. Security decisions are intentionally owned by the Hubitat driver/application layer rather than by the keypad firmware.

## Local Configuration

Wi-Fi and MQTT settings are configured locally on the device and saved to ESP32 NVS. Release firmware does not require private settings to be compiled into the binary.

On first boot, or whenever saved settings are incomplete, the keypad enters setup mode and starts a temporary Wi-Fi access point. Join that AP and open `http://192.168.4.1` to enter:

- device id
- home Wi-Fi SSID/password
- MQTT broker URI
- optional MQTT username/password

Leave the MQTT username and password empty for an unauthenticated local broker.

Developers can still force setup mode with a local build define, for example `-DFORCE_SETUP_MODE=1`.

## Setup Mode

Setup mode is the first step toward new-out-of-box provisioning. It provides a temporary AP and local web page that saves configuration to ESP32 NVS.

Setup mode can be reached from the keypad hamburger menu by tapping `Network`, then `Setup`. The keypad saves a setup request, restarts into setup mode, then clears the request after the setup page saves successfully.

To remove the device from a home, tap Network, then Reset, then Erase & restart. This deliberately separate confirmation erases the keypad's saved Wi-Fi, MQTT, Hubitat LAN, and device ID settings from its own NVS namespace before rebooting into setup mode.

When setup mode is active, the device:

- shows a setup screen instead of the keypad
- starts an open temporary Wi-Fi AP named like `Keypad-Setup-6C19` on a fresh/reset device
- serves a local setup page at `http://192.168.4.1`
- does not connect to the home Wi-Fi or MQTT broker
- saves submitted settings to NVS
- restarts after saving

The setup page collects:

- device id
- home Wi-Fi SSID/password
- MQTT broker URI, such as `mqtt://192.168.1.50:1883`; `192.168.1.50:1883` is also accepted
- MQTT username/password, optional

Password fields are intentionally not prefilled when the page is shown.

`FORCE_SETUP_MODE` can still be set to `1` locally for developer testing without using the menu.

## Build And Flash

From the repository root:

```sh
source /Users/john/.espressif/v6.0.2/esp-idf/export.sh
idf.py build
idf.py -p /dev/cu.usbmodem334301 flash
idf.py -p /dev/cu.usbmodem334301 monitor
```

Exit the serial monitor with `Ctrl-]`.

If the serial device changes, list ports with:

```sh
ls /dev/cu.*
```

## Release Build

`version.txt` is the firmware version shown on the keypad and published in its MQTT status. Set it deliberately before creating a release.

Create the single factory-install image after a successful build:

```sh
idf.py merge-bin -o ESP32-Wall-Keypad-0.2.5.bin -f raw
shasum -a 256 build/ESP32-Wall-Keypad-0.2.5.bin
```

The merged image includes the bootloader, partition table, and application. It is flashed at offset `0x0` by the browser installer. The smaller `build/keypad.bin` is the application-only image that will be used by OTA updates.

### Browser Installer

The browser installer source is in `install/`:

- `install/index.html` is the GitHub Pages Web Serial page.
- `install/manifest.json` is its ESP Web Tools manifest.

For a release, copy the merged image into `install/`, update the version and filename in `install/manifest.json`, then publish that directory to the GitHub Pages site. The browser installer always uses the merged image at offset `0`; it must never be pointed at `build/keypad.bin`.

`install/ota-manifest.json` is the separate HTTPS release feed consumed by the keypad itself. For an OTA release, update its `version` and `url`, then publish the application-only `build/keypad.bin` at that URL. The OTA image must not be merged and must not be flashed at offset `0`.

## MQTT Contract

The MQTT topic prefix is derived from the keypad `device_id` as `keypad/<device_id>`. The examples below use:

```text
keypad/front_door
```

### Device Availability

Topic:

```text
keypad/front_door/availability
```

Payloads:

```text
online
offline
```

The firmware publishes `online` after MQTT connects and configures `offline` as the retained MQTT last will.

### Device Status

Topic:

```text
keypad/front_door/status
```

Retained payload example:

```json
{"device_id":"front_door","ready":true,"armed_state":"disarmed","wifi_connected":true,"mqtt_connected":true,"ip":"192.168.7.183","firmware":"8450212-dirty","topic_prefix":"keypad/front_door","last_request_id":"front_door-000001"}
```

The firmware publishes status after MQTT connects and after accepted driver state updates. This topic describes the keypad device, its current view of the driver state, and the most recent keypad action request ID if one has been sent since boot.

### Device To Driver Actions

Topic:

```text
keypad/front_door/action
```

Published when the keypad requests an alarm/access action.

Disarm example:

```json
{"device_id":"front_door","request_id":"front_door-000001","uptime_ms":123456,"action":"disarm","credential":"1234","credential_length":4}
```

Arm away example:

```json
{"device_id":"front_door","request_id":"front_door-000002","uptime_ms":130250,"action":"arm_away","credential":"","credential_length":0}
```

Arm stay/partial example:

```json
{"device_id":"front_door","request_id":"front_door-000003","uptime_ms":134900,"action":"arm_stay","credential":"1234","credential_length":4}
```

Current actions are published with QoS 1 and are not retained. Each action includes a boot-local `request_id` and `uptime_ms` for driver logs/correlation. The keypad publishes the action with the currently entered credential, including an empty credential. The driver decides whether a credential is required and whether it is valid for the requested action.

### Driver To Device Results

Topic:

```text
keypad/front_door/result
```

Optional non-retained response after a keypad action:

```json
{"device_id":"front_door","request_id":"front_door-000001","result":"rejected","reason":"invalid_code","message":"Invalid code"}
```

The firmware subscribes with QoS 1. `device_id` is optional; mismatched IDs are ignored. `request_id`, `reason`, and `message` are optional. The keypad briefly displays the result line, then returns to `READY` or `OFFLINE`. `reason` is machine-readable for driver logs; `message` is human-readable screen text.

Known `result` values used by the UI:

- `accepted`
- `rejected`
- `invalid_code`
- `not_ready`
- `error`

### Driver To Device Commands

Topic:

```text
keypad/front_door/command
```

Optional non-retained command payload:

```json
{"device_id":"front_door","command":"beep"}
```

The firmware subscribes with QoS 1. `device_id` is optional; mismatched IDs are ignored.

Known commands:

- `beep` plays the same short local chirp used for key feedback
- `siren` starts a repeating local alert warble
- `strobe` starts a red screen flash
- `both` starts the warble and screen flash together
- `off` stops local alert output

### Driver/App To Device Dashboards

Dashboard topics use a one-level dashboard id:

```text
keypad/front_door/dashboard/{dashboard_id}
```

Examples:

```text
keypad/front_door/dashboard/hsm
keypad/front_door/dashboard/climate
keypad/front_door/dashboard/doors
keypad/front_door/dashboard/power
```

The firmware subscribes to:

```text
keypad/front_door/dashboard/+
```

`dashboard_id` is used for routing and as a fallback screen title. The JSON `title` field is the human label shown on the keypad and does not have to match the topic id.

Expected retained payload:

```json
{
  "device_id": "front_door",
  "title": "HSM",
  "items": [
    {"label": "Security", "value": "Armed Away", "state": "alert"},
    {"label": "Doors", "value": "All closed", "state": "ok"},
    {"label": "Locks", "value": "All locked", "state": "ok"},
    {"label": "Leak", "value": "1 wet", "state": "alert"}
  ]
}
```

The firmware currently displays the most recently received dashboard payload on the dashboard screen. It stores up to twelve items and shows six at a time; if more than six are present, the screen auto-rotates pages while the dashboard is visible. Swipe up/down on the dashboard moves to the next/previous page and holds that page for 60 seconds before auto-rotation resumes. `device_id` is optional; mismatched IDs are ignored. Item `state` controls color and currently supports:

- `ok`
- `info`
- `warn`
- `alert`
- `offline`

### Driver To Device State

Topic:

```text
keypad/front_door/state
```

Expected retained payload:

```json
{"ready":true,"armed_state":"disarmed"}
```

Delay states may include an optional countdown:

```json
{"ready":true,"armed_state":"exit_delay","delay_remaining":5}
```

An optional `device_id` field may be included. If present and it does not match this keypad's configured `DEVICE_ID`, the firmware ignores the state payload.

Known `armed_state` values used by the UI:

- `disarmed`
- `armed_away`
- `armed_stay`
- `arming`
- `exit_delay`
- `entry_delay`
- `alarming`
- `unknown`

Unknown `armed_state` values are logged and displayed as `unknown`.

When `ready` is false, the UI displays `OFFLINE`. When `ready` is true, the UI displays the current alarm state. Delay states use amber/orange status text: `arming` and `exit_delay` display `ARMING` / `EXIT DELAY`; `entry_delay` displays `ENTER CODE` / `ENTRY DELAY`. When `delay_remaining` is present, the detail line includes the countdown.

## UI Behavior

- Normal boot shows the Home summary screen.
- Swipe left/right moves between `Keypad`, `Home`, and `Dashboard`, with Home as the center/default screen.
- Swipe up/down on a multi-page dashboard moves to the next/previous dashboard page and pauses auto-rotation briefly.
- The Home screen has direct `Keypad` and `Dashboard` buttons.
- Entry delay forces the Keypad screen.
- Alarm/alarming state forces the Dashboard screen.
- Non-home screens return to Home after the inactivity timeout.
- Numeric buttons append digits.
- Backspace removes the most recent digit.
- OK publishes a `disarm` action with the entered credential, then clears entry.
- ARM publishes `arm_away` with the entered credential, then clears entry.
- PARTIAL publishes `arm_stay` with the entered credential, then clears entry.
- OK, ARM, and PARTIAL publish with an empty credential if no digits have been entered.
- Driver result messages briefly replace the `READY`/`OFFLINE` detail line.
- The hamburger menu opens `Status` and `Network` options and closes automatically after 10 seconds.
- The `Network` menu shows current Wi-Fi, IP, action transport, MQTT, Device ID, and Hubitat LAN details before offering setup mode.
- The display dims after 30 seconds of inactivity.
- Touching the screen while dimmed wakes it. The first dimmed tap on a real control is consumed as wake-only.

## Hubitat Driver

Initial driver source:

```text
hubitat/ESP32WallKeypadDriver.groovy
```

Install it in Hubitat under **Drivers Code**, then create a virtual device using driver type **ESP32 Wall Keypad**.

Initial preferences:

- MQTT broker host or URI, for example `192.168.7.43` or `tcp://192.168.7.43:1883`
- MQTT broker port, usually `1883`
- MQTT username/password, optional
- keypad `device_id`, for example `front_door`
- optional valid disarm credential
- maximum managed code slots
- whether disarm requires a valid credential
- whether arm actions require any credential
- optional exit delay seconds before publishing the final armed state
- optional entry delay seconds before publishing `alarming`

The first driver is intentionally simple. It:

- connects directly to the MQTT broker using Hubitat's built-in MQTT interface
- subscribes to keypad `/action`, `/availability`, and `/status`
- publishes non-retained `/result` feedback
- publishes retained `/state`
- stores useful device attributes such as alarm state, ready state, availability, last action, last request ID, keypad IP, and firmware
- reports configured `entryDelay` and `exitDelay` as Current States because Hubitat command inputs do not show current values
- exposes Hubitat's built-in `Actuator` and `SecurityKeypad` capabilities while keeping the richer custom `alarmState` attribute for delay/alarm states
- stores encrypted lock-code slots in the Hubitat driver for Lock Code Manager
- publishes retained dashboard payloads through `publishDashboard(dashboardId, dashboardJson)`
- clears stale retained dashboard payloads through `clearDashboard(dashboardId)`

Default behavior:

- `disarm` publishes `accepted` and retained `disarmed` when credential policy passes
- `arm_away` publishes `accepted`, then retained `exit_delay` countdown states if configured, then retained `armed_away`
- `arm_stay` publishes `accepted`, then retained `exit_delay` countdown states if configured, then retained `armed_stay`
- `disarm` cancels any pending exit-delay or entry-delay transition
- invalid or missing disarm credentials while armed can publish retained `entry_delay` countdown states, then retained `alarming`
- valid `disarm` cancels any pending entry-delay transition
- rejected requests publish `/result` with a machine-readable `reason` and human-readable `message`

Hubitat `SecurityKeypad` compatibility:

- `armAway()` publishes the same state sequence as a keypad `arm_away` request
- `armHome()` publishes the same state sequence as a keypad `arm_stay` request
- `disarm()` publishes the same state sequence as a trusted Hubitat disarm request
- `beep()` publishes a non-retained `/command` beep for the keypad speaker
- `both()`, `siren()`, and `strobe()` publish retained `alarming` state and matching local output commands
- `off()` clears the Hubitat alarm attribute and publishes the same state sequence as a trusted Hubitat disarm request
- `setCode()`, `deleteCode()`, `getCodes()`, and `setCodeLength()` manage encrypted Hubitat-side code slots
- `setExitDelay()` and `setEntryDelay()` update the driver delay preferences
- native `securityKeypad` mirrors `disarmed`, `armed home`, `armed away`, or `unknown`
- detailed states such as `exit_delay`, `entry_delay`, and `alarming` remain available through `alarmState`

Credential evaluation:

- The keypad does not store or validate PINs.
- The keypad sends the entered credential in `/action`; the Hubitat driver validates it against managed code slots.
- If no managed code slots exist, the older `validDisarmCredential` preference remains a temporary fallback.
- Managed code values are stored encrypted in Hubitat driver state and published through the standard `lockCodes` attribute.

## Hubitat Dashboard App

Initial dashboard app source:

```text
hubitat/ESP32WallKeypadDashboardApp.groovy
```

Install it in Hubitat under **Apps Code**, then add a user app instance named **ESP32 Wall Keypad Dashboard**.

The dashboard app:

- selects one ESP32 wall keypad driver device
- publishes dashboard JSON by calling the driver's `publishDashboard(dashboardId, dashboardJson)` command
- pairs LAN keypads with a six-digit code and provides the local `/action` and `/status` endpoints
- optionally subscribes to Hubitat Safety Monitor status
- optionally summarizes selected contact sensors, locks, leak sensors, switches/lights, temperature sensors, power meters, and Hubitat mode
- lets the user choose tile priority with a comma-separated order such as `contacts,locks,leak,switches,temp,power,mode,hsm`
- publishes ordered dashboard items over MQTT and paired LAN; the keypad shows six at a time and rotates pages when more are present
- provides a **Publish dashboard now** app button for manual testing

This app is the filtered Hubitat-to-keypad dashboard and LAN pairing layer. It avoids using Hubitat's unfiltered event stream and lets the user choose which devices are relevant to this wall keypad.

## Hubitat Package Manager

The HPM package lives in `hubitat/`:

- `packageManifest.json` installs the required keypad driver and offers the dashboard app as an optional component.

For the first public HPM release, publish the Groovy files and package manifest to:

```text
https://github.com/csteele-pd/Hubitat-public/tree/main/ESP32-Wall-Keypad/hubitat
```

Then test HPM's **Install from a URL** path with:

```text
https://raw.githubusercontent.com/csteele-pd/Hubitat-public/main/ESP32-Wall-Keypad/hubitat/packageManifest.json
```

The driver is required because it owns the keypad protocol and fan-out. The dashboard app is optional for MQTT-only use, but required for LAN pairing and dashboard tiles.
