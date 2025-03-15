# **PID Thermostat App**

**Author**: John Abraham  
**License**: [Apache License Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)  
**Project URL**: [GitHub Repository](https://github.com/jeabraham/hubitat_pid_thermostat)

---

## **Overview**

The **PID Thermostat App** is a Groovy-based smart home app for the **Hubitat** platform, that applies
a duty-cycle to a thermostat using a **Proportional-Integral-Derivative (PID) controller**. 

Some heating systems
have too much lag, or you may not them to over-respond to changes in temperature.  I have a hydronic in-floor 
heating, with hot water heating a gigantic slab of concrete.  If someone opens the door, a blast of cold 
air hits a normal thermostat, so it decides to rigourously pump water into the slab, so much that 20 minutes
after the door has closed the slab is now way too hot, and the room overshoots its temperature for an hour or more.

This app uses the well-known PID algorithm to figure out how much heating is needed as a percentage of the total capacity,
and applies it for a percentage of the time, over the course of Cycle Time, which defaults to 1200s / 20 min.  So, if you
need 15% heat, it will heat for the first 3 minutes of every 20 minutes.  If you need 50% heat, it will heat for the 
first 10 minutes of every 20 minutes.  The portion of heating is called the duty-cycle, and it's in the variable W_control.

This app is only useful if you find your heating system overreacting, overshooting or undershooting, or just want to smooth out 
how it behaves.

Although there is *no warranty* and *no liability for damages*, the App only changes
the setpoint of your real thermostat up and down. If your desired setpoint is 18C and your Temperature Threshold is 4 degrees, the app should only turn your real thermostat up to 22 when the duty-cycle is on, and turn your real thermostat down to 14 when the duty cycle is off, so regardless of what happens with the W_control and even if you badly tune your PID algorithm it *shouldn't* cause any big troubles. 

For more information, you can refer to
the [Wikipedia article on PID control](https://en.wikipedia.org/wiki/PID_controller)
and [Wikipedia article on Duty Cycle](https://en.wikipedia.org/wiki/Duty_cycle).

---

## **Features**

- Configurable **PID parameters** (`Proportional`, `Integral`, `Derivative`) to tune your controller.
- Supports a **manual setpoint** or one from a device such as a virtual thermostat.
- Adjustable temperature threshold and cycle time.
- Dynamic logging with levels (`trace`, `debug`, `info`, `warn`, `error`) for easy troubleshooting.
- Anti-reset windup control to avoid PID saturation issues.
- State management to maintain continuity of PID control across cycles.

---

## **How It Works**

The app adjusts the targeted thermostat, turning it on by setting its heating temperature to be `Temperature Threshold` above the `setpoint` to turn it on, or setting its heating temperature to be `Temperature Threshold` below the `setpoint` to turn it off.  Using the **PID control algorithm**, the app calculates a duty cycle to determine the fraction of time the thermostat should remain active versus inactive to achieve a steady state.  This duty cycle is adjusted by monitoring the difference between the target setpoint (desired temperature) and an actual measured temperature. 

### PID Parameters:
- **Proportional Gain (P):** Reacts to the magnitude of the temperature error. Higher values lead to stronger reactions.
- **Integral Gain (I):** Reacts to accumulated errors over time to eliminate longer-term temperature deviations.
- **Derivative Gain (D):** Reacts to the rate of temperature change, helping to stabilize overshooting.

The app periodically (every minute) runs a control loop for continuous adjustments.

---

## **Installation**

### Prerequisites
- **Hubitat Elevation** smart home hub.
- A compatible thermostat device connected to the Hubitat hub.
- (Optional) a hubitat device such as a virtual thermostat (supporting the heatingSetpoint attribute) or some other temperature attribute, for you to adjust the desired temperature (otherwise you can set it in the app directly)

### Using the Script
1. Download or copy the Groovy file pid_thermostat-singleapp.groovy from the [GitHub repository](https://github.com/jeabraham/hubitat_pid_thermostat).
2. Log in to your Hubitat Elevation instance.
3. Navigate to the **Apps Code** section from the Hubitat interface.
4. Click on **New App** and paste the Groovy code.
5. Save the app and click **Load** to initialize it.
6. Go to the **Apps** section, add the newly installed app, and configure the settings as required.

---

## **Configuration**

### Preferences:
During setup in Hubitat, you will be asked to configure the following:

1. **Logging Level**  
   Choose the verbosity of logs for debugging or troubleshooting. Options:
    - `trace`
    - `debug`
    - `info` (Default)
    - `warn`
    - `error`

2. **Thermostat Selection**  
   Select a thermostat device to control.

3. **Setpoint Source**  
   Specify how the setpoint is determined:
    - Use a device which has a `heatingSetpoint` (e.g. a virtual thermostat) or `temperature` attribute for dynamic setpoint adjustments.
    - Manually input a setpoint value.

4. **PID Parameters**  
   Fine-tune the behavior of the PID controller.  Default values seem to work ok for thermostats in Celsius, they are probably too high for Fahrenheit. 
    - Proportional Gain (`P`) (Default: `0.25`)
    - Integral Gain (`I`) applied to error integral in degrees * seconds (Default: `0.00007`)
    - Derivative Gain (`D`) applied to change in error in degrees per second (Default: `0.1`)
   
Note that the Wikipedia article has tuning advice https://en.wikipedia.org/wiki/Proportional–integral–derivative_controller#Loop_tuning

5. **Temperature Threshold**  
   Specify the temperature delta used to adjust the thermostat’s setpoint when cycling. (Default: `4` degrees)

6. **Cycle Time**  
   Define the time interval for the duty cycle in seconds. For example, a `1200`-second cycle will adjust the heat every 20 minutes. Since the loop currently runs every minute, this should probably not be less than 600 seconds. (Default: `1200` seconds)

---

## **Logging**

The app supports dynamic logging at different levels of detail. Based on your selected log level, the following kinds of logs will be generated:

- **trace**: Detailed execution flow, including intermediate calculations.
- **debug**: Key state changes like PID parameter updates and thermostat mode adjustments.
- **info**: Messages summarizing major events like initialization, updates, or completed cycles.
- **warn**: Warnings about incorrect configurations or potential errors.
- **error**: Critical log messages when input validation fails or app malfunctions.

Logs will appear in the Hubitat log interface for easy monitoring.

---

## **Version History**

- **V0.1**: Initial release – core functionality for PID-based thermostat adjustments.

---

## **License**

This project is licensed under the **Apache License, Version 2.0**. See the [LICENSE](http://www.apache.org/licenses/LICENSE-2.0) file for details.

---

## **Acknowledgments**

- Thanks to [NelsonClark](https://github.com/NelsonClark/Hubitat/tree/main/Apps/Advanced_vThermostat_V2) for inspiration and reference implementations of advanced virtual thermostat apps.
- Developed by **John Abraham** in 2025.

---

## **Contributing**

Contributions are welcome! If you'd like to enhance the app, feel free to fork the repository, work on the changes, and submit a pull request.

For bug reports or feature requests, open an issue on the [GitHub project page](https://github.com/jeabraham/hubitat_pid_thermostat).

---

## **Support**

If you encounter any issues or have questions about the app, please open an issue on GitHub or reach out via the Hubitat community forums.
