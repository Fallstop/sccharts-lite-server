/*
 * SCCharts Lab - sccharts-lite server
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.diagram

import com.google.inject.Inject
import com.google.inject.Singleton
import de.cau.cs.kieler.klighd.ViewContext
import de.cau.cs.kieler.klighd.kgraph.KNode
import de.cau.cs.kieler.klighd.lsp.KGraphDiagramServer
import de.cau.cs.kieler.klighd.lsp.KGraphDiagramState
import de.cau.cs.kieler.klighd.lsp.KGraphDiagramUpdater
import de.cau.cs.kieler.klighd.lsp.KGraphLanguageServerExtension
import de.cau.cs.kieler.sccharts.Region
import de.cau.cs.kieler.sccharts.State
import de.cau.cs.kieler.sccharts.Transition
import de.cau.cs.kieler.sccharts.ui.synthesis.hooks.actions.MemorizingExpandCollapseAction
import java.net.URLDecoder
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.List
import java.util.Set
import java.util.concurrent.CompletableFuture
import org.apache.log4j.Logger
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.ide.server.ILanguageServerAccess
import org.eclipse.xtext.ide.server.ILanguageServerExtension
import org.eclipse.xtext.resource.EObjectAtOffsetHelper
import org.eclipse.xtext.resource.XtextResource

import static extension de.cau.cs.kieler.klighd.kgraph.util.KGraphIterators.*

/**
 * Mirrors the editor cursor in the diagram: the state, region or transition under the cursor is selected and every
 * region on the path to it is expanded. In {@code expand} mode the regions off that path are collapsed again, unless
 * the user expanded them and the synthesis remembers expansion states, which is what the Eclipse editor's
 * SmartCollapseHook did through the editor's cursor listener.
 */
@Singleton
class CursorSyncLanguageServerExtension implements ILanguageServerExtension, CursorCommandExtension {

    static val LOG = Logger.getLogger(CursorSyncLanguageServerExtension)

    @Inject KGraphLanguageServerExtension diagramLanguageServer
    @Inject KGraphDiagramState diagramState

    val offsetHelper = new EObjectAtOffsetHelper

    override initialize(ILanguageServerAccess access) {
        // Contributed extensions are plain JSON-RPC delegates; the access object is taken from the diagram
        // language server, which Xtext does initialise.
    }

    override cursor(CursorParam param) {
        if (param === null || param.uri.nullOrEmpty || param.clientId.nullOrEmpty) {
            return CompletableFuture.completedFuture(CursorResult.fail("uri and clientId are required"))
        }
        val uri = URLDecoder.decode(param.uri, "UTF-8")
        val collapseOthers = "expand".equals(param.mode)
        val access = diagramLanguageServer.languageServerAccess
        if (access === null) {
            return CompletableFuture.completedFuture(CursorResult.fail("The language server is not initialised yet"))
        }
        return access.doRead(uri) [ context |
            try {
                val resource = context.resource
                if (!(resource instanceof XtextResource) || resource.contents.empty) {
                    return CursorResult.fail("The document holds no model")
                }
                val length = if (context.document === null) 0 else context.document.contents.length
                if (param.offset < 0 || param.offset > length) {
                    return CursorResult.fail("The cursor offset lies outside the document")
                }
                val hit = offsetHelper.resolveContainedElementAt(resource as XtextResource, param.offset)
                if (hit === null) {
                    return CursorResult.fail("Nothing under the cursor")
                }
                // The innermost element the diagram can show, plus the state/region containers around it.
                var EObject innermost = null
                val chain = new ArrayList<EObject>
                var current = hit
                while (current !== null) {
                    if (current instanceof State || current instanceof Region || current instanceof Transition) {
                        if (innermost === null) innermost = current
                        if (!(current instanceof Transition)) chain.add(current)
                    }
                    current = current.eContainer
                }
                if (innermost === null) {
                    return CursorResult.fail("Nothing under the cursor is shown in the diagram")
                }
                return sync(uri, param.clientId, resource, innermost, chain, collapseOthers)
            } catch (Exception e) {
                LOG.warn("Cursor sync failed", e)
                return CursorResult.fail("Cursor sync failed: " + e)
            }
        ]
    }

