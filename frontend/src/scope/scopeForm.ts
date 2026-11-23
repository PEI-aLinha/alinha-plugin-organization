import type { components } from '@alinha/api-client'
import { SECTION_TITLE } from './labels'

export type Scope = components['schemas']['OrganizationScope']
export type Answers = components['schemas']['ApplicabilityAnswers']
export type AnswersBody = components['schemas']['ApplicabilityAnswersUpdate']
export type EntityTypeOption = components['schemas']['EntityTypeOption']
export type Nis2EntityType = components['schemas']['Nis2EntityType']
export type Nis2Classification = components['schemas']['Nis2Classification']
export type Applies = components['schemas']['FrameworkApplicability']['applies']

export type YesNo = 'yes' | 'no'

/**
 * The applicability questions as the person answers them (organization#4). Yes/no questions start unanswered
 * (`null`) so nothing is assumed; the follow-up checkboxes only count when their question is answered "yes".
 */
export interface ScopeForm {
  entityTypes: Nis2EntityType[]
  /** "None of these describes the company": ticked on purpose, so an empty list is never an oversight. */
  noEntityType: boolean
  publicBody: YesNo | null
  higherEducation: YesNo | null
  criticalEntity: YesNo | null
  significantImpact: YesNo | null
  /** Euros as typed (spaces and dots allowed); optional. */
  balanceSheet: string
  processesPersonalData: YesNo | null
  regularOrRiskyProcessing: boolean
  specialOrCriminalData: boolean
  largeScaleMonitoring: boolean
  largeScaleSpecialData: boolean
  usesAiSystems: YesNo | null
  providesAiSystems: YesNo | null
  highRiskArea: boolean
  interactsOrGeneratesContent: boolean
}

export type ScopeErrors = Partial<Record<keyof ScopeForm, string>>

export const EMPTY_SCOPE_FORM: ScopeForm = {
  entityTypes: [], noEntityType: false,
  publicBody: null, higherEducation: null, criticalEntity: null, significantImpact: null,
  balanceSheet: '',
  processesPersonalData: null,
  regularOrRiskyProcessing: false, specialOrCriminalData: false, largeScaleMonitoring: false, largeScaleSpecialData: false,
  usesAiSystems: null, providesAiSystems: null,
  highRiskArea: false, interactsOrGeneratesContent: false,
}

const YES_NO_QUESTIONS = [
  'publicBody', 'higherEducation', 'criticalEntity', 'significantImpact',
  'processesPersonalData', 'usesAiSystems', 'providesAiSystems',
] as const satisfies readonly (keyof ScopeForm)[]

const ANSWER_REQUIRED = 'Escolha uma resposta.'
const PICK_A_TYPE = 'Escolha pelo menos um tipo, ou indique que nenhum descreve a empresa.'
const EUROS_ONLY = 'Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.'

const yesNo = (value: boolean | undefined): YesNo => (value ? 'yes' : 'no')
const euros = (typed: string) => typed.replace(/[\s.]/g, '')

/** The saved answers as a form; an empty one before the first save. */
export function fromAnswers(answers: Answers | null): ScopeForm {
  if (!answers) return EMPTY_SCOPE_FORM
  const { nis2, gdpr, aiAct } = answers
  const entityTypes = nis2.entityTypes ?? []
  return {
    entityTypes,
    noEntityType: entityTypes.length === 0,
    publicBody: yesNo(answers.publicBody),
    higherEducation: yesNo(nis2.higherEducation),
    criticalEntity: yesNo(nis2.criticalEntity),
    significantImpact: yesNo(nis2.significantImpact),
    balanceSheet: answers.annualBalanceSheetEur == null ? '' : String(answers.annualBalanceSheetEur),
    processesPersonalData: yesNo(gdpr.processesPersonalData),
    regularOrRiskyProcessing: gdpr.regularOrRiskyProcessing ?? false,
    specialOrCriminalData: gdpr.specialOrCriminalData ?? false,
    largeScaleMonitoring: gdpr.largeScaleMonitoring ?? false,
    largeScaleSpecialData: gdpr.largeScaleSpecialData ?? false,
    usesAiSystems: yesNo(aiAct.usesAiSystems),
    providesAiSystems: yesNo(aiAct.providesAiSystems),
    highRiskArea: aiAct.highRiskArea ?? false,
    interactsOrGeneratesContent: aiAct.interactsOrGeneratesContent ?? false,
  }
}

/** The messages to show; an empty object when the form can be sent. Same rules as the backend, plus "answer everything". */
export function validateScope(form: ScopeForm): ScopeErrors {
  const errors: ScopeErrors = {}
  if (form.entityTypes.length === 0 && !form.noEntityType) errors.entityTypes = PICK_A_TYPE
  for (const question of YES_NO_QUESTIONS) {
    if (form[question] === null) errors[question] = ANSWER_REQUIRED
  }
  if (form.balanceSheet.trim() && !/^\d+$/.test(euros(form.balanceSheet))) errors.balanceSheet = EUROS_ONLY
  return errors
}

/**
 * The body of `PUT /organization/scope/answers`. Follow-up answers of a question answered "no" are sent as false,
 * so a box ticked before changing the answer never makes the answers contradictory (the backend refuses those).
 */
export function toAnswersBody(form: ScopeForm, version: number | null): AnswersBody {
  const personalData = form.processesPersonalData === 'yes'
  const ai = form.usesAiSystems === 'yes' || form.providesAiSystems === 'yes'
  const balanceSheet = euros(form.balanceSheet)
  return {
    version,
    publicBody: form.publicBody === 'yes',
    annualBalanceSheetEur: balanceSheet ? Number(balanceSheet) : null,
    nis2: {
      entityTypes: form.noEntityType ? [] : form.entityTypes,
      criticalEntity: form.criticalEntity === 'yes',
      significantImpact: form.significantImpact === 'yes',
      higherEducation: form.higherEducation === 'yes',
    },
    gdpr: {
      processesPersonalData: personalData,
      regularOrRiskyProcessing: personalData && form.regularOrRiskyProcessing,
      specialOrCriminalData: personalData && form.specialOrCriminalData,
      largeScaleMonitoring: personalData && form.largeScaleMonitoring,
      largeScaleSpecialData: personalData && form.largeScaleSpecialData,
    },
    aiAct: {
      usesAiSystems: form.usesAiSystems === 'yes',
      providesAiSystems: form.providesAiSystems === 'yes',
      highRiskArea: ai && form.highRiskArea,
      interactsOrGeneratesContent: ai && form.interactsOrGeneratesContent,
    },
  }
}

/**
 * The section of an annex row: `Anexo I, 1.a)` → `Anexo I, 1.`. Compare sections with equality, never with
 * `startsWith`: `Anexo I, 1.` (energy) is a prefix of `Anexo I, 10.` (space).
 */
export function annexSection(reference: string): string {
  return reference.slice(0, reference.indexOf('.') + 1)
}

export interface EntityTypeSection {
  key: string
  title: string
  options: EntityTypeOption[]
}

/** Suggested types first; the others grouped by annex section, in the order they arrive (annex order). */
export function groupEntityTypes(options: EntityTypeOption[]): { suggested: EntityTypeOption[]; sections: EntityTypeSection[] } {
  const suggested = options.filter((o) => o.suggested)
  const sections = new Map<string, EntityTypeSection>()
  for (const o of options) {
    if (o.suggested) continue
    const key = annexSection(o.reference)
    const section = sections.get(key) ?? { key, title: SECTION_TITLE[key] ?? key, options: [] }
    section.options.push(o)
    sections.set(key, section)
  }
  return { suggested, sections: [...sections.values()] }
}
