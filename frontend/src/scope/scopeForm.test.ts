import { describe, expect, it } from 'vitest'
import { NIS2_LABEL, NIS2_MEANING, SECTION_TITLE } from './labels'
import {
  EMPTY_SCOPE_FORM, annexSection, fromAnswers, groupEntityTypes, toAnswersBody, validateScope,
  type Answers, type EntityTypeOption, type ScopeForm,
} from './scopeForm'

const ANSWERS: Answers = {
  version: 2,
  publicBody: false,
  annualBalanceSheetEur: 43_000_000,
  nis2: { entityTypes: ['MOTOR_VEHICLE_MANUFACTURING'], criticalEntity: false, significantImpact: true, higherEducation: false },
  gdpr: {
    processesPersonalData: true, regularOrRiskyProcessing: true, specialOrCriminalData: false,
    largeScaleMonitoring: false, largeScaleSpecialData: false,
  },
  aiAct: { usesAiSystems: true, providesAiSystems: false, highRiskArea: false, interactsOrGeneratesContent: true },
  answeredAt: '2026-11-20T10:00:00Z',
  updatedAt: '2026-11-21T10:00:00Z',
}

/** Every question answered, with one entity type. */
const COMPLETE: ScopeForm = {
  ...EMPTY_SCOPE_FORM,
  entityTypes: ['FOOD'],
  publicBody: 'no', higherEducation: 'no', criticalEntity: 'no', significantImpact: 'no',
  processesPersonalData: 'yes', usesAiSystems: 'no', providesAiSystems: 'no',
}

const option = (id: EntityTypeOption['id'], reference: string, suggested = false): EntityTypeOption =>
  ({ id, annex: reference.startsWith('Anexo II') ? 'II' : 'I', reference, label: id, suggested })

describe('fromAnswers', () => {
  it('starts empty when there are no answers', () => {
    const form = fromAnswers(null)

    expect(form).toEqual(EMPTY_SCOPE_FORM)
    expect(form.processesPersonalData).toBeNull()
    expect(form.noEntityType).toBe(false)
  })

  it('turns saved answers into the form', () => {
    const form = fromAnswers(ANSWERS)

    expect(form.entityTypes).toEqual(['MOTOR_VEHICLE_MANUFACTURING'])
    expect(form.noEntityType).toBe(false)
    expect(form.significantImpact).toBe('yes')
    expect(form.publicBody).toBe('no')
    expect(form.balanceSheet).toBe('43000000')
    expect(form.processesPersonalData).toBe('yes')
    expect(form.regularOrRiskyProcessing).toBe(true)
    expect(form.usesAiSystems).toBe('yes')
    expect(form.interactsOrGeneratesContent).toBe(true)
  })

  it('marks "none" when saved answers have no entity type', () => {
    const form = fromAnswers({ ...ANSWERS, nis2: { ...ANSWERS.nis2, entityTypes: [] } })

    expect(form.noEntityType).toBe(true)
  })
})

describe('validateScope', () => {
  it('accepts a complete form', () => {
    expect(validateScope(COMPLETE)).toEqual({})
  })

  it('requires a type or the explicit none', () => {
    expect(validateScope({ ...COMPLETE, entityTypes: [] }).entityTypes)
      .toBe('Escolha pelo menos um tipo, ou indique que nenhum descreve a empresa.')
    expect(validateScope({ ...COMPLETE, entityTypes: [], noEntityType: true }).entityTypes).toBeUndefined()
  })

  it('requires every yes/no question', () => {
    const errors = validateScope({ ...EMPTY_SCOPE_FORM, noEntityType: true })

    expect(errors).toEqual({
      publicBody: 'Escolha uma resposta.',
      higherEducation: 'Escolha uma resposta.',
      criticalEntity: 'Escolha uma resposta.',
      significantImpact: 'Escolha uma resposta.',
      processesPersonalData: 'Escolha uma resposta.',
      usesAiSystems: 'Escolha uma resposta.',
      providesAiSystems: 'Escolha uma resposta.',
    })
  })

  it('accepts a balance sheet with spaces or dots and refuses anything else', () => {
    expect(validateScope({ ...COMPLETE, balanceSheet: '43 000 000' }).balanceSheet).toBeUndefined()
    expect(validateScope({ ...COMPLETE, balanceSheet: '43.000.000' }).balanceSheet).toBeUndefined()
    expect(validateScope({ ...COMPLETE, balanceSheet: '43 milhões' }).balanceSheet)
      .toBe('Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.')
    expect(validateScope({ ...COMPLETE, balanceSheet: '-5' }).balanceSheet)
      .toBe('Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.')
  })
})

