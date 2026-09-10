package de.cau.cs.kieler.core.diagnostics;

import java.util.*;

/** JSON payload shared with the extension. Offsets are UTF-16, like Xtext and VS Code. */
public final class Issue {
    /** Payload marking a raw compiler message that a structured issue already explains. */
    public static final Object SUPPRESSED = new Object();

    public String code;
    public String message;
    public String severity = "error";
    public String hint;
    public String details;
    public List<Location> locations = new ArrayList<>();
    public List<Edge> cycle = new ArrayList<>();

    public Issue(String code, String message) { this.code = code; this.message = message; }

    /** An issue located at the given model objects (through the copies made of them) with a hint from the analysis that found it. */
    public static Issue at(String code, String message, String hint, org.eclipse.emf.ecore.EObject... objects) {
        Issue issue = new Issue(code, message);
        issue.hint = hint;
        for (org.eclipse.emf.ecore.EObject object : objects) {
            if (object != null) for (Location location : SourceTrace.locations(object)) SourceTrace.add(issue.locations, location);
        }
        return issue;
    }

    /** Variables assigned in the listed source ranges, in order of first appearance; what a message names. */
    public static List<String> assignedSymbols(List<Location> locations) {
        List<String> symbols = new ArrayList<>();
        java.util.regex.Pattern assigned = java.util.regex.Pattern.compile("\\b([A-Za-z_]\\w*)(?=\\s*=(?!=))");
        for (Location location : locations) {
            if (location.label == null) continue;
            java.util.regex.Matcher matcher = assigned.matcher(location.label);
            while (matcher.find()) if (!symbols.contains(matcher.group(1))) symbols.add(matcher.group(1));
        }
        return symbols;
    }

    public static final class Location {
        public String uri;
        public int offset;
        public int length;
        public String label;
        public int line = -1;
        public int column = -1;
        public String generatedLine;
        public List<String> traceUris = new ArrayList<>();
        public Location(String uri, int offset, int length, String label) {
            this.uri = uri; this.offset = offset; this.length = length; this.label = label;
        }
    }

    public static final class Edge {
        public String from;
        public String to;
        public String reason;
        public String fromLabel;
        public String toLabel;
        public List<Location> locations = new ArrayList<>();
    }
}
