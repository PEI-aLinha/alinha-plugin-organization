const LABEL = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/
const TOP_LEVEL = /^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$/
const MAX_LENGTH = 253

/**
 * The organization's domain from what was typed (`https://www.empresa.pt/` → `empresa.pt`), internationalized
 * names in ASCII form, or null when it is not a domain. Same rules as the backend (`DomainName.kt`), which also
 * refuses shared domains such as gmail.com and reserved or internal names such as intranet.local.
 */
export function normalizeDomain(raw: string): string | null {
  const text = raw.trim().toLowerCase()
  if (!text || /[\s@[\]]/.test(text)) return null
  let host: string
  try {
    host = new URL(/^[a-z][a-z0-9+.-]*:\/\//.test(text) ? text : `http://${text}`).hostname
  } catch {
    return null
  }
  host = host.replace(/\.$/, '').replace(/^www\./, '')
  const labels = host.split('.')
  const valid = host.length <= MAX_LENGTH && labels.length >= 2 && labels.every((l) => LABEL.test(l)) &&
    TOP_LEVEL.test(labels[labels.length - 1])
  return valid ? host : null
}
