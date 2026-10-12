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
package de.cau.cs.kieler.scg.processors.codegen.prio.c

import com.google.inject.Inject
import com.google.inject.Injector
import de.cau.cs.kieler.scg.codegen.SCGCodeGeneratorModule
import de.cau.cs.kieler.scg.processors.codegen.c.CCodeGenerator
import de.cau.cs.kieler.scg.diagnostics.MissingSuccessor

/**
 * C Prio Code Processor
 * Migrated from SCLPTransformation
 * 
 * @author ssm
 * @kieler.design 2019-06-09 proposed 
 * @kieler.rating 2019-06-09 proposed yellow 
 * 
 */
class CPrioCodeGenerator extends CCodeGenerator {
    
    @Inject Injector injector
    
    override getId() {
        "de.cau.cs.kieler.scg.processors.codegen.prio.c"
    }
    
    override getName() {
        "C Prio Code"
    }
    
    override process() {
        try {
            super.process
        } catch (MissingSuccessor missing) {
            // Lab: the logic module stops where KIELER fails; report the chart or region that has no state to start in.
            val issue = missing.issue("priority-based compiler")
            environment.errors.add(null, issue.message, missing.node, issue)
        }
    }
    
    override SCGCodeGeneratorModule createCodeGeneratorModule() {
        return injector.getInstance(CPrioCodeGeneratorModule)
    }
}

