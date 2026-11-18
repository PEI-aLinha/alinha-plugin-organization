import { EMPTY_ORGANIZATION, type FieldKey, type OrganizationForm } from './organizationForm'

/**
 * "Save and resume": the server keeps no draft (organization#2), so the half-filled form lives in this browser,
 * one per account. localStorage may be missing or blocked (private window, policies): then the draft only lives
 * in memory and every function here quietly does nothing.
 */
const KEY_PREFIX = 'alinha-registration-draft:'
const VERSION = 1

const keyFor = (userId: string) => `${KEY_PREFIX}${userId}`

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/** True when nothing has been typed yet (an empty form is not worth saving). */
export const isEmptyForm = (form: OrganizationForm): boolean =>
  Object.values(form).every((v) => !v.trim())

/** The saved draft, or null. Only known text fields are read back: the stored value is untrusted. */
export function loadDraft(userId: string): OrganizationForm | null {
  try {
    const raw = storage()?.getItem(keyFor(userId))
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || (parsed as { v?: unknown }).v !== VERSION) return null
    const form = (parsed as { form?: unknown }).form
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
    s.setItem(keyFor(userId), JSON.stringify({ v: VERSION, form }))
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
