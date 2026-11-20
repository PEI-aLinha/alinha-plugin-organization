import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DONE_TTL_MS, DRAFT_TTL_MS, clearDraft, forgetExpired, isEmptyForm, loadDraft, markRegistered, registration, saveDraft } from './draft'
import { EMPTY_ORGANIZATION } from './organizationForm'
import { VALID } from '../test/fixtures'

describe('registration draft', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  const stored = (v: number, extra: object) => JSON.stringify({ v, savedAt: Date.now(), ...extra })

  it('saves and loads the form, per account', () => {
    expect(saveDraft('u1', VALID)).toBe(true)
    expect(loadDraft('u1')).toEqual(VALID)
    expect(loadDraft('u2')).toBeNull()
  })

  it('remembers, per account, that the organization was registered', () => {
    expect(registration('u1')).toBeNull()
    markRegistered('u1', 'Acme, Lda.')
    expect(registration('u1')).toEqual({ name: 'Acme, Lda.' })
    expect(registration('u2')).toBeNull()
    localStorage.setItem('alinha-registration-done:u4', '{')
    expect(registration('u4')).toBeNull()
  })

  it('remembers a registration without a name, and ignores a name that is not text', () => {
    markRegistered('u1')
    expect(registration('u1')).toEqual({ name: null })
    localStorage.setItem('alinha-registration-done:u3', stored(2, { name: 7 }))
    expect(registration('u3')).toEqual({ name: null })
  })

  it('forgets the registration after a day, so the form opens again', () => {
    vi.useFakeTimers()
    markRegistered('u1', 'Acme, Lda.')
    vi.advanceTimersByTime(DONE_TTL_MS - 1)
    expect(registration('u1')).toEqual({ name: 'Acme, Lda.' })
    vi.advanceTimersByTime(1)
    expect(registration('u1')).toBeNull()
    expect(localStorage.getItem('alinha-registration-done:u1')).toBeNull()
  })

  it('drops a draft older than its time to live, and removes it from the browser', () => {
    vi.useFakeTimers()
    saveDraft('u1', VALID)
    vi.advanceTimersByTime(DRAFT_TTL_MS - 1)
    expect(loadDraft('u1')).toEqual(VALID)
    vi.advanceTimersByTime(1)
    expect(loadDraft('u1')).toBeNull()
    expect(localStorage.getItem('alinha-registration-draft:u1')).toBeNull()
  })

  it('saving again restarts the time to live', () => {
    vi.useFakeTimers()
    saveDraft('u1', VALID)
    vi.advanceTimersByTime(DRAFT_TTL_MS - 1)
    saveDraft('u1', VALID)
    vi.advanceTimersByTime(DRAFT_TTL_MS - 1)
    expect(loadDraft('u1')).toEqual(VALID)
  })

  it('removes drafts with no date, a future date or an older format', () => {
    for (const value of [
      JSON.stringify({ v: 2, form: VALID }),
      JSON.stringify({ v: 2, savedAt: Date.now() + 60_000, form: VALID }),
      JSON.stringify({ v: 1, form: VALID }),
      '{',
    ]) {
      localStorage.setItem('alinha-registration-draft:u1', value)
      expect(loadDraft('u1')).toBeNull()
      expect(localStorage.getItem('alinha-registration-draft:u1')).toBeNull()
    }
  })

  it('forgets the expired drafts and marks of every account, and keeps the rest', () => {
    vi.useFakeTimers()
    saveDraft('old', VALID)
    markRegistered('old', 'Acme, Lda.')
    vi.advanceTimersByTime(DRAFT_TTL_MS)
    saveDraft('recent', VALID)
    markRegistered('recent')
    localStorage.setItem('alinha-registration-draft:broken', '{')
    localStorage.setItem('unrelated', 'kept')

    forgetExpired()

    expect(localStorage.getItem('alinha-registration-draft:old')).toBeNull()
    expect(localStorage.getItem('alinha-registration-done:old')).toBeNull()
    expect(localStorage.getItem('alinha-registration-draft:broken')).toBeNull()
    expect(loadDraft('recent')).toEqual(VALID)
    expect(registration('recent')).toEqual({ name: null })
    expect(localStorage.getItem('unrelated')).toBe('kept')
  })

  it('clears the draft', () => {
    saveDraft('u1', VALID)
    clearDraft('u1')
    expect(loadDraft('u1')).toBeNull()
  })

  it('does not keep an empty form', () => {
    saveDraft('u1', VALID)
    saveDraft('u1', EMPTY_ORGANIZATION)
    expect(loadDraft('u1')).toBeNull()
    expect(isEmptyForm({ ...EMPTY_ORGANIZATION, name: '  ' })).toBe(true)
  })

  it('reads back only known text fields', () => {
    localStorage.setItem('alinha-registration-draft:u1', stored(2, { form: { name: 'Acme', nif: 5, evil: 'x' } }))
    expect(loadDraft('u1')).toEqual({ ...EMPTY_ORGANIZATION, name: 'Acme' })
  })

  it('ignores broken or old drafts', () => {
    localStorage.setItem('alinha-registration-draft:u1', '{')
    expect(loadDraft('u1')).toBeNull()
    localStorage.setItem('alinha-registration-draft:u1', stored(0, { form: VALID }))
    expect(loadDraft('u1')).toBeNull()
    localStorage.setItem('alinha-registration-draft:u1', stored(2, { form: 'x' }))
    expect(loadDraft('u1')).toBeNull()
  })

  it('works without storage', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('QuotaExceeded') })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('SecurityError') })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('SecurityError') })
    expect(saveDraft('u1', VALID)).toBe(false)
    expect(loadDraft('u1')).toBeNull()
    expect(() => clearDraft('u1')).not.toThrow()
    expect(() => forgetExpired()).not.toThrow()
  })
})
