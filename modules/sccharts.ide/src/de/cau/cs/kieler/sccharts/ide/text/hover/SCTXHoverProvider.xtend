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
import de.cau.cs.kieler.annotations.Annotatable
import de.cau.cs.kieler.annotations.CommentAnnotation
import de.cau.cs.kieler.kexpressions.Declaration
import de.cau.cs.kieler.kexpressions.ValueType
import de.cau.cs.kieler.kexpressions.ValuedObject
import de.cau.cs.kieler.kexpressions.ValuedObjectReference
import de.cau.cs.kieler.kexpressions.VariableDeclaration
import de.cau.cs.kieler.kexpressions.keffects.Assignment
import de.cau.cs.kieler.kexpressions.keffects.Emission
import de.cau.cs.kieler.sccharts.ControlflowRegion
import de.cau.cs.kieler.sccharts.DataflowRegion
import de.cau.cs.kieler.sccharts.DelayType
import de.cau.cs.kieler.sccharts.DeferredType
import de.cau.cs.kieler.sccharts.DuringAction
import de.cau.cs.kieler.sccharts.EntryAction
import de.cau.cs.kieler.sccharts.ExitAction
import de.cau.cs.kieler.sccharts.HistoryType
import de.cau.cs.kieler.sccharts.LocalAction
import de.cau.cs.kieler.sccharts.PreemptionType
import de.cau.cs.kieler.sccharts.Region
import de.cau.cs.kieler.sccharts.Scope
import de.cau.cs.kieler.sccharts.State
import de.cau.cs.kieler.sccharts.Transition
import de.cau.cs.kieler.sccharts.extensions.SCChartsSerializeHRExtensions
import java.util.List
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.nodemodel.ILeafNode
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

/**
 * Builds the Markdown hover card for an SCTX model element.
 */
class SCTXHoverProvider {

    @Inject extension SCChartsSerializeHRExtensions

