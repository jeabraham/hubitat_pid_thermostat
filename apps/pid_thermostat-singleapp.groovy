/*
 *  PID Thermostat App
 *  Project URL: https://github.com/jeabraham/hubitat_pid_thermostat
 *  Thanks to https://github.com/NelsonClark/Hubitat/tree/main/Apps/Advanced_vThermostat_V2
 *  Copyright 2025 John Abraham
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Version History...
 *
 *  V0.1 First tries
 *
 */

definition(
    name: "PID Thermostat",
    namespace: "jeabraham",
    author: "John Abraham",
    description: "Turns another thermostat on (by bumping up its setpoint a few degrees) and off (by bumping down its setpoint) to duty-cycle its control, to avoid overshooting and attempt to keep a smooth amount of heat.",
    category: "Green Living",
    iconUrl: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat_V2/Advanced_vThermostat_V2-logo-small.png",
    iconX2Url: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat_V2/Advanced_vThermostat_V2-logo.png",
    importUrl: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat_V2/Advanced_vThermostat_V2-Child.groovy",
)

preferences {
    section("Logging Level:") {
        input "logLevel", "enum", title: "Select Log Level", options: ["trace", "debug", "info", "warn", "error"], defaultValue: "info", required: true
    }
    section("Select Thermostat to control") {
        input "thermostat", "capability.thermostat", title: "Thermostat", required: true
    }
    section("Select Setpoint Source:") {
        input "setpointDevice", "capability.sensor", title: "Setpoint Device or Sensor", required: false
        input "manualSetpoint", "decimal", title: "Manual Setpoint (If no device is specified)", required: false
    }
    section("PID Parameters:") {
        input "P_parameter", "decimal", title: "Proportional Gain (P)", defaultValue: 0.25
        input "I_parameter", "decimal", title: "Integral Gain (I)", defaultValue: 0.00007
        input "D_parameter", "decimal", title: "Derivative Gain (D)", defaultValue: 0.1
    }
    section("Adjustable Parameters:") {
        input "tempThreshold", "decimal", title: "Temperature delta applied to controlled thermostat to turn it on or off", defaultValue: 4
        input "cycleTime", "decimal", title: "What is the cycle time in seconds? E.g. 1200 will turn the heat on for a portion of the time every 20 minutes", defaultValue:  1200
    }
}

def logMessage(level, message) {
    def levels = ["trace": 1, "debug": 2, "info": 3, "warn": 4, "error": 5]
    def configuredLevel = levels[logLevel] ?: 3 // Default to "info" if not set

    if (levels[level] >= configuredLevel) {
        switch (level) {
            case "trace":
                log.trace message
                break
            case "debug":
                log.debug message
                break
            case "info":
                log.info message
                break
            case "warn":
                log.warn message
                break
            case "error":
                log.error message
                break
            default:
                log.debug message
                break
        }
    }
}

def updated() {
    unschedule()
    logMessage("info", "App updated. Unscheduling existing tasks and validating inputs...")

    // Validate PID parameters
    if (P_parameter == null || P_parameter <= 0) {
        logMessage("error", "Invalid Proportional Gain (P). Setting to default value of 0.25.")
        P_parameter = 0.25
    }

    if (I_parameter == null || I_parameter < 0) {
        logMessage("error", "Invalid Integral Gain (I). Setting to default value of 0.00007.")
        I_parameter = 0.00007
    }

    if (D_parameter == null || D_parameter < 0) {
        logMessage("error", "Invalid Derivative Gain (D). Setting to default value of 0.1.")
        D_parameter = 0.1
    }

    // Validate cycleTime
    if (cycleTime == null || cycleTime <= 0) {
        logMessage("error", "Invalid cycleTime. Setting to default value of 1200 seconds.")
        cycleTime = 1200
    }

    logMessage("info", "PID parameters successfully updated: P=${P_parameter}, I=${I_parameter}, D=${D_parameter}, cycleTime=${cycleTime}.")
    state.e0 = 0
    state.e1 = 0
    state.e2 = 0
    if (state.d_on_or_off == null) {
        state.d_on_or_off = false
    }
    runEvery1Minute(controlLoop)
}

def initialize() {
    unschedule()
    state.W_control = 0.1
    state.W_trimmed = 0.1
    state.e0 = 0
    state.e1 = 0
    state.e2 = 0
    state.d_on_or_off = false
    runEvery1Minute(controlLoop)
}

