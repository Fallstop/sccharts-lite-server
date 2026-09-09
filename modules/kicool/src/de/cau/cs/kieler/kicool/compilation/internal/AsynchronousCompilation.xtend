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
package de.cau.cs.kieler.kicool.compilation.internal

import org.eclipse.xtend.lib.annotations.Accessors
import de.cau.cs.kieler.kicool.compilation.CompilationContext

/**
 * Internal helper class for asynchronous compilation jobs.  
 * 
 * @author ssm
 * @kieler.design 2017-02-19 proposed
 * @kieler.rating 2017-02-19 proposed yellow  
 */
class AsynchronousCompilation {
    
    /** Compilation context storage */ 
    @Accessors CompilationContext compilationContext
    
    new(CompilationContext compilationContext) {
        this.compilationContext = compilationContext
    }
    
    def schedule() {
        val worker = new Thread([compilationContext.compile], "Compiling (IMBC): " + compilationContext.system.id)
        worker.daemon = true
        worker.start()
    }
    
    static def compile(CompilationContext compilationContext) {
        new AsynchronousCompilation(compilationContext).schedule
    }
}
