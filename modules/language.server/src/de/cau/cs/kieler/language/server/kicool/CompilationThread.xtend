/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2019 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.kicool

import java.lang.Thread
import java.util.function.Consumer
import de.cau.cs.kieler.kicool.compilation.CompilationContext
import de.cau.cs.kieler.kicool.compilation.CompileGate
import org.eclipse.xtend.lib.annotations.Accessors

/**
 * @author sdo
 *
 */
class CompilationThread extends Thread {
    
    @Accessors val CompilationContext context
    
    val Consumer<Exception> onFailure

    public var boolean terminated
    
    new(CompilationContext context, Consumer<Exception> onFailure) {
        this.context = context
        this.onFailure = onFailure
        terminated = false
    }
    
    override run()  {
        this.name = "Compilation Thread"
        // Background analyses stop first, then this compilation runs alone (see CompileGate).
        CompileGate.preempt()
        CompileGate.lock()
        try {
            context.compile()
        } catch (Exception failure) {
            onFailure.accept(failure)
        } finally {
            CompileGate.unlock()
        }
        return
    }
}
