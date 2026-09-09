/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 *
 * Copyright 2014 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.kicool.ui.kitt.tracing;

import org.eclipse.emf.ecore.EObject;

import de.cau.cs.kieler.klighd.kgraph.KNode;

/**
 * Headless stand-in for the Piccolo figure that drew tracing and dependency edges between
 * arbitrary diagram elements. Syntheses still attach it to a custom rendering so the edge keeps
 * its source and target; without a Piccolo canvas nothing is drawn from it.
 */
public class TracingEdgeNode {

    private final EObject source;
    private final EObject target;
    private final KNode attachNode;

    public TracingEdgeNode(final EObject source, final EObject target, final KNode attachNode) {
        this.source = source;
        this.target = target;
        this.attachNode = attachNode;
    }

    public void setIgnoreFirstCollapsibleParent(final boolean ignoreForSource, final boolean ignoreForTarget) {
        // No figure to update.
    }

    public EObject getSource() {
        return source;
    }

    public EObject getTarget() {
        return target;
    }

    public KNode getAttachNode() {
        return attachNode;
    }
}
