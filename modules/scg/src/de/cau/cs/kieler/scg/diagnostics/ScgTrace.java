package de.cau.cs.kieler.scg.diagnostics;

import java.util.*;
import org.eclipse.emf.ecore.EObject;
import de.cau.cs.kieler.kexpressions.keffects.Link;
import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;
import de.cau.cs.kieler.scg.GuardDependency;
import de.cau.cs.kieler.scg.Node;

/** Source locations of SCG nodes, including the operations a guard node stands for. */
public final class ScgTrace {
    public static List<Issue.Location> locations(EObject object) {
        List<Issue.Location> result = new ArrayList<>(SourceTrace.recorded(object));
        if (object instanceof Node) {
            for (Link link : ((Node) object).getOutgoingLinks()) {
                if (link instanceof GuardDependency) {
                    for (Issue.Location location : SourceTrace.recorded(link.getTarget())) SourceTrace.add(result, location);
                }
            }
        }
        if (result.isEmpty()) SourceTrace.add(result, SourceTrace.direct(object));
        return result;
    }
}
