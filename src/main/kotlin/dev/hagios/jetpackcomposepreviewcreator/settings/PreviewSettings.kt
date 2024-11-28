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

    var addParameterNames
        get() = state.addParameterNames
        set(value) {
            state.addParameterNames = value
        }

    var useDefaultValues
        get() = state.useDefaultValues
        set(value) {
            state.useDefaultValues = value
        }

    var wrapInTheme
        get() = state.wrapInTheme
        set(value) {
            state.wrapInTheme = value
        }

    var defaultTheme
        get() = state.defaultTheme ?: ""
        set(value) {
            state.defaultTheme = value
        }

    companion object {
        fun getInstance(project: Project): PreviewSettings = project.service()
    }
}