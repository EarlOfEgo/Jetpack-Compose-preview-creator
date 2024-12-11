package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import dev.hagios.jetpackcomposepreviewcreator.isComposableToplevelFunction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class PreviewConfigurable(private val project: Project, private val coroutineScope: CoroutineScope) :
    BoundConfigurable("Composable Preview") {
    val previewSettings = PreviewSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group("Function") {
            row("Function name suffix:") {
                textField()
                    .resizableColumn()
                    .gap(RightGap.SMALL)
                    .align(AlignX.FILL)
                    .bindText(previewSettings::functionNameExtension)
                contextHelp("The suffix that is used when the composable function is created. The function name will look like this: MyComposablePreview.")
            }
            row("Default visibility:") {
                comboBox(Visibility.entries)
                    .bindItem(previewSettings::defaultVisibility.toNullableProperty())
                contextHelp("The visibility of the created preview function. Note: public is omitted as it is the default visibility in Kotlin.")
            }
            row("New function position:") {
                comboBox(Position.entries)
                    .bindItem(previewSettings::generatePosition.toNullableProperty())
                contextHelp("The position of the generated preview function. Before or after the composable, or at the end of the file.")
            }
            row("When the function exists:") {
                comboBox(Behaviour.entries)
                    .bindItem(previewSettings::overrideBehaviour.toNullableProperty())
                contextHelp("The behaviour when the preview function already exists. Replace will replace the old one, increment will create a new one with an incremented name and do nothing, does indeed nothing.")
            }
        }
        group("Parameters") {
            row {
                checkBox("Add parameter names")
                    .bindSelected(previewSettings::addParameterNames)
                contextHelp("If parameter names should be added in the function call in the preview function.")
            }
            row {
                checkBox("Use default values of parameters")
                    .bindSelected(previewSettings::useDefaultValues)
                contextHelp("Uses the default values of parameters.")
            }
            row {
                checkBox("Initialize with null")
                    .bindSelected(previewSettings::useNullValues)
                contextHelp("Uses null as value when the parameter is nullable.")
            }
        }
        group("Theme") {
            lateinit var wrapInThemeCheckBox: Cell<JBCheckBox>
            lateinit var projectTheme: Cell<JBTextField>
            lateinit var customTheme: Cell<JBRadioButton>
            row {
                wrapInThemeCheckBox = checkBox("Wrap in theme")
                    .bindSelected(previewSettings::wrapInTheme)
                contextHelp("Puts the composable function into the project theme. Note: Make sure there is a composable function that has Theme in it's title.")
            }
            buttonsGroup {
                row {
                    customTheme = radioButton("Project Theme:", Theme.project)
                    projectTheme = textField()
                    projectTheme
                        .text("Searching for Theme…")
                        .bindText(previewSettings::projectTheme)
                    projectTheme
                        .resizableColumn()
                        .enabled(false)
                        .gap(RightGap.SMALL)
                        .align(AlignX.FILL)
                    coroutineScope.launch {
                        val theme = withContext(Dispatchers.IO) {
                            projectThemeFunction(project)
                        }

                        customTheme.enabled(theme?.isNotBlank() == true)
                        previewSettings.projectTheme = theme ?: "No Theme found in the project"
                        projectTheme.text(previewSettings.projectTheme)
                        if (theme?.isNotBlank() == true) {
                            icon(AllIcons.General.Warning)
                        }
                    }
                    contextHelp("Uses a theme that was found in the project. Themes are toplevel composable functions that contain the keyword \"Theme\" and have at least one composable argument.")
                }
                row {
                    radioButton("Custom Theme:", Theme.custom)
                    textField()
                        .bindText(previewSettings::defaultTheme)
                        .validationOnInput {
                            if (it.text.isBlank()) {
                                ValidationInfo("Mustn't be empty")
                            } else
                                null
                        }
                        .resizableColumn()
                        .gap(RightGap.SMALL)
                        .align(AlignX.FILL)
                    contextHelp("The name of the theme composable. Note: Make sure that the last argument is of type composable.")
                }
            }.bind(previewSettings::usedTheme).enabledIf(wrapInThemeCheckBox.selected)
        }
    }
}

private fun projectThemeFunction(project: Project): String? {
    return ReadAction.compute<String?, Throwable> {
        val virtualFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.allScope(project))
        var foundThemeFunction: KtNamedFunction? = null
        for (virtualFile in virtualFiles) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            psiFile?.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitNamedFunction(function: KtNamedFunction) {
                        if (function.isComposableToplevelFunction && function.text.contains("Theme")) {
                            val contentComposableParameter = function.valueParameters.lastOrNull {
                                val context = it.analyze(BodyResolveMode.PARTIAL)
                                val typeReference = context[BindingContext.TYPE, it.typeReference]
                                typeReference?.annotations?.any {
                                    it.type.fqName?.shortName()?.asString() == "Composable"
                                } ?: false
                            }
                            if (contentComposableParameter != null) {
                                foundThemeFunction = function
                            }
                        }
                        super.visitNamedFunction(function)
                    }
                }
            )
        }
        foundThemeFunction?.name
    }
}


enum class Visibility {
    public,
    private,
    internal,
}

enum class Theme {
    custom,
    project
}

enum class Position {
    before,
    after,
    `end of file`
}

enum class Behaviour {
    replace,
    `do nothing`,
    `increment`
}