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

import java.util.Map

/**
 * One-line details and Markdown documentation for the SCTX keywords a modeller writes.
 *
 * <p>The texts describe SCCharts semantics, not grammar: what the keyword does to the tick, not which rule
 * it belongs to. Keywords that only appear in the generated or the host-language parts of the grammar are
 * deliberately absent; an unknown keyword simply gets no documentation.</p>
 */
class SCTXKeywordDocs {

    /** Display title, one-line detail and Markdown body of a single keyword. */
    static class Doc {
        public val String title
        public val String detail
        public val String body

        new(String title, String detail, String body) {
            this.title = title
            this.detail = detail
            this.body = body
        }

        /**
         * The card shown in the completion popup. Assembled by hand rather than with an Xtend template,
         * because templates break lines with the platform separator and Markdown wants plain newlines.
         */
        def String markdown() {
            "**`" + title + "`** — " + detail + "\n\n" + body
        }
    }

    /**
     * Keyword to documentation. A keyword whose meaning depends on where it is written is keyed
     * "&lt;rule&gt;.&lt;keyword&gt;" as well; {@code abort} is the example, a preemption in a transition
     * and a flag on a region.
     */
    static val Map<String, Doc> DOCS = docs()

    private static def void add(Map<String, Doc> docs, String key, String title, String detail, String body) {
        docs.put(key, new Doc(title, detail, body))
    }

