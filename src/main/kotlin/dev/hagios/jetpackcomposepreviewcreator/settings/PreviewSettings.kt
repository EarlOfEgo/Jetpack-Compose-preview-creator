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

    var generatePosition
        get() = state.generatePosition
        set(value) {
            state.generatePosition = value
        }

    var overrideBehaviour
        get() = state.overrideBehaviour
        set(value) {
            state.overrideBehaviour = value
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

    var useNullValues
        get() = state.useNullValues
        set(value) {
            state.useNullValues = value
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

    var projectTheme
        get() = state.projectTheme ?: ""
        set(value) {
            state.projectTheme = value
        }

    var usedTheme
        get() = state.usedTheme
        set(value) {
            state.usedTheme = value
        }

    val theme: String
        get() {
            return when (usedTheme) {
                Theme.custom -> defaultTheme
                Theme.project -> projectTheme
            }
        }

    companion object {
        fun getInstance(project: Project): PreviewSettings = project.service()
    }
}