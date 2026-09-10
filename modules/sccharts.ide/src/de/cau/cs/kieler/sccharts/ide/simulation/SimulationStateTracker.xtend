/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 * 
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2026 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.sccharts.ide.simulation

import com.google.gson.JsonArray
import de.cau.cs.kieler.kicool.compilation.CompilationContext
import de.cau.cs.kieler.kicool.ide.klighd.models.ModelChain
import de.cau.cs.kieler.sccharts.Region
import de.cau.cs.kieler.sccharts.SCCharts
import de.cau.cs.kieler.sccharts.State
import de.cau.cs.kieler.sccharts.Transition
import de.cau.cs.kieler.sccharts.extensions.SCChartsStateExtensions
import de.cau.cs.kieler.sccharts.iterators.StateIterator
import de.cau.cs.kieler.sccharts.processors.TakenTransitionSignaling
import de.cau.cs.kieler.simulation.SimulationContext
import java.util.ArrayList
import java.util.List
import java.util.Set
import org.eclipse.xtend.lib.annotations.Accessors

/**
 * Follows the control flow of a simulated SCChart tick by tick, from the taken-transition signaling
 * the simulation systems compile in: after every tick it knows which states the model rests in and
 * which of them were entered during that tick. This is the same computation the diagram highlighter
 * does for its colouring, kept here as data so that breakpoints can pause on a state entry.
 * 
 * Requires a simulation compiled with taken transition signaling (every {@code tts} system).
 */
class SimulationStateTracker {

    extension SCChartsStateExtensions = new SCChartsStateExtensions

    /** The root state of the simulated SCChart, or null when the model is not an SCChart. */
    @Accessors(PUBLIC_GETTER)
    val State rootState

    val List<Transition> transitions

    /**
     * Whether the transition array holds running counters (reset once, on entry of the root state) or
     * flags that the root state's during action clears every tick. Read from the environment of the
     * processor that generated the array, so that it is the mode the executable was built with.
     */
    @Accessors(PUBLIC_GETTER)
    val boolean valueChangeSignaling

    var List<Integer> lastTakenTransitionValues = newLinkedList

    /** The states the control flow rests in after the last tick. */
    @Accessors(PUBLIC_GETTER)
    var List<State> currentStates = null

    /** States that were entered during the last tick (including re-entered ones). */
    @Accessors(PUBLIC_GETTER)
    var Set<State> enteredStates = newLinkedHashSet

    /** Transitions taken during the last tick. */
    @Accessors(PUBLIC_GETTER)
    var List<Transition> traversedTransitions = newArrayList

    new(SimulationContext ctx) {
        val compileCtx = ctx.sourceCompilationContext
        var State root = null
        if (compileCtx !== null) {
            root = rootOf(compileCtx.originalModel)
        }
        rootState = root
        transitions = if (root === null) newArrayList else TakenTransitionSignaling.getTransitions(root)
        valueChangeSignaling = compileCtx !== null && compileCtx.isValueChangeSignaling
    }

    /**
     * The signaling mode of the compiled executable. The processor's own environment is authoritative
     * (it is what the processor read when it generated the array); the final environment only inherits it.
     * Without any environment the processor's default applies.
     */
    private static def boolean isValueChangeSignaling(CompilationContext compileCtx) {
        val processor = compileCtx.processorInstancesSequence?.findFirst[
            TakenTransitionSignaling.ID == id
        ]
        val environment = if (processor !== null && processor.environment !== null) processor.environment
            else compileCtx.result
        if (environment === null) {
            return TakenTransitionSignaling.USE_VALUE_CHANGE_SIGNALING.^default
        }
        return environment.getProperty(TakenTransitionSignaling.USE_VALUE_CHANGE_SIGNALING)
            ?: TakenTransitionSignaling.USE_VALUE_CHANGE_SIGNALING.^default
    }

    static def State rootOf(Object model) {
        switch model {
            SCCharts: return model.rootStates.head
            ModelChain: return (model.models.findFirst[it instanceof SCCharts] as SCCharts)?.rootStates?.head
            default: return null
        }
    }

    def isSupported() {
        rootState !== null
    }

    /** Forgets everything; the next update starts from the initial states again. */
    def reset() {
        currentStates = null
        lastTakenTransitionValues = newLinkedList
        enteredStates = newLinkedHashSet
        traversedTransitions = newArrayList
    }

