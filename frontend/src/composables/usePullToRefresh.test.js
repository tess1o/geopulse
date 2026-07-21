import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { usePullToRefresh } from './usePullToRefresh'

const createTouchEvent = ({
  x = 0,
  y = 0,
  target = createTarget(false),
  cancelable = true,
  touches = [{ clientX: x, clientY: y }]
} = {}) => ({
  touches,
  changedTouches: touches,
  target,
  cancelable,
  preventDefault: vi.fn()
})

const createTarget = (ignored) => ({
  closest: vi.fn(() => (ignored ? {} : null))
})

const createPullToRefresh = (overrides = {}) => usePullToRefresh({
  isStandalonePwa: () => true,
  isTouchPrimary: () => true,
  getScrollTop: () => 0,
  onRefresh: vi.fn().mockResolvedValue(undefined),
  ...overrides
})

const pullPastThreshold = (pullToRefresh, target = createTarget(false)) => {
  const start = createTouchEvent({ y: 10, target })
  const move = createTouchEvent({ y: 92, target })

  pullToRefresh.pullRefreshBindings.onTouchstart(start)
  pullToRefresh.pullRefreshBindings.onTouchmove(move)
  pullToRefresh.pullRefreshBindings.onTouchend(createTouchEvent({ y: 92, target }))

  return { start, move }
}

const dispatchDomTouchEvent = (target, type, { x = 0, y = 0 } = {}) => {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'touches', {
    configurable: true,
    value: type === 'touchend' || type === 'touchcancel' ? [] : [{ clientX: x, clientY: y }]
  })
  Object.defineProperty(event, 'changedTouches', {
    configurable: true,
    value: [{ clientX: x, clientY: y }]
  })
  target.dispatchEvent(event)
  return event
}

describe('usePullToRefresh', () => {
  it('does not activate outside standalone touch mode', () => {
    const onRefresh = vi.fn()
    const pullToRefresh = createPullToRefresh({
      isStandalonePwa: () => false,
      onRefresh
    })

    const { move } = pullPastThreshold(pullToRefresh)

    expect(onRefresh).not.toHaveBeenCalled()
    expect(move.preventDefault).not.toHaveBeenCalled()
    expect(pullToRefresh.pullState.value).toBe('idle')
  })

  it('does not start when the page is already scrolled', () => {
    const onRefresh = vi.fn()
    const pullToRefresh = createPullToRefresh({
      getScrollTop: () => 12,
      onRefresh
    })

    pullPastThreshold(pullToRefresh)

    expect(onRefresh).not.toHaveBeenCalled()
    expect(pullToRefresh.pullDistance.value).toBe(0)
  })

  it('ignores pulls that begin from interactive targets', () => {
    const onRefresh = vi.fn()
    const pullToRefresh = createPullToRefresh({ onRefresh })

    pullPastThreshold(pullToRefresh, createTarget(true))

    expect(onRefresh).not.toHaveBeenCalled()
    expect(pullToRefresh.pullState.value).toBe('idle')
  })

  it('snaps back without refreshing when released below the threshold', () => {
    const onRefresh = vi.fn()
    const pullToRefresh = createPullToRefresh({ onRefresh })
    const start = createTouchEvent({ y: 0 })
    const move = createTouchEvent({ y: 48 })

    pullToRefresh.pullRefreshBindings.onTouchstart(start)
    pullToRefresh.pullRefreshBindings.onTouchmove(move)
    pullToRefresh.pullRefreshBindings.onTouchend(createTouchEvent({ y: 48 }))

    expect(move.preventDefault).toHaveBeenCalled()
    expect(onRefresh).not.toHaveBeenCalled()
    expect(pullToRefresh.pullDistance.value).toBe(0)
    expect(pullToRefresh.pullState.value).toBe('idle')
  })

  it('refreshes once when released past the threshold', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    const pullToRefresh = createPullToRefresh({ onRefresh })
    const { move } = pullPastThreshold(pullToRefresh)

    expect(move.preventDefault).toHaveBeenCalled()
    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(pullToRefresh.pullState.value).toBe('refreshing')

    await Promise.resolve()

    expect(pullToRefresh.pullState.value).toBe('idle')
    expect(pullToRefresh.pullDistance.value).toBe(0)
  })

  it('does not start a duplicate refresh while one is in flight', () => {
    let resolveRefresh
    const onRefresh = vi.fn(() => new Promise(resolve => {
      resolveRefresh = resolve
    }))
    const pullToRefresh = createPullToRefresh({ onRefresh })

    pullPastThreshold(pullToRefresh)
    pullPastThreshold(pullToRefresh)

    expect(onRefresh).toHaveBeenCalledTimes(1)

    resolveRefresh()
  })

  it('can refresh from document-level pulls outside the component tree', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined)
    const Component = defineComponent({
      setup() {
        usePullToRefresh({
          listenOnDocument: true,
          isStandalonePwa: () => true,
          isTouchPrimary: () => true,
          getScrollTop: () => 0,
          onRefresh
        })
        return () => null
      }
    })
    const navbarSurface = document.createElement('div')
    document.body.appendChild(navbarSurface)
    const wrapper = mount(Component)

    dispatchDomTouchEvent(navbarSurface, 'touchstart', { y: 4 })
    const move = dispatchDomTouchEvent(navbarSurface, 'touchmove', { y: 86 })
    dispatchDomTouchEvent(navbarSurface, 'touchend', { y: 86 })

    expect(move.defaultPrevented).toBe(true)
    expect(onRefresh).toHaveBeenCalledTimes(1)

    await Promise.resolve()

    wrapper.unmount()
    navbarSurface.remove()
  })
})
