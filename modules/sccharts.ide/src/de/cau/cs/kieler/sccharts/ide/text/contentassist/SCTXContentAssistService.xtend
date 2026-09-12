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
package de.cau.cs.kieler.sccharts.ide.text.contentassist

import com.google.inject.Singleton
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry
import org.eclipse.xtext.ide.server.Document
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService

/**
 * Xtext hands {@code ContentAssistEntry.documentation} to the client as a plain string, which editors show
 * verbatim — backticks, headings and all. The proposal documentation is Markdown, so it is sent as
 * {@link MarkupContent} instead, the same way the hover cards are.
 */
@Singleton
class SCTXContentAssistService extends ContentAssistService {

    override protected toCompletionItem(ContentAssistEntry entry, int caretOffset, Position caretPosition,
        Document document) {
        val item = super.toCompletionItem(entry, caretOffset, caretPosition, document)
        val documentation = entry.documentation
        if (!documentation.nullOrEmpty) {
            // Markdown wants plain newlines; Xtend templates elsewhere in the card break lines per platform.
            item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, documentation.replace("\r\n", "\n")))
        }
        return item
    }
}
