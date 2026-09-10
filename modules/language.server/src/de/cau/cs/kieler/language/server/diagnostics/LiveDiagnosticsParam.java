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

import java.util.List;
import de.cau.cs.kieler.core.diagnostics.Issue;

/** Payload of {@code keith/diagnostics/live}: the issues the live analysis found in one version of a document. */
public class LiveDiagnosticsParam {
    public String uri;
    /** The document version the analysis saw, as numbered by the client; null when unknown. */
    public Integer version;
    public List<Issue> issues;
    /** Wall time of the analysis in milliseconds; 0 when nothing was analysed (cleared). */
    public long durationMs;
    /** Why the list is empty when it is: "clean", "syntax", "disabled", "closed" or "cancelled". */
    public String reason;

    public LiveDiagnosticsParam(String uri, Integer version, List<Issue> issues, long durationMs, String reason) {
        this.uri = uri; this.version = version; this.issues = issues; this.durationMs = durationMs; this.reason = reason;
    }
}
