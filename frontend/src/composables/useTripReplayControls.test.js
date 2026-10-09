import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useTripReplayControls } from './useTripReplayControls'

const trip = { id: 7, type: 'trip', tripDuration: 120 }

const replayPayload = {
  tripKey: '7',
  points: [
    { latitude: 50.45, longitude: 30.52, timestamp: '2026-01-01T10:00:00Z' },
    { latitude: 50.46, longitude: 30.53, timestamp: '2026-01-01T10:02:00Z' }
  ]
}

const selectTrip = async (replay, activeTrip) => {
  activeTrip.value = trip
  await nextTick()
  replay.handleHighlightedTripReplayData(replayPayload)
  await nextTick()
}

describe('useTripReplayControls', () => {
  beforeEach(() => {
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const setup = async (autoShowControls) => {
    const activeTrip = ref(null)
    let replay
    const wrapper = mount(defineComponent({
      setup() {
        replay = useTripReplayControls({ enabled: ref(true), activeTrip, autoShowControls })
        return () => h('div')
      }
    }))
    await selectTrip(replay, activeTrip)
    return { wrapper, replay }
  }

  it('opens auto-shown controls paused', async () => {
    const { wrapper, replay } = await setup(true)

    expect(replay.showTripReplayBar.value).toBe(true)
    expect(replay.isReplayPlaying.value).toBe(false)
    wrapper.unmount()
  })

  it('starts playback when the Replay button restores hidden controls', async () => {
    const { wrapper, replay } = await setup(false)

    expect(replay.showTripReplayBar.value).toBe(false)
    expect(replay.showTripReplayRestoreButton.value).toBe(true)

    replay.restoreTripReplayControls()

    expect(replay.showTripReplayBar.value).toBe(true)
    expect(replay.isReplayPlaying.value).toBe(true)
    wrapper.unmount()
  })

  it('starts playback again after the controls were dismissed', async () => {
    const { wrapper, replay } = await setup(true)

    replay.dismissTripReplayControls()
    expect(replay.isReplayPlaying.value).toBe(false)

    replay.restoreTripReplayControls()

    expect(replay.isReplayPlaying.value).toBe(true)
    wrapper.unmount()
  })
})
