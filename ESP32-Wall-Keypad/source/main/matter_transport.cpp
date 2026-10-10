#include "matter_transport.h"

#include "esp_log.h"
#include "sdkconfig.h"

#include <inttypes.h>
#include <string.h>

static const char *TAG = "keypad_matter";

#if CONFIG_ESP_MATTER_ENABLE_MATTER_SERVER

#include "esp_heap_caps.h"

#include <esp_matter.h>
#include <esp_matter_core.h>
#include <app-common/zap-generated/ids/Attributes.h>
#include <app-common/zap-generated/ids/Clusters.h>
#include <app/server/Server.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <app/reporting/reporting.h>
#include <platform/CHIPDeviceEvent.h>
#include <platform/CHIPDeviceLayer.h>
#include <setup_payload/ManualSetupPayloadGenerator.h>

using namespace chip::app::Clusters;
using namespace esp_matter;

static uint16_t matter_keypad_endpoint_id;
static bool matter_started;
static bool matter_last_on;
static bool matter_has_last_on;
static bool matter_internal_update;
static bool matter_pending_on;
static bool matter_apply_scheduled;
static bool matter_commissioned;

static constexpr uint32_t kMatterSetupPin = 20202021;
static constexpr uint16_t kMatterSetupDiscriminator = 3840;

static esp_err_t matter_build_manual_pairing_code(char *buffer, size_t buffer_size)
{
    if (buffer == nullptr || buffer_size == 0) {
        return ESP_ERR_INVALID_ARG;
    }

    buffer[0] = '\0';
    chip::PayloadContents payload;
    payload.version = 0;
    payload.commissioningFlow = chip::CommissioningFlow::kStandard;
    payload.discriminator.SetLongValue(kMatterSetupDiscriminator);
    payload.setUpPINCode = kMatterSetupPin;

    chip::MutableCharSpan manual_code(buffer, buffer_size);
    CHIP_ERROR err = chip::ManualSetupPayloadGenerator(payload).payloadDecimalStringRepresentation(manual_code);
    if (err != CHIP_NO_ERROR) {
        ESP_LOGW(TAG, "Matter manual pairing code generation failed: %" CHIP_ERROR_FORMAT, err.Format());
        return ESP_FAIL;
    }

    if (manual_code.size() >= buffer_size) {
        buffer[buffer_size - 1] = '\0';
    } else {
        buffer[manual_code.size()] = '\0';
    }
    return ESP_OK;
}

static esp_err_t matter_update_and_report_on_off(bool on)
{
    esp_matter_attr_val_t val = esp_matter_attr_val(on);
    matter_internal_update = true;
    esp_err_t err = attribute::report(matter_keypad_endpoint_id,
                                      OnOff::Id,
                                      OnOff::Attributes::OnOff::Id,
                                      &val);
    matter_internal_update = false;
    if (err != ESP_OK) {
        return err;
    }

    return err;
}

static void matter_apply_on_off_work(intptr_t arg)
{
    (void)arg;
    const bool on = matter_pending_on;
    matter_apply_scheduled = false;

    if (!matter_started || matter_keypad_endpoint_id == 0) {
        return;
    }

    esp_err_t err = matter_update_and_report_on_off(on);
    if (err == ESP_OK) {
        ESP_LOGI(TAG, "Matter on/off applied value=%s", on ? "on" : "off");
    } else {
        ESP_LOGW(TAG, "Matter on/off apply failed: %s", esp_err_to_name(err));
    }
}

static esp_err_t matter_schedule_on_off_apply(bool on)
{
    matter_pending_on = on;
    if (matter_apply_scheduled) {
        return ESP_OK;
    }

    matter_apply_scheduled = true;
    CHIP_ERROR err = chip::DeviceLayer::PlatformMgr().ScheduleWork(matter_apply_on_off_work, 0);
    if (err != CHIP_NO_ERROR) {
        matter_apply_scheduled = false;
        ESP_LOGW(TAG, "Matter on/off schedule failed: %" CHIP_ERROR_FORMAT, err.Format());
        return ESP_FAIL;
    }

    return ESP_OK;
}

