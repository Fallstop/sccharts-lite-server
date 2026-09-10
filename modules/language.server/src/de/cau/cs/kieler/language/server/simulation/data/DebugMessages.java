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
package de.cau.cs.kieler.language.server.simulation.data;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/** JSON payloads of the keith/simulation debugging methods (breakpoints, watches, history, rewind). */
public final class DebugMessages {
    private DebugMessages() {
    }

    /** A breakpoint as the client defines it. */
    public static final class Breakpoint {
        public String id;
        /** "state" pauses when the named state is entered, "condition" when the expression holds after a tick. */
        public String kind;
        public String state;
        public String expression;
        public boolean enabled = true;
    }

    public static final class SetBreakpointsParam {
        public List<Breakpoint> breakpoints = new ArrayList<>();
    }

    public static final class Accepted {
        public String id;
        public boolean ok;
        public String message;

        public Accepted() {
        }

        public Accepted(String id, boolean ok, String message) {
            this.id = id;
            this.ok = ok;
            this.message = message;
        }
    }

    public static final class AcceptedResult {
        public List<Accepted> accepted = new ArrayList<>();
        /** How the executable signals taken transitions: "counters" (value change), "flags" (reset each tick), or "none". */
        public String signaling;
    }

    public static final class Watch {
        public String id;
        public String expression;
    }

    public static final class SetWatchesParam {
        public List<Watch> watches = new ArrayList<>();
    }

    /** One watch value after a tick. */
    public static final class WatchValue {
        public String id;
        public Object value;
        public String error;
    }

    /** The breakpoint a tick stopped at. */
    public static final class BreakpointHit {
        public String id;
        public String kind;
        public String label;
        public int step;
    }

    /** The step message with the debugging additions; the base fields match SimulationStepMessage. */
    public static final class DebugStepMessage {
        public boolean successful;
        public String error;
        public JsonObject values;
        public int step;
        public List<WatchValue> watches;
        public BreakpointHit breakpoint;
        /** Set on the message that shows the state reached by a rewind. */
        public boolean rewound;
    }

    public static final class RunToBreakpointParam {
        public int maxSteps = 1000;
    }

    public static final class HistoryStep {
        public int step;
        public JsonObject pool;
    }

    public static final class HistoryResult {
        public List<HistoryStep> steps = new ArrayList<>();
        /** The number of the latest tick. */
        public int step;
    }

    public static final class StepBackParam {
        public int toStep;
    }

    public static final class StepBackResult {
        public boolean ok;
        public String message;
        public int step;
        public long replayMs;
    }

    public static final class StatesParam {
        public String uri;
    }

    public static final class StateInfo {
        public String name;
        public String qualified;
        public int offset = -1;
        public int length;
        public boolean initial;
        public boolean current;
    }

    public static final class StatesResult {
        public List<StateInfo> states = new ArrayList<>();
        public String message;
    }
}