    def String markdown(EObject element) {
        switch element {
            ValuedObject: element.card
            VariableDeclaration: element.card
            State: element.card
            Transition: element.card
            Region: element.card
            LocalAction: element.card
            default: ""
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Variables, signals, inputs and outputs

    /**
     * The declaration as written says kind, type, name and initial value already; the card adds only what
     * the source line does not show: the documentation, where a nested declaration lives, and how it is used.
     */
    private def String card(ValuedObject vo) {
        val declaration = vo.eContainer
        val lines = newArrayList
        lines += code(declaration ?: vo, 3)
        lines += doc(declaration as Annotatable, vo)
        if (vo.combineOperator !== null && vo.combineOperator.literal != "NONE") lines += '''Combine operator: `«vo.combineOperator.literal»`'''
        lines += nestedScopeLine(declaration ?: vo)
        lines += vo.usage
        return lines.filterNull.join("\n\n")
    }

    private def String card(VariableDeclaration declaration) {
        val lines = newArrayList
        lines += code(declaration, 3)
        lines += '''**«declaration.kind» declaration** of type `«declaration.typeName»`: «declaration.valuedObjects.map["`" + name + "`"].join(", ")»'''
        lines += scopeLine(declaration)
        lines += doc(declaration, null)
        return lines.filterNull.join("\n\n")
    }

    private def String kind(VariableDeclaration d) {
        val parts = newArrayList
        if (d.const) parts += "const"
        if (d.static) parts += "static"
        if (d.extern) parts += "extern"
        if (d.volatile) parts += "volatile"
        if (d.input && d.output) parts += "input output" else if (d.input) parts += "input" else if (d.output) parts += "output"
        if (d.signal) parts += "signal" else if (d.type == ValueType.CLOCK) parts += "clock" else if (parts.empty || !(d.input || d.output)) parts += "variable"
        return parts.join(" ")
    }

    private def String typeName(VariableDeclaration d) {
        if (d.type == ValueType.HOST && !d.hostType.nullOrEmpty) return d.hostType
        return d.type?.literal ?: "unknown"
    }

    /** Where in the model the object is read and written, counted over the whole chart. */
    private def String usage(ValuedObject vo) {
        var root = vo as EObject
        while (root.eContainer !== null) root = root.eContainer
        var reads = 0
        var writes = 0
        val contents = root.eAllContents
        while (contents.hasNext) {
            val object = contents.next
            if (object instanceof ValuedObjectReference && (object as ValuedObjectReference).valuedObject === vo) {
                val owner = object.eContainer
                if ((owner instanceof Assignment && (owner as Assignment).reference === object) ||
                    (owner instanceof Emission && (owner as Emission).reference === object)) writes++ else reads++
            }
        }
        if (reads + writes == 0) return "Not used anywhere in this chart."
        if (reads == 0) return '''Written «times(writes)», never read.'''
        if (writes == 0) return '''Read «times(reads)», never written.'''
        '''Written «times(writes)», read «times(reads)».'''
    }

    private static def String times(int n) {
        if (n == 1) "once" else '''«n» times'''
    }

    // ------------------------------------------------------------------------------------------------
    // States, regions, transitions, actions

    private def String card(State state) {
        val lines = newArrayList
        lines += code(state, 1)
        val flags = newArrayList
        if (state.initial) flags += "initial"
        if (state.final) flags += "final"
        if (state.connector) flags += "connector"
        if (state.violation) flags += "violation"
        val title = state.parentRegion === null ? "root state" : "state"
        lines += '''**«IF !flags.empty»«flags.join(" ")» «ENDIF»«title»** `«state.name»`«IF !state.label.nullOrEmpty && state.label != state.name» "«state.label»"«ENDIF»'''
        lines += scopeLine(state)
        if (!state.regions.empty) {
            lines += '''Contains «state.regions.size» region«state.regions.size == 1 ? "" : "s"»:
«FOR region : state.regions»
- «region.summary»
«ENDFOR»'''.toString.trim
        }
        val actions = state.actions.filter(LocalAction).toList
        if (!actions.empty) {
            lines += '''Actions:
«FOR action : actions»
- `«action.serializeHR»`
«ENDFOR»'''.toString.trim
        }
        if (!state.outgoingTransitions.empty) {
            lines += '''Outgoing transitions («state.outgoingTransitions.size»):
«FOR transition : state.outgoingTransitions»
- «transition.summary»
«ENDFOR»'''.toString.trim
        } else if (state.parentRegion !== null) {
            lines += "No outgoing transitions."
        }
        if (!state.incomingTransitions.empty) {
            lines += '''Entered from «state.incomingTransitions.map[sourceState].toSet.map["`" + name + "`"].join(", ")».'''
        }
        lines += doc(state, null)
        return lines.filterNull.join("\n\n")
    }

    private def String card(Region region) {
        val lines = newArrayList
        lines += code(region, 1)
        lines += '''**«region.summary»**'''
        lines += scopeLine(region)
        if (region instanceof ControlflowRegion) {
            val initial = region.states.filter[initial].toList
            if (!initial.empty) lines += '''Initial state: «initial.map["`" + name + "`"].join(", ")»'''
            val finals = region.states.filter[final].toList
            if (!finals.empty) lines += '''Final state«finals.size == 1 ? "" : "s"»: «finals.map["`" + name + "`"].join(", ")»'''
        }
        lines += doc(region, null)
        return lines.filterNull.join("\n\n")
    }

    private def String summary(Region region) {
        val name = if (!region.name.nullOrEmpty) '''`«region.name»`''' else if (!region.label.nullOrEmpty) '''"«region.label»"''' else "unnamed"
        switch region {
            ControlflowRegion: '''controlflow region «name» with «region.states.size» state«region.states.size == 1 ? "" : "s"»'''
            DataflowRegion: '''dataflow region «name» with «region.equations.size» equation«region.equations.size == 1 ? "" : "s"»'''
            default: '''region «name»'''
        }
    }

    private def String card(Transition transition) {
        val lines = newArrayList
        lines += code(transition, 3)
        val source = transition.sourceState
        val priority = source === null ? -1 : source.outgoingTransitions.indexOf(transition) + 1
        lines += '''**transition** `«source?.name»` → `«transition.targetState?.name»`«IF priority > 0» · priority «priority» of «source.outgoingTransitions.size»«ENDIF»'''
        lines += '''«transition.kindLine»'''
        if (transition.trigger !== null) {
            lines += '''Trigger: `«IF transition.triggerDelay > 1»«transition.triggerDelay» «ENDIF»«transition.trigger.serializeHR»`'''
        } else {
            lines += transition.delay == DelayType.IMMEDIATE ? "Trigger: none (taken as soon as the state is entered)." : "Trigger: none (taken in the next tick)."
        }
        if (!transition.effects.empty) {
            lines += '''Effects:
«FOR effect : transition.effects»
- `«effect.serializeHR»`
«ENDFOR»'''.toString.trim
        }
        lines += doc(transition, null)
        return lines.filterNull.join("\n\n")
    }

    private def String kindLine(Transition t) {
        val parts = newArrayList
        parts += switch t.preemption {
            case PreemptionType.STRONG: "strong abort (`abort to`): preempts the state's regions before they run"
            case PreemptionType.TERMINATION: "termination (`join to`): taken when all regions reached a final state"
            default: "weak abort (`go to`): the state's regions run this tick before leaving"
        }
        parts += t.delay == DelayType.IMMEDIATE ? "immediate" : "delayed (not in the tick the state is entered)"
        if (t.history == HistoryType.SHALLOW) parts += "shallow history" else if (t.history == HistoryType.DEEP) parts += "deep history"
        if (t.deferred !== null && t.deferred != DeferredType.NONE) parts += t.deferred.literal.toLowerCase + " deferred"
        if (t.nondeterministic) parts += "nondeterministic"
        return parts.join(" · ")
    }

    private def String summary(Transition t) {
        val arrow = switch t.preemption {
            case PreemptionType.STRONG: "abort to"
            case PreemptionType.TERMINATION: "join to"
            default: "go to"
        }
        val label = t.serializeHR.toString.trim
        '''«IF t.delay == DelayType.IMMEDIATE»immediate «ENDIF»«IF !label.empty»`«label»` «ENDIF»«arrow» `«t.targetState?.name»`'''
    }

    private def String card(LocalAction action) {
        val lines = newArrayList
        lines += code(action, 3)
        val kind = switch action {
            EntryAction: "entry action"
            DuringAction: "during action"
            ExitAction: "exit action"
            default: action.eClass.name
        }
        lines += '''**«kind»** `«action.serializeHR»`'''
        lines += scopeLine(action)
        lines += doc(action, null)
        return lines.filterNull.join("\n\n")
    }

    // ------------------------------------------------------------------------------------------------
    // Shared pieces

    /** The element's own source text as an sctx code block, cut to the first lines. */
    private def String code(EObject element, int maxLines) {
        val node = NodeModelUtils.findActualNodeFor(element)
        if (node === null) return null
        var text = node.text
        // Leading hidden tokens (comments, whitespace) belong to the node but are not the element's text.
        for (leaf : node.leafNodes) {
            if (leaf.hidden) text = text.substring(leaf.text.length) else return text.block(maxLines)
        }
        return text.block(maxLines)
    }

    private def String block(String text, int maxLines) {
        val lines = text.trim.split("\r?\n").map[trim].filter[!empty].toList
        val shown = lines.take(maxLines).map[if (endsWith("{")) substring(0, length - 1).trim else it].toList
        '''```sctx
«shown.join("\n")»«IF lines.size > maxLines»
…«ENDIF»
```'''
    }

    /** The enclosing states and regions, but only for a declaration inside a nested state; the root is implied. */
    private def String nestedScopeLine(EObject element) {
        var container = element.eContainer
        var depth = 0
        while (container !== null) {
            if (container instanceof State || container instanceof Region || container instanceof Scope) depth++
            container = container.eContainer
        }
        if (depth <= 1) null else scopeLine(element)
    }

    /** The chain of states and regions enclosing the element. */
    private def String scopeLine(EObject element) {
        val path = newLinkedList
        var container = element.eContainer
        while (container !== null) {
            switch container {
                State: path.addFirst(container.name ?: "state")
                Region: path.addFirst(if (!container.name.nullOrEmpty) container.name else if (!container.label.nullOrEmpty) container.label else "region")
                Scope: path.addFirst(container.name ?: container.eClass.name)
            }
            container = container.eContainer
        }
        if (path.empty) return null
        '''Declared in «path.map["`" + it + "`"].join(" › ")»'''
    }

    /**
     * The element's own documentation comments, without the card's separator. Content assist shows this on
     * its own, as the whole card would be far too much for a proposal list.
     */
    def String documentation(EObject element) {
        val vo = element instanceof ValuedObject ? element as ValuedObject : null
        val annotated = (vo === null ? element : element.eContainer) as EObject
        val text = doc(annotated instanceof Annotatable ? annotated as Annotatable : null, vo)
        if (text.nullOrEmpty) null else text.replaceFirst("^---\\n\\n", "")
    }

    /** Documentation from semantic comments and from plain comments immediately before the element. */
    private def String doc(Annotatable annotated, ValuedObject vo) {
        val texts = newArrayList
        if (vo !== null) texts += vo.annotations.filter(CommentAnnotation).map[values].flatten.map[clean]
        if (annotated !== null) texts += annotated.annotations.filter(CommentAnnotation).map[values].flatten.map[clean]
        if (texts.empty && annotated instanceof EObject) texts += (annotated as EObject).leadingComments
        val text = texts.filter[!nullOrEmpty].join("\n\n")
        if (text.empty) return null
        return "---\n\n" + text
    }

    private def List<String> leadingComments(EObject element) {
        val node = NodeModelUtils.findActualNodeFor(element)
        val comments = newArrayList
        if (node === null) return comments
        for (ILeafNode leaf : node.leafNodes) {
            if (!leaf.hidden) return comments
            val text = leaf.text.trim
            if (text.startsWith("//") || text.startsWith("/*")) comments += text.clean
        }
        return comments
    }

    private def String clean(String comment) {
        comment.trim
            .replaceFirst("^/\\*\\*?", "").replaceFirst("\\*/$", "").replaceFirst("^//\\*?", "")
            .split("\r?\n").map[replaceFirst("^\\s*\\*\\s?", "").replaceFirst("^\\s*//\\s?", "").trim].join("\n").trim
    }
}