    private static def Map<String, Doc> docs() {
        val docs = <String, Doc>newHashMap
        // ---- Structure -----------------------------------------------------------------------------
        docs.add("scchart", "scchart", "Root state of an SCChart.",
            "Declares the root state of an SCChart. Its body holds the interface declarations, the local actions and the concurrent regions that make up the chart. One file may hold several charts, and a chart may be reused from another with `is` or `extends`.")
        docs.add("state", "state", "A state of a controlflow region.",
            "Declares a state. A state may carry its own declarations, entry, during and exit actions and nested regions, and it is left through the transitions written after it. Exactly one state of a region must be `initial`.")
        docs.add("initial", "initial", "Marks the state a region starts in.",
            "Marks the state that is active when its region is entered. Every controlflow region needs exactly one initial state.")
        docs.add("final", "final", "Marks a state that completes its region.",
            "Marks a state whose activation completes the region. A `join to` transition of the surrounding state is enabled once every one of its regions has reached a final state.")
        docs.add("region", "region", "A concurrent controlflow region.",
            "Declares a controlflow region inside a state. The regions of one state run concurrently, in lockstep, one tick at a time. Write the body in braces, or after a colon.")
        docs.add("dataflow", "dataflow", "A region written as equations instead of states.",
            "Declares a dataflow region: instead of states and transitions the body holds equations that are evaluated every tick. The compiler schedules them by their data dependencies.")
        docs.add("extends", "extends", "Inherit declarations, actions and regions from a base chart.",
            "Makes the chart or state inherit the declarations, actions and regions of the listed base charts. An inherited member can be replaced by declaring it again with `override`.")
        docs.add("import", "import", "Make another SCTX file's charts visible.",
            "Imports another `.sctx` file so that the charts it declares can be referenced from this one. The path is written as a string, relative to this file.")
        docs.add("ref", "ref", "A reference to another chart or class.",
            "Declares a reference to another SCChart or class declaration, so that its interface can be bound and its behaviour instantiated. On a state or region, `is` expands the referenced chart in place.")
        docs.add("connector", "connector", "A state that must be left in the same tick.",
            "Marks a connector state: it only joins transitions and may not stay active, so one of its outgoing transitions has to be enabled whenever it is entered.")
        docs.add("override", "override", "Replace an inherited member.",
            "Replaces a declaration, action or region inherited through `extends` with the one written here.")

        // ---- Declarations --------------------------------------------------------------------------
        docs.add("input", "input", "Declaration read from the environment.",
            "Marks a declaration as an input: the environment writes the value at the start of every tick, and the chart may only read it. `input output` declares a value that flows both ways.")
        docs.add("output", "output", "Declaration written to the environment.",
            "Marks a declaration as an output: the chart writes the value and the environment reads it at the end of the tick. An output may also be read inside the chart.")
        docs.add("signal", "signal", "A signal, absent unless it is emitted.",
            "Declares a signal in the Esterel sense: it is absent in every tick unless it is emitted, and it is absent again in the next tick. A valued signal folds concurrent emissions with its combine operator.")
        docs.add("clock", "clock", "A clock that grows with physical time.",
            "Declares a clock for timed automata: its value grows with physical time instead of being assigned. Compare a clock against a bound in a trigger, and reset it with an assignment in an effect.")
        docs.add("const", "const", "A compile-time constant.",
            "Declares a constant. Its initialisation has to be known at compile time and the object may not be assigned anywhere in the chart.")
        docs.add("host", "host", "A type or value taken verbatim from the host language.",
            "Declares an object whose type is written in the host language of the generated code, given as a string after the keyword. The compiler passes the type and any host expressions through untouched.")
        docs.add("bool", "bool", "Boolean value type.",
            "The boolean value type, with the literals `true` and `false`. Triggers are boolean expressions.")
        docs.add("int", "int", "Integer value type.",
            "The integer value type. It maps to the integer type of the target language in the generated code.")
        docs.add("float", "float", "Floating point value type.",
            "The floating point value type. It maps to the target language's floating point type; not every code generation target supports it.")
        docs.add("string", "string", "String value type.",
            "The string value type, written in double quotes. Not every code generation target supports strings.")
        docs.add("pure", "pure", "A signal that carries presence only.",
            "Declares a pure signal: it has no value, only a presence that a trigger can test. It is the default kind of signal.")

        // ---- Transitions ---------------------------------------------------------------------------
        docs.add("PreemptionType.go", "go to", "Weak abort: the state's regions still run this tick.",
            "Takes a weak abort transition. In the tick the transition is taken the source state's own regions still run, and only then is the target state entered. This is the default kind of transition.")
        docs.add("PreemptionType.abort", "abort to", "Strong abort: the state's regions do not run this tick.",
            "Takes a strong abort transition. The source state's regions are preempted before they run, so nothing inside the state happens in the tick the transition is taken.")
        docs.add("PreemptionType.join", "join to", "Taken once every region reached a final state.",
            "Takes a termination transition. It becomes enabled when every region of the source state has reached a final state; a trigger written on it is checked on top of that condition.")
        docs.add("ControlflowRegion.abort", "abort", "Marks the region that may preempt its siblings.",
            "Marks a controlflow region as the aborting one, so that its transitions may preempt the sibling regions of the same state.")
        docs.add("to", "to", "Names the target state of a transition.",
            "Separates the preemption kind from the target state: `go to`, `abort to` and `join to` all end in the name of the state to enter.")
        docs.add("if", "if", "Trigger of a transition or action.",
            "Introduces the trigger: a boolean expression over the variables, signals and clocks in scope. Without a trigger the transition is enabled as soon as its delay allows.")
        docs.add("do", "do", "Effects executed when the transition or action fires.",
            "Introduces the effects: assignments and emissions separated by `;`. They run in the tick the transition or action is taken.")
        docs.add("immediate", "immediate", "May be taken in the tick the state is entered.",
            "Makes a transition or action immediate: it may already be taken in the tick its state is entered. Immediate transitions can form instantaneous loops, which the compiler rejects as not constructive.")
        docs.add("delayed", "delayed", "Spells out the default: not taken in the entering tick.",
            "Writes the default delay explicitly: the transition or action is not taken in the tick its state is entered, so at least one tick passes first. It is the opposite of `immediate`.")
        docs.add("deferred", "deferred", "Enter the target without its immediate behaviour.",
            "Defers entering the target state: its entry actions and immediate transitions do not run in the tick the transition is taken. `deep deferred` defers the nested regions as well.")
        docs.add("nondeterministic", "nondeterministic", "Allow the transition to compete with its siblings.",
            "Marks the transition as nondeterministic, so the compiler does not resolve it against the other outgoing transitions by their written order.")
        docs.add("history", "history", "Re-enter the target in the configuration it was left in.",
            "Makes the transition restore the target state's previous configuration instead of starting from its initial states. `shallow history` restores only the immediate regions.")
        docs.add("label", "label", "A display name for the diagram.",
            "Attaches a display label to the element. The label is what the diagram shows; the name stays the identifier used in the text.")

        // ---- Actions -------------------------------------------------------------------------------
        docs.add("entry", "entry", "Action run when the state is entered.",
            "Declares an entry action: its effects run when the state is entered, before the state's regions run for the first time. Entry actions typically reset local variables.")
        docs.add("during", "during", "Action run in every tick the state is active.",
            "Declares a during action: its effects run in every tick the state stays active. `immediate during` includes the tick the state is entered.")
        docs.add("exit", "exit", "Action run when the state is left.",
            "Declares an exit action: its effects run when the state is left through any outgoing transition, in the same tick as the transition itself.")
        docs.add("suspend", "suspend", "Freeze the state's regions while the trigger holds.",
            "Declares a suspend action: while its trigger holds, the state's regions do not run and the state keeps its configuration. `weak suspend` lets the current tick finish before freezing.")
        docs.add("period", "period", "Run the state's regions only every n-th tick.",
            "Declares a period action: the state's regions only run every n-th tick, as given by the expression. It is how parts of a chart are clocked at a slower rate.")
        docs.add("weak", "weak", "Let the current tick finish before preempting.",
            "Weakens a suspend or preemption: the behaviour of the current tick still runs, and only the following ticks are affected.")
        docs.add("strong", "strong", "Preempt before the current tick runs.",
            "Strengthens a suspend or preemption: the state's regions are stopped before they run in this tick.")

        docs.add("is", "is", "Expand another chart in place of this state or region.",
            "Binds the state or region to another chart, whose behaviour is expanded here. Arguments in the parentheses bind the referenced chart's interface to objects of this one.")
        docs.add("schedule", "schedule", "Pin an element into the compiler's schedule.",
            "Attaches a scheduling directive, which fixes where the element goes in the order the compiler computes. It is only needed where the data dependencies leave a choice.")
        docs.add("auto", "auto", "Let the compiler pick the delay.",
            "Leaves the delay of the transition or action to the compiler, which decides between immediate and delayed from the surrounding model.")
        docs.add("undefined", "undefined", "No delay stated.",
            "Leaves the delay unstated, which the compiler treats like the delayed default.")
        docs.add("violation", "violation", "A state that must never be reached.",
            "Marks a state as a violation: reaching it is a property failure, which verification and simulation report instead of treating it as normal behaviour.")
        docs.add("ode", "ode", "A continuous equation over a variable.",
            "Declares an ode action: the effect gives the derivative of a variable, integrated between ticks. It is used for the continuous part of a hybrid model.")

        // ---- Expressions ---------------------------------------------------------------------------
        docs.add("pre", "pre", "pre(x): x's value in the previous tick.",
            "Reads the value an object had in the previous tick. It breaks a causality cycle where a value would otherwise have to be written and read in the same tick.")
        docs.add("val", "val", "val(s): the value carried by signal s.",
            "Reads the value of a valued signal, as opposed to its presence. Concurrent emissions in the same tick are folded with the signal's combine operator.")
        docs.add("fby", "fby", "x fby y: x in the first tick, y afterwards.",
            "The followed-by operator of the dataflow languages: the expression is the left value in the first tick and the right value in every later one.")
        docs.add("sfby", "sfby", "Followed-by with the right side kept for one tick.",
            "The stateful variant of `fby`: it holds the right hand value for one tick before it is read, which breaks an instantaneous dependency.")
        return docs
    }

    /** The documentation for a keyword written in the given grammar rule, or null if there is none. */
    static def Doc find(String rule, String keyword) {
        DOCS.get(rule + "." + keyword) ?: DOCS.get(keyword)
    }

    /** The documentation for a keyword, ignoring where it is written, or null if there is none. */
    static def Doc find(String keyword) {
        DOCS.get(keyword)
    }
}
