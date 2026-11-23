import type { Applies, Nis2Classification } from './scopeForm'

/** Plain-language names of the NIS2 classifications (Decree-Law 125/2025). */
export const NIS2_LABEL: Record<Nis2Classification, string> = {
  essential: 'Entidade essencial',
  important: 'Entidade importante',
  'not-covered': 'Não abrangida',
  'to-be-assessed': 'Por confirmar',
}

/**
 * What each classification means for the company. Text and fine amounts from the prototype
 * (`features/organization/framing/nis2Framing.ts`); the advisors confirm them in review.
 */
export const NIS2_MEANING: Record<Nis2Classification, string[]> = {
  essential: [
    'Tem de cumprir todas as obrigações de cibersegurança da lei.',
    'O CNCS pode fiscalizar a qualquer momento, mesmo sem incidentes.',
    'As coimas podem chegar a 10 milhões de euros ou 2% do volume de negócios.',
  ],
  important: [
    'Tem de cumprir as mesmas obrigações de cibersegurança que as entidades essenciais.',
    'Em regra, o CNCS só fiscaliza depois de um incidente ou de uma queixa.',
    'As coimas podem chegar a 7 milhões de euros ou 1,4% do volume de negócios.',
  ],
  'not-covered': [
    'A lei de cibersegurança não obriga a empresa a registar-se no CNCS.',
    'Proteger os sistemas continua a ser uma boa prática, e o RGPD pode aplicar-se na mesma.',
  ],
  'to-be-assessed': [
    'Entidades públicas e instituições de ensino superior têm regras próprias na lei.',
    'O consultor confirma a classificação consigo e com o CNCS.',
  ],
}

export const APPLIES_LABEL: Record<Applies, string> = {
  yes: 'Aplica-se',
  no: 'Não se aplica',
  review: 'Por confirmar',
}

/** The frameworks the applicability rules check, by framework id (`frameworks/<id>/`). */
export const FRAMEWORKS: Record<string, { name: string; fullName: string; summary: string }> = {
  'nis2-qnrcs': {
    name: 'NIS2',
    fullName: 'Regime Jurídico da Cibersegurança (Decreto-Lei 125/2025)',
    summary: 'Regras de cibersegurança para empresas de setores importantes para o país.',
  },
  gdpr: {
    name: 'RGPD',
    fullName: 'Regulamento Geral sobre a Proteção de Dados',
    summary: 'Regras para guardar e usar informação sobre pessoas.',
  },
  'ai-act': {
    name: 'Regulamento da IA',
    fullName: 'Regulamento Europeu da Inteligência Artificial (AI Act)',
    summary: 'Regras para quem usa ou desenvolve inteligência artificial.',
  },
}

export const MYCIBER_REMINDER =
  'Isto é uma sugestão. A qualificação oficial é atribuída pelo CNCS depois de a empresa se registar na plataforma ' +
  'MyCiber. Quando a receber, registe-a aqui.'

/** Plain-language title of each section of annexes I and II, keyed by `annexSection(reference)`. */
export const SECTION_TITLE: Record<string, string> = {
  'Anexo I, 1.': 'Energia',
  'Anexo I, 2.': 'Transportes',
  'Anexo I, 3.': 'Setor bancário',
  'Anexo I, 4.': 'Infraestruturas do mercado financeiro',
  'Anexo I, 5.': 'Saúde',
  'Anexo I, 6.': 'Água potável',
  'Anexo I, 7.': 'Águas residuais',
  'Anexo I, 8.': 'Infraestruturas digitais',
  'Anexo I, 9.': 'Gestão de serviços de TIC (entre empresas)',
  'Anexo I, 10.': 'Espaço',
  'Anexo II, 1.': 'Serviços postais e de estafeta',
  'Anexo II, 2.': 'Gestão de resíduos',
  'Anexo II, 3.': 'Produtos químicos',
  'Anexo II, 4.': 'Produtos alimentares',
  'Anexo II, 5.': 'Indústria transformadora',
  'Anexo II, 6.': 'Prestadores de serviços digitais',
  'Anexo II, 7.': 'Investigação',
}
