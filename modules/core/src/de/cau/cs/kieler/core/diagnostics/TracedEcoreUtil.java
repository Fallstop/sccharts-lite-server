package de.cau.cs.kieler.core.diagnostics;

import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * {@link EcoreUtil} whose copies record where the copied objects came from, so that diagnostics on
 * transformed models can point at the original source text. Used in place of EcoreUtil in the
 * compiler bundles below KiCool; KiCool's own TracingEcoreUtil records the same information.
 */
public class TracedEcoreUtil extends EcoreUtil {

    public static <T extends EObject> T copy(T eObject) {
        return SourceTrace.copy(eObject);
    }

    @SuppressWarnings("unchecked")
    public static <T> Collection<T> copyAll(Collection<? extends T> eObjects) {
        Collection<T> result = new ArrayList<T>(eObjects.size());
        Copier copier = new Copier();
        for (T t : eObjects) result.add((T) copier.copy((EObject) t));
        copier.copyReferences();
        SourceTrace.copied(copier);
        return result;
    }
}
