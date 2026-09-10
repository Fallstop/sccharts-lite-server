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
package de.cau.cs.kieler.language.server.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.ILanguageServerExtension;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Singleton;

import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;
import de.cau.cs.kieler.kicool.compilation.CompilationContext;
import de.cau.cs.kieler.kicool.compilation.Compile;
import de.cau.cs.kieler.kicool.compilation.CompileGate;
import de.cau.cs.kieler.kicool.compilation.Processor;
import de.cau.cs.kieler.kicool.deploy.ProjectInfrastructure;
import de.cau.cs.kieler.kicool.environments.Environment;
import de.cau.cs.kieler.language.server.ILanguageClientProvider;
import de.cau.cs.kieler.language.server.KeithLanguageClient;
import de.cau.cs.kieler.language.server.kicool.data.SnapshotDescription;

/**
 * Analyses open SCCharts documents while they are edited. After Xtext has rebuilt a changed document the
 * extension waits for the edits to settle, compiles a copy of the model through the front half of the netlist
 * chain (as far as the scheduler, no code generation) on its own thread and publishes the located issues as
 * {@code keith/diagnostics/live}. A newer edit cancels an analysis that is still running; its result is dropped.
 */
@Singleton
public class LiveDiagnosticsExtension implements ILanguageServerExtension, LiveDiagnosticsCommandExtension, ILanguageClientProvider {

    /** The kico system that runs SCCharts through dependency analysis, loop analysis and the scheduler. */
    public static final String SYSTEM = "de.cau.cs.kieler.sccharts.live.analysis";

