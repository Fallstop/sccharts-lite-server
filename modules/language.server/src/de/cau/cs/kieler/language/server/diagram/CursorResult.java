/*
 * SCCharts Lab - sccharts-lite server
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.diagram;

/** Answer to {@code keith/diagram/cursor}. */
public class CursorResult {
    /** Whether the diagram was updated for the cursor. */
    public boolean ok;
    /** Why nothing was done when {@link #ok} is false. */
    public String message;
    /** The innermost diagram element under the cursor, if any. */
    public Element element;
    /** How many diagram nodes were expanded for the cursor. */
    public int expanded;
    /** How many regions were collapsed (only in {@code expand} mode). */
    public int collapsed;

    public static final class Element {
        /** {@code State}, {@code Region}, or {@code Transition}. */
        public String kind;
        /** The element's name, or its label when it has no name. */
        public String name;
        /** The element's URI fragment in the source resource. */
        public String id;

        public Element(String kind, String name, String id) {
            this.kind = kind; this.name = name; this.id = id;
        }
    }

    public static CursorResult fail(String message) {
        CursorResult result = new CursorResult();
        result.message = message;
        return result;
    }
}
