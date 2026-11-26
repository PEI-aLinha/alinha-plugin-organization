package pt.alinha.organization.internal.domain

import java.net.IDN
import java.text.Normalizer

/**
 * The organization's own internet domain (website and email), e.g. `empresa.pt`.
 *
 * What people type is often a web address, so the scheme, `www.`, port, path and trailing dot are dropped. The
 * result is lower case, internationalized names in ASCII form (`xn--...`), letters, digits and hyphens only, at
 * least two labels and a real top-level domain (so never an IP address). The external-surface provider checks this
 * domain from the internet, so shared domains that belong to someone else are refused, and so are names that
 * cannot be an organization's public domain: reserved and internal names (`.local`, `.internal`, `.test`,
 * `home.arpa`, `example.com`, ...) and the wildcard DNS services that answer with any address (`nip.io`,
 * `localtest.me`, ...).
 *
 * This is only a check on the name, so it cannot keep the provider off a private network: any public name can
 * resolve to a private address. That guard is where the provider connects (the workers' `alinha_job_consumer.net`:
 * every resolved address must be public, the connection is pinned to it and redirects are not followed). Nor does it
 * prove the organization owns the domain: that is the officer's declaration in the MVP (main repo ADR 0009).
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

    /**
     * Registry suffixes under which organizations register their names (`empresa.com.pt`): a suffix on its own is not
     * a domain. The common ones only, not the whole Public Suffix List.
     */
    private val REGISTRY_SUFFIXES = setOf(
        "com.pt", "org.pt", "gov.pt", "edu.pt", "net.pt", "int.pt", "publ.pt", "nome.pt",
        "co.uk", "org.uk", "gov.uk", "ac.uk", "com.br", "org.br", "gov.br", "com.es", "org.es", "gob.es",
        "com.fr", "co.ao", "gov.ao", "co.mz", "gov.mz", "com.au", "co.nz", "co.jp", "com.cn",
    )

    /** Shared by many organizations: their subdomains are not the organization's own either. */
    private val SHARED = setOf(
        "gmail.com", "googlemail.com", "outlook.com", "outlook.pt", "hotmail.com", "hotmail.pt", "live.com",
        "live.com.pt", "msn.com", "yahoo.com", "yahoo.com.br", "icloud.com", "me.com", "proton.me", "protonmail.com",
        "gmx.com", "gmx.net", "aol.com", "mail.com", "zoho.com", "sapo.pt", "netcabo.pt", "clix.pt", "iol.pt",
        "github.io", "gitlab.io", "wixsite.com", "wordpress.com", "blogspot.com", "blogspot.pt", "squarespace.com",
        "weebly.com", "webnode.pt", "netlify.app", "vercel.app", "pages.dev", "herokuapp.com", "azurewebsites.net",
        "cloudfront.net", "amazonaws.com", "web.app", "firebaseapp.com", "sharepoint.com", "onmicrosoft.com",
        "myshopify.com",
    )

    /**
     * Top-level names that are not on the public internet: special-use and documentation names, and the ones used
     * inside private networks by convention (`.lan`, `.corp`, `.home`, ...), which ICANN will not delegate.
     */
    private val RESERVED_TOP_LEVEL = setOf(
        "local", "localhost", "localdomain", "internal", "intranet", "private", "lan", "home", "corp", "test",
        "example", "invalid", "onion", "arpa", "alt",
    )

    /**
     * Second-level names reserved for documentation (RFC 2606), and wildcard DNS services whose names resolve to an
     * address written in them or to loopback, never to an organization's own servers.
     */
    private val RESERVED = setOf(
        "example.com", "example.net", "example.org",
        "nip.io", "sslip.io", "xip.io", "localtest.me", "lvh.me", "vcap.me", "traefik.me", "local.gd",
    )

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
        if (!sameNameAfterIdna2003(text, ascii)) return Result.Malformed
        val labels = ascii.split('.')
        val wellFormed = ascii.length <= MAX_LENGTH && labels.size >= 2 && labels.all(LABEL::matches) &&
            TOP_LEVEL.matches(labels.last())
        return when {
            !wellFormed || ascii in REGISTRY_SUFFIXES -> Result.Malformed
            labels.last() in RESERVED_TOP_LEVEL || RESERVED.any { ascii == it || ascii.endsWith(".$it") } ->
                Result.Reserved
            SHARED.any { ascii == it || ascii.endsWith(".$it") } -> Result.Shared
            else -> Result.Valid(ascii)
        }
    }

    /**
     * `java.net.IDN` implements IDNA2003, which maps some letters away (`ß` to `ss`, final `ς` to `σ`), so
     * `straße.de` would become `strasse.de`, a different domain from the one IDNA2008 registries and browsers use
     * (`xn--strae-oqa.de`), possibly owned by someone else. A name the conversion changes is refused rather than
     * stored as another host. ASCII input (including `xn--` labels) is never changed.
     */
    private fun sameNameAfterIdna2003(typed: String, ascii: String): Boolean {
        if (typed.all { it.code < 0x80 }) return true
        val expected = Normalizer.normalize(typed, Normalizer.Form.NFC).replace(IDEOGRAPHIC_DOTS, ".")
        return IDN.toUnicode(ascii, IDN.USE_STD3_ASCII_RULES) == expected
    }

    private val IDEOGRAPHIC_DOTS = Regex("[\u3002\uFF0E\uFF61]")
}
