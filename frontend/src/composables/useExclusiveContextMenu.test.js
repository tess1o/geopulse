import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useExclusiveContextMenu } from './useExclusiveContextMenu'

const createMenu = () => ({
  show: vi.fn(),
  hide: vi.fn()
})

describe('useExclusiveContextMenu', () => {
  it('hides the previously opened menu in the same group', () => {
    const firstMenu = createMenu()
    const secondMenu = createMenu()
    const first = useExclusiveContextMenu(ref(firstMenu), 'timeline-card-test-1')
    const second = useExclusiveContextMenu(ref(secondMenu), 'timeline-card-test-1')
    const firstEvent = { pageX: 10, pageY: 20 }
    const secondEvent = { pageX: 30, pageY: 40 }

    first.show(firstEvent)
    second.show(secondEvent)

    expect(firstMenu.show).toHaveBeenCalledWith(firstEvent)
    expect(firstMenu.hide).toHaveBeenCalledTimes(1)
    expect(secondMenu.show).toHaveBeenCalledWith(secondEvent)
  })

  it('does not hide the same menu before showing it again', () => {
    const menu = createMenu()
    const contextMenu = useExclusiveContextMenu(ref(menu), 'timeline-card-test-2')
    const firstEvent = { pageX: 10, pageY: 20 }
    const secondEvent = { pageX: 30, pageY: 40 }

    contextMenu.show(firstEvent)
    contextMenu.show(secondEvent)

    expect(menu.hide).not.toHaveBeenCalled()
    expect(menu.show).toHaveBeenNthCalledWith(1, firstEvent)
    expect(menu.show).toHaveBeenNthCalledWith(2, secondEvent)
  })
})
