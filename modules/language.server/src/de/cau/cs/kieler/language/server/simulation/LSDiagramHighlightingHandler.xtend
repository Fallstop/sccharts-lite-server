/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 * 
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2019 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.simulation

import com.google.inject.Binder
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Module
import de.cau.cs.kieler.klighd.ViewContext
import de.cau.cs.kieler.klighd.lsp.KGraphDiagramState
import de.cau.cs.kieler.klighd.lsp.KGraphLanguageServerExtension
import de.cau.cs.kieler.language.server.ILSDiagramHighlighter
import de.cau.cs.kieler.simulation.SimulationContext
import de.cau.cs.kieler.simulation.events.SimulationControlEvent
import de.cau.cs.kieler.simulation.events.SimulationControlEvent.SimulationOperation
import de.cau.cs.kieler.simulation.ide.CentralSimulation
import de.cau.cs.kieler.simulation.ide.visualization.AbstractDiagramHighlighter
import de.cau.cs.kieler.simulation.ide.visualization.AbstractDiagramHighlightingHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Works similar to the KlighdPiccoloDiagramHighlightingHandler. After each stop, start, or step action the diagram layout is updated.
 * This includes the styles, therefore the taken transitions are marked.
 * 
 * @author sdo
 * @see de.cau.cs.kieler.simulation.ui.visualization.KlighdPiccoloDiagramHighlightingHandler
 */
class LSDiagramHighlightingHandler extends AbstractDiagramHighlightingHandler implements ILSDiagramHighlighter {

    @Inject SimulationLanguageServerExtension simulationExt

    @Inject KGraphLanguageServerExtension kgraphExt

    @Inject KGraphDiagramState diagramState

    /**
     * Set while the server replays ticks itself (a rewind): the highlighter still follows every tick,
     * but the diagram is only laid out once at the end instead of once per replayed tick.
     */
    public static volatile boolean suppressLayoutUpdates = false

    /** A simulation-triggered layout is on its way to the client. */
    static val layoutRunning = new AtomicBoolean

    /** A tick came in while a layout was running: lay out once more when it is done. */
    static volatile String layoutWanted = null
    
    static def create(Injector parentInjector, Class<? extends AbstractDiagramHighlighter> highlighter) {
        val injector = parentInjector.createChildInjector(new Module() {
            override configure(Binder binder) {
                binder.bind(AbstractDiagramHighlighter).to(highlighter)
            }
        })
        return injector.getInstance(LSDiagramHighlightingHandler)
    }
    
    @Inject
    new(AbstractDiagramHighlighter highlighter) {
        super(highlighter)
    }

    override handleUpdate(SimulationContext ctx, SimulationControlEvent e, ViewContext vc) {
        if (e.operation != SimulationOperation.START && e.operation != SimulationOperation.STOP &&
            e.operation != SimulationOperation.STEP) {
            return
        }

        try {
            // The highlighter edits the KGraph's styles. The diagram generator and the layout read them under
            // the diagram state's lock, and the diagram sent to the client shares them, serialized under the
            // diagram server's model lock, which is taken first. Unlocked, a fast run tore those reads apart.
            val server = kgraphExt.findDiagramServerForUri(simulationExt.currentlySimulatedModel)
            synchronized (server?.modelLock ?: diagramState) {
                synchronized (diagramState) {
                    switch (e.operation) {
                        case START : highlighter.initialize(ctx)
                        case STOP : highlighter.stop(ctx)
                        case STEP: highlighter.update(ctx)
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace
        } finally {
            if (!suppressLayoutUpdates) {
                requestLayout(kgraphExt, simulationExt.currentlySimulatedModel)
            }
        }
    }

    /**
     * Lays out the simulated model's diagram so it shows the latest highlighting. Ticks can come far faster
     * than layouts: each one used to queue a layout of its own, the queue grew without bound and the server
     * and the editor drowned in redraws. Now one layout runs at a time, and the ticks that came in meanwhile
     * share one more layout once it is done, which shows the state after the last of them.
     */
    static def void requestLayout(KGraphLanguageServerExtension kgraphExt, String uri) {
        if (uri === null) {
            return
        }
        layoutWanted = uri
        if (layoutRunning.compareAndSet(false, true)) {
            layoutWanted = null
            var CompletableFuture<Void> future
            try {
                future = kgraphExt.updateLayout(uri)
            } catch (Exception ex) {
                // The diagram only visualises the run: a refresh that fails must not take down the
                // start, step or stop that triggered it.
                ex.printStackTrace
                future = CompletableFuture.completedFuture(null)
            }
            future.whenComplete [ result, error |
                layoutRunning.set(false)
                val next = layoutWanted
                if (next !== null) requestLayout(kgraphExt, next)
            ]
        }
    }

    override protected ViewContext getDiagramViewContext() {
        synchronized (diagramState) {
            if (diagramState === null || simulationExt.currentlySimulatedModel === null) {
                return null
            }
            // TODO Get model of snapshots model (currently saved in different fields
            return diagramState.getKGraphContext(simulationExt.currentlySimulatedModel)
        }
    }
    
    
    override registerObserver() {
        CentralSimulation.addListener(this)
    }
    
    override unregisterObserver() {
        CentralSimulation.removeListener(this)
    }

}
