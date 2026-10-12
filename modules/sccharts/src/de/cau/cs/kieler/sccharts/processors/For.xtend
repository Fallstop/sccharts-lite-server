/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2017 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.sccharts.processors

import com.google.inject.Inject
import de.cau.cs.kieler.core.diagnostics.Issue
import de.cau.cs.kieler.kexpressions.IntValue
import de.cau.cs.kieler.kexpressions.ValuedObjectReference
import de.cau.cs.kieler.kexpressions.extensions.KExpressionsCreateExtensions
import de.cau.cs.kieler.kexpressions.extensions.KExpressionsDeclarationExtensions
import de.cau.cs.kieler.kexpressions.extensions.KExpressionsSerializeHRExtensions
import de.cau.cs.kieler.kicool.kitt.tracing.Traceable
import de.cau.cs.kieler.sccharts.Region
import de.cau.cs.kieler.sccharts.State
import de.cau.cs.kieler.sccharts.extensions.SCChartsScopeExtensions
import de.cau.cs.kieler.sccharts.processors.SCChartsProcessor

import static extension de.cau.cs.kieler.kicool.kitt.tracing.TracingEcoreUtil.*

/**
 * 
 * @author ssm
 * @kieler.design 2017-08-01 proposed
 * @kieler.rating 2017-08-01 proposed yellow  
 */
class For extends SCChartsProcessor implements Traceable {
    
    @Inject extension KExpressionsDeclarationExtensions
    @Inject extension KExpressionsCreateExtensions
    @Inject extension SCChartsScopeExtensions
    @Inject extension KExpressionsSerializeHRExtensions
    
    override getId() {
        "de.cau.cs.kieler.sccharts.processors.for"
    }
    
    override getName() {
        "For"
    }
    
    override process() {
        val model = getModel
        
        for (rootState : model.rootStates) {
            if (!rootState.transformFor) return
        }
    }
    
    protected def boolean transformFor(State state) {
        val forRegions = state.getAllStates.map[regions.filter[ counterVariable !== null ]].toList.flatten.toList
        
        // getForRegionRange fails on these ranges in KIELER too: report them on the source instead.
        val unsupportedRanges = forRegions.filter[ forStart instanceof IntValue && !(forEnd instanceof IntValue) ].toList
        if (!unsupportedRanges.empty) {
            unsupportedRanges.forEach[ reportUnsupportedRange ]
            return false
        }
        
        for (r : forRegions) {
            r.transformForRegion
        }
        return true
    }
    
    /** Diagnostics: a range that starts at a number but has no end, or ends at a variable, as a located, explained issue. */
    private def reportUnsupportedRange(Region region) {
        val range = "for " + region.counterVariable.name + " : " + region.forStart.serializeHR
            + if (region.forEnd === null) "" else " to " + region.forEnd.serializeHR
        val portable = "Write both bounds as integer literals, such as \"for " + region.counterVariable.name
            + " : 0 to 2\", or loop over an array x with \"for " + region.counterVariable.name + " : x\" (indices 0 to its size - 1)."
        val issue = if (region.forEnd === null) {
            Issue.at("unsupported-construct", "The for-region range \"" + range + "\" has a start value but no end value.",
                "KIELER cannot compile a for-region that starts at a number without \"to <end>\". " + portable, region.forStart)
        } else {
            Issue.at("unsupported-construct", "The for-region range \"" + range + "\" ends at " + region.forEnd.serializeHR
                + ", which is not an integer literal.",
                "KIELER cannot compile a for-region that starts at a number and ends at a variable, even a const. " + portable,
                region.forEnd)
        }
        environment.errors.add(null, issue.message, region, issue)
    }
    
    protected def transformForRegion(Region region) {
        val range = region.forRegionRange
        region.transformForRegionRange(range.key, range.value)
    }
    
    static def Pair<Integer, Integer> getForRegionRange(Region region) {
        switch (region.forStart) {
            IntValue: {
                val firstValue = (region.forStart as IntValue).value
                val secondValue = (region.forEnd as IntValue).value
                return new Pair<Integer, Integer>(firstValue, secondValue)
            }
            ValuedObjectReference: {
                val firstValue = 0
                val card = (region.forStart as ValuedObjectReference).valuedObject.cardinalities.head
                var secondValue = -1
                if (card instanceof IntValue) {
                    secondValue = (card as IntValue).value - 1
                } else if (card instanceof ValuedObjectReference) {
                    val initialValue = card.valuedObject.initialValue
                    if (initialValue instanceof IntValue) {
                        secondValue = initialValue.value - 1
                    }
                }
                return new Pair<Integer, Integer>(firstValue, secondValue)
            }
        }
        throw new UnsupportedOperationException("The for region range type is not supported.")
    }
    
    protected def transformForRegionRange(Region region, int firstValue, int secondValue) {
        val regionCopy = region.copy => [
            forStart = null
            forEnd = null
        ]
        val references = regionCopy.eAllContents.filter(ValuedObjectReference).filter[ valuedObject == regionCopy.counterVariable ].toList
        regionCopy.counterVariable = null
        
        for(i : firstValue..secondValue) {
            val counterVariable = region.counterVariable.copy => [
                initialValue = createIntValue(i)
            ]
            
            for (ref : references) {
              ref.valuedObject = counterVariable  
            }  
            val regionCopyCopy = regionCopy.copy
            
            createIntDeclaration => [
                const = true
                valuedObjects += counterVariable
                regionCopyCopy.declarations += it
            ]
            
            region.parentState.regions += regionCopyCopy
            
            snapshot
        }
        
        region.remove
    }
    
    protected def transformForRegionWithReference(Region region) {
        
    }
    
}
