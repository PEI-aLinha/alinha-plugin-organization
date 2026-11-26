package pt.alinha.organization.internal.domain

import java.net.IDN

/**
 * The organization's own internet domain (website and email), e.g. `empresa.pt`.
 *
 * What people type is often a web address, so the scheme, `www.`, port, path and trailing dot are dropped. The
 * result is lower case, internationalized names in ASCII form (`xn--...`), letters, digits and hyphens only, at
 * least two labels and a real top-level domain (so never an IP address). The external-surface provider checks this
 * domain from the internet, so shared domains that belong to someone else are refused, and so are reserved and
 * internal names (`.local`, `.internal`, `.test`, `home.arpa`, `example.com`, ...), which have no public owner and
 * could point the provider at a private network.
 */
object DomainName {
    const val MAX_LENGTH = 253

    sealed interface Result {
        data class Valid(val domain: String) : Result
        /** Not a domain name at all. */
        data object Malformed : Result
        /** A domain shared by many organizations (webmail, free hosting): not the organization's own. */
        data object Shared : Result
        /** A reserved or internal name (RFC 2606, RFC 6761, RFC 6762, RFC 9476, ICANN's `.internal`). */
        data object Reserved : Result
    }

    private val SCHEME = Regex("""^[a-z][a-z0-9+.\-]*://""")
    private val PORT = Regex(""":\d{1,5}$""")
    private val LABEL = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
    private val TOP_LEVEL = Regex("""^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$""")

    /** Shared by many organizations: their subdomains are not the organization's own either. */
    private val SHARED = setOf(
        "gmail.com", "googlemail.com", "outlook.com", "outlook.pt", "hotmail.com", "hotmail.pt", "live.com",
        "live.com.pt", "msn.com", "yahoo.com", "yahoo.com.br", "icloud.com", "me.com", "proton.me", "protonmail.com",
        "gmx.com", "gmx.net", "aol.com", "mail.com", "zoho.com", "sapo.pt", "netcabo.pt", "clix.pt", "iol.pt",
        "github.io", "gitlab.io", "wixsite.com", "wordpress.com", "blogspot.com", "blogspot.pt", "squarespace.com",
        "weebly.com", "webnode.pt", "netlify.app", "vercel.app", "pages.dev", "herokuapp.com", "azurewebsites.net",
        "cloudfront.net", "amazonaws.com", "web.app", "firebaseapp.com", "sharepoint.com", "onmicrosoft.com",
    )

    /**
     * Top-level names that are not on the public internet: special-use and documentation names, and the ones used
     * inside private networks by convention (`.lan`, `.corp`, `.home`, ...), which ICANN will not delegate.
     */
    private val RESERVED_TOP_LEVEL = setOf(
        "local", "localhost", "localdomain", "internal", "intranet", "private", "lan", "home", "corp", "test",
        "example", "invalid", "onion", "arpa", "alt",
    )

    /** Second-level names reserved for documentation (RFC 2606). */
    private val RESERVED = setOf("example.com", "example.net", "example.org")

    fun parse(raw: String): Result {
        var text = raw.trim().lowercase()
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return Result.Malformed
        text = text.replace(SCHEME, "")
        val end = text.indexOfAny(charArrayOf('/', '?', '#'))
        if (end >= 0) text = text.substring(0, end)
        if ('@' in text || '[' in text) return Result.Malformed
        text = text.replace(PORT, "").removeSuffix(".").removePrefix("www.")
        val ascii = try {
            IDN.toASCII(text, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (_: IllegalArgumentException) {
            return Result.Malformed
        }
        val labels = ascii.split('.')
        val wellFormed = ascii.length <= MAX_LENGTH && labels.size >= 2 && labels.all(LABEL::matches) &&
            TOP_LEVEL.matches(labels.last())
        return when {
            !wellFormed -> Result.Malformed
            labels.last() in RESERVED_TOP_LEVEL || RESERVED.any { ascii == it || ascii.endsWith(".$it") } ->
                Result.Reserved
            SHARED.any { ascii == it || ascii.endsWith(".$it") } -> Result.Shared
            else -> Result.Valid(ascii)
        }
    }
}
