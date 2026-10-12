package de.cau.cs.kieler.core.diagnostics;

import org.eclipse.emf.ecore.EObject;

/**
 * A chart the lab compiles although the official KIELER compiler fails on it. Every place where the lab works around
 * a defect it inherited from KIELER reports one of these warnings, so a chart that compiles here is not mistaken for
 * one that compiles in KIELER. The condition that raises it must be the one that makes KIELER fail.
 */
public final class Divergence {
    public static final String CODE = "kieler-divergence";

    private Divergence() {}

    /**
     * A warning located at the given model objects (through the copies made of them).
     *
     * @param failure what KIELER does with this chart, e.g. "fails with a NullPointerException in the C code generator"
     * @param portable how to write the chart so that KIELER compiles it too
     */
    public static Issue issue(String construct, String failure, String portable, EObject... objects) {
        Issue issue = Issue.at(CODE, "The official KIELER compiler " + failure + " on " + construct
            + ". SCCharts Lab compiles it, but this chart will not compile in KIELER.", portable, objects);
        issue.severity = "warning";
        return issue;
    }
}
