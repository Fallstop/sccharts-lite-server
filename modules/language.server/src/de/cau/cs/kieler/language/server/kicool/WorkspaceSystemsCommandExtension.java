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
package de.cau.cs.kieler.language.server.kicool;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

import de.cau.cs.kieler.language.server.kicool.data.SystemFoldersParam;
import de.cau.cs.kieler.language.server.kicool.data.WorkspaceSystemInfo;

/** Protocol for compilation systems defined by {@code .kico} files in the user's workspace. */
@JsonSegment("keith/kicool")
public interface WorkspaceSystemsCommandExtension {

    /**
     * Sets the directories, in addition to the workspace itself, that are scanned for {@code .kico} files and
     * rescans everything. Relative entries are resolved against every workspace folder.
     */
    @JsonNotification("systemFolders")
    void systemFolders(SystemFoldersParam param);

    /** Lists the workspace systems currently registered, including files that failed to load. */
    @JsonRequest("workspaceSystems")
    CompletableFuture<List<WorkspaceSystemInfo>> workspaceSystems();
}
