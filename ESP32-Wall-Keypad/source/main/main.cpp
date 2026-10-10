#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "driver/i2s_std.h"
#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "driver/ledc.h"
#include "driver/spi_master.h"
#include "esp_codec_dev.h"
#include "esp_codec_dev_defaults.h"
#include "esp_app_desc.h"
#include "esp_check.h"
#include "esp_err.h"
#include "esp_event.h"
#include "esp_io_expander_tca9554.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_axs15231b.h"
#include "axs15231b_display.h"
#include "esp_lcd_st7796.h"
#include "esp_lcd_touch_ft6336.h"
#include "esp_log.h"
#include "esp_lvgl_port.h"
#include "esp_http_server.h"
#include "esp_http_client.h"
#include "esp_https_ota.h"
#include "esp_mac.h"
#include "esp_netif.h"
#include "esp_ota_ops.h"
#include "esp_crt_bundle.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "lvgl.h"
#include "mqtt_client.h"
#include "matter_transport.h"
#include "nvs.h"
#include "nvs_flash.h"

#ifndef WIFI_SSID
#define WIFI_SSID ""
#endif

#ifndef WIFI_PASSWORD
#define WIFI_PASSWORD ""
#endif

#ifndef MQTT_BROKER_URI
#define MQTT_BROKER_URI ""
#endif

#ifndef MQTT_USERNAME
#define MQTT_USERNAME ""
#endif

#ifndef MQTT_PASSWORD
#define MQTT_PASSWORD ""
#endif

#ifndef MQTT_CLIENT_ID
#define MQTT_CLIENT_ID "keypad"
#endif

#ifndef MQTT_TOPIC_PREFIX
#define MQTT_TOPIC_PREFIX ""
#endif

#ifndef DEVICE_ID
#define DEVICE_ID "front_door"
#endif

#ifndef FORCE_SETUP_MODE
#define FORCE_SETUP_MODE 0
#endif

#define PIN_I2C_SDA GPIO_NUM_8
#define PIN_I2C_SCL GPIO_NUM_7
#define I2C_PORT_NUM 0

#define LCD_SPI_HOST SPI2_HOST
#define LCD_PIXEL_CLOCK_HZ (80 * 1000 * 1000)
#define PIN_LCD_MOSI GPIO_NUM_1
#define PIN_LCD_SCLK GPIO_NUM_5
#define PIN_LCD_DC GPIO_NUM_3
#define PIN_LCD_BL GPIO_NUM_6
#define PIN_LCD_MISO GPIO_NUM_NC
#define PIN_LCD_CS GPIO_NUM_NC
#define PIN_LCD_RST GPIO_NUM_NC

#define PIN_TP_INT GPIO_NUM_NC
#define PIN_TP_RST GPIO_NUM_NC
#define AXS15231B_I2C_ADDRESS 0x3B

#define PIN_I2S_MCLK GPIO_NUM_12
#define PIN_AXS_I2S_MCLK GPIO_NUM_44
#define PIN_I2S_BCLK GPIO_NUM_13
#define PIN_I2S_LRCK GPIO_NUM_15
#define PIN_I2S_DOUT GPIO_NUM_16
#define PIN_I2S_DIN GPIO_NUM_14

#define LCD_H_RES 320
#define LCD_V_RES 480
#define LCD_BUFFER_SIZE (LCD_H_RES * LCD_V_RES / 8)
#define LCD_DMA_TRANS_SIZE (LCD_H_RES * 10)

#define LCD_BL_LEDC_TIMER LEDC_TIMER_1
#define LCD_BL_LEDC_MODE LEDC_LOW_SPEED_MODE
#define LCD_BL_LEDC_CHANNEL LEDC_CHANNEL_0
#define LCD_BL_LEDC_DUTY_RES LEDC_TIMER_10_BIT
#define LCD_BL_LEDC_DUTY_MAX 1024
#define LCD_BL_LEDC_FREQUENCY 5000
#define BACKLIGHT_ACTIVE_BRIGHTNESS 80
#define BACKLIGHT_DIM_BRIGHTNESS 8
#define SCREEN_DIM_TIMEOUT_MS 30000
#define DRIVER_RESULT_DISPLAY_MS 4000
#define MAX_CREDENTIAL_DIGITS 16
#define MENU_AUTO_CLOSE_MS 10000
#define AUDIO_SAMPLE_RATE 48000
#define AUDIO_MCLK_MULTIPLE I2S_MCLK_MULTIPLE_256
#define AUDIO_CLICK_MS 80
#define AUDIO_CLICK_SAMPLES ((AUDIO_SAMPLE_RATE * AUDIO_CLICK_MS) / 1000)
#define AUDIO_EXIT_CHIRP_MS 70
#define AUDIO_EXIT_CHIRP_SAMPLES ((AUDIO_SAMPLE_RATE * AUDIO_EXIT_CHIRP_MS) / 1000)
#define AUDIO_ENTRY_CHIRP_MS 140
#define AUDIO_ENTRY_CHIRP_SAMPLES ((AUDIO_SAMPLE_RATE * AUDIO_ENTRY_CHIRP_MS) / 1000)
#define AUDIO_ALARM_CHIRP_MS 260
#define AUDIO_ALARM_CHIRP_SAMPLES ((AUDIO_SAMPLE_RATE * AUDIO_ALARM_CHIRP_MS) / 1000)
#define AUDIO_CLICK_QUEUE_LENGTH 8
#define EXIT_DELAY_CHIRP_INTERVAL_MS 2000
#define ENTRY_DELAY_CHIRP_INTERVAL_MS 900
#define ALARM_SIREN_INTERVAL_MS 650
#define ALARM_STROBE_INTERVAL_MS 350
#define WIFI_SCAN_RECORD_LIMIT 16
#define MQTT_TOPIC_BUFFER_SIZE 96
#define MQTT_PAYLOAD_BUFFER_SIZE 1024
#define MQTT_STATE_VALUE_SIZE 24
#define MQTT_RESULT_VALUE_SIZE 32
#define MQTT_COMMAND_VALUE_SIZE 24
#define OTA_VERSION_SIZE 24
#define OTA_URL_SIZE 192
#define OTA_STATUS_SIZE 72
#define OTA_MANIFEST_BUFFER_SIZE 768
#define MQTT_RESULT_MESSAGE_SIZE 48
#define DASHBOARD_TITLE_SIZE 32
#define DASHBOARD_VISIBLE_ITEM_COUNT 6
#define DASHBOARD_MAX_ITEM_COUNT 12
#define DASHBOARD_ITEM_LABEL_SIZE 20
#define DASHBOARD_ITEM_VALUE_SIZE 32
#define DASHBOARD_ITEM_STATE_SIZE 12
#define DASHBOARD_ITEM_ID_SIZE 24
#define DASHBOARD_ITEM_KIND_SIZE 12
#define DASHBOARD_PAGE_ROTATE_MS 8000
#define DASHBOARD_MANUAL_PAGE_HOLD_MS 60000
#define UI_UPDATE_LOCK_TIMEOUT_MS 3000
#define WIFI_IP_TEXT_SIZE 16
#define SETUP_AP_SSID_SIZE 33
#define DEVICE_ID_SIZE 32
#define WIFI_SSID_SIZE 32
#define WIFI_PASSWORD_SIZE 64
#define MQTT_BROKER_URI_SIZE 96
#define MQTT_USERNAME_SIZE 64
#define MQTT_PASSWORD_SIZE 64
#define MQTT_CLIENT_ID_SIZE 64
#define HUBITAT_ENDPOINT_URL_SIZE 192
#define HUBITAT_PAIRING_CODE_SIZE 8
#define TRANSPORT_MODE_SIZE 8
#define CONFIG_NAMESPACE "keypad_cfg"
#define CONFIG_SETUP_REQUESTED_KEY "setup_req"
#define OTA_MANIFEST_URL "https://csteele-pd.github.io/Hubitat-public/ESP32-Wall-Keypad/install/ota-manifest.json"

static const char *TAG = "keypad";

typedef enum {
    KEYPAD_BOARD_ST7796,
    KEYPAD_BOARD_AXS15231B,
} keypad_board_type_t;

static i2c_master_bus_handle_t i2c_bus_handle;
static keypad_board_type_t board_type = KEYPAD_BOARD_ST7796;
static esp_io_expander_handle_t expander_handle;
static esp_lcd_panel_io_handle_t lcd_io_handle;
static esp_lcd_panel_handle_t lcd_panel_handle;
static esp_lcd_touch_handle_t touch_handle;
static lv_display_t *display;
static i2s_chan_handle_t audio_tx_handle;
static esp_codec_dev_handle_t audio_output_dev;
static QueueHandle_t audio_click_queue;
static esp_mqtt_client_handle_t mqtt_client;
static httpd_handle_t setup_http_server;
static httpd_handle_t normal_http_server;
static char mqtt_availability_topic[MQTT_TOPIC_BUFFER_SIZE];
static char setup_ap_ssid[SETUP_AP_SSID_SIZE];
static wifi_ap_record_t wifi_scan_records[WIFI_SCAN_RECORD_LIMIT];
static int16_t audio_click_samples[AUDIO_CLICK_SAMPLES];
static int16_t audio_exit_chirp_samples[AUDIO_EXIT_CHIRP_SAMPLES];
static int16_t audio_entry_chirp_samples[AUDIO_ENTRY_CHIRP_SAMPLES];
static int16_t audio_alarm_chirp_samples[AUDIO_ALARM_CHIRP_SAMPLES];
static uint32_t action_request_sequence;
static char last_action_request_id[DEVICE_ID_SIZE + 12];
static bool audio_ready;
static bool network_core_initialized;
static bool wifi_driver_initialized;
static bool wifi_started;
static bool wifi_scan_running;
static bool wifi_scan_completed;
static bool wifi_connected;
static bool mqtt_started;
static bool mqtt_connected;
static bool matter_transport_start_requested;
static char wifi_ip_text[WIFI_IP_TEXT_SIZE] = "none";

typedef enum {
    KEY_ACTION_DIGIT,
    KEY_ACTION_BACKSPACE,
    KEY_ACTION_ENTER,
    KEY_ACTION_ARM_AWAY,
    KEY_ACTION_ARM_STAY,
} key_action_t;

typedef struct {
    const char *label;
    key_action_t action;
    char digit;
} keypad_key_t;

typedef enum {
    AUDIO_SIGNAL_KEY_CLICK = 1,
    AUDIO_SIGNAL_EXIT_CHIRP,
    AUDIO_SIGNAL_ENTRY_CHIRP,
    AUDIO_SIGNAL_ALARM_CHIRP,
} audio_signal_t;

typedef enum {
    LOCAL_ALARM_OFF,
    LOCAL_ALARM_SIREN,
    LOCAL_ALARM_STROBE,
    LOCAL_ALARM_BOTH,
} local_alarm_mode_t;

typedef enum {
    DELAY_CHIRP_NONE,
    DELAY_CHIRP_EXIT,
    DELAY_CHIRP_ENTRY,
} delay_chirp_mode_t;

typedef enum {
    APP_SCREEN_HOME,
    APP_SCREEN_KEYPAD,
    APP_SCREEN_HSM,
} app_screen_t;

typedef struct {
    lv_obj_t *code_label;
    char digits[MAX_CREDENTIAL_DIGITS + 1];
    size_t digit_count;
} credential_entry_screen_t;

typedef struct {
    bool ready;
    char armed_state[MQTT_STATE_VALUE_SIZE];
    int delay_remaining;
    bool has_delay_remaining;
    bool driver_result_active;
    char driver_result[MQTT_RESULT_VALUE_SIZE];
    char driver_result_message[MQTT_RESULT_MESSAGE_SIZE];
} system_status_t;

typedef struct {
    char id[DASHBOARD_ITEM_ID_SIZE];
    char label[DASHBOARD_ITEM_LABEL_SIZE];
    char value[DASHBOARD_ITEM_VALUE_SIZE];
    char state[DASHBOARD_ITEM_STATE_SIZE];
    char kind[DASHBOARD_ITEM_KIND_SIZE];
    int level;
    bool has_level;
    bool primary;
    bool detail;
} dashboard_item_t;

typedef struct {
    char id[DASHBOARD_TITLE_SIZE];
    char title[DASHBOARD_TITLE_SIZE];
    dashboard_item_t items[DASHBOARD_MAX_ITEM_COUNT];
    size_t item_count;
    bool loaded;
} dashboard_screen_t;

typedef struct {
    char device_id[DEVICE_ID_SIZE];
    char wifi_ssid[WIFI_SSID_SIZE];
    char wifi_password[WIFI_PASSWORD_SIZE];
    char mqtt_broker_uri[MQTT_BROKER_URI_SIZE];
    char mqtt_username[MQTT_USERNAME_SIZE];
    char mqtt_password[MQTT_PASSWORD_SIZE];
    char mqtt_client_id[MQTT_CLIENT_ID_SIZE];
    char mqtt_topic_prefix[MQTT_TOPIC_BUFFER_SIZE];
    char hubitat_lan_endpoint[HUBITAT_ENDPOINT_URL_SIZE];
    char hubitat_pairing_code[HUBITAT_PAIRING_CODE_SIZE];
    char transport_mode[TRANSPORT_MODE_SIZE];
    bool loaded_from_nvs;
} device_config_t;

static credential_entry_screen_t credential_screen;
static dashboard_screen_t hsm_dashboard = {
    .id = "",
    .title = "Dashboard",
    .items = {},
    .item_count = 0,
    .loaded = false,
};
static dashboard_screen_t controls_dashboard = {
    .id = "controls",
    .title = "Controls",
    .items = {},
    .item_count = 0,
    .loaded = false,
};
static system_status_t system_status = {
    .ready = false,
    .armed_state = "unknown",
    .delay_remaining = 0,
    .has_delay_remaining = false,
    .driver_result_active = false,
    .driver_result = "",
    .driver_result_message = "",
};
static device_config_t active_config = {
    .device_id = DEVICE_ID,
    .wifi_ssid = WIFI_SSID,
    .wifi_password = WIFI_PASSWORD,
    .mqtt_broker_uri = MQTT_BROKER_URI,
    .mqtt_username = MQTT_USERNAME,
    .mqtt_password = MQTT_PASSWORD,
    .mqtt_client_id = MQTT_CLIENT_ID,
    .mqtt_topic_prefix = MQTT_TOPIC_PREFIX,
    .hubitat_lan_endpoint = "",
    .hubitat_pairing_code = "",
    .transport_mode = "auto",
    .loaded_from_nvs = false,
};

static lv_style_t style_screen;
static lv_style_t style_code;
static lv_style_t style_key;
static lv_style_t style_key_pressed;
static lv_style_t style_key_action;
static lv_style_t style_key_enter;
static lv_style_t style_key_arm;
static lv_style_t style_key_partial;
static lv_style_t style_menu_button;
static lv_style_t style_menu_bar;
static lv_style_t style_menu_panel;
static lv_style_t style_menu_item;
static lv_style_t style_menu_item_pressed;
static lv_style_t style_dimmer_track;
static lv_style_t style_dimmer_fill;
static lv_style_t style_dimmer_knob;

static lv_obj_t *home_view;
static lv_obj_t *keypad_view;
static lv_obj_t *hsm_view;
static lv_obj_t *menu_panel;
static lv_obj_t *menu_home_view;
static lv_obj_t *menu_dashboard_view;
static lv_obj_t *menu_network_view;
static lv_obj_t *menu_factory_reset_view;
static lv_obj_t *menu_firmware_view;
static lv_timer_t *menu_timer;
static lv_timer_t *driver_result_timer;
static lv_timer_t *delay_chirp_timer;
static lv_timer_t *alarm_siren_timer;
static lv_timer_t *alarm_strobe_timer;
static lv_timer_t *dashboard_page_timer;
static lv_obj_t *alarm_state_label;
static lv_obj_t *alarm_detail_label;
static lv_obj_t *dashboard_wifi_label;
static lv_obj_t *dashboard_mqtt_label;
static lv_obj_t *dashboard_ip_label;
static lv_obj_t *dashboard_alarm_label;
static lv_obj_t *dashboard_topic_label;
static lv_obj_t *dashboard_broker_label;
static lv_obj_t *dashboard_fw_label;
static lv_obj_t *network_wifi_label;
static lv_obj_t *network_mqtt_label;
static lv_obj_t *network_ip_label;
static lv_obj_t *network_device_label;
static lv_obj_t *network_topic_label;
static lv_obj_t *network_broker_label;
static lv_obj_t *firmware_current_label;
static lv_obj_t *firmware_status_label;
static lv_obj_t *firmware_install_button;
static lv_obj_t *home_alarm_label;
static lv_obj_t *home_ready_label;
static lv_obj_t *home_network_label;
static lv_obj_t *home_delay_label;
static lv_obj_t *home_dashboard_button;
static lv_obj_t *hsm_state_label;
static lv_obj_t *hsm_title_label;
static lv_obj_t *hsm_page_label;
static lv_obj_t *hsm_ready_label;
static lv_obj_t *hsm_delay_label;
static lv_obj_t *hsm_last_result_label;
static lv_obj_t *hsm_item_tiles[DASHBOARD_VISIBLE_ITEM_COUNT];
static lv_obj_t *hsm_item_labels[DASHBOARD_VISIBLE_ITEM_COUNT];
static lv_obj_t *hsm_item_value_labels[DASHBOARD_VISIBLE_ITEM_COUNT];
static lv_obj_t *control_detail_panel;
static lv_obj_t *control_detail_title_label;
static lv_obj_t *control_detail_value_label;
static lv_obj_t *control_detail_slider;
static lv_timer_t *screen_dim_timer;
static lv_timer_t *idle_return_timer;
static bool screen_dimmed;
static bool setup_requested_from_nvs;
static bool alarm_strobe_on;
static app_screen_t active_screen = APP_SCREEN_HOME;
static size_t dashboard_page_index;
static bool dashboard_page_auto_paused;
static bool controls_dashboard_active;
static int active_control_detail_index = -1;
static delay_chirp_mode_t active_delay_chirp_mode = DELAY_CHIRP_NONE;
static local_alarm_mode_t local_alarm_mode = LOCAL_ALARM_OFF;
static bool ota_busy;
static bool ota_update_available;
static char ota_available_version[OTA_VERSION_SIZE];
static char ota_image_url[OTA_URL_SIZE];
static char ota_status[OTA_STATUS_SIZE] = "Check for an update.";

static const keypad_key_t KEYPAD_KEYS[] = {
    {"1", KEY_ACTION_DIGIT, '1'},
    {"2", KEY_ACTION_DIGIT, '2'},
    {"3", KEY_ACTION_DIGIT, '3'},
    {"4", KEY_ACTION_DIGIT, '4'},
    {"5", KEY_ACTION_DIGIT, '5'},
    {"6", KEY_ACTION_DIGIT, '6'},
    {"7", KEY_ACTION_DIGIT, '7'},
    {"8", KEY_ACTION_DIGIT, '8'},
    {"9", KEY_ACTION_DIGIT, '9'},
    {"<", KEY_ACTION_BACKSPACE, 0},
    {"0", KEY_ACTION_DIGIT, '0'},
    {"OK", KEY_ACTION_ENTER, 0},
};

static const keypad_key_t ARM_KEY = {"ARM", KEY_ACTION_ARM_AWAY, 0};
static const keypad_key_t PARTIAL_KEY = {"PARTIAL", KEY_ACTION_ARM_STAY, 0};

static void play_key_click(void);
static void play_audio_signal(audio_signal_t signal);
static void wake_screen(void);
static void update_local_alarm_outputs_locked(void);
static void publish_driver_action_request(const char *action, const char *credential, size_t credential_length);
static void post_hubitat_lan_action_request(const char *payload);
static void start_hubitat_lan_pairing(void);
static void publish_mqtt_status(void);
static void start_network_transports_when_ready(void);
static void update_dashboard_ui(void);
static void show_app_screen_locked(app_screen_t screen, const char *reason);
static void apply_firmware_status_to_ui_locked(void);
static void apply_hsm_status_to_ui_locked(void);
static void hide_control_detail_panel(void);

static void confirm_ota_boot_if_needed(void)
{
    const esp_partition_t *running_partition = esp_ota_get_running_partition();
    esp_ota_img_states_t state;
    esp_err_t err = esp_ota_get_state_partition(running_partition, &state);
    if (err != ESP_OK || state != ESP_OTA_IMG_PENDING_VERIFY) {
        return;
    }

    ESP_LOGI(TAG, "OTA image self-test passed; confirming boot partition");
    err = esp_ota_mark_app_valid_cancel_rollback();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "failed to confirm OTA image: %s", esp_err_to_name(err));
    }
}

static void copy_mqtt_string(char *dest, size_t dest_size, const char *source, size_t source_len)
{
    if (dest_size == 0) {
        return;
    }

    size_t copy_len = source_len < dest_size - 1 ? source_len : dest_size - 1;
    memcpy(dest, source, copy_len);
    dest[copy_len] = '\0';
}

static int json_hex_value(char ch)
{
    if (ch >= '0' && ch <= '9') {
        return ch - '0';
    }
    if (ch >= 'a' && ch <= 'f') {
        return ch - 'a' + 10;
    }
    if (ch >= 'A' && ch <= 'F') {
        return ch - 'A' + 10;
    }
    return -1;
}

static bool append_utf8(char *dest, size_t dest_size, size_t *dest_len, uint32_t codepoint)
{
    if (dest_size == 0 || *dest_len >= dest_size - 1) {
        return false;
    }

    if (codepoint <= 0x7f) {
        dest[(*dest_len)++] = (char)codepoint;
    } else if (codepoint <= 0x7ff) {
        if (*dest_len + 2 >= dest_size) {
            return false;
        }
        dest[(*dest_len)++] = (char)(0xc0 | (codepoint >> 6));
        dest[(*dest_len)++] = (char)(0x80 | (codepoint & 0x3f));
    } else {
        if (*dest_len + 3 >= dest_size) {
            return false;
        }
        dest[(*dest_len)++] = (char)(0xe0 | (codepoint >> 12));
        dest[(*dest_len)++] = (char)(0x80 | ((codepoint >> 6) & 0x3f));
        dest[(*dest_len)++] = (char)(0x80 | (codepoint & 0x3f));
    }
    dest[*dest_len] = '\0';
    return true;
}

static bool copy_json_string_value(char *dest, size_t dest_size, const char *start, const char **end)
{
    if (dest_size == 0) {
        return false;
    }

    dest[0] = '\0';
    size_t dest_len = 0;
    const char *cursor = start;
    while (*cursor != '\0') {
        char ch = *cursor++;
        if (ch == '"') {
            if (end != NULL) {
                *end = cursor - 1;
            }
            dest[dest_len] = '\0';
            return true;
        }

        if (ch == '\\') {
            char escaped = *cursor++;
            switch (escaped) {
            case '"':
            case '\\':
            case '/':
                ch = escaped;
                break;
            case 'b':
                ch = '\b';
                break;
            case 'f':
                ch = '\f';
                break;
            case 'n':
                ch = '\n';
                break;
            case 'r':
                ch = '\r';
                break;
            case 't':
                ch = '\t';
                break;
            case 'u': {
                uint32_t codepoint = 0;
                for (int i = 0; i < 4; ++i) {
                    int nibble = json_hex_value(cursor[i]);
                    if (nibble < 0) {
                        return false;
                    }
                    codepoint = (codepoint << 4) | (uint32_t)nibble;
                }
                cursor += 4;
                append_utf8(dest, dest_size, &dest_len, codepoint);
                continue;
            }
            default:
                return false;
            }
        }

        if (dest_len + 1 >= dest_size) {
            dest[dest_len] = '\0';
            return true;
        }
        dest[dest_len++] = ch;
        dest[dest_len] = '\0';
    }

    return false;
}

static esp_err_t nvs_get_string_or_empty(nvs_handle_t nvs, const char *key, char *value, size_t value_size)
{
    if (value_size == 0) {
        return ESP_ERR_INVALID_ARG;
    }

    value[0] = '\0';
    size_t required_size = value_size;
    esp_err_t err = nvs_get_str(nvs, key, value, &required_size);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        return ESP_OK;
    }
    return err;
}

static bool normalize_mqtt_broker_uri(char *broker_uri, size_t broker_uri_size)
{
    if (broker_uri_size == 0 || broker_uri[0] == '\0' || strstr(broker_uri, "://") != NULL) {
        return true;
    }

    char normalized[MQTT_BROKER_URI_SIZE] = {};
    int written = snprintf(normalized, sizeof(normalized), "mqtt://%s", broker_uri);
    if (written < 0 || written >= (int)broker_uri_size) {
        return false;
    }

    strlcpy(broker_uri, normalized, broker_uri_size);
    return true;
}

static bool is_known_transport_mode(const char *mode)
{
    return strcmp(mode, "auto") == 0 ||
           strcmp(mode, "mqtt") == 0 ||
           strcmp(mode, "lan") == 0 ||
           strcmp(mode, "both") == 0;
}

static bool transport_sends_mqtt_actions(void)
{
    return strcmp(active_config.transport_mode, "mqtt") == 0 ||
           strcmp(active_config.transport_mode, "both") == 0 ||
           (strcmp(active_config.transport_mode, "auto") == 0 && mqtt_connected);
}

static bool transport_sends_lan_actions(void)
{
    return strcmp(active_config.transport_mode, "lan") == 0 ||
           strcmp(active_config.transport_mode, "both") == 0 ||
           (strcmp(active_config.transport_mode, "auto") == 0 && !mqtt_connected);
}

static bool load_setup_requested_from_nvs(void)
{
    nvs_handle_t nvs;
    esp_err_t err = nvs_open(CONFIG_NAMESPACE, NVS_READONLY, &nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        return false;
    }
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "setup request nvs open failed: %s", esp_err_to_name(err));
        return false;
    }

    uint8_t requested = 0;
    err = nvs_get_u8(nvs, CONFIG_SETUP_REQUESTED_KEY, &requested);
    nvs_close(nvs);
    return err == ESP_OK && requested == 1;
}

static esp_err_t save_setup_requested_to_nvs(bool requested)
{
    nvs_handle_t nvs;
    ESP_RETURN_ON_ERROR(nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &nvs), TAG, "open config nvs");
    esp_err_t err = nvs_set_u8(nvs, CONFIG_SETUP_REQUESTED_KEY, requested ? 1 : 0);
    if (err == ESP_OK) {
        err = nvs_commit(nvs);
    }
    nvs_close(nvs);
    return err;
}

static esp_err_t erase_config_from_nvs(void)
{
    nvs_handle_t nvs;
    ESP_RETURN_ON_ERROR(nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &nvs), TAG, "open config nvs");
    esp_err_t err = nvs_erase_all(nvs);
    if (err == ESP_OK) {
        err = nvs_commit(nvs);
    }
    nvs_close(nvs);
    return err;
}

