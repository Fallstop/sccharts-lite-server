/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 *
 * Copyright 2026 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.kicool;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.ILanguageServerExtension;
import org.eclipse.xtext.ide.server.ProjectManager;
import org.eclipse.xtext.ide.server.UriExtensions;
import org.eclipse.xtext.ide.server.WorkspaceManager;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.LineAndColumn;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import de.cau.cs.kieler.klighd.lsp.KGraphLanguageServerExtension;

import de.cau.cs.kieler.kicool.KiCoolPackage;
import de.cau.cs.kieler.kicool.ProcessorReference;
import de.cau.cs.kieler.kicool.ProcessorSystem;
import de.cau.cs.kieler.kicool.ide.KiCoolIdeSetup;
import de.cau.cs.kieler.kicool.registration.KiCoolRegistration;
import de.cau.cs.kieler.language.server.ILanguageClientProvider;
import de.cau.cs.kieler.language.server.KeithLanguageClient;
import de.cau.cs.kieler.language.server.kicool.data.SystemFoldersParam;
import de.cau.cs.kieler.language.server.kicool.data.SystemsChangedParam;
import de.cau.cs.kieler.language.server.kicool.data.WorkspaceSystemInfo;

/**
 * Loads compilation systems that users define in {@code .kico} files of their workspace and registers them with
 * {@link KiCoolRegistration} as temporary systems, so they appear next to the built-in systems in the compile menu
 * and compile like any other system. This replaces the Eclipse-only registration (extension point plus
 * {@code Register} class) that the sccharts-lite build removed.
 * <p>
 * Every {@code .kico} inside a workspace folder is loaded, the moment the Xtext build sees it (initial scan, an
 * edit in the editor, a file change on disk). Directories outside the workspace can be added with
 * {@code keith/kicool/systemFolders}. Files that fail to load are reported as diagnostics on the file and through
 * {@code keith/kicool/systemsChanged}; nothing is skipped silently.
 */
