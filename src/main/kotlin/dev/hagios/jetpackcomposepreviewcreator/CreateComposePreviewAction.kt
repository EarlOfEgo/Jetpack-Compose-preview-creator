package dev.hagios.jetpackcomposepreviewcreator

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ImportPath


class CreateComposePreviewAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val element = e.getData(PSI_ELEMENT) as? KtNamedFunction ?: return
        val functionName = element.fqName?.asString() ?: return
        val parameters = element.valueParameters
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return

        val ktPsiFactory = KtPsiFactory(project)
        val newFunction = ktPsiFactory.createFunction("fun ${functionName}Preview(){$functionName()}")

        val importFqName = FqName("androidx.compose.ui.tooling.preview.Preview")
        val importDirectiveList = psiFile.collectDescendantsOfType<KtImportDirective>()

        val isImported = importDirectiveList.any { it.importedFqName == importFqName }
        newFunction.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@Preview"))
        newFunction.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@Composable"))
        WriteCommandAction.runWriteCommandAction(project) {
            psiFile.add(newFunction)
            if(!isImported) {
                val importDirective = KtPsiFactory(project).createImportDirective(ImportPath(importFqName, false))
                val importList = psiFile.importList
                importList?.add(importDirective)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val element = e.getData(PSI_ELEMENT)
        e.presentation.isEnabledAndVisible = (element as? KtNamedFunction)?.let { ktNamedFunction ->
            ktNamedFunction.annotationEntries.any { annotationEntry: KtAnnotationEntry ->
                annotationEntry.typeReference?.text == "Composable"
            } && ktNamedFunction.isTopLevel
        } ?: false
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
