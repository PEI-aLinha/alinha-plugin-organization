import type { EntityTypeOption, Scope } from '../scope/scopeForm'

/** A few entity types, in annex order; the car-parts maker's own type comes suggested. */
export const ENTITY_TYPES: EntityTypeOption[] = [
  { id: 'ELECTRICITY_PRODUCER', annex: 'I', reference: 'Anexo I, 1.a)', label: 'Produção de eletricidade', suggested: false },
  { id: 'DNS_SERVICE_PROVIDER', annex: 'I', reference: 'Anexo I, 8.', label: 'Prestação de serviços de DNS', suggested: false },
  { id: 'SPACE_GROUND_INFRASTRUCTURE', annex: 'I', reference: 'Anexo I, 10.', label: 'Infraestruturas terrestres que apoiam serviços espaciais', suggested: false },
  { id: 'FOOD', annex: 'II', reference: 'Anexo II, 4.', label: 'Distribuição por grosso, produção ou transformação industrial de produtos alimentares', suggested: false },
  { id: 'MOTOR_VEHICLE_MANUFACTURING', annex: 'II', reference: 'Anexo II, 5.e)', label: 'Fabrico de veículos automóveis, reboques e semirreboques', suggested: true },
]

/** Registered, nothing answered yet. */
export const SCOPE_EMPTY: Scope = {
  answers: null,
  result: null,
  qualification: null,
  nis2: null,
  entityTypes: ENTITY_TYPES,
}

/** Componentes Águeda (120 employees, 18.5 M€) answered: an important entity, GDPR and AI Act apply. */
export const SCOPE_ANSWERED: Scope = {
  answers: {
    version: 1,
    publicBody: false,
    annualBalanceSheetEur: null,
    nis2: { entityTypes: ['MOTOR_VEHICLE_MANUFACTURING'], criticalEntity: false, significantImpact: false, higherEducation: false },
    gdpr: {
      processesPersonalData: true, regularOrRiskyProcessing: true, specialOrCriminalData: false,
      largeScaleMonitoring: false, largeScaleSpecialData: false,
    },
    aiAct: { usesAiSystems: true, providesAiSystems: false, highRiskArea: true, interactsOrGeneratesContent: false },
    answeredAt: '2026-11-20T10:00:00Z',
    updatedAt: '2026-11-21T10:00:00Z',
  },
  result: {
    rulesVersion: 'applicability-2026.11-v1',
    size: 'medium',
    nis2Classification: 'important',
    cncsMayQualify: false,
    indicative: true,
    frameworks: [
      {
        framework: 'nis2-qnrcs',
        applies: 'yes',
        findings: [
          {
            id: 'nis2.size.medium', legalBasis: 'RJC, anexo III, art. 2.º (Recomendação 2003/361/CE)', review: false,
            explanation: 'Pelo número de trabalhadores e pelo volume de negócios ou balanço, a organização é média empresa.',
          },
          {
            id: 'nis2.important.medium-or-annex-ii', legalBasis: 'RJC, art. 3.º, n.º 1, e art. 6.º, n.º 2', review: false,
            explanation: 'Fabrico de veículos automóveis, reboques e semirreboques: é um setor abrangido e a organização é pelo menos média empresa, por isso é entidade importante.',
          },
        ],
      },
      {
        framework: 'gdpr',
        applies: 'yes',
        findings: [
          {
            id: 'gdpr.applies', legalBasis: 'RGPD, art. 2.º, n.º 1, art. 3.º, n.º 1, e art. 4.º, 1) e 2)', review: false,
            explanation: 'Trata dados de pessoas (clientes, trabalhadores ou contactos), por isso o RGPD aplica-se.',
          },
        ],
      },
      {
        framework: 'ai-act',
        applies: 'yes',
        findings: [
          {
            id: 'ai-act.deployer', legalBasis: 'AI Act, art. 2.º, n.º 1, alínea b), e art. 3.º, 4)', review: false,
            explanation: 'Usa sistemas de IA no trabalho: é responsável pela implantação.',
          },
          {
            id: 'ai-act.possible-high-risk', legalBasis: 'AI Act, art. 6.º, n.os 2 e 3, e anexo III', review: true,
            explanation: 'Usa ou desenvolve IA numa área sensível (por exemplo recrutamento, crédito, seguros ou educação). Pode ser um sistema de risco elevado, com obrigações adicionais. O consultor confirma.',
          },
        ],
      },
    ],
  },
  qualification: null,
  nis2: { classification: 'important', official: false, suggested: 'important' },
  entityTypes: ENTITY_TYPES,
}

/** A small food distributor (30 employees): its sector is covered by NIS2, but small companies are not. */
export const SCOPE_NOT_COVERED: Scope = {
  ...SCOPE_ANSWERED,
  answers: {
    ...SCOPE_ANSWERED.answers!,
    nis2: { entityTypes: ['FOOD'], criticalEntity: false, significantImpact: false, higherEducation: false },
  },
  result: {
    rulesVersion: 'applicability-2026.11-v1',
    size: 'small',
    nis2Classification: 'not-covered',
    cncsMayQualify: false,
    indicative: true,
    frameworks: [
      {
        framework: 'nis2-qnrcs',
        applies: 'no',
        findings: [
          {
            id: 'nis2.size.small', legalBasis: 'RJC, anexo III, art. 2.º (Recomendação 2003/361/CE)', review: false,
            explanation: 'Pelo número de trabalhadores e pelo volume de negócios ou balanço, a organização é pequena empresa.',
          },
          {
            id: 'nis2.not_covered.small', legalBasis: 'RJC, art. 3.º, n.º 1, alínea a)', review: false,
            explanation: 'Distribuição por grosso, produção ou transformação industrial de produtos alimentares: o setor é abrangido, mas as micro e pequenas empresas ficam de fora.',
          },
        ],
      },
      ...SCOPE_ANSWERED.result!.frameworks.slice(1),
    ],
  },
  nis2: { classification: 'not-covered', official: false, suggested: 'not-covered' },
}

/** The CNCS qualified the organization as essential, above the suggestion. */
export const SCOPE_OFFICIAL: Scope = {
  ...SCOPE_ANSWERED,
  qualification: {
    version: 0,
    classification: 'essential',
    actDate: '2026-01-15',
    actReference: 'CNCS/QA/2026/0042',
    registeredAt: '2026-11-22T09:00:00Z',
    updatedAt: '2026-11-22T09:00:00Z',
  },
  nis2: { classification: 'essential', official: true, suggested: 'important' },
}
