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

import com.google.inject.Inject
import de.cau.cs.kieler.kexpressions.KExpressionsPackage
import de.cau.cs.kieler.kexpressions.ValuedObject
import de.cau.cs.kieler.kexpressions.VariableDeclaration
import de.cau.cs.kieler.sccharts.Region
import de.cau.cs.kieler.sccharts.Scope
import de.cau.cs.kieler.sccharts.State
import de.cau.cs.kieler.sccharts.ide.text.hover.SCTXHoverProvider
import de.cau.cs.kieler.sccharts.ide.text.symbols.SCTXSymbolDetailsProvider
import java.util.Collection
import java.util.LinkedHashMap
import java.util.List
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.Assignment
import org.eclipse.xtext.CrossReference
import org.eclipse.xtext.GrammarUtil
import org.eclipse.xtext.Keyword
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider
import org.eclipse.xtext.resource.IEObjectDescription
import org.eclipse.xtext.scoping.IScope
import org.eclipse.xtext.xtext.CurrentTypeFinder

/**
 * Content assist for SCTX.
 *
 * <p>Xtext's default provider offers exactly what the content assist parser computes as the follow set, which
 * for SCTX means undocumented keywords, every binary operator of the expression grammar, and — because the
 * generated parser stops inside the declaration loop of a state body — neither the local action keywords nor
 * the variables of a transition trigger. This provider</p>
 * <ul>
 * <li>documents keywords from {@link SCTXKeywordDocs} and drops the bare operators,</li>
 * <li>offers snippets for the shapes a modeller writes by hand,</li>
 * <li>resolves cross references through the language's own scope provider and labels them the way the
 *     outline does ({@code input bool}, {@code initial state}), with the declaration's comment as
 *     documentation, and</li>
 * <li>adds the action keywords and the visible variables at the two positions where the follow set is
 *     missing them: a state or region body, and the trigger of a transition or action.</li>
 * </ul>
 */
class SCTXProposalProvider extends IdeContentProposalProvider {

    /** Local actions are valid wherever a state may start, but the follow set does not always list them. */
    static val List<String> ACTION_KEYWORDS = #["entry", "during", "exit", "suspend", "period"]

    /** Value keywords that the grammar hides inside terminal or enum rules, so they are never proposed. */
    static val List<String> EXPRESSION_KEYWORDS = #["pre", "val", "true", "false"]

