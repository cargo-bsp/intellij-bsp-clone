package org.jetbrains.plugins.bsp.ui.widgets.tool.window.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.bsp.server.connection.BspConnectionService
import org.jetbrains.plugins.bsp.server.tasks.CollectProjectDetailsTask
import org.jetbrains.plugins.bsp.ui.console.BspConsoleService
import org.jetbrains.plugins.bsp.ui.widgets.tool.window.BspToolWindowPanel
import org.jetbrains.plugins.bsp.ui.widgets.tool.window.all.targets.BspAllTargetsWidgetBundle

public class ReloadAction : AnAction(BspAllTargetsWidgetBundle.message("reload.action.text")) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    if (project != null) {
      doAction(project, e.inputEvent.component.parent.parent as? BspToolWindowPanel)
    } else {
      log.warn("ReloadAction cannot be performed! Project not available.")
    }
  }

  private fun doAction(project: Project, panel: BspToolWindowPanel?) {
    val bspSyncConsole = BspConsoleService.getInstance(project).bspSyncConsole
    bspSyncConsole.startTask("bsp-reload", "Reload", "Reloading...")
    val collectProjectDetailsTask = CollectProjectDetailsTask(project, "bsp-reload").prepareBackgroundTask()
    collectProjectDetailsTask.executeInTheBackground(
      "Reloading...",
      true,
      afterOnSuccess = {
        bspSyncConsole.finishTask("bsp-reload", "Done!")
        panel?.invalidateLoadedTargets()
      }
    )
  }

  public override fun update(e: AnActionEvent) {
    val project = e.project

    if (project != null) {
      doUpdate(project, e)
    } else {
      log.warn("ReloadAction cannot be updated! Project not available.")
    }
  }

  private fun doUpdate(project: Project, e: AnActionEvent) {
    val connection = BspConnectionService.getInstance(project).value
    e.presentation.isEnabled = connection.isConnected() == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.BGT

  private companion object {
    private val log = logger<ReloadAction>()
  }
}
