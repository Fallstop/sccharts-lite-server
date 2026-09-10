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
package de.cau.cs.kieler.kicool.compilation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serialises model compilations in one JVM. The SCG extensions keep per-injector caches that two
 * compilations running at once corrupt (a scheduling block looked up by one compilation while the other
 * rebuilds the cache comes back null), so a user compile and the live analysis of the same document must
 * never overlap. A user compile also asks background analyses to stop first, so it never waits for one.
 */
public final class CompileGate {
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static final List<Runnable> PREEMPTORS = new CopyOnWriteArrayList<>();

    private CompileGate() {}

    /** Registers a hook that cancels a background compilation when a foreground one is requested. */
    public static void addPreemptor(Runnable preemptor) { PREEMPTORS.add(preemptor); }

    /** Asks every background compilation to stop; they release the gate between processors. */
    public static void preempt() {
        for (Runnable preemptor : PREEMPTORS) {
            try { preemptor.run(); } catch (RuntimeException e) { /* a finished analysis has nothing to cancel */ }
        }
    }

    public static void lock() { LOCK.lock(); }
    public static void unlock() { LOCK.unlock(); }
    public static boolean isLocked() { return LOCK.isLocked(); }
}
