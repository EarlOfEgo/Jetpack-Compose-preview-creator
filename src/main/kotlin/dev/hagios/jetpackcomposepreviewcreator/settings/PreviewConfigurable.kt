package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

class PreviewConfigurable(project: Project) : BoundConfigurable("Composable Preview") {
    val previewSettings = PreviewSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group("Creation") {
            row("Function name extension:") {
                textField()
                    .bindText(previewSettings::functionNameExtension)
            }
            row("Default visibility:") {
                comboBox(Visibility.entries)
                    .bindItem(previewSettings::defaultVisibility.toNullableProperty())
            }
        }
    }
}

enum class Visibility {
    public,
    private,
    internal,
    protected,
}