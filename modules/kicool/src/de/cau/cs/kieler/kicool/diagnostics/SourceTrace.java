package de.cau.cs.kieler.kicool.diagnostics;

import java.util.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

/**
 * Remembers which source ranges a model object stems from, across the copies and explicit trace calls
 * that the compilation chain performs. Independent of KiCool's experimental tracing engine.
 */
public final class SourceTrace {
    private static final Map<EObject, List<Issue.Location>> origins = Collections.synchronizedMap(new WeakHashMap<>());

    /** Records the direct source ranges of a model about to be compiled. */
    public static void begin(Object original) {
        if (!(original instanceof EObject)) return;
        EObject root = (EObject) original;
        remember(root);
        root.eAllContents().forEachRemaining(SourceTrace::remember);
    }

    private static void remember(EObject object) {
        Issue.Location location = direct(object);
        if (location != null) origins.put(object, Collections.singletonList(location));
    }

    /** A copy or an explicitly traced object inherits the origins of what it was made from. */
    public static void copied(EObject result, EObject original) {
        if (result == null || original == null || result == original) return;
        List<Issue.Location> source = origins.get(original);
        if (source == null) {
            Issue.Location location = direct(original);
            if (location != null) source = Collections.singletonList(location);
        }
        if (source == null) return;
        List<Issue.Location> merged = new ArrayList<>(origins.getOrDefault(result, Collections.emptyList()));
        for (Issue.Location location : source) add(merged, location);
        origins.put(result, merged);
    }

    /** Records every pair of an EMF copier after it ran. */
    public static void copied(Map<EObject, EObject> copier) {
        for (Map.Entry<EObject, EObject> entry : copier.entrySet()) copied(entry.getValue(), entry.getKey());
    }

    /** Convenience for the common copy-and-copy-references idiom, with provenance recorded. */
    public static <T extends EObject> T copy(T original) {
        if (original == null) return null;
        EcoreUtil.Copier copier = new EcoreUtil.Copier();
        @SuppressWarnings("unchecked")
        T result = (T) copier.copy(original);
        copier.copyReferences();
        copied(copier);
        return result;
    }

    /** Known origins, falling back to the object's own syntax node. */
    public static List<Issue.Location> locations(EObject object) {
        List<Issue.Location> result = new ArrayList<>(origins.getOrDefault(object, Collections.emptyList()));
        if (result.isEmpty()) add(result, direct(object));
        return result;
    }

    /** Recorded origins only, without the syntax-node fallback. */
    public static List<Issue.Location> recorded(EObject object) {
        return origins.getOrDefault(object, Collections.emptyList());
    }

    public static Issue.Location direct(EObject object) {
        INode node = NodeModelUtils.getNode(object);
        if (node == null || object.eResource() == null || object.eResource().getURI() == null) return null;
        String uri = object.eResource().getURI().toString();
        if (!uri.startsWith("file:") || !uri.endsWith(".sctx")) return null;
        int start = node.getOffset() - node.getTotalOffset();
        String label = node.getText().substring(start, start + node.getLength()).trim().replaceAll("\\s+", " ");
        if (label.length() > 180) label = label.substring(0, 177) + "...";
        Issue.Location location = new Issue.Location(uri, node.getOffset(), node.getLength(), label);
        for (EObject element = object; element != null; element = element.eContainer()) {
            location.traceUris.add(EcoreUtil.getURI(element).toString());
        }
        return location;
    }

    public static void add(List<Issue.Location> list, Issue.Location location) {
        if (location != null && list.stream().noneMatch(l -> l.uri.equals(location.uri) && l.offset == location.offset && l.length == location.length)) list.add(location);
    }
}
