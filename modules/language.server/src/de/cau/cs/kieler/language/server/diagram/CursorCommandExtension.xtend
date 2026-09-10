/*
 * SCCharts Lab - sccharts-lite server
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.diagram

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment

/**
 * Editor-to-diagram synchronisation: the LSP replacement for the Eclipse editor's SmartCollapseHook.
 */
@JsonSegment('keith/diagram')
interface CursorCommandExtension {

    /**
     * Expands the diagram to the element under the editor cursor and selects it.
     */
    @JsonRequest('cursor')
    def CompletableFuture<CursorResult> cursor(CursorParam param)
}
