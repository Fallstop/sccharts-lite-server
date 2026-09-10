package de.cau.cs.kieler.language.server.kicool.data;

/** A processor of the compiled system with its timing; part of the didCompile results. */
public class ProcessorTiming {
    public String id;
    public String name;
    /** ok, warning, error, skipped (never started because an earlier stage failed or it was disabled) or cancelled. */
    public String status;
    /** Wall time of the stage including its pre and post processors; absent when it never ran. */
    public Long durationMs;
    /** Milliseconds since compilation start; absent when it never ran. */
    public Long startedAtMs;
    /** Flat index of the snapshot showing this processor's result, or -1. */
    public int snapshotIndex = -1;

    public ProcessorTiming(String id, String name, String status) { this.id = id; this.name = name; this.status = status; }
}
