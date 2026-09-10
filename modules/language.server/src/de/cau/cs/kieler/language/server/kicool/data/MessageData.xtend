/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2024 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.kicool.data

import org.eclipse.xtend.lib.annotations.Data

/**
 * Data class to hold the parameters and results of the keith/kicool/compile message.
 * 
 * @author nre
 */
@Data
class CompileParam {
    
    /**
     * The uri string of the model to compile (for snapshot compilation this is the uri of the original model.
     */
    String uri
    
    /**
     * The id of the diagram client.
     */
    String clientId
    
    /**
     * The compilation system used to compile.
     */
    String command
    
    /**
     * Whether in-place compilation should be used.
     */
    boolean inplace
    
    /**
     * Whether the final model should be shown in the diagram specified by clientId.
     */
    boolean showResultingModel
    
    /**
     * Whether the model to compile is a snapshot model.
     */
    boolean snapshot
    
}

/**
 * Data class to hold the parameters and results of the keith/kicool/didCompile message.
 * 
 * @author nre
 */
@Data
class DidCompileParam {
    
    /**
     * The result object should have the type de.cau.cs.kieler.language.server.kicool.CompilationResults
     */
    Object results
    
    /**
     * uri of model file
     */
    String uri
    
    /**
     * whether the compilation finished after this snapshot (can also happen because it was stopped).
     */
    boolean finished
    
    /**
     * processor index of current snapshot
     */
    int currentIndex
    
    /**
     * maximum number of processors
     */
    int maxIndex
    
    /**
     * The processor whose result this notification carries (a {@code ProcessorInfo}), or null.
     */
    Object currentProcessor
    
}

/**
 * Identifies a processor of the running compilation.
 */
@Data
class ProcessorInfo {
    String id
    String name
    /** Index of the processor in the system's execution order, starting at 0. */
    int index
}

/**
 * Sent when a processor starts, so the client can show what the compiler is doing right now.
 */
@Data
class CompileProgressParam {
    String uri
    ProcessorInfo processor
    /** Number of processors that finished before this one. */
    int index
    /** Number of processors the system will run. */
    int maxIndex
    /** Milliseconds since the compilation started. */
    long elapsedMs
}

/**
 * Data class to hold the parameters and results of the keith/kicool/show message.
 * 
 * @author nre
 */
@Data
class ShowParam {
    
    /**
     * uri of model
     */
    String uri
    
    /**
     * id of diagramServer
     */
    String clientId
    
    /**
     * index of snapshot. -1 equals the original model.
     */
    int index
    
}

/**
 * Data class to hold the parameters and results of the keith/kicool/compilation-systems message.
 * 
 * @author nre
 */
@Data
class SendCompilationSystemsParam {
    
    /**
     * Both objects should be List<de.cau.cs.kieler.language.server.kicool.SystemDescription>
     */
    Object systems
    
    /**
     * Both objects should be List<de.cau.cs.kieler.language.server.kicool.SystemDescription>
     */
    Object snapshotSystems
    
}
