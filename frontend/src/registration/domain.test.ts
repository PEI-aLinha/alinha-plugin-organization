import { describe, expect, it } from 'vitest'
import { normalizeDomain } from './domain'

describe('normalizeDomain', () => {
  it.each([
    ['empresa.pt', 'empresa.pt'],
    ['  Empresa.PT ', 'empresa.pt'],
    ['https://www.empresa.pt/', 'empresa.pt'],
    ['http://empresa.pt:8080/contactos?x=1', 'empresa.pt'],
    ['loja.empresa.com.pt.', 'loja.empresa.com.pt'],
    ['açores-turismo.pt', 'xn--aores-turismo-igb.pt'],
  ])('reduces %j to the domain', (typed, expected) => {
    expect(normalizeDomain(typed)).toBe(expected)
  })

  it.each(['', 'empresa', '192.168.1.10', '[::1]', 'geral@empresa.pt', 'emp resa.pt', 'empresa.p', 'a.123', 'x_y.pt'])(
    'refuses %j',
    (typed) => {
      expect(normalizeDomain(typed)).toBeNull()
    },
  )
})
