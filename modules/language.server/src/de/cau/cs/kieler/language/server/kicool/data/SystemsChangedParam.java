package de.cau.cs.kieler.language.server.kicool.data;

import java.util.ArrayList;
import java.util.List;

/** Payload of {@code keith/kicool/systemsChanged}: what a rescan of the workspace systems changed. */
public class SystemsChangedParam {
    public List<WorkspaceSystemInfo> added = new ArrayList<>();
    public List<WorkspaceSystemInfo> removed = new ArrayList<>();
    public List<WorkspaceSystemInfo> errors = new ArrayList<>();
}