@Singleton
public class WorkspaceSystemsExtension implements ILanguageServerExtension, WorkspaceSystemsCommandExtension,
        ILanguageClientProvider, ILanguageServerAccess.IBuildListener {

    private static final Logger LOG = Logger.getLogger(WorkspaceSystemsExtension.class);
    private static final String EXTENSION = "kico";
    private static final String SOURCE = "KIELER · compilation systems";
    /** Build output and dependency folders are not where anybody keeps compilation systems. */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("node_modules", "target", "out", "dist", "build", "bin", "kieler-gen");
    private static final int MAX_DEPTH = 8;

    /** System id per registered file (keyed by path), and the file URI per system id for labelling the systems. */
    private static final Map<String, String> ID_BY_FILE = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, String> SOURCE_BY_ID = Collections.synchronizedMap(new LinkedHashMap<>());

    private final Map<String, WorkspaceSystemInfo> files = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "workspace-kico");
        thread.setDaemon(true);
        return thread;
    });

    private WorkspaceManager workspaceManager;

    /** Runs on the worker; an executor would swallow a failure, which then only shows as silence. */
    private void submit(Runnable task) {
        worker.submit(() -> {
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                LOG.error("Workspace compilation systems: " + e, e);
                System.err.println("Workspace compilation systems failed: " + e);
                e.printStackTrace();
                if (client != null) client.sendMessage("Workspace compilation systems: " + e, "error");
            }
        });
    }
    private UriExtensions uriExtensions;
    private KGraphLanguageServerExtension languageServer;
    private KeithLanguageClient client;
    private List<String> extraFolders = new ArrayList<>();
    private List<String> clientWorkspaceFolders = new ArrayList<>();
    private boolean scanned;

    /** Files are keyed by their normalised path; URIs differ in spelling between EMF, Xtext and the client. */
    private static String key(URI uri) {
        if (uri.isFile()) {
            Path path = toPath(uri);
            if (path != null) return canonical(path).toString();
        }
        return uri.toString();
    }

    /**
     * The file behind a {@code file:} URI. EMF's {@code toFileString} keeps an empty authority as a
     * leading {@code //}, which Windows reads as a malformed UNC path ({@code \\\C:\...}), so a URI with
     * a device is assembled from its parts; a real authority (a UNC share) goes through EMF.
     */
    private static Path toPath(URI uri) {
        try {
            if (uri.device() != null) return Path.of(uri.device() + URI.decode(uri.path()));
            if (uri.authority() != null && !uri.authority().isEmpty()) return Path.of(uri.toFileString());
            return Path.of(URI.decode(uri.path()));
        } catch (RuntimeException e) {
            try {
                return Path.of(new java.net.URI(uri.toString()));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** The URI spelling the client uses ({@code file:///...}), for diagnostics and reports. */
    private String lspUri(URI uri) {
        return uriExtensions == null ? uri.toString() : uriExtensions.toUriString(uri);
    }

    /** The file a workspace system was loaded from, or null for built-in systems. */
    public static String sourceOf(String systemId) {
        synchronized (SOURCE_BY_ID) {
            return SOURCE_BY_ID.get(systemId);
        }
    }

    /**
     * The KIELER extensions are not bound per language, so Xtext never calls {@link #initialize}; the build
     * listener is registered directly with the (singleton) workspace manager instead.
     */
    @Inject
    void connect(WorkspaceManager workspaceManager, KGraphLanguageServerExtension languageServer, UriExtensions uriExtensions) {
        this.workspaceManager = workspaceManager;
        this.languageServer = languageServer;
        this.uriExtensions = uriExtensions;
        workspaceManager.addBuildListener(this);
    }

    @Override
    public void initialize(ILanguageServerAccess access) {
        // Not called for server-wide extensions; see connect().
    }

    @Override
    public void setLanguageClient(LanguageClient client) {
        this.client = (KeithLanguageClient) client;
    }

    @Override
    public LanguageClient getLanguageClient() {
        return client;
    }

    @Override
    public void afterBuild(List<IResourceDescription.Delta> deltas) {
        List<URI> changed = new ArrayList<>();
        for (IResourceDescription.Delta delta : deltas) {
            if (EXTENSION.equals(delta.getUri().fileExtension())) changed.add(delta.getUri());
        }
        if (changed.isEmpty() && scanned) return;
        // The build holds the write lock; reading the resources back goes through the request manager and must
        // not happen on this thread.
        submit(() -> {
            if (!scanned) {
                // The first build: the workspace is complete now, so load everything, including root-level
                // files that are not part of any Xtext project.
                rescan();
                return;
            }
            SystemsChangedParam changes = new SystemsChangedParam();
            for (URI uri : changed) {
                if (isFile(uri)) load(uri, changes);
                else unload(key(uri), changes);
            }
            publish(changes);
        });
    }

    @Override
    public void systemFolders(SystemFoldersParam param) {
        extraFolders = param == null || param.folders == null ? new ArrayList<>() : new ArrayList<>(param.folders);
        clientWorkspaceFolders = param == null || param.workspaceFolders == null ? new ArrayList<>() : new ArrayList<>(param.workspaceFolders);
        submit(this::rescan);
    }

    @Override
    public CompletableFuture<List<WorkspaceSystemInfo>> workspaceSystems() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (files) {
                List<WorkspaceSystemInfo> result = new ArrayList<>();
                for (Map.Entry<String, WorkspaceSystemInfo> entry : files.entrySet()) {
                    WorkspaceSystemInfo info = entry.getValue();
                    info.key = entry.getKey();
                    result.add(info);
                }
                result.sort(Comparator.comparing(info -> info.file));
                return result;
            }
        }, worker);
    }

    /** Scans the workspace folders and the extra folders; files that vanished are unregistered. */
    private void rescan() {
        scanned = true;
        SystemsChangedParam changes = new SystemsChangedParam();
        Set<URI> candidates = new LinkedHashSet<>();
        List<Path> folders = new ArrayList<>(workspaceRoots());
        folders.addAll(extraFolders());
        for (Path folder : folders) {
            try {
                Files.walkFileTree(folder, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                        String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
                        return !directory.equals(folder) && SKIPPED_DIRECTORIES.contains(name) || name.startsWith(".") && !directory.equals(folder)
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (isKicoFile(file)) candidates.add(URI.createFileURI(canonical(file).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                changes.errors.add(new WorkspaceSystemInfo(folder.toString(), null, null, false, "Cannot read folder: " + e.getMessage()));
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (URI uri : candidates) seen.add(key(uri));
        // Vanished (or re-spelled) files go first, so a file that comes back under another key does not
        // collide with its own earlier registration.
        for (String file : new ArrayList<>(files.keySet())) {
            if (!seen.contains(file)) unload(file, changes);
        }
        for (URI uri : candidates) load(uri, changes);
        publish(changes);
    }

    /**
     * One spelling per file: the real path resolves symlinks and Windows 8.3 short names (a client may say
     * {@code C:\\Users\\RUNNER~1} for what Xtext calls {@code C:\\Users\\runneradmin}); on a case-insensitive
     * file system the drive letter is lowered as well.
     */
    private static Path canonical(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            absolute = absolute.toRealPath();
        } catch (IOException | RuntimeException e) {
            // A file that vanished: its directory usually still exists and settles the spelling.
            Path parent = absolute.getParent();
            if (parent != null && absolute.getFileName() != null) {
                try {
                    absolute = parent.toRealPath().resolve(absolute.getFileName());
                } catch (IOException | RuntimeException ignored) {
                    // Keep the normalised spelling.
                }
            }
        }
        String text = absolute.toString();
        if (text.length() > 1 && text.charAt(1) == ':' && Character.isUpperCase(text.charAt(0))) {
            return Path.of(Character.toLowerCase(text.charAt(0)) + text.substring(1));
        }
        return absolute;
    }

    /** Two keys name the same file, also when only one of them could be resolved to its real path. */
    private static boolean sameFile(String a, String b) {
        if (a.equals(b)) return true;
        try {
            return Files.isSameFile(Path.of(a), Path.of(b));
        } catch (IOException | RuntimeException e) {
            return a.equalsIgnoreCase(b);
        }
    }

    private static boolean isKicoFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith("." + EXTENSION);
    }

    /**
     * The workspace roots: derived from the Xtext projects (every direct subdirectory of a workspace folder is
     * one) and the folders the client named, which covers workspace folders without subdirectories.
     */
    private List<Path> workspaceRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        if (workspaceManager != null) {
            try {
                for (ProjectManager project : workspaceManager.getProjectManagers()) {
                    // Xtext's catch-all "unknown" project has no path.
                    URI path = project.getProjectConfig() == null ? null : project.getProjectConfig().getPath();
                    Path directory = path != null && path.isFile() ? toPath(path) : null;
                    if (directory != null) {
                        if (directory.getParent() != null && Files.isDirectory(directory.getParent())) roots.add(canonical(directory.getParent()));
                    }
                }
            } catch (Exception e) {
                LOG.debug("No workspace projects yet", e);
            }
        }
        for (String folder : clientWorkspaceFolders) {
            Path path = Path.of(folder);
            if (path.isAbsolute() && Files.isDirectory(path)) roots.add(canonical(path));
        }
        return new ArrayList<>(roots);
    }

    /** The configured extra folders, absolute or relative to each workspace root; walked recursively. */
    private List<Path> extraFolders() {
        List<Path> roots = workspaceRoots();
        Set<Path> result = new LinkedHashSet<>();
        for (String extra : extraFolders) {
            Path path = Path.of(extra);
            if (path.isAbsolute()) {
                if (Files.isDirectory(path)) result.add(canonical(path));
            } else {
                for (Path root : roots) {
                    Path candidate = root.resolve(path);
                    if (Files.isDirectory(candidate)) result.add(canonical(candidate));
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static boolean isFile(URI uri) {
        if (!uri.isFile()) return false;
        Path path = toPath(uri);
        return path != null && Files.isRegularFile(path);
    }

    /** Loads one file: from the language server's copy when it is part of the workspace, else from disk. */
    private void load(URI uri, SystemsChangedParam changes) {
        String key = key(uri);
        // Diagnostics for a file must always go out under one spelling of its URI, or a client that
        // received them under the first spelling keeps showing them after they are cleared under another.
        WorkspaceSystemInfo known = files.get(key);
        String file = known != null && known.file != null ? known.file : lspUri(uri);
        // The read goes through Xtext under the spelling the build reported, which is the one it indexes.
        String readUri = lspUri(uri);
        Loaded loaded = null;
        try {
            if (languageServer != null) {
                loaded = languageServer.doRead(readUri, (resource, cancel) -> {
                    if (!(resource instanceof XtextResource)) return null;
                    return inspect((XtextResource) resource, cancel, key);
                }).get(20, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOG.debug("Reading " + readUri + " through the language server failed; loading it from disk", e);
        }
        try {
            if (loaded == null) loaded = inspect(parse(uri), CancelIndicator.NullImpl, key);
        } catch (Exception e) {
            loaded = new Loaded();
            loaded.diagnostics.add(diagnostic("Cannot load compilation system: " + e, DiagnosticSeverity.Error, new Range(new Position(0, 0), new Position(0, 1))));
        }
        WorkspaceSystemInfo previous = files.get(key);
        String previousId = ID_BY_FILE.get(key);
        if (previousId != null && (loaded.system == null || !previousId.equals(loaded.system.getId()))) {
            KiCoolRegistration.removeTemporarySystem(previousId);
            ID_BY_FILE.remove(key);
            SOURCE_BY_ID.remove(previousId);
            if (previous != null && previous.loaded) changes.removed.add(previous);
        }
        WorkspaceSystemInfo info;
        if (loaded.system == null) {
            String error = loaded.diagnostics.stream().filter(d -> d.getSeverity() == DiagnosticSeverity.Error)
                .map(Diagnostic::getMessage).findFirst().orElse("The file does not define a compilation system.");
            info = new WorkspaceSystemInfo(file, null, null, false, error);
            changes.errors.add(info);
        } else {
            String id = loaded.system.getId();
            try {
                if (previousId == null || !previousId.equals(id)) {
                    KiCoolRegistration.registerTemporarySystem(loaded.system);
                } else {
                    // Same id as before: swap the model behind it.
                    KiCoolRegistration.removeTemporarySystem(id);
                    KiCoolRegistration.registerTemporarySystem(loaded.system);
                }
                ID_BY_FILE.put(key, id);
                SOURCE_BY_ID.put(id, file);
                info = new WorkspaceSystemInfo(file, id, loaded.system.getLabel(), true, null);
                boolean same = previous != null && previous.loaded && id.equals(previous.id) && java.util.Objects.equals(previous.label, info.label);
                if (!same) changes.added.add(info);
            } catch (Exception e) {
                String message = "Cannot register compilation system '" + id + "': " + e.getMessage();
                loaded.diagnostics.add(diagnostic(message, DiagnosticSeverity.Error, loaded.idRange));
                info = new WorkspaceSystemInfo(file, id, loaded.system.getLabel(), false, message);
                changes.errors.add(info);
            }
        }
        files.put(key, info);
        if (client != null) client.publishDiagnostics(new PublishDiagnosticsParams(file, loaded.diagnostics));
    }

    private void unload(String key, SystemsChangedParam changes) {
        WorkspaceSystemInfo info = files.remove(key);
        String id = ID_BY_FILE.remove(key);
        if (id != null) {
            KiCoolRegistration.removeTemporarySystem(id);
            SOURCE_BY_ID.remove(id);
        }
        if (info != null && info.loaded) changes.removed.add(info);
        if (info != null && client != null) client.publishDiagnostics(new PublishDiagnosticsParams(info.file, new ArrayList<>()));
    }

    private void publish(SystemsChangedParam changes) {
        if (client == null) return;
        if (changes.added.isEmpty() && changes.removed.isEmpty() && changes.errors.isEmpty()) return;
        try {
            client.systemsChanged(changes);
        } catch (Exception e) {
            LOG.error("Cannot report changed compilation systems", e);
        }
    }

    private static XtextResource parse(URI uri) {
        XtextResourceSet resourceSet = KiCoolIdeSetup.doSetup().getInstance(XtextResourceSet.class);
        return (XtextResource) resourceSet.getResource(uri, true);
    }

    /** Validates the resource and checks the ids it references; the system is returned only when there is no error. */
    /** @param key the loaded file's own key, so that a re-read file is never its own rival for an id */
    private static Loaded inspect(XtextResource resource, CancelIndicator cancel, String key) {
        Loaded loaded = new Loaded();
        IResourceValidator validator = resource.getResourceServiceProvider().getResourceValidator();
        boolean errors = false;
        for (Issue issue : validator.validate(resource, CheckMode.ALL, cancel)) {
            DiagnosticSeverity severity = issue.getSeverity() == Severity.ERROR ? DiagnosticSeverity.Error
                : issue.getSeverity() == Severity.WARNING ? DiagnosticSeverity.Warning : DiagnosticSeverity.Information;
            errors |= severity == DiagnosticSeverity.Error;
            int line = Math.max(0, (issue.getLineNumber() == null ? 1 : issue.getLineNumber()) - 1);
            int column = Math.max(0, (issue.getColumn() == null ? 1 : issue.getColumn()) - 1);
            int lineEnd = issue.getLineNumberEnd() == null ? line : Math.max(0, issue.getLineNumberEnd() - 1);
            int columnEnd = issue.getColumnEnd() == null ? column + 1 : Math.max(0, issue.getColumnEnd() - 1);
            loaded.diagnostics.add(diagnostic(issue.getMessage(), severity, new Range(new Position(line, column), new Position(lineEnd, columnEnd))));
        }
        EObject root = resource.getContents().isEmpty() ? null : resource.getContents().get(0);
        if (!(root instanceof de.cau.cs.kieler.kicool.System)) {
            if (!errors) loaded.diagnostics.add(diagnostic("The file does not define a compilation system.", DiagnosticSeverity.Error, new Range(new Position(0, 0), new Position(0, 1))));
            return loaded;
        }
        de.cau.cs.kieler.kicool.System system = (de.cau.cs.kieler.kicool.System) root;
        loaded.idRange = range(resource, NodeModelUtils.findNodesForFeature(system, KiCoolPackage.Literals.SYSTEM__ID));
        if (system.getId() == null || system.getId().isEmpty()) {
            errors = true;
        } else if (KiCoolRegistration.hasSystemWithId(system.getId()) && !KiCoolRegistration.isTemporarySystem(system.getId())) {
            errors = true;
            loaded.diagnostics.add(diagnostic("'" + system.getId() + "' is the id of a built-in compilation system; choose another id.", DiagnosticSeverity.Error, loaded.idRange));
        } else {
            String owner = ID_BY_FILE.entrySet().stream().filter(entry -> entry.getValue().equals(system.getId()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
            if (owner != null && !owner.equals(key) && !sameFile(owner, key)) {
                errors = true;
                loaded.diagnostics.add(diagnostic("'" + system.getId() + "' is already defined in " + sourceOf(system.getId()), DiagnosticSeverity.Error, loaded.idRange));
            }
        }
        for (EObject object : (Iterable<EObject>) () -> EcoreUtil.getAllContents(system, true)) {
            if (object instanceof ProcessorReference) {
                String id = ((ProcessorReference) object).getId();
                if (id != null && KiCoolRegistration.getProcessorClass(id) == null) {
                    errors = true;
                    loaded.diagnostics.add(diagnostic("Unknown processor '" + id + "'. Processor ids are the ones the built-in .kico systems use, for example de.cau.cs.kieler.scg.processors.codegen.c.",
                        DiagnosticSeverity.Error, range(resource, NodeModelUtils.findNodesForFeature(object, KiCoolPackage.Literals.PROCESSOR_ENTRY__ID))));
                }
            } else if (object instanceof ProcessorSystem) {
                String id = ((ProcessorSystem) object).getId();
                if (id != null && !KiCoolRegistration.hasSystemWithId(id)) {
                    errors = true;
                    loaded.diagnostics.add(diagnostic("Unknown compilation system '" + id + "'.", DiagnosticSeverity.Error,
                        range(resource, NodeModelUtils.findNodesForFeature(object, KiCoolPackage.Literals.PROCESSOR_ENTRY__ID))));
                }
            }
        }
        if (!errors) loaded.system = EcoreUtil.copy(system);
        return loaded;
    }

    private static Range range(XtextResource resource, List<INode> nodes) {
        ICompositeNode root = resource.getParseResult() == null ? null : resource.getParseResult().getRootNode();
        if (nodes.isEmpty() || root == null) return new Range(new Position(0, 0), new Position(0, 1));
        INode node = nodes.get(0);
        LineAndColumn start = NodeModelUtils.getLineAndColumn(root, node.getOffset());
        LineAndColumn end = NodeModelUtils.getLineAndColumn(root, node.getOffset() + node.getLength());
        return new Range(new Position(start.getLine() - 1, start.getColumn() - 1), new Position(end.getLine() - 1, end.getColumn() - 1));
    }

    private static Diagnostic diagnostic(String message, DiagnosticSeverity severity, Range range) {
        Diagnostic diagnostic = new Diagnostic(range, message, severity, SOURCE);
        return diagnostic;
    }

    private static final class Loaded {
        de.cau.cs.kieler.kicool.System system;
        Range idRange = new Range(new Position(0, 0), new Position(0, 1));
        final List<Diagnostic> diagnostics = new ArrayList<>();
    }
}
