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
 *  V0.2 Fancier, with 5 point second derivative using Savitzky-Golay approximation
 *  V1.0 Seems to be working well.  Let's add a hub variable connection to display our duty cycle.  
 *  V1.1 Added pause/resume (manual, timed, indefinite, virtual buttons) and virtual thermostat mode syncing.
 *  V1.2 Measured temperature is now the average of one or more selected temperature sensors, and is pushed to the
 *       setpoint virtual thermostat every minute (even while paused).
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
    importUrl: "https://raw.githubusercontent.com/jeabraham/hubitat_pid_thermostat/main/apps/pid_thermostat-singleapp.groovy",
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    // Keep the virtual thermostat and the alternative setpoint device mutually exclusive
    def curTherm = settings.setpointThermostat?.id
    def curDev = settings.setpointDevice?.id
    if (curTherm && curDev) {
        if (curTherm != state.prevSetpointThermostat) {
            app.removeSetting("setpointDevice")
            curDev = null
        } else {
            app.removeSetting("setpointThermostat")
            curTherm = null
        }
    }
    state.prevSetpointThermostat = curTherm
    state.prevSetpointDevice = curDev
    dynamicPage(name: "mainPage", title: "PID Thermostat", install: true, uninstall: true) {
        section("Logging Level:") {
            input "logLevel", "enum", title: "Select Log Level", options: ["trace", "debug", "info", "warn", "error"], defaultValue: "info", required: true
        }
        section("Select Thermostat to control") {
            input "thermostat", "capability.thermostat", title: "Thermostat", required: true
        }
        section("Temperature Sensors:") {
            paragraph "The average of the selected sensors is used as the measured temperature. If a setpoint virtual thermostat is selected, its temperature is updated with this average every minute, even while this app is paused."
            input "tempSensors", "capability.temperatureMeasurement", title: "Temperature Sensor(s)", multiple: true, required: true
        }
        section("Select Setpoint Source:") {
            paragraph "<b>Recommended:</b> select a virtual thermostat (e.g. one created with Hubitat's \"Virtual Thermostat\" or \"Generic Component Thermostat\" driver). Its heating setpoint is used as the target, and its mode is linked to this app: when this app is paused the virtual thermostat's mode is set to <i>off</i>, setting it to <i>off</i> pauses this app indefinitely, and setting it to <i>heat</i> resumes this app."
            input "setpointThermostat", "capability.thermostat", title: "Setpoint Virtual Thermostat (recommended)", required: false, submitOnChange: true
            paragraph "<i>Alternative (not recommended):</i> pick a temperature measurement device below and enter the set point manually in this app. You really should create a virtual thermostat instead; pause/resume mode syncing is only available with a virtual thermostat."
            input "setpointDevice", "capability.sensor", title: "Alternative: Setpoint Device or Temperature Sensor", required: false, submitOnChange: true
            input "manualSetpoint", "decimal", title: "Manual Setpoint (If no device is specified)", required: false
        }
        section("PID Parameters:") {
            input "P_parameter", "decimal", title: "Proportional Gain (P)", defaultValue: 0.25
            input "I_parameter", "decimal", title: "Integral Gain (I), integral in degrees*hours", defaultValue: 0.12
            input "D_parameter", "decimal", title: "Derivative Gain (D) derivative in degrees per minute", defaultValue: 0.3
        }
        section("Adjustable Parameters:") {
            input "tempThreshold", "decimal", title: "Temperature delta applied to controlled thermostat to turn it on or off", defaultValue: 4
            input "cycleTime", "decimal", title: "What is the cycle time in seconds? E.g. 1200 will turn the heat on for a portion of the time every 20 minutes", defaultValue:  1200
        }
        section(title: "Connections") {
                input(name: "dutyCycleDevice", type: "capability.actuator", title: "Select Duty Cycle Hub Variable (display/output only)", required: false)
        }
        section("Pause Control:") {
            input "manualPauseMode", "enum", title: "Pause when set point is changed manually on the device", options: ["no": "No", "hours": "Yes, for a number of hours", "indefinitely": "Yes, indefinitely"], defaultValue: "hours", required: true, submitOnChange: true
            if (manualPauseMode != "no" && manualPauseMode != "indefinitely") {
                input "manualPauseHours", "decimal", title: "Hours to pause after a manual change", defaultValue: 8
            }
            input "pauseNowHours", "decimal", title: "Hours to pause for when pressing \"Pause now\"", defaultValue: 8
            input "createPauseDevices", "bool", title: "Create virtual buttons (Pause Now, Pause Indefinitely, Resume) for use on dashboards", defaultValue: false
            paragraph "Status: <b>${pauseStatusText()}</b>"
            input "pauseNowBtn", "button", title: "Pause now"
            input "pauseIndefBtn", "button", title: "Pause indefinitely"
            if (isPaused()) {
                input "resumeBtn", "button", title: "Resume"
            } else {
                paragraph "Resume is only available while paused."
            }
        }
        section(title: "Modes") {
            input(name: "modes", type: "mode", title: "Set for specific Mode(s)", multiple: true, required: false)
        }
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

def updateDutyCycleVariable() {
    if (dutyCycleDevice) {
        def val = (state.W_control instanceof Number) ? Math.round(state.W_control * 100) : -1
        dutyCycleDevice.setVariable(val)
    }
}

private void validateErrorHistory() {
    if (state.errors == null) {
        logMessage("warn", "Initializing error history")
        state.errors = new ArrayList<>(Collections.nCopies(state.HISTORY_SIZE, 0))
        return
    }

    int currentSize = state.errors.size()
    if (currentSize < state.HISTORY_SIZE) {
        // Extend by duplicating last entry
        def lastError = state.errors.isEmpty() ? 0 : state.errors.last()
        for (int i = currentSize; i < state.HISTORY_SIZE; i++) {
            state.errors.add(lastError)
        }
        logMessage("debug", "Extended error history from ${currentSize} to ${state.HISTORY_SIZE} entries")
    } else if (currentSize > state.HISTORY_SIZE) {
        // Reduce size by removing oldest entries
        state.errors = state.errors.subList(0, state.HISTORY_SIZE)
        logMessage("debug", "Trimmed error history from ${currentSize} to ${state.HISTORY_SIZE} entries")
    }
}

def getSetpointSource() {
    return settings.setpointThermostat ?: settings.setpointDevice
}

private void setVirtualThermostatMode(mode) {
    def dev = settings.setpointThermostat
    if (!dev) return
    try {
        if (dev.currentValue("thermostatMode") == mode) return
        if (mode == "off") dev.off() else dev.heat()
    } catch (Throwable e) {
        logMessage("warn", "Could not set mode ${mode} on ${dev}: ${e.message}")
    }
}

def modeHandler(evt) {
    logMessage("debug", "Setpoint thermostat mode changed to ${evt.value}")
    if (evt.value == "off") {
        if (!isPaused()) {
            logMessage("info", "Setpoint thermostat set to off; pausing indefinitely.")
            pauseIndefinitely()
        }
    } else if (evt.value == "heat") {
        if (isPaused()) {
            logMessage("info", "Setpoint thermostat set to heat; resuming.")
            resumeApp()
        }
    }
}

// On pause, put the controlled thermostat back at the real set point instead of leaving it at set point +/- delta
private void restoreControlledSetpoint() {
    def sp = null
    def src = getSetpointSource()
    if (src) sp = src.currentValue("heatingSetpoint")
    else sp = settings.manualSetpoint
    if (sp instanceof Number) {
        logMessage("info", "Paused: setting controlled thermostat set point to ${sp}")
        state.d_on_or_off = false
        sendSetpoint(sp)
    } else {
        logMessage("warn", "Paused, but no numeric set point available to restore on the controlled thermostat.")
    }
}

def pauseStatusText() {
    if (state.pausedIndefinitely) return "Paused indefinitely"
    if (state.pausedUntil && now() < state.pausedUntil) return "Paused until ${new Date(state.pausedUntil as Long)}"
    return "Running"
}

def isPaused() {
    if (state.pausedIndefinitely) return true
    if (state.pausedUntil) {
        if (now() < state.pausedUntil) return true
        state.remove("pausedUntil")
        logMessage("info", "Pause period ended, resuming.")
        setVirtualThermostatMode("heat")
    }
    return false
}

def pauseFor(hours, boolean restoreSetpoint = true) {
    def h = (hours instanceof Number && hours > 0) ? hours : 8
    state.pausedIndefinitely = false
    state.pausedUntil = now() + (h * 3600000L as Long)
    logMessage("info", "Paused for ${h} hours.")
    setVirtualThermostatMode("off")
    if (restoreSetpoint) restoreControlledSetpoint()
}

def pauseIndefinitely(boolean restoreSetpoint = true) {
    state.pausedIndefinitely = false
    state.remove("pausedUntil")
    state.pausedIndefinitely = true
    logMessage("info", "Paused indefinitely.")
    setVirtualThermostatMode("off")
    if (restoreSetpoint) restoreControlledSetpoint()
}

def resumeApp() {
    state.pausedIndefinitely = false
    state.remove("pausedUntil")
    logMessage("info", "Resumed.")
    setVirtualThermostatMode("heat")
}

def appButtonHandler(btn) {
    switch (btn) {
        case "pauseNowBtn": pauseFor(settings.pauseNowHours); break
        case "pauseIndefBtn": pauseIndefinitely(); break
        case "resumeBtn": resumeApp(); break
    }
}

def pauseDeviceHandler(evt) {
    switch (evt.value) {
        case "1": pauseFor(settings.pauseNowHours); break
        case "2": pauseIndefinitely(); break
        case "3": resumeApp(); break
    }
}

private void setupPauseDevices() {
    def names = ["PauseNow": "PID Pause Now", "PauseIndefinitely": "PID Pause Indefinitely", "Resume": "PID Resume"]
    names.each { key, label ->
        def dni = "pidpause-${app.id}-${key}"
        def dev = getChildDevice(dni)
        if (settings.createPauseDevices) {
            if (!dev) {
                try {
                    dev = addChildDevice("hubitat", "Virtual Button", dni, [name: label, label: label, isComponent: false])
                } catch (Throwable e) {
                    logMessage("error", "Could not create ${label}: ${e.message}")
                }
            }
            if (dev) subscribe(dev, "pushed", pauseDeviceHandler)
        } else if (dev) {
            deleteChildDevice(dni)
        }
    }
}

def subscribeSetpoint() {
    unsubscribe()
    if (thermostat) subscribe(thermostat, "heatingSetpoint", setpointHandler)
    if (settings.setpointThermostat) subscribe(settings.setpointThermostat, "thermostatMode", modeHandler)
    setupPauseDevices()
}

private void sendSetpoint(val) {
    if (!(state.recentCommands instanceof List)) state.recentCommands = []
    state.recentCommands = (state.recentCommands.findAll { now() - it.t < 600000 } + [[v: val as BigDecimal, t: now()]])
    thermostat.setHeatingSetpoint(val)
}

def setpointHandler(evt) {
    def v = evt.numberValue
    if (v == null) return
    // Ignore changes made by this app (value matches a recent command)
    def mine = (state.recentCommands instanceof List) && state.recentCommands.any {
        now() - it.t < 600000 && Math.abs(it.v - v) < 0.3
    }
    if (mine) {
        logMessage("debug", "Setpoint change to ${v} was made by this app; ignoring.")
        return
    }
    // Changes sent digitally (by other apps/rules) are not treated as manual
    if (evt.type == "digital") {
        logMessage("debug", "Setpoint change to ${v} was digital (not physical); ignoring.")
        return
    }
    if (manualPauseMode == "no" || isPaused()) return
    logMessage("info", "Manual setpoint change to ${v} detected on device; pausing.")
    if (manualPauseMode == "indefinitely") pauseIndefinitely(false) else pauseFor(settings.manualPauseHours, false)
}

def updated() {
    state.HISTORY_SIZE = 7  // Also set in initialize()
    unschedule()
    logMessage("info", "App updated. Unscheduling existing tasks and validating inputs...")

    // Validate PID parameters
    if (P_parameter == null || P_parameter <= 0) {
        logMessage("error", "Invalid Proportional Gain (P). Setting to default value of 0.25.")
        P_parameter = 0.25
    }

    if (I_parameter == null || I_parameter < 0) {
        logMessage("error", "Invalid Integral Gain (I). Setting to default value of 0.12.")
        I_parameter = 0.12
    }

    if (D_parameter == null || D_parameter < 0) {
        logMessage("error", "Invalid Derivative Gain (D). Setting to default value of 0.3.")
        D_parameter = 0.3
    }

    // Validate cycleTime
    if (cycleTime == null || cycleTime <= 0) {
        logMessage("error", "Invalid cycleTime. Setting to default value of 1200 seconds.")
        cycleTime = 1200
    }

    logMessage("info", "PID parameters successfully updated: P=${P_parameter}, I=${I_parameter}, D=${D_parameter}, cycleTime=${cycleTime}.")
    validateErrorHistory()
    if (state.d_on_or_off == null) {
        state.d_on_or_off = false
    }
    runEvery1Minute(controlLoop)
    subscribeSetpoint()
}

def initialize() {
    state.HISTORY_SIZE = 7  // Also set in updated()
    unschedule()
    state.W_control = 0.1
    state.W_trimmed = 0.1
    validateErrorHistory()
    state.d_on_or_off = false
    runEvery1Minute(controlLoop)
    subscribeSetpoint()
}

def uninstalled() {
    logMessage("trace", "Uninstalled")
    unschedule()
}

def disableApp() {
    logMessage("warn", "App is being disabled. Stopping all scheduled tasks.")
    unschedule()
}

def getAverageTemperature() {
    def vals = (settings.tempSensors ?: []).collect { it.currentValue("temperature") }.findAll { it != null }.collect { it as BigDecimal }
    if (!vals) return null
    return vals.sum() / vals.size()
}

// Runs every minute (even when paused); returns the averaged measured temperature
private updateMeasuredTemperature() {
    def avg = getAverageTemperature()
    if (avg == null) {
        logMessage("warn", "No valid temperature readings from the selected sensors.")
        return null
    }
    avg = avg.setScale(2, BigDecimal.ROUND_HALF_UP)
    def vt = settings.setpointThermostat
    if (vt) {
        try {
            def cur = vt.currentValue("temperature")
            if (cur == null || Math.abs((cur as BigDecimal) - avg) >= 0.005) {
                vt.setTemperature(avg)
                logMessage("debug", "Updated ${vt} temperature to ${avg}")
            }
        } catch (Throwable e) {
            logMessage("warn", "Could not set temperature on ${vt}: ${e.message}")
        }
    }
    return avg
}

def controlLoop() {
    logMessage("trace", "Starting control loop...")
    def Tm_avg = updateMeasuredTemperature()
    if (isPaused()) {
        logMessage("debug", "App is paused: ${pauseStatusText()}")
        return
    }
    try {
        def allowedModes = settings.modes
        if (allowedModes && !allowedModes.contains(location.mode)) {
            log.info "App paused: current mode '${location.mode}' not in allowed modes: ${allowedModes}"
            return
        } else {
            log.debug "App running: current mode '${location.mode}' is in allowed modes: ${allowedModes}"
        }
    } catch (Throwable e) {
        log.warn "Mode check failed: ${e.message}"
    }

    if (state.W_control == null) {
        logMessage("error", "state.W_control is null. Initializing to 0.1.")
        state.W_control = 0.1
    }

    def Ts_setpoint = null
    // since we are running once per minute, delta_t is 1.0 (note have to use 1.0 not 1!)
    def delta_t = 1.0

    def Tm_measured = Tm_avg

    if (Tm_measured == null || !(Tm_measured instanceof Number)) {
            logMessage("error", "Thermostat temperature is not a valid number.")
            return
    }

    logMessage("debug", "Measured temperature: ${Tm_measured}")

    def setpointDevice = getSetpointSource()
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

    if (state.errors == null) {
        logMessage("warn", "state.errors was not initialized. Initializing with default values.")
        state.errors = new ArrayList<>(Collections.nCopies(state.HISTORY_SIZE, 0))
    }

    state.errors.add(0, Ts_setpoint - Tm_measured)
    if (state.errors.size() > state.HISTORY_SIZE) {
        state.errors.removeAt(state.HISTORY_SIZE)
    }
    logMessage("trace", "Calculated error: ${state.errors[0]}")

    if (state.errors.size() < 5) {
         logMessage("error", "Not enough error states for PID calculation. Exiting control loop.")
         return
    }

    // I_parameter is in degree*hours, so we have to divide by 60.0
    def I_times_dt = I_parameter * delta_t / 60.0
    def D_divide_dt = D_parameter / delta_t

    //def A0 = P_parameter + I_times_dt + D_divide_dt
    //def A1 = -1.0 * P_parameter - (2.0 * D_divide_dt)
    //def A2 = D_divide_dt

    // Update P amount, subtract old value add new value
    def P_change = P_parameter * (state.errors[0] - state.errors[1])
    logMessage("trace", "P parameter changing W_control from ${state.W_control} by ${P_change}")
    state.W_control = state.W_control + P_change

    // Update integral, I, reset amount, add in the I
    def I_change = I_times_dt * state.errors[0]
    logMessage("trace", "I parameter ${I_times_dt} now changing W_control from ${state.W_control} by ${I_change}")
    state.W_control = state.W_control + I_change

    // estimate second derivative using Savitsky-Golay smoothed estimate
    def secondDerivSmoothed = (
     2  * state.errors[0] +
    -27 * state.errors[1] +
    270 * state.errors[2] +
   -490 * state.errors[3] +
    270 * state.errors[4] +
    -27 * state.errors[5] +
     2  * state.errors[6]
    ) / (180 * delta_t * delta_t)

    def D_influence = D_parameter * secondDerivSmoothed * delta_t
    logMessage("trace", "Calculated 2nd derivative as ${secondDerivSmoothed} degrees/m^2 from ${state.errors}, influence on W is ${D_influence}")

    // D parameter is influence of first derivative on W, so change in W is the second derivative
    state.W_control = state.W_control + D_influence

    //state.W_control = state.W_control + (A0 * state.errors[0]) + (A1 * state.errors[1]) + (A2 * state.errors[2])

    logMessage("trace", "Calculated duty cycle W_control: ${state.W_control}")

    if (state.W_control > 0.9) {
        state.W_trimmed = 1.0
    } else if (state.W_control < 0.1) {
        state.W_trimmed = 0.0
    } else {
        state.W_trimmed = state.W_control
    }
    
    def TH_high = Ts_setpoint + tempThreshold
    def TL_low = Ts_setpoint - tempThreshold

    // TODO check against boundaries 
	//def maxSetpoint = thermostat.currentValue("heatingSetpointRangeHigh") ?: 26.0
	//def minSetpoint = thermostat.currentValue("heatingSetpointRangeLow") ?: 10.0

	//TH_high = Math.min(TH_high, maxSetpoint)
	//TL_low  = Math.max(TL_low, minSetpoint)

    def t_time = now() / 1000 // Current time in seconds
    def where_in_cycle = (t_time % cycleTime) / cycleTime

    def actualSetpoint = thermostat.currentHeatingSetpoint
    def hysteresis = 0.5  // Degrees C tolerance to avoid reacting to small noise

    // OFF Logic
    // Normal Case: duty cycle says "off" (we're past the 'on' portion) and we believe it's currently on
    // Recovery Case: we believe it's OFF, but actual setpoint is still HIGH → someone changed it
    if ((where_in_cycle > state.W_trimmed && state.d_on_or_off == true) ||               // Normal turn-off
        (state.d_on_or_off == false && actualSetpoint > TL_low + hysteresis)) {         // Recovery turn-off

        logMessage("info", "Turning OFF thermostat: (W_trimmed=${state.W_trimmed}, where_in_cycle=${where_in_cycle})")
        state.d_on_or_off = false
        // perhaps sending commands too quickly was confusing it, let's just send one. 
        //thermostat.setThermostatMode("heat")
        sendSetpoint(TL_low)
        //thermostat.setThermostatFanMode("auto")
    }

    // ON Logic
    // Normal Case: duty cycle says "on" (we're inside the 'on' portion) and we believe it's currently off
    // Recovery Case: we believe it's ON, but actual setpoint is still LOW → someone changed it
    if ((where_in_cycle < state.W_trimmed && state.d_on_or_off == false) ||             // Normal turn-on
        (state.d_on_or_off == true && actualSetpoint < TH_high - hysteresis)) {         // Recovery turn-on

        logMessage("info", "Turning ON thermostat: (W_trimmed=${state.W_trimmed}, where_in_cycle=${where_in_cycle})")
        state.d_on_or_off = true
        // perhaps sending commands too quickly was confusing it, let's just send one. 
        //thermostat.setThermostatMode("heat")
        sendSetpoint(TH_high)
        //thermostat.setThermostatFanMode("auto")
    }

    // Anti reset windup at 20%
    def test = P_parameter * state.errors[0] - 0.2 - Math.abs(D_influence)
    if (state.W_control < test) {
        logMessage("info", "Anti reset winddown, changing W control from ${state.W_control} to ${test}")
        state.W_control = test
    }
    test = P_parameter * state.errors[0] + 1.2 + Math.abs(D_influence)
    if (state.W_control > test) {
        logMessage("info", "Anti reset windup, changing W control from ${state.W_control} to ${test}")
        state.W_control = test
    }
    updateDutyCycleVariable()
}
