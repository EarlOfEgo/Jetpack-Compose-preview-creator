package dev.hagios.jetpackcomposepreviewcreator

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

class CreateComposePreviewAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val function = e.getData(PSI_ELEMENT) as? KtNamedFunction ?: return
        val functionName = function.fqName?.asString() ?: return
        val parameters = function.valueParameters
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return

        val ktPsiFactory = KtPsiFactory(project)
        val functionParameter = parameters.joinToString(", ") { ktParameter ->
            val parameterDefaultValue = getDefaultParameterValue(ktParameter)
            "${ktParameter.nameAsSafeName} = ${ktParameter.defaultValue?.text ?: parameterDefaultValue}"
        }

        val newFunction = ktPsiFactory.createFunction("fun ${functionName}Preview(){$functionName($functionParameter)}")

        val importFqName = FqName("androidx.compose.ui.tooling.preview.Preview")
        val importDirectiveList = psiFile.collectDescendantsOfType<KtImportDirective>()

        val isImported = importDirectiveList.any { it.importedFqName == importFqName }
        newFunction.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@Preview"))
        newFunction.addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@Composable"))
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


private data class SyntheticParameter(val name: String, val type: KotlinType, val isNullable: Boolean)

private fun getDefaultParameterValue(ktParameter: KtParameter): String {
    val context = ktParameter.analyze(BodyResolveMode.PARTIAL)
    val typeRef = ktParameter.typeReference ?: return "null"
    val type = context[BindingContext.TYPE, typeRef] ?: return "null"
    val isNullable = type.isMarkedNullable
    return getDefaultParameterValue(SyntheticParameter(ktParameter.name ?: "", type, isNullable))
}

private fun getDefaultParameterValue(syntheticParameter: SyntheticParameter): String {
    val type = syntheticParameter.type
    val isNullable = syntheticParameter.isNullable

    return when {
        isNullable -> "null"
        type.isFunctionType -> getDefaultFunctionParameterValue(type)
        type.constructor.declarationDescriptor is ClassDescriptor -> {
            getPrimitiveDefaultValue(type.getKotlinTypeFqName(false)) {
                val classDescriptor = type.constructor.declarationDescriptor as ClassDescriptor
                val constructor = classDescriptor.unsubstitutedPrimaryConstructor
                if (constructor != null) {
                    val constructorParamsValueList = constructor.valueParameters.joinToString(", ") { param ->
                        val paramType = param.type
                        val paramIsNullable = paramType.isMarkedNullable
                        getDefaultParameterValue(SyntheticParameter(param.name.asString(), paramType, paramIsNullable))
                    }
                    "${type.constructor.declarationDescriptor?.name}($constructorParamsValueList)"
                } else {
                    "${type.constructor.declarationDescriptor?.name}()"
                }
            }
        }

        else -> {
            getPrimitiveDefaultValue(type.getKotlinTypeFqName(false))
        }
    }
}

private fun getDefaultFunctionParameterValue(type: KotlinType): String {
    val parameterTypes = type.arguments.dropLast(1).map { it.type }
    val returnType = type.arguments.last().type

    val parameterValues = parameterTypes.joinToString(", ") { paramType ->
        val paramIsNullable = paramType.isMarkedNullable
        getDefaultParameterValue(SyntheticParameter("", paramType, paramIsNullable))
    }.ifEmpty { null }?.let { "$it -> " } ?: ""

    val returnDefaultValue = getDefaultParameterValue(SyntheticParameter("", returnType, returnType.isMarkedNullable))

    return "{ $parameterValues$returnDefaultValue }"
}

private fun getPrimitiveDefaultValue(typeString: String, elseCase: () -> String = { "null" }) = when (typeString) {
    "kotlin.Byte" -> "0"
    "kotlin.Short" -> "0"
    "kotlin.Int" -> "0"
    "kotlin.Long" -> "0L"
    "kotlin.Float" -> "0.0f"
    "kotlin.Double" -> "0.0"
    "kotlin.Char" -> "'\\u0000'"
    "kotlin.String" -> "\"\""
    "kotlin.Boolean" -> "false"
    "kotlin.Unit" -> "Unit"
    else -> elseCase()
}