#pragma once

#include <stddef.h>

#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"

#ifdef __cplusplus
extern "C" {
#endif

void keypad_init_axs15231b_display(esp_lcd_panel_io_handle_t *io_handle,
                                   esp_lcd_panel_handle_t *panel_handle,
                                   size_t max_transfer_size);

#ifdef __cplusplus
}
#endif
