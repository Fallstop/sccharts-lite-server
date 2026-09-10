package de.cau.cs.kieler.language.server.kicool.data;

import java.util.ArrayList;
import java.util.List;

/** Parameter of {@code keith/kicool/systemFolders}. */
public class SystemFoldersParam {
    /** Absolute directories, or directories relative to each workspace folder; walked recursively. */
    public List<String> folders = new ArrayList<>();
    /** The client's workspace folders as absolute paths; their .kico files are loaded like those of Xtext's projects. */
    public List<String> workspaceFolders = new ArrayList<>();
}
