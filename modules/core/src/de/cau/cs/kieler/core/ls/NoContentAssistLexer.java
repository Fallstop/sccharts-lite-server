package de.cau.cs.kieler.core.ls;

import org.antlr.runtime.CharStream;
import org.antlr.runtime.RecognizerSharedState;
import org.eclipse.xtext.ide.editor.contentassist.antlr.internal.Lexer;

/**
 * Stands in for the generated content-assist lexer of a grammar that does not ship one. It is only
 * ever bound, never run, because {@link NoContentAssistService} answers completion requests before
 * any lexing happens.
 */
public class NoContentAssistLexer extends Lexer {
    public NoContentAssistLexer() {
        super();
    }

    public NoContentAssistLexer(CharStream input) {
        super(input);
    }

    public NoContentAssistLexer(CharStream input, RecognizerSharedState state) {
        super(input, state);
    }

    @Override
    public String getGrammarFileName() {
        return "NoContentAssist.g";
    }

    @Override
    public void mTokens() {
        // No tokens: this lexer is never used for content assist.
    }
}
