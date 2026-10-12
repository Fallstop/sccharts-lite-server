/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.kexpressions.eval;

import de.cau.cs.kieler.kexpressions.OperatorExpression;

/**
 * An operator expression on values that {@link PartialExpressionEvaluator#compute} cannot compute, raised where upstream
 * KIELER fails: with an ArithmeticException on an integer division or remainder by zero, and with EMF's 'no null'
 * IllegalArgumentException on a chain of three or more operands whose first operands do not give a value.
 */
public class UncomputableExpressionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** An integer division or remainder (/ or %) whose divisor is zero. */
        DIVISION_BY_ZERO,
        /** A chain of three or more operands in which computing the first two (or the result so far and the next) gives no value. */
        UNCOMPUTABLE_CHAIN
    }

    public final Reason reason;

    /** The evaluated expression, as it is in the model, once the evaluator knows it; null for a direct call of compute. */
    public OperatorExpression expression;

    public UncomputableExpressionException(Reason reason) {
        super(reason == Reason.DIVISION_BY_ZERO ? "/ by zero" : "operand chain cannot be computed");
        this.reason = reason;
    }
}
