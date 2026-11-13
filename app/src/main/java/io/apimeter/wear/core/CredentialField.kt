package io.apimeter.wear.core

data class CredentialField(
    val key: String,
    val labelRes: Int,
    val hintRes: Int,
    val kind: FieldKind = FieldKind.TEXT,
    val required: Boolean = true,
    val secret: Boolean = false,
    val defaultValue: String = "",
)

enum class FieldKind { TEXT, SECRET, NUMBER, CHOICE }