static bool load_config_from_nvs(void)
{
    nvs_handle_t nvs;
    esp_err_t err = nvs_open(CONFIG_NAMESPACE, NVS_READONLY, &nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        ESP_LOGI(TAG, "config nvs not found; using factory defaults");
        return false;
    }
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "config nvs open failed: %s", esp_err_to_name(err));
        return false;
    }

    uint8_t valid = 0;
    err = nvs_get_u8(nvs, "valid", &valid);
    if (err != ESP_OK || valid != 1) {
        nvs_close(nvs);
        ESP_LOGI(TAG, "config nvs invalid; using factory defaults");
        return false;
    }

    device_config_t loaded = active_config;
    err = nvs_get_string_or_empty(nvs, "device_id", loaded.device_id, sizeof(loaded.device_id));
    err |= nvs_get_string_or_empty(nvs, "wifi_ssid", loaded.wifi_ssid, sizeof(loaded.wifi_ssid));
    err |= nvs_get_string_or_empty(nvs, "wifi_pw", loaded.wifi_password, sizeof(loaded.wifi_password));
    err |= nvs_get_string_or_empty(nvs, "mqtt_uri", loaded.mqtt_broker_uri, sizeof(loaded.mqtt_broker_uri));
    err |= nvs_get_string_or_empty(nvs, "mqtt_user", loaded.mqtt_username, sizeof(loaded.mqtt_username));
    err |= nvs_get_string_or_empty(nvs, "mqtt_pw", loaded.mqtt_password, sizeof(loaded.mqtt_password));
    err |= nvs_get_string_or_empty(nvs, "mqtt_id", loaded.mqtt_client_id, sizeof(loaded.mqtt_client_id));
    err |= nvs_get_string_or_empty(nvs, "topic", loaded.mqtt_topic_prefix, sizeof(loaded.mqtt_topic_prefix));
    err |= nvs_get_string_or_empty(nvs, "hubitat_url", loaded.hubitat_lan_endpoint, sizeof(loaded.hubitat_lan_endpoint));
    err |= nvs_get_string_or_empty(nvs, "hubitat_pair", loaded.hubitat_pairing_code, sizeof(loaded.hubitat_pairing_code));
    err |= nvs_get_string_or_empty(nvs, "transport", loaded.transport_mode, sizeof(loaded.transport_mode));
    nvs_close(nvs);

    if (loaded.transport_mode[0] == '\0') {
        strlcpy(loaded.transport_mode, "auto", sizeof(loaded.transport_mode));
    }
    const bool mqtt_configured = loaded.mqtt_broker_uri[0] != '\0' && loaded.mqtt_topic_prefix[0] != '\0';
    if (err != ESP_OK ||
        loaded.device_id[0] == '\0' ||
        loaded.wifi_ssid[0] == '\0' ||
        !is_known_transport_mode(loaded.transport_mode) ||
        ((strcmp(loaded.transport_mode, "mqtt") == 0 || strcmp(loaded.transport_mode, "both") == 0) && !mqtt_configured) ||
        (mqtt_configured && loaded.mqtt_client_id[0] == '\0')) {
        ESP_LOGW(TAG, "config nvs incomplete; using factory defaults");
        return false;
    }

    if (mqtt_configured && !normalize_mqtt_broker_uri(loaded.mqtt_broker_uri, sizeof(loaded.mqtt_broker_uri))) {
        ESP_LOGW(TAG, "config nvs mqtt broker is too long; using factory defaults");
        return false;
    }

    loaded.loaded_from_nvs = true;
    active_config = loaded;
    ESP_LOGI(TAG,
             "config loaded from nvs device_id=%s transport=%s topic=%s hubitat_lan=%s",
             active_config.device_id,
             active_config.transport_mode,
             active_config.mqtt_topic_prefix,
             active_config.hubitat_lan_endpoint[0] != '\0' ? "configured" : "off");
    return true;
}

static esp_err_t save_config_to_nvs(const device_config_t *config)
{
    nvs_handle_t nvs;
    ESP_RETURN_ON_ERROR(nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &nvs), TAG, "open config nvs");

    esp_err_t err = ESP_OK;
    err |= nvs_set_str(nvs, "device_id", config->device_id);
    err |= nvs_set_str(nvs, "wifi_ssid", config->wifi_ssid);
    err |= nvs_set_str(nvs, "wifi_pw", config->wifi_password);
    err |= nvs_set_str(nvs, "mqtt_uri", config->mqtt_broker_uri);
    err |= nvs_set_str(nvs, "mqtt_user", config->mqtt_username);
    err |= nvs_set_str(nvs, "mqtt_pw", config->mqtt_password);
    err |= nvs_set_str(nvs, "mqtt_id", config->mqtt_client_id);
    err |= nvs_set_str(nvs, "topic", config->mqtt_topic_prefix);
    err |= nvs_set_str(nvs, "hubitat_url", config->hubitat_lan_endpoint);
    err |= nvs_set_str(nvs, "hubitat_pair", config->hubitat_pairing_code);
    err |= nvs_set_str(nvs, "transport", config->transport_mode);
    err |= nvs_set_u8(nvs, CONFIG_SETUP_REQUESTED_KEY, 0);
    err |= nvs_set_u8(nvs, "valid", 1);
    if (err == ESP_OK) {
        err = nvs_commit(nvs);
    }
    nvs_close(nvs);
    return err;
}

static bool config_needs_setup(void)
{
    return !active_config.loaded_from_nvs &&
           (active_config.wifi_ssid[0] == '\0' ||
            strcmp(active_config.wifi_ssid, "replace-me") == 0);
}

static int hex_digit_value(char c)
{
    if (c >= '0' && c <= '9') {
        return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
        return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
        return c - 'A' + 10;
    }
    return -1;
}

static void url_decode(char *dest, size_t dest_size, const char *source, size_t source_len)
{
    if (dest_size == 0) {
        return;
    }

    size_t out = 0;
    for (size_t i = 0; i < source_len && out < dest_size - 1; ++i) {
        if (source[i] == '+') {
            dest[out++] = ' ';
        } else if (source[i] == '%' && i + 2 < source_len) {
            int hi = hex_digit_value(source[i + 1]);
            int lo = hex_digit_value(source[i + 2]);
            if (hi >= 0 && lo >= 0) {
                dest[out++] = (char)((hi << 4) | lo);
                i += 2;
            } else {
                dest[out++] = source[i];
            }
        } else {
            dest[out++] = source[i];
        }
    }
    dest[out] = '\0';
}

static bool get_form_field(const char *body, const char *field, char *value, size_t value_size)
{
    value[0] = '\0';
    size_t field_len = strlen(field);
    const char *cursor = body;

    while (cursor != NULL && *cursor != '\0') {
        const char *next = strchr(cursor, '&');
        size_t pair_len = next == NULL ? strlen(cursor) : (size_t)(next - cursor);
        if (pair_len > field_len && strncmp(cursor, field, field_len) == 0 && cursor[field_len] == '=') {
            url_decode(value, value_size, cursor + field_len + 1, pair_len - field_len - 1);
            return true;
        }
        cursor = next == NULL ? NULL : next + 1;
    }

    return false;
}

static bool parse_json_bool_field(const char *json, const char *field, bool *value)
{
    char pattern[MQTT_STATE_VALUE_SIZE + 4] = {};
    snprintf(pattern, sizeof(pattern), "\"%s\"", field);

    const char *field_pos = strstr(json, pattern);
    if (field_pos == NULL) {
        return false;
    }

    const char *colon = strchr(field_pos + strlen(pattern), ':');
    if (colon == NULL) {
        return false;
    }

    const char *cursor = colon + 1;
    while (*cursor == ' ' || *cursor == '\t' || *cursor == '\r' || *cursor == '\n') {
        ++cursor;
    }

    if (strncmp(cursor, "true", 4) == 0) {
        *value = true;
        return true;
    }

    if (strncmp(cursor, "false", 5) == 0) {
        *value = false;
        return true;
    }

    return false;
}

static bool parse_json_int_field(const char *json, const char *field, int *value)
{
    char pattern[MQTT_STATE_VALUE_SIZE + 4] = {};
    snprintf(pattern, sizeof(pattern), "\"%s\"", field);

    const char *field_pos = strstr(json, pattern);
    if (field_pos == NULL) {
        return false;
    }

    const char *colon = strchr(field_pos + strlen(pattern), ':');
    if (colon == NULL) {
        return false;
    }

    char *end = NULL;
    long parsed = strtol(colon + 1, &end, 10);
    if (end == colon + 1) {
        return false;
    }

    if (parsed < 0) {
        parsed = 0;
    }
    if (parsed > 999) {
        parsed = 999;
    }
    *value = (int)parsed;
    return true;
}

static bool parse_json_string_field(const char *json, const char *field, char *value, size_t value_size)
{
    char pattern[MQTT_STATE_VALUE_SIZE + 4] = {};
    snprintf(pattern, sizeof(pattern), "\"%s\"", field);

    const char *field_pos = strstr(json, pattern);
    if (field_pos == NULL) {
        return false;
    }

    const char *colon = strchr(field_pos + strlen(pattern), ':');
    if (colon == NULL) {
        return false;
    }

    const char *cursor = colon + 1;
    while (*cursor == ' ' || *cursor == '\t' || *cursor == '\r' || *cursor == '\n') {
        ++cursor;
    }

    if (*cursor != '"') {
        return false;
    }

    ++cursor;
    if (!copy_json_string_value(value, value_size, cursor, NULL)) {
        return false;
    }
    return true;
}

static int compare_firmware_versions(const char *left, const char *right)
{
    const char *left_cursor = left;
    const char *right_cursor = right;

    for (int component = 0; component < 3; ++component) {
        char *left_end = NULL;
        char *right_end = NULL;
        long left_value = strtol(left_cursor, &left_end, 10);
        long right_value = strtol(right_cursor, &right_end, 10);
        if (left_end == left_cursor || right_end == right_cursor) {
            return 0;
        }
        if (left_value != right_value) {
            return left_value < right_value ? -1 : 1;
        }
        left_cursor = *left_end == '.' ? left_end + 1 : left_end;
        right_cursor = *right_end == '.' ? right_end + 1 : right_end;
    }

    return 0;
}

static esp_err_t download_https_text(const char *url, char *buffer, size_t buffer_size)
{
    if (buffer_size == 0) {
        return ESP_ERR_INVALID_ARG;
    }

    buffer[0] = '\0';
    esp_http_client_config_t config = {};
    config.url = url;
    config.crt_bundle_attach = esp_crt_bundle_attach;
    config.timeout_ms = 12000;
    config.keep_alive_enable = true;
    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (client == NULL) {
        return ESP_ERR_NO_MEM;
    }

    esp_err_t err = esp_http_client_open(client, 0);
    if (err != ESP_OK) {
        esp_http_client_cleanup(client);
        return err;
    }

    int64_t content_length = esp_http_client_fetch_headers(client);
    if (content_length >= (int64_t)buffer_size || esp_http_client_get_status_code(client) != 200) {
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return ESP_FAIL;
    }

    size_t length = 0;
    while (length < buffer_size - 1) {
        int read = esp_http_client_read(client, buffer + length, buffer_size - 1 - length);
        if (read < 0) {
            err = ESP_FAIL;
            break;
        }
        if (read == 0) {
            break;
        }
        length += (size_t)read;
    }
    buffer[length] = '\0';
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    return err;
}

static const char *armed_state_label_text(const char *armed_state)
{
    if (strcmp(armed_state, "disarmed") == 0) {
        return "DISARMED";
    }
    if (strcmp(armed_state, "armed_away") == 0 || strcmp(armed_state, "arm_away") == 0) {
        return "ARMED";
    }
    if (strcmp(armed_state, "armed_stay") == 0 || strcmp(armed_state, "arm_stay") == 0 ||
        strcmp(armed_state, "partial") == 0) {
        return "PARTIAL";
    }
    if (strcmp(armed_state, "alarming") == 0 || strcmp(armed_state, "alarm") == 0) {
        return "ALARM";
    }
    if (strcmp(armed_state, "arming") == 0 || strcmp(armed_state, "exit_delay") == 0) {
        return "ARMING";
    }
    if (strcmp(armed_state, "entry_delay") == 0) {
        return "ENTER CODE";
    }
    return "UNKNOWN";
}

static lv_color_t armed_state_color(const char *armed_state)
{
    if (strcmp(armed_state, "disarmed") == 0) {
        return lv_color_hex(0x38d26f);
    }
    if (strcmp(armed_state, "armed_away") == 0 || strcmp(armed_state, "arm_away") == 0) {
        return lv_color_hex(0xe95757);
    }
    if (strcmp(armed_state, "armed_stay") == 0 || strcmp(armed_state, "arm_stay") == 0 ||
        strcmp(armed_state, "partial") == 0) {
        return lv_color_hex(0xf0b84a);
    }
    if (strcmp(armed_state, "alarming") == 0 || strcmp(armed_state, "alarm") == 0) {
        return lv_color_hex(0xff2f2f);
    }
    if (strcmp(armed_state, "arming") == 0 ||
        strcmp(armed_state, "exit_delay") == 0 ||
        strcmp(armed_state, "entry_delay") == 0) {
        return lv_color_hex(0xff9f2a);
    }
    return lv_color_hex(0xb8c6d8);
}

static const char *driver_result_label_text(const char *result)
{
    if (strcmp(result, "accepted") == 0 || strcmp(result, "ok") == 0) {
        return "ACCEPTED";
    }
    if (strcmp(result, "rejected") == 0) {
        return "REJECTED";
    }
    if (strcmp(result, "invalid_code") == 0 || strcmp(result, "invalid") == 0) {
        return "INVALID CODE";
    }
    if (strcmp(result, "not_ready") == 0) {
        return "NOT READY";
    }
    if (strcmp(result, "error") == 0) {
        return "ERROR";
    }
    return "RESPONSE";
}

static lv_color_t driver_result_color(const char *result)
{
    if (strcmp(result, "accepted") == 0 || strcmp(result, "ok") == 0) {
        return lv_color_hex(0x38d26f);
    }
    if (strcmp(result, "rejected") == 0 ||
        strcmp(result, "invalid_code") == 0 ||
        strcmp(result, "invalid") == 0 ||
        strcmp(result, "error") == 0) {
        return lv_color_hex(0xe95757);
    }
    if (strcmp(result, "not_ready") == 0) {
        return lv_color_hex(0xf0b84a);
    }
    return lv_color_hex(0xb8c6d8);
}

static lv_color_t dashboard_state_color(const char *state)
{
    if (strcmp(state, "ok") == 0) {
        return lv_color_hex(0x38d26f);
    }
    if (strcmp(state, "info") == 0) {
        return lv_color_hex(0x5aa9ff);
    }
    if (strcmp(state, "warn") == 0) {
        return lv_color_hex(0xf0b84a);
    }
    if (strcmp(state, "alert") == 0) {
        return lv_color_hex(0xe95757);
    }
    if (strcmp(state, "offline") == 0) {
        return lv_color_hex(0x9aa6b2);
    }
    return lv_color_hex(0xb8c6d8);
}

static lv_color_t dashboard_state_bg_color(const char *state)
{
    if (strcmp(state, "ok") == 0) {
        return lv_color_hex(0x173226);
    }
    if (strcmp(state, "info") == 0) {
        return lv_color_hex(0x193049);
    }
    if (strcmp(state, "warn") == 0) {
        return lv_color_hex(0x3a2f18);
    }
    if (strcmp(state, "alert") == 0) {
        return lv_color_hex(0x3a2024);
    }
    if (strcmp(state, "offline") == 0) {
        return lv_color_hex(0x252b34);
    }
    return lv_color_hex(0x202733);
}

static const char *alarm_detail_text(void)
{
    static char delay_text[24];

    if (!system_status.ready) {
        return "OFFLINE";
    }
    if (strcmp(system_status.armed_state, "arming") == 0 ||
        strcmp(system_status.armed_state, "exit_delay") == 0) {
        if (system_status.has_delay_remaining) {
            snprintf(delay_text, sizeof(delay_text), "EXIT DELAY %d", system_status.delay_remaining);
            return delay_text;
        }
        return "EXIT DELAY";
    }
    if (strcmp(system_status.armed_state, "entry_delay") == 0) {
        if (system_status.has_delay_remaining) {
            snprintf(delay_text, sizeof(delay_text), "ENTRY DELAY %d", system_status.delay_remaining);
            return delay_text;
        }
        return "ENTRY DELAY";
    }
    if (strcmp(system_status.armed_state, "alarming") == 0 || strcmp(system_status.armed_state, "alarm") == 0) {
        return "ALARMING";
    }
    return "READY";
}

static lv_color_t alarm_detail_color(void)
{
    if (!system_status.ready) {
        return lv_color_hex(0xe95757);
    }
    if (strcmp(system_status.armed_state, "arming") == 0 ||
        strcmp(system_status.armed_state, "exit_delay") == 0 ||
        strcmp(system_status.armed_state, "entry_delay") == 0) {
        return lv_color_hex(0xffc46b);
    }
    if (strcmp(system_status.armed_state, "alarming") == 0 || strcmp(system_status.armed_state, "alarm") == 0) {
        return lv_color_hex(0xff8a8a);
    }
    return lv_color_hex(0xb8c6d8);
}

static bool is_known_armed_state(const char *armed_state)
{
    return strcmp(armed_state, "disarmed") == 0 ||
           strcmp(armed_state, "armed_away") == 0 ||
           strcmp(armed_state, "arm_away") == 0 ||
           strcmp(armed_state, "armed_stay") == 0 ||
           strcmp(armed_state, "arm_stay") == 0 ||
           strcmp(armed_state, "partial") == 0 ||
           strcmp(armed_state, "alarming") == 0 ||
           strcmp(armed_state, "alarm") == 0 ||
           strcmp(armed_state, "arming") == 0 ||
           strcmp(armed_state, "exit_delay") == 0 ||
           strcmp(armed_state, "entry_delay") == 0 ||
           strcmp(armed_state, "unknown") == 0;
}

static bool is_exit_delay_state(void)
{
    return strcmp(system_status.armed_state, "arming") == 0 ||
           strcmp(system_status.armed_state, "exit_delay") == 0;
}

static bool is_entry_delay_state(void)
{
    return strcmp(system_status.armed_state, "entry_delay") == 0;
}

static bool has_active_delay_countdown(void)
{
    return system_status.has_delay_remaining && system_status.delay_remaining > 0;
}

static bool local_alarm_uses_siren(void)
{
    return local_alarm_mode == LOCAL_ALARM_SIREN || local_alarm_mode == LOCAL_ALARM_BOTH;
}

static bool local_alarm_uses_strobe(void)
{
    return local_alarm_mode == LOCAL_ALARM_STROBE || local_alarm_mode == LOCAL_ALARM_BOTH;
}

static void apply_screen_alarm_strobe_locked(bool enabled)
{
    lv_obj_t *screen = lv_scr_act();
    if (screen == NULL) {
        return;
    }

    lv_obj_set_style_bg_color(screen, enabled ? lv_color_hex(0x4a1216) : lv_color_hex(0x111318), 0);
    if (alarm_state_label != NULL) {
        lv_obj_set_style_text_color(alarm_state_label,
                                    enabled ? lv_color_hex(0xffffff) : armed_state_color(system_status.armed_state),
                                    0);
    }
    if (alarm_detail_label != NULL && !system_status.driver_result_active) {
        lv_obj_set_style_text_color(alarm_detail_label,
                                    enabled ? lv_color_hex(0xffffff) : alarm_detail_color(),
                                    0);
    }
}

static void alarm_siren_timer_cb(lv_timer_t *timer)
{
    if (!local_alarm_uses_siren()) {
        lv_timer_pause(timer);
        return;
    }

    play_audio_signal(AUDIO_SIGNAL_ALARM_CHIRP);
}

static void alarm_strobe_timer_cb(lv_timer_t *timer)
{
    if (!local_alarm_uses_strobe()) {
        alarm_strobe_on = false;
        apply_screen_alarm_strobe_locked(false);
        lv_timer_pause(timer);
        return;
    }

    alarm_strobe_on = !alarm_strobe_on;
    apply_screen_alarm_strobe_locked(alarm_strobe_on);
}

static void update_local_alarm_outputs_locked(void)
{
    if (alarm_siren_timer == NULL) {
        alarm_siren_timer = lv_timer_create(alarm_siren_timer_cb, ALARM_SIREN_INTERVAL_MS, NULL);
        lv_timer_pause(alarm_siren_timer);
    }
    if (alarm_strobe_timer == NULL) {
        alarm_strobe_timer = lv_timer_create(alarm_strobe_timer_cb, ALARM_STROBE_INTERVAL_MS, NULL);
        lv_timer_pause(alarm_strobe_timer);
    }

    if (local_alarm_uses_siren()) {
        wake_screen();
        play_audio_signal(AUDIO_SIGNAL_ALARM_CHIRP);
        lv_timer_set_period(alarm_siren_timer, ALARM_SIREN_INTERVAL_MS);
        lv_timer_reset(alarm_siren_timer);
        lv_timer_resume(alarm_siren_timer);
    } else {
        lv_timer_pause(alarm_siren_timer);
    }

    if (local_alarm_uses_strobe()) {
        wake_screen();
        alarm_strobe_on = true;
        apply_screen_alarm_strobe_locked(true);
        lv_timer_set_period(alarm_strobe_timer, ALARM_STROBE_INTERVAL_MS);
        lv_timer_reset(alarm_strobe_timer);
        lv_timer_resume(alarm_strobe_timer);
    } else {
        alarm_strobe_on = false;
        apply_screen_alarm_strobe_locked(false);
        lv_timer_pause(alarm_strobe_timer);
    }
}

static void delay_chirp_timer_cb(lv_timer_t *timer)
{
    if (!system_status.ready || !has_active_delay_countdown()) {
        lv_timer_pause(timer);
        active_delay_chirp_mode = DELAY_CHIRP_NONE;
        return;
    }

    if (is_entry_delay_state()) {
        play_audio_signal(AUDIO_SIGNAL_ENTRY_CHIRP);
        lv_timer_set_period(timer, ENTRY_DELAY_CHIRP_INTERVAL_MS);
        return;
    }

    if (is_exit_delay_state()) {
        play_audio_signal(AUDIO_SIGNAL_EXIT_CHIRP);
        lv_timer_set_period(timer, EXIT_DELAY_CHIRP_INTERVAL_MS);
        return;
    }

    lv_timer_pause(timer);
}

static void update_delay_chirp_timer_locked(void)
{
    if (delay_chirp_timer == NULL) {
        delay_chirp_timer = lv_timer_create(delay_chirp_timer_cb, EXIT_DELAY_CHIRP_INTERVAL_MS, NULL);
    }

    if (system_status.ready && has_active_delay_countdown() && is_entry_delay_state()) {
        lv_timer_set_period(delay_chirp_timer, ENTRY_DELAY_CHIRP_INTERVAL_MS);
        if (active_delay_chirp_mode != DELAY_CHIRP_ENTRY) {
            lv_timer_reset(delay_chirp_timer);
        }
        lv_timer_resume(delay_chirp_timer);
        active_delay_chirp_mode = DELAY_CHIRP_ENTRY;
        return;
    }

    if (system_status.ready && has_active_delay_countdown() && is_exit_delay_state()) {
        lv_timer_set_period(delay_chirp_timer, EXIT_DELAY_CHIRP_INTERVAL_MS);
        if (active_delay_chirp_mode != DELAY_CHIRP_EXIT) {
            lv_timer_reset(delay_chirp_timer);
        }
        lv_timer_resume(delay_chirp_timer);
        active_delay_chirp_mode = DELAY_CHIRP_EXIT;
        return;
    }

    lv_timer_pause(delay_chirp_timer);
    active_delay_chirp_mode = DELAY_CHIRP_NONE;
}

static void get_mqtt_broker_display(char *buffer, size_t buffer_size)
{
    if (buffer_size == 0) {
        return;
    }

    const char *start = strstr(active_config.mqtt_broker_uri, "://");
    start = start == NULL ? active_config.mqtt_broker_uri : start + 3;

    const char *end = strchr(start, '/');
    size_t len = end == NULL ? strlen(start) : (size_t)(end - start);
    const char *at = (const char *)memchr(start, '@', len);
    if (at != NULL) {
        start = at + 1;
        len = end == NULL ? strlen(start) : (size_t)(end - start);
    }

    copy_mqtt_string(buffer, buffer_size, start, len);
}

static void get_hubitat_lan_display(char *buffer, size_t buffer_size)
{
    if (buffer_size == 0) {
        return;
    }

    if (active_config.hubitat_lan_endpoint[0] == '\0') {
        if (active_config.hubitat_pairing_code[0] != '\0') {
            snprintf(buffer, buffer_size, "pair %s", active_config.hubitat_pairing_code);
        } else {
            strlcpy(buffer, "not paired", buffer_size);
        }
        return;
    }

    const char *start = strstr(active_config.hubitat_lan_endpoint, "://");
    start = start == NULL ? active_config.hubitat_lan_endpoint : start + 3;

    const char *end = strchr(start, '/');
    size_t len = end == NULL ? strlen(start) : (size_t)(end - start);
    copy_mqtt_string(buffer, buffer_size, start, len);
}

static const char *transport_mode_display(void)
{
    if (strcmp(active_config.transport_mode, "lan") == 0) {
        return "Hubitat LAN";
    }
    if (strcmp(active_config.transport_mode, "both") == 0) {
        return "Both";
    }
    if (strcmp(active_config.transport_mode, "mqtt") == 0) {
        return "MQTT";
    }
    return "Auto";
}

static const char *local_alarm_mode_display(void)
{
    switch (local_alarm_mode) {
    case LOCAL_ALARM_SIREN:
        return "siren";
    case LOCAL_ALARM_STROBE:
        return "strobe";
    case LOCAL_ALARM_BOTH:
        return "siren+strobe";
    case LOCAL_ALARM_OFF:
    default:
        return "off";
    }
}

static void set_label_text_fmt(lv_obj_t *label, const char *fmt, ...)
{
    if (label == NULL) {
        return;
    }

    char text[128] = {};
    va_list args;
    va_start(args, fmt);
    vsnprintf(text, sizeof(text), fmt, args);
    va_end(args);
    lv_label_set_text(label, text);
}

