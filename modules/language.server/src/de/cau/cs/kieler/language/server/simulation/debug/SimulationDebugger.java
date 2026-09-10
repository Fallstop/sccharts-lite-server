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
package de.cau.cs.kieler.language.server.simulation.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.Accepted;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.AcceptedResult;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.BreakpointHit;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.HistoryResult;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.HistoryStep;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.SetBreakpointsParam;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.SetWatchesParam;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.StateInfo;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.StatesResult;
import de.cau.cs.kieler.language.server.simulation.data.DebugMessages.WatchValue;
import de.cau.cs.kieler.sccharts.State;
import de.cau.cs.kieler.sccharts.ide.simulation.SimulationStateTracker;
import de.cau.cs.kieler.simulation.DataPool;
import de.cau.cs.kieler.simulation.SimulationContext;
import de.cau.cs.kieler.simulation.internal.processor.ReadTrace;
import de.cau.cs.kieler.kicool.ProcessorGroup;
import de.cau.cs.kieler.kicool.ProcessorEntry;
import de.cau.cs.kieler.kicool.ProcessorReference;

/**
 * Breakpoints, watch expressions and the input record of the running simulation. One instance per
 * language server; {@link #attach} binds it to the simulation that was just started.
 *
 * Everything here is evaluated after a tick, on the data pool the tick produced: a condition
 * breakpoint fires when its expression holds, a state breakpoint when the control flow entered the
 * named state during the tick. Watches are evaluated on every tick and travel with the step message.
 */
public final class SimulationDebugger {

    /** Bound on the pools kept for history and rewinding; the default of 100 forgets long runs. */
    public static final int HISTORY_LENGTH = 100000;

    private static final class Breakpoint {
        String id;
        String kind;
        String state;
        String expression;
        boolean enabled;
        DebugExpression condition;
        /** Qualified names of the states a state breakpoint stands for. */
        List<String> states = new ArrayList<>();
    }

    private static final class Watch {
        String id;
        String expression;
        DebugExpression compiled;
        String error;
    }

    /** What a tick produced for the client. */
    public static final class TickInfo {
        public final List<WatchValue> watches = new ArrayList<>();
        public BreakpointHit breakpoint;
    }

    private final List<Breakpoint> breakpoints = new ArrayList<>();
    private final List<Watch> watches = new ArrayList<>();
    private final List<JsonObject> recordedInputs = new ArrayList<>();
    private SimulationContext context;
    private SimulationStateTracker tracker;
    /** The values before the first tick, so that pre() has an answer on tick 1. */
    private JsonObject initialPool;

    /** Set while a rewind or run-to-breakpoint drives the simulation from the server side. */
    private volatile boolean replaying;
    private volatile CountDownLatch tickLatch;
    private volatile boolean cancelRun;

    // ---------------------------------------------------------------------------------------------
    // Lifecycle

    /** Binds the debugger to a simulation about to start; keeps breakpoints and watches. */
    public synchronized void attach(SimulationContext ctx) {
        this.context = ctx;
        this.tracker = new SimulationStateTracker(ctx);
        this.recordedInputs.clear();
        this.replaying = false;
        this.cancelRun = false;
        ctx.getStartEnvironment().setProperty(SimulationContext.MAX_HISTORY_LENGTH, HISTORY_LENGTH);
        for (Breakpoint breakpoint : breakpoints) resolveStates(breakpoint);
    }

    /** The executable reported its initial values (also after a restart for a rewind). */
    public synchronized void started(SimulationContext ctx) {
        if (ctx != context || ctx.getDataPool() == null || ctx.getDataPool().getPool() == null) return;
        initialPool = JsonParser.parseString(ctx.getDataPool().getPool().toString()).getAsJsonObject();
    }

    public synchronized void detach() {
        this.context = null;
        this.tracker = null;
        this.recordedInputs.clear();
        this.replaying = false;
    }

    public boolean isAttached(SimulationContext ctx) {
        return ctx != null && ctx == context;
    }

    public boolean isReplaying() {
        return replaying;
    }

