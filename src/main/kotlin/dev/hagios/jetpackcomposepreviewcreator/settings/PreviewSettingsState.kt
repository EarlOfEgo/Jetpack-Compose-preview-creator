package dev.hagios.jetpackcomposepreviewcreator.settings

import com.intellij.openapi.components.BaseState

class PreviewSettingsState : BaseState() {
    var functionNameExtension by string("Preview")
    var defaultVisibility by enum(Visibility.private)
}
