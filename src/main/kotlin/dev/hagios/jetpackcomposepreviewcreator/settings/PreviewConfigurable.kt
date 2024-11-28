package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*

class PreviewConfigurable(project: Project) : BoundConfigurable("Composable Preview") {
    val previewSettings = PreviewSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group("Creation") {
            row("Function name suffix:") {
                textField()
                    .bindText(previewSettings::functionNameExtension)
            }
            row("Default visibility:") {
                comboBox(Visibility.entries)
                    .bindItem(previewSettings::defaultVisibility.toNullableProperty())
            }
            row {
                checkBox("Add parameter names")
                    .bindSelected(previewSettings::addParameterNames)
            }
        }
    }
}

enum class Visibility {
    public,
    private,
    internal,
}