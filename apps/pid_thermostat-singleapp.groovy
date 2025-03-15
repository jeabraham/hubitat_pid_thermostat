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
    section("PID Parameters:") {
        input "P_parameter", "decimal", title: "Proportional Gain (P)", defaultValue: 0.25
        input "I_parameter", "decimal", title: "Integral Gain (I)", defaultValue: 0.00007
        input "D_parameter", "decimal", title: "Derivative Gain (D)", defaultValue: 0.1
        input "delta_t", "number", title: "Update Interval (Seconds)", defaultValue: 60
    }
    section("Setpoint and Threshold:") {
        input "tempSetpoint", "decimal", title: "Setpoint Temperature", defaultValue: 18.5
        input "tempThreshold", "decimal", title: "Temperature Delta Threshold", defaultValue: 4
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
    state.e0 = 0
    state.e1 = 0
    state.e2 = 0
    controlLoop()
}

def uninstalled() {
    logger("trace", "< uninstalled ---------------")
}

def controlLoop() {
    if (state.is_running) return
    state.is_running = true

    def Tm_measured = thermostat.currentTemperature
    def Ts_setpoint = tempSetpoint

    state.e0 = Ts_setpoint - Tm_measured
    state.e2 = state.e1
    state.e1 = state.e0

    def I_times_dt = I_parameter * delta_t
    def D_divide_dt = D_parameter / delta_t

    def A0 = P_parameter + I_times_dt + D_divide_dt
    def A1 = -1.0 * P_parameter - (2.0 * D_divide_dt)
    def A2 = D_divide_dt

    def W_control = state.W_control + (A0 * state.e0) + (A1 * state.e1) + (A2 * state.e2)
    state.W_control = Math.max(0.1, Math.min(0.9, W_control))

    def TH_high = Ts_setpoint + tempThreshold
    def TL_low = Ts_setpoint - tempThreshold

    if (state.W_control < 0.1) {
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TL_low)
    } else if (state.W_control > 0.9) {
        thermostat.setThermostatMode("heat")
        thermostat.setHeatingSetpoint(TH_high)
    }

    state.is_running = false
}
