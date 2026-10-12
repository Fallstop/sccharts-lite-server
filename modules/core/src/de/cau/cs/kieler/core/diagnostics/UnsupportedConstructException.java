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

    public UnsupportedConstructException(String message, EObject object) {
        super(message);
        this.object = object;
    }
}
