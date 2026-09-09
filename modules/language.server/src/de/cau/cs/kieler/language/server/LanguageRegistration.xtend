/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 * 
 * Copyright 2019,2020 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server

import de.cau.cs.kieler.core.ls.ILSSetup
import de.cau.cs.kieler.core.services.KielerServiceLoader
import de.cau.cs.kieler.klighd.lsp.launch.ILanguageRegistration

/**
 * Binds and registers all {@link ILSSetupContribution}s.
 * 
 * @author sdo, nre
 */
class LanguageRegistration implements ILanguageRegistration {
    
    override bindAndRegisterLanguages() {        
        // Every KIELER language on the classpath registers itself through ILSSetup.
        for (ideSetup: KielerServiceLoader.load(ILSSetup)) {
            ideSetup.doLSSetup()
        }
    }
}