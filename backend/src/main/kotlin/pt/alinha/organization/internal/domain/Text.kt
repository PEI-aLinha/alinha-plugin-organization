package pt.alinha.organization.internal.domain

/** Control characters (Unicode Cc: tabs, line breaks, NUL, escape, C1) together with the spaces around them. */
private val CONTROL_OR_SPACE = Regex("""[\p{Cc}\s]+""")

/**
 * One-line text: control characters (line breaks included) become a space, runs of spaces become one, and the
 * ends are trimmed. These fields end up in reports, where a stray line break or escape code breaks the layout.
 */
internal fun String?.singleLine(): String = this.orEmpty().replace(CONTROL_OR_SPACE, " ").trim()
