import { definePlugin } from '@alinha/plugin-sdk'
import { OrganizationPage } from './pages/OrganizationPage'

/**
 * Ponto de entrada do plugin no frontend. O shell lê este objeto e monta
 * as rotas e o menu — não há nada a registar à mão no shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 */
export default definePlugin({
  id: 'organization',
  name: 'Organization',
  routes: {
    client: [
      { path: 'organization', handle: { title: 'Organization' }, Component: OrganizationPage },
    ],
  },
  nav: [
    { label: 'Organization', path: 'organization', area: 'client' },
  ],
})