static void apply_dashboard_status_to_ui_locked(void)
{
    if (dashboard_wifi_label == NULL) {
        return;
    }

    const esp_app_desc_t *app_desc = esp_app_get_description();
    char delay_text[24] = "none";
    if (system_status.has_delay_remaining) {
        snprintf(delay_text, sizeof(delay_text), "%ds", system_status.delay_remaining);
    }

    set_label_text_fmt(dashboard_wifi_label, "Security: %s", armed_state_label_text(system_status.armed_state));
    set_label_text_fmt(dashboard_mqtt_label, "Ready: %s", system_status.ready ? "yes" : "no");
    set_label_text_fmt(dashboard_ip_label, "Delay: %s", delay_text);
    set_label_text_fmt(dashboard_alarm_label, "Output: %s", local_alarm_mode_display());
    set_label_text_fmt(dashboard_topic_label,
                       "Request: %s",
                       last_action_request_id[0] != '\0' ? last_action_request_id : "none");
    set_label_text_fmt(dashboard_broker_label,
                       "Result: %s",
                       system_status.driver_result_active && system_status.driver_result_message[0] != '\0'
                           ? system_status.driver_result_message
                           : "none");
    set_label_text_fmt(dashboard_fw_label, "Firmware: %s", app_desc->version);
}

static void apply_network_status_to_ui_locked(void)
{
    if (network_wifi_label == NULL) {
        return;
    }

    char broker[MQTT_TOPIC_BUFFER_SIZE] = {};
    get_mqtt_broker_display(broker, sizeof(broker));
    char hubitat[MQTT_TOPIC_BUFFER_SIZE] = {};
    get_hubitat_lan_display(hubitat, sizeof(hubitat));
    char matter_status[24] = {};
    if (matter_transport_is_commissioned()) {
        strlcpy(matter_status, "paired", sizeof(matter_status));
    } else if (matter_transport_get_manual_pairing_code(matter_status, sizeof(matter_status)) != ESP_OK) {
        strlcpy(matter_status, "unavailable", sizeof(matter_status));
    }
    const char *auto_path = "";
    if (strcmp(active_config.transport_mode, "auto") == 0) {
        auto_path = mqtt_connected ? " -> MQTT" : " -> LAN";
    }

    set_label_text_fmt(network_wifi_label,
                       "Wi-Fi: %s (%s)",
                       wifi_connected ? "online" : "offline",
                       active_config.wifi_ssid[0] != '\0' ? active_config.wifi_ssid : "not configured");
    set_label_text_fmt(network_mqtt_label, "IP: %s", wifi_connected ? wifi_ip_text : "none");
    set_label_text_fmt(network_ip_label, "Actions: %s%s", transport_mode_display(), auto_path);
    set_label_text_fmt(network_device_label,
                       "MQTT: %s%s%s",
                       active_config.mqtt_broker_uri[0] == '\0' ? "not set" :
                       mqtt_connected ? "connected" : "offline",
                       active_config.mqtt_broker_uri[0] == '\0' ? "" : " ",
                       active_config.mqtt_broker_uri[0] == '\0' ? "" : broker);
    set_label_text_fmt(network_topic_label, "Matter: %s", matter_status);
    set_label_text_fmt(network_broker_label, "Hubitat: %s", hubitat);
}

static void apply_firmware_status_to_ui_locked(void)
{
    if (firmware_current_label == NULL) {
        return;
    }

    const esp_app_desc_t *app_desc = esp_app_get_description();
    set_label_text_fmt(firmware_current_label, "Current: %s", app_desc->version);
    set_label_text_fmt(firmware_status_label, "%s", ota_status);

    if (firmware_install_button == NULL) {
        return;
    }

    lv_obj_t *label = lv_obj_get_child(firmware_install_button, 0);
    if (ota_busy) {
        lv_obj_add_state(firmware_install_button, LV_STATE_DISABLED);
        lv_label_set_text(label, "Working...");
    } else if (ota_update_available) {
        lv_obj_clear_state(firmware_install_button, LV_STATE_DISABLED);
        set_label_text_fmt(label, "Install %s", ota_available_version);
    } else {
        lv_obj_add_state(firmware_install_button, LV_STATE_DISABLED);
        lv_label_set_text(label, "No update selected");
    }
    lv_obj_center(label);
}

static void set_ota_status_from_task(const char *status)
{
    if (!lvgl_port_lock(pdMS_TO_TICKS(100))) {
        return;
    }
    strlcpy(ota_status, status, sizeof(ota_status));
    apply_firmware_status_to_ui_locked();
    lvgl_port_unlock();
}

static bool any_dashboard_loaded(void)
{
    return hsm_dashboard.loaded || controls_dashboard.loaded;
}

static void apply_home_status_to_ui_locked(void)
{
    if (home_alarm_label == NULL) {
        return;
    }

    const bool lan_available = wifi_connected && active_config.hubitat_lan_endpoint[0] != '\0';
    const bool action_path_available = mqtt_connected || lan_available;
    const bool online_ready = wifi_connected && action_path_available && system_status.ready;
    const char *home_transport = matter_transport_is_commissioned() ? "Matter ok" :
                                 mqtt_connected ? "MQTT ok" :
                                 lan_available ? "LAN ok" : "LAN off";
    set_label_text_fmt(home_alarm_label, "%s", armed_state_label_text(system_status.armed_state));
    lv_obj_set_style_text_color(home_alarm_label, armed_state_color(system_status.armed_state), 0);
    set_label_text_fmt(home_ready_label, "%s", online_ready ? "Ready" : "Offline");
    lv_obj_set_style_text_color(home_ready_label,
                                online_ready ? lv_color_hex(0x38d26f) : lv_color_hex(0xe95757),
                                0);
    set_label_text_fmt(home_network_label,
                       "Wi-Fi %s / %s",
                       wifi_connected ? "ok" : "off",
                       home_transport);
    set_label_text_fmt(home_delay_label, "%s", alarm_detail_text());
    lv_obj_set_style_text_color(home_delay_label, alarm_detail_color(), 0);
    if (home_dashboard_button != NULL) {
        if (any_dashboard_loaded()) {
            lv_obj_clear_flag(home_dashboard_button, LV_OBJ_FLAG_HIDDEN);
        } else {
            lv_obj_add_flag(home_dashboard_button, LV_OBJ_FLAG_HIDDEN);
        }
    }
}

static dashboard_screen_t *visible_dashboard(void)
{
    if (controls_dashboard_active && controls_dashboard.loaded) {
        return &controls_dashboard;
    }
    if (!hsm_dashboard.loaded && controls_dashboard.loaded) {
        return &controls_dashboard;
    }
    return &hsm_dashboard;
}

static bool visible_dashboard_is_controls(void)
{
    return visible_dashboard() == &controls_dashboard && controls_dashboard.loaded;
}

static size_t dashboard_page_count(void)
{
    dashboard_screen_t *dashboard = visible_dashboard();
    if (!dashboard->loaded || dashboard->item_count == 0) {
        return 1;
    }
    return (dashboard->item_count + DASHBOARD_VISIBLE_ITEM_COUNT - 1) / DASHBOARD_VISIBLE_ITEM_COUNT;
}

static void update_dashboard_page_timer_locked(void)
{
    if (dashboard_page_timer == NULL) {
        return;
    }

    if (active_screen == APP_SCREEN_HSM && dashboard_page_count() > 1) {
        if (dashboard_page_auto_paused) {
            return;
        }
        lv_timer_set_period(dashboard_page_timer, DASHBOARD_PAGE_ROTATE_MS);
        lv_timer_reset(dashboard_page_timer);
        lv_timer_resume(dashboard_page_timer);
    } else {
        lv_timer_pause(dashboard_page_timer);
    }
}

static void apply_hsm_status_to_ui_locked(void)
{
    if (hsm_state_label == NULL) {
        return;
    }

    dashboard_screen_t *dashboard = visible_dashboard();
    const size_t page_count = dashboard_page_count();
    if (dashboard_page_index >= page_count) {
        dashboard_page_index = 0;
    }
    set_label_text_fmt(hsm_title_label, "%s", dashboard->title);
    const bool showing_controls_dashboard = visible_dashboard_is_controls();
    if (active_control_detail_index >= 0) {
        lv_obj_add_flag(hsm_page_label, LV_OBJ_FLAG_HIDDEN);
    } else if (page_count > 1) {
        set_label_text_fmt(hsm_page_label,
                           "%u / %u",
                           (unsigned)(dashboard_page_index + 1),
                           (unsigned)page_count);
        lv_obj_clear_flag(hsm_page_label, LV_OBJ_FLAG_HIDDEN);
    } else if (showing_controls_dashboard) {
        lv_label_set_text(hsm_page_label, "Controls");
        lv_obj_clear_flag(hsm_page_label, LV_OBJ_FLAG_HIDDEN);
    } else {
        lv_obj_add_flag(hsm_page_label, LV_OBJ_FLAG_HIDDEN);
    }

    if (dashboard->loaded) {
        lv_obj_add_flag(hsm_state_label, LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(hsm_ready_label, LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(hsm_delay_label, LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(hsm_last_result_label, LV_OBJ_FLAG_HIDDEN);

        const size_t item_offset = dashboard_page_index * DASHBOARD_VISIBLE_ITEM_COUNT;
        for (size_t i = 0; i < DASHBOARD_VISIBLE_ITEM_COUNT; ++i) {
            const size_t item_index = item_offset + i;
            if (item_index < dashboard->item_count) {
                const dashboard_item_t *item = &dashboard->items[item_index];
                const lv_color_t state_color = dashboard_state_color(item->state);
                const lv_color_t state_bg_color = dashboard_state_bg_color(item->state);
                set_label_text_fmt(hsm_item_labels[i], "%s", item->label);
                set_label_text_fmt(hsm_item_value_labels[i], "%s", item->value);
                lv_obj_set_style_text_color(hsm_item_labels[i], lv_color_hex(0xe6edf5), 0);
                lv_obj_set_style_text_color(hsm_item_value_labels[i], state_color, 0);
                lv_obj_set_style_border_color(hsm_item_tiles[i], state_color, 0);
                lv_obj_set_style_border_width(hsm_item_tiles[i], 2, 0);
                lv_obj_set_style_bg_color(hsm_item_tiles[i], state_bg_color, 0);
                lv_obj_clear_flag(hsm_item_tiles[i], LV_OBJ_FLAG_HIDDEN);
            } else {
                lv_obj_add_flag(hsm_item_tiles[i], LV_OBJ_FLAG_HIDDEN);
            }
        }
        update_dashboard_page_timer_locked();
        return;
    }

    lv_obj_clear_flag(hsm_state_label, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(hsm_ready_label, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(hsm_delay_label, LV_OBJ_FLAG_HIDDEN);
    lv_obj_clear_flag(hsm_last_result_label, LV_OBJ_FLAG_HIDDEN);
    for (size_t i = 0; i < DASHBOARD_VISIBLE_ITEM_COUNT; ++i) {
        lv_obj_add_flag(hsm_item_tiles[i], LV_OBJ_FLAG_HIDDEN);
    }

    set_label_text_fmt(hsm_state_label, "Security: %s", armed_state_label_text(system_status.armed_state));
    lv_obj_set_style_text_color(hsm_state_label, armed_state_color(system_status.armed_state), 0);
    set_label_text_fmt(hsm_ready_label, "Readiness: %s", system_status.ready ? "ready" : "offline");
    lv_obj_set_style_text_color(hsm_ready_label,
                                system_status.ready ? lv_color_hex(0x38d26f) : lv_color_hex(0xe95757),
                                0);
    set_label_text_fmt(hsm_delay_label, "Detail: %s", alarm_detail_text());
    lv_obj_set_style_text_color(hsm_delay_label, alarm_detail_color(), 0);

    const char *result = "none";
    if (system_status.driver_result[0] != '\0') {
        result = system_status.driver_result_message[0] != '\0'
                     ? system_status.driver_result_message
                     : driver_result_label_text(system_status.driver_result);
    }
    set_label_text_fmt(hsm_last_result_label, "Last: %s", result);
    update_dashboard_page_timer_locked();
}

static void apply_system_status_to_ui_locked(void)
{
    if (alarm_state_label == NULL || alarm_detail_label == NULL) {
        return;
    }

    lv_label_set_text(alarm_state_label, armed_state_label_text(system_status.armed_state));
    lv_obj_set_style_text_color(alarm_state_label, armed_state_color(system_status.armed_state), 0);
    if (system_status.driver_result_active) {
        const char *result_text = system_status.driver_result_message[0] != '\0'
                                      ? system_status.driver_result_message
                                      : driver_result_label_text(system_status.driver_result);
        lv_label_set_text(alarm_detail_label, result_text);
        lv_obj_set_style_text_color(alarm_detail_label, driver_result_color(system_status.driver_result), 0);
    } else {
        lv_label_set_text(alarm_detail_label, alarm_detail_text());
        lv_obj_set_style_text_color(alarm_detail_label, alarm_detail_color(), 0);
    }
    if (local_alarm_uses_strobe()) {
        apply_screen_alarm_strobe_locked(alarm_strobe_on);
    }
    apply_dashboard_status_to_ui_locked();
    apply_network_status_to_ui_locked();
    apply_home_status_to_ui_locked();
    apply_hsm_status_to_ui_locked();
}

static void update_system_status_ui(void)
{
    if (!lvgl_port_lock(pdMS_TO_TICKS(UI_UPDATE_LOCK_TIMEOUT_MS))) {
        ESP_LOGW(TAG, "ui update skipped; lvgl lock timeout after %dms", UI_UPDATE_LOCK_TIMEOUT_MS);
        return;
    }

    apply_system_status_to_ui_locked();
    update_delay_chirp_timer_locked();
    update_local_alarm_outputs_locked();
    if (is_entry_delay_state()) {
        show_app_screen_locked(APP_SCREEN_KEYPAD, "entry_delay");
    } else if (strcmp(system_status.armed_state, "alarming") == 0 || strcmp(system_status.armed_state, "alarm") == 0) {
        show_app_screen_locked(APP_SCREEN_KEYPAD, "alarming");
    }
    lvgl_port_unlock();
}

static void update_dashboard_ui(void)
{
    if (!lvgl_port_lock(0)) {
        return;
    }

    apply_dashboard_status_to_ui_locked();
    lvgl_port_unlock();
}

static void set_system_offline(void)
{
    system_status.ready = false;
    strlcpy(system_status.armed_state, "unknown", sizeof(system_status.armed_state));
    update_system_status_ui();
}

static void driver_result_timeout_cb(lv_timer_t *timer)
{
    system_status.driver_result_active = false;
    system_status.driver_result[0] = '\0';
    system_status.driver_result_message[0] = '\0';
    apply_system_status_to_ui_locked();
    lv_timer_pause(timer);
}

static void dashboard_page_timer_cb(lv_timer_t *timer)
{
    if (active_screen != APP_SCREEN_HSM || dashboard_page_count() <= 1) {
        dashboard_page_auto_paused = false;
        lv_timer_pause(timer);
        return;
    }

    if (dashboard_page_auto_paused) {
        dashboard_page_auto_paused = false;
        lv_timer_set_period(timer, DASHBOARD_PAGE_ROTATE_MS);
        lv_timer_reset(timer);
        lv_timer_resume(timer);
        return;
    }

    dashboard_page_index = (dashboard_page_index + 1) % dashboard_page_count();
    apply_hsm_status_to_ui_locked();
    if (board_type == KEYPAD_BOARD_AXS15231B) {
        lv_obj_invalidate(lv_scr_act());
    }
}

static bool page_dashboard_locked(int direction)
{
    const size_t page_count = dashboard_page_count();
    if (active_screen != APP_SCREEN_HSM || page_count <= 1) {
        return false;
    }

    if (direction > 0) {
        dashboard_page_index = (dashboard_page_index + 1) % page_count;
    } else {
        dashboard_page_index = dashboard_page_index == 0 ? page_count - 1 : dashboard_page_index - 1;
    }

    dashboard_page_auto_paused = true;
    apply_hsm_status_to_ui_locked();
    if (board_type == KEYPAD_BOARD_AXS15231B) {
        lv_obj_invalidate(lv_scr_act());
    }
    if (dashboard_page_timer != NULL) {
        lv_timer_set_period(dashboard_page_timer, DASHBOARD_MANUAL_PAGE_HOLD_MS);
        lv_timer_reset(dashboard_page_timer);
        lv_timer_resume(dashboard_page_timer);
    }
    return true;
}

static void handle_mqtt_state_payload(const char *payload, size_t payload_len)
{
    char json[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    copy_mqtt_string(json, sizeof(json), payload, payload_len);

    bool ready = false;
    int delay_remaining = 0;
    char armed_state[MQTT_STATE_VALUE_SIZE] = {};
    char state_device_id[DEVICE_ID_SIZE] = {};
    if (!parse_json_bool_field(json, "ready", &ready) ||
        !parse_json_string_field(json, "armed_state", armed_state, sizeof(armed_state))) {
        ESP_LOGW(TAG, "mqtt state ignored; expected ready and armed_state");
        return;
    }

    if (parse_json_string_field(json, "device_id", state_device_id, sizeof(state_device_id)) &&
        state_device_id[0] != '\0' &&
        strcmp(state_device_id, active_config.device_id) != 0) {
        ESP_LOGW(TAG, "mqtt state ignored; device_id=%s expected=%s", state_device_id, active_config.device_id);
        return;
    }

    if (!is_known_armed_state(armed_state)) {
        ESP_LOGW(TAG, "mqtt state has unknown armed_state=%s; displaying unknown", armed_state);
        strlcpy(armed_state, "unknown", sizeof(armed_state));
    }

    system_status.ready = ready;
    strlcpy(system_status.armed_state, armed_state, sizeof(system_status.armed_state));
    system_status.has_delay_remaining = parse_json_int_field(json, "delay_remaining", &delay_remaining);
    system_status.delay_remaining = delay_remaining;
    if (strcmp(system_status.armed_state, "alarming") == 0 || strcmp(system_status.armed_state, "alarm") == 0) {
        if (local_alarm_mode == LOCAL_ALARM_OFF) {
            local_alarm_mode = LOCAL_ALARM_BOTH;
        }
    } else {
        local_alarm_mode = LOCAL_ALARM_OFF;
    }
    if (system_status.driver_result_active &&
        (strcmp(system_status.driver_result, "accepted") == 0 || strcmp(system_status.driver_result, "ok") == 0)) {
        system_status.driver_result_active = false;
        system_status.driver_result[0] = '\0';
        system_status.driver_result_message[0] = '\0';
    }
    if (system_status.has_delay_remaining) {
        ESP_LOGI(TAG, "mqtt state ready=%s armed_state=%s delay_remaining=%d",
                 ready ? "true" : "false",
                 system_status.armed_state,
                 system_status.delay_remaining);
    } else {
        ESP_LOGI(TAG, "mqtt state ready=%s armed_state=%s delay_remaining=absent",
                 ready ? "true" : "false",
                 system_status.armed_state);
    }
    matter_transport_set_armed_state(system_status.armed_state, system_status.ready);
    update_system_status_ui();
    publish_mqtt_status();
}

static void handle_mqtt_result_payload(const char *payload, size_t payload_len)
{
    char json[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    copy_mqtt_string(json, sizeof(json), payload, payload_len);

    char result_device_id[DEVICE_ID_SIZE] = {};
    char result[MQTT_RESULT_VALUE_SIZE] = {};
    char message[MQTT_RESULT_MESSAGE_SIZE] = {};
    char reason[MQTT_RESULT_VALUE_SIZE] = {};
    char request_id[MQTT_RESULT_MESSAGE_SIZE] = {};

    if (!parse_json_string_field(json, "result", result, sizeof(result))) {
        ESP_LOGW(TAG, "mqtt result ignored; expected result");
        return;
    }

    if (parse_json_string_field(json, "device_id", result_device_id, sizeof(result_device_id)) &&
        result_device_id[0] != '\0' &&
        strcmp(result_device_id, active_config.device_id) != 0) {
        ESP_LOGW(TAG, "mqtt result ignored; device_id=%s expected=%s", result_device_id, active_config.device_id);
        return;
    }

    parse_json_string_field(json, "message", message, sizeof(message));
    parse_json_string_field(json, "reason", reason, sizeof(reason));
    parse_json_string_field(json, "request_id", request_id, sizeof(request_id));

    ESP_LOGI(TAG, "mqtt result request_id=%s result=%s reason=%s message=%s", request_id, result, reason, message);
    if (!lvgl_port_lock(0)) {
        return;
    }

    system_status.driver_result_active = true;
    strlcpy(system_status.driver_result, result, sizeof(system_status.driver_result));
    strlcpy(system_status.driver_result_message, message, sizeof(system_status.driver_result_message));
    apply_system_status_to_ui_locked();

    if (driver_result_timer == NULL) {
        driver_result_timer = lv_timer_create(driver_result_timeout_cb, DRIVER_RESULT_DISPLAY_MS, NULL);
    }
    lv_timer_reset(driver_result_timer);
    lv_timer_resume(driver_result_timer);
    lvgl_port_unlock();
}

static void handle_mqtt_command_payload(const char *payload, size_t payload_len)
{
    char json[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    copy_mqtt_string(json, sizeof(json), payload, payload_len);

    char command_device_id[DEVICE_ID_SIZE] = {};
    char command[MQTT_COMMAND_VALUE_SIZE] = {};

    if (!parse_json_string_field(json, "command", command, sizeof(command))) {
        ESP_LOGW(TAG, "mqtt command ignored; expected command");
        return;
    }

    if (parse_json_string_field(json, "device_id", command_device_id, sizeof(command_device_id)) &&
        command_device_id[0] != '\0' &&
        strcmp(command_device_id, active_config.device_id) != 0) {
        ESP_LOGW(TAG, "mqtt command ignored; device_id=%s expected=%s", command_device_id, active_config.device_id);
        return;
    }

    ESP_LOGI(TAG, "mqtt command=%s", command);
    if (strcmp(command, "beep") == 0) {
        play_key_click();
    } else if (strcmp(command, "siren") == 0) {
        local_alarm_mode = LOCAL_ALARM_SIREN;
        if (lvgl_port_lock(0)) {
            update_local_alarm_outputs_locked();
            lvgl_port_unlock();
        }
    } else if (strcmp(command, "strobe") == 0) {
        local_alarm_mode = LOCAL_ALARM_STROBE;
        if (lvgl_port_lock(0)) {
            update_local_alarm_outputs_locked();
            lvgl_port_unlock();
        }
    } else if (strcmp(command, "both") == 0) {
        local_alarm_mode = LOCAL_ALARM_BOTH;
        if (lvgl_port_lock(0)) {
            update_local_alarm_outputs_locked();
            lvgl_port_unlock();
        }
    } else if (strcmp(command, "off") == 0) {
        local_alarm_mode = LOCAL_ALARM_OFF;
        if (lvgl_port_lock(0)) {
            update_local_alarm_outputs_locked();
            lvgl_port_unlock();
        }
    } else {
        ESP_LOGW(TAG, "mqtt command ignored; unknown command=%s", command);
    }
}

static bool copy_json_object_slice(const char *object_start, char *dest, size_t dest_size, const char **next)
{
    if (dest_size == 0 || object_start == NULL || *object_start != '{') {
        return false;
    }

    bool in_string = false;
    bool escaped = false;
    int depth = 0;
    const char *cursor = object_start;

    while (*cursor != '\0') {
        const char ch = *cursor;
        if (in_string) {
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                in_string = false;
            }
        } else {
            if (ch == '"') {
                in_string = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    size_t len = (size_t)(cursor - object_start + 1);
                    copy_mqtt_string(dest, dest_size, object_start, len);
                    if (next != NULL) {
                        *next = cursor + 1;
                    }
                    return true;
                }
            }
        }
        cursor++;
    }

    return false;
}

static void handle_mqtt_dashboard_payload(const char *dashboard_id, const char *payload, size_t payload_len)
{
    const bool is_controls_dashboard = strcmp(dashboard_id, "controls") == 0;
    dashboard_screen_t *target_dashboard = is_controls_dashboard ? &controls_dashboard : &hsm_dashboard;

    if (payload_len == 0) {
        if (target_dashboard->loaded && strcmp(target_dashboard->id, dashboard_id) == 0) {
            memset(target_dashboard, 0, sizeof(*target_dashboard));
            strlcpy(target_dashboard->id, dashboard_id, sizeof(target_dashboard->id));
            strlcpy(target_dashboard->title, is_controls_dashboard ? "Controls" : "Dashboard", sizeof(target_dashboard->title));
            dashboard_page_index = 0;
            dashboard_page_auto_paused = false;
            if (is_controls_dashboard) {
                controls_dashboard_active = false;
            }
            ESP_LOGI(TAG, "mqtt dashboard cleared id=%s", dashboard_id);
            if (active_screen == APP_SCREEN_HSM) {
                show_app_screen_locked(APP_SCREEN_HOME, "dashboard_cleared");
            }
            update_system_status_ui();
        } else {
            ESP_LOGI(TAG,
                     "mqtt dashboard clear ignored id=%s active=%s",
                     dashboard_id,
                     target_dashboard->id[0] == '\0' ? "none" : target_dashboard->id);
        }
        return;
    }

    char json[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    copy_mqtt_string(json, sizeof(json), payload, payload_len);

    char dashboard_device_id[DEVICE_ID_SIZE] = {};
    if (parse_json_string_field(json, "device_id", dashboard_device_id, sizeof(dashboard_device_id)) &&
        dashboard_device_id[0] != '\0' &&
        strcmp(dashboard_device_id, active_config.device_id) != 0) {
        ESP_LOGW(TAG,
                 "mqtt dashboard ignored id=%s; device_id=%s expected=%s",
                 dashboard_id,
                 dashboard_device_id,
                 active_config.device_id);
        return;
    }

    dashboard_screen_t submitted = {};
    strlcpy(submitted.id, dashboard_id, sizeof(submitted.id));
    if (!parse_json_string_field(json, "title", submitted.title, sizeof(submitted.title)) ||
        submitted.title[0] == '\0') {
        strlcpy(submitted.title, dashboard_id, sizeof(submitted.title));
    }

    const char *items_field = strstr(json, "\"items\"");
    const char *items_start = items_field == NULL ? NULL : strchr(items_field, '[');
    if (items_start == NULL) {
        ESP_LOGW(TAG, "mqtt dashboard ignored id=%s; expected items array", dashboard_id);
        return;
    }

    const char *cursor = items_start + 1;
    while (*cursor != '\0' && submitted.item_count < DASHBOARD_MAX_ITEM_COUNT) {
        const char *object_start = strchr(cursor, '{');
        const char *array_end = strchr(cursor, ']');
        if (object_start == NULL || (array_end != NULL && array_end < object_start)) {
            break;
        }

        char item_json[320] = {};
        const char *next = NULL;
        if (!copy_json_object_slice(object_start, item_json, sizeof(item_json), &next)) {
            break;
        }

        if (submitted.item_count >= DASHBOARD_MAX_ITEM_COUNT) {
            break;
        }

        dashboard_item_t *target = &submitted.items[submitted.item_count];
        parse_json_string_field(item_json, "id", target->id, sizeof(target->id));
        parse_json_string_field(item_json, "label", target->label, sizeof(target->label));
        parse_json_string_field(item_json, "value", target->value, sizeof(target->value));
        parse_json_string_field(item_json, "state", target->state, sizeof(target->state));
        parse_json_string_field(item_json, "kind", target->kind, sizeof(target->kind));
        target->has_level = parse_json_int_field(item_json, "level", &target->level);
        parse_json_bool_field(item_json, "primary", &target->primary);
        parse_json_bool_field(item_json, "detail", &target->detail);

        if (target->label[0] == '\0' || target->value[0] == '\0') {
            cursor = next == NULL ? object_start + 1 : next;
            continue;
        }
        if (target->state[0] == '\0') {
            strlcpy(target->state, "info", sizeof(target->state));
        }
        submitted.item_count++;
        cursor = next == NULL ? object_start + 1 : next;
    }

    if (submitted.item_count == 0) {
        ESP_LOGW(TAG, "mqtt dashboard ignored id=%s; no usable items", dashboard_id);
        return;
    }

    submitted.loaded = true;
    *target_dashboard = submitted;
    dashboard_page_index = 0;
    dashboard_page_auto_paused = false;
    ESP_LOGI(TAG, "mqtt dashboard id=%s title=%s items=%u",
             dashboard_id,
             target_dashboard->title,
             (unsigned)target_dashboard->item_count);

    update_system_status_ui();
}

static void build_mqtt_topic(char *buffer, size_t buffer_size, const char *suffix)
{
    snprintf(buffer, buffer_size, "%s/%s", active_config.mqtt_topic_prefix, suffix);
}

static void build_setup_ap_ssid(void)
{
    uint8_t mac[6] = {};
    ESP_ERROR_CHECK(esp_read_mac(mac, ESP_MAC_WIFI_SOFTAP));
    const char *ssid_identity = active_config.loaded_from_nvs ? active_config.device_id : "Setup";
    snprintf(setup_ap_ssid, sizeof(setup_ap_ssid), "Keypad-%.20s-%02X%02X", ssid_identity, mac[4], mac[5]);
}

static void derive_mqtt_identifiers(device_config_t *config)
{
    snprintf(config->mqtt_client_id, sizeof(config->mqtt_client_id), "keypad-%s", config->device_id);
    snprintf(config->mqtt_topic_prefix, sizeof(config->mqtt_topic_prefix), "keypad/%s", config->device_id);
}

static bool is_valid_device_id(const char *device_id)
{
    if (device_id == NULL || device_id[0] == '\0') {
        return false;
    }

    for (const char *cursor = device_id; *cursor != '\0'; cursor++) {
        const char ch = *cursor;
        const bool allowed = (ch >= 'A' && ch <= 'Z') ||
                             (ch >= 'a' && ch <= 'z') ||
                             (ch >= '0' && ch <= '9') ||
                             ch == '_' ||
                             ch == '-';
        if (!allowed) {
            return false;
        }
    }

    return true;
}

static void restart_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(1200));
    esp_restart();
}

static esp_err_t setup_http_get_handler(httpd_req_t *request)
{
    ESP_LOGI(TAG, "setup http get uri=%s", request->uri);

    char broker[MQTT_TOPIC_BUFFER_SIZE] = {};
    get_mqtt_broker_display(broker, sizeof(broker));
    const char *wifi_password_hint = active_config.wifi_password[0] != '\0'
                                        ? "******** (saved; leave blank to keep)"
                                        : "Password";
    const char *mqtt_password_hint = active_config.mqtt_password[0] != '\0'
                                        ? "******** (saved; leave blank to keep)"
                                        : "Optional";
    const char *auto_selected = strcmp(active_config.transport_mode, "auto") == 0 ? " selected" : "";
    const char *mqtt_selected = strcmp(active_config.transport_mode, "mqtt") == 0 ? " selected" : "";
    const char *lan_selected = strcmp(active_config.transport_mode, "lan") == 0 ? " selected" : "";
    const char *both_selected = strcmp(active_config.transport_mode, "both") == 0 ? " selected" : "";
    const char *hubitat_url_hint = active_config.hubitat_lan_endpoint[0] != '\0'
                                      ? "Already paired; leave blank to keep, or CLEAR to unpair"
                                      : "Optional advanced fallback";

    constexpr size_t page_size = 4400;
    char *page = (char *)calloc(1, page_size);
    if (page == NULL) {
        httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "out of memory");
        return ESP_FAIL;
    }

    int page_len = snprintf(
        page,
        page_size,
        "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        "<title>Keypad Setup</title>"
        "<style>"
        "body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;margin:0;background:#111318;color:#e6edf5}"
        "main{max-width:460px;margin:0 auto;padding:28px 20px}"
        "h1{font-size:28px;margin:0 0 6px}"
        "p{color:#b8c6d8;line-height:1.35}"
        "label{display:block;margin:16px 0 6px;color:#b8c6d8}"
        "input,select{box-sizing:border-box;width:100%%;font-size:18px;padding:12px;border-radius:8px;border:1px solid #465365;background:#202733;color:#e6edf5}"
        "button{margin-top:20px;width:100%%;font-size:18px;padding:12px;border:0;border-radius:8px;background:#38d26f;color:#07100a}"
        ".hint{margin:6px 0 0;font-size:13px;color:#8ea1b8}"
        ".note{margin-top:18px;font-size:14px;color:#f0b84a}"
        "</style></head><body><main>"
        "<h1>Keypad Setup</h1>"
        "<p>MQTT is optional. Hubitat LAN can be paired from the Hubitat app after restart.</p>"
        "<form method=\"post\" action=\"/save\">"
        "<label>Device ID</label><input name=\"device_id\" value=\"%s\" required maxlength=\"31\" pattern=\"[A-Za-z0-9_-]+\" autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\" title=\"Letters, numbers, underscore, and hyphen only\">"
        "<p class=\"hint\">Case-sensitive. Used in Hubitat messages and MQTT topics. Use letters, numbers, underscore, or hyphen; no spaces.</p>"
        "<label>Action transport</label><select name=\"transport\">"
        "<option value=\"auto\"%s>Auto</option>"
        "<option value=\"mqtt\"%s>MQTT</option>"
        "<option value=\"lan\"%s>Hubitat LAN</option>"
        "<option value=\"both\"%s>Both</option>"
        "</select>"
        "<label>Home Wi-Fi SSID</label><input name=\"wifi_ssid\" value=\"%s\" required maxlength=\"31\">"
        "<label>Home Wi-Fi Password</label><input name=\"wifi_password\" type=\"password\" placeholder=\"%s\" maxlength=\"63\">"
        "<label>MQTT Broker (optional if Hubitat LAN is set)</label><input name=\"mqtt_broker\" value=\"%s\" maxlength=\"95\">"
        "<label>MQTT Username (optional)</label><input name=\"mqtt_username\" value=\"%s\" maxlength=\"63\">"
        "<label>MQTT Password (optional)</label><input name=\"mqtt_password\" type=\"password\" placeholder=\"%s\" maxlength=\"63\">"
        "<label>Hubitat LAN action URL (advanced)</label><input name=\"hubitat_url\" value=\"\" placeholder=\"%s\" maxlength=\"191\">"
        "<button>Save and restart</button>"
        "</form>"
        "<p class=\"note\">The keypad will restart after saving and boot from the stored settings.</p>"
        "</main></body></html>",
        active_config.device_id,
        auto_selected,
        mqtt_selected,
        lan_selected,
        both_selected,
        active_config.loaded_from_nvs ? active_config.wifi_ssid : "",
        wifi_password_hint,
        broker,
        active_config.mqtt_username,
        mqtt_password_hint,
        hubitat_url_hint);

    if (page_len < 0 || page_len >= (int)page_size) {
        free(page);
        httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "setup page too large");
        return ESP_FAIL;
    }

    httpd_resp_set_type(request, "text/html");
    httpd_resp_send(request, page, HTTPD_RESP_USE_STRLEN);
    free(page);
    return ESP_OK;
}

static esp_err_t setup_http_save_handler(httpd_req_t *request)
{
    ESP_LOGI(TAG, "setup http post uri=%s len=%d", request->uri, request->content_len);

    char body[1024] = {};
    int total_read = 0;

    while (total_read < request->content_len && total_read < (int)sizeof(body) - 1) {
        int read_len = httpd_req_recv(request, body + total_read, sizeof(body) - 1 - total_read);
        if (read_len <= 0) {
            httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "failed to read request");
            return ESP_FAIL;
        }
        total_read += read_len;
    }
    body[total_read] = '\0';

    device_config_t submitted = active_config;
    get_form_field(body, "device_id", submitted.device_id, sizeof(submitted.device_id));
    get_form_field(body, "transport", submitted.transport_mode, sizeof(submitted.transport_mode));
    get_form_field(body, "wifi_ssid", submitted.wifi_ssid, sizeof(submitted.wifi_ssid));
    char submitted_wifi_password[WIFI_PASSWORD_SIZE] = {};
    get_form_field(body, "wifi_password", submitted_wifi_password, sizeof(submitted_wifi_password));
    get_form_field(body, "mqtt_broker", submitted.mqtt_broker_uri, sizeof(submitted.mqtt_broker_uri));
    get_form_field(body, "mqtt_username", submitted.mqtt_username, sizeof(submitted.mqtt_username));
    char submitted_mqtt_password[MQTT_PASSWORD_SIZE] = {};
    get_form_field(body, "mqtt_password", submitted_mqtt_password, sizeof(submitted_mqtt_password));
    char submitted_hubitat_url[HUBITAT_ENDPOINT_URL_SIZE] = {};
    get_form_field(body, "hubitat_url", submitted_hubitat_url, sizeof(submitted_hubitat_url));
    if (submitted_wifi_password[0] != '\0') {
        strlcpy(submitted.wifi_password, submitted_wifi_password, sizeof(submitted.wifi_password));
    }
    if (submitted_mqtt_password[0] != '\0') {
        strlcpy(submitted.mqtt_password, submitted_mqtt_password, sizeof(submitted.mqtt_password));
    }
    if (strcasecmp(submitted_hubitat_url, "CLEAR") == 0) {
        submitted.hubitat_lan_endpoint[0] = '\0';
        submitted.hubitat_pairing_code[0] = '\0';
    } else if (submitted_hubitat_url[0] != '\0') {
        strlcpy(submitted.hubitat_lan_endpoint, submitted_hubitat_url, sizeof(submitted.hubitat_lan_endpoint));
    }
    derive_mqtt_identifiers(&submitted);
    bool broker_uri_valid = normalize_mqtt_broker_uri(submitted.mqtt_broker_uri, sizeof(submitted.mqtt_broker_uri));
    const bool mqtt_configured = submitted.mqtt_broker_uri[0] != '\0';

    if (!is_valid_device_id(submitted.device_id) ||
        submitted.wifi_ssid[0] == '\0' ||
        !is_known_transport_mode(submitted.transport_mode) ||
        ((strcmp(submitted.transport_mode, "mqtt") == 0 || strcmp(submitted.transport_mode, "both") == 0) && !mqtt_configured) ||
        !broker_uri_valid) {
        httpd_resp_send_err(request, HTTPD_400_BAD_REQUEST, "device id, wifi, or transport setting is invalid");
        return ESP_FAIL;
    }

    esp_err_t err = save_config_to_nvs(&submitted);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "setup config save failed: %s", esp_err_to_name(err));
        httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "save failed");
        return ESP_FAIL;
    }

    active_config = submitted;
    active_config.loaded_from_nvs = true;
    ESP_LOGI(TAG, "setup config saved device_id=%s topic=%s", active_config.device_id, active_config.mqtt_topic_prefix);

    httpd_resp_set_type(request, "text/html");
    httpd_resp_sendstr(request,
                       "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                       "<title>Keypad Saved</title></head><body style=\"font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;background:#111318;color:#e6edf5;padding:28px\">"
                       "<h1>Saved</h1><p>The keypad is restarting.</p></body></html>");
    xTaskCreate(restart_task, "restart", 2048, NULL, 5, NULL);
    return ESP_OK;
}