    /**
     * Consumes the data pool of the tick that just finished.
     * @return the states entered in this tick
     */
    def Set<State> update(SimulationContext ctx) {
        enteredStates = newLinkedHashSet
        traversedTransitions = newArrayList
        if (rootState === null || ctx.dataPool === null) {
            return enteredStates
        }
        val transitionArrayVariable = ctx.dataPool.entries.get(TakenTransitionSignaling.transitionArrayName)
        val transitionArray = if (transitionArrayVariable === null || !transitionArrayVariable.rawValue.isJsonArray)
                new JsonArray
            else
                transitionArrayVariable.rawValue.asJsonArray

        val newLastTakenTransitionValues = <Integer>newLinkedList
        var index = 0
        for (element : transitionArray) {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
                val value = element.asInt
                val lastValue = if (lastTakenTransitionValues.size > index) lastTakenTransitionValues.get(index) else 0
                // Counters only grow between resets; a flag is set for the tick it was taken in.
                val taken = if (valueChangeSignaling) value > lastValue || (value != 0 && value < lastValue) else value != 0
                if (taken && index < transitions.size) {
                    traversedTransitions.add(transitions.get(index))
                }
                newLastTakenTransitionValues.add(value)
            }
            index++
        }
        if (valueChangeSignaling) {
            lastTakenTransitionValues = newLastTakenTransitionValues
        }

        val first = currentStates === null
        if (first) {
            currentStates = newArrayList
            for (state : rootState.initialStates) {
                currentStates.enterState(state)
            }
            // The initial states are entered when the simulation starts, which is before the first tick.
        }
        val before = newLinkedHashSet(currentStates)
        currentStates = calculateNewCurrentStates(currentStates, traversedTransitions)
        for (state : currentStates) {
            if (!before.contains(state)) {
                enteredStates.add(state)
            }
        }
        // Every target of a taken transition was entered, also a state that was left again within the
        // same tick (a self loop, or a final state whose parent terminated immediately) and the
        // initial states a re-entered state activated.
        for (transition : traversedTransitions) {
            val target = transition.targetState
            enteredStates.add(target)
            if (currentStates.contains(target)) {
                for (child : StateIterator.sccAllContainedStates(target).toIterable) {
                    if (currentStates.contains(child)) enteredStates.add(child)
                }
            }
        }
        return enteredStates
    }

    private def List<State> calculateNewCurrentStates(List<State> lastCurrentStates, List<Transition> takenTransitions) {
        val newCurrentStates = <State>newArrayList
        val outgoingTransitionsForState = <State, List<Transition>>newHashMap
        for (trans : takenTransitions) {
            val list = outgoingTransitionsForState.getOrDefault(trans.sourceState, newArrayList)
            list.add(trans)
            outgoingTransitionsForState.put(trans.sourceState, list)
        }
        val List<State> states = new ArrayList(lastCurrentStates)
        while (!states.isNullOrEmpty) {
            val state = states.get(0)
            val outgoing = outgoingTransitionsForState.getOrDefault(state, newArrayList)
            if (outgoing.size == 0) {
                newCurrentStates.add(state)
                states.remove(state)
            } else if (outgoing.size == 1) {
                val transition = outgoing.get(0)
                states.leaveState(state)
                states.enterState(transition.targetState)
                outgoing.remove(transition)
            } else {
                // Ambiguous control flow: keep what is known and stop.
                newCurrentStates.addAll(states)
                return newCurrentStates
            }
        }
        return newCurrentStates
    }

    private def void leaveState(List<State> states, State state) {
        states.remove(state)
        states.removeAll(StateIterator.sccAllContainedStates(state).toList)
    }

    private def void enterState(List<State> states, State state) {
        if (!states.contains(state)) states.add(state)
        for (initialState : state.initialStates ?: emptyList) {
            enterState(states, initialState)
        }
    }

    /** All states of the simulated model with their qualified names, in document order. */
    def List<Pair<String, State>> allStates() {
        val result = <Pair<String, State>>newArrayList
        if (rootState !== null) {
            result.add(rootState.qualifiedName -> rootState)
            for (state : StateIterator.sccAllContainedStates(rootState).toIterable) {
                result.add(state.qualifiedName -> state)
            }
        }
        return result
    }

    /** Names of the named states and regions from the root down, joined with dots. */
    static def String qualifiedName(State state) {
        val parts = <String>newLinkedList
        var Object current = state
        while (current !== null) {
            switch current {
                State: if (!current.name.nullOrEmpty) parts.addFirst(current.name)
                Region: if (!current.name.nullOrEmpty) parts.addFirst(current.name)
            }
            current = (current as org.eclipse.emf.ecore.EObject).eContainer
        }
        return parts.join(".")
    }

    /** True when a name as written by the user ("Done", "Counting.Done", "Root.Counting.Done") denotes this qualified name. */
    static def boolean matches(String qualifiedName, String written) {
        if (written.nullOrEmpty) return false
        if (qualifiedName == written) return true
        return qualifiedName.endsWith("." + written)
    }
}