    private static final String EXTENSION = "sctx";

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "keith-live-diagnostics");
        thread.setDaemon(true);
        return thread;
    });

    /** The analysis waiting for the debounce, per document. */
    private final Map<String, ScheduledFuture<?>> pending = new HashMap<>();

    /** Counts the edits seen per document; an analysis only publishes if no edit arrived while it ran. */
    private final Map<String, Long> generations = new HashMap<>();

    private volatile boolean enabled = true;
    private volatile long debounceMs = 400;

    /** The compilation currently running on the worker, cancelled when superseded. */
    private volatile CompilationContext running;

    private ILanguageServerAccess access;
    private KeithLanguageClient client;

    public LiveDiagnosticsExtension() {
        // A user compile must not wait behind a background analysis: it cancels the running one first.
        CompileGate.addPreemptor(this::cancelRunning);
    }

    @Override
    public void initialize(ILanguageServerAccess access) {
        this.access = access;
        access.addBuildListener(this::afterBuild);
    }

    private void afterBuild(List<IResourceDescription.Delta> deltas) {
        for (IResourceDescription.Delta delta : deltas) {
            URI uri = delta.getUri();
            if (uri == null || !EXTENSION.equals(uri.fileExtension()) || !uri.isFile()) continue;
            if (delta.getNew() == null) {
                clear(uri.toString(), "closed");
            } else {
                schedule(uri.toString(), debounceMs);
            }
        }
    }

    @Override
    public void configure(LiveDiagnosticsConfig config) {
        if (config == null) return;
        if (config.debounceMs != null) debounceMs = Math.max(0, config.debounceMs);
        if (config.enabled != null && enabled != config.enabled) {
            enabled = config.enabled;
            if (!enabled) {
                List<String> uris;
                synchronized (pending) {
                    pending.values().forEach(future -> future.cancel(false));
                    uris = new ArrayList<>(pending.keySet());
                    pending.clear();
                    generations.replaceAll((uri, generation) -> generation + 1);
                }
                cancelRunning();
                for (String uri : uris) clear(uri, "disabled");
            }
        }
    }

    @Override
    public void analyze(String uri) {
        schedule(uri, 0);
    }

    private void schedule(String uri, long delay) {
        if (!enabled) return;
        synchronized (pending) {
            ScheduledFuture<?> previous = pending.remove(uri);
            if (previous != null) previous.cancel(false);
            long generation = generations.merge(uri, 1L, Long::sum);
            pending.put(uri, worker.schedule(() -> run(uri, generation), delay, TimeUnit.MILLISECONDS));
        }
        // An analysis of an older version of the document is worthless; let it stop between processors.
        cancelRunning();
    }

    private void cancelRunning() {
        CompilationContext context = running;
        if (context == null) return;
        try {
            context.getStartEnvironment().setProperty(Environment.CANCEL_COMPILATION, true);
            for (Processor<?, ?> processor : new ArrayList<>(context.getProcessorInstancesSequence())) processor.cancelCompilation();
        } catch (Exception e) {
            // The context may have finished concurrently; nothing to cancel then.
        }
    }

    private boolean current(String uri, long generation) {
        synchronized (pending) {
            return generations.getOrDefault(uri, 0L) == generation;
        }
    }

    /** What the read of the document yields: a detached, traced copy of the model, or why there is none. */
    private static final class Snapshot {
        final Integer version;
        final EObject model;
        final String reason;
        Snapshot(Integer version, EObject model, String reason) { this.version = version; this.model = model; this.reason = reason; }
    }

    private void run(String uri, long generation) {
        synchronized (pending) {
            pending.remove(uri);
        }
        if (!enabled || !current(uri, generation) || client == null || access == null) return;
        Snapshot snapshot;
        try {
            snapshot = access.doRead(uri, context -> {
                if (!context.isDocumentOpen()) return new Snapshot(null, null, "closed");
                Resource resource = context.getResource();
                Integer version = context.getDocument() == null ? null : context.getDocument().getVersion();
                if (resource == null || !resource.getErrors().isEmpty() || resource.getContents().isEmpty()) {
                    return new Snapshot(version, null, "syntax");
                }
                EObject model = resource.getContents().get(0);
                // Origins are recorded on the live objects and inherited by the copy, which is what gets compiled
                // once the read lock is released.
                SourceTrace.begin(model);
                EObject copy = SourceTrace.copy(model);
                try {
                    Resource holder = new XtextResourceSet().createResource(resource.getURI());
                    holder.getContents().add(copy);
                } catch (Exception e) {
                    // Without a resource factory the copy stays detached; the transformations do not need one.
                }
                return new Snapshot(version, copy, null);
            }).get();
        } catch (Exception e) {
            return;
        }
        if (snapshot == null || !current(uri, generation)) return;
        if (snapshot.model == null) {
            publish(uri, snapshot.version, new ArrayList<>(), 0, snapshot.reason);
            return;
        }
        long start = System.nanoTime();
        List<Issue> issues = new ArrayList<>();
        CompilationContext context = null;
        boolean cancelled = false;
        try {
            context = Compile.createCompilationContext(SYSTEM, snapshot.model);
            context.getStartEnvironment().setProperty(Environment.INPLACE, false);
            context.getStartEnvironment().setProperty(ProjectInfrastructure.USE_TEMPORARY_PROJECT, false);
            context.setStopOnError(true);
            running = context;
            if (!current(uri, generation)) return;
            // Never overlap a user compile: it corrupts the SCG extension caches (CompileGate). A user compile
            // requested meanwhile cancels this one through the preemptor registered in the constructor.
            CompileGate.lock();
            try {
                if (!current(uri, generation)) return;
                context.compile();
            } finally {
                CompileGate.unlock();
            }
            cancelled = Boolean.TRUE.equals(context.getStartEnvironment().getProperty(Environment.CANCEL_COMPILATION));
            for (Issue issue : SnapshotDescription.issues(context)) {
                if (!issue.locations.isEmpty()) issues.add(issue);
            }
        } catch (Exception e) {
            // A transformation that throws on a half-written model is not a finding; the next edit retries.
            return;
        } finally {
            if (running == context) running = null;
        }
        if (!current(uri, generation)) return;
        if (cancelled) {
            // Stopped for a user compile rather than by a newer edit: the document still needs its result.
            schedule(uri, debounceMs);
            return;
        }
        publish(uri, snapshot.version, issues, (System.nanoTime() - start) / 1_000_000, issues.isEmpty() ? "clean" : null);
    }

    private void clear(String uri, String reason) {
        synchronized (pending) {
            ScheduledFuture<?> previous = pending.remove(uri);
            if (previous != null) previous.cancel(false);
            generations.merge(uri, 1L, Long::sum);
        }
        publish(uri, null, new ArrayList<>(), 0, reason);
    }

    private void publish(String uri, Integer version, List<Issue> issues, long durationMs, String reason) {
        KeithLanguageClient target = client;
        if (target == null) return;
        try {
            target.liveDiagnostics(new LiveDiagnosticsParam(uri, version, issues, durationMs, reason));
        } catch (Exception e) {
            // The client went away; nothing else to do.
        }
    }

    @Override
    public void setLanguageClient(LanguageClient client) {
        this.client = client instanceof KeithLanguageClient ? (KeithLanguageClient) client : null;
    }

    @Override
    public LanguageClient getLanguageClient() {
        return client;
    }
}
