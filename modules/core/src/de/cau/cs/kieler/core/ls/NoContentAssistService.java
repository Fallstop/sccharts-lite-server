package de.cau.cs.kieler.core.ls;

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

/**
 * Completion for languages whose content-assist parser is not part of this build. The generated
 * parsers are the largest classes of every grammar; only SCTX keeps its own. Editing support for
 * the other languages is parsing, validation and highlighting, and a completion request gets an
 * empty list instead of a Guice configuration error.
 */
public class NoContentAssistService extends ContentAssistService {
    @Override
    public CompletionList createCompletionList(Document document, XtextResource resource, CompletionParams params, CancelIndicator cancelIndicator) {
        return new CompletionList();
    }
}