    public void setReplaying(boolean replaying) {
        this.replaying = replaying;
    }

    /** The inputs the client sent for tick {@code step} (1-based), remembered for rewinding. */
    public synchronized void recordInputs(int step, JsonObject values) {
        while (recordedInputs.size() < step - 1) recordedInputs.add(null);
        JsonObject copy = values == null ? null : JsonParser.parseString(values.toString()).getAsJsonObject();
        if (recordedInputs.size() >= step) recordedInputs.set(step - 1, copy);
        else recordedInputs.add(copy);
    }

    public synchronized JsonObject recordedInputs(int step) {
        return step >= 1 && step <= recordedInputs.size() ? recordedInputs.get(step - 1) : null;
    }

    public synchronized void truncateInputs(int step) {
        while (recordedInputs.size() > step) recordedInputs.remove(recordedInputs.size() - 1);
    }

    /** Whether inputs come from a loaded trace rather than the client, which a rewind cannot replay. */
    public static boolean drivenByTrace(SimulationContext ctx) {
        ProcessorEntry root = ctx.getSystem().getProcessors();
        if (root instanceof ProcessorGroup) {
            for (ProcessorEntry entry : ((ProcessorGroup) root).getProcessors()) {
                if (entry instanceof ProcessorReference && ReadTrace.ID.equals(((ProcessorReference) entry).getId())) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Ticks

    /** Called after every tick of the attached simulation, on the pool it produced. */
    public synchronized TickInfo tick(SimulationContext ctx, int step) {
        TickInfo info = new TickInfo();
        if (ctx != context) return info;
        java.util.Set<State> entered = tracker == null ? Collections.emptySet() : tracker.update(ctx);
        DebugExpression.Scope scope = scope(ctx);
        for (Watch watch : watches) {
            WatchValue value = new WatchValue();
            value.id = watch.id;
            if (watch.compiled == null) {
                value.error = watch.error;
            } else {
                try {
                    value.value = plain(watch.compiled.evaluate(scope));
                } catch (DebugExpression.EvaluationException e) {
                    value.error = e.getMessage();
                }
            }
            info.watches.add(value);
        }
        for (Breakpoint breakpoint : breakpoints) {
            if (!breakpoint.enabled) continue;
            if ("state".equals(breakpoint.kind)) {
                for (State state : entered) {
                    String qualified = SimulationStateTracker.qualifiedName(state);
                    if (breakpoint.states.contains(qualified)) {
                        info.breakpoint = hit(breakpoint, "Entered state " + qualified, step);
                        return info;
                    }
                }
            } else if (breakpoint.condition != null) {
                try {
                    if (breakpoint.condition.evaluateCondition(scope)) {
                        info.breakpoint = hit(breakpoint, breakpoint.expression + " holds", step);
                        return info;
                    }
                } catch (DebugExpression.EvaluationException e) {
                    // A condition that cannot be evaluated on this tick (pre() before the first tick) does not fire.
                }
            }
        }
        return info;
    }

    private static BreakpointHit hit(Breakpoint breakpoint, String label, int step) {
        BreakpointHit hit = new BreakpointHit();
        hit.id = breakpoint.id;
        hit.kind = breakpoint.kind;
        hit.label = label;
        hit.step = step;
        return hit;
    }

    /** Resets the control-flow tracking, for a simulation restarted from tick 0. */
    public synchronized void resetTracking() {
        if (tracker != null) tracker.reset();
    }

    /** Waits for the next tick notification while the server drives the simulation. */
    public CountDownLatch armTick() {
        CountDownLatch latch = new CountDownLatch(1);
        tickLatch = latch;
        return latch;
    }

    public void tickArrived() {
        CountDownLatch latch = tickLatch;
        if (latch != null) latch.countDown();
    }

    public static boolean await(CountDownLatch latch, long seconds) {
        try {
            return latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void cancelRun() {
        cancelRun = true;
    }

    public boolean runCancelled() {
        return cancelRun;
    }

    public void beginRun() {
        cancelRun = false;
    }

    // ---------------------------------------------------------------------------------------------
    // Breakpoints and watches

    public synchronized AcceptedResult setBreakpoints(SetBreakpointsParam param) {
        AcceptedResult result = new AcceptedResult();
        result.signaling = tracker == null || !tracker.isSupported() ? "none" : tracker.isValueChangeSignaling() ? "counters" : "flags";
        breakpoints.clear();
        if (param == null || param.breakpoints == null) return result;
        for (de.cau.cs.kieler.language.server.simulation.data.DebugMessages.Breakpoint given : param.breakpoints) {
            Breakpoint breakpoint = new Breakpoint();
            breakpoint.id = given.id;
            breakpoint.kind = given.kind == null ? "condition" : given.kind;
            breakpoint.state = given.state;
            breakpoint.expression = given.expression;
            breakpoint.enabled = given.enabled;
            Accepted accepted = new Accepted(given.id, true, null);
            if ("state".equals(breakpoint.kind)) {
                if (breakpoint.state == null || breakpoint.state.trim().isEmpty()) {
                    accepted.ok = false;
                    accepted.message = "A state breakpoint needs a state name.";
                } else {
                    breakpoint.state = breakpoint.state.trim();
                    accepted.message = resolveStates(breakpoint);
                    accepted.ok = tracker == null || !tracker.isSupported() || !breakpoint.states.isEmpty();
                    if (tracker != null && tracker.isSupported() && breakpoint.states.isEmpty()) {
                        accepted.message = "No state named '" + breakpoint.state + "' in the simulated model.";
                    }
                }
            } else {
                try {
                    breakpoint.condition = DebugExpression.parse(breakpoint.expression);
                    accepted.message = checkIdentifiers(breakpoint.condition);
                    accepted.ok = accepted.message == null;
                } catch (DebugExpression.EvaluationException e) {
                    accepted.ok = false;
                    accepted.message = e.getMessage();
                }
            }
            if (accepted.ok) breakpoints.add(breakpoint);
            result.accepted.add(accepted);
        }
        return result;
    }

    public synchronized AcceptedResult setWatches(SetWatchesParam param) {
        AcceptedResult result = new AcceptedResult();
        watches.clear();
        if (param == null || param.watches == null) return result;
        for (de.cau.cs.kieler.language.server.simulation.data.DebugMessages.Watch given : param.watches) {
            Watch watch = new Watch();
            watch.id = given.id;
            watch.expression = given.expression;
            Accepted accepted = new Accepted(given.id, true, null);
            try {
                watch.compiled = DebugExpression.parse(given.expression);
                String problem = checkIdentifiers(watch.compiled);
                if (problem != null) {
                    accepted.ok = false;
                    accepted.message = problem;
                    watch.error = problem;
                    watch.compiled = null;
                }
            } catch (DebugExpression.EvaluationException e) {
                accepted.ok = false;
                accepted.message = e.getMessage();
                watch.error = e.getMessage();
            }
            // Broken watches stay in the list so that the client sees the error next to the expression.
            watches.add(watch);
            result.accepted.add(accepted);
        }
        return result;
    }

    /** Current values of all watches, for a message sent outside a tick (after a rewind). */
    public synchronized List<WatchValue> currentWatches(SimulationContext ctx) {
        List<WatchValue> values = new ArrayList<>();
        DebugExpression.Scope scope = scope(ctx);
        for (Watch watch : watches) {
            WatchValue value = new WatchValue();
            value.id = watch.id;
            if (watch.compiled == null) {
                value.error = watch.error;
            } else {
                try {
                    value.value = plain(watch.compiled.evaluate(scope));
                } catch (DebugExpression.EvaluationException e) {
                    value.error = e.getMessage();
                }
            }
            values.add(value);
        }
        return values;
    }

    private String checkIdentifiers(DebugExpression expression) {
        if (context == null || context.getDataPool() == null) return null;
        JsonObject pool = context.getDataPool().getPool();
        if (pool == null || pool.size() == 0) return null;
        List<String> unknown = new ArrayList<>();
        for (String name : expression.identifiers()) {
            if (DebugExpression.resolveKey(pool, name) == null) unknown.add(name);
        }
        if (unknown.isEmpty()) return null;
        return "Unknown variable" + (unknown.size() > 1 ? "s " : " ") + String.join(", ", unknown) + ".";
    }

    /** Fills the qualified names a state breakpoint matches; returns a note about ambiguity, or null. */
    private String resolveStates(Breakpoint breakpoint) {
        breakpoint.states.clear();
        if (tracker == null || !tracker.isSupported() || breakpoint.state == null) return null;
        for (org.eclipse.xtext.xbase.lib.Pair<String, State> entry : tracker.allStates()) {
            if (SimulationStateTracker.matches(entry.getKey(), breakpoint.state)) breakpoint.states.add(entry.getKey());
        }
        if (breakpoint.states.size() > 1) {
            return "'" + breakpoint.state + "' matches " + breakpoint.states.size() + " states (" + String.join(", ", breakpoint.states)
                + "); the breakpoint pauses on any of them. Use a dotted name to pick one.";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // History and states

    public synchronized HistoryResult history(SimulationContext ctx, int latestStep) {
        HistoryResult result = new HistoryResult();
        result.step = latestStep;
        if (ctx == null) return result;
        List<DataPool> pools = new ArrayList<>();
        for (Iterator<DataPool> it = ctx.getHistory().reverseIterator(); it.hasNext();) pools.add(it.next());
        int step = latestStep - pools.size() + 1;
        for (DataPool pool : pools) {
            HistoryStep entry = new HistoryStep();
            entry.step = step++;
            entry.pool = pool.getPool();
            result.steps.add(entry);
        }
        return result;
    }

    /** All states of a model, marking the ones the attached simulation currently rests in. */
    public synchronized StatesResult states(Object model) {
        StatesResult result = new StatesResult();
        State root = SimulationStateTracker.rootOf(model);
        if (root == null) {
            result.message = "The model is not an SCChart.";
            return result;
        }
        List<State> current = tracker != null && tracker.getRootState() == root && tracker.getCurrentStates() != null
            ? tracker.getCurrentStates() : Collections.emptyList();
        addState(result, root, current, true);
        for (Iterator<State> it = de.cau.cs.kieler.sccharts.iterators.StateIterator.sccAllContainedStates(root); it.hasNext();) {
            State state = it.next();
            addState(result, state, current, false);
        }
        return result;
    }

    private static void addState(StatesResult result, State state, List<State> current, boolean root) {
        StateInfo info = new StateInfo();
        info.name = state.getName();
        info.qualified = SimulationStateTracker.qualifiedName(state);
        info.initial = state.isInitial();
        info.current = current.contains(state);
        // Point at the name, so that a click lands on the state rather than on its whole body.
        org.eclipse.emf.ecore.EStructuralFeature name = state.eClass().getEStructuralFeature("name");
        List<INode> nodes = name == null ? Collections.emptyList() : NodeModelUtils.findNodesForFeature(state, name);
        INode node = nodes.isEmpty() ? NodeModelUtils.findActualNodeFor(state) : nodes.get(0);
        if (node != null) {
            info.offset = node.getOffset();
            info.length = nodes.isEmpty() && root ? 0 : node.getLength();
        }
        result.states.add(info);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers

    private DebugExpression.Scope scope(SimulationContext ctx) {
        JsonObject current = ctx.getDataPool() == null ? new JsonObject() : ctx.getDataPool().getPool();
        JsonObject previous = ctx.getHistory().length() == 1 ? initialPool : null;
        if (ctx.getHistory().length() > 1) {
            // history[0] is the pool of the tick that just finished.
            Iterator<DataPool> it = ctx.getHistory().iterator();
            it.next();
            previous = it.next().getPool();
        }
        return DebugExpression.scope(current, previous);
    }

    /** JSON to the plain Java value Gson serialises the same way (numbers stay numbers). */
    private static Object plain(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isString()) return primitive.getAsString();
            Number number = primitive.getAsNumber();
            double d = number.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && !number.toString().contains(".")) return number.longValue();
            return d;
        }
        return value;
    }
}
