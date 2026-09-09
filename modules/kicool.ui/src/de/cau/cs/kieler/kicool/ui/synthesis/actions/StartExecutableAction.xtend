
package de.cau.cs.kieler.kicool.ui.synthesis.actions

import de.cau.cs.kieler.klighd.IAction

/**
 * Launched executables through the Eclipse debug framework; headless it keeps only its id.
 */
class StartExecutableAction implements IAction {

    public static val ID = "de.cau.cs.kieler.kicool.ui.synthesis.actions.StartExecutableAction"

    override execute(ActionContext context) {
        ActionResult.createResult(false)
    }
}
