import type { components } from '@alinha/api-client'
import type { FieldProblem } from '@alinha/plugin-sdk'
import { sectorById } from './sectors'

export type OrganizationProfileInput = components['schemas']['OrganizationProfileInput']
export type OrganizationProfile = components['schemas']['OrganizationProfile']

/** What the person types, always as text. Converted with [toOrganizationInput] on submit. */
export interface OrganizationForm {
  name: string
  nif: string
  cae: string
  sector: string
  subsector: string
  services: string
  employees: string
  turnover: string
  address: string
  postalCode: string
  city: string
  phone: string
  email: string
  contactName: string
  contactPhone: string
}

export type FieldKey = keyof OrganizationForm
export type OrganizationErrors = Partial<Record<FieldKey, string>>

export const EMPTY_ORGANIZATION: OrganizationForm = {
  name: '', nif: '', cae: '', sector: '', subsector: '', services: '', employees: '', turnover: '',
  address: '', postalCode: '', city: '', phone: '', email: '', contactName: '', contactPhone: '',
}

export type StepId = 'company' | 'activity' | 'size' | 'contacts' | 'summary'

export interface Step {
  id: StepId
  label: string
  fields: readonly FieldKey[]
}

/** The wizard's steps, in order. The summary has no fields of its own. */
export const STEPS: readonly Step[] = [
  { id: 'company', label: 'Empresa', fields: ['name', 'nif', 'cae'] },
  { id: 'activity', label: 'Atividade', fields: ['sector', 'subsector', 'services'] },
  { id: 'size', label: 'Dimensão', fields: ['employees', 'turnover'] },
  { id: 'contacts', label: 'Contactos', fields: ['address', 'postalCode', 'city', 'phone', 'email', 'contactName', 'contactPhone'] },
  { id: 'summary', label: 'Resumo', fields: [] },
]

export const isStepId = (value: string | null): value is StepId => STEPS.some((s) => s.id === value)
export const stepIndex = (id: StepId): number => STEPS.findIndex((s) => s.id === id)

/** Same limits as the backend (`OrganizationProfileValidator.kt`, `MaxLength`). */
const MAX = { name: 200, services: 2000, street: 300, city: 120, email: 254, contactName: 120, number: 20 } as const
const MAX_EMPLOYEES = 10_000_000
/** Whole euros above this are not safe JavaScript integers. */
const MAX_TURNOVER_DIGITS = 15
const NIF_LENGTH = 9
const CAE_LENGTH = 5
const MIN_SERVICES_LENGTH = 10
const PHONE = /^[29]\d{8}$/
const POSTAL_CODE = /^\d{4}-\d{3}$/
/** NIF, CAE, phones and amounts may be typed with spaces, dots or dashes, never letters. */
const NUMBER_TYPING = /^[0-9 .-]*$/

export const digits = (value: string): string => value.replace(/[^0-9]/g, '')

/** Portuguese NIF: 9 digits, the last one a mod-11 check digit (same rule as the backend). */
export function isValidNif(value: string): boolean {
  const d = digits(value)
  if (d.length !== NIF_LENGTH) return false
  const sum = [...d.slice(0, NIF_LENGTH - 1)].reduce((acc, c, i) => acc + Number(c) * (NIF_LENGTH - i), 0)
  const rest = sum % 11
  return Number(d[NIF_LENGTH - 1]) === (rest < 2 ? 0 : 11 - rest)
}

/** One "@", no spaces, a dotted domain with no empty label (same rule as the backend). */
export function isValidEmail(value: string): boolean {
  if (/\s/.test(value)) return false
  const parts = value.split('@')
  if (parts.length !== 2 || !parts[0]) return false
  const labels = parts[1].split('.')
  return labels.length >= 2 && labels.every(Boolean)
}

const tooLong = (max: number) => `Use no máximo ${max} caracteres.`

function numberError(raw: string, notNumber: string): string | undefined {
  if (raw.length > MAX.number) return tooLong(MAX.number)
  if (!NUMBER_TYPING.test(raw)) return notNumber
  return undefined
}

function phoneError(raw: string, missing: string): string | undefined {
  const typed = numberError(raw.trim(), 'O telefone só tem números.')
  if (typed) return typed
  if (!digits(raw)) return missing
  if (!PHONE.test(digits(raw))) return 'O telefone tem 9 dígitos e começa por 2 ou 9.'
  return undefined
}

function textError(value: string, max: number, missing: string): string | undefined {
  const text = value.trim()
  if (!text) return missing
  if (text.length > max) return tooLong(max)
  return undefined
}

