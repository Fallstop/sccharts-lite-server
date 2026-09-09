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
package de.cau.cs.kieler.kicool.external

import de.cau.cs.kieler.core.services.KielerServiceLoader
import de.cau.cs.kieler.kicool.deploy.ProjectInfrastructure
import de.cau.cs.kieler.kicool.environments.Environment
import java.io.File
import java.io.PrintStream
import java.util.ArrayList
import java.util.List
import java.util.Map
import org.eclipse.emf.common.util.URI

import static extension de.cau.cs.kieler.kicool.deploy.ProjectInfrastructure.*

/**
 * @author als
 * @kieler.design proposed
 * @kieler.rating proposed yellow
 */
abstract class AbstractExternalCompiler {
    
    public static val PROVIDERS = KielerServiceLoader.load(IExternalCompilerProvider).toMap[id]
    
    protected var URI root
    protected var File rootDir
    
    protected val Environment environment
    
    new(Environment environment) {
        this.environment = environment
    }

    abstract def String getName()
    
    abstract def void configureEnvironment(Map<String, String> map)
    
    abstract def List<String> generateCodeCommand(List<File> files, ArrayList<String> strings)
    
    abstract def List<File> getExpectedResults(List<File> files)
    
    protected def boolean setup(ProjectInfrastructure pinf, PrintStream logger, String... executables) {
        if (root !== null) {
            if (root.isFile) {
                val file = new File(root.toFileString)
                logger.println("Dectected external compiler on system path: " + file)
                if (file.checkExecutableFlags(logger, executables)) {
                    rootDir = file
                    return true
                }
            } else if (root.isPlatformPlugin) {
                logger.println("External compilers inside Eclipse plug-ins are not supported in this build: " + root)
            }
        } else {
            logger.println("No compiler available")
        }
        logger.println("Could not set up compiler")
        return false
    }
    
    def isAvailable() {
        return root !== null
    }
    
    def isProperlySetUp() {
        return root !== null && rootDir !== null && rootDir.directory
    }
    
    protected def boolean checkExecutableFlags(File source, PrintStream logger, String... files) {
        logger.println("Checking permissions")
        for (fileName : files) {
            val file = new File(source, fileName)
            if (file.directory) {
                for (exe : file.listFiles.filter[isFile]) {
                    if (!exe.checkExecutableFlag(logger)) return false
                }
            } else {
                if (!file.checkExecutableFlag(logger)) return false
            }
        }
        return true
    }
    
    private def checkExecutableFlag(File file, PrintStream logger) {
        if (!file.name.contains(".") || file.name.endsWith(".exe")) {
            if (!file.canExecute) {
                logger.println("Setting executable flag for " + file)
                val success = file.executable = true
                if (!success) logger.println("Unable to set executable flag.")
                return success
            }
        }
        return true
    }
    
}
