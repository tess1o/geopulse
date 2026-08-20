import { getCurrentInstance, onBeforeUnmount, unref } from 'vue'

const activeMenusByGroup = new Map()

export function useExclusiveContextMenu(menuRef, group = 'default') {
  const menuId = Symbol(group)

  const getMenu = () => unref(menuRef)

  const hide = () => {
    getMenu()?.hide?.()

    const activeMenu = activeMenusByGroup.get(group)
    if (activeMenu?.id === menuId) {
      activeMenusByGroup.delete(group)
    }
  }

  const show = (event) => {
    const activeMenu = activeMenusByGroup.get(group)
    if (activeMenu?.id !== menuId) {
      activeMenu?.hide?.()
    }

    getMenu()?.show?.(event)
    activeMenusByGroup.set(group, { id: menuId, hide })
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(hide)
  }

  return {
    show,
    hide
  }
}
