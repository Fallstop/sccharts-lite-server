package de.cau.cs.kieler.language.server.kicool;

import java.util.*;
import de.cau.cs.kieler.kicool.compilation.CompilationContext;
import de.cau.cs.kieler.kicool.compilation.Processor;
import de.cau.cs.kieler.kicool.environments.Environment;
import de.cau.cs.kieler.language.server.kicool.data.*;

/**
 * Per-processor timings of one compilation, in the order the system runs its processors. Filled by the
 * {@link KeithCompilationUpdater} as processors finish and copied into every {@link CompilationResults}.
 */
public final class CompilationTimeline {
    private final long startNanos = System.nanoTime();
    private final List<Processor<?, ?>> sequence;
    private final Map<Processor<?, ?>, ProcessorTiming> timings = new IdentityHashMap<>();
    private int finished;

    public CompilationTimeline(CompilationContext context) {
        sequence = new ArrayList<>(context.getProcessorInstancesSequence());
    }

    /** Number of processors the system runs when nothing fails. */
    public int size() { return sequence.size(); }

    /** Number of processors that finished so far. */
    public int finished() { return finished; }

    public long elapsedMs() { return (System.nanoTime() - startNanos) / 1_000_000; }

    public ProcessorInfo info(Processor<?, ?> processor) {
        return new ProcessorInfo(processor.getId(), processor.getName(), Math.max(0, sequence.indexOf(processor)));
    }

    /** Records a finished processor from the timing properties of its target environment. */
    public ProcessorTiming finished(Processor<?, ?> processor, int snapshotIndex) {
        Environment environment = processor.getEnvironment();
        boolean enabled = processor.getSourceEnvironment() == null || Boolean.TRUE.equals(processor.getSourceEnvironment().getProperty(Environment.ENABLED));
        String status = !enabled ? "skipped"
            : environment.getErrors() != null && !environment.getErrors().isEmpty() ? "error"
            : environment.getWarnings() != null && !environment.getWarnings().isEmpty() ? "warning" : "ok";
        ProcessorTiming timing = new ProcessorTiming(processor.getId(), processor.getName(), status);
        Long duration = environment.getProperty(Environment.TRANSFORMATION_TIME);
        if (duration == null || duration == 0) duration = environment.getProperty(Environment.PROCESSOR_TIME);
        timing.durationMs = duration == null ? 0 : duration / 1_000_000;
        Long start = environment.getProperty(Environment.TRANSFORMATION_TIME_START);
        timing.startedAtMs = start == null || start == 0 ? elapsedMs() - timing.durationMs : Math.max(0, (start - startNanos) / 1_000_000);
        timing.snapshotIndex = snapshotIndex;
        timings.put(processor, timing);
        finished++;
        return timing;
    }

    /** Copies the timing of a finished processor onto the snapshot that shows its result. */
    public void decorate(SnapshotDescription snapshot, Processor<?, ?> processor) {
        snapshot.processorId = processor.getId();
        ProcessorTiming timing = timings.get(processor);
        if (timing == null) return;
        snapshot.durationMs = timing.durationMs;
        snapshot.startedAtMs = timing.startedAtMs;
        snapshot.status = timing.status;
    }

    /** Fills the results with the timeline; processors that never ran are skipped, or cancelled if the user stopped. */
    public void applyTo(CompilationResults results, boolean finishedCompilation, boolean cancelled) {
        List<ProcessorTiming> list = new ArrayList<>();
        for (Processor<?, ?> processor : sequence) {
            ProcessorTiming timing = timings.get(processor);
            list.add(timing != null ? timing : new ProcessorTiming(processor.getId(), processor.getName(), cancelled ? "cancelled" : "skipped"));
        }
        results.processors = list;
        results.processorCount = sequence.size();
        if (finishedCompilation) results.totalMs = elapsedMs();
    }

    public void applyTo(CompilationResults results, boolean finishedCompilation) {
        applyTo(results, finishedCompilation, false);
    }
}
