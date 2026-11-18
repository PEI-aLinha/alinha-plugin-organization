import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearDraft, isEmptyForm, loadDraft, saveDraft } from './draft'
import { EMPTY_ORGANIZATION } from './organizationForm'
import { VALID } from '../test/fixtures'

describe('registration draft', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.restoreAllMocks())

  it('saves and loads the form, per account', () => {
    expect(saveDraft('u1', VALID)).toBe(true)
    expect(loadDraft('u1')).toEqual(VALID)
    expect(loadDraft('u2')).toBeNull()
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
    localStorage.setItem('alinha-registration-draft:u1', JSON.stringify({ v: 1, form: { name: 'Acme', nif: 5, evil: 'x' } }))
    expect(loadDraft('u1')).toEqual({ ...EMPTY_ORGANIZATION, name: 'Acme' })
  })

  it('ignores broken or old drafts', () => {
    localStorage.setItem('alinha-registration-draft:u1', '{')
    expect(loadDraft('u1')).toBeNull()
    localStorage.setItem('alinha-registration-draft:u1', JSON.stringify({ v: 0, form: VALID }))
    expect(loadDraft('u1')).toBeNull()
    localStorage.setItem('alinha-registration-draft:u1', JSON.stringify({ v: 1, form: 'x' }))
    expect(loadDraft('u1')).toBeNull()
  })

  it('works without storage', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('QuotaExceeded') })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('SecurityError') })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('SecurityError') })
    expect(saveDraft('u1', VALID)).toBe(false)
    expect(loadDraft('u1')).toBeNull()
    expect(() => clearDraft('u1')).not.toThrow()
  })
})
