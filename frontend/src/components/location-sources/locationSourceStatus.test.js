import { describe, expect, it } from 'vitest'
import { buildLocationSourceStatus } from './locationSourceStatus'

const NOW = Date.parse('2026-10-14T12:00:00Z')

describe('buildLocationSourceStatus', () => {
  it('treats a source without ingest status as never received', () => {
    const status = buildLocationSourceStatus({ id: 'a', ingestStatus: null }, { now: NOW })

    expect(status).toEqual({
      severity: 'secondary',
      lastReceivedAt: null,
      newestPointAt: null,
      rejection: null,
      authProblem: null
    })
  })

  it('reports a recently active source as healthy', () => {
    const status = buildLocationSourceStatus({
      ingestStatus: {
        lastReceivedAt: '2026-10-14T11:55:00Z',
        lastSavedAt: '2026-10-14T11:55:00Z',
        lastSavedPointTimestamp: '2026-10-14T11:54:58Z'
      }
    }, { now: NOW })

    expect(status.severity).toBe('success')
    expect(status.lastReceivedAt).toBe('2026-10-14T11:55:00Z')
    expect(status.newestPointAt).toBeNull()
  })

  it('flags a source that has been silent for more than a day', () => {
    const status = buildLocationSourceStatus({
      ingestStatus: { lastReceivedAt: '2026-10-13T10:00:00Z' }
    }, { now: NOW })

    expect(status.severity).toBe('warn')
  })

  it('shows the newest point time when stored points lag behind (queued upload)', () => {
    const status = buildLocationSourceStatus({
      ingestStatus: {
        lastReceivedAt: '2026-10-14T11:00:00Z',
        lastSavedAt: '2026-10-14T11:00:00Z',
        lastSavedPointTimestamp: '2026-10-14T08:30:00Z'
      }
    }, { now: NOW })

    expect(status.newestPointAt).toBe('2026-10-14T08:30:00Z')
  })

  it('maps the latest skipped point to a message key with its params', () => {
    const status = buildLocationSourceStatus({
      ingestStatus: {
        lastReceivedAt: '2026-10-14T11:00:00Z',
        lastRejectedAt: '2026-10-14T11:00:00Z',
        lastRejectionCode: 'ACCURACY_EXCEEDED',
        lastRejectionParams: { accuracy: 120.5, threshold: 50 }
      }
    }, { now: NOW })

    expect(status.rejection).toEqual({
      at: '2026-10-14T11:00:00Z',
      messageKey: 'locationSources.status.rejection.ACCURACY_EXCEEDED',
      params: { accuracy: 120.5, threshold: 50 }
    })
  })

  it('falls back to a generic message for unknown codes', () => {
    const status = buildLocationSourceStatus({
      ingestStatus: {
        lastRejectedAt: '2026-10-14T11:00:00Z',
        lastRejectionCode: 'SOMETHING_NEW',
        lastAuthFailureAt: '2026-10-14T11:00:00Z',
        lastAuthFailureCode: 'SOMETHING_ELSE'
      }
    }, { now: NOW })

    expect(status.rejection.messageKey).toBe('locationSources.status.rejection.UNKNOWN')
    expect(status.rejection.params).toEqual({})
    expect(status.authProblem.messageKey).toBe('locationSources.status.authFailure.UNKNOWN')
  })

  it('raises an auth problem only while no request has been accepted since', () => {
    const failing = buildLocationSourceStatus({
      ingestStatus: {
        lastReceivedAt: '2026-10-14T10:00:00Z',
        lastAuthFailureAt: '2026-10-14T11:30:00Z',
        lastAuthFailureCode: 'INVALID_CREDENTIALS'
      }
    }, { now: NOW })
    expect(failing.severity).toBe('danger')
    expect(failing.authProblem).toEqual({
      at: '2026-10-14T11:30:00Z',
      messageKey: 'locationSources.status.authFailure.INVALID_CREDENTIALS'
    })

    const recovered = buildLocationSourceStatus({
      ingestStatus: {
        lastReceivedAt: '2026-10-14T11:45:00Z',
        lastAuthFailureAt: '2026-10-14T11:30:00Z',
        lastAuthFailureCode: 'INVALID_CREDENTIALS'
      }
    }, { now: NOW })
    expect(recovered.severity).toBe('success')
    expect(recovered.authProblem).toBeNull()
  })
})
