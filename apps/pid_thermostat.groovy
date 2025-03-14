/*
 *  PID Thermostat App
 *  Project URL: https://github.com/jeabraham/hubitat_pid_thermostat
 *  Initial code from Project URL: https://github.com/NelsonClark/Hubitat/tree/main/Apps/Advanced_vThermostat_V2
 *  Copyright 2025 John Abraham
 *
 *  This app requires it's child app and uses the built in virtual thermostat device driver to function, please go to the project page for more information.
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
	description: "Uses a duty-cycle calculation to turn a heater on for a while and off for a while, to attempt surf the right amount of heat to maintain a temperature without overshooting or having dramatic changes.",
	category: "Green Living",
	iconUrl: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat_V2/Advanced_vThermostat_V2-logo-small.png",
	iconX2Url: "https://raw.githubusercontent.com/NelsonClark/Hubitat/main/Apps/Advanced_vThermostat_V2/Advanced_vThermostat_V2-logo.png",
	importUrl: "https://raw.githubusercontent.com/jeabraham/hubitat_pid_thermostat/apps/pid_thermostat.groovy",
	singleInstance: true
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	return dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if (app.getInstallationState() == 'INCOMPLETE') {
            section("Hit Done to install PID Thermostat") {
        	}
        } else {
			section("<b>Create a new PID Thermostat Instance.</b>") {
				app(name: "Thermostats", appName: "PID Thermostat Child", namespace: "jeabraham", title: "Add PID Thermostat", multiple: true)
			}
		}
	}
}

def installed() {
	log.debug "Installed"
	initialize()
}

def updated() {
	log.debug "Updated"
	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "Initializing; there are ${childApps.size()} child apps installed"
	childApps.each {child ->
		log.debug "  child app: ${child.label}"
	}
}