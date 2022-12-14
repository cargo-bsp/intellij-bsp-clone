package org.jetbrains.plugins.bsp.flow.open

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isNewProject
import org.jetbrains.plugins.bsp.config.BspPluginIcons
import org.jetbrains.plugins.bsp.config.BspProjectPropertiesService
import org.jetbrains.plugins.bsp.config.ProjectPropertiesService
import org.jetbrains.plugins.bsp.extension.points.BspConnectionDetailsGeneratorExtension
import org.jetbrains.plugins.bsp.flow.open.wizard.ConnectionFile
import org.jetbrains.plugins.bsp.flow.open.wizard.ImportProjectWizard
import org.jetbrains.plugins.bsp.flow.open.wizard.NewConnection
import org.jetbrains.plugins.bsp.protocol.connection.BspConnectionDetailsGeneratorProvider
import org.jetbrains.plugins.bsp.server.connection.BspConnectionService
import org.jetbrains.plugins.bsp.server.connection.BspFileConnection
import org.jetbrains.plugins.bsp.server.connection.BspGeneratorConnection
import org.jetbrains.plugins.bsp.server.tasks.CollectProjectDetailsTask
import org.jetbrains.plugins.bsp.ui.console.BspConsoleService
import org.jetbrains.plugins.bsp.ui.widgets.document.targets.BspDocumentTargetsWidget
import org.jetbrains.plugins.bsp.ui.widgets.tool.window.all.targets.BspAllTargetsWidgetFactory

/**
 * Runs actions after the project has started up and the index is up-to-date.
 *
 * @see BspProjectOpenProcessor for additional actions that
 * may run when a project is being imported for the first time.
 */
public class BspStartupActivity : StartupActivity.DumbAware {

  override fun runActivity(project: Project) {
    val projectProperties = BspProjectPropertiesService.getInstance(project).value

    if (projectProperties.isBspProject) {
      doRunActivity(project)
    }
  }

  private fun doRunActivity(project: Project) {
    val bspSyncConsoleService = BspConsoleService.getInstance(project)
    bspSyncConsoleService.init()

    val bspSyncConsole = bspSyncConsoleService.bspSyncConsole
    bspSyncConsole.startTask("bsp-import", "Import", "Importing...")

    if (project.isNewProject()) {
      suspendIndexingAndShowWizardAndInitializeConnectionOnUiThread(project)
    }

    val collectProjectDetailsTask = CollectProjectDetailsTask(project, "bsp-import").prepareBackgroundTask()
    collectProjectDetailsTask.executeInTheBackground(
      "Syncing...",
      true,
      beforeRun = { BspConnectionService.getInstance(project).value.connect("bsp-import") },
      afterOnSuccess = { addBspWidgets(project); bspSyncConsole.finishTask("bsp-import", "Done!") }
    )
  }

  private fun suspendIndexingAndShowWizardAndInitializeConnectionOnUiThread(project: Project) {
    DumbService.getInstance(project).suspendIndexingAndRun("BSP sync") {
      AppUIExecutor.onUiThread().execute {
        showWizardAndInitializeConnection(project)
      }
    }
  }

  private fun showWizardAndInitializeConnection(
    project: Project,
  ) {
    val projectProperties = ProjectPropertiesService.getInstance(project).value
    val bspConnectionDetailsGeneratorProvider = BspConnectionDetailsGeneratorProvider(
      projectProperties.projectRootDir,
      BspConnectionDetailsGeneratorExtension.extensions()
    )

    val wizard = ImportProjectWizard(project, bspConnectionDetailsGeneratorProvider)
    if (wizard.showAndGet()) {
      when (val connectionFileOrNewConnection = wizard.connectionFileOrNewConnectionProperty.get()) {
        is NewConnection -> initializeNewConnection(project, bspConnectionDetailsGeneratorProvider)
        is ConnectionFile -> initializeConnectionFromFile(project, connectionFileOrNewConnection)
      }
    }
  }

  private fun initializeNewConnection(
    project: Project,
    bspConnectionDetailsGeneratorProvider: BspConnectionDetailsGeneratorProvider,
  ) {
    val name = bspConnectionDetailsGeneratorProvider.firstGeneratorTEMPORARY()
    // TODO
    val generator = BspConnectionDetailsGeneratorExtension.extensions().find { it.name() == name }!!
    val bspGeneratorConnection = BspGeneratorConnection(project, generator)

    val bspConnectionService = BspConnectionService.getInstance(project)
    bspConnectionService.init(bspGeneratorConnection)
  }

  private fun initializeConnectionFromFile(project: Project, connectionFileInfo: ConnectionFile) {
    val bspFileConnection = BspFileConnection(project, connectionFileInfo.locatedBspConnectionDetails)

    val bspConnectionService = BspConnectionService.getInstance(project)
    bspConnectionService.init(bspFileConnection)
  }

  private fun addBspWidgets(project: Project) {
    ToolWindowManager.getInstance(project).registerToolWindow("BSP") {
      icon = BspPluginIcons.bsp
      canCloseContent = false
      anchor = ToolWindowAnchor.RIGHT
      contentFactory = BspAllTargetsWidgetFactory()
    }

    val statusBar = WindowManager.getInstance().getStatusBar(project)
    // TODO it's internal - we shouldnt use it
    statusBar.addWidget(BspDocumentTargetsWidget(project), "before git", BspDocumentTargetsWidget(project))
  }
}
