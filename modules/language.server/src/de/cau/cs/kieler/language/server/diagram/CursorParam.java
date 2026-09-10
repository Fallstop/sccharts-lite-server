/*
 * SCCharts Lab - sccharts-lite server
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.diagram;

/** Parameter of {@code keith/diagram/cursor}: the editor cursor to mirror in the diagram. */
public class CursorParam {
    /** URI of the text document the cursor is in. */
    public String uri;
    /** UTF-16 offset of the cursor in the document. */
    public int offset;
    /** Id of the diagram client (webview) that shows the model. */
    public String clientId;
    /** {@code focus} expands and selects the element under the cursor; {@code expand} additionally collapses the other regions. */
    public String mode = "focus";
}
