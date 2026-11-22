/**
 * Sectors and subsectors of the Regime Jurídico da Cibersegurança (DL 125/2025, NIS2), with the same ids as the
 * backend (`internal/domain/Sectors.kt`), which validates them and derives the NIS2 annex.
 */
export interface Sector {
  id: string
  label: string
  subsectors: ReadonlyArray<{ id: string; label: string }>
}

export const SECTORS: readonly Sector[] = [
  { id: 'energia', label: 'Energia', subsectors: [
    { id: 'eletricidade', label: 'Eletricidade' },
    { id: 'aquecimento', label: 'Aquecimento e arrefecimento urbano' },
    { id: 'petroleo', label: 'Petróleo' },
    { id: 'gas', label: 'Gás' },
    { id: 'hidrogenio', label: 'Hidrogénio' },
  ] },
  { id: 'transportes', label: 'Transportes', subsectors: [
    { id: 'aereo', label: 'Transporte aéreo' },
    { id: 'ferroviario', label: 'Transporte ferroviário' },
    { id: 'maritimo', label: 'Transporte marítimo e fluvial' },
    { id: 'rodoviario', label: 'Transporte rodoviário' },
  ] },
  { id: 'bancario', label: 'Setor bancário', subsectors: [] },
  { id: 'financeiro', label: 'Infraestruturas do mercado financeiro', subsectors: [] },
  { id: 'saude', label: 'Saúde', subsectors: [] },
  { id: 'agua', label: 'Água potável', subsectors: [] },
  { id: 'aguas_residuais', label: 'Águas residuais', subsectors: [] },
  { id: 'infra_digitais', label: 'Infraestruturas digitais', subsectors: [] },
  { id: 'tic', label: 'Gestão de serviços de TIC (entre empresas)', subsectors: [] },
  { id: 'espaco', label: 'Espaço', subsectors: [] },
  { id: 'postais', label: 'Serviços postais e de estafeta', subsectors: [] },
  { id: 'residuos', label: 'Gestão de resíduos', subsectors: [] },
  { id: 'quimicos', label: 'Produção, fabrico e distribuição de produtos químicos', subsectors: [] },
  { id: 'alimentar', label: 'Produção, transformação e distribuição de produtos alimentares', subsectors: [] },
  { id: 'industria', label: 'Indústria transformadora', subsectors: [
    { id: 'dispositivos_medicos', label: 'Fabrico de dispositivos médicos' },
    { id: 'informatica', label: 'Fabrico de equipamentos informáticos, eletrónicos e óticos' },
    { id: 'eletrico', label: 'Fabrico de equipamento elétrico' },
    { id: 'maquinas', label: 'Fabrico de máquinas e equipamentos' },
    { id: 'automoveis', label: 'Fabrico de veículos automóveis, reboques e componentes' },
    { id: 'outro_transporte', label: 'Fabrico de outro equipamento de transporte' },
  ] },
  { id: 'digitais', label: 'Prestadores de serviços digitais', subsectors: [] },
  { id: 'investigacao', label: 'Investigação', subsectors: [] },
  { id: 'outro', label: 'Outro setor (não está nesta lista)', subsectors: [] },
]

export const sectorById = (id: string): Sector | undefined => SECTORS.find((s) => s.id === id)

/** "Indústria transformadora · Fabrico de veículos automóveis…", or "—" for an unknown sector id. */
export function sectorLabel(sectorId: string, subsectorId?: string | null): string {
  const sector = sectorById(sectorId)
  if (!sector) return '—'
  const subsector = sector.subsectors.find((s) => s.id === subsectorId)
  return subsector ? `${sector.label} · ${subsector.label}` : sector.label
}
