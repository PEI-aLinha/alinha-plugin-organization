import { definePlugin } from '@alinha/plugin-sdk'
import { OrganizationPage } from './pages/OrganizationPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /portfolio/:org/<path>
 * User-visible text (title, label) is in European Portuguese, plain language
 * (UI requirement in the technical report). Paths are in English.
 */
export default definePlugin({
  id: 'organization',
  name: 'Organization',
  routes: {
    client: [
      { path: 'organization', handle: { title: 'Organização' }, Component: OrganizationPage },
    ],
  },
  nav: [
    { label: 'Organização', path: 'organization', area: 'client' },
  ],
})
