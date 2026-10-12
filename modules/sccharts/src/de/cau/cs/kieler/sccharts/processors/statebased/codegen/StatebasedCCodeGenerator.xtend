/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2018 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.sccharts.processors.statebased.codegen

import de.cau.cs.kieler.kicool.compilation.codegen.CodeGeneratorModule
import de.cau.cs.kieler.kicool.compilation.codegen.AbstractCodeGenerator
import java.util.Map
import de.cau.cs.kieler.sccharts.SCCharts
import de.cau.cs.kieler.sccharts.State
import de.cau.cs.kieler.kicool.environments.AnnotationModel
import org.eclipse.xtend.lib.annotations.Accessors
import de.cau.cs.kieler.core.properties.IProperty
import de.cau.cs.kieler.core.properties.Property
import de.cau.cs.kieler.core.diagnostics.Issue
import de.cau.cs.kieler.sccharts.ControlflowRegion

/**
 * C Code Generator  Processor for the State-based pattern
 * 
 * @author ssm
 * @kieler.design 2018-04-16 proposed 
 * @kieler.rating 2018-04-16 proposed yellow 
 * 
 */
class StatebasedCCodeGenerator extends AbstractCodeGenerator<SCCharts, State> {
    
    public static val IProperty<Boolean> PRINT_DEBUG_ENABLED = 
       new Property<Boolean>("de.cau.cs.kieler.kicool.codegen.statebased.printDebug", false)    
    
    public static val IProperty<Boolean> LEAN_MODE = 
       new Property<Boolean>("de.cau.cs.kieler.kicool.codegen.statebased.leanMode", false)    
    
    @Accessors(PUBLIC_GETTER) var AnnotationModel<SCCharts> annotationModel
    @Accessors(PUBLIC_GETTER) var AnnotationModel<SCCharts> annotationModelStates 
    @Accessors(PUBLIC_GETTER) var AnnotationModel<SCCharts> annotationModelStatesAndRegions  
    
    override getId() {
        "de.cau.cs.kieler.sccharts.processors.codegen.statebased"
    }
    
    override getName() {
        "State-based C Code"
    }
    
    override process() {
        // Lab: KIELER's logic module fails on a region without an initial state (getStateEnumName of null).
        // Report such regions on the source instead of generating code.
        val regions = model.rootStates.map[ eAllContents.filter(ControlflowRegion).filter[ !states.exists[ initial ] ].toList ].flatten.toList
        if (!regions.empty) {
            regions.forEach[ reportMissingInitialState ]
            return
        }
        super.process
    }
    
    /** Diagnostics: a region the state-based compiler cannot start, explained for the student. */
    private def reportMissingInitialState(ControlflowRegion region) {
        val parent = region.parentState
        val portable = " The priority-based and netlist-based compilers accept the chart as it is."
        val issue = if (region.states.empty) {
            val subject = if (parent.eContainer instanceof SCCharts && parent.regions.size == 1) "Chart " + parent.name else "This region"
            Issue.at("unsupported-construct", subject + " does nothing and finishes in the instant it starts, which KIELER's"
                    + " state-based compiler cannot compile.",
                "Its initial state is final, or leads straight to a final state without doing anything. KIELER fails on"
                    + " this chart too. Let it wait at least one tick before it finishes, e.g. `initial state Wait` with"
                    + " `go to Done` and `final state Done`." + portable, region)
        } else {
            Issue.at("unsupported-construct", "KIELER's state-based compiler loses the initial state of this region while"
                    + " simplifying the chart, so it cannot compile it.",
                "This is a defect in KIELER's state-based compiler, which fails on this chart too, not a mistake in the"
                    + " chart." + portable, region)
        }
        // Without a traced region, the chart is the best location there is.
        if (issue.locations.empty) issue.locations += Issue.at(issue.code, issue.message, issue.hint, parent).locations
        environment.errors.add(null, issue.message, region, issue)
    }
    
    override preProcess(SCCharts rootModel) {
        annotationModel = rootModel.createAnnotationModel
        annotationModelStates = rootModel.createAnnotationModel
        annotationModelStatesAndRegions = rootModel.createAnnotationModel
    }
    
    override createModuleMap(SCCharts rootModel, Map<State, CodeGeneratorModule<SCCharts, State>> moduleMap) {
        for (rootState : rootModel.rootStates) {
            val generatorModule = createCodeGeneratorModule.configure(rootState.name.hostcodeSafeName, 
                rootModel, rootState, this, moduleMap, rootState.name, null, null)
            moduleMap.put(rootState, generatorModule)
            generatorModule.suffix = hostcodeSafeName(rootState.name)
            (generatorModule as StatebasedCCodeGeneratorModule).printDebug = environment.getProperty(PRINT_DEBUG_ENABLED)
            (generatorModule as StatebasedCCodeGeneratorModule).leanMode = environment.getProperty(LEAN_MODE)
        }
    }
    
    override def createCodeGeneratorModule() {
        return injector.getInstance(StatebasedCCodeGeneratorModule)
    }
    
    
}
