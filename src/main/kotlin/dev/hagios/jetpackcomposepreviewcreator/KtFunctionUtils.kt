package dev.hagios.jetpackcomposepreviewcreator

import dev.hagios.jetpackcomposepreviewcreator.settings.PreviewSettings
import dev.hagios.jetpackcomposepreviewcreator.settings.Visibility
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.collections.isCollection
import org.jetbrains.kotlin.idea.inspections.collections.isMap
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType

internal fun KtFunction.generateNewPreviewFunction(
    ktPsiFactory: KtPsiFactory,
    settings: PreviewSettings
): KtNamedFunction {
    val functionName = name ?: "Function"
    val parameters = valueParameters
    val functionParameter = parameters.joinToString(", ") { ktParameter ->
        val parameterDefaultValue = getDefaultParameterValue(ktParameter = ktParameter, settings = settings)
        val parameterName = if (settings.addParameterNames) "${ktParameter.name} = " else ""
        val value = if (settings.useDefaultValues) {
            ktParameter.defaultValue?.text ?: parameterDefaultValue
        } else parameterDefaultValue
        "$parameterName$value"
    }

    val functionString = buildString {
        append("fun ", functionName, settings.functionNameExtension, "(){")
        if (settings.wrapInTheme && settings.theme.isNotBlank()) {
            append(settings.theme, "{")
        }
        append(functionName, "(", functionParameter, ")")
        if (settings.wrapInTheme && settings.theme.isNotBlank()) {
            append("}")
        }
        append("}")
    }

    return ktPsiFactory.createFunction(functionString)
        .apply {
            addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@Preview"))
            addAnnotationEntry(ktPsiFactory.createAnnotationEntry("@Composable"))
            settings.defaultVisibility.toModifier()?.let { addModifier(it) }
        }
}

internal fun Visibility.toModifier(): KtModifierKeywordToken? = when (this) {
    Visibility.public -> null
    Visibility.private -> KtTokens.PRIVATE_KEYWORD
    Visibility.internal -> KtTokens.INTERNAL_KEYWORD
}

internal val KtNamedFunction.isComposableToplevelFunction: Boolean
    get() {
        return annotationEntries.any { annotationEntry: KtAnnotationEntry ->
            annotationEntry.typeReference?.text == "Composable"
        } && isTopLevel
    }

private fun getDefaultParameterValue(ktParameter: KtParameter, settings: PreviewSettings): String {
    val context = ktParameter.analyze(BodyResolveMode.PARTIAL)
    val typeRef = ktParameter.typeReference ?: return "null"
    val type = context[BindingContext.TYPE, typeRef] ?: return "null"
    val isNullable = type.isMarkedNullable
    return if (hasCompanionObjectImplementingItself(ktParameter)) {
        if (isNullable && settings.useNullValues) {
            "null"
        } else {
            type.constructor.toString()
        }
    } else {
        getDefaultParameterValue(type, isNullable, settings)
    }
}

/**
 * Checks if an interface implements itself in a companion object, like Modifiers.
 * interface Modifier {
 *  companion object: Modifier
 * }
 */
private fun hasCompanionObjectImplementingItself(parameter: KtParameter): Boolean {

    val interfaceElement =
        (parameter.typeReference?.typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolve() as? KtClassOrObject
    val interfaceName = interfaceElement?.name ?: return false

    return interfaceElement.companionObjects.any { companionObject ->
        val superTypes = companionObject.superTypeListEntries
            .mapNotNull { it.typeReference?.typeElement as? KtUserType }

        superTypes.any { it.referencedName == interfaceName }
    }
}

private fun getDefaultParameterValue(type: KotlinType, isNullable: Boolean, settings: PreviewSettings): String {

    return when {
        isNullable && settings.useNullValues -> "null"
        type.isFunctionType -> getDefaultFunctionParameterValue(type, settings)
        type.isCollection() -> getCollectionParameterValue(type)
        type.isMap() -> getMapParameterValue(type)
        type.constructor.declarationDescriptor is ClassDescriptor -> {
            getPrimitiveDefaultValue(type.getKotlinTypeFqName(false)) {
                val classDescriptor = type.constructor.declarationDescriptor as ClassDescriptor
                val constructor = classDescriptor.unsubstitutedPrimaryConstructor
                if (constructor != null) {
                    val constructorParamsValueList = constructor.valueParameters.joinToString(", ") { param ->
                        val paramType = param.type
                        val paramIsNullable = paramType.isMarkedNullable
                        getDefaultParameterValue(paramType, paramIsNullable, settings)
                    }
                    "${type.constructor.declarationDescriptor?.name}($constructorParamsValueList)"
                } else {
                    "object: ${type.constructor.declarationDescriptor?.name} {}"
                }
            }
        }

        else -> {
            getPrimitiveDefaultValue(type.getKotlinTypeFqName(false))
        }
    }
}

fun getMapParameterValue(type: KotlinType): String {
    return type.constructor.declarationDescriptor?.name?.asString()?.let {
        when (it) {
            "Map" -> "emptyMap()"
            "MutableMap" -> "mutableMapOf()"
            else -> null
        }
    } ?: "object: ${type.constructor.declarationDescriptor?.name} {}"
}

fun getCollectionParameterValue(type: KotlinType): String {
    return type.constructor.declarationDescriptor?.name?.asString()?.let {
        when (it) {
            "List" -> "emptyList()"
            "MutableList" -> "mutableListOf()"
            "Set" -> "emptySet()"
            "MutableSet" -> "mutableSetOf()"
            else -> null
        }
    } ?: "object: ${type.constructor.declarationDescriptor?.name} {}"
}

private fun getDefaultFunctionParameterValue(type: KotlinType, settings: PreviewSettings): String {
    val parameterTypes = type.arguments.dropLast(1).map { it.type }
    val returnType = type.arguments.last().type

    val parameterValues = parameterTypes.joinToString(", ") {
        "_"
    }.ifEmpty { null }?.let { "$it -> " } ?: ""

    val returnDefaultValue = getDefaultParameterValue(returnType, returnType.isMarkedNullable, settings)

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
