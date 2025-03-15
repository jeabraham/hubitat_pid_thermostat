/*
 *  PID Thermostat Child App
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
    section("Temperature Threshold:") {
        input "tempThreshold", "decimal", title: "Temperature delta applied to controlled thermostat to turn it on or off", defaultValue: 4
    }
    section("Cycle Time:") {}
        input "cycleTime", "decimnal", title: "What is the cycle time in seconds? E.g. 1200 will turn the heat on for a portion of the time every 20 minutes"
    }
}

def installed() {
    initialize()
    logger("trace", "--------------- installed >")
}


def updated() {
    unschedule()
    runEvery1Minute(controlLoop)
}

def initialize() {
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
}


def controlLoop() {
// since we are running once per minute, delta_t is 60
    def delta_t = 60
    if (state.is_running) return
    state.is_running = true

    def Tm_measured = thermostat.currentTemperature
    

    if (setpointDevice) {
        if (setpointDevice.hasCapability("thermostat")) {
            Ts_setpoint = setpointDevice.currentHeatingSetpoint
        } else if (setpointDevice.hasCapability("sensor")) {
            Ts_setpoint = setpointDevice.currentValue("temperature")
        }
    } else if (manualSetpoint) {
        Ts_setpoint = manualSetpoint
    } else {
        logger("error", "No valid setpoint source defined. Please specify a Setpoint Source or provide a Manual Setpoint.")
        return
    }

    state.e0 = Ts_setpoint - Tm_measured
    state.e2 = state.e1
    state.e1 = state.e0

    def I_times_dt = I_parameter * delta_t
    def D_divide_dt = D_parameter / delta_t

    def A0 = P_parameter + I_times_dt + D_divide_dt
    def A1 = -1.0 * P_parameter - (2.0 * D_divide_dt)
    def A2 = D_divide_dt

    state W_control = state.W_control + (A0 * state.e0) + (A1 * state.e1) + (A2 * state.e2)
    if (state.W_control > 0.9) {
        state.W_trimmed = 1.0
    } else if (state.W_control < 0.1) {
        state.W_trimmed = 0
    } else {
        state.W_trimmed = state.W_control
    }

    def TH_high = Ts_setpoint + tempThreshold
    def TL_low = Ts_setpoint - tempThreshold

    if (state.W_control < 0.1) {
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TL_low)
    } else if (state.W_control > 0.9) {
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TH_high)
    }

    
    def t_time = now() / 1000 // Current time in seconds
    def where_in_cycle = (t_time % cycleTime) / cycleTime

    if (where_in_cycle > state.W_trimmed && state.d_on_or_off == true) {
        state.d_on_or_off = false
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TL_low)
        thermostat.setThermostatFanMode("auto")
    }

    if (where_in_cycle < state.W_trimmed && state.d_on_or_off == false) {
        state.d_on_or_off = true
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TH_high)
        thermostat.setThermostatFanMode("auto")
    }

    // Anti reset windup at 20%
    accumulator = (P_parameter * state.e0) + -0.2
    if (state.W_control < accumulator) {
        state.W_control = accumulator
    }
    accumulator = accumulator + 1.2
    if (state.W_control > accumulator) {
        state.W_control = accumulator
    }

}
