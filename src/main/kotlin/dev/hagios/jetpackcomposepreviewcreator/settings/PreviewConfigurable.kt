package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*

class PreviewConfigurable(project: Project) : BoundConfigurable("Composable Preview") {
    val previewSettings = PreviewSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group("Creation") {
            row("Function name suffix:") {
                textField()
                    .bindText(previewSettings::functionNameExtension)
                contextHelp("The suffix that is used when the composable function is created. The function name will look like this: MyComposablePreview.")
            }
            row("Default visibility:") {
                comboBox(Visibility.entries)
                    .bindItem(previewSettings::defaultVisibility.toNullableProperty())
                contextHelp("The visibility of the created preview function. Note: public is omitted as it is the default visibility in Kotlin.")
            }
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
            lateinit var wrapInThemeCheckBox: Cell<JBCheckBox>
            row {
                wrapInThemeCheckBox = checkBox("Wrap in theme")
                    .bindSelected(previewSettings::wrapInTheme)
                contextHelp("Puts the composable function into the project theme. Note: Make sure there is a composable function that has Theme in it's title.")
            }
            row("Used Theme:") {
                textField()
                    .bindText(previewSettings::defaultTheme)
                    .cellValidation {
                        addInputRule("Mustn't be empty") {
                            it.text.isBlank()
                        }
                    }
                    .enabledIf(wrapInThemeCheckBox.selected)
                contextHelp("The name of the theme composable. Note: Make sure that the last argument is of type composable.")
            }
        }
    }
}


enum class Visibility {
    public,
    private,
    internal,
}