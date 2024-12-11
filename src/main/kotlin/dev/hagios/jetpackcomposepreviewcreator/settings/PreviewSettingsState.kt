package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.openapi.components.BaseState

class PreviewSettingsState : BaseState() {
    var functionNameExtension by string("Preview")
    var defaultVisibility by enum(Visibility.private)
    var generatePosition by enum(Position.`end of file`)
    var overrideBehaviour by enum(Behaviour.replace)
    var addParameterNames by property(true)
    var useDefaultValues by property(true)
    var useNullValues by property(true)
    var wrapInTheme by property(false)
    var usedTheme by enum(Theme.project)
    var defaultTheme by string("MaterialTheme")
    var projectTheme by string("")
}
