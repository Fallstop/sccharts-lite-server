package de.cau.cs.kieler.scg.diagnostics;

import java.util.*;
import org.eclipse.emf.ecore.EObject;
import de.cau.cs.kieler.kexpressions.ValuedObjectReference;
import de.cau.cs.kieler.kexpressions.keffects.AssignOperator;
import de.cau.cs.kieler.kicool.compilation.Processor;
import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;
import de.cau.cs.kieler.kicool.environments.*;
import de.cau.cs.kieler.scg.Assignment;
import de.cau.cs.kieler.scg.Conditional;
import de.cau.cs.kieler.scg.Node;
import de.cau.cs.kieler.scg.processors.analyzer.*;

/** Structured issues for the loops the loop analyzer found: located on the source model and explained. */
public final class Loops {
    public static final String CODE = "instantaneous-loop";
    public static final String MESSAGE = "Instantaneous loop detected!";

    /** One issue per loop, ready for the processor to report with the given severity. */
    public static List<Issue> issues(Processor<?, ?> processor, String severity, LoopData data) {
        List<Issue> issues = new ArrayList<>();
        if (data == null) return issues;
        List<Set<Node>> loops = new ArrayList<>();
        for (SingleLoop loop : data.getLoops()) if (!loop.getCriticalNodes().isEmpty()) loops.add(loop.getCriticalNodes());
        if (loops.isEmpty() && !data.getCriticalNodes().isEmpty()) loops.add(data.getCriticalNodes());
        for (Set<Node> loop : loops) {
            Issue issue = issue(processor, severity, loop);
            // Several loops through the same operations are one finding.
            boolean known = false;
            for (Issue other : issues) if (other.message.equals(issue.message) && sameLocations(other, issue)) known = true;
            if (!known) issues.add(issue);
        }
        return issues;
    }

    /** Reports the loops into the given message list, keeping the analyzer's bare text as a suppressed detail. */
    public static void report(Processor<?, ?> processor, MessageObjectReferences messages, String severity, LoopData data) {
        List<Issue> issues = issues(processor, severity, data);
        if (issues.isEmpty()) {
            messages.add(MESSAGE);
            return;
        }
        for (Issue issue : issues) messages.add(null, issue.message, null, issue);
    }

    private static boolean sameLocations(Issue a, Issue b) {
        if (a.locations.size() != b.locations.size()) return false;
        for (int i = 0; i < a.locations.size(); i++) {
            if (a.locations.get(i).offset != b.locations.get(i).offset || !a.locations.get(i).uri.equals(b.locations.get(i).uri)) return false;
        }
        return true;
    }

    private static Issue issue(Processor<?, ?> processor, String severity, Set<Node> loop) {
        Timed timed = Timed.of(processor, loop);
        Issue issue = new Issue(CODE, timed == null
            ? "Potential instantaneous loop."
            : "Potential instantaneous loop through the timed transitions on " + timed.clockList() + ".");
        issue.severity = severity;
        issue.hint = timed == null
            ? "Control flow or a data dependency returns to one of these operations without passing a tick boundary. "
                + "Make one transition on the loop delayed instead of immediate, or read the previous tick's value with pre(). "
                + "When the model still schedules, the loop only spans a clock or variable that is reset and read in the same tick and this is advisory."
            : "The compiler tests each timeout in the tick its state is entered, so the loop analyzer sees a path through all of these "
                + "states that never waits for the next tick. This is advisory when every state on the loop resets " + timed.clockList()
                + " on entry (entry do " + timed.sourceName(timed.clocks.iterator().next()) + " = 0) and no timeout is 0: a clock that was just reset cannot reach "
                + "its timeout in the same tick. A state that does not reset the clock lets an already expired timeout carry the machine "
                + "through several states in one tick; reset the clock there or guard the transition with pre().";
        List<Issue.Location> locations = new ArrayList<>();
        for (Node node : loop) {
            for (Issue.Location location : ScgTrace.locations(node)) {
                // On a timed loop the operations worth listing are the timed transitions and the clock resets; entry
                // actions that happen to lie on the path, such as output assignments, would only mislead.
                if (timed == null || timed.mentionsClock(location.label)) SourceTrace.add(locations, location);
            }
        }
        // Without the taken-transition tracing of the simulation systems the timed transitions carry no trace;
        // they are then found in the source model, which still names the clock as written.
        if (timed != null && locations.isEmpty()) for (Issue.Location location : timed.sourceTransitions(processor)) SourceTrace.add(locations, location);
        locations.sort(Comparator.comparingInt((Issue.Location location) -> location.offset));
        if (locations.size() > 12) locations = new ArrayList<>(locations.subList(0, 12));
        issue.locations.addAll(locations);
        if (timed == null) {
            List<String> symbols = Issue.assignedSymbols(locations);
            if (!symbols.isEmpty()) issue.message = "Potential instantaneous loop through " + String.join(", ", symbols) + ".";
        }
        issue.details = "Loop analyzer: " + MESSAGE + " " + loop.size() + " generated operations lie on this loop"
            + (locations.isEmpty() ? " and none carries a source location." : ".")
            + (timed == null ? "" : " The loop is control flow only (no data dependency) through the timed-automata expansion of "
                + timed.clockList() + " with " + timed.resets.size() + " reset(s) and " + timed.timeouts + " timeout check(s).");
        return issue;
    }

