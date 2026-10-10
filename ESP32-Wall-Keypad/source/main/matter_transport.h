#pragma once

#include "esp_err.h"

#include <stddef.h>

esp_err_t matter_transport_start(const char *device_id);
esp_err_t matter_transport_set_armed_state(const char *armed_state, bool ready);
esp_err_t matter_transport_get_manual_pairing_code(char *buffer, size_t buffer_size);
bool matter_transport_is_commissioned(void);
esp_err_t matter_transport_factory_reset(void);
