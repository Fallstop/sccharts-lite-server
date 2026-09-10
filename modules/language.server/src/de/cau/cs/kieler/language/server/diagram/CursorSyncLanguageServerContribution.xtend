/*
 * SCCharts Lab - sccharts-lite server
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.diagram

import com.google.inject.Injector
import de.cau.cs.kieler.language.server.ILanguageServerContribution

class CursorSyncLanguageServerContribution implements ILanguageServerContribution {

    override getLanguageServerExtension(Injector injector) {
        return injector.getInstance(CursorSyncLanguageServerExtension)
    }
}
