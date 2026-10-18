import { describe, expect, it } from 'vitest'
import { i18n } from '@/locales'
import {
  buildExplanationCallouts,
  buildExplanationSteps,
  describePointStatus,
  explanationStepText,
  summarizePoints
} from './timelineExplanationStory'

const formatters = {
  t: (key, params) => i18n.global.t(key, params),
  te: key => i18n.global.te(key),
  formatDuration: seconds => `${seconds}s`,
  formatDistance: meters => `${meters} m`,
  formatSpeed: kmh => `${kmh} km/h`,
  formatTime: value => value.slice(11, 16)
}

const point = (time, extra = {}) => ({
  timestamp: `2026-10-08T${time}:00Z`,
  latitude: 49.84,
  longitude: 24.03,
  part: 'EVENT',
  used: true,
  reasons: [],
  ...extra
})

const stay = {
  kind: 'STAY',
  startTime: '2026-10-08T17:00:00Z',
  endTime: '2026-10-08T18:02:00Z',
  radiusMeters: 50,
  settings: { staypointRadiusMeters: 50, staypointMinDurationMinutes: 7, staypointMaxAccuracyThreshold: 60 },
  explanation: {
    start: { code: 'ARRIVAL', params: { stopSeconds: 240, points: 2, stopSpeedKmh: 9 } },
    notes: [{ code: 'CONFIRMED', params: { radiusMeters: 50, minMinutes: 7 } }],
    end: { code: 'MOVED_AWAY', params: { distanceMeters: 177, radiusMeters: 50 } }
  },
  points: [
    point('17:00', { reasons: ['ARRIVAL'] }),
    point('17:02', { reasons: ['ARRIVAL'] }),
    point('17:08', { reasons: ['CONFIRMED'] }),
    point('17:30', { used: false, accuracyMeters: 180 }),
    point('18:03', { part: 'AFTER', reasons: ['MOVED_AWAY'], outsideRadius: true, distanceFromCentreMeters: 177 })
  ],
  filtered: { inEvent: 1, inContext: 0, thresholdMeters: 60 },
  totalEventPoints: 4
}

describe('buildExplanationSteps', () => {
  it('says when each decision happened, with the points and settings behind it', () => {
    const steps = buildExplanationSteps(stay)

    expect(steps.map(step => step.code)).toEqual(['ARRIVAL', 'CONFIRMED', 'MOVED_AWAY'])
    expect(steps[0].pointIndexes).toEqual([0, 1])
    expect(steps[2].pointIndexes).toEqual([4])
    expect(steps[2].params.time).toBe('2026-10-08T18:03:00Z')
    expect(steps.every(step => !step.notable)).toBe(true)

    const radius = steps[2].settings[0]
    expect(radius.key).toBe('staypointRadiusMeters')
    expect(radius.value).toBe(50)
    expect(radius.route).toEqual({
      path: '/app/timeline/preferences',
      query: { tab: 'staypoints', setting: 'staypointRadiusMeters' }
    })
  })

  it('reads in user terms with the time the stay ended', () => {
    const steps = buildExplanationSteps(stay)

    expect(explanationStepText(steps[0], 'stay', formatters)).toBe('You arrived at 17:00, when you stopped moving.')
    expect(explanationStepText(steps[1], 'stay', formatters)).toContain('from 17:08')
    expect(explanationStepText(steps[2], 'stay', formatters))
      .toBe('Ended at 18:03, when you were 177 m from the centre, more than the 50 m stay radius.')
  })

  it('has no sentence for codes it does not know', () => {
    const steps = buildExplanationSteps({
      ...stay,
      explanation: { start: { code: 'SOMETHING_NEW' } }
    })

    expect(steps.map(step => step.code)).toEqual(['SOMETHING_NEW'])
    expect(explanationStepText(steps[0], 'stay', formatters)).toBeNull()
  })
})

describe('notable explanations', () => {
  it('points out a continuity trip, a data gap of left-out points and settings changed since', () => {
    const trip = {
      kind: 'TRIP',
      startTime: '2026-10-08T12:00:00Z',
      endTime: '2026-10-08T12:30:00Z',
      explanation: { start: { code: 'CONTINUITY', params: { distanceMeters: 900 } } },
      points: [],
      settingsChanged: [{ setting: 'staypointRadiusMeters', builtWith: 80, current: 50 }]
    }
    const callouts = buildExplanationCallouts(trip)
    expect(callouts.map(callout => callout.type)).toEqual(['step', 'settingChanged'])
    expect(callouts[0].code).toBe('CONTINUITY')
    expect(callouts[1].setting.route.query.setting).toBe('staypointRadiusMeters')

    const gap = {
      kind: 'DATA_GAP',
      explanation: { start: { code: 'NO_POINTS', params: { gapSeconds: 7200, thresholdSeconds: 3600 } } },
      points: [],
      filtered: { inEvent: 13, inContext: 0, thresholdMeters: 60 }
    }
    expect(buildExplanationCallouts(gap)).toEqual([expect.objectContaining({
      type: 'gapFiltered',
      params: { count: 13, thresholdMeters: 60 }
    })])
  })
})

describe('points', () => {
  it('counts the points left out and the least accurate one', () => {
    expect(summarizePoints(stay)).toEqual({
      eventTotal: 4,
      eventUsed: 3,
      eventFiltered: 1,
      contextFiltered: 0,
      thresholdMeters: 60,
      worstAccuracyMeters: 180
    })
  })

  it('explains a left-out point and the point that ended the stay', () => {
    expect(describePointStatus(stay.points[3], stay).map(line => line.key))
      .toEqual(['timeline.explanationDialog.point.filtered'])
    expect(describePointStatus(stay.points[4], stay).map(line => line.key)).toEqual([
      'timeline.explanationDialog.point.after',
      'timeline.explanationDialog.point.outsideRadius',
      'timeline.explanationDialog.point.reason.MOVED_AWAY'
    ])
  })
})
