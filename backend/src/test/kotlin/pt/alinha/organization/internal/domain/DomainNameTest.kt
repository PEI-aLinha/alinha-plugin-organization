package pt.alinha.organization.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class DomainNameTest {

    @ParameterizedTest
    @CsvSource(
        "empresa.pt, empresa.pt",
        "  Empresa.PT  , empresa.pt",
        "www.empresa.pt, empresa.pt",
        "https://www.empresa.pt/, empresa.pt",
        "http://empresa.pt:8080/contactos?x=1#topo, empresa.pt",
        "empresa.pt., empresa.pt",
        "loja.empresa.com.pt, loja.empresa.com.pt",
        "empresa.com.pt, empresa.com.pt",
        "camara.gov.pt, camara.gov.pt",
        "açores-turismo.pt, xn--aores-turismo-igb.pt",
        "xn--aores-turismo-igb.pt, xn--aores-turismo-igb.pt",
    )
    fun `a web address is reduced to the domain`(typed: String, expected: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Valid(expected))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", "empresa", "192.168.1.10", "https://10.0.0.1/", "[::1]", "http://[2001:db8::1]/", "geral@empresa.pt",
            "https://user@empresa.pt", "*.empresa.pt", "-empresa.pt", "empresa-.pt", "emp resa.pt", "empresa..pt",
            "empresa.p", "empresa.123", "empresa_ok.pt", "empresa.pt\nx", "com.pt", "www.gov.pt", "https://co.uk/",
        ],
    )
    fun `anything that is not a domain is malformed`(typed: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Malformed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["gmail.com", "GMAIL.com", "empresa.github.io", "www.sapo.pt", "loja.wixsite.com", "loja.myshopify.com"])
    fun `shared domains are not the organization's own`(typed: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Shared)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "intranet.local", "db.internal", "x.localhost", "host.corp", "nas.lan", "router.home", "foo.test",
            "foo.example", "foo.invalid", "abc.onion", "router.home.arpa", "1.168.192.in-addr.arpa", "x.alt",
            "srv.localdomain", "example.com", "www.example.org", "mail.example.net", "HTTPS://Intranet.LOCAL/",
            "10.0.0.1.nip.io", "127.0.0.1.sslip.io", "anything.localtest.me", "app.lvh.me", "nip.io",
        ],
    )
    fun `reserved and internal names are not the organization's own`(typed: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Reserved)
    }

    @ParameterizedTest
    @ValueSource(strings = ["local.pt", "internal-audit.com", "example-empresa.pt", "testes.pt", "corp.pt"])
    fun `a reserved word below a public top-level domain is fine`(typed: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Valid(typed))
    }

    @ParameterizedTest
    @ValueSource(ints = [63, 64])
    fun `a label has at most 63 characters`(length: Int) {
        val result = DomainName.parse("a".repeat(length) + ".pt")
        assertThat(result is DomainName.Result.Valid).isEqualTo(length == 63)
    }

    @org.junit.jupiter.api.Test
    fun `a domain has at most 253 characters`() {
        val label = "a".repeat(60)
        val long = List(5) { label }.joinToString(".") + ".pt"
        assertThat(DomainName.parse(long)).isEqualTo(DomainName.Result.Malformed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["straße.de", "www.straße.de", "ελληνικός.gr"])
    fun `a name that IDNA2003 maps to another domain is refused`(typed: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Malformed)
    }

    @ParameterizedTest
    @CsvSource(
        "xn--strae-oqa.de, xn--strae-oqa.de",
        "açores-turismo。pt, xn--aores-turismo-igb.pt",
        "ação.pt, xn--ao-siap.pt",
    )
    fun `an IDNA2008 name or one IDNA2003 keeps is accepted`(typed: String, expected: String) {
        assertThat(DomainName.parse(typed)).isEqualTo(DomainName.Result.Valid(expected))
    }
}
