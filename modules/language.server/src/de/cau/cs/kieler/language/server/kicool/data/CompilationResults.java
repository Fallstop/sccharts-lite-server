package de.cau.cs.kieler.language.server.kicool.data;

import java.util.*;
import de.cau.cs.kieler.language.server.kicool.GeneratedCode;

/** Keeps the existing stage protocol and adds source files only to successful final results. */
public class CompilationResults {
    public List<List<SnapshotDescription>> files;
    public List<GeneratedCode.File> generatedFiles;
    public String generationError;
    /** Wall time of the whole compilation; only set once it finished. */
    public Long totalMs;
    /** Number of processors the system runs when nothing fails. */
    public Integer processorCount;
    /** One entry per processor of the system in execution order, including the ones that never ran. */
    public List<ProcessorTiming> processors;

    public CompilationResults(List<List<SnapshotDescription>> files) {
        this.files = files;
    }

    public CompilationResults(List<List<SnapshotDescription>> files, List<?> models, boolean finished, String uri) {
        this(files);
        if (finished) GeneratedCode.collect(this, models, uri);
    }
}
