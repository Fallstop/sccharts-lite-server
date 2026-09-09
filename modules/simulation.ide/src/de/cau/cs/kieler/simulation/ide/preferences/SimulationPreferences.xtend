/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 *
 * Copyright 2018 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.simulation.ide.preferences

import de.cau.cs.kieler.core.properties.MapPropertyHolder
import de.cau.cs.kieler.simulation.SimulationContext
import java.util.HashMap
import java.util.Map

/**
 * Simulation preferences without the JFace preference store: the language server has no
 * preference UI, so defaults and explicit values are kept in plain maps.
 */
class SimulationPreferences extends MapPropertyHolder {

    val Map<String, Integer> defaults = new HashMap
    val Map<String, Integer> values = new HashMap

    new() {
        defaults.put(SimulationContext.REACTION_TIMEOUT_IN_SECONDS.id, SimulationContext.REACTION_TIMEOUT_IN_SECONDS.^default)
        setProperty(SimulationContext.REACTION_TIMEOUT_IN_SECONDS, getIntValue(SimulationContext.REACTION_TIMEOUT_IN_SECONDS.id))

        defaults.put(SimulationContext.MAX_HISTORY_LENGTH.id, SimulationContext.MAX_HISTORY_LENGTH.^default)
        setProperty(SimulationContext.MAX_HISTORY_LENGTH, getIntValue(SimulationContext.MAX_HISTORY_LENGTH.id))
    }

    def resetToDefault() {
        propertyMap.clear
        values.clear
    }

    def int getIntValue(String id) {
        values.getOrDefault(id, defaults.getOrDefault(id, 0))
    }

    def setValue(String id, int value) {
        defaults.put(id, value)
        setPropertyById(id, value)
    }
}