/** Rules per field. Messages match the backend validator, in plain European Portuguese. */
const RULES: Record<FieldKey, (f: OrganizationForm) => string | undefined> = {
  name: (f) => textError(f.name, MAX.name, 'Indique o nome da empresa, como está na certidão permanente.'),
  nif: (f) => {
    const typed = numberError(f.nif.trim(), 'O NIF só tem números.')
    if (typed) return typed
    const nif = digits(f.nif)
    if (!nif) return 'Indique o NIF da empresa.'
    if (nif.length !== NIF_LENGTH) return `O NIF tem 9 dígitos. Este tem ${nif.length}.`
    if (!isValidNif(nif)) return 'Este NIF não é válido. Confirme se algum número está trocado.'
    return undefined
  },
  cae: (f) => {
    const typed = numberError(f.cae.trim(), 'O código CAE só tem números. Exemplo: 29320.')
    if (typed) return typed
    const cae = digits(f.cae)
    if (!cae) return 'Indique o código CAE principal.'
    if (cae.length !== CAE_LENGTH) return 'O código CAE tem 5 dígitos. Exemplo: 29320.'
    return undefined
  },
  sector: (f) => (sectorById(f.sector) ? undefined : 'Escolha o setor de atividade.'),
  subsector: (f) => {
    const sector = sectorById(f.sector)
    if (!sector || sector.subsectors.length === 0) return undefined
    return sector.subsectors.some((s) => s.id === f.subsector) ? undefined : 'Escolha o subsetor.'
  },
  services: (f) => {
    const text = f.services.trim()
    if (text.length < MIN_SERVICES_LENGTH) return 'Descreva em poucas palavras o que a empresa faz ou vende.'
    if (text.length > MAX.services) return tooLong(MAX.services)
    return undefined
  },
  employees: (f) => {
    const raw = f.employees.trim()
    if (!raw) return 'Indique quantas pessoas trabalham na empresa.'
    if (!/^[0-9][0-9 .]*$/.test(raw)) return 'Use só números inteiros. Exemplo: 120.'
    const n = Number(digits(raw))
    if (n < 1) return 'A empresa tem de ter pelo menos 1 trabalhador.'
    if (n > MAX_EMPLOYEES) return 'Confirme o número de trabalhadores: parece demasiado alto.'
    return undefined
  },
  turnover: (f) => {
    const raw = f.turnover.trim()
    if (!raw) return 'Indique o volume de negócios do último ano.'
    if (!/^[0-9][0-9 .]*$/.test(raw)) return 'Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.'
    if (digits(raw).replace(/^0+/, '').length > MAX_TURNOVER_DIGITS) return 'Confirme o volume de negócios: parece demasiado alto.'
    return undefined
  },
  address: (f) => textError(f.address, MAX.street, 'Indique a morada da sede.'),
  postalCode: (f) => {
    const value = f.postalCode.trim()
    if (!value) return 'Indique o código postal.'
    return POSTAL_CODE.test(value) ? undefined : 'Use o formato 0000-000.'
  },
  city: (f) => textError(f.city, MAX.city, 'Indique a localidade.'),
  phone: (f) => phoneError(f.phone, 'Indique o telefone da empresa.'),
  email: (f) => {
    const value = f.email.trim()
    if (!value) return 'Indique o email geral da empresa.'
    if (value.length > MAX.email) return tooLong(MAX.email)
    return isValidEmail(value) ? undefined : 'Este email não parece válido. Exemplo: geral@empresa.pt'
  },
  contactName: (f) => textError(f.contactName, MAX.contactName, 'Indique quem é o ponto de contacto permanente.'),
  contactPhone: (f) => phoneError(f.contactPhone, 'Indique um telefone disponível a qualquer hora.'),
}

function validateFields(form: OrganizationForm, fields: readonly FieldKey[]): OrganizationErrors {
  return Object.fromEntries(
    fields.flatMap((field) => {
      const message = RULES[field](form)
      return message ? [[field, message]] : []
    }),
  )
}

/** Errors of one step's fields (empty object when the step is complete). */
export const validateStep = (step: StepId, form: OrganizationForm): OrganizationErrors =>
  validateFields(form, STEPS[stepIndex(step)].fields)

/** Errors of every field. */
export const validateOrganization = (form: OrganizationForm): OrganizationErrors =>
  validateFields(form, STEPS.flatMap((s) => s.fields))

/** First step with an error, or undefined when every step is complete. */
export function firstStepWithErrors(errors: OrganizationErrors): StepId | undefined {
  return STEPS.find((s) => s.fields.some((f) => errors[f]))?.id
}

/** Backend JSON paths (Problem Details `errors[].field`) → form fields. */
const SERVER_FIELDS: Record<string, FieldKey> = {
  name: 'name', nif: 'nif', cae: 'cae', sector: 'sector', subsector: 'subsector', services: 'services',
  employees: 'employees', turnover: 'turnover',
  'address.street': 'address', 'address.postalCode': 'postalCode', 'address.city': 'city',
  'contacts.phone': 'phone', 'contacts.email': 'email',
  'permanentContact.name': 'contactName', 'permanentContact.phone': 'contactPhone',
}

/** Field errors sent by the server, on the form's fields. Unknown paths are dropped (the summary shows a note). */
export function fromServerErrors(errors: readonly FieldProblem[] = []): OrganizationErrors {
  return Object.fromEntries(errors.flatMap((e) => (SERVER_FIELDS[e.field] ? [[SERVER_FIELDS[e.field], e.message]] : [])))
}

/** Form → request body: numbers without separators, NIF, CAE and phones as digits, email in lower case. */
export function toOrganizationInput(f: OrganizationForm): OrganizationProfileInput {
  return {
    name: f.name.trim(),
    nif: digits(f.nif),
    cae: digits(f.cae),
    sector: f.sector,
    subsector: f.subsector || null,
    services: f.services.trim(),
    employees: Number(digits(f.employees)),
    turnover: Number(digits(f.turnover)),
    address: { street: f.address.trim(), postalCode: f.postalCode.trim(), city: f.city.trim() },
    contacts: { phone: digits(f.phone), email: f.email.trim().toLowerCase() },
    permanentContact: { name: f.contactName.trim(), phone: digits(f.contactPhone) },
  }
}
