package de.cau.cs.kieler.scg.diagnostics;

import java.util.*;
import org.eclipse.emf.ecore.EObject;
import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;
import de.cau.cs.kieler.core.properties.IProperty;
import de.cau.cs.kieler.core.properties.Property;
import de.cau.cs.kieler.kexpressions.MethodDeclaration;
import de.cau.cs.kieler.kexpressions.ValueType;
import de.cau.cs.kieler.kexpressions.ValuedObject;
import de.cau.cs.kieler.kexpressions.VariableDeclaration;
import de.cau.cs.kieler.kexpressions.keffects.Emission;
import de.cau.cs.kieler.kicool.environments.Environment;
import de.cau.cs.kieler.scg.Assignment;

/**
 * Emissions of something that is not a signal, such as do b for a bool variable b. The SCG has no operation for them:
 * the SCG transformation leaves an empty assignment in their place, and KIELER's C and Java code generators fail on
 * that assignment with a NullPointerException. The transformation records each such emission here, so that the code
 * generator can reject it with an error at the emission instead.
 */
public final class Emissions {
    public static final String CODE = "unsupported-construct";

    /** The explanations of the emissions this compilation left empty, by their source ranges. */
    private static final IProperty<Map<String, Issue>> RECORDED = new Property<>("de.cau.cs.kieler.scg.diagnostics.emissions", null);

    private Emissions() {}

    /** Called by the SCG transformation for an emission it leaves as an empty assignment. */
    public static void record(Environment environment, Emission emission) {
        Issue issue = issue(emission);
        if (issue == null || issue.locations.isEmpty()) return;
        Map<String, Issue> recorded = environment.getProperty(RECORDED);
        if (recorded == null) {
            recorded = new HashMap<>();
            environment.setProperty(RECORDED, recorded);
        }
        for (Issue.Location location : issue.locations) recorded.put(key(location), issue);
    }

    /** The errors for an empty assignment the code generator cannot serialize; none if it stands for no recorded emission. */
    public static List<Issue> issues(Environment environment, Assignment node) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Issue> recorded = environment == null ? null : environment.getProperty(RECORDED);
        if (recorded == null || node.getExpression() != null) return issues;
        for (Issue.Location location : ScgTrace.locations(node)) {
            Issue issue = recorded.get(key(location));
            if (issue != null && !issues.contains(issue)) issues.add(issue);
        }
        return issues;
    }

    /** Null for an emission of a signal, which is not this defect. */
    private static Issue issue(Emission emission) {
        ValuedObject object = emission.getReference() == null ? null : emission.getReference().getValuedObject();
        EObject declaration = object == null ? null : object.eContainer();
        if (declaration instanceof VariableDeclaration && ((VariableDeclaration) declaration).isSignal()) return null;
        String name = identifier(label(emission), object == null ? "This" : object.getName());
        String target = label(emission.getReference());
        if (target == null) target = name;
        String value = emission.getNewValue() == null ? null : label(emission.getNewValue());
        if (emission.getNewValue() != null && value == null) value = "<value>";
        String message;
        String hint;
        if (declaration instanceof VariableDeclaration) {
            VariableDeclaration variable = (VariableDeclaration) declaration;
            String io = (variable.isInput() ? "input " : "") + (variable.isOutput() ? "output " : "");
            String type = variable.getType() == null ? "" : variable.getType().getLiteral() + " ";
            boolean array = !object.getCardinalities().isEmpty();
            String kind = io + (variable.isConst() ? "const " : "") + type + (array ? "array" : "variable");
            message = name + " is " + article(kind) + kind + ", not a signal, so it cannot be emitted.";
            String assignment = target + " = " + (value != null ? value : variable.getType() == ValueType.BOOL ? "true" : "<value>");
            String signal = array ? "" : " (" + io + "signal " + (value == null ? "" : type) + name + ")";
            hint = "Write " + assignment + " to set it, or declare " + name + " as " + (value == null ? "a" : "a valued")
                + " signal" + signal + " to emit it. KIELER cannot compile the emission of a variable either.";
        } else if (declaration instanceof MethodDeclaration) {
            message = name + " is a method, not a signal, so it cannot be emitted.";
            hint = "Write " + name + "() to call it. KIELER cannot compile this emission either.";
        } else {
            message = name + " is not a signal, so it cannot be emitted.";
            hint = "Only signals can be emitted. Declare " + name + " as a signal, or assign it instead. "
                + "KIELER cannot compile this emission either.";
        }
        return Issue.at(CODE, message, hint, emission);
    }

    /** The source text of a model object, as the student wrote it. */
    private static String label(EObject object) {
        if (object == null) return null;
        for (Issue.Location location : SourceTrace.recorded(object)) if (location.label != null && !location.label.isEmpty()) return location.label;
        return null;
    }

    private static String identifier(String label, String fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^([A-Za-z_]\\w*)").matcher(label == null ? "" : label);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String article(String noun) {
        return "aeiou".indexOf(Character.toLowerCase(noun.charAt(0))) >= 0 ? "an " : "a ";
    }

    private static String key(Issue.Location location) {
        return location.uri + "#" + location.offset + "+" + location.length;
    }
}
