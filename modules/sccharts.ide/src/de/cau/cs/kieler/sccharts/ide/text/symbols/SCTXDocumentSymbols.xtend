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
package de.cau.cs.kieler.sccharts.ide.text.symbols

import de.cau.cs.kieler.kexpressions.ValueType
import de.cau.cs.kieler.kexpressions.ValuedObject
import de.cau.cs.kieler.kexpressions.VariableDeclaration
import de.cau.cs.kieler.sccharts.ControlflowRegion
import de.cau.cs.kieler.sccharts.DataflowRegion
import de.cau.cs.kieler.sccharts.Region
import de.cau.cs.kieler.sccharts.State
import org.eclipse.emf.ecore.EObject
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper.DocumentSymbolDetailsProvider
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper.DocumentSymbolKindProvider
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper.DocumentSymbolNameProvider
import org.eclipse.xtext.naming.QualifiedName

/**
 * Outline entries for SCTX: simple names instead of dotted paths, a kind per element type and a detail
 * naming the declaration.
 */
class SCTXSymbolNameProvider extends DocumentSymbolNameProvider {
    override protected getName(QualifiedName name) {
        name === null || name.empty ? null : name.lastSegment
    }
}

class SCTXSymbolKindProvider extends DocumentSymbolKindProvider {
    override getSymbolKind(EObject object) {
        switch object {
            State: object.parentRegion === null ? SymbolKind.Class : SymbolKind.Struct
            ControlflowRegion: SymbolKind.Namespace
            DataflowRegion: SymbolKind.Module
            ValuedObject: {
                val declaration = object.eContainer
                if (declaration instanceof VariableDeclaration) {
                    if (declaration.signal) SymbolKind.Event
                    else if (declaration.const) SymbolKind.Constant
                    else if (declaration.input || declaration.output) SymbolKind.Property
                    else SymbolKind.Variable
                } else {
                    SymbolKind.Variable
                }
            }
            default: super.getSymbolKind(object)
        }
    }
}

class SCTXSymbolDetailsProvider extends DocumentSymbolDetailsProvider {
    override getDetails(EObject object) {
        switch object {
            State: {
                val flags = newArrayList
                if (object.initial) flags += "initial"
                if (object.final) flags += "final"
                if (object.connector) flags += "connector"
                flags += object.parentRegion === null ? "root state" : "state"
                if (!object.regions.empty) flags += '''«object.regions.size» region«object.regions.size == 1 ? "" : "s"»'''
                flags.join(" ")
            }
            ControlflowRegion: '''controlflow region, «object.states.size» state«object.states.size == 1 ? "" : "s"»'''
            DataflowRegion: "dataflow region"
            Region: "region"
            ValuedObject: {
                val declaration = object.eContainer
                if (declaration instanceof VariableDeclaration) {
                    val parts = newArrayList
                    if (declaration.const) parts += "const"
                    if (declaration.input) parts += "input"
                    if (declaration.output) parts += "output"
                    if (declaration.signal) parts += "signal"
                    parts += declaration.type == ValueType.HOST && !declaration.hostType.nullOrEmpty ? declaration.hostType : declaration.type.literal
                    if (!object.cardinalities.empty) parts += "array"
                    parts.join(" ")
                } else {
                    ""
                }
            }
            default: super.getDetails(object)
        }
    }
}