static esp_err_t matter_attribute_callback(attribute::callback_type_t type,
                                           uint16_t endpoint_id,
                                           uint32_t cluster_id,
                                           uint32_t attribute_id,
                                           esp_matter_attr_val_t *val,
                                           void *priv_data)
{
    if ((type != attribute::PRE_UPDATE && type != attribute::POST_UPDATE) ||
        endpoint_id != matter_keypad_endpoint_id ||
        cluster_id != OnOff::Id ||
        attribute_id != OnOff::Attributes::OnOff::Id ||
        val == nullptr) {
        return ESP_OK;
    }

    if (matter_internal_update) {
        return ESP_OK;
    }

    if (type == attribute::PRE_UPDATE) {
        ESP_LOGI(TAG, "Matter on/off command observed endpoint=%u value=%s", endpoint_id, val->val.b ? "on" : "off");
    } else {
        ESP_LOGI(TAG,
                 "Matter on/off external update accepted value=%s keypad_truth=%s%s",
                 val->val.b ? "on" : "off",
                 matter_has_last_on ? (matter_last_on ? "on" : "off") : "unknown",
                 matter_has_last_on && val->val.b != matter_last_on ? " pending next keypad truth update" : "");
    }
    return ESP_OK;
}

static bool armed_state_is_matter_on(const char *armed_state, bool ready)
{
    if (!ready || armed_state == nullptr) {
        return false;
    }
    return strcmp(armed_state, "armed_away") == 0 ||
           strcmp(armed_state, "arm_away") == 0 ||
           strcmp(armed_state, "armed_stay") == 0 ||
           strcmp(armed_state, "arm_stay") == 0 ||
           strcmp(armed_state, "partial") == 0 ||
           strcmp(armed_state, "arming") == 0 ||
           strcmp(armed_state, "exit_delay") == 0 ||
           strcmp(armed_state, "entry_delay") == 0 ||
           strcmp(armed_state, "alarming") == 0 ||
           strcmp(armed_state, "alarm") == 0;
}

static void matter_event_callback(const chip::DeviceLayer::ChipDeviceEvent *event, intptr_t arg)
{
    if (event == nullptr) {
        return;
    }

    switch (event->Type) {
    case chip::DeviceLayer::DeviceEventType::kCommissioningWindowOpened:
        ESP_LOGI(TAG, "commissioning window opened");
        break;
    case chip::DeviceLayer::DeviceEventType::kCommissioningWindowClosed:
        ESP_LOGI(TAG, "commissioning window closed");
        break;
    case chip::DeviceLayer::DeviceEventType::kCommissioningSessionStarted:
        ESP_LOGI(TAG, "commissioning session started");
        break;
    case chip::DeviceLayer::DeviceEventType::kCommissioningSessionStopped:
        ESP_LOGI(TAG, "commissioning session stopped");
        break;
    case chip::DeviceLayer::DeviceEventType::kFabricCommitted:
        matter_commissioned = true;
        ESP_LOGI(TAG, "fabric committed");
        break;
    case chip::DeviceLayer::DeviceEventType::kFabricRemoved:
        matter_commissioned = false;
        ESP_LOGI(TAG, "fabric removed");
        break;
    default:
        break;
    }
}

