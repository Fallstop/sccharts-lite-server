package de.cau.cs.kieler.sccharts.text

import java.io.File
import java.util.Collection
import org.eclipse.xtext.resource.IResourceDescription
import org.eclipse.xtext.resource.IResourceDescription.Delta
import org.eclipse.xtext.resource.IResourceDescriptions
import org.eclipse.xtext.resource.impl.DefaultResourceDescriptionManager

/**
 * Imports are resolved through the resource set, not the index, so a change to an imported file that keeps its exported
 * names would otherwise leave its importers' import errors stale.
 */
class SCTXResourceDescriptionManager extends DefaultResourceDescriptionManager implements IResourceDescription.Manager.AllChangeAware {

    override isAffectedByAny(Collection<Delta> deltas, IResourceDescription candidate, IResourceDescriptions context) {
        if (isAffected(deltas, candidate, context)) return true
        val imports = candidate.exportedObjects.map[getUserData(SCTXResourceDescriptionStrategy.IMPORTS)].filterNull
            .flatMap[split("\n").toList].filter[!empty].toSet
        if (imports.empty) return false
        return deltas.exists [ delta |
            val changed = SCTXResourceDescriptionStrategy.path(delta.uri)
            changed !== null && imports.exists[it == changed || endsWith(File.separator) && changed.startsWith(it)]
        ]
    }
}
