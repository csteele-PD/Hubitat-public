/*
	Generic Component Invertible Switch
	2022-05-24 csteele
	    -refactor generic into Invertible

	Generic Component Switch
	Copyright 2016 -> 2020 Hubitat Inc. All Rights Reserved
    2020-04-16 2.2.0 maxwell
        -refactor
	2018-12-15 maxwell
	    -initial pub

*/

metadata {
    definition(name: "Generic Component Invertible Switch", namespace: "csteele", author: "CSteele", component: true) {
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
    }
    preferences {
        input name: "nOpen",     type: "bool", title: "Invert Open/Close Events?", required: false, defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List description) {
    description.each {
        if (it.name in ["switch"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
    }
}

void on() {
    nOpen ? parent?.componentOn(this.device) : parent?.componentOff(this.device)
}

void off() {
    nOpen ? parent?.componentOff(this.device) : parent?.componentOn(this.device)
}

void refresh() {
    parent?.componentRefresh(this.device)
}