def uninstalled() {
    logger("trace", "< uninstalled ---------------")
    unschedule()
}

def disableApp() {
    log.warn "App is being disabled. Stopping all scheduled tasks."
    unschedule()
}

def controlLoop() {
    logMessage("trace", "Starting control loop...")

    if (state.W_control == null) {
        logMessage("error", "state.W_control is null. Initializing to 0.1.")
        state.W_control = 0.1
    }

    def Ts_setpoint = null
    // since we are running once per minute, delta_t is 60
    def delta_t = 60

    def Tm_measured = thermostat.currentTemperature
    logMessage("debug", "Measured temperature: ${Tm_measured}")

    if (setpointDevice) {
        Ts_setpoint = setpointDevice.currentValue("heatingSetpoint")
        if (Ts_setpoint != null) {
            logMessage("debug", "Using setpointDevice's heatingSetpoint: ${Ts_setpoint}")
        } else {
            logMessage("debug", "setpointDevice does not have \"heatingSetpoint\", trying \"Temperature\"")
            Ts_setpoint = setpointDevice.currentValue("Temperature")
            if (Ts_setpoint != null) {
                logMessage("debug", "Using setpointDevice's Temperature: ${Ts_setpoint}")
            } else {
              logMessage("error", "setpointDevice does not have \"heatingSetpoint\" nor \"Temperature\", it has ${setpointDevice.getSupportedAttributes()}")
              return       
            }
        }
    } else if (manualSetpoint) {
        Ts_setpoint = manualSetpoint
        logMessage("debug", "Using manual setpoint: ${Ts_setpoint}")
    }

    // check if Ts_setpoint exists and is numeric
    if (Ts_setpoint == null || !(Ts_setpoint instanceof Number)) {
        logMessage("error", "Setpoint is missing or is not numeric.")
        return
    }


    state.e0 = Ts_setpoint - Tm_measured
    logMessage("trace", "Calculated error: ${state.e0}")
    state.e2 = state.e1
    state.e1 = state.e0

    if (P_parameter == null || I_parameter == null || D_parameter == null) {
           logMessage("error", "PID parameters are invalid. Exiting control loop.")
           return
       }

    if (state.e0 == null || state.e1 == null || state.e2 == null) {
         logMessage("error", "State error values (e0, e1, e2) are missing. Exiting control loop.")
         return
    }

    def I_times_dt = I_parameter * delta_t
    def D_divide_dt = D_parameter / delta_t

    def A0 = P_parameter + I_times_dt + D_divide_dt
    def A1 = -1.0 * P_parameter - (2.0 * D_divide_dt)
    def A2 = D_divide_dt

    state.W_control = state.W_control + (A0 * state.e0) + (A1 * state.e1) + (A2 * state.e2)

    logMessage("trace", "Calculated duty cycle W_control: ${state.W_control}")

    if (state.W_control > 0.9) {
        state.W_trimmed = 1.0
    } else if (state.W_control < 0.1) {
        state.W_trimmed = 0
    } else {
        state.W_trimmed = state.W_control
    }

    def TH_high = Ts_setpoint + tempThreshold
    def TL_low = Ts_setpoint - tempThreshold

    def t_time = now() / 1000 // Current time in seconds
    def where_in_cycle = (t_time % cycleTime) / cycleTime

    if (where_in_cycle > state.W_trimmed && state.d_on_or_off == true) {
        logMessage("trace", "Turning off (down) thermostat at cycle portion: ${where_in_cycle}")
        state.d_on_or_off = false
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TL_low)
        thermostat.setThermostatFanMode("auto")
    }

    if (where_in_cycle < state.W_trimmed && state.d_on_or_off == false) {
        logMessage("trace", "Turning on (up) thermostat at cycle portion: ${where_in_cycle}")
        state.d_on_or_off = true
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TH_high)
        thermostat.setThermostatFanMode("auto")
    }

    // Anti reset windup at 20%
    def accumulator = (P_parameter * state.e0) + -0.2
    if (state.W_control < accumulator) {
        state.W_control = accumulator
    }
    accumulator = accumulator + 1.2
    if (state.W_control > accumulator) {
        state.W_control = accumulator
    }
}
