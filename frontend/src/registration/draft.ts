import { EMPTY_ORGANIZATION, type FieldKey, type OrganizationForm } from './organizationForm'

/**
 * "Save and resume": the server keeps no draft (organization#2), so the half-filled form lives in this browser,
 * one per account. localStorage may be missing or blocked (private window, policies): then the draft only lives
 * in memory and every function here quietly does nothing.
 *
 * The draft holds company and contact data in clear text, so it expires: a draft older than DRAFT_TTL_MS is
 * ignored and removed the next time it is read, and `forgetExpired` removes every account's expired drafts when
 * the wizard opens, so an abandoned wizard does not leave it in the browser for good.
 *
 * Trade-off: localStorage, not sessionStorage, because resuming in a later session is the point of the draft.
 * Until it expires, anyone with access to this browser profile can read it with the developer tools. Clearing it
 * on sign-out needs a hook from the shell, which has none yet (follow-up issue).
 */
const KEY_PREFIX = 'alinha-registration-draft:'
const VERSION = 2
const DAY_MS = 24 * 60 * 60 * 1000
export const DRAFT_TTL_MS = 7 * DAY_MS

const keyFor = (userId: string) => `${KEY_PREFIX}${userId}`

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/**
 * The stored value at `key` when it has this version and is younger than `ttlMs`. Anything else (expired, an older
 * version, unreadable) is removed: it may still hold personal data and will never be read back.
 */
function readFresh(key: string, ttlMs: number): Record<string, unknown> | null {
  const s = storage()
  const raw = s?.getItem(key)
  if (!raw) return null
  let parsed: unknown = null
  try {
    parsed = JSON.parse(raw)
  } catch {
    // Unreadable: removed below.
  }
  const { v, savedAt } = (typeof parsed === 'object' && parsed !== null ? parsed : {}) as { v?: unknown; savedAt?: unknown }
  const age = typeof savedAt === 'number' ? Date.now() - savedAt : Number.NaN
  if (v !== VERSION || !(age >= 0 && age < ttlMs)) {
    s?.removeItem(key)
    return null
  }
  return parsed as Record<string, unknown>
}

/** True when nothing has been typed yet (an empty form is not worth saving). */
export const isEmptyForm = (form: OrganizationForm): boolean =>
  Object.values(form).every((v) => !v.trim())

/** The saved draft, or null. Only known text fields are read back: the stored value is untrusted. */
export function loadDraft(userId: string): OrganizationForm | null {
  try {
    const form = readFresh(keyFor(userId), DRAFT_TTL_MS)?.form
    if (typeof form !== 'object' || form === null) return null
    const fields = form as Record<string, unknown>
    const draft = Object.fromEntries(
      (Object.keys(EMPTY_ORGANIZATION) as FieldKey[]).map((k) => [k, typeof fields[k] === 'string' ? fields[k] : '']),
    ) as unknown as OrganizationForm
    return isEmptyForm(draft) ? null : draft
  } catch {
    return null
  }
}

/** Saves the form (an empty one removes the draft). Returns whether it was stored. */
export function saveDraft(userId: string, form: OrganizationForm): boolean {
  if (isEmptyForm(form)) {
    clearDraft(userId)
    return true
  }
  try {
    const s = storage()
    if (!s) return false
    s.setItem(keyFor(userId), JSON.stringify({ v: VERSION, savedAt: Date.now(), form }))
    return true
  } catch {
    return false
  }
}

export function clearDraft(userId: string): void {
  try {
    storage()?.removeItem(keyFor(userId))
  } catch {
    // Nothing to clear without storage.
  }
}

/**
 * After the 201 the session token still says "no organization" until the user signs in again (ADR 0004, BFF #14).
 * If they leave before that, the shell sends them back to the wizard: this mark lets it show "already registered"
 * and the sign-in button instead of an empty form. It holds the organization name when known (not after a 409).
 * It expires after DONE_TTL_MS: the new sign-in it waits for takes minutes, and a mark that never expired would
 * keep an account that is a `registrant` again (organization deleted) out of the form in this browser for good.
 */
const DONE_PREFIX = 'alinha-registration-done:'
export const DONE_TTL_MS = DAY_MS

/** What this account already registered in this browser: `name` is null when it is not known. */
export interface Registered {
  name: string | null
}

export function markRegistered(userId: string, name?: string): void {
  try {
    storage()?.setItem(`${DONE_PREFIX}${userId}`, JSON.stringify({ v: VERSION, savedAt: Date.now(), name: name ?? null }))
  } catch {
    // Without storage the 409 ORGANIZATION_ALREADY_EXISTS still leads to a new sign-in.
  }
}

/** Forgets the mark, e.g. when the sign-in it waits for showed the account still has no organization. */
export function clearRegistered(userId: string): void {
  try {
    storage()?.removeItem(`${DONE_PREFIX}${userId}`)
  } catch {
    // Nothing to clear without storage.
  }
}

/** The organization this account registered in this browser not long ago, or null. */
export function registration(userId: string): Registered | null {
  try {
    const mark = readFresh(`${DONE_PREFIX}${userId}`, DONE_TTL_MS)
    if (!mark) return null
    const { name } = mark
    return { name: typeof name === 'string' && name.trim() ? name : null }
  } catch {
    return null
  }
}

/**
 * Removes the drafts and marks of every account in this browser that expired or can no longer be read, not only the
 * current account's: another person's abandoned draft would otherwise stay until they come back.
 */
export function forgetExpired(): void {
  try {
    const s = storage()
    if (!s) return
    const keys = Array.from({ length: s.length }, (_, i) => s.key(i)).filter((k): k is string => k !== null)
    for (const key of keys) {
      if (key.startsWith(KEY_PREFIX)) readFresh(key, DRAFT_TTL_MS)
      else if (key.startsWith(DONE_PREFIX)) readFresh(key, DONE_TTL_MS)
    }
  } catch {
    // Nothing to sweep without storage.
  }
}
