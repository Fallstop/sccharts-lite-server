package de.cau.cs.kieler.scg.diagnostics;

import org.eclipse.emf.ecore.EObject;
import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;
import de.cau.cs.kieler.scg.ControlFlow;
import de.cau.cs.kieler.scg.Entry;
import de.cau.cs.kieler.scg.Exit;
import de.cau.cs.kieler.scg.Node;
import de.cau.cs.kieler.scg.SCGraph;

/**
 * A control flow without a target, met while walking an SCG. The SCG transformation leaves one behind when the chart,
 * or one of its regions, has no state to start in. KIELER's priority code generators and its SCG-to-SCCharts step
 * (state-based compilation) fail on it, so the walk throws this and the processor reports the issue instead.
 */
public final class MissingSuccessor extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public static final String CODE = "unsupported-construct";
    public static final String NETLIST = "The netlist-based compiler accepts this chart.";

    /** The node whose control flow has no target, or null when the walk does not know it. */
    public final transient Node node;
    private final transient SCGraph graph;

    public MissingSuccessor(Node node, SCGraph graph) {
        super("A control flow of the SCG has no target.", null, false, false);
        this.node = node;
        this.graph = graph != null ? graph : node != null && node.eContainer() instanceof SCGraph ? (SCGraph) node.eContainer() : null;
    }

    /** Thrown by a walk that knows which node's control flow has no target. */
    public MissingSuccessor(Node node) {
        this(node, null);
    }

    /** The first node of the graph, among the given ones, that has a control flow without a target. */
    public static Node source(SCGraph graph, java.util.Set<Node> among) {
        for (Node node : graph.getNodes()) {
            if (!among.contains(node)) continue;
            for (EObject child : node.eContents()) {
                if (child instanceof ControlFlow && ((ControlFlow) child).getTarget() == null) return node;
            }
        }
        return null;
    }

    /** The issue, located on the chart or region that has no state to start in, for e.g. "priority-based compiler". */
    public Issue issue(String compiler) {
        String kieler = "KIELER's " + compiler;
        String chart = graph != null ? graph.getName() : null;
        Issue issue;
        // A chart without states leaves nothing but its entry and exit; a root entry also dangles when normalization
        // removed the only state of a chart that does have states.
        boolean stateless = graph != null && graph.getNodes().stream().allMatch(n -> n instanceof Entry || n instanceof Exit);
        if (stateless && node instanceof Entry && node.getIncomingLinks().stream().noneMatch(ControlFlow.class::isInstance)) {
            issue = Issue.at(CODE, "Chart " + chart + " has no states, so " + kieler + " cannot compile it.",
                "KIELER compiles the first chart in the file and fails when it has no states. If " + chart
                    + " only holds declarations for other charts to extend, put the chart you want to compile first;"
                    + " otherwise give " + chart + " a state, e.g. `initial state Idle`. " + NETLIST, node);
        } else if (node instanceof Entry) {
            issue = Issue.at(CODE, "This region has no state left to start in once KIELER has normalized the chart, so "
                    + kieler + " cannot compile it.",
                "This happens when the region only holds an initial final state and the state around it has an outgoing"
                    + " transition other than `join` or a during action: KIELER's normalization deletes that final state. Give the region"
                    + " a state that waits before it finishes, e.g. `initial state Run` with `go to Done` and"
                    + " `final state Done`. " + NETLIST, node);
        } else {
            issue = Issue.at(CODE, "Part of this chart leads to no next state once KIELER has normalized it, so " + kieler
                    + " cannot compile it.",
                "KIELER cannot compile this chart with this compiler. Simplify the marked part of the chart, or use the"
                    + " netlist-based compiler.", node);
        }
        // Without a traced node, the chart the graph was made from is the best location there is.
        if (issue.locations.isEmpty() && graph != null) {
            for (Issue.Location location : SourceTrace.locations(graph)) SourceTrace.add(issue.locations, location);
        }
        return issue;
    }
}
