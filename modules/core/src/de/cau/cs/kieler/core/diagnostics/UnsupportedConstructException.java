package de.cau.cs.kieler.core.diagnostics;

import org.eclipse.emf.ecore.EObject;

/**
 * Thrown where the official KIELER compiler fails on a model object, in place of the exception KIELER throws there, so
 * that the processor that is running can report a located issue instead of crashing. It extends the exception KIELER
 * throws, so code that catches that one behaves as before.
 */
public class UnsupportedConstructException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** The model object KIELER fails on. */
    public final transient EObject object;

    /** The issue to report, when the code that throws can explain it; otherwise the processor that catches explains it. */
    public final transient Issue issue;

    public UnsupportedConstructException(String message, EObject object) {
        super(message);
        this.object = object;
        this.issue = null;
    }

    public UnsupportedConstructException(Issue issue, EObject object) {
        super(issue.message);
        this.object = object;
        this.issue = issue;
    }
}
