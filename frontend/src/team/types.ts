import type { components } from '@alinha/api-client'

export type Member = components['schemas']['Member']
export type Invitation = components['schemas']['Invitation']
export type IssuedInvitation = components['schemas']['IssuedInvitation']

export const TEAM_KEY = ['organization', 'team'] as const

/** The link the invitee opens. The secret goes in the fragment, so it never reaches a server log (ADR 0007). */
export function invitationLink(token: string, origin: string = window.location.origin): string {
  return `${origin}/onboarding/invitation#${encodeURIComponent(token)}`
}

export const ROLE_LABEL: Record<Member['role'], string> = {
  responsible: 'Responsável',
  analyst: 'Analista',
}
