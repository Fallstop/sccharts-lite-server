package de.cau.cs.kieler.language.server.kicool.data;

/** One {@code .kico} file the server looked at: what it registered, or why it did not. */
public class WorkspaceSystemInfo {
    public String file;
    public String id;
    public String label;
    public boolean loaded;
    public String error;
    /** The server's canonical path for the file; diagnostic, it tells why two spellings did or did not meet. */
    public String key;

    public WorkspaceSystemInfo() {}

    public WorkspaceSystemInfo(String file, String id, String label, boolean loaded, String error) {
        this.file = file; this.id = id; this.label = label; this.loaded = loaded; this.error = error;
    }
}
