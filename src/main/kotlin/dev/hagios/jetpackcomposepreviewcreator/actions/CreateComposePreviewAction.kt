package dev.hagios.jetpackcomposepreviewcreator.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.hagios.jetpackcomposepreviewcreator.generateNewPreviewFunction
import dev.hagios.jetpackcomposepreviewcreator.isComposableToplevelFunction
import dev.hagios.jetpackcomposepreviewcreator.settings.Behaviour
import dev.hagios.jetpackcomposepreviewcreator.settings.Position
import dev.hagios.jetpackcomposepreviewcreator.settings.PreviewSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ImportPath

class CreateComposePreviewAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val function = e.getData(CommonDataKeys.PSI_ELEMENT) as? KtNamedFunction ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val settings = project.service<PreviewSettings>()
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return


        createPreviewFunction(project, function, settings, psiFile)
    }

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.presentation.isEnabledAndVisible =
            (element as? KtNamedFunction)?.isComposableToplevelFunction ?: false
    }


    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

fun createPreviewFunction(
    project: Project,
    function: KtNamedFunction,
    settings: PreviewSettings,
    psiFile: KtFile
) {
    val ktPsiFactory = KtPsiFactory(project)

    val newFunction = function.generateNewPreviewFunction(ktPsiFactory, settings)
    val previewFunction =
        psiFile.children.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == newFunction.name }

    val importFqName = FqName("androidx.compose.ui.tooling.preview.Preview")
    val importDirectiveList = psiFile.collectDescendantsOfType<KtImportDirective>()

    val isImported = importDirectiveList.any { it.importedFqName == importFqName }

    WriteCommandAction.runWriteCommandAction(project) {
        if (previewFunction == null) {
            addFunction(settings, psiFile, newFunction, function)
        } else {
            when (settings.overrideBehaviour) {
                Behaviour.replace -> {
                    previewFunction.replace(newFunction)
                }

                Behaviour.`do nothing` -> return@runWriteCommandAction
                Behaviour.increment -> {
                    val incrementedFunctionName = createIncrementedFunctionName(newFunction.name!!, psiFile)
                    val renamedNewFunction = newFunction.setName(incrementedFunctionName)
                    addFunction(settings, psiFile, renamedNewFunction, function)
                }
            }
        }

        if (!isImported) {
            val importDirective = KtPsiFactory(project).createImportDirective(ImportPath(importFqName, false))
            val importList = psiFile.importList
            importList?.add(importDirective)
        }
    }
}

fun createIncrementedFunctionName(currentFileName: String, psiFile: PsiFile): String {
    val function = psiFile.children.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == currentFileName }
    return if (function != null) {
        val incrementNumber = function.name?.last()?.digitToIntOrNull() ?: 0
        val dropAmount = when {
            incrementNumber == 0 -> 0
            else -> 1
        }
        createIncrementedFunctionName("${function.name?.dropLast(dropAmount)}${incrementNumber + 1}", psiFile)
    } else currentFileName
}

private fun addFunction(
    settings: PreviewSettings,
    psiFile: KtFile,
    previewFunction: PsiElement,
    composableFunction: KtNamedFunction
) {
    when (settings.generatePosition) {
        Position.before -> psiFile.addBefore(previewFunction, composableFunction)
        Position.after -> psiFile.addAfter(previewFunction, composableFunction)
        Position.`end of file` -> psiFile.add(previewFunction)
    }
}
