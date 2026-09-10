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
package de.cau.cs.kieler.sccharts.ide.text.hover

import com.google.inject.Inject
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.ide.server.hover.HoverService

/**
 * Hover for SCTX documents: Xtext's default only shows documentation comments, which SCCharts models rarely
 * carry, so the card is built from the model itself (declaration, scope, transitions, comments).
 */
class SCTXHoverService extends HoverService {

    @Inject SCTXHoverProvider provider

    override getContents(EObject element) {
        try {
            // Xtend templates break lines with the platform separator; Markdown wants plain newlines
            // (and the hover test compares against them on every platform).
            return (provider.markdown(element) ?: "").replace("\r\n", "\n")
        } catch (Exception e) {
            // A hover must never fail a request; an empty card is the fallback.
            return ""
        }
    }
}
