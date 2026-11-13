package io.apimeter.wear.ui

/** 极简导航：手表上只有 4~5 层，手写一个栈比引入 navigation-compose 更轻。 */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Proxy : Screen
    data object Backup : Screen
    data object About : Screen
    data object ProviderPicker : Screen
    data class Editor(val providerKey: String, val credentialId: String? = null) : Screen
    data class Detail(val credentialId: String) : Screen
}
