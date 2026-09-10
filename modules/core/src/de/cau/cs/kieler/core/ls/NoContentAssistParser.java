package de.cau.cs.kieler.core.ls;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.xtext.ide.editor.contentassist.antlr.FollowElement;
import org.eclipse.xtext.ide.editor.contentassist.antlr.IContentAssistParser;

/**
 * Stands in for the generated content-assist parser of a grammar that does not ship one, so the
 * Guice module of that language still resolves. It never proposes anything; see
 * {@link NoContentAssistService}.
 */
public class NoContentAssistParser implements IContentAssistParser {
    @Override
    public Collection<FollowElement> getFollowElements(String input, boolean strict) {
        return Collections.emptyList();
    }

    @Override
    public Collection<FollowElement> getFollowElements(FollowElement element) {
        return Collections.emptyList();
    }
}