describe('toAnswersBody', () => {
  it('sends the answers with the version', () => {
    const body = toAnswersBody({ ...COMPLETE, significantImpact: 'yes', regularOrRiskyProcessing: true }, 2)

    expect(body).toEqual({
      version: 2,
      publicBody: false,
      annualBalanceSheetEur: null,
      nis2: { entityTypes: ['FOOD'], criticalEntity: false, significantImpact: true, higherEducation: false },
      gdpr: {
        processesPersonalData: true, regularOrRiskyProcessing: true, specialOrCriminalData: false,
        largeScaleMonitoring: false, largeScaleSpecialData: false,
      },
      aiAct: { usesAiSystems: false, providesAiSystems: false, highRiskArea: false, interactsOrGeneratesContent: false },
    })
  })

  it('clears personal-data details when the company does not process personal data', () => {
    const body = toAnswersBody({ ...COMPLETE, processesPersonalData: 'no', specialOrCriminalData: true, largeScaleMonitoring: true }, null)

    expect(body.gdpr).toEqual({
      processesPersonalData: false, regularOrRiskyProcessing: false, specialOrCriminalData: false,
      largeScaleMonitoring: false, largeScaleSpecialData: false,
    })
  })

  it('clears AI details when the company neither uses nor provides AI', () => {
    const body = toAnswersBody({ ...COMPLETE, highRiskArea: true, interactsOrGeneratesContent: true }, null)

    expect(body.aiAct).toEqual({ usesAiSystems: false, providesAiSystems: false, highRiskArea: false, interactsOrGeneratesContent: false })
  })

  it('keeps AI details for a company that provides AI', () => {
    const body = toAnswersBody({ ...COMPLETE, providesAiSystems: 'yes', highRiskArea: true }, null)

    expect(body.aiAct).toEqual({ usesAiSystems: false, providesAiSystems: true, highRiskArea: true, interactsOrGeneratesContent: false })
  })

  it('sends no entity types when "none" is ticked', () => {
    expect(toAnswersBody({ ...COMPLETE, noEntityType: true }, null).nis2?.entityTypes).toEqual([])
  })

  it('turns the balance sheet into a number, or null when empty', () => {
    expect(toAnswersBody({ ...COMPLETE, balanceSheet: '43 000 000' }, null).annualBalanceSheetEur).toBe(43_000_000)
    expect(toAnswersBody({ ...COMPLETE, balanceSheet: '  ' }, null).annualBalanceSheetEur).toBeNull()
  })

  it('round-trips saved answers', () => {
    const { version, answeredAt, updatedAt, ...answers } = ANSWERS
    expect(toAnswersBody(fromAnswers(ANSWERS), version)).toEqual({ version, ...answers })
    expect(answeredAt).toBeDefined()
    expect(updatedAt).toBeDefined()
  })
})

describe('annexSection and groupEntityTypes', () => {
  it('cuts the reference at the first dot', () => {
    expect(annexSection('Anexo I, 1.a)')).toBe('Anexo I, 1.')
    expect(annexSection('Anexo I, 10.')).toBe('Anexo I, 10.')
  })

  it('groups Anexo I, 10. apart from Anexo I, 1.', () => {
    const { sections } = groupEntityTypes([
      option('ELECTRICITY_PRODUCER', 'Anexo I, 1.a)'),
      option('HYDROGEN_OPERATOR', 'Anexo I, 1.e)'),
      option('SPACE_GROUND_INFRASTRUCTURE', 'Anexo I, 10.'),
    ])

    expect(sections.map((s) => [s.title, s.options.map((o) => o.id)])).toEqual([
      ['Energia', ['ELECTRICITY_PRODUCER', 'HYDROGEN_OPERATOR']],
      ['Espaço', ['SPACE_GROUND_INFRASTRUCTURE']],
    ])
  })

  it('puts suggested types first and not again in their section', () => {
    const { suggested, sections } = groupEntityTypes([
      option('FOOD', 'Anexo II, 4.'),
      option('MOTOR_VEHICLE_MANUFACTURING', 'Anexo II, 5.e)', true),
      option('MEDICAL_DEVICES', 'Anexo II, 5.a)'),
    ])

    expect(suggested.map((o) => o.id)).toEqual(['MOTOR_VEHICLE_MANUFACTURING'])
    expect(sections.flatMap((s) => s.options.map((o) => o.id))).toEqual(['FOOD', 'MEDICAL_DEVICES'])
  })

  it('falls back to the section key for an unknown section', () => {
    expect(groupEntityTypes([option('FOOD', 'Anexo III, 1.')]).sections[0].title).toBe('Anexo III, 1.')
  })
})

describe('labels', () => {
  it('names and explains every classification', () => {
    for (const c of ['essential', 'important', 'not-covered', 'to-be-assessed'] as const) {
      expect(NIS2_LABEL[c]).toBeTruthy()
      expect(NIS2_MEANING[c].length).toBeGreaterThan(0)
    }
    expect(NIS2_LABEL['not-covered']).toBe('Não abrangida')
  })

  it('has a title for every annex section', () => {
    expect(Object.keys(SECTION_TITLE)).toHaveLength(17)
    expect(SECTION_TITLE['Anexo I, 10.']).toBe('Espaço')
  })
})
