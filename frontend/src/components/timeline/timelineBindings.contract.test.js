import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import DataGapCard from './DataGapCard.vue'
import OvernightDataGapCard from './OvernightDataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import OvernightTripCard from './OvernightTripCard.vue'
import StayCard from './StayCard.vue'
import TimelineContainer from './TimelineContainer.vue'
import TripCard from './TripCard.vue'
import TimelineEventExplanationDialog from '../dialogs/TimelineEventExplanationDialog.vue'
import TripClassificationDialog from '../dialogs/TripClassificationDialog.vue'

// A prop renamed on one side of a parent/child boundary is invisible to unit tests - the child simply
// receives null - so assert the bindings against the declared props instead.

const sourceOf = (relativePath) => readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')

const boundPropNamesIn = (source, tagName) => {
  const start = source.indexOf(`<${tagName}`)
  if (start === -1) return []
  const tag = source.slice(start, source.indexOf('/>', start))
  return [...tag.matchAll(/:([a-zA-Z][a-zA-Z0-9-]*)=/g)]
    .map(match => match[1].replace(/-([a-z])/g, (_, letter) => letter.toUpperCase()))
}

const undeclaredProps = (boundProps, component) => {
  const declaredProps = Object.keys(component.props || {})
  return boundProps.filter(prop => !declaredProps.includes(prop))
}

describe('timeline prop bindings', () => {
  it('binds only props TimelineContainer declares', () => {
    const bound = boundPropNamesIn(sourceOf('../../views/app/TimelinePage.vue'), 'TimelineContainer')

    expect(bound.length).toBeGreaterThan(0)
    expect(undeclaredProps(bound, TimelineContainer)).toEqual([])
  })

  it('binds only props the trip cards declare', () => {
    const container = sourceOf('./TimelineContainer.vue')

    expect(undeclaredProps(boundPropNamesIn(container, 'TripCard'), TripCard)).toEqual([])
    expect(undeclaredProps(boundPropNamesIn(container, 'OvernightTripCard'), OvernightTripCard)).toEqual([])
  })

  it('lets every card open the explanation dialog, and binds only props the cards and dialogs declare', () => {
    const container = sourceOf('./TimelineContainer.vue')
    const components = {
      StayCard, OvernightStayCard, TripCard, OvernightTripCard, DataGapCard, OvernightDataGapCard,
      TimelineEventExplanationDialog, TripClassificationDialog
    }

    for (const [tagName, component] of Object.entries(components)) {
      expect(undeclaredProps(boundPropNamesIn(container, tagName), component), tagName).toEqual([])
    }
    expect(container.match(/@show-explanation="handleShowExplanation"/g)).toHaveLength(6)
    expect(container.match(/:allow-explanation="!isPublicView"/g)).toHaveLength(6)
  })

  it('passes map matching state from the page down to the cards', () => {
    expect(sourceOf('../../views/app/TimelinePage.vue')).toContain(':map-matching-by-trip-id=')
    expect(sourceOf('./TimelineContainer.vue')).toContain(':map-matching-info=')
  })
})