    /**
     * Applies the cursor to the diagram of {@code clientId}. The diagram's input model is either the resource's own
     * model or a separately parsed copy of it (after showing the source model through keith/kicool/show); a compiled
     * snapshot has no source resource and is left alone.
     */
    private def CursorResult sync(String uri, String clientId, org.eclipse.emf.ecore.resource.Resource resource,
        EObject innermost, List<EObject> chain, boolean collapseOthers
    ) {
        val server = diagramLanguageServer.diagramServerManager.diagramServers.filter(KGraphDiagramServer)
            .findFirst[clientId.equals(it.clientId)]
        if (server === null) {
            return CursorResult.fail("No diagram is open for client " + clientId)
        }
        var ViewContext viewContext
        var Object input
        var String diagramUri
        synchronized (diagramState) {
            diagramUri = diagramState.getURIString(clientId)
            if (diagramUri === null || !sameDocument(diagramUri, uri)) {
                return CursorResult.fail("The diagram shows another file")
            }
            // The updater keys the context by the client's own spelling of the URI (percent-encoded on
            // Windows); decoding is only a fallback for callers that stored it decoded.
            viewContext = diagramState.getKGraphContext(diagramUri)
                ?: diagramState.getKGraphContext(URLDecoder.decode(diagramUri, "UTF-8"))
            input = viewContext?.inputModel
        }
        if (viewContext === null || viewContext.viewer === null || viewContext.viewModel === null) {
            return CursorResult.fail("The diagram has not been generated yet")
        }
        val root = resource.contents.head
        val mapped = new ArrayList<EObject>
        var EObject mappedInnermost
        if (input === root) {
            mapped.addAll(chain)
            mappedInnermost = innermost
        } else if (input instanceof EObject && (input as EObject).eResource !== null
            && sameDocument((input as EObject).eResource.URI.toString, uri)) {
            // The same text parsed again: URI fragments are structural, so they address the twin objects.
            val twin = (input as EObject).eResource
            for (element : chain) {
                val match = twin.getEObject(resource.getURIFragment(element))
                if (match !== null) mapped.add(match)
            }
            mappedInnermost = twin.getEObject(resource.getURIFragment(innermost))
        } else {
            return CursorResult.fail("The diagram shows a compilation snapshot; show the source model to follow the cursor")
        }
        if (mappedInnermost === null && mapped.empty) {
            return CursorResult.fail("The element under the cursor is not part of the shown model")
        }

        val viewer = viewContext.viewer
        val result = new CursorResult
        result.element = describe(resource, innermost)
        val Set<EObject> onPath = new LinkedHashSet(mapped)
        synchronized (diagramState) {
            // Everything from the root down to the cursor becomes visible.
            for (element : mapped) {
                for (target : viewContext.getTargetElements(element)) {
                    if (target instanceof KNode && !viewer.isExpanded(target as KNode)) {
                        viewer.expand(target as KNode)
                        if (element instanceof Region) MemorizingExpandCollapseAction.SCOPE_STATES.put(element, true)
                        result.expanded++
                    }
                }
            }
            if (collapseOthers) {
                val memorize = viewContext.getOptionValue(MemorizingExpandCollapseAction.MEMORIZE_EXPANSION_STATES) as Boolean
                for (node : viewContext.viewModel.getKNodeIterator(false).toIterable) {
                    val source = viewContext.getSourceElement(node)
                    if (source instanceof Region && !onPath.contains(source) && viewer.isExpanded(node)) {
                        val remembered = MemorizingExpandCollapseAction.getExpansionState(source as EObject)
                        if (!(memorize ?: false) || !(remembered ?: false)) {
                            viewer.collapse(node)
                            result.collapsed++
                        }
                    }
                }
            }
        }
        val List<EObject> selection = new ArrayList
        if (mappedInnermost !== null) selection.addAll(viewContext.getTargetElements(mappedInnermost))
        val updater = diagramLanguageServer.diagramUpdater
        if (result.expanded > 0 || result.collapsed > 0) {
            if (updater instanceof KGraphDiagramUpdater) {
                // The relayout regenerates the SGraph (and its id map); select once that has been sent.
                updater.updateLayout(server).thenCompose[it].thenRun [
                    if (!selection.empty) server.selectElements(selection)
                ]
            }
        } else if (!selection.empty) {
            server.selectElements(selection)
        }
        result.ok = true
        return result
    }

    private static def CursorResult.Element describe(org.eclipse.emf.ecore.resource.Resource resource, EObject element) {
        val kind = element.eClass.name
        val name = switch element {
            State: element.name ?: element.label
            Region: element.name ?: element.label
            Transition: element.label ?: ((element.sourceState?.name ?: "?") + " -> " + (element.targetState?.name ?: "?"))
            default: null
        }
        return new CursorResult.Element(kind, name, resource.getURIFragment(element))
    }

    /** Two URI strings of the same file, ignoring percent-encoding differences. */
    private static def boolean sameDocument(String a, String b) {
        val left = URLDecoder.decode(a, "UTF-8")
        val right = URLDecoder.decode(b, "UTF-8")
        return left.equals(right) || left.replace("file:///", "file:/").equals(right.replace("file:///", "file:/"))
    }
}
