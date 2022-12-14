package org.jetbrains.plugins.bsp.protocol.connection

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.flow.open.wizard.ConnectionFileOrNewConnection
import org.jetbrains.plugins.bsp.flow.open.wizard.ImportProjectWizardStep
import org.jetbrains.plugins.bsp.utils.withRealEnvs
import java.io.OutputStream
import java.nio.file.Path

public interface BspConnectionDetailsGenerator {
  public fun executeAndWait(command: List<String>, projectPath: VirtualFile, outputStream: OutputStream) {
    // TODO - consider verbosing what command is being executed
    val builder = ProcessBuilder(command)
      .directory(projectPath.toNioPath().toFile())
      .withRealEnvs()

    val consoleProcess = builder.start()

    consoleProcess.inputStream.transferTo(outputStream)
    consoleProcess.waitFor()
  }

  public fun getChild(root: VirtualFile?, path: List<String>): VirtualFile? {
    val found: VirtualFile? = path.fold(root) { vf: VirtualFile?, child: String ->
      vf?.refresh(false, false)
      vf?.findChild(child)
    }
    found?.refresh(false, false)
    return found
  }

  public fun name(): String

  public fun canGenerateBspConnectionDetailsFile(projectPath: VirtualFile): Boolean

  public fun calculateImportWizardSteps(
    projectBasePath: Path,
    connectionFileOrNewConnectionProperty: ObservableMutableProperty<ConnectionFileOrNewConnection>
  ): List<ImportProjectWizardStep> = emptyList()

  public fun generateBspConnectionDetailsFile(projectPath: VirtualFile, outputStream: OutputStream): VirtualFile
}

public class BspConnectionDetailsGeneratorProvider(
  private val projectPath: VirtualFile,
  bspConnectionDetailsGenerators: List<BspConnectionDetailsGenerator>,
) {

  private val availableBspConnectionDetailsGenerators by lazy {
    bspConnectionDetailsGenerators.filter { it.canGenerateBspConnectionDetailsFile(projectPath) }
  }

  public fun canGenerateAnyBspConnectionDetailsFile(): Boolean =
    availableBspConnectionDetailsGenerators.isNotEmpty()

  public fun availableGeneratorsNames(): List<String> =
    availableBspConnectionDetailsGenerators.map { it.name() }

  public fun firstGeneratorTEMPORARY(): String? =
    availableGeneratorsNames().firstOrNull()

  public fun calculateWizardSteps(
    generatorName: String,
    connectionFileOrNewConnectionProperty: ObservableMutableProperty<ConnectionFileOrNewConnection>
  ): List<ImportProjectWizardStep> =
    availableBspConnectionDetailsGenerators
      .find { it.name() == generatorName }
      ?.calculateImportWizardSteps(projectPath.toNioPath(), connectionFileOrNewConnectionProperty).orEmpty()

  public fun generateBspConnectionDetailFileForGeneratorWithName(
    generatorName: String,
    outputStream: OutputStream
  ): VirtualFile? =
    availableBspConnectionDetailsGenerators
      .find { it.name() == generatorName }
      ?.generateBspConnectionDetailsFile(projectPath, outputStream)
}
