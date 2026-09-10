package de.cau.cs.kieler.language.server.kicool.data;

import java.util.*;
import org.eclipse.emf.ecore.EObject;
import de.cau.cs.kieler.kicool.environments.*;
import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;

/** Compatible with the bundled DTO; adds structured diagnostics without removing raw messages. */
public class SnapshotDescription {
    private String name;
    private int index, snapshotIndex;
    private List<String> errors = new ArrayList<>(), warnings = new ArrayList<>(), infos = new ArrayList<>();
    public List<Issue> diagnostics = new ArrayList<>();
    /** Id of the processor that produced this snapshot, e.g. de.cau.cs.kieler.sccharts.scg.processors.SCG. */
    public String processorId;
    /** Wall time of the whole processor stage (pre/post processors included); absent on intermediate snapshots. */
    public Long durationMs;
    /** Time the processor started, in milliseconds since the compilation started. */
    public Long startedAtMs;
    /** ok, warning, error, skipped or cancelled. */
    public String status;

    public SnapshotDescription(String name, int index, int snapshotIndex, Errors errors, Warnings warnings, Infos infos) {
        this.name = name; this.index = index; this.snapshotIndex = snapshotIndex;
        copy(errors, this.errors, "error"); copy(warnings, this.warnings, "warning"); copy(infos, this.infos, "info");
    }

    private void copy(MessageObjectReferences messages, List<String> text, String severity) {
        collect(messages, severity, text, diagnostics);
    }

    /**
     * Turns a processor's messages into raw text and structured issues: an {@link Issue} payload is taken as is,
     * a suppressed message stays raw text only, and any other error or warning becomes a plain compiler issue
     * located at its message object (through the copies the compilation made of it).
     */
    public static void collect(MessageObjectReferences messages, String severity, List<String> text, List<Issue> diagnostics) {
        if (messages == null) return;
        for (MessageObjectLink message : messages.getAllRootMessages()) {
            String raw = String.valueOf(message.getMessage());
            if (message.getException() != null) {
                raw += "\n" + message.getException();
                for (StackTraceElement frame : message.getException().getStackTrace()) raw += "\n\t" + frame;
            }
            if (text != null) text.add(raw);
            Object payload = message.getPayload();
            if (payload instanceof Issue) diagnostics.add((Issue) payload);
            else if (payload != Issue.SUPPRESSED && !severity.equals("info")) {
                Issue issue = new Issue("compiler", String.valueOf(message.getMessage()));
                issue.severity = severity; issue.details = raw;
                if (message.getObject() instanceof EObject) {
                    for (Issue.Location location : SourceTrace.locations((EObject) message.getObject())) SourceTrace.add(issue.locations, location);
                }
                diagnostics.add(issue);
            }
        }
    }

    /** All issues of every processor that ran in the context, in pipeline order. */
    public static List<Issue> issues(de.cau.cs.kieler.kicool.compilation.CompilationContext context) {
        List<Issue> issues = new ArrayList<>();
        for (de.cau.cs.kieler.kicool.compilation.Processor<?, ?> processor : context.getProcessorInstancesSequence()) {
            de.cau.cs.kieler.kicool.environments.Environment environment = processor.getEnvironment();
            if (environment == null) continue;
            collect(environment.getErrors(), "error", null, issues);
            collect(environment.getWarnings(), "warning", null, issues);
            collect(environment.getInfos(), "info", null, issues);
        }
        return issues;
    }

    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public int getIndex() { return index; }
    public void setIndex(int value) { index = value; }
    public int getSnapshotIndex() { return snapshotIndex; }
    public void setSnapshotIndex(int value) { snapshotIndex = value; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> value) { errors = value; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> value) { warnings = value; }
    public List<String> getInfos() { return infos; }
    public void setInfos(List<String> value) { infos = value; }
}
