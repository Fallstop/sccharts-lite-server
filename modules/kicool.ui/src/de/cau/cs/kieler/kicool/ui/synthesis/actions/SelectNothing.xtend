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
package de.cau.cs.kieler.kicool.ui.synthesis.actions

import de.cau.cs.kieler.klighd.IAction

/**
 * Selection action of the Eclipse compiler view. The headless server keeps only the action id
 * that syntheses attach to renderings; the action itself does nothing here.
 */
class SelectNothing implements IAction {

    public static val ID = "de.cau.cs.kieler.kicool.ui.synthesis.actions.selectNothing"

    override execute(ActionContext context) {
        ActionResult.createResult(false)
    }
}
