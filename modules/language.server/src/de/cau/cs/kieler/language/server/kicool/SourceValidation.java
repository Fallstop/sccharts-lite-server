package de.cau.cs.kieler.language.server.kicool;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.util.CancelIndicator;
import de.cau.cs.kieler.core.diagnostics.Issue;
import de.cau.cs.kieler.core.diagnostics.SourceTrace;
import de.cau.cs.kieler.language.server.kicool.data.CompilationResults;
import de.cau.cs.kieler.language.server.kicool.data.SnapshotDescription;

/** Rejects incomplete source models before transformations assume their references are resolved. */
public final class SourceValidation {
    /** Expected input failures must not leave an exceptional read in Xtext's request queue. */
    public static final class ReadResult {
        private Object value;
        private Exception failure;
        public Object getValue() throws Exception {
            if (failure != null) throw failure;
            return value;
        }
    }

    public static ReadResult capture(Supplier<Object> read) {
        ReadResult result = new ReadResult();
        try { result.value = read.get(); }
        catch (Exception failure) { result.failure = failure; }
        return result;
    }

    public static final class InvalidSource extends IllegalArgumentException {
        public final List<Issue> issues;
        InvalidSource(List<Issue> issues) {
            super(issues.get(0).message);
            this.issues = issues;
        }
    }

    public static void requireSystem(String systemId, String uri) {
        if (systemId != null && de.cau.cs.kieler.kicool.registration.KiCoolRegistration.hasSystemWithId(systemId)) return;
        Issue issue = new Issue("compilation-system", "Compilation system " + systemId + " is unavailable.");
        issue.hint = "Choose an available compilation system. If it belongs to the workspace, check its .kico file for errors.";
        issue.locations.add(new Issue.Location(uri, 0, 0, systemId));
        throw new InvalidSource(List.of(issue));
    }

    public static void requireValid(Object model, String uri) {
        if (model == null) throw invalid(uri, "There is no SCChart to compile. Add a scchart declaration first.");
        if (model instanceof EObject && ((EObject) model).eResource() != null) {
            requireValid(((EObject) model).eResource());
        }
    }

    public static void requireValid(Resource resource) {
        if (resource instanceof de.cau.cs.kieler.sccharts.text.SCTXResource) {
            var dependencies = ((de.cau.cs.kieler.sccharts.text.SCTXResource) resource).getAllImports().values();
            var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Resource, Boolean>());
            for (Resource dependency : new ArrayList<>(dependencies)) {
                if (visited.add(dependency)) validateResource(dependency);
            }
        } else {
            validateResource(resource);
        }
    }

    private static void validateResource(Resource resource) {
        if (!(resource instanceof XtextResource)) return;
        XtextResource source = (XtextResource) resource;
        String uri = source.getURI().toString();
        List<Issue> errors = new ArrayList<>();
        // Linking is lazy. Resolve it before inspecting resource errors or making a detached working copy.
        EcoreUtil.resolveAll(source);
        for (Resource.Diagnostic problem : source.getErrors()) {
            Issue issue = new Issue("source-validation", problem.getMessage());
            issue.hint = "Fix this source error before compiling again.";
            int offset = problem instanceof Diagnostic ? ((Diagnostic) problem).getOffset() : 0;
            int length = problem instanceof Diagnostic ? ((Diagnostic) problem).getLength() : 0;
            issue.locations.add(location(source, offset, length, problem.getMessage()));
            errors.add(issue);
        }
        if (!errors.isEmpty()) throw new InvalidSource(errors);
        for (var problem : source.getResourceServiceProvider().getResourceValidator().validate(source, CheckMode.FAST_ONLY, CancelIndicator.NullImpl)) {
            if (problem.getSeverity() != Severity.ERROR) continue;
            String code = problem.getCode();
            String message = problem.getMessage();
            // Other semantic checks have more detailed, traced explanations in their compiler processors.
            if (!problem.isSyntaxError() && !Diagnostic.LINKING_DIAGNOSTIC.equals(code)
                && !message.startsWith("Every region must") && !message.startsWith("Connector states must")) continue;
            Issue issue = new Issue("source-validation", message);
            issue.hint = "Fix this source error before compiling again.";
            issue.locations.add(location(source, problem.getOffset() == null ? 0 : problem.getOffset(),
                problem.getLength() == null ? 0 : problem.getLength(), message));
            errors.add(issue);
        }
        if (!errors.isEmpty()) throw new InvalidSource(errors);
        if (source.getContents().isEmpty()) throw invalid(uri, "There is no SCChart to compile. Add a scchart declaration first.");
    }

    private static InvalidSource invalid(String uri, String message) {
        Issue issue = new Issue("source-validation", message);
        issue.hint = "Start with scchart Name { initial state Idle }.";
        Issue.Location location = new Issue.Location(uri, 0, 0, message);
        location.line = 0;
        location.column = 0;
        issue.locations.add(location);
        return new InvalidSource(List.of(issue));
    }

    private static Issue.Location location(XtextResource source, int offset, int length, String label) {
        Issue.Location location = new Issue.Location(source.getURI().toString(), offset, length, label);
        return SourceTrace.withPosition(location, source.getParseResult() == null ? null : source.getParseResult().getRootNode());
    }

    public static Issue internalFailure(String stage, Throwable failure, Object model, String uri) {
        Issue issue = Issue.at("internal-compiler-error", "The compiler failed during " + stage + ".",
            "Retry compilation. If it happens again, copy the diagnostic report and include the model when reporting the problem.",
            model instanceof EObject ? (EObject) model : null);
        StringWriter details = new StringWriter();
        failure.printStackTrace(new PrintWriter(details));
        issue.details = details.toString();
        if (issue.locations.isEmpty() && uri != null) issue.locations.add(new Issue.Location(uri, 0, 0, stage));
        return issue;
    }

    public static CompilationResults failed(String uri, Exception failure, Object model) {
        List<Issue> issues = failure instanceof InvalidSource ? ((InvalidSource) failure).issues
            : List.of(internalFailure("compilation setup", failure, model, uri));
        SnapshotDescription stage = new SnapshotDescription(failure instanceof InvalidSource ? "Source Validation" : "Compiler", 0, 0, null, null, null);
        stage.status = "error";
        stage.processorId = "source-validation";
        stage.diagnostics.addAll(issues);
        issues.forEach(issue -> stage.getErrors().add(issue.message));
        CompilationResults result = new CompilationResults(List.of(List.of(stage)));
        result.processorCount = 0;
        result.totalMs = 0L;
        result.processors = List.of();
        return result;
    }
}
