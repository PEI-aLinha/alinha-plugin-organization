import { describe, expect, it } from 'vitest'
import {
  EMPTY_ORGANIZATION, firstStepWithErrors, fromServerErrors, isValidEmail, isValidNif, toOrganizationInput,
  validateOrganization, validateStep,
} from './organizationForm'
import { SECTORS } from './sectors'
import { VALID } from '../test/fixtures'

describe('isValidNif', () => {
  it('checks the mod-11 digit', () => {
    expect(isValidNif('509180027')).toBe(true)
    expect(isValidNif('509 180 027')).toBe(true)
    expect(isValidNif('509180028')).toBe(false)
    expect(isValidNif('50918002')).toBe(false)
  })
})

describe('isValidEmail', () => {
  it('needs one @ and a dotted domain without spaces', () => {
    expect(isValidEmail('geral@empresa.pt')).toBe(true)
    expect(isValidEmail('geral@empresa')).toBe(false)
    expect(isValidEmail('geral@@empresa.pt')).toBe(false)
    expect(isValidEmail('ge ral@empresa.pt')).toBe(false)
    expect(isValidEmail('geral@empresa..pt')).toBe(false)
  })
})

describe('validation', () => {
  it('accepts a complete form', () => {
    expect(validateOrganization(VALID)).toEqual({})
  })

  it('checks only the fields of the given step', () => {
    expect(Object.keys(validateStep('company', EMPTY_ORGANIZATION))).toEqual(['name', 'nif', 'cae'])
    expect(Object.keys(validateStep('size', EMPTY_ORGANIZATION))).toEqual(['employees', 'turnover'])
    expect(validateStep('summary', EMPTY_ORGANIZATION)).toEqual({})
  })

  it('explains what is wrong in plain Portuguese', () => {
    const errors = validateOrganization({
      ...VALID, nif: '509180028', cae: '293', employees: '0', postalCode: '3750750', phone: '123456789', email: 'x@y',
    })
    expect(errors).toEqual({
      nif: 'Este NIF não é válido. Confirme se algum número está trocado.',
      cae: 'O código CAE tem 5 dígitos. Exemplo: 29320.',
      employees: 'A empresa tem de ter pelo menos 1 trabalhador.',
      postalCode: 'Use o formato 0000-000.',
      phone: 'O telefone tem 9 dígitos e começa por 2 ou 9.',
      email: 'Este email não parece válido. Exemplo: geral@empresa.pt',
    })
  })

  it('refuses letters in numbers, like the server', () => {
    expect(validateOrganization({ ...VALID, nif: '509l80027' }).nif).toBe('O NIF só tem números.')
    expect(validateOrganization({ ...VALID, turnover: '18,5 M' }).turnover).toMatch(/Use só números/)
    expect(validateOrganization({ ...VALID, contactPhone: '9123456789012345678901' }).contactPhone).toBe('Use no máximo 20 caracteres.')
  })

  it('accepts whole numbers grouped in thousands, never decimals', () => {
    for (const ok of ['120', '1 000', '1 500 000', '1.500.000', '1000000'])
      expect(validateOrganization({ ...VALID, employees: ok, turnover: ok })).toEqual({})
    for (const bad of ['1.5', '18.5', '1 00', '12 3456', '1..000'])
      expect(validateOrganization({ ...VALID, employees: bad, turnover: bad })).toMatchObject({
        employees: 'Use só números inteiros. Exemplo: 120.',
        turnover: expect.stringMatching(/Use só números/),
      })
  })

  it('asks for a subsector only when the sector has them', () => {
    expect(validateOrganization({ ...VALID, subsector: '' }).subsector).toBe('Escolha o subsetor.')
    expect(validateOrganization({ ...VALID, subsector: 'aereo' }).subsector).toBe('Escolha o subsetor.')
    expect(validateOrganization({ ...VALID, sector: 'saude', subsector: '' })).toEqual({})
  })

  it('applies the server length limits', () => {
    expect(validateOrganization({ ...VALID, name: 'x'.repeat(201) }).name).toBe('Use no máximo 200 caracteres.')
    expect(validateOrganization({ ...VALID, turnover: '1'.repeat(16) }).turnover).toMatch(/demasiado alto/)
  })

  it('finds the first step with an error', () => {
    expect(firstStepWithErrors({ email: 'x', employees: 'y' })).toBe('size')
    expect(firstStepWithErrors({})).toBeUndefined()
  })
})

describe('fromServerErrors', () => {
  it('maps the server JSON paths to form fields and drops unknown ones', () => {
    expect(fromServerErrors([
      { field: 'address.postalCode', message: 'Use o formato 0000-000.' },
      { field: 'permanentContact.phone', message: 'p' },
      { field: 'unknown', message: 'u' },
    ])).toEqual({ postalCode: 'Use o formato 0000-000.', contactPhone: 'p' })
    expect(fromServerErrors()).toEqual({})
  })
})

describe('toOrganizationInput', () => {
  it('sends digits, whole numbers and a lower-case email', () => {
    expect(toOrganizationInput(VALID)).toEqual({
      name: 'Componentes Águeda, Lda.',
      nif: '509180027',
      cae: '29320',
      sector: 'industria',
      subsector: 'automoveis',
      services: 'Fabrico de componentes para a indústria automóvel.',
      employees: 120,
      turnover: 18500000,
      address: { street: 'Rua da Zona Industrial do Casarão, 45', postalCode: '3750-750', city: 'Águeda' },
      contacts: { phone: '234600120', email: 'geral@compagueda.pt' },
      permanentContact: { name: 'Rui Santos', phone: '912345678' },
      domain: 'compagueda.pt',
    })
  })

  it('sends no subsector when there is none', () => {
    expect(toOrganizationInput({ ...VALID, sector: 'saude', subsector: '' }).subsector).toBeNull()
  })

  it('drops a subsector left over from another sector', () => {
    expect(toOrganizationInput({ ...VALID, sector: 'saude', subsector: 'automoveis' }).subsector).toBeNull()
  })
})

describe('sectors', () => {
  it('have unique ids', () => {
    const ids = SECTORS.map((s) => s.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})

describe('domain', () => {
  it('is optional, normalised on submit and checked when typed', () => {
    expect(toOrganizationInput({ ...VALID, domain: '' }).domain).toBeNull()
    expect(toOrganizationInput(VALID).domain).toBe('compagueda.pt')
    expect(validateStep('contacts', { ...VALID, domain: '' })).toEqual({})
    expect(validateStep('contacts', { ...VALID, domain: '10.0.0.1' })).toEqual({ domain: 'Indique só o domínio, por exemplo empresa.pt.' })
  })

  it('takes the server message for the domain', () => {
    expect(fromServerErrors([{ field: 'domain', message: 'Partilhado.' }])).toEqual({ domain: 'Partilhado.' })
  })
})

