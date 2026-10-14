package de.cau.cs.kieler.sccharts.text

import de.cau.cs.kieler.sccharts.SCCharts
import de.cau.cs.kieler.sccharts.State
import java.io.File
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.resource.EObjectDescription
import org.eclipse.xtext.resource.IEObjectDescription
import org.eclipse.xtext.resource.impl.DefaultResourceDescriptionStrategy
import org.eclipse.xtext.util.IAcceptor

/**
 * Records on each top-level chart's index entry the files its document imports, so that
 * {@link SCTXResourceDescriptionManager} revalidates importers when an imported file changes.
 */
class SCTXResourceDescriptionStrategy extends DefaultResourceDescriptionStrategy {

    /** Newline-separated file paths; a folder import ends in the path separator. */
    public static val IMPORTS = "imports"

    override createEObjectDescriptions(EObject obj, IAcceptor<IEObjectDescription> acceptor) {
        if (obj instanceof State && obj.eContainer instanceof SCCharts && obj.eResource !== null) {
            val name = qualifiedNameProvider.getFullyQualifiedName(obj)
            val imports = (obj.eContainer as SCCharts).imports
            if (name !== null && !imports.nullOrEmpty) {
                val base = obj.eResource.URI.trimSegments(1).appendSegment("").toString
                val paths = imports.map [
                    if (endsWith("*")) path(URI.createURI(base + substring(0, length - 1))) + File.separator
                    else path(URI.createURI(base + it + if (endsWith(".sctx")) "" else ".sctx"))
                ].filterNull
                acceptor.accept(EObjectDescription.create(name, obj, #{IMPORTS -> paths.join("\n")}))
                return true
            }
        }
        return super.createEObjectDescriptions(obj, acceptor)
    }

    /** A comparable path for a file URI, or null for other schemes. */
    static def String path(URI uri) {
        if (!uri.isFile) return null
        return new File(uri.toFileString).toPath.normalize.toString
    }
}