    /**
     * Tokens after which an expression starts. Used to recognise a trigger or effect position, where the
     * content assist parser does not reach the cross reference of a valued object reference.
     */
    static val List<String> EXPRESSION_OPENERS = #[
        "if", "do", "period", "ode", "return", ";", ",", "(", "[", "?", ":",
        "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=",
        "==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/", "%", "&", "|", "^", "&&", "||", "!", "~",
        "<<", ">>", ">>>", "->", "fby", "sfby", "pre", "val"
    ]

    /** Snippets by the keyword that triggers them: label shown in the list, then the text with tab stops. */
    static val Map<String, Pair<String, String>> SNIPPETS = #{
        "scchart" -> ("scchart Name { }" -> "scchart ${1:Name} {\n\t$0\n}"),
        "state" -> ("state Name { }" -> "state ${1:Name} {\n\t$0\n}"),
        "initial" -> ("initial state Name" -> "initial state ${1:Name}$0"),
        "region" -> ("region Name { initial state S }" -> "region ${1:Name} {\n\tinitial state ${2:S}$0\n}"),
        "if" -> ("if cond go to Target" -> "if ${1:cond} go to ${2:Target}$0"),
        "entry" -> ("entry do x = 0" -> "entry do ${1:x} = ${2:0}$0")
    }

    /** Above a plain keyword, so that a half-typed `en` offers `entry` before `enum`. */
    static val int EXTRA_KEYWORD_BONUS = 60

    /** Just above a plain keyword: a snippet is a convenience, not the main answer. */
    static val int SNIPPET_BONUS = 10

    @Inject CurrentTypeFinder typeFinder
    @Inject SCTXSymbolDetailsProvider details
    @Inject SCTXHoverProvider hover

    /**
     * The grammar's own proposals, plus the ones its follow set misses. The extras run first so that a
     * keyword they document survives the de-duplication.
     */
    override createProposals(Collection<ContentAssistContext> contexts, IIdeContentProposalAcceptor acceptor) {
        val once = new OnceAcceptor(acceptor)
        for (context : getFilteredContexts(contexts)) {
            if (!once.canAcceptMoreProposals) return;
            createMissingProposals(context, once)
        }
        for (context : getFilteredContexts(contexts)) {
            for (element : context.firstSetGrammarElements) {
                if (!once.canAcceptMoreProposals) return;
                createProposals(element, context, once)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The positions the follow set does not cover

    private def void createMissingProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        if (context.expressionPosition) {
            proposeValuedObjects(context, acceptor)
            for (keyword : EXPRESSION_KEYWORDS) {
                proposeKeyword(keyword, null, context, acceptor, EXTRA_KEYWORD_BONUS)
            }
        }
        if (context.bodyPosition) {
            for (keyword : ACTION_KEYWORDS) {
                proposeKeyword(keyword, null, context, acceptor, EXTRA_KEYWORD_BONUS)
            }
        }
    }

    /**
     * True where a state may start, which is the body of a state or of a controlflow region. Local actions
     * are written there too, but the content assist parser only offers them once one is already present.
     */
    private def boolean isBodyPosition(ContentAssistContext context) {
        context.firstSetGrammarElements.exists [ element |
            if (element instanceof Keyword) {
                val rule = GrammarUtil.containingRule(element)
                return element.value == "state" && rule !== null && rule.name == "State"
            }
            return false
        ]
    }

    /**
     * True right after a token that opens an expression — `if`, `do`, an operator. The follow set stops at
     * the rule call there, so no valued object reaches the proposal list.
     */
    private def boolean isExpressionPosition(ContentAssistContext context) {
        val node = context.lastCompleteNode
        node !== null && EXPRESSION_OPENERS.contains(node.text.trim)
    }

    private def void proposeValuedObjects(ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        // SCTX scopes a valued object reference against the surrounding scope, not against the transition
        // that holds it: asked from a transition, the same lookup answers with that region's states instead.
        val model = (context.currentModel ?: context.previousModel ?: context.rootModel).enclosingScope
        if (model === null) return;
        try {
            val reference = KExpressionsPackage.eINSTANCE.valuedObjectReference_ValuedObject
            // The scope of a valued object reference also carries the named objects a scope call may target;
            // in an expression only the valued objects can be written, so the rest is dropped.
            acceptScope(scopeProvider.getScope(model, reference), context, acceptor,
                [KExpressionsPackage.eINSTANCE.valuedObject.isSuperTypeOf(it.EClass)])
        } catch (Exception exception) {
            // Scoping a half-written model may fail; completion still has its keywords to offer.
        }
    }

    /** The state, region or chart the element belongs to, which is what declarations are scoped against. */
    private def EObject enclosingScope(EObject element) {
        var candidate = element
        while (candidate !== null && !(candidate instanceof Scope)) {
            candidate = candidate.eContainer
        }
        return candidate ?: element
    }

    // ------------------------------------------------------------------------------------------------
    // Keywords and snippets

    /** Operators are punctuation, not something to pick from a list; `Pr=` is a probability, not a word. */
    override protected filterKeyword(Keyword keyword, ContentAssistContext context) {
        val value = keyword.value
        !value.nullOrEmpty && Character.isLetter(value.charAt(0)) && value != "Pr=" && value != "Pr"
    }

    override protected _createProposals(Keyword keyword, ContentAssistContext context,
        IIdeContentProposalAcceptor acceptor) {
        if (!filterKeyword(keyword, context)) return;
        val rule = GrammarUtil.containingRule(keyword)
        proposeKeyword(keyword.value, rule?.name, context, acceptor, 0)
    }

    /**
     * A keyword with its documentation, followed by the snippet it opens. The rule disambiguates a keyword
     * whose meaning depends on where it stands, so that `abort` reads differently on a transition and on a
     * region; pass null where there is nothing to disambiguate.
     */
    private def void proposeKeyword(String keyword, String rule, ContentAssistContext context,
        IIdeContentProposalAcceptor acceptor, int bonus) {
        val doc = rule === null ? SCTXKeywordDocs.find(keyword) : SCTXKeywordDocs.find(rule, keyword)
        val entry = proposalCreator.createProposal(keyword, context, ContentAssistEntry.KIND_KEYWORD) [
            if (doc !== null) {
                description = doc.detail
                documentation = doc.markdown
            }
        ]
        if (entry === null) return;
        acceptor.accept(entry, proposalPriorities.getKeywordPriority(keyword, entry) + bonus)
        proposeSnippet(keyword, doc, acceptor, entry)
    }

    private def void proposeSnippet(String keyword, SCTXKeywordDocs.Doc doc, IIdeContentProposalAcceptor acceptor,
        ContentAssistEntry keywordEntry) {
        val snippet = SNIPPETS.get(keyword)
        if (snippet === null) return;
        val entry = new ContentAssistEntry
        entry.prefix = keywordEntry.prefix
        entry.proposal = snippet.value
        entry.label = snippet.key
        entry.kind = ContentAssistEntry.KIND_SNIPPET
        entry.description = "snippet"
        if (doc !== null) entry.documentation = doc.markdown
        acceptor.accept(entry, proposalPriorities.getKeywordPriority(keyword, keywordEntry) + SNIPPET_BONUS)
    }

    // ------------------------------------------------------------------------------------------------
    // Cross references

    /**
     * Only the cross references of an assignment. Xtext's default also proposes the feature name of an
     * assignment whose terminal is a terminal rule, which is how `triggerDelay` and `values` ended up in
     * the list: grammar vocabulary, not something anyone writes in a chart.
     */
    override protected _createProposals(Assignment assignment, ContentAssistContext context,
        IIdeContentProposalAcceptor acceptor) {
        val terminal = assignment.terminal
        if (terminal instanceof CrossReference) createProposals(terminal, context, acceptor)
    }

    /**
     * The same lookup as the default provider, but the entries are labelled and documented from the model,
     * and an object reachable under both its simple and its qualified name is proposed once.
     */
    override protected _createProposals(CrossReference reference, ContentAssistContext context,
        IIdeContentProposalAcceptor acceptor) {
        val type = typeFinder.findCurrentTypeAfter(reference)
        if (!(type instanceof EClass)) return;
        val eReference = GrammarUtil.getReference(reference, type as EClass)
        val model = context.currentModel
        if (eReference === null || model === null) return;
        try {
            acceptScope(scopeProvider.getScope(model, eReference), context, acceptor, [true])
        } catch (Exception exception) {
            // A cross reference that cannot be scoped simply has nothing to propose.
        }
    }

    /**
     * Every element of the scope the filter admits, proposed once. A valued object is in scope under both its
     * simple and its qualified name; the shorter of the two is the one worth offering.
     */
    private def void acceptScope(IScope scope, ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
        (IEObjectDescription)=>boolean filter) {
        val shortest = new LinkedHashMap<String, IEObjectDescription>
        for (candidate : scope.allElements) {
            if (filter.apply(candidate)) {
                val uri = candidate.EObjectURI
                val key = uri === null ? candidate.name.toString : uri.toString
                val previous = shortest.get(key)
                if (previous === null || candidate.name.segmentCount < previous.name.segmentCount) {
                    shortest.put(key, candidate)
                }
            }
        }
        for (candidate : shortest.values) {
            if (!acceptor.canAcceptMoreProposals) return;
            val entry = candidate.proposal(context)
            if (entry !== null) acceptor.accept(entry, proposalPriorities.getCrossRefPriority(candidate, entry))
        }
    }

    private def ContentAssistEntry proposal(IEObjectDescription candidate, ContentAssistContext context) {
        val name = qualifiedNameConverter.toString(candidate.name)
        proposalCreator.createProposal(name, context) [ entry |
            entry.source = candidate
            val object = candidate.local(context)
            switch object {
                ValuedObject: {
                    entry.kind = object.entryKind
                    entry.description = details.getDetails(object)
                    entry.documentation = hover.documentation(object)
                }
                State: {
                    entry.kind = ContentAssistEntry.KIND_CLASS
                    entry.description = details.getDetails(object)
                    entry.documentation = hover.documentation(object)
                }
                Region: {
                    entry.kind = ContentAssistEntry.KIND_MODULE
                    entry.description = details.getDetails(object)
                    entry.documentation = hover.documentation(object)
                }
                default: {
                    entry.kind = ContentAssistEntry.KIND_REFERENCE
                    entry.description = candidate.EClass?.name
                }
            }
        ]
    }

    /**
     * The described object, but only when it lives in the document being edited. Resolving a proxy out of
     * the index would load the other resource, and a keystroke is not the place to pay for that; an object
     * from elsewhere keeps the generic label instead.
     */
    private def EObject local(IEObjectDescription candidate, ContentAssistContext context) {
        val uri = candidate.EObjectURI
        val resource = context.resource
        if (uri === null || resource === null || resource.URI === null) return null
        if (!resource.URI.equals(uri.trimFragment)) return null
        try {
            val object = EcoreUtil.resolve(candidate.EObjectOrProxy, resource)
            return object === null || object.eIsProxy ? null : object
        } catch (Exception exception) {
            return null
        }
    }

    private def String entryKind(ValuedObject object) {
        val declaration = object.eContainer
        if (declaration instanceof VariableDeclaration) {
            if (declaration.const) return ContentAssistEntry.KIND_VALUE
            if (declaration.input || declaration.output || declaration.signal) return ContentAssistEntry.KIND_FIELD
        }
        return ContentAssistEntry.KIND_VARIABLE
    }

    /**
     * Keeps the first proposal of a kind and text. The same keyword reaches the acceptor once per content
     * assist context, and the acceptor only folds duplicates that also share a priority.
     */
    private static class OnceAcceptor implements IIdeContentProposalAcceptor {
        val IIdeContentProposalAcceptor delegate
        val seen = <String>newHashSet

        new(IIdeContentProposalAcceptor delegate) {
            this.delegate = delegate
        }

        override accept(ContentAssistEntry entry, int priority) {
            if (entry === null || entry.proposal === null) return;
            if (!seen.add(entry.kind + "/" + entry.proposal)) return;
            delegate.accept(entry, priority)
        }

        override canAcceptMoreProposals() {
            delegate.canAcceptMoreProposals
        }

        override didAcceptAllProposals() {
            delegate.didAcceptAllProposals
        }
    }
}
