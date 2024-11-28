package dev.hagios.jetpackcomposepreviewcreator.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import dev.hagios.jetpackcomposepreviewcreator.generateNewPreviewFunction
import dev.hagios.jetpackcomposepreviewcreator.isComposableToplevelFunction
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


        val ktPsiFactory = KtPsiFactory(project)

        val newFunction = function.generateNewPreviewFunction(ktPsiFactory, settings)

        val importFqName = FqName("androidx.compose.ui.tooling.preview.Preview")
        val importDirectiveList = psiFile.collectDescendantsOfType<KtImportDirective>()

        val isImported = importDirectiveList.any { it.importedFqName == importFqName }

        WriteCommandAction.runWriteCommandAction(project) {
            psiFile.add(newFunction)
            if (!isImported) {
                val importDirective = KtPsiFactory(project).createImportDirective(ImportPath(importFqName, false))
                val importList = psiFile.importList
                importList?.add(importDirective)
            }
        }
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