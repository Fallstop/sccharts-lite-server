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
package de.cau.cs.kieler.language.server.diagnostics;

import com.google.inject.Injector;
import de.cau.cs.kieler.language.server.ILanguageServerContribution;
import org.eclipse.xtext.ide.server.ILanguageServerExtension;

/** Registers the live analysis with the language server (META-INF/services). */
public class LiveDiagnosticsContribution implements ILanguageServerContribution {
    @Override
    public ILanguageServerExtension getLanguageServerExtension(Injector injector) {
        return injector.getInstance(LiveDiagnosticsExtension.class);
    }
}