static esp_err_t setup_http_redirect_handler(httpd_req_t *request)
{
    ESP_LOGI(TAG, "setup http redirect uri=%s", request->uri);
    httpd_resp_set_status(request, "302 Found");
    httpd_resp_set_hdr(request, "Location", "/");
    httpd_resp_sendstr(request, "Keypad setup is at http://192.168.4.1/");
    return ESP_OK;
}

static void start_setup_web_server(void)
{
    if (setup_http_server != NULL) {
        return;
    }

    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = 80;
    config.stack_size = 8192;
    config.lru_purge_enable = true;

    ESP_ERROR_CHECK(httpd_start(&setup_http_server, &config));

    httpd_uri_t root_uri = {};
    root_uri.uri = "/";
    root_uri.method = HTTP_GET;
    root_uri.handler = setup_http_get_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(setup_http_server, &root_uri));

    httpd_uri_t save_uri = {};
    save_uri.uri = "/save";
    save_uri.method = HTTP_POST;
    save_uri.handler = setup_http_save_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(setup_http_server, &save_uri));

    httpd_uri_t fallback_uri = {};
    fallback_uri.uri = "/*";
    fallback_uri.method = HTTP_GET;
    fallback_uri.handler = setup_http_redirect_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(setup_http_server, &fallback_uri));

    ESP_LOGI(TAG, "setup web server started at http://192.168.4.1");
}

static const char *wifi_disconnect_reason_name(uint8_t reason)
{
    switch ((wifi_err_reason_t)reason) {
    case WIFI_REASON_NO_AP_FOUND:
        return "NO_AP_FOUND";
    case WIFI_REASON_AUTH_FAIL:
        return "AUTH_FAIL";
    case WIFI_REASON_ASSOC_FAIL:
        return "ASSOC_FAIL";
    case WIFI_REASON_HANDSHAKE_TIMEOUT:
        return "HANDSHAKE_TIMEOUT";
    case WIFI_REASON_NO_AP_FOUND_W_COMPATIBLE_SECURITY:
        return "NO_AP_FOUND_W_COMPATIBLE_SECURITY";
    case WIFI_REASON_NO_AP_FOUND_IN_AUTHMODE_THRESHOLD:
        return "NO_AP_FOUND_IN_AUTHMODE_THRESHOLD";
    case WIFI_REASON_NO_AP_FOUND_IN_RSSI_THRESHOLD:
        return "NO_AP_FOUND_IN_RSSI_THRESHOLD";
    case WIFI_REASON_BEACON_TIMEOUT:
        return "BEACON_TIMEOUT";
    default:
        return "UNKNOWN";
    }
}

static const char *wifi_auth_mode_name(wifi_auth_mode_t authmode)
{
    switch (authmode) {
    case WIFI_AUTH_OPEN:
        return "OPEN";
    case WIFI_AUTH_WEP:
        return "WEP";
    case WIFI_AUTH_WPA_PSK:
        return "WPA_PSK";
    case WIFI_AUTH_WPA2_PSK:
        return "WPA2_PSK";
    case WIFI_AUTH_WPA_WPA2_PSK:
        return "WPA_WPA2_PSK";
    case WIFI_AUTH_WPA2_ENTERPRISE:
        return "WPA2_ENTERPRISE";
    case WIFI_AUTH_WPA3_PSK:
        return "WPA3_PSK";
    case WIFI_AUTH_WPA2_WPA3_PSK:
        return "WPA2_WPA3_PSK";
    case WIFI_AUTH_WAPI_PSK:
        return "WAPI_PSK";
    case WIFI_AUTH_OWE:
        return "OWE";
    case WIFI_AUTH_WPA3_ENT_192:
        return "WPA3_ENT_192";
    default:
        return "UNKNOWN";
    }
}

static void wifi_scan_log_task(void *arg)
{
    uint16_t ap_count = 0;
    ESP_ERROR_CHECK_WITHOUT_ABORT(esp_wifi_scan_get_ap_num(&ap_count));
    ESP_LOGI(TAG, "wifi scan found %u access point%s", ap_count, ap_count == 1 ? "" : "s");

    uint16_t record_count = ap_count < WIFI_SCAN_RECORD_LIMIT ? ap_count : WIFI_SCAN_RECORD_LIMIT;
    if (record_count == 0) {
        ESP_LOGI(TAG, "wifi reconnecting after scan");
        esp_wifi_connect();
        vTaskDelete(NULL);
        return;
    }

    memset(wifi_scan_records, 0, sizeof(wifi_scan_records));
    ESP_ERROR_CHECK_WITHOUT_ABORT(esp_wifi_scan_get_ap_records(&record_count, wifi_scan_records));

    for (uint16_t i = 0; i < record_count; ++i) {
        const char *ssid = (const char *)wifi_scan_records[i].ssid;
        if (wifi_scan_records[i].ssid[0] == '\0') {
            ssid = "<hidden>";
        }

        ESP_LOGI(TAG,
                 "wifi ap %u ssid=%s rssi=%d channel=%u auth=%s",
                 i + 1,
                 ssid,
                 wifi_scan_records[i].rssi,
                 wifi_scan_records[i].primary,
                 wifi_auth_mode_name(wifi_scan_records[i].authmode));
    }

    if (ap_count > record_count) {
        ESP_LOGI(TAG, "wifi scan omitted %u lower-priority access point%s",
                 ap_count - record_count,
                 (ap_count - record_count) == 1 ? "" : "s");
    }

    ESP_LOGI(TAG, "wifi reconnecting after scan");
    esp_wifi_connect();
    vTaskDelete(NULL);
}

static void start_wifi_scan(void)
{
    if (wifi_scan_running || wifi_scan_completed) {
        return;
    }

    wifi_scan_config_t scan_config = {};
    scan_config.show_hidden = true;

    ESP_LOGI(TAG, "wifi no matching AP found; scanning visible 2.4 GHz networks once");
    esp_err_t err = esp_wifi_scan_start(&scan_config, false);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "wifi scan start failed: %s", esp_err_to_name(err));
        esp_wifi_connect();
        return;
    }

    wifi_scan_running = true;
}

static void publish_mqtt_availability(const char *state)
{
    if (mqtt_client == NULL || !mqtt_connected) {
        return;
    }

    char topic[MQTT_TOPIC_BUFFER_SIZE] = {};
    build_mqtt_topic(topic, sizeof(topic), "availability");
    int msg_id = esp_mqtt_client_publish(mqtt_client, topic, state, 0, 1, 1);
    ESP_LOGI(TAG, "mqtt availability=%s msg_id=%d", state, msg_id);
}

static void publish_mqtt_status(void)
{
    if (mqtt_client == NULL || !mqtt_connected) {
        return;
    }

    char topic[MQTT_TOPIC_BUFFER_SIZE] = {};
    build_mqtt_topic(topic, sizeof(topic), "status");

    const esp_app_desc_t *app_desc = esp_app_get_description();
    char payload[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int payload_len = 0;
    if (system_status.has_delay_remaining) {
        payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"device_id\":\"%s\",\"ready\":%s,\"armed_state\":\"%s\","
                               "\"delay_remaining\":%d,"
                               "\"wifi_connected\":%s,\"mqtt_connected\":%s,\"ip\":\"%s\","
                               "\"firmware\":\"%s\",\"topic_prefix\":\"%s\",\"last_request_id\":\"%s\"}",
                               active_config.device_id,
                               system_status.ready ? "true" : "false",
                               system_status.armed_state,
                               system_status.delay_remaining,
                               wifi_connected ? "true" : "false",
                               mqtt_connected ? "true" : "false",
                               wifi_connected ? wifi_ip_text : "none",
                               app_desc->version,
                               active_config.mqtt_topic_prefix,
                               last_action_request_id);
    } else {
        payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"device_id\":\"%s\",\"ready\":%s,\"armed_state\":\"%s\","
                               "\"wifi_connected\":%s,\"mqtt_connected\":%s,\"ip\":\"%s\","
                               "\"firmware\":\"%s\",\"topic_prefix\":\"%s\",\"last_request_id\":\"%s\"}",
                               active_config.device_id,
                               system_status.ready ? "true" : "false",
                               system_status.armed_state,
                               wifi_connected ? "true" : "false",
                               mqtt_connected ? "true" : "false",
                               wifi_connected ? wifi_ip_text : "none",
                               app_desc->version,
                               active_config.mqtt_topic_prefix,
                               last_action_request_id);
    }
    if (payload_len < 0 || payload_len >= (int)sizeof(payload)) {
        ESP_LOGW(TAG, "mqtt status payload too large");
        return;
    }

    int msg_id = esp_mqtt_client_publish(mqtt_client, topic, payload, 0, 1, 1);
    ESP_LOGI(TAG, "mqtt status topic=%s msg_id=%d", topic, msg_id);
}

typedef struct {
    char url[HUBITAT_ENDPOINT_URL_SIZE];
    char payload[MQTT_PAYLOAD_BUFFER_SIZE];
    int response_handler;
} hubitat_lan_post_t;

enum {
    HUBITAT_LAN_RESPONSE_IGNORE = 0,
    HUBITAT_LAN_RESPONSE_RESULT = 1,
    HUBITAT_LAN_RESPONSE_STATE = 2,
};

static void post_hubitat_lan_payload(hubitat_lan_post_t *post);

static void hubitat_lan_action_post_task(void *arg)
{
    hubitat_lan_post_t *post = (hubitat_lan_post_t *)arg;
    if (post == NULL) {
        vTaskDelete(NULL);
        return;
    }

    post_hubitat_lan_payload(post);
    vTaskDelete(NULL);
}

static void post_hubitat_lan_payload(hubitat_lan_post_t *post)
{
    if (post == NULL) {
        return;
    }

    esp_http_client_config_t config = {};
    config.url = post->url;
    config.timeout_ms = 2500;
    config.keep_alive_enable = false;

    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (client == NULL) {
        ESP_LOGW(TAG, "hubitat lan action client init failed");
        free(post);
        return;
    }

    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");

    const int payload_len = strlen(post->payload);
    esp_err_t err = esp_http_client_open(client, payload_len);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "hubitat lan action open failed: %s", esp_err_to_name(err));
        esp_http_client_cleanup(client);
        free(post);
        return;
    }

    int written = esp_http_client_write(client, post->payload, payload_len);
    if (written != payload_len) {
        ESP_LOGW(TAG, "hubitat lan action write failed written=%d expected=%d", written, payload_len);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        free(post);
        return;
    }

    esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);
    if (status < 200 || status >= 300) {
        ESP_LOGW(TAG, "hubitat lan action post failed status=%d", status);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        free(post);
        return;
    }

    char response[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int response_len = esp_http_client_read(client, response, sizeof(response) - 1);
    if (response_len > 0) {
        response[response_len] = '\0';
        ESP_LOGI(TAG, "hubitat lan action response status=%d payload=%s", status, response);
        if (post->response_handler == HUBITAT_LAN_RESPONSE_RESULT) {
            handle_mqtt_result_payload(response, (size_t)response_len);
        } else if (post->response_handler == HUBITAT_LAN_RESPONSE_STATE) {
            handle_mqtt_state_payload(response, (size_t)response_len);
            if (strstr(response, "\"items\"") != NULL) {
                char dashboard_id[DASHBOARD_TITLE_SIZE] = {};
                parse_json_string_field(response, "dashboard_id", dashboard_id, sizeof(dashboard_id));
                if (dashboard_id[0] == '\0') {
                    strlcpy(dashboard_id, "home", sizeof(dashboard_id));
                }
                handle_mqtt_dashboard_payload(dashboard_id, response, (size_t)response_len);
            }
        }
    } else {
        ESP_LOGI(TAG, "hubitat lan action accepted status=%d", status);
    }

    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    free(post);
}

static void post_hubitat_lan_action_request(const char *payload)
{
    if (!wifi_connected || active_config.hubitat_lan_endpoint[0] == '\0') {
        return;
    }

    hubitat_lan_post_t *post = (hubitat_lan_post_t *)calloc(1, sizeof(hubitat_lan_post_t));
    if (post == NULL) {
        ESP_LOGW(TAG, "hubitat lan action post allocation failed");
        return;
    }

    strlcpy(post->url, active_config.hubitat_lan_endpoint, sizeof(post->url));
    strlcpy(post->payload, payload, sizeof(post->payload));
    post->response_handler = HUBITAT_LAN_RESPONSE_RESULT;

    BaseType_t task_created = xTaskCreate(hubitat_lan_action_post_task, "hubitat_lan_post", 4096, post, 5, NULL);
    if (task_created != pdPASS) {
        ESP_LOGW(TAG, "hubitat lan action post task create failed");
        free(post);
    }
}

static bool build_status_payload(char *payload, size_t payload_size)
{
    const esp_app_desc_t *app_desc = esp_app_get_description();
    int payload_len = 0;
    if (system_status.has_delay_remaining) {
        payload_len = snprintf(payload,
                               payload_size,
                               "{\"device_id\":\"%s\",\"ready\":%s,\"armed_state\":\"%s\","
                               "\"delay_remaining\":%d,"
                               "\"wifi_connected\":%s,\"mqtt_connected\":%s,\"ip\":\"%s\","
                               "\"firmware\":\"%s\",\"topic_prefix\":\"%s\",\"last_request_id\":\"%s\"}",
                               active_config.device_id,
                               system_status.ready ? "true" : "false",
                               system_status.armed_state,
                               system_status.delay_remaining,
                               wifi_connected ? "true" : "false",
                               mqtt_connected ? "true" : "false",
                               wifi_connected ? wifi_ip_text : "none",
                               app_desc->version,
                               active_config.mqtt_topic_prefix,
                               last_action_request_id);
    } else {
        payload_len = snprintf(payload,
                               payload_size,
                               "{\"device_id\":\"%s\",\"ready\":%s,\"armed_state\":\"%s\","
                               "\"wifi_connected\":%s,\"mqtt_connected\":%s,\"ip\":\"%s\","
                               "\"firmware\":\"%s\",\"topic_prefix\":\"%s\",\"last_request_id\":\"%s\"}",
                               active_config.device_id,
                               system_status.ready ? "true" : "false",
                               system_status.armed_state,
                               wifi_connected ? "true" : "false",
                               mqtt_connected ? "true" : "false",
                               wifi_connected ? wifi_ip_text : "none",
                               app_desc->version,
                               active_config.mqtt_topic_prefix,
                               last_action_request_id);
    }

    return payload_len >= 0 && payload_len < (int)payload_size;
}