esp_err_t matter_transport_start(const char *device_id)
{
    if (matter_started || esp_matter::is_started()) {
        return ESP_OK;
    }

    node::config_t node_config;
    node_t *node = node::create(&node_config, matter_attribute_callback, nullptr);
    if (node == nullptr) {
        ESP_LOGW(TAG, "node create failed");
        return ESP_FAIL;
    }

    endpoint::on_off_light::config_t endpoint_config;
    endpoint_config.on_off.on_off = false;
    endpoint_t *endpoint = endpoint::on_off_light::create(node, &endpoint_config, ENDPOINT_FLAG_NONE, nullptr);
    if (endpoint == nullptr) {
        ESP_LOGW(TAG, "on/off light endpoint create failed");
        return ESP_FAIL;
    }

    matter_keypad_endpoint_id = endpoint::get_id(endpoint);
    ESP_LOGI(TAG,
             "starting Matter endpoint=%u device_id=%s setup_pin=%" PRIu32 " discriminator=%u",
             matter_keypad_endpoint_id,
             device_id != nullptr ? device_id : "",
             kMatterSetupPin,
             kMatterSetupDiscriminator);
    char manual_pairing_code[chip::kManualSetupLongCodeCharLength + 2] = {};
    if (matter_build_manual_pairing_code(manual_pairing_code, sizeof(manual_pairing_code)) == ESP_OK) {
        ESP_LOGI(TAG, "Matter manual pairing code=%s", manual_pairing_code);
    }
    ESP_LOGI(TAG,
             "heap before Matter start internal_free=%u internal_largest=%u default_free=%u default_largest=%u chip_stack=%u",
             heap_caps_get_free_size(MALLOC_CAP_INTERNAL),
             heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL),
             heap_caps_get_free_size(MALLOC_CAP_DEFAULT),
             heap_caps_get_largest_free_block(MALLOC_CAP_DEFAULT),
             CONFIG_CHIP_TASK_STACK_SIZE);

    esp_err_t err = esp_matter::start(matter_event_callback);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Matter start failed: %s", esp_err_to_name(err));
        return err;
    }

    matter_started = true;
    matter_commissioned = chip::Server::GetInstance().GetFabricTable().FabricCount() > 0;
    ESP_LOGI(TAG, "Matter started");
    return ESP_OK;
}

esp_err_t matter_transport_set_armed_state(const char *armed_state, bool ready)
{
    if (!matter_started || matter_keypad_endpoint_id == 0) {
        return ESP_ERR_INVALID_STATE;
    }

    const bool on = armed_state_is_matter_on(armed_state, ready);
    matter_last_on = on;
    matter_has_last_on = true;

    esp_err_t err = matter_schedule_on_off_apply(on);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Matter on/off schedule failed: %s", esp_err_to_name(err));
        return err;
    }

    ESP_LOGI(TAG,
             "Matter on/off mirror scheduled armed_state=%s ready=%s value=%s",
             armed_state != nullptr ? armed_state : "",
             ready ? "true" : "false",
             on ? "on" : "off");
    return ESP_OK;
}

esp_err_t matter_transport_get_manual_pairing_code(char *buffer, size_t buffer_size)
{
    return matter_build_manual_pairing_code(buffer, buffer_size);
}

bool matter_transport_is_commissioned(void)
{
    return matter_started && matter_commissioned;
}

esp_err_t matter_transport_factory_reset(void)
{
    if (!matter_started && !esp_matter::is_started()) {
        return ESP_ERR_INVALID_STATE;
    }

    ESP_LOGI(TAG, "Matter factory reset requested");
    return esp_matter::factory_reset();
}

#else

esp_err_t matter_transport_start(const char *device_id)
{
    ESP_LOGI(TAG, "Matter transport disabled");
    return ESP_OK;
}

esp_err_t matter_transport_set_armed_state(const char *armed_state, bool ready)
{
    (void)armed_state;
    (void)ready;
    return ESP_OK;
}

esp_err_t matter_transport_get_manual_pairing_code(char *buffer, size_t buffer_size)
{
    if (buffer != nullptr && buffer_size > 0) {
        buffer[0] = '\0';
    }
    return ESP_ERR_INVALID_STATE;
}

bool matter_transport_is_commissioned(void)
{
    return false;
}

esp_err_t matter_transport_factory_reset(void)
{
    return ESP_ERR_INVALID_STATE;
}

#endif
