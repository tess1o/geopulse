import { describe, expect, it } from 'vitest'
import { describeMapMatchingState } from './mapMatchingDetails'

const valhallaHttpError = (status, body) =>
  `Valhalla trace_route failed with HTTP ${status}: ${body}`

describe('describeMapMatchingState', () => {
  it('confirms a refined route', () => {
    const result = describeMapMatchingState({ status: 'COMPLETED', targetId: 11 })

    expect(result.title).toBe('This route was refined with map matching.')
    expect(result.detail).toContain('roads and paths')
  })

  it('describes trips that are still waiting', () => {
    expect(describeMapMatchingState({ status: 'QUEUED' }).title).toBe('This trip is queued for map matching.')
    expect(describeMapMatchingState({ status: 'PROCESSING' }).title).toBe('This trip is being matched right now.')
  })

  it('explains an unmatchable trace reported as Valhalla error 443', () => {
    const result = describeMapMatchingState({
      status: 'FAILED',
      error: valhallaHttpError(400, '{"error_code":443,"error":"Exact route match algorithm failed to find path"}')
    })

    expect(result.title).toBe('The routing engine could not find a road or path for this trip.')
    expect(result.detail).toContain('error_code')
  })

  it('distinguishes other rejected traces from missing map data', () => {
    expect(describeMapMatchingState({ status: 'FAILED', error: valhallaHttpError(400, '{}') }).title)
      .toBe('The routing engine rejected this trip\'s GPS trace.')
    expect(describeMapMatchingState({ status: 'FAILED', error: valhallaHttpError(404, 'not found') }).title)
      .toBe('The routing engine has no map data for this area.')
  })

  it('treats server-side failures as retryable', () => {
    expect(describeMapMatchingState({ status: 'FAILED', error: valhallaHttpError(503, 'unavailable') }).title)
      .toBe('The routing engine was temporarily unavailable. This trip will be retried.')
    expect(describeMapMatchingState({ status: 'FAILED', error: valhallaHttpError(429, 'slow down') }).title)
      .toBe('The routing engine was temporarily unavailable. This trip will be retried.')
  })

  it('passes through skip reasons GeoPulse already writes for users', () => {
    const result = describeMapMatchingState({
      status: 'SKIPPED',
      targetId: null,
      error: { key: 'mapMatching.skipped.insufficientPoints', fallback: 'Trip has fewer than two eligible GPS points' }
    })

    expect(result.title).toBe('Trip has fewer than two eligible GPS points')
    expect(result.detail).toBeNull()
  })

  it('falls back to a generic explanation without a reason', () => {
    expect(describeMapMatchingState({ status: 'FAILED', error: null }).title)
      .toBe('This trip could not be map-matched.')
    expect(describeMapMatchingState(null).title).toBe('This trip could not be map-matched.')
  })
})
