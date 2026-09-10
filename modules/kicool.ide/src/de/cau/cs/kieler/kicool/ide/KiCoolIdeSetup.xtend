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
package de.cau.cs.kieler.kicool.ide

import com.google.inject.Guice
import com.google.inject.Injector
import de.cau.cs.kieler.core.ls.ILSSetup
import de.cau.cs.kieler.kicool.KiCoolRuntimeModule
import de.cau.cs.kieler.kicool.KiCoolStandaloneSetup
import de.cau.cs.kieler.kicool.registration.KiCoolRegistration
import org.eclipse.xtext.util.Modules2

/**
 * Registers the KiCool system language ({@code .kico}) with the language server so that files
 * describing user-defined compilation systems get parsing, validation and the other editor services.
 */
class KiCoolIdeSetup extends KiCoolStandaloneSetup implements ILSSetup {

    static Injector ideInjector

	override createInjector() {
		Guice.createInjector(Modules2.mixin(new KiCoolRuntimeModule, new KiCoolIdeModule))
	}
	
    def static synchronized doSetup() {
        if (ideInjector === null) {
            // The compiler registry sets the runtime-only injector up for "kico" when it is first touched;
            // do that now so the IDE injector registered below is the one that stays in the registry.
            KiCoolRegistration.getSystemModels
            ideInjector = new KiCoolIdeSetup().createInjectorAndDoEMFRegistration()
        }
        return ideInjector
    }

    override doLSSetup() {
        return KiCoolIdeSetup.doSetup
    }
	
}
