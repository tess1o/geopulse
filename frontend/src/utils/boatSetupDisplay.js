import { formatMessageDescriptor } from '@/utils/messageDescriptor'
import { t, te } from '@/locales'

const NS = 'timeline.preferences.tripClassification.boat.setup'

export const formatBoatSetupPhase = (phase) => (
  te(`${NS}.phases.${phase}`) ? t(`${NS}.phases.${phase}`) : t(`${NS}.phases.fallback`)
)

export const formatBoatSetupError = (error) => (
  formatMessageDescriptor(error) || t(`${NS}.errorFallback`)
)