    /** A loop that runs through the timed-automata expansion: sleepT updates and timeout checks on one or more clocks. */
    private static final class Timed {
        final Set<String> clocks = new LinkedHashSet<>();
        final Set<Node> resets = new HashSet<>();
        int timeouts;

        /** Names as written in the model; region-local clocks are renamed during compilation. */
        final Map<String, String> sourceNames = new HashMap<>();

        static Timed of(Processor<?, ?> processor, Set<Node> loop) {
            Timed timed = new Timed();
            for (Node node : loop) {
                if (!(node instanceof Assignment)) continue;
                Assignment assignment = (Assignment) node;
                if (assignment.getReference() == null || assignment.getReference().getValuedObject() == null) continue;
                if ("sleepT".equals(assignment.getReference().getValuedObject().getName()) && assignment.getOperator() == AssignOperator.ASSIGNMIN) {
                    for (ValuedObjectReference reference : references(assignment.getExpression())) {
                        String name = reference.getValuedObject().getName();
                        if ("sleepT".equals(name)) continue;
                        timed.clocks.add(name);
                        for (Issue.Location origin : ScgTrace.locations(reference.getValuedObject())) {
                            java.util.regex.Matcher declared = java.util.regex.Pattern.compile("^([A-Za-z_]\\w*)").matcher(origin.label);
                            if (declared.find()) { timed.sourceNames.put(name, declared.group(1)); break; }
                        }
                    }
                }
            }
            if (timed.clocks.isEmpty()) return null;
            for (Node node : loop) {
                if (node instanceof Assignment) {
                    Assignment assignment = (Assignment) node;
                    if (assignment.getReference() != null && assignment.getReference().getValuedObject() != null
                        && assignment.getOperator() == AssignOperator.ASSIGN
                        && timed.clocks.contains(assignment.getReference().getValuedObject().getName())) timed.resets.add(node);
                } else if (node instanceof Conditional) {
                    for (ValuedObjectReference reference : references(((Conditional) node).getCondition()))
                        if (timed.clocks.contains(reference.getValuedObject().getName())) { timed.timeouts++; break; }
                }
            }
            return timed;
        }

        String sourceName(String clock) { return sourceNames.getOrDefault(clock, clock); }

        /** Transitions and actions of the original model whose trigger reads one of the clocks. */
        List<Issue.Location> sourceTransitions(Processor<?, ?> processor) {
            List<Issue.Location> locations = new ArrayList<>();
            Object original = processor.getCompilationContext() == null ? null : processor.getCompilationContext().getOriginalModel();
            if (!(original instanceof EObject)) return locations;
            Set<String> names = new HashSet<>();
            for (String clock : clocks) names.add(sourceName(clock));
            for (Iterator<EObject> contents = ((EObject) original).eAllContents(); contents.hasNext();) {
                EObject object = contents.next();
                // SCCharts actions carry their trigger in a feature named "trigger"; the SCG bundle cannot see
                // the SCCharts metamodel, so the feature is read reflectively.
                org.eclipse.emf.ecore.EStructuralFeature trigger = object.eClass().getEStructuralFeature("trigger");
                if (trigger == null || !(object.eGet(trigger) instanceof EObject)) continue;
                for (ValuedObjectReference reference : references((EObject) object.eGet(trigger))) {
                    EObject declaration = reference.getValuedObject().eContainer();
                    boolean clock = declaration instanceof de.cau.cs.kieler.kexpressions.VariableDeclaration
                        && ((de.cau.cs.kieler.kexpressions.VariableDeclaration) declaration).getType() == de.cau.cs.kieler.kexpressions.ValueType.CLOCK;
                    if (clock && names.contains(reference.getValuedObject().getName())) { SourceTrace.add(locations, SourceTrace.direct(object)); break; }
                }
            }
            return locations;
        }

        String clockList() {
            Set<String> names = new LinkedHashSet<>();
            for (String clock : clocks) names.add(sourceName(clock));
            return String.join(", ", names);
        }

        boolean mentionsClock(String label) {
            for (String clock : clocks) {
                for (String name : new String[] { clock, sourceName(clock) })
                    if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(name) + "\\b").matcher(label).find()) return true;
            }
            return false;
        }

        private static List<ValuedObjectReference> references(EObject expression) {
            List<ValuedObjectReference> references = new ArrayList<>();
            if (expression == null) return references;
            if (expression instanceof ValuedObjectReference && ((ValuedObjectReference) expression).getValuedObject() != null)
                references.add((ValuedObjectReference) expression);
            for (Iterator<EObject> contents = expression.eAllContents(); contents.hasNext();) {
                EObject child = contents.next();
                if (child instanceof ValuedObjectReference && ((ValuedObjectReference) child).getValuedObject() != null)
                    references.add((ValuedObjectReference) child);
            }
            return references;
        }
    }
}