static hubitat_lan_post_t *build_hubitat_lan_status_post(void)
{
    if (!wifi_connected || active_config.hubitat_lan_endpoint[0] == '\0') {
        return NULL;
    }

    hubitat_lan_post_t *post = (hubitat_lan_post_t *)calloc(1, sizeof(hubitat_lan_post_t));
    if (post == NULL) {
        ESP_LOGW(TAG, "hubitat lan status post allocation failed");
        return NULL;
    }

    strlcpy(post->url, active_config.hubitat_lan_endpoint, sizeof(post->url));
    char *action_path = strstr(post->url, "/action");
    if (action_path == NULL) {
        ESP_LOGW(TAG, "hubitat lan status skipped; action URL has no /action path");
        free(post);
        return NULL;
    }
    memcpy(action_path, "/status", strlen("/status"));

    if (!build_status_payload(post->payload, sizeof(post->payload))) {
        ESP_LOGW(TAG, "hubitat lan status payload too large");
        free(post);
        return NULL;
    }
    post->response_handler = HUBITAT_LAN_RESPONSE_STATE;

    return post;
}

static void hubitat_lan_status_sync_task(void *arg)
{
    (void)arg;
    for (int attempt = 0; attempt < 3; attempt++) {
        if (attempt > 0) {
            vTaskDelay(pdMS_TO_TICKS(attempt == 1 ? 2000 : 6000));
        }
        hubitat_lan_post_t *post = build_hubitat_lan_status_post();
        if (post != NULL) {
            post_hubitat_lan_payload(post);
        }
    }
    vTaskDelete(NULL);
}

static void start_hubitat_lan_status_sync(void)
{
    if (!wifi_connected || active_config.hubitat_lan_endpoint[0] == '\0') {
        return;
    }

    BaseType_t task_created = xTaskCreate(hubitat_lan_status_sync_task,
                                          "hubitat_lan_sync",
                                          6144,
                                          NULL,
                                          5,
                                          NULL);
    if (task_created != pdPASS) {
        ESP_LOGW(TAG, "hubitat lan status sync task create failed");
    }
}

static void matter_and_lan_start_task(void *arg)
{
    (void)arg;

    esp_err_t err = matter_transport_start(active_config.device_id);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "matter transport start failed: %s", esp_err_to_name(err));
    } else {
        matter_transport_set_armed_state(system_status.armed_state, system_status.ready);
    }

    vTaskDelay(pdMS_TO_TICKS(1000));
    start_hubitat_lan_pairing();
    start_hubitat_lan_status_sync();

    vTaskDelete(NULL);
}

static void start_network_transports_when_ready(void)
{
    if (matter_transport_start_requested || !wifi_connected) {
        return;
    }

    matter_transport_start_requested = true;
    BaseType_t task_created = xTaskCreate(matter_and_lan_start_task,
                                          "matter_lan_start",
                                          4096,
                                          NULL,
                                          5,
                                          NULL);
    if (task_created != pdPASS) {
        matter_transport_start_requested = false;
        ESP_LOGW(TAG, "matter/lan start task create failed");
    }
}

static void generate_hubitat_pairing_code(char *buffer, size_t buffer_size)
{
    if (buffer_size < HUBITAT_PAIRING_CODE_SIZE) {
        return;
    }

    uint32_t value = esp_random() % 1000000;
    snprintf(buffer, buffer_size, "%06lu", (unsigned long)value);
}

static void ensure_hubitat_pairing_code(void)
{
    if (active_config.hubitat_lan_endpoint[0] != '\0' ||
        active_config.hubitat_pairing_code[0] != '\0') {
        return;
    }

    generate_hubitat_pairing_code(active_config.hubitat_pairing_code, sizeof(active_config.hubitat_pairing_code));
    esp_err_t err = save_config_to_nvs(&active_config);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "hubitat lan pairing code save failed: %s", esp_err_to_name(err));
    }
}

static esp_err_t normal_http_pair_status_handler(httpd_req_t *request)
{
    ensure_hubitat_pairing_code();

    const esp_app_desc_t *app_desc = esp_app_get_description();
    char payload[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"device_id\":\"%s\",\"ip\":\"%s\",\"firmware\":\"%s\","
                               "\"paired\":%s,\"pairing_code\":\"%s\",\"protocol\":1}",
                               active_config.device_id,
                               wifi_connected ? wifi_ip_text : "none",
                               app_desc->version,
                               active_config.hubitat_lan_endpoint[0] != '\0' ? "true" : "false",
                               active_config.hubitat_pairing_code);
    if (payload_len < 0 || payload_len >= (int)sizeof(payload)) {
        httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "status too large");
        return ESP_FAIL;
    }

    httpd_resp_set_type(request, "application/json");
    httpd_resp_send(request, payload, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
}

static esp_err_t send_pair_json_error(httpd_req_t *request, const char *status, const char *reason, const char *message)
{
    char payload[160] = {};
    int payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"ok\":false,\"reason\":\"%s\",\"message\":\"%s\"}",
                               reason,
                               message);
    if (payload_len < 0 || payload_len >= (int)sizeof(payload)) {
        httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "pair error too large");
        return ESP_FAIL;
    }

    httpd_resp_set_status(request, status);
    httpd_resp_set_type(request, "application/json");
    httpd_resp_send(request, payload, HTTPD_RESP_USE_STRLEN);
    return ESP_FAIL;
}

static esp_err_t normal_http_pair_handler(httpd_req_t *request)
{
    char body[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int total_read = 0;

    while (total_read < request->content_len && total_read < (int)sizeof(body) - 1) {
        int read_len = httpd_req_recv(request, body + total_read, sizeof(body) - 1 - total_read);
        if (read_len <= 0) {
            return send_pair_json_error(request,
                                        "500 Internal Server Error",
                                        "read_failed",
                                        "Failed to read request");
        }
        total_read += read_len;
    }
    body[total_read] = '\0';

    char device_id[DEVICE_ID_SIZE] = {};
    char pairing_code[HUBITAT_PAIRING_CODE_SIZE] = {};
    char action_url[HUBITAT_ENDPOINT_URL_SIZE] = {};
    parse_json_string_field(body, "device_id", device_id, sizeof(device_id));
    parse_json_string_field(body, "pairing_code", pairing_code, sizeof(pairing_code));
    parse_json_string_field(body, "action_url", action_url, sizeof(action_url));

    ensure_hubitat_pairing_code();
    if (device_id[0] != '\0' && strcmp(device_id, active_config.device_id) != 0) {
        ESP_LOGW(TAG, "hubitat lan pair rejected device_id=%s expected=%s", device_id, active_config.device_id);
        return send_pair_json_error(request, "403 Forbidden", "wrong_device", "Wrong device");
    }

    if (active_config.hubitat_lan_endpoint[0] != '\0' &&
        action_url[0] != '\0' &&
        strcmp(action_url, active_config.hubitat_lan_endpoint) == 0) {
        ESP_LOGI(TAG, "hubitat lan pair already paired endpoint=%s", active_config.hubitat_lan_endpoint);
        httpd_resp_set_type(request, "application/json");
        httpd_resp_sendstr(request, "{\"ok\":true,\"paired\":true}");
        return ESP_OK;
    }

    if (pairing_code[0] == '\0' ||
        strcmp(pairing_code, active_config.hubitat_pairing_code) != 0 ||
        action_url[0] == '\0') {
        ESP_LOGW(TAG,
                 "hubitat lan pair rejected code=%s expected=%s action_url=%s",
                 pairing_code[0] != '\0' ? pairing_code : "empty",
                 active_config.hubitat_pairing_code[0] != '\0' ? active_config.hubitat_pairing_code : "empty",
                 action_url[0] != '\0' ? "set" : "empty");
        return send_pair_json_error(request, "403 Forbidden", "invalid_pairing", "Invalid pairing");
    }

    strlcpy(active_config.hubitat_lan_endpoint, action_url, sizeof(active_config.hubitat_lan_endpoint));
    active_config.hubitat_pairing_code[0] = '\0';
    esp_err_t err = save_config_to_nvs(&active_config);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "hubitat lan pair save failed: %s", esp_err_to_name(err));
        return send_pair_json_error(request, "500 Internal Server Error", "save_failed", "Save failed");
    }

    ESP_LOGI(TAG, "hubitat lan paired endpoint=%s", active_config.hubitat_lan_endpoint);
    update_dashboard_ui();

    httpd_resp_set_type(request, "application/json");
    httpd_resp_sendstr(request, "{\"ok\":true}");
    return ESP_OK;
}

static esp_err_t read_json_request(httpd_req_t *request, char *body, size_t body_size)
{
    int total_read = 0;

    while (total_read < request->content_len && total_read < (int)body_size - 1) {
        int read_len = httpd_req_recv(request, body + total_read, body_size - 1 - total_read);
        if (read_len <= 0) {
            httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "failed to read request");
            return ESP_FAIL;
        }
        total_read += read_len;
    }
    body[total_read] = '\0';
    return ESP_OK;
}

static esp_err_t send_lan_json_ok(httpd_req_t *request)
{
    httpd_resp_set_type(request, "application/json");
    httpd_resp_sendstr(request, "{\"ok\":true}");
    return ESP_OK;
}

static esp_err_t normal_http_state_handler(httpd_req_t *request)
{
    char body[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    if (read_json_request(request, body, sizeof(body)) != ESP_OK) {
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "lan state payload=%s", body);
    handle_mqtt_state_payload(body, strlen(body));
    return send_lan_json_ok(request);
}

static esp_err_t normal_http_result_handler(httpd_req_t *request)
{
    char body[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    if (read_json_request(request, body, sizeof(body)) != ESP_OK) {
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "lan result payload=%s", body);
    handle_mqtt_result_payload(body, strlen(body));
    return send_lan_json_ok(request);
}

static esp_err_t normal_http_command_handler(httpd_req_t *request)
{
    char body[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    if (read_json_request(request, body, sizeof(body)) != ESP_OK) {
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "lan command payload=%s", body);
    handle_mqtt_command_payload(body, strlen(body));
    return send_lan_json_ok(request);
}

static esp_err_t normal_http_dashboard_handler(httpd_req_t *request)
{
    char body[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    if (read_json_request(request, body, sizeof(body)) != ESP_OK) {
        return ESP_FAIL;
    }

    char dashboard_id[DASHBOARD_TITLE_SIZE] = {};
    bool clear = false;
    parse_json_string_field(body, "dashboard_id", dashboard_id, sizeof(dashboard_id));
    if (dashboard_id[0] == '\0') {
        strlcpy(dashboard_id, "hsm", sizeof(dashboard_id));
    }

    parse_json_bool_field(body, "clear", &clear);
    ESP_LOGI(TAG, "lan dashboard id=%s clear=%s payload=%s", dashboard_id, clear ? "true" : "false", body);
    handle_mqtt_dashboard_payload(dashboard_id, clear ? "" : body, clear ? 0 : strlen(body));
    return send_lan_json_ok(request);
}

static void start_hubitat_lan_pairing(void)
{
    if (normal_http_server != NULL) {
        ensure_hubitat_pairing_code();
        return;
    }

    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = 80;
    config.stack_size = 8192;
    config.lru_purge_enable = true;

    esp_err_t err = httpd_start(&normal_http_server, &config);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "normal web server start failed: %s", esp_err_to_name(err));
        return;
    }

    httpd_uri_t pair_status_uri = {};
    pair_status_uri.uri = "/pair";
    pair_status_uri.method = HTTP_GET;
    pair_status_uri.handler = normal_http_pair_status_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(normal_http_server, &pair_status_uri));

    httpd_uri_t pair_uri = {};
    pair_uri.uri = "/pair";
    pair_uri.method = HTTP_POST;
    pair_uri.handler = normal_http_pair_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(normal_http_server, &pair_uri));

    httpd_uri_t state_uri = {};
    state_uri.uri = "/state";
    state_uri.method = HTTP_POST;
    state_uri.handler = normal_http_state_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(normal_http_server, &state_uri));

    httpd_uri_t result_uri = {};
    result_uri.uri = "/result";
    result_uri.method = HTTP_POST;
    result_uri.handler = normal_http_result_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(normal_http_server, &result_uri));

    httpd_uri_t command_uri = {};
    command_uri.uri = "/command";
    command_uri.method = HTTP_POST;
    command_uri.handler = normal_http_command_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(normal_http_server, &command_uri));

    httpd_uri_t dashboard_uri = {};
    dashboard_uri.uri = "/dashboard";
    dashboard_uri.method = HTTP_POST;
    dashboard_uri.handler = normal_http_dashboard_handler;
    ESP_ERROR_CHECK(httpd_register_uri_handler(normal_http_server, &dashboard_uri));

    ensure_hubitat_pairing_code();
    ESP_LOGI(TAG, "normal web server started pair_code=%s", active_config.hubitat_pairing_code);
    update_dashboard_ui();
}

static void mqtt_event_handler(void *handler_args, esp_event_base_t event_base, int32_t event_id, void *event_data)
{
    esp_mqtt_event_handle_t event = (esp_mqtt_event_handle_t)event_data;

    switch ((esp_mqtt_event_id_t)event_id) {
    case MQTT_EVENT_CONNECTED: {
        mqtt_connected = true;
        ESP_LOGI(TAG, "mqtt connected");
        update_system_status_ui();

        char state_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(state_topic, sizeof(state_topic), "state");
        int msg_id = esp_mqtt_client_subscribe(event->client, state_topic, 1);
        ESP_LOGI(TAG, "mqtt subscribe topic=%s msg_id=%d", state_topic, msg_id);

        char result_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(result_topic, sizeof(result_topic), "result");
        msg_id = esp_mqtt_client_subscribe(event->client, result_topic, 1);
        ESP_LOGI(TAG, "mqtt subscribe topic=%s msg_id=%d", result_topic, msg_id);

        char command_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(command_topic, sizeof(command_topic), "command");
        msg_id = esp_mqtt_client_subscribe(event->client, command_topic, 1);
        ESP_LOGI(TAG, "mqtt subscribe topic=%s msg_id=%d", command_topic, msg_id);

        char dashboard_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(dashboard_topic, sizeof(dashboard_topic), "dashboard/+");
        msg_id = esp_mqtt_client_subscribe(event->client, dashboard_topic, 1);
        ESP_LOGI(TAG, "mqtt subscribe topic=%s msg_id=%d", dashboard_topic, msg_id);

        publish_mqtt_availability("online");
        publish_mqtt_status();
        break;
    }
    case MQTT_EVENT_DISCONNECTED:
        mqtt_connected = false;
        set_system_offline();
        ESP_LOGI(TAG, "mqtt disconnected");
        break;
    case MQTT_EVENT_DATA: {
        ESP_LOGI(TAG,
                 "mqtt data topic=%.*s payload=%.*s",
                 event->topic_len,
                 event->topic,
                 event->data_len,
                 event->data);

        char state_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(state_topic, sizeof(state_topic), "state");
        bool is_state_topic = event->topic_len == (int)strlen(state_topic) &&
                              strncmp(event->topic, state_topic, event->topic_len) == 0;

        char result_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(result_topic, sizeof(result_topic), "result");
        bool is_result_topic = event->topic_len == (int)strlen(result_topic) &&
                               strncmp(event->topic, result_topic, event->topic_len) == 0;

        char command_topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(command_topic, sizeof(command_topic), "command");
        bool is_command_topic = event->topic_len == (int)strlen(command_topic) &&
                                strncmp(event->topic, command_topic, event->topic_len) == 0;

        char dashboard_topic_prefix[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(dashboard_topic_prefix, sizeof(dashboard_topic_prefix), "dashboard/");
        size_t dashboard_topic_prefix_len = strlen(dashboard_topic_prefix);
        bool is_dashboard_topic = event->topic_len > (int)dashboard_topic_prefix_len &&
                                  strncmp(event->topic, dashboard_topic_prefix, dashboard_topic_prefix_len) == 0;

        if (is_state_topic) {
            if (event->current_data_offset == 0 && event->data_len == event->total_data_len) {
                handle_mqtt_state_payload(event->data, event->data_len);
            } else {
                ESP_LOGW(TAG, "mqtt state ignored; fragmented payload");
            }
        } else if (is_result_topic) {
            if (event->current_data_offset == 0 && event->data_len == event->total_data_len) {
                handle_mqtt_result_payload(event->data, event->data_len);
            } else {
                ESP_LOGW(TAG, "mqtt result ignored; fragmented payload");
            }
        } else if (is_command_topic) {
            if (event->current_data_offset == 0 && event->data_len == event->total_data_len) {
                handle_mqtt_command_payload(event->data, event->data_len);
            } else {
                ESP_LOGW(TAG, "mqtt command ignored; fragmented payload");
            }
        } else if (is_dashboard_topic) {
            if (event->current_data_offset == 0 && event->data_len == event->total_data_len) {
                char dashboard_id[DASHBOARD_TITLE_SIZE] = {};
                copy_mqtt_string(dashboard_id,
                                 sizeof(dashboard_id),
                                 event->topic + dashboard_topic_prefix_len,
                                 (size_t)event->topic_len - dashboard_topic_prefix_len);
                handle_mqtt_dashboard_payload(dashboard_id, event->data, event->data_len);
            } else {
                ESP_LOGW(TAG, "mqtt dashboard ignored; fragmented payload");
            }
        }
        break;
    }
    case MQTT_EVENT_ERROR:
        mqtt_connected = false;
        set_system_offline();
        ESP_LOGW(TAG, "mqtt error");
        break;
    default:
        break;
    }
}

static void start_mqtt_client(void)
{
    if (mqtt_started) {
        return;
    }
    if (active_config.mqtt_broker_uri[0] == '\0') {
        ESP_LOGI(TAG, "mqtt client not started; broker not configured");
        return;
    }

    build_mqtt_topic(mqtt_availability_topic, sizeof(mqtt_availability_topic), "availability");

    esp_mqtt_client_config_t mqtt_config = {};
    mqtt_config.broker.address.uri = active_config.mqtt_broker_uri;
    mqtt_config.credentials.client_id = active_config.mqtt_client_id;
    if (strlen(active_config.mqtt_username) > 0) {
        mqtt_config.credentials.username = active_config.mqtt_username;
        mqtt_config.credentials.authentication.password = active_config.mqtt_password;
    }
    mqtt_config.session.last_will.topic = mqtt_availability_topic;
    mqtt_config.session.last_will.msg = "offline";
    mqtt_config.session.last_will.qos = 1;
    mqtt_config.session.last_will.retain = true;

    mqtt_client = esp_mqtt_client_init(&mqtt_config);
    if (mqtt_client == NULL) {
        ESP_LOGW(TAG, "mqtt client init failed");
        return;
    }

    ESP_ERROR_CHECK(esp_mqtt_client_register_event(mqtt_client,
                                                   (esp_mqtt_event_id_t)ESP_EVENT_ANY_ID,
                                                   mqtt_event_handler,
                                                   NULL));
    ESP_ERROR_CHECK(esp_mqtt_client_start(mqtt_client));
    mqtt_started = true;
    ESP_LOGI(TAG, "mqtt client started broker=%s", active_config.mqtt_broker_uri);
}

static void wifi_event_handler(void *handler_args, esp_event_base_t event_base, int32_t event_id, void *event_data)
{
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_START) {
        ESP_LOGI(TAG, "wifi station started");
        esp_wifi_connect();
        return;
    }

    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_SCAN_DONE) {
        wifi_scan_running = false;
        wifi_scan_completed = true;
        BaseType_t task_created = xTaskCreate(wifi_scan_log_task, "wifi_scan_log", 6144, NULL, 5, NULL);
        if (task_created != pdPASS) {
            ESP_LOGW(TAG, "wifi scan log task create failed; reconnecting");
            esp_wifi_connect();
        }
        return;
    }

    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        wifi_event_sta_disconnected_t *event = (wifi_event_sta_disconnected_t *)event_data;
        wifi_connected = false;
        strlcpy(wifi_ip_text, "none", sizeof(wifi_ip_text));
        mqtt_connected = false;
        set_system_offline();
        ESP_LOGI(TAG,
                 "wifi disconnected reason=%u (%s)",
                 (unsigned)event->reason,
                 wifi_disconnect_reason_name(event->reason));

        if (event->reason == WIFI_REASON_NO_AP_FOUND && !wifi_scan_completed) {
            start_wifi_scan();
            return;
        }

        ESP_LOGI(TAG, "wifi reconnecting");
        esp_wifi_connect();
        return;
    }

    if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)event_data;
        wifi_connected = true;
        snprintf(wifi_ip_text, sizeof(wifi_ip_text), IPSTR, IP2STR(&event->ip_info.ip));
        ESP_LOGI(TAG, "wifi got ip " IPSTR, IP2STR(&event->ip_info.ip));
        update_system_status_ui();
        start_mqtt_client();
        start_network_transports_when_ready();
    }
}

static void init_network_core(void)
{
    if (network_core_initialized) {
        return;
    }

    ESP_ERROR_CHECK(esp_netif_init());
    esp_err_t event_loop_err = esp_event_loop_create_default();
    if (event_loop_err != ESP_OK && event_loop_err != ESP_ERR_INVALID_STATE) {
        ESP_ERROR_CHECK(event_loop_err);
    }

    network_core_initialized = true;
}

static void init_wifi_driver(void)
{
    if (wifi_driver_initialized) {
        return;
    }

    wifi_init_config_t wifi_init_config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&wifi_init_config));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event_handler, NULL, NULL));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL, NULL));
    wifi_driver_initialized = true;
}

static void init_station_network(void)
{
    if (wifi_started) {
        return;
    }

    init_network_core();
    esp_netif_create_default_wifi_sta();
    init_wifi_driver();

    wifi_config_t wifi_config = {};
    strlcpy((char *)wifi_config.sta.ssid, active_config.wifi_ssid, sizeof(wifi_config.sta.ssid));
    strlcpy((char *)wifi_config.sta.password, active_config.wifi_password, sizeof(wifi_config.sta.password));
    wifi_config.sta.threshold.authmode = WIFI_AUTH_WPA_PSK;
    wifi_config.sta.sae_pwe_h2e = WPA3_SAE_PWE_BOTH;

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));
    ESP_ERROR_CHECK(esp_wifi_start());
    wifi_started = true;
    ESP_LOGI(TAG, "wifi connecting");
}

static void init_setup_ap_network(void)
{
    if (wifi_started) {
        return;
    }

    init_network_core();
    esp_netif_create_default_wifi_ap();
    init_wifi_driver();
    build_setup_ap_ssid();

    wifi_config_t wifi_config = {};
    strlcpy((char *)wifi_config.ap.ssid, setup_ap_ssid, sizeof(wifi_config.ap.ssid));
    wifi_config.ap.ssid_len = strlen(setup_ap_ssid);
    wifi_config.ap.channel = 1;
    wifi_config.ap.max_connection = 4;
    wifi_config.ap.authmode = WIFI_AUTH_OPEN;

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());
    wifi_started = true;
    wifi_connected = true;
    strlcpy(wifi_ip_text, "192.168.4.1", sizeof(wifi_ip_text));
    start_setup_web_server();
    ESP_LOGI(TAG, "setup ap started ssid=%s url=http://192.168.4.1", setup_ap_ssid);
}

static void init_nvs(void)
{
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);
}

static void init_i2c(void)
{
    i2c_master_bus_config_t i2c_config = {};
    i2c_config.clk_source = I2C_CLK_SRC_DEFAULT;
    i2c_config.i2c_port = (i2c_port_num_t)I2C_PORT_NUM;
    i2c_config.scl_io_num = PIN_I2C_SCL;
    i2c_config.sda_io_num = PIN_I2C_SDA;
    i2c_config.glitch_ignore_cnt = 7;
    i2c_config.flags.enable_internal_pullup = 1;

    ESP_ERROR_CHECK(i2c_new_master_bus(&i2c_config, &i2c_bus_handle));
}

static void detect_board_type(void)
{
    if (i2c_master_probe(i2c_bus_handle, ESP_LCD_TOUCH_IO_I2C_FT6336_ADDRESS, -1) == ESP_OK) {
        board_type = KEYPAD_BOARD_ST7796;
        ESP_LOGI(TAG, "board detected: original 3.5 ST7796 / FT6336");
        return;
    }

    if (i2c_master_probe(i2c_bus_handle, AXS15231B_I2C_ADDRESS, -1) == ESP_OK) {
        board_type = KEYPAD_BOARD_AXS15231B;
        ESP_LOGI(TAG, "board detected: 3.5B AXS15231B QSPI / touch");
        return;
    }

    board_type = KEYPAD_BOARD_ST7796;
    ESP_LOGW(TAG, "touch controller not detected; assuming original 3.5 ST7796 board");
}

static esp_err_t init_audio_i2s(void)
{
    i2s_chan_config_t chan_config = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);
    chan_config.auto_clear = true;

    ESP_RETURN_ON_ERROR(i2s_new_channel(&chan_config, &audio_tx_handle, NULL), TAG, "create i2s tx");

    i2s_std_config_t std_config = {};
    std_config.clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(AUDIO_SAMPLE_RATE);
    std_config.clk_cfg.mclk_multiple = AUDIO_MCLK_MULTIPLE;
    std_config.slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO);
    std_config.gpio_cfg.mclk = board_type == KEYPAD_BOARD_AXS15231B ? PIN_AXS_I2S_MCLK : PIN_I2S_MCLK;
    std_config.gpio_cfg.bclk = PIN_I2S_BCLK;
    std_config.gpio_cfg.ws = PIN_I2S_LRCK;
    std_config.gpio_cfg.dout = PIN_I2S_DOUT;
    std_config.gpio_cfg.din = PIN_I2S_DIN;

    ESP_RETURN_ON_ERROR(i2s_channel_init_std_mode(audio_tx_handle, &std_config), TAG, "init i2s tx");
    ESP_RETURN_ON_ERROR(i2s_channel_enable(audio_tx_handle), TAG, "enable i2s tx");
    return ESP_OK;
}

