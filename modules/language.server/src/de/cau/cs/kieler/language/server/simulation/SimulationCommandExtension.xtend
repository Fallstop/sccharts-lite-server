/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2019-2024 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.simulation

import de.cau.cs.kieler.language.server.simulation.data.AddCoSimulationParam
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.AcceptedResult
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.HistoryResult
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.RunToBreakpointParam
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.SetBreakpointsParam
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.SetWatchesParam
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.StatesParam
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.StatesResult
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.StepBackParam
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.StepBackResult
import de.cau.cs.kieler.language.server.simulation.data.LoadedTraceMessage
import de.cau.cs.kieler.language.server.simulation.data.SavedTraceMessage
import de.cau.cs.kieler.language.server.simulation.data.SimulationStartParam
import de.cau.cs.kieler.language.server.simulation.data.SimulationStepParam
import de.cau.cs.kieler.language.server.simulation.data.SimulationStoppedMessage
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment

/**
 * Interface to the LSP extension commands
 * 
 * @author sdo
 *
 */
@JsonSegment('keith/simulation')
interface SimulationCommandExtension {
    
    /**
     * Simulates a model given by uri. It is assumed that the model was compiled via a simulation CS before.
     * The client should take care that this happened.
     */
    @JsonNotification('start')
    def void start(SimulationStartParam param);
    
    /**
     * Performs a step.
     */
    @JsonNotification('step')
    def void step(SimulationStepParam param)
    
    /**
     * Stops a running simulation.
     */
    @JsonRequest('stop')
    def CompletableFuture<SimulationStoppedMessage> stop()
    
    
    @JsonNotification('addCoSimulation')
    def void addCoSimulation(AddCoSimulationParam param);
    
    /**
     * Loads the trace from the file uri given in this message.
     */
    @JsonRequest('loadTrace')
    def CompletableFuture<LoadedTraceMessage> loadTrace(String fileUri);
    
    /**
     * Saves the trace in the current simulation and notifies the client if the trace generated from the current
     * simulation context is saved successfully.
     */
    @JsonRequest('saveTrace')
    def CompletableFuture<SavedTraceMessage> saveTrace(String fileUri);

    // ---- Debugging: breakpoints, watches, history and rewinding ----

    /**
     * Replaces the breakpoints. State breakpoints pause when the named state is entered, condition
     * breakpoints when the expression holds after a tick. Each is validated; the result says which were accepted.
     */
    @JsonRequest('setBreakpoints')
    def CompletableFuture<AcceptedResult> setBreakpoints(SetBreakpointsParam param)

    /**
     * Replaces the watch expressions; every step message then carries their values.
     */
    @JsonRequest('setWatches')
    def CompletableFuture<AcceptedResult> setWatches(SetWatchesParam param)

    /**
     * Steps with the current inputs until a breakpoint fires or maxSteps ticks were executed.
     * Every tick is reported through didStep; 'pause' or 'stop' cancel the run.
     */
    @JsonNotification('runToBreakpoint')
    def void runToBreakpoint(RunToBreakpointParam param)

    /**
     * Cancels a running runToBreakpoint after the current tick.
     */
    @JsonNotification('pause')
    def void pause()

    /**
     * The data pools of the ticks so far, oldest first.
     */
    @JsonRequest('history')
    def CompletableFuture<HistoryResult> history()

    /**
     * Rewinds the simulation to the state after tick toStep (0 is the initial state) by restarting the
     * executable and replaying the recorded inputs. A didStep message with rewound=true follows on success.
     */
    @JsonRequest('stepBack')
    def CompletableFuture<StepBackResult> stepBack(StepBackParam param)

    /**
     * The states of a model with their qualified names, and which are active in a running simulation.
     */
    @JsonRequest('states')
    def CompletableFuture<StatesResult> states(StatesParam param)
}
