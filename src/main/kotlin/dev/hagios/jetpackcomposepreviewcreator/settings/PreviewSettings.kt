package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "PreviewSettings", storages = [(Storage("PreviewSettings.xml"))])
class PreviewSettings : SimplePersistentStateComponent<PreviewSettingsState>(PreviewSettingsState()) {

    var functionNameExtension: String
        get() = state.functionNameExtension ?: "Preview"
        set(value) {
            state.functionNameExtension = value
        }

    var defaultVisibility
        get() = state.defaultVisibility
        set(value) {
            state.defaultVisibility = value
        }

    companion object {
        fun getInstance(project: Project): PreviewSettings = project.service()
    }
}