static esp_err_t init_audio_codec(void)
{
    audio_codec_i2s_cfg_t i2s_config = {};
    i2s_config.tx_handle = audio_tx_handle;
    const audio_codec_data_if_t *data_if = audio_codec_new_i2s_data(&i2s_config);
    ESP_RETURN_ON_FALSE(data_if != NULL, ESP_FAIL, TAG, "create codec i2s data interface");

    audio_codec_i2c_cfg_t i2c_config = {};
    i2c_config.addr = ES8311_CODEC_DEFAULT_ADDR;
    i2c_config.bus_handle = i2c_bus_handle;
    const audio_codec_ctrl_if_t *ctrl_if = audio_codec_new_i2c_ctrl(&i2c_config);
    ESP_RETURN_ON_FALSE(ctrl_if != NULL, ESP_FAIL, TAG, "create codec i2c control interface");

    const audio_codec_gpio_if_t *gpio_if = audio_codec_new_gpio();
    ESP_RETURN_ON_FALSE(gpio_if != NULL, ESP_FAIL, TAG, "create codec gpio interface");

    es8311_codec_cfg_t es8311_config = {};
    es8311_config.ctrl_if = ctrl_if;
    es8311_config.gpio_if = gpio_if;
    es8311_config.codec_mode = ESP_CODEC_DEV_WORK_MODE_DAC;
    es8311_config.master_mode = false;
    es8311_config.use_mclk = true;
    es8311_config.pa_pin = GPIO_NUM_NC;
    es8311_config.hw_gain.pa_voltage = 5.0;
    es8311_config.hw_gain.codec_dac_voltage = 3.3;
    es8311_config.mclk_div = AUDIO_MCLK_MULTIPLE;
    const audio_codec_if_t *codec_if = es8311_codec_new(&es8311_config);
    ESP_RETURN_ON_FALSE(codec_if != NULL, ESP_FAIL, TAG, "create es8311 codec");

    esp_codec_dev_cfg_t dev_config = {};
    dev_config.dev_type = ESP_CODEC_DEV_TYPE_OUT;
    dev_config.codec_if = codec_if;
    dev_config.data_if = data_if;
    audio_output_dev = esp_codec_dev_new(&dev_config);
    ESP_RETURN_ON_FALSE(audio_output_dev != NULL, ESP_FAIL, TAG, "create codec output device");

    esp_codec_set_disable_when_closed(audio_output_dev, false);

    esp_codec_dev_sample_info_t sample_info = {};
    sample_info.sample_rate = AUDIO_SAMPLE_RATE;
    sample_info.channel = 1;
    sample_info.channel_mask = 0;
    sample_info.bits_per_sample = 16;
    sample_info.mclk_multiple = AUDIO_MCLK_MULTIPLE;
    ESP_RETURN_ON_FALSE(esp_codec_dev_open(audio_output_dev, &sample_info) == ESP_CODEC_DEV_OK,
                        ESP_FAIL,
                        TAG,
                        "open codec output");
    ESP_RETURN_ON_FALSE(esp_codec_dev_set_out_vol(audio_output_dev, 90.0) == ESP_CODEC_DEV_OK,
                        ESP_FAIL,
                        TAG,
                        "set codec volume");
    return ESP_OK;
}

static void fill_tone_samples(int16_t *buffer, size_t sample_count, uint32_t step, int16_t amplitude)
{
    static const int16_t waveform[] = {
        0, 6270, 11585, 15136, 16384, 15136, 11585, 6270,
        0, -6270, -11585, -15136, -16384, -15136, -11585, -6270,
    };

    uint32_t phase = 0;
    const size_t waveform_len = sizeof(waveform) / sizeof(waveform[0]);
    for (size_t i = 0; i < sample_count; ++i) {
        int32_t envelope = (int32_t)(sample_count - i);
        int32_t sample = (waveform[(phase >> 16) % waveform_len] * amplitude) / 16384;
        buffer[i] = (int16_t)((sample * envelope) / sample_count);
        phase += step;
    }
}

static void fill_alarm_chirp_samples(void)
{
    static const int16_t waveform[] = {
        0, 6270, 11585, 15136, 16384, 15136, 11585, 6270,
        0, -6270, -11585, -15136, -16384, -15136, -11585, -6270,
    };

    uint32_t phase = 0;
    const size_t waveform_len = sizeof(waveform) / sizeof(waveform[0]);
    for (size_t i = 0; i < AUDIO_ALARM_CHIRP_SAMPLES; ++i) {
        uint32_t step = (i / (AUDIO_SAMPLE_RATE / 12)) % 2 == 0 ? (2u << 16) : (3u << 16);
        int32_t envelope = (int32_t)(AUDIO_ALARM_CHIRP_SAMPLES - i);
        int32_t sample = (waveform[(phase >> 16) % waveform_len] * 15000) / 16384;
        audio_alarm_chirp_samples[i] = (int16_t)((sample * envelope) / AUDIO_ALARM_CHIRP_SAMPLES);
        phase += step;
    }
}

static void write_audio_signal(audio_signal_t signal)
{
    if (audio_output_dev == NULL) {
        return;
    }

    int16_t *samples = audio_click_samples;
    size_t sample_count = AUDIO_CLICK_SAMPLES;
    const char *name = "click";
    if (signal == AUDIO_SIGNAL_EXIT_CHIRP) {
        samples = audio_exit_chirp_samples;
        sample_count = AUDIO_EXIT_CHIRP_SAMPLES;
        name = "exit chirp";
    } else if (signal == AUDIO_SIGNAL_ENTRY_CHIRP) {
        samples = audio_entry_chirp_samples;
        sample_count = AUDIO_ENTRY_CHIRP_SAMPLES;
        name = "entry chirp";
    } else if (signal == AUDIO_SIGNAL_ALARM_CHIRP) {
        samples = audio_alarm_chirp_samples;
        sample_count = AUDIO_ALARM_CHIRP_SAMPLES;
        name = "alarm chirp";
    }

    int ret = esp_codec_dev_write(audio_output_dev, samples, sample_count * sizeof(samples[0]));
    if (ret != ESP_CODEC_DEV_OK) {
        ESP_LOGW(TAG, "audio %s codec write failed ret=%d", name, ret);
    }
}

static void audio_click_task(void *arg)
{
    fill_tone_samples(audio_click_samples, AUDIO_CLICK_SAMPLES, 1u << 16, 16384);
    fill_tone_samples(audio_exit_chirp_samples, AUDIO_EXIT_CHIRP_SAMPLES, 1u << 16, 9000);
    fill_tone_samples(audio_entry_chirp_samples, AUDIO_ENTRY_CHIRP_SAMPLES, 2u << 16, 11000);
    fill_alarm_chirp_samples();

    uint8_t token = 0;
    while (true) {
        if (xQueueReceive(audio_click_queue, &token, portMAX_DELAY) == pdTRUE) {
            write_audio_signal((audio_signal_t)token);
        }
    }
}

static void init_audio(void)
{
    esp_err_t ret = init_audio_i2s();
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "audio disabled; i2s init failed: %s", esp_err_to_name(ret));
        return;
    }

    ret = init_audio_codec();
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "audio disabled; es8311 init failed: %s", esp_err_to_name(ret));
        return;
    }

    audio_click_queue = xQueueCreate(AUDIO_CLICK_QUEUE_LENGTH, sizeof(uint8_t));
    if (audio_click_queue == NULL) {
        ESP_LOGW(TAG, "audio disabled; click queue allocation failed");
        return;
    }

    BaseType_t task_created = xTaskCreate(audio_click_task, "audio_click", 4096, NULL, 5, NULL);
    if (task_created != pdPASS) {
        ESP_LOGW(TAG, "audio disabled; click task allocation failed");
        return;
    }

    audio_ready = true;
    ESP_LOGI(TAG, "audio click initialized");
}

static void play_key_click(void)
{
    play_audio_signal(AUDIO_SIGNAL_KEY_CLICK);
}

static void play_audio_signal(audio_signal_t signal)
{
    if (!audio_ready || audio_click_queue == NULL) {
        return;
    }

    uint8_t token = (uint8_t)signal;
    if (xQueueSend(audio_click_queue, &token, 0) != pdTRUE) {
        ESP_LOGW(TAG, "audio click queue full");
    }
}

static void reset_lcd_via_expander(void)
{
    ESP_ERROR_CHECK(esp_io_expander_new_i2c_tca9554(
        i2c_bus_handle,
        ESP_IO_EXPANDER_I2C_TCA9554_ADDRESS_000,
        &expander_handle));

    ESP_ERROR_CHECK(esp_io_expander_set_dir(expander_handle, IO_EXPANDER_PIN_NUM_1, IO_EXPANDER_OUTPUT));
    ESP_ERROR_CHECK(esp_io_expander_set_level(expander_handle, IO_EXPANDER_PIN_NUM_1, 0));
    vTaskDelay(pdMS_TO_TICKS(100));
    ESP_ERROR_CHECK(esp_io_expander_set_level(expander_handle, IO_EXPANDER_PIN_NUM_1, 1));
    vTaskDelay(pdMS_TO_TICKS(100));
}

static void init_backlight(void)
{
    ledc_timer_config_t timer = {};
    timer.speed_mode = LCD_BL_LEDC_MODE;
    timer.timer_num = LCD_BL_LEDC_TIMER;
    timer.duty_resolution = LCD_BL_LEDC_DUTY_RES;
    timer.freq_hz = LCD_BL_LEDC_FREQUENCY;
    timer.clk_cfg = LEDC_AUTO_CLK;
    ESP_ERROR_CHECK(ledc_timer_config(&timer));

    ledc_channel_config_t channel = {};
    channel.speed_mode = LCD_BL_LEDC_MODE;
    channel.channel = LCD_BL_LEDC_CHANNEL;
    channel.timer_sel = LCD_BL_LEDC_TIMER;
    channel.gpio_num = PIN_LCD_BL;
    channel.duty = 0;
    channel.hpoint = 0;
    ESP_ERROR_CHECK(ledc_channel_config(&channel));
}

static void set_backlight(uint8_t brightness)
{
    if (brightness > 100) {
        brightness = 100;
    }
    uint32_t duty = (brightness * (LCD_BL_LEDC_DUTY_MAX - 1)) / 100;
    ESP_ERROR_CHECK(ledc_set_duty(LCD_BL_LEDC_MODE, LCD_BL_LEDC_CHANNEL, duty));
    ESP_ERROR_CHECK(ledc_update_duty(LCD_BL_LEDC_MODE, LCD_BL_LEDC_CHANNEL));
}

static void reset_screen_dim_timer(void)
{
    if (screen_dim_timer == NULL) {
        return;
    }

    lv_timer_reset(screen_dim_timer);
    lv_timer_resume(screen_dim_timer);
}

static void wake_screen(void)
{
    if (screen_dimmed) {
        screen_dimmed = false;
        set_backlight(BACKLIGHT_ACTIVE_BRIGHTNESS);
        ESP_LOGI(TAG, "screen woke");
    }

    reset_screen_dim_timer();
}

static bool wake_screen_for_touch_only(void)
{
    if (!screen_dimmed) {
        reset_screen_dim_timer();
        return false;
    }

    wake_screen();
    return true;
}

static void wake_area_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_PRESSED) {
        return;
    }

    if (!screen_dimmed) {
        reset_screen_dim_timer();
        return;
    }

    wake_screen();
    lv_indev_t *indev = lv_event_get_indev(event);
    if (indev != NULL) {
        lv_indev_wait_release(indev);
    }
}

static void screen_dim_timeout_cb(lv_timer_t *timer)
{
    if (screen_dimmed) {
        return;
    }

    screen_dimmed = true;
    set_backlight(BACKLIGHT_DIM_BRIGHTNESS);
    ESP_LOGI(TAG, "screen dimmed");
}

static void init_screen_dim_timer(void)
{
    if (!lvgl_port_lock(0)) {
        return;
    }

    screen_dim_timer = lv_timer_create(screen_dim_timeout_cb, SCREEN_DIM_TIMEOUT_MS, NULL);
    reset_screen_dim_timer();
    lvgl_port_unlock();
}

static void init_lcd(void)
{
    if (board_type == KEYPAD_BOARD_AXS15231B) {
        ESP_LOGI(TAG, "initializing 3.5B AXS15231B QSPI display");
        keypad_init_axs15231b_display(&lcd_io_handle, &lcd_panel_handle, LCD_BUFFER_SIZE);
        return;
    }

    ESP_LOGI(TAG, "initializing original 3.5 ST7796 SPI display");
    spi_bus_config_t bus_config = {};
    bus_config.sclk_io_num = PIN_LCD_SCLK;
    bus_config.mosi_io_num = PIN_LCD_MOSI;
    bus_config.miso_io_num = PIN_LCD_MISO;
    bus_config.quadwp_io_num = -1;
    bus_config.quadhd_io_num = -1;
    bus_config.max_transfer_sz = LCD_DMA_TRANS_SIZE * sizeof(lv_color_t);
    ESP_ERROR_CHECK(spi_bus_initialize(LCD_SPI_HOST, &bus_config, SPI_DMA_CH_AUTO));

    esp_lcd_panel_io_spi_config_t io_config = {};
    io_config.cs_gpio_num = PIN_LCD_CS;
    io_config.dc_gpio_num = PIN_LCD_DC;
    io_config.spi_mode = 0;
    io_config.pclk_hz = LCD_PIXEL_CLOCK_HZ;
    io_config.trans_queue_depth = 3;
    io_config.lcd_cmd_bits = 8;
    io_config.lcd_param_bits = 8;
    ESP_ERROR_CHECK(esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)LCD_SPI_HOST, &io_config, &lcd_io_handle));

    // Waveshare's ST7796 example restarts once after an external reset so the panel starts from a known state.
    if (esp_reset_reason() != ESP_RST_SW) {
        ESP_LOGI(TAG, "restarting once to stabilize LCD after reset");
        esp_restart();
    }

    esp_lcd_panel_dev_config_t panel_config = {};
    panel_config.reset_gpio_num = PIN_LCD_RST;
    panel_config.rgb_ele_order = LCD_RGB_ELEMENT_ORDER_BGR;
    panel_config.bits_per_pixel = 16;

    ESP_ERROR_CHECK(esp_lcd_new_panel_st7796(lcd_io_handle, &panel_config, &lcd_panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_reset(lcd_panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_init(lcd_panel_handle));
    ESP_ERROR_CHECK(esp_lcd_panel_invert_color(lcd_panel_handle, true));
    ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(lcd_panel_handle, true));
}

static void init_touch(void)
{
    esp_lcd_panel_io_handle_t touch_io_handle = NULL;
    esp_lcd_panel_io_i2c_config_t touch_io_config = {};
    if (board_type == KEYPAD_BOARD_AXS15231B) {
        touch_io_config.dev_addr = AXS15231B_I2C_ADDRESS;
        touch_io_config.control_phase_bytes = 1;
        touch_io_config.dc_bit_offset = 0;
        touch_io_config.lcd_cmd_bits = 8;
        touch_io_config.flags.disable_control_phase = 1;
        touch_io_config.scl_speed_hz = 400 * 1000;
    } else {
        touch_io_config.dev_addr = ESP_LCD_TOUCH_IO_I2C_FT6336_ADDRESS;
        touch_io_config.control_phase_bytes = 1;
        touch_io_config.lcd_cmd_bits = 8;
        touch_io_config.flags.disable_control_phase = 1;
        touch_io_config.scl_speed_hz = 400 * 1000;
    }
    ESP_ERROR_CHECK(esp_lcd_new_panel_io_i2c(i2c_bus_handle, &touch_io_config, &touch_io_handle));

    esp_lcd_touch_config_t touch_config = {};
    touch_config.x_max = LCD_H_RES;
    touch_config.y_max = LCD_V_RES;
    touch_config.rst_gpio_num = PIN_TP_RST;
    touch_config.int_gpio_num = PIN_TP_INT;
    touch_config.flags.swap_xy = 0;
    touch_config.flags.mirror_x = 0;
    touch_config.flags.mirror_y = 0;

    if (board_type == KEYPAD_BOARD_AXS15231B) {
        ESP_ERROR_CHECK(esp_lcd_touch_new_i2c_axs15231b(touch_io_handle, &touch_config, &touch_handle));
    } else {
        ESP_ERROR_CHECK(esp_lcd_touch_new_i2c_ft6336(touch_io_handle, &touch_config, &touch_handle));
    }
}

static void init_lvgl(void)
{
    lvgl_port_cfg_t port_config = ESP_LVGL_PORT_INIT_CONFIG();
    ESP_ERROR_CHECK(lvgl_port_init(&port_config));

    lvgl_port_display_cfg_t display_config = {};
    display_config.io_handle = lcd_io_handle;
    display_config.panel_handle = lcd_panel_handle;
    display_config.buffer_size = LCD_BUFFER_SIZE;
    display_config.double_buffer = true;
    if (board_type == KEYPAD_BOARD_ST7796) {
        display_config.trans_size = LCD_DMA_TRANS_SIZE;
    }
    display_config.hres = LCD_H_RES;
    display_config.vres = LCD_V_RES;
    display_config.rotation.swap_xy = 0;
    display_config.rotation.mirror_x = board_type == KEYPAD_BOARD_ST7796;
    display_config.rotation.mirror_y = 0;
    display_config.flags.buff_spiram = 1;
    display_config.flags.sw_rotate = 1;

    display = lvgl_port_add_disp(&display_config);

    const lvgl_port_touch_cfg_t touch_config = {
        .disp = display,
        .handle = touch_handle,
        .scale = {
            .x = 1.0f,
            .y = 1.0f,
        },
    };
    lvgl_port_add_touch(&touch_config);
}

static void init_keypad_styles(void)
{
    lv_style_init(&style_screen);
    lv_style_set_bg_color(&style_screen, lv_color_hex(0x111318));

    lv_style_init(&style_code);
    lv_style_set_bg_color(&style_code, lv_color_hex(0x1b2028));
    lv_style_set_border_color(&style_code, lv_color_hex(0x344051));
    lv_style_set_border_width(&style_code, 1);
    lv_style_set_radius(&style_code, 8);
    lv_style_set_pad_all(&style_code, 12);

    lv_style_init(&style_dimmer_track);
    lv_style_set_bg_opa(&style_dimmer_track, LV_OPA_COVER);
    lv_style_set_bg_color(&style_dimmer_track, lv_color_hex(0x263142));
    lv_style_set_border_width(&style_dimmer_track, 1);
    lv_style_set_border_color(&style_dimmer_track, lv_color_hex(0x4b596c));
    lv_style_set_radius(&style_dimmer_track, 8);
    lv_style_set_pad_all(&style_dimmer_track, 0);

    lv_style_init(&style_dimmer_fill);
    lv_style_set_bg_opa(&style_dimmer_fill, LV_OPA_COVER);
    lv_style_set_bg_color(&style_dimmer_fill, lv_color_hex(0x38d26f));
    lv_style_set_radius(&style_dimmer_fill, 8);

    lv_style_init(&style_dimmer_knob);
    lv_style_set_bg_opa(&style_dimmer_knob, LV_OPA_TRANSP);
    lv_style_set_border_width(&style_dimmer_knob, 0);
    lv_style_set_pad_all(&style_dimmer_knob, 0);

    lv_style_init(&style_key);
    lv_style_set_bg_opa(&style_key, LV_OPA_COVER);
    lv_style_set_bg_color(&style_key, lv_color_hex(0xf3f5f7));
    lv_style_set_text_color(&style_key, lv_color_hex(0x111318));
    lv_style_set_text_opa(&style_key, LV_OPA_COVER);
    lv_style_set_border_opa(&style_key, LV_OPA_COVER);
    lv_style_set_border_color(&style_key, lv_color_hex(0xb8c0ca));
    lv_style_set_border_width(&style_key, 1);
    lv_style_set_radius(&style_key, 8);
    lv_style_set_shadow_width(&style_key, 0);

    lv_style_init(&style_key_pressed);
    lv_style_set_bg_opa(&style_key_pressed, LV_OPA_COVER);
    lv_style_set_bg_color(&style_key_pressed, lv_color_hex(0xbfd8ff));
    lv_style_set_border_opa(&style_key_pressed, LV_OPA_COVER);
    lv_style_set_border_color(&style_key_pressed, lv_color_hex(0x5f93dc));

    lv_style_init(&style_key_action);
    lv_style_set_bg_opa(&style_key_action, LV_OPA_COVER);
    lv_style_set_bg_color(&style_key_action, lv_color_hex(0xdfe6ee));
    lv_style_set_text_color(&style_key_action, lv_color_hex(0x18212c));
    lv_style_set_text_opa(&style_key_action, LV_OPA_COVER);

    lv_style_init(&style_key_enter);
    lv_style_set_bg_opa(&style_key_enter, LV_OPA_COVER);
    lv_style_set_bg_color(&style_key_enter, lv_color_hex(0x38d26f));
    lv_style_set_text_color(&style_key_enter, lv_color_hex(0x07100a));
    lv_style_set_text_opa(&style_key_enter, LV_OPA_COVER);
    lv_style_set_border_opa(&style_key_enter, LV_OPA_COVER);
    lv_style_set_border_color(&style_key_enter, lv_color_hex(0x2ab35c));

    lv_style_init(&style_key_arm);
    lv_style_set_bg_opa(&style_key_arm, LV_OPA_COVER);
    lv_style_set_bg_color(&style_key_arm, lv_color_hex(0xdc4d4d));
    lv_style_set_text_color(&style_key_arm, lv_color_hex(0xffffff));
    lv_style_set_text_opa(&style_key_arm, LV_OPA_COVER);
    lv_style_set_border_opa(&style_key_arm, LV_OPA_COVER);
    lv_style_set_border_color(&style_key_arm, lv_color_hex(0xb73232));

    lv_style_init(&style_key_partial);
    lv_style_set_bg_opa(&style_key_partial, LV_OPA_COVER);
    lv_style_set_bg_color(&style_key_partial, lv_color_hex(0xf0b247));
    lv_style_set_text_color(&style_key_partial, lv_color_hex(0x15100a));
    lv_style_set_text_opa(&style_key_partial, LV_OPA_COVER);
    lv_style_set_border_opa(&style_key_partial, LV_OPA_COVER);
    lv_style_set_border_color(&style_key_partial, lv_color_hex(0xc88a26));

    lv_style_init(&style_menu_button);
    lv_style_set_bg_opa(&style_menu_button, LV_OPA_COVER);
    lv_style_set_bg_color(&style_menu_button, lv_color_hex(0x202733));
    lv_style_set_border_opa(&style_menu_button, LV_OPA_COVER);
    lv_style_set_border_color(&style_menu_button, lv_color_hex(0x465365));
    lv_style_set_border_width(&style_menu_button, 1);
    lv_style_set_shadow_width(&style_menu_button, 0);
    lv_style_set_text_color(&style_menu_button, lv_color_hex(0xd7e0ec));
    lv_style_set_text_opa(&style_menu_button, LV_OPA_COVER);
    lv_style_set_radius(&style_menu_button, 8);

    lv_style_init(&style_menu_bar);
    lv_style_set_bg_opa(&style_menu_bar, LV_OPA_COVER);
    lv_style_set_bg_color(&style_menu_bar, lv_color_hex(0xd7e0ec));
    lv_style_set_border_width(&style_menu_bar, 0);
    lv_style_set_radius(&style_menu_bar, 2);

    lv_style_init(&style_menu_panel);
    lv_style_set_bg_opa(&style_menu_panel, LV_OPA_COVER);
    lv_style_set_bg_color(&style_menu_panel, lv_color_hex(0x202733));
    lv_style_set_border_opa(&style_menu_panel, LV_OPA_COVER);
    lv_style_set_border_color(&style_menu_panel, lv_color_hex(0x465365));
    lv_style_set_border_width(&style_menu_panel, 1);
    lv_style_set_radius(&style_menu_panel, 8);
    lv_style_set_pad_all(&style_menu_panel, 8);

    lv_style_init(&style_menu_item);
    lv_style_set_bg_opa(&style_menu_item, LV_OPA_COVER);
    lv_style_set_bg_color(&style_menu_item, lv_color_hex(0x2b3442));
    lv_style_set_border_opa(&style_menu_item, LV_OPA_COVER);
    lv_style_set_border_color(&style_menu_item, lv_color_hex(0x465365));
    lv_style_set_border_width(&style_menu_item, 1);
    lv_style_set_shadow_width(&style_menu_item, 0);
    lv_style_set_radius(&style_menu_item, 6);
    lv_style_set_text_color(&style_menu_item, lv_color_hex(0xe6edf5));
    lv_style_set_text_opa(&style_menu_item, LV_OPA_COVER);

    lv_style_init(&style_menu_item_pressed);
    lv_style_set_bg_opa(&style_menu_item_pressed, LV_OPA_COVER);
    lv_style_set_bg_color(&style_menu_item_pressed, lv_color_hex(0x344051));
}

static void update_code_label(void)
{
    char masked[MAX_CREDENTIAL_DIGITS + 1] = {};
    for (size_t i = 0; i < credential_screen.digit_count; ++i) {
        masked[i] = '*';
    }

    if (credential_screen.digit_count == 0) {
        lv_label_set_text(credential_screen.code_label, "Enter code");
    } else {
        lv_label_set_text(credential_screen.code_label, masked);
    }
    lv_obj_invalidate(lv_scr_act());
}

static void clear_entered_credential(void)
{
    credential_screen.digit_count = 0;
    credential_screen.digits[0] = '\0';
    update_code_label();
}

static void publish_driver_action_request(const char *action, const char *credential, size_t credential_length)
{
    const uint32_t request_sequence = ++action_request_sequence;
    char request_id[DEVICE_ID_SIZE + 12] = {};
    snprintf(request_id, sizeof(request_id), "%s-%06lu", active_config.device_id, (unsigned long)request_sequence);
    strlcpy(last_action_request_id, request_id, sizeof(last_action_request_id));
    const int64_t uptime_ms = esp_timer_get_time() / 1000;

    char payload[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"device_id\":\"%s\",\"request_id\":\"%s\",\"uptime_ms\":%lld,"
                               "\"action\":\"%s\",\"credential\":\"%s\",\"credential_length\":%u}",
                               active_config.device_id,
                               request_id,
                               (long long)uptime_ms,
                               action,
                               credential,
                               (unsigned)credential_length);
    if (payload_len < 0 || payload_len >= (int)sizeof(payload)) {
        ESP_LOGW(TAG, "mqtt action payload too large action=%s", action);
        return;
    }

    if (transport_sends_mqtt_actions() && mqtt_client != NULL && mqtt_connected) {
        char topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(topic, sizeof(topic), "action");
        int msg_id = esp_mqtt_client_publish(mqtt_client, topic, payload, 0, 1, 0);
        ESP_LOGI(TAG, "mqtt action published topic=%s msg_id=%d", topic, msg_id);
    } else if (!transport_sends_mqtt_actions()) {
        ESP_LOGI(TAG, "mqtt action skipped by transport mode action=%s", action);
    } else {
        ESP_LOGI(TAG, "mqtt action skipped offline action=%s", action);
    }

    if (transport_sends_lan_actions()) {
        post_hubitat_lan_action_request(payload);
    }
}

static void publish_dashboard_action_payload(const char *payload, const char *action)
{
    if (transport_sends_mqtt_actions() && mqtt_client != NULL && mqtt_connected) {
        char topic[MQTT_TOPIC_BUFFER_SIZE] = {};
        build_mqtt_topic(topic, sizeof(topic), "action");
        int msg_id = esp_mqtt_client_publish(mqtt_client, topic, payload, 0, 1, 0);
        ESP_LOGI(TAG, "mqtt dashboard action published action=%s topic=%s msg_id=%d", action, topic, msg_id);
    } else if (!transport_sends_mqtt_actions()) {
        ESP_LOGI(TAG, "mqtt dashboard action skipped by transport mode action=%s", action);
    } else {
        ESP_LOGI(TAG, "mqtt dashboard action skipped offline action=%s", action);
    }

    if (transport_sends_lan_actions()) {
        post_hubitat_lan_action_request(payload);
    }
}

static void publish_dashboard_tap_request(const dashboard_item_t *item, const char *zone)
{
    if (item == NULL || item->id[0] == '\0') {
        return;
    }

    const uint32_t request_sequence = ++action_request_sequence;
    char request_id[DEVICE_ID_SIZE + 12] = {};
    snprintf(request_id, sizeof(request_id), "%s-%06lu", active_config.device_id, (unsigned long)request_sequence);
    strlcpy(last_action_request_id, request_id, sizeof(last_action_request_id));
    const int64_t uptime_ms = esp_timer_get_time() / 1000;

    char payload[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"device_id\":\"%s\",\"request_id\":\"%s\",\"uptime_ms\":%lld,"
                               "\"action\":\"dashboard_tap\",\"dashboard_id\":\"controls\","
                               "\"tile_id\":\"%s\",\"zone\":\"%s\"}",
                               active_config.device_id,
                               request_id,
                               (long long)uptime_ms,
                               item->id,
                               zone);
    if (payload_len < 0 || payload_len >= (int)sizeof(payload)) {
        ESP_LOGW(TAG, "dashboard tap payload too large tile=%s", item->id);
        return;
    }

    publish_dashboard_action_payload(payload, "dashboard_tap");
}

static void publish_dashboard_level_request(const dashboard_item_t *item, int level)
{
    if (item == NULL || item->id[0] == '\0') {
        return;
    }

    const uint32_t request_sequence = ++action_request_sequence;
    char request_id[DEVICE_ID_SIZE + 12] = {};
    snprintf(request_id, sizeof(request_id), "%s-%06lu", active_config.device_id, (unsigned long)request_sequence);
    strlcpy(last_action_request_id, request_id, sizeof(last_action_request_id));
    const int64_t uptime_ms = esp_timer_get_time() / 1000;

    if (level < 0) {
        level = 0;
    } else if (level > 100) {
        level = 100;
    }

    char payload[MQTT_PAYLOAD_BUFFER_SIZE] = {};
    int payload_len = snprintf(payload,
                               sizeof(payload),
                               "{\"device_id\":\"%s\",\"request_id\":\"%s\",\"uptime_ms\":%lld,"
                               "\"action\":\"dashboard_control\",\"dashboard_id\":\"controls\","
                               "\"tile_id\":\"%s\",\"control_id\":\"level\",\"event\":\"release\",\"value\":%d}",
                               active_config.device_id,
                               request_id,
                               (long long)uptime_ms,
                               item->id,
                               level);
    if (payload_len < 0 || payload_len >= (int)sizeof(payload)) {
        ESP_LOGW(TAG, "dashboard level payload too large tile=%s", item->id);
        return;
    }

    publish_dashboard_action_payload(payload, "dashboard_control");
}

static void emit_driver_action_request(const char *action)
{
    ESP_LOGI(TAG,
             "driver request action=%s credential_length=%u credential=%s",
             action,
             (unsigned)credential_screen.digit_count,
             credential_screen.digits);
    publish_driver_action_request(action, credential_screen.digits, credential_screen.digit_count);
    clear_entered_credential();
}

static void handle_keypad_key(const keypad_key_t *key)
{
    play_key_click();

    if (key->action == KEY_ACTION_DIGIT) {
        if (credential_screen.digit_count < MAX_CREDENTIAL_DIGITS) {
            credential_screen.digits[credential_screen.digit_count++] = key->digit;
            credential_screen.digits[credential_screen.digit_count] = '\0';
            ESP_LOGI(TAG, "digit key=%c digits_entered=%u", key->digit, (unsigned)credential_screen.digit_count);
        } else {
            ESP_LOGW(TAG, "digit key=%c ignored; credential buffer full", key->digit);
        }
        update_code_label();
        return;
    }

    if (key->action == KEY_ACTION_BACKSPACE) {
        if (credential_screen.digit_count > 0) {
            credential_screen.digits[--credential_screen.digit_count] = '\0';
        }
        ESP_LOGI(TAG, "backspace digits_entered=%u", (unsigned)credential_screen.digit_count);
        update_code_label();
        return;
    }

    if (key->action == KEY_ACTION_ENTER) {
        emit_driver_action_request("disarm");
        return;
    }

    if (key->action == KEY_ACTION_ARM_AWAY) {
        emit_driver_action_request("arm_away");
        return;
    }

    if (key->action == KEY_ACTION_ARM_STAY) {
        emit_driver_action_request("arm_stay");
        return;
    }
}

static void keypad_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    const keypad_key_t *key = (const keypad_key_t *)lv_event_get_user_data(event);
    handle_keypad_key(key);
}

static void close_menu_overlay(const char *reason)
{
    if (menu_panel == NULL || lv_obj_has_flag(menu_panel, LV_OBJ_FLAG_HIDDEN)) {
        return;
    }

    lv_obj_add_flag(menu_panel, LV_OBJ_FLAG_HIDDEN);
    lv_obj_invalidate(lv_scr_act());
    if (menu_timer != NULL) {
        lv_timer_pause(menu_timer);
    }
    ESP_LOGI(TAG, "menu closed reason=%s", reason);
}

static void reset_idle_return_timeout(void)
{
    if (idle_return_timer == NULL) {
        return;
    }

    lv_timer_reset(idle_return_timer);
    lv_timer_resume(idle_return_timer);
}

static void show_app_screen_locked(app_screen_t screen, const char *reason)
{
    if (home_view == NULL || keypad_view == NULL || hsm_view == NULL) {
        return;
    }

    active_screen = screen;
    if (screen == APP_SCREEN_HOME) {
        lv_obj_clear_flag(home_view, LV_OBJ_FLAG_HIDDEN);
    } else {
        lv_obj_add_flag(home_view, LV_OBJ_FLAG_HIDDEN);
    }

    if (screen == APP_SCREEN_KEYPAD) {
        lv_obj_clear_flag(keypad_view, LV_OBJ_FLAG_HIDDEN);
    } else {
        lv_obj_add_flag(keypad_view, LV_OBJ_FLAG_HIDDEN);
    }

    if (screen == APP_SCREEN_HSM) {
        lv_obj_clear_flag(hsm_view, LV_OBJ_FLAG_HIDDEN);
    } else {
        dashboard_page_auto_paused = false;
        controls_dashboard_active = false;
        hide_control_detail_panel();
        lv_obj_add_flag(hsm_view, LV_OBJ_FLAG_HIDDEN);
    }

    if (screen == APP_SCREEN_HOME && idle_return_timer != NULL) {
        lv_timer_pause(idle_return_timer);
    } else {
        reset_idle_return_timeout();
    }

    close_menu_overlay("screen_change");
    apply_system_status_to_ui_locked();
    update_dashboard_page_timer_locked();
    if (board_type == KEYPAD_BOARD_AXS15231B) {
        lv_obj_invalidate(lv_scr_act());
    }
    ESP_LOGI(TAG, "screen active=%d reason=%s", (int)screen, reason);
}

static void idle_return_timeout_cb(lv_timer_t *timer)
{
    show_app_screen_locked(APP_SCREEN_HOME, "idle_timeout");
}

static void handle_root_swipe(lv_dir_t direction)
{
    ESP_LOGI(TAG,
             "swipe direction=%d active=%d controls=%s summary_loaded=%s controls_loaded=%s",
             (int)direction,
             (int)active_screen,
             controls_dashboard_active ? "true" : "false",
             hsm_dashboard.loaded ? "true" : "false",
             controls_dashboard.loaded ? "true" : "false");
    if (direction == LV_DIR_LEFT) {
        if (active_screen == APP_SCREEN_HOME && any_dashboard_loaded()) {
            show_app_screen_locked(APP_SCREEN_HSM, "swipe_left");
        } else if (active_screen == APP_SCREEN_HSM && hsm_dashboard.loaded && controls_dashboard.loaded && !controls_dashboard_active) {
            controls_dashboard_active = true;
            dashboard_page_index = 0;
            hide_control_detail_panel();
            apply_hsm_status_to_ui_locked();
            if (board_type == KEYPAD_BOARD_AXS15231B) {
                lv_obj_invalidate(lv_scr_act());
            }
            update_dashboard_page_timer_locked();
            ESP_LOGI(TAG, "dashboard controls shown");
        } else if (active_screen == APP_SCREEN_KEYPAD) {
            show_app_screen_locked(APP_SCREEN_HOME, "swipe_left");
        }
    } else if (direction == LV_DIR_RIGHT) {
        if (active_screen == APP_SCREEN_HSM && controls_dashboard_active && hsm_dashboard.loaded) {
            controls_dashboard_active = false;
            dashboard_page_index = 0;
            hide_control_detail_panel();
            apply_hsm_status_to_ui_locked();
            if (board_type == KEYPAD_BOARD_AXS15231B) {
                lv_obj_invalidate(lv_scr_act());
            }
            update_dashboard_page_timer_locked();
            ESP_LOGI(TAG, "dashboard summary shown");
        } else if (active_screen == APP_SCREEN_HSM) {
            show_app_screen_locked(APP_SCREEN_HOME, "swipe_right");
        } else if (active_screen == APP_SCREEN_HOME) {
            show_app_screen_locked(APP_SCREEN_KEYPAD, "swipe_right");
        }
    } else if (direction == LV_DIR_TOP) {
        if (page_dashboard_locked(1)) {
            ESP_LOGI(TAG, "dashboard page manual direction=next page=%u/%u",
                     (unsigned)(dashboard_page_index + 1),
                     (unsigned)dashboard_page_count());
        }
    } else if (direction == LV_DIR_BOTTOM) {
        if (page_dashboard_locked(-1)) {
            ESP_LOGI(TAG, "dashboard page manual direction=previous page=%u/%u",
                     (unsigned)(dashboard_page_index + 1),
                     (unsigned)dashboard_page_count());
        }
    }
}

static void root_screen_event_cb(lv_event_t *event)
{
    const lv_event_code_t code = lv_event_get_code(event);
    if (code == LV_EVENT_PRESSED || code == LV_EVENT_CLICKED) {
        if (wake_screen_for_touch_only()) {
            return;
        }
        reset_idle_return_timeout();
        return;
    }

    if (code == LV_EVENT_GESTURE) {
        if (wake_screen_for_touch_only()) {
            return;
        }
        reset_idle_return_timeout();
        lv_indev_t *indev = lv_indev_get_act();
        if (indev != NULL) {
            handle_root_swipe(lv_indev_get_gesture_dir(indev));
        }
    }
}

static void reset_menu_timeout(void)
{
    if (menu_timer == NULL) {
        return;
    }

    lv_timer_reset(menu_timer);
    lv_timer_resume(menu_timer);
}

static void menu_timeout_cb(lv_timer_t *timer)
{
    close_menu_overlay("timeout");
}

static void show_menu_home_view(void)
{
    if (menu_home_view != NULL) {
        lv_obj_clear_flag(menu_home_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_dashboard_view != NULL) {
        lv_obj_add_flag(menu_dashboard_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_network_view != NULL) {
        lv_obj_add_flag(menu_network_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_factory_reset_view != NULL) {
        lv_obj_add_flag(menu_factory_reset_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_firmware_view != NULL) {
        lv_obj_add_flag(menu_firmware_view, LV_OBJ_FLAG_HIDDEN);
    }
    reset_menu_timeout();
    lv_obj_invalidate(lv_scr_act());
}

static void show_menu_dashboard_view(void)
{
    if (menu_home_view != NULL) {
        lv_obj_add_flag(menu_home_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_dashboard_view != NULL) {
        lv_obj_clear_flag(menu_dashboard_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_network_view != NULL) {
        lv_obj_add_flag(menu_network_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_factory_reset_view != NULL) {
        lv_obj_add_flag(menu_factory_reset_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_firmware_view != NULL) {
        lv_obj_add_flag(menu_firmware_view, LV_OBJ_FLAG_HIDDEN);
    }
    apply_dashboard_status_to_ui_locked();
    reset_menu_timeout();
    lv_obj_invalidate(lv_scr_act());
}

static void show_menu_network_view(void)
{
    if (menu_home_view != NULL) {
        lv_obj_add_flag(menu_home_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_dashboard_view != NULL) {
        lv_obj_add_flag(menu_dashboard_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_network_view != NULL) {
        lv_obj_clear_flag(menu_network_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_factory_reset_view != NULL) {
        lv_obj_add_flag(menu_factory_reset_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_firmware_view != NULL) {
        lv_obj_add_flag(menu_firmware_view, LV_OBJ_FLAG_HIDDEN);
    }
    apply_network_status_to_ui_locked();
    reset_menu_timeout();
    lv_obj_invalidate(lv_scr_act());
}

static void show_menu_factory_reset_view(void)
{
    if (menu_home_view != NULL) {
        lv_obj_add_flag(menu_home_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_dashboard_view != NULL) {
        lv_obj_add_flag(menu_dashboard_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_network_view != NULL) {
        lv_obj_add_flag(menu_network_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_factory_reset_view != NULL) {
        lv_obj_clear_flag(menu_factory_reset_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_firmware_view != NULL) {
        lv_obj_add_flag(menu_firmware_view, LV_OBJ_FLAG_HIDDEN);
    }
    reset_menu_timeout();
    lv_obj_invalidate(lv_scr_act());
}

static void show_menu_firmware_view(void)
{
    if (menu_home_view != NULL) {
        lv_obj_add_flag(menu_home_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_dashboard_view != NULL) {
        lv_obj_add_flag(menu_dashboard_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_network_view != NULL) {
        lv_obj_add_flag(menu_network_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_factory_reset_view != NULL) {
        lv_obj_add_flag(menu_factory_reset_view, LV_OBJ_FLAG_HIDDEN);
    }
    if (menu_firmware_view != NULL) {
        lv_obj_clear_flag(menu_firmware_view, LV_OBJ_FLAG_HIDDEN);
    }
    apply_firmware_status_to_ui_locked();
    reset_menu_timeout();
    lv_obj_invalidate(lv_scr_act());
}

static void menu_panel_event_cb(lv_event_t *event)
{
    const lv_event_code_t code = lv_event_get_code(event);
    if (code == LV_EVENT_PRESSED || code == LV_EVENT_CLICKED) {
        if (wake_screen_for_touch_only()) {
            return;
        }
        reset_menu_timeout();
    }
}

static void menu_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    if (menu_panel == NULL) {
        return;
    }

    play_key_click();

    if (lv_obj_has_flag(menu_panel, LV_OBJ_FLAG_HIDDEN)) {
        lv_obj_clear_flag(menu_panel, LV_OBJ_FLAG_HIDDEN);
        lv_obj_move_foreground(menu_panel);
        show_menu_home_view();
        lv_obj_invalidate(lv_scr_act());
        ESP_LOGI(TAG, "menu opened");
    } else {
        close_menu_overlay("button");
    }
}

static void dashboard_menu_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_menu_dashboard_view();
    ESP_LOGI(TAG, "menu status opened");
}

static void network_menu_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_menu_network_view();
    ESP_LOGI(TAG, "menu network opened");
}

static void ota_check_task(void *arg)
{
    (void)arg;
    char manifest[OTA_MANIFEST_BUFFER_SIZE] = {};
    char version[OTA_VERSION_SIZE] = {};
    char image_url[OTA_URL_SIZE] = {};
    esp_err_t err = ESP_OK;
    const esp_app_desc_t *current = esp_app_get_description();

    if (!wifi_connected) {
        set_ota_status_from_task("Wi-Fi is offline.");
        goto done;
    }

    set_ota_status_from_task("Checking for updates...");
    err = download_https_text(OTA_MANIFEST_URL, manifest, sizeof(manifest));
    if (err != ESP_OK || !parse_json_string_field(manifest, "version", version, sizeof(version)) ||
        !parse_json_string_field(manifest, "url", image_url, sizeof(image_url))) {
        set_ota_status_from_task("Release feed unavailable.");
        goto done;
    }

    if (compare_firmware_versions(version, current->version) <= 0) {
        set_ota_status_from_task("Already up to date.");
        goto done;
    }

    if (lvgl_port_lock(pdMS_TO_TICKS(100))) {
        strlcpy(ota_available_version, version, sizeof(ota_available_version));
        strlcpy(ota_image_url, image_url, sizeof(ota_image_url));
        ota_update_available = true;
        set_label_text_fmt(firmware_status_label, "Version %s is ready.", ota_available_version);
        strlcpy(ota_status, "Tap Install to update and restart.", sizeof(ota_status));
        apply_firmware_status_to_ui_locked();
        lvgl_port_unlock();
    }

done:
    if (lvgl_port_lock(pdMS_TO_TICKS(100))) {
        ota_busy = false;
        apply_firmware_status_to_ui_locked();
        lvgl_port_unlock();
    }
    vTaskDelete(NULL);
}

static void ota_install_task(void *arg)
{
    (void)arg;
    esp_http_client_config_t http_config = {};
    http_config.url = ota_image_url;
    http_config.crt_bundle_attach = esp_crt_bundle_attach;
    http_config.timeout_ms = 15000;
    http_config.keep_alive_enable = true;
    esp_https_ota_config_t ota_config = {};
    ota_config.http_config = &http_config;
    esp_https_ota_handle_t handle = NULL;
    esp_app_desc_t new_app = {};
    esp_err_t err = esp_https_ota_begin(&ota_config, &handle);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "OTA begin failed: %s", esp_err_to_name(err));
        set_ota_status_from_task("Could not start update.");
        goto done;
    }

    err = esp_https_ota_get_img_desc(handle, &new_app);
    if (err != ESP_OK || strcmp(new_app.version, ota_available_version) != 0) {
        ESP_LOGW(TAG, "OTA image version rejected: %s", new_app.version);
        set_ota_status_from_task("Update image was rejected.");
        esp_https_ota_abort(handle);
        goto done;
    }

    while ((err = esp_https_ota_perform(handle)) == ESP_ERR_HTTPS_OTA_IN_PROGRESS) {
        int completed = esp_https_ota_get_image_len_read(handle);
        int total = esp_https_ota_get_image_size(handle);
        if (total > 0) {
            char progress[OTA_STATUS_SIZE] = {};
            snprintf(progress, sizeof(progress), "Downloading %d%%...", (completed * 100) / total);
            set_ota_status_from_task(progress);
        }
    }

    if (err != ESP_OK || !esp_https_ota_is_complete_data_received(handle)) {
        ESP_LOGW(TAG, "OTA update failed: %s", esp_err_to_name(err));
        set_ota_status_from_task("Update failed; current firmware kept.");
        esp_https_ota_abort(handle);
        goto done;
    }

    err = esp_https_ota_finish(handle);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "OTA finalize failed: %s", esp_err_to_name(err));
        set_ota_status_from_task("Update failed; current firmware kept.");
        goto done;
    }

    set_ota_status_from_task("Update ready. Restarting...");
    vTaskDelay(pdMS_TO_TICKS(800));
    esp_restart();

done:
    if (lvgl_port_lock(pdMS_TO_TICKS(100))) {
        ota_busy = false;
        apply_firmware_status_to_ui_locked();
        lvgl_port_unlock();
    }
    vTaskDelete(NULL);
}

static void firmware_menu_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED || wake_screen_for_touch_only()) {
        return;
    }
    play_key_click();
    show_menu_firmware_view();
    ESP_LOGI(TAG, "menu firmware opened");
}

static void firmware_check_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED || wake_screen_for_touch_only() || ota_busy) {
        return;
    }
    play_key_click();
    ota_busy = true;
    ota_update_available = false;
    strlcpy(ota_status, "Checking for updates...", sizeof(ota_status));
    apply_firmware_status_to_ui_locked();
    xTaskCreate(ota_check_task, "ota_check", 6144, NULL, 5, NULL);
}

static void firmware_install_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED || wake_screen_for_touch_only() || ota_busy || !ota_update_available) {
        return;
    }
    play_key_click();
    ota_busy = true;
    strlcpy(ota_status, "Preparing update...", sizeof(ota_status));
    apply_firmware_status_to_ui_locked();
    xTaskCreate(ota_install_task, "ota_install", 8192, NULL, 5, NULL);
}

static void network_factory_reset_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_menu_factory_reset_view();
    ESP_LOGI(TAG, "factory reset confirmation opened");
}

static void menu_back_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_menu_home_view();
    ESP_LOGI(TAG, "menu home opened");
}

static void factory_reset_cancel_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_menu_network_view();
    ESP_LOGI(TAG, "factory reset canceled");
}

static void network_setup_start_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    lv_obj_t *button = lv_event_get_target(event);
    lv_obj_add_state(button, LV_STATE_DISABLED);
    lv_label_set_text(lv_obj_get_child(button, 0), "Starting setup...");
    lv_obj_center(lv_obj_get_child(button, 0));
    lv_obj_invalidate(button);

    esp_err_t err = save_setup_requested_to_nvs(true);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "network setup request save failed: %s", esp_err_to_name(err));
        return;
    }

    ESP_LOGI(TAG, "network setup requested; restarting into setup mode");
    xTaskCreate(restart_task, "restart_setup", 2048, NULL, 5, NULL);
}

static void factory_reset_confirm_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    lv_obj_t *button = lv_event_get_target(event);
    lv_obj_add_state(button, LV_STATE_DISABLED);
    lv_label_set_text(lv_obj_get_child(button, 0), "Erasing...");
    lv_obj_center(lv_obj_get_child(button, 0));
    lv_obj_invalidate(button);

    esp_err_t err = erase_config_from_nvs();
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "factory reset failed: %s", esp_err_to_name(err));
        lv_obj_clear_state(button, LV_STATE_DISABLED);
        lv_label_set_text(lv_obj_get_child(button, 0), "Erase & restart");
        lv_obj_center(lv_obj_get_child(button, 0));
        lv_obj_invalidate(button);
        return;
    }

    esp_err_t matter_err = matter_transport_factory_reset();
    if (matter_err == ESP_OK) {
        ESP_LOGI(TAG, "factory reset complete; Matter reset will restart device");
        return;
    }
    if (matter_err != ESP_ERR_INVALID_STATE) {
        ESP_LOGW(TAG, "matter factory reset failed: %s", esp_err_to_name(matter_err));
    }

    ESP_LOGI(TAG, "factory reset complete; restarting into setup mode");
    xTaskCreate(restart_task, "restart_reset", 2048, NULL, 5, NULL);
}

static dashboard_item_t *visible_dashboard_item_for_tile(size_t visible_index)
{
    dashboard_screen_t *dashboard = visible_dashboard();
    const size_t item_index = dashboard_page_index * DASHBOARD_VISIBLE_ITEM_COUNT + visible_index;
    if (!dashboard->loaded || item_index >= dashboard->item_count) {
        return NULL;
    }
    return &dashboard->items[item_index];
}

static void hide_control_detail_panel(void)
{
    active_control_detail_index = -1;
    if (control_detail_panel != NULL) {
        lv_obj_add_flag(control_detail_panel, LV_OBJ_FLAG_HIDDEN);
    }
    if (active_screen == APP_SCREEN_HSM) {
        apply_hsm_status_to_ui_locked();
    }
}

static void show_control_detail_panel(dashboard_item_t *item, int item_index)
{
    if (control_detail_panel == NULL || item == NULL) {
        return;
    }

    active_control_detail_index = item_index;
    int level = item->has_level ? item->level : 0;
    if (level < 0) level = 0;
    if (level > 100) level = 100;

    set_label_text_fmt(control_detail_title_label, "%s", item->label);
    set_label_text_fmt(control_detail_value_label, "%d%%", level);
    lv_slider_set_value(control_detail_slider, level, LV_ANIM_OFF);
    if (hsm_page_label != NULL) {
        lv_obj_add_flag(hsm_page_label, LV_OBJ_FLAG_HIDDEN);
    }
    lv_obj_clear_flag(control_detail_panel, LV_OBJ_FLAG_HIDDEN);
}

static void hsm_tile_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }
    if (wake_screen_for_touch_only()) {
        return;
    }

    reset_idle_return_timeout();
    const bool controls_visible = visible_dashboard_is_controls();
    const size_t visible_index = (size_t)(uintptr_t)lv_event_get_user_data(event);
    dashboard_item_t *item = visible_dashboard_item_for_tile(visible_index);
    if (item == NULL) {
        return;
    }

    play_key_click();
    if (controls_visible && strcmp(item->kind, "dimmer") == 0 && item->detail) {
        const int item_index = (int)(dashboard_page_index * DASHBOARD_VISIBLE_ITEM_COUNT + visible_index);
        show_control_detail_panel(item, item_index);
        publish_dashboard_tap_request(item, "detail");
        return;
    }

    if (controls_visible && item->primary) {
        publish_dashboard_tap_request(item, "primary");
    }
}

static void control_detail_back_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }
    if (wake_screen_for_touch_only()) {
        return;
    }
    play_key_click();
    hide_control_detail_panel();
}

static void control_detail_slider_event_cb(lv_event_t *event)
{
    if (active_control_detail_index < 0 || active_control_detail_index >= (int)controls_dashboard.item_count) {
        return;
    }

    lv_event_code_t code = lv_event_get_code(event);
    int level = lv_slider_get_value(control_detail_slider);
    set_label_text_fmt(control_detail_value_label, "%d%%", level);

    if (code == LV_EVENT_RELEASED) {
        dashboard_item_t *item = &controls_dashboard.items[active_control_detail_index];
        item->level = level;
        item->has_level = true;
        snprintf(item->value, sizeof(item->value), "%d%%", level);
        publish_dashboard_level_request(item, level);
        apply_hsm_status_to_ui_locked();
    }
}

static void show_keypad_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_app_screen_locked(APP_SCREEN_KEYPAD, "button_keypad");
}

static void show_hsm_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    if (any_dashboard_loaded()) {
        show_app_screen_locked(APP_SCREEN_HSM, "button_hsm");
    }
}

static void show_home_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    show_app_screen_locked(APP_SCREEN_HOME, "button_home");
}

static void setup_cancel_button_event_cb(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED) {
        return;
    }

    if (wake_screen_for_touch_only()) {
        return;
    }

    play_key_click();
    lv_obj_t *button = lv_event_get_target(event);
    lv_obj_add_state(button, LV_STATE_DISABLED);
    lv_label_set_text(lv_obj_get_child(button, 0), "Returning...");
    lv_obj_center(lv_obj_get_child(button, 0));
    lv_obj_invalidate(button);

    esp_err_t err = save_setup_requested_to_nvs(false);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "setup cancel save failed: %s", esp_err_to_name(err));
        return;
    }

    ESP_LOGI(TAG, "setup canceled; restarting into keypad mode");
    xTaskCreate(restart_task, "restart_cancel", 2048, NULL, 5, NULL);
}

static lv_obj_t *create_label(lv_obj_t *parent, const char *text, const lv_font_t *font, lv_color_t color)
{
    lv_obj_t *label = lv_label_create(parent);
    lv_label_set_text(label, text);
    lv_obj_set_style_text_font(label, font, 0);
    lv_obj_set_style_text_color(label, color, 0);
    lv_obj_set_style_text_opa(label, LV_OPA_COVER, 0);
    lv_obj_set_style_text_letter_space(label, 0, 0);
    return label;
}

static lv_obj_t *create_key_button(lv_obj_t *parent,
                                   const keypad_key_t *key,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   lv_style_t *state_style,
                                   lv_color_t label_color)
{
    lv_obj_t *button = lv_btn_create(parent);
    lv_obj_add_style(button, &style_key, 0);
    lv_obj_add_style(button, &style_key_pressed, LV_STATE_PRESSED);
    if (state_style != NULL) {
        lv_obj_add_style(button, state_style, 0);
    }
    lv_obj_set_size(button, width, height);
    lv_obj_set_pos(button, x, y);
    lv_obj_add_event_cb(button, keypad_button_event_cb, LV_EVENT_CLICKED, (void *)key);

    lv_obj_t *label = create_label(button, key->label, &lv_font_montserrat_20, label_color);
    lv_obj_center(label);
    return button;
}

static void create_menu_button(lv_obj_t *parent)
{
    lv_obj_t *button = lv_btn_create(parent);
    lv_obj_add_style(button, &style_menu_button, 0);
    lv_obj_add_style(button, &style_menu_item_pressed, LV_STATE_PRESSED);
    lv_obj_set_size(button, 42, 42);
    lv_obj_set_pos(button, 8, 8);
    lv_obj_set_style_pad_all(button, 0, 0);
    lv_obj_clear_flag(button, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(button, menu_button_event_cb, LV_EVENT_CLICKED, NULL);

    for (int i = 0; i < 3; ++i) {
        lv_obj_t *bar = lv_obj_create(button);
        lv_obj_add_style(bar, &style_menu_bar, 0);
        lv_obj_set_size(bar, 20, 3);
        lv_obj_set_pos(bar, 11, 11 + i * 8);
        lv_obj_clear_flag(bar, LV_OBJ_FLAG_SCROLLABLE);
    }
}

static lv_obj_t *create_menu_item_button(lv_obj_t *parent, const char *text, int x, int y, int width, int height, lv_event_cb_t event_cb)
{
    lv_obj_t *button = lv_btn_create(parent);
    lv_obj_add_style(button, &style_menu_item, 0);
    lv_obj_add_style(button, &style_menu_item_pressed, LV_STATE_PRESSED);
    lv_obj_set_size(button, width, height);
    lv_obj_set_pos(button, x, y);
    lv_obj_clear_flag(button, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(button, event_cb, LV_EVENT_CLICKED, NULL);

    lv_obj_t *label = create_label(button, text, &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_center(label);
    return button;
}

static lv_obj_t *create_dashboard_tile(lv_obj_t *parent, const char *title, int x, int y, int width, int height)
{
    lv_obj_t *tile = lv_obj_create(parent);
    lv_obj_add_style(tile, &style_code, 0);
    lv_obj_set_size(tile, width, height);
    lv_obj_set_pos(tile, x, y);
    lv_obj_clear_flag(tile, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *label = create_label(tile, title, &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(label, 12, 8);
    return tile;
}

static void create_menu_overlay(lv_obj_t *parent)
{
    menu_panel = lv_obj_create(parent);
    lv_obj_add_style(menu_panel, &style_menu_panel, 0);
    lv_obj_set_size(menu_panel, 304, 260);
    lv_obj_set_pos(menu_panel, 8, 54);
    lv_obj_clear_flag(menu_panel, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(menu_panel, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_event_cb(menu_panel, menu_panel_event_cb, LV_EVENT_PRESSED, NULL);
    lv_obj_add_event_cb(menu_panel, menu_panel_event_cb, LV_EVENT_CLICKED, NULL);

    menu_home_view = lv_obj_create(menu_panel);
    lv_obj_set_size(menu_home_view, 288, 244);
    lv_obj_set_pos(menu_home_view, 0, 0);
    lv_obj_set_style_bg_opa(menu_home_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(menu_home_view, 0, 0);
    lv_obj_set_style_pad_all(menu_home_view, 0, 0);
    lv_obj_clear_flag(menu_home_view, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *menu_title = create_label(menu_home_view, "Menu", &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(menu_title, 8, 4);

    create_menu_item_button(menu_home_view, "Status", 8, 54, 272, 54, dashboard_menu_button_event_cb);
    create_menu_item_button(menu_home_view, "Network", 8, 122, 272, 54, network_menu_button_event_cb);
    create_menu_item_button(menu_home_view, "Firmware", 8, 190, 272, 54, firmware_menu_button_event_cb);

    menu_dashboard_view = lv_obj_create(menu_panel);
    lv_obj_set_size(menu_dashboard_view, 288, 244);
    lv_obj_set_pos(menu_dashboard_view, 0, 0);
    lv_obj_set_style_bg_opa(menu_dashboard_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(menu_dashboard_view, 0, 0);
    lv_obj_set_style_pad_all(menu_dashboard_view, 0, 0);
    lv_obj_clear_flag(menu_dashboard_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(menu_dashboard_view, LV_OBJ_FLAG_HIDDEN);

    lv_obj_t *dashboard_title = create_label(menu_dashboard_view, "Status", &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(dashboard_title, 8, 4);

    create_menu_item_button(menu_dashboard_view, "Back", 200, 0, 80, 34, menu_back_button_event_cb);

    dashboard_wifi_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(dashboard_wifi_label, 8, 46);

    dashboard_mqtt_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(dashboard_mqtt_label, 8, 72);

    dashboard_ip_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(dashboard_ip_label, 8, 98);

    dashboard_alarm_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(dashboard_alarm_label, 8, 124);

    dashboard_topic_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(dashboard_topic_label, 8, 154);
    lv_label_set_long_mode(dashboard_topic_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(dashboard_topic_label, 280);

    dashboard_broker_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(dashboard_broker_label, 8, 180);
    lv_label_set_long_mode(dashboard_broker_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(dashboard_broker_label, 280);

    dashboard_fw_label = create_label(menu_dashboard_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(dashboard_fw_label, 8, 206);
    lv_label_set_long_mode(dashboard_fw_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(dashboard_fw_label, 280);

    menu_network_view = lv_obj_create(menu_panel);
    lv_obj_set_size(menu_network_view, 288, 244);
    lv_obj_set_pos(menu_network_view, 0, 0);
    lv_obj_set_style_bg_opa(menu_network_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(menu_network_view, 0, 0);
    lv_obj_set_style_pad_all(menu_network_view, 0, 0);
    lv_obj_clear_flag(menu_network_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(menu_network_view, LV_OBJ_FLAG_HIDDEN);

    lv_obj_t *network_title = create_label(menu_network_view, "Network", &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(network_title, 8, 4);

    create_menu_item_button(menu_network_view, "Back", 200, 0, 80, 34, menu_back_button_event_cb);

    network_wifi_label = create_label(menu_network_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(network_wifi_label, 8, 46);
    lv_label_set_long_mode(network_wifi_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(network_wifi_label, 280);

    network_mqtt_label = create_label(menu_network_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(network_mqtt_label, 8, 72);

    network_ip_label = create_label(menu_network_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(network_ip_label, 8, 98);

    network_device_label = create_label(menu_network_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(network_device_label, 8, 124);
    lv_label_set_long_mode(network_device_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(network_device_label, 280);

    network_topic_label = create_label(menu_network_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(network_topic_label, 8, 150);
    lv_label_set_long_mode(network_topic_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(network_topic_label, 280);

    network_broker_label = create_label(menu_network_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(network_broker_label, 8, 176);
    lv_label_set_long_mode(network_broker_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(network_broker_label, 280);

    create_menu_item_button(menu_network_view, "Setup", 8, 208, 132, 34, network_setup_start_button_event_cb);
    create_menu_item_button(menu_network_view, "Reset", 148, 208, 132, 34, network_factory_reset_button_event_cb);

    menu_firmware_view = lv_obj_create(menu_panel);
    lv_obj_set_size(menu_firmware_view, 288, 244);
    lv_obj_set_pos(menu_firmware_view, 0, 0);
    lv_obj_set_style_bg_opa(menu_firmware_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(menu_firmware_view, 0, 0);
    lv_obj_set_style_pad_all(menu_firmware_view, 0, 0);
    lv_obj_clear_flag(menu_firmware_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(menu_firmware_view, LV_OBJ_FLAG_HIDDEN);

    lv_obj_t *firmware_title = create_label(menu_firmware_view, "Firmware", &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(firmware_title, 8, 4);
    create_menu_item_button(menu_firmware_view, "Back", 200, 0, 80, 34, menu_back_button_event_cb);

    firmware_current_label = create_label(menu_firmware_view, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(firmware_current_label, 8, 52);
    firmware_status_label = create_label(menu_firmware_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(firmware_status_label, 8, 82);
    lv_label_set_long_mode(firmware_status_label, LV_LABEL_LONG_WRAP);
    lv_obj_set_width(firmware_status_label, 272);

    create_menu_item_button(menu_firmware_view, "Check for update", 8, 132, 272, 42, firmware_check_button_event_cb);
    firmware_install_button = create_menu_item_button(menu_firmware_view, "No update selected", 8, 184, 272, 42, firmware_install_button_event_cb);

    menu_factory_reset_view = lv_obj_create(menu_panel);
    lv_obj_set_size(menu_factory_reset_view, 288, 244);
    lv_obj_set_pos(menu_factory_reset_view, 0, 0);
    lv_obj_set_style_bg_opa(menu_factory_reset_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(menu_factory_reset_view, 0, 0);
    lv_obj_set_style_pad_all(menu_factory_reset_view, 0, 0);
    lv_obj_clear_flag(menu_factory_reset_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(menu_factory_reset_view, LV_OBJ_FLAG_HIDDEN);

    lv_obj_t *factory_reset_title = create_label(menu_factory_reset_view,
                                                  "Factory reset",
                                                  &lv_font_montserrat_20,
                                                  lv_color_hex(0xe95757));
    lv_obj_set_pos(factory_reset_title, 8, 4);
    create_menu_item_button(menu_factory_reset_view, "Cancel", 200, 0, 80, 34, factory_reset_cancel_button_event_cb);

    lv_obj_t *factory_reset_warning = create_label(menu_factory_reset_view,
                                                    "Erase saved Wi-Fi, MQTT,\nand device identity.",
                                                    &lv_font_montserrat_16,
                                                    lv_color_hex(0xe6edf5));
    lv_obj_set_pos(factory_reset_warning, 8, 54);

    lv_obj_t *factory_reset_note = create_label(menu_factory_reset_view,
                                                 "This cannot be undone.",
                                                 &lv_font_montserrat_16,
                                                 lv_color_hex(0xf0b84a));
    lv_obj_set_pos(factory_reset_note, 8, 116);

    create_menu_item_button(menu_factory_reset_view,
                            "Erase & restart",
                            8,
                            166,
                            272,
                            48,
                            factory_reset_confirm_button_event_cb);

    apply_dashboard_status_to_ui_locked();
    apply_network_status_to_ui_locked();
    apply_firmware_status_to_ui_locked();

    menu_timer = lv_timer_create(menu_timeout_cb, MENU_AUTO_CLOSE_MS, NULL);
    lv_timer_pause(menu_timer);
}

static void show_setup_screen(void)
{
    if (!lvgl_port_lock(0)) {
        return;
    }

    init_keypad_styles();
    build_setup_ap_ssid();

    lv_obj_t *screen = lv_scr_act();
    lv_obj_clean(screen);
    lv_obj_add_style(screen, &style_screen, 0);
    lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(screen, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(screen, wake_area_event_cb, LV_EVENT_PRESSED, NULL);

    lv_obj_t *title = create_label(screen, "SETUP", &lv_font_montserrat_20, lv_color_hex(0xf0b84a));
    lv_obj_align(title, LV_ALIGN_TOP_MID, 0, 38);

    lv_obj_t *subtitle = create_label(screen, "Join setup Wi-Fi", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_align(subtitle, LV_ALIGN_TOP_MID, 0, 82);

    lv_obj_t *ssid_box = lv_obj_create(screen);
    lv_obj_add_style(ssid_box, &style_code, 0);
    lv_obj_set_size(ssid_box, 292, 56);
    lv_obj_align(ssid_box, LV_ALIGN_TOP_MID, 0, 114);
    lv_obj_clear_flag(ssid_box, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *ssid_label = create_label(ssid_box, setup_ap_ssid, &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_label_set_long_mode(ssid_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(ssid_label, 260);
    lv_obj_center(ssid_label);

    lv_obj_t *url_title = create_label(screen, "Then open", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_align(url_title, LV_ALIGN_TOP_MID, 0, 206);

    lv_obj_t *url_label = create_label(screen, "http://192.168.4.1", &lv_font_montserrat_20, lv_color_hex(0x38d26f));
    lv_obj_align(url_label, LV_ALIGN_TOP_MID, 0, 236);

    char device_text[DEVICE_ID_SIZE + 9] = {};
    snprintf(device_text, sizeof(device_text), "Device: %s", active_config.device_id);
    lv_obj_t *device_label = create_label(screen, device_text, &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_align(device_label, LV_ALIGN_TOP_MID, 0, 304);

    lv_obj_t *note = create_label(screen, "Web setup saves to device", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_align(note, LV_ALIGN_TOP_MID, 0, 354);

    create_menu_item_button(screen, "Cancel", 200, 8, 104, 34, setup_cancel_button_event_cb);

    lvgl_port_unlock();
}

static void show_keypad_screen(void)
{
    if (!lvgl_port_lock(0)) {
        return;
    }

    init_keypad_styles();

    lv_obj_t *screen = lv_scr_act();
    lv_obj_clean(screen);
    lv_obj_add_style(screen, &style_screen, 0);
    lv_obj_clear_flag(screen, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(screen, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(screen, root_screen_event_cb, LV_EVENT_PRESSED, NULL);
    lv_obj_add_event_cb(screen, root_screen_event_cb, LV_EVENT_CLICKED, NULL);
    lv_obj_add_event_cb(screen, root_screen_event_cb, LV_EVENT_GESTURE, NULL);

    home_view = lv_obj_create(screen);
    lv_obj_set_size(home_view, 320, 480);
    lv_obj_set_pos(home_view, 0, 0);
    lv_obj_set_style_bg_opa(home_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(home_view, 0, 0);
    lv_obj_set_style_pad_all(home_view, 0, 0);
    lv_obj_clear_flag(home_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(home_view, root_screen_event_cb, LV_EVENT_GESTURE, NULL);

    lv_obj_t *home_title = create_label(home_view, "Home", &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_align(home_title, LV_ALIGN_TOP_MID, 0, 26);

    lv_obj_t *security_tile = create_dashboard_tile(home_view, "Security", 18, 84, 284, 86);
    home_alarm_label = create_label(security_tile, "", &lv_font_montserrat_20, lv_color_hex(0x38d26f));
    lv_obj_set_pos(home_alarm_label, 12, 34);
    home_ready_label = create_label(security_tile, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(home_ready_label, 170, 38);

    lv_obj_t *network_tile = create_dashboard_tile(home_view, "Network", 18, 184, 284, 72);
    home_network_label = create_label(network_tile, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(home_network_label, 12, 36);
    lv_label_set_long_mode(home_network_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(home_network_label, 252);

    lv_obj_t *detail_tile = create_dashboard_tile(home_view, "Detail", 18, 270, 284, 72);
    home_delay_label = create_label(detail_tile, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(home_delay_label, 12, 36);
    lv_label_set_long_mode(home_delay_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(home_delay_label, 252);

    create_menu_item_button(home_view, "Keypad", 18, 390, 132, 48, show_keypad_button_event_cb);
    home_dashboard_button = create_menu_item_button(home_view, "Dashboard", 170, 390, 132, 48, show_hsm_button_event_cb);

    keypad_view = lv_obj_create(screen);
    lv_obj_set_size(keypad_view, 320, 480);
    lv_obj_set_pos(keypad_view, 0, 0);
    lv_obj_set_style_bg_opa(keypad_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(keypad_view, 0, 0);
    lv_obj_set_style_pad_all(keypad_view, 0, 0);
    lv_obj_clear_flag(keypad_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(keypad_view, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_event_cb(keypad_view, root_screen_event_cb, LV_EVENT_GESTURE, NULL);

    alarm_state_label = create_label(keypad_view,
                                     armed_state_label_text(system_status.armed_state),
                                     &lv_font_montserrat_20,
                                     armed_state_color(system_status.armed_state));
    lv_obj_align(alarm_state_label, LV_ALIGN_TOP_MID, 0, 26);

    alarm_detail_label = create_label(keypad_view,
                                      alarm_detail_text(),
                                      &lv_font_montserrat_16,
                                      alarm_detail_color());
    lv_obj_align(alarm_detail_label, LV_ALIGN_TOP_MID, 0, 54);

    lv_obj_t *code_box = lv_obj_create(keypad_view);
    lv_obj_add_style(code_box, &style_code, 0);
    lv_obj_set_size(code_box, 256, 50);
    lv_obj_align(code_box, LV_ALIGN_TOP_MID, 0, 92);
    lv_obj_clear_flag(code_box, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(code_box, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(code_box, wake_area_event_cb, LV_EVENT_PRESSED, NULL);

    credential_screen.code_label = create_label(code_box, "Enter code", &lv_font_montserrat_20, lv_color_hex(0xd7e0ec));
    lv_obj_center(credential_screen.code_label);

    constexpr int key_width = 78;
    constexpr int key_height = 58;
    constexpr int key_gap = 10;
    constexpr int grid_x = 31;
    constexpr int grid_y = 164;

    for (size_t index = 0; index < sizeof(KEYPAD_KEYS) / sizeof(KEYPAD_KEYS[0]); ++index) {
        const int row = index / 3;
        const int col = index % 3;
        const keypad_key_t *key = &KEYPAD_KEYS[index];
        lv_style_t *key_style = NULL;
        lv_color_t label_color = lv_color_hex(0x111318);
        if (key->action == KEY_ACTION_BACKSPACE) {
            key_style = &style_key_action;
        } else if (key->action == KEY_ACTION_ENTER) {
            key_style = &style_key_enter;
        }
        create_key_button(keypad_view,
                          key,
                          grid_x + col * (key_width + key_gap),
                          grid_y + row * (key_height + key_gap),
                          key_width,
                          key_height,
                          key_style,
                          label_color);
    }

    create_key_button(keypad_view, &ARM_KEY, 31, 436, 122, 36, &style_key_arm, lv_color_hex(0xffffff));
    create_key_button(keypad_view, &PARTIAL_KEY, 167, 436, 122, 36, &style_key_partial, lv_color_hex(0x15100a));

    hsm_view = lv_obj_create(screen);
    lv_obj_set_size(hsm_view, 320, 480);
    lv_obj_set_pos(hsm_view, 0, 0);
    lv_obj_set_style_bg_opa(hsm_view, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(hsm_view, 0, 0);
    lv_obj_set_style_pad_all(hsm_view, 0, 0);
    lv_obj_clear_flag(hsm_view, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(hsm_view, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_event_cb(hsm_view, root_screen_event_cb, LV_EVENT_GESTURE, NULL);

    hsm_title_label = create_label(hsm_view, hsm_dashboard.title, &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_align(hsm_title_label, LV_ALIGN_TOP_MID, 0, 26);

    lv_obj_t *hsm_tile = create_dashboard_tile(hsm_view, "", 18, 84, 284, 284);
    lv_obj_set_style_bg_opa(hsm_tile, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(hsm_tile, 0, 0);
    hsm_state_label = create_label(hsm_tile, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(hsm_state_label, 12, 40);
    lv_label_set_long_mode(hsm_state_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(hsm_state_label, 252);

    hsm_ready_label = create_label(hsm_tile, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(hsm_ready_label, 12, 72);
    hsm_delay_label = create_label(hsm_tile, "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
    lv_obj_set_pos(hsm_delay_label, 12, 104);
    hsm_last_result_label = create_label(hsm_tile, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_set_pos(hsm_last_result_label, 12, 136);
    lv_label_set_long_mode(hsm_last_result_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(hsm_last_result_label, 252);

    hsm_page_label = create_label(hsm_view, "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
    lv_obj_align(hsm_page_label, LV_ALIGN_TOP_MID, 0, 366);
    lv_obj_add_flag(hsm_page_label, LV_OBJ_FLAG_HIDDEN);

    constexpr int hsm_item_tile_width = 128;
    constexpr int hsm_item_tile_height = 66;
    constexpr int hsm_item_tile_gap_x = 8;
    constexpr int hsm_item_tile_gap_y = 12;
    constexpr int hsm_item_grid_x = 1;
    constexpr int hsm_item_grid_y = 12;

    for (size_t i = 0; i < DASHBOARD_VISIBLE_ITEM_COUNT; ++i) {
        const int row = (int)i / 2;
        const int col = (int)i % 2;
        hsm_item_tiles[i] = lv_obj_create(hsm_tile);
        lv_obj_add_style(hsm_item_tiles[i], &style_menu_item, 0);
        lv_obj_add_style(hsm_item_tiles[i], &style_menu_item_pressed, LV_STATE_PRESSED);
        lv_obj_set_size(hsm_item_tiles[i], hsm_item_tile_width, hsm_item_tile_height);
        lv_obj_set_style_pad_all(hsm_item_tiles[i], 0, 0);
        lv_obj_set_pos(hsm_item_tiles[i],
                       hsm_item_grid_x + col * (hsm_item_tile_width + hsm_item_tile_gap_x),
                       hsm_item_grid_y + row * (hsm_item_tile_height + hsm_item_tile_gap_y));
        lv_obj_clear_flag(hsm_item_tiles[i], LV_OBJ_FLAG_SCROLLABLE);
        lv_obj_add_flag(hsm_item_tiles[i], LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_event_cb(hsm_item_tiles[i], hsm_tile_event_cb, LV_EVENT_CLICKED, (void *)(uintptr_t)i);

        hsm_item_labels[i] = create_label(hsm_item_tiles[i], "", &lv_font_montserrat_16, lv_color_hex(0xb8c6d8));
        lv_obj_set_pos(hsm_item_labels[i], 5, 2);
        lv_label_set_long_mode(hsm_item_labels[i], LV_LABEL_LONG_DOT);
        lv_obj_set_width(hsm_item_labels[i], 118);

        hsm_item_value_labels[i] = create_label(hsm_item_tiles[i], "", &lv_font_montserrat_16, lv_color_hex(0xe6edf5));
        lv_obj_set_pos(hsm_item_value_labels[i], 5, 34);
        lv_label_set_long_mode(hsm_item_value_labels[i], LV_LABEL_LONG_DOT);
        lv_obj_set_width(hsm_item_value_labels[i], 118);
    }

    create_menu_item_button(hsm_view, "Keypad", 18, 396, 132, 42, show_keypad_button_event_cb);
    create_menu_item_button(hsm_view, "Home", 170, 396, 132, 42, show_home_button_event_cb);

    control_detail_panel = lv_obj_create(hsm_view);
    lv_obj_add_style(control_detail_panel, &style_code, 0);
    lv_obj_set_size(control_detail_panel, 284, 284);
    lv_obj_set_pos(control_detail_panel, 18, 84);
    lv_obj_clear_flag(control_detail_panel, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(control_detail_panel, LV_OBJ_FLAG_HIDDEN);

    control_detail_title_label = create_label(control_detail_panel, "", &lv_font_montserrat_20, lv_color_hex(0xe6edf5));
    lv_obj_align(control_detail_title_label, LV_ALIGN_TOP_MID, 0, 12);
    lv_label_set_long_mode(control_detail_title_label, LV_LABEL_LONG_DOT);
    lv_obj_set_width(control_detail_title_label, 240);

    control_detail_value_label = create_label(control_detail_panel, "0%", &lv_font_montserrat_20, lv_color_hex(0x38d26f));
    lv_obj_align(control_detail_value_label, LV_ALIGN_TOP_MID, 0, 48);

    control_detail_slider = lv_slider_create(control_detail_panel);
    lv_obj_remove_style_all(control_detail_slider);
    lv_obj_add_style(control_detail_slider, &style_dimmer_track, LV_PART_MAIN);
    lv_obj_add_style(control_detail_slider, &style_dimmer_fill, LV_PART_INDICATOR);
    lv_obj_add_style(control_detail_slider, &style_dimmer_knob, LV_PART_KNOB);
    lv_obj_set_size(control_detail_slider, 76, 132);
    lv_obj_align(control_detail_slider, LV_ALIGN_TOP_MID, 0, 92);
    lv_slider_set_range(control_detail_slider, 0, 100);
    lv_obj_add_event_cb(control_detail_slider, control_detail_slider_event_cb, LV_EVENT_VALUE_CHANGED, NULL);
    lv_obj_add_event_cb(control_detail_slider, control_detail_slider_event_cb, LV_EVENT_RELEASED, NULL);

    create_menu_item_button(control_detail_panel, "Back", 76, 238, 132, 34, control_detail_back_event_cb);

    idle_return_timer = lv_timer_create(idle_return_timeout_cb, SCREEN_DIM_TIMEOUT_MS, NULL);
    lv_timer_pause(idle_return_timer);
    dashboard_page_timer = lv_timer_create(dashboard_page_timer_cb, DASHBOARD_PAGE_ROTATE_MS, NULL);
    lv_timer_pause(dashboard_page_timer);

    create_menu_button(screen);
    create_menu_overlay(screen);
    lv_obj_move_foreground(menu_panel);
    show_app_screen_locked(APP_SCREEN_HOME, "boot");

    lvgl_port_unlock();
}

extern "C" void app_main(void)
{
    ESP_LOGI(TAG, "Starting Waveshare ESP32-S3-Touch-LCD-3.5 keypad");
    init_nvs();
    load_config_from_nvs();
    setup_requested_from_nvs = load_setup_requested_from_nvs();
    const bool setup_mode = FORCE_SETUP_MODE || setup_requested_from_nvs || config_needs_setup();
    init_i2c();
    detect_board_type();
    init_audio();
    reset_lcd_via_expander();
    init_lcd();
    init_touch();
    init_backlight();
    set_backlight(BACKLIGHT_ACTIVE_BRIGHTNESS);
    init_lvgl();
    confirm_ota_boot_if_needed();
    if (setup_mode) {
        show_setup_screen();
    } else {
        show_keypad_screen();
    }
    init_screen_dim_timer();
    if (setup_mode) {
        init_setup_ap_network();
    } else {
        init_station_network();
    }
}
