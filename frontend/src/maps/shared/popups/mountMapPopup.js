import { createApp, h, reactive } from 'vue'

const DEFAULT_STOP_EVENTS = [
  'click',
  'dblclick',
  'mousedown',
  'touchstart',
  'wheel'
]

const stopEventPropagation = (event) => {
  event.stopPropagation()
}

export const mountMapPopup = (Component, initialProps = {}, options = {}) => {
  const element = document.createElement('div')
  const props = reactive({ ...initialProps })
  let app = null
  let mounted = false
  const stopEvents = options.stopPropagation === false
    ? []
    : (Array.isArray(options.stopEvents) ? options.stopEvents : DEFAULT_STOP_EVENTS)

  if (options.className) {
    element.className = options.className
  }

  stopEvents.forEach((eventName) => {
    element.addEventListener(eventName, stopEventPropagation)
  })

  app = createApp({
    name: 'MountedMapPopup',
    setup() {
      return () => h(Component, props)
    }
  })

  app.mount(element)
  mounted = true

  const unmount = () => {
    if (!mounted) return
    mounted = false
    stopEvents.forEach((eventName) => {
      element.removeEventListener(eventName, stopEventPropagation)
    })
    app?.unmount()
    app = null
  }

  return {
    element,
    updateProps(nextProps = {}) {
      Object.keys(props).forEach((key) => {
        if (!Object.prototype.hasOwnProperty.call(nextProps, key)) {
          delete props[key]
        }
      })
      Object.assign(props, nextProps)
    },
    unmount
  }
}
