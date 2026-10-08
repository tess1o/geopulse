// PrimeVue's AutoComplete closes its suggestion list on Escape but lets the keydown bubble, and Dialog closes on
// any Escape that reaches document. Inside a dialog one press would close both. While the list is open, stop the
// event at the input: the first Escape closes the suggestions, the next one closes the dialog as usual.
export const stopEscapeWhileSuggestionsOpen = (event) => {
  if (event.key === 'Escape' && event.currentTarget?.getAttribute?.('aria-expanded') === 'true') {
    event.stopPropagation()
  }
}

// Global PrimeVue pass-through for AutoComplete: `pcInputText` is the single-value input, `input` the one used with
// `multiple`.
export const autocompleteEscapePassThrough = {
  pcInputText: {
    root: {
      onKeydown: stopEscapeWhileSuggestionsOpen
    }
  },
  input: {
    onKeydown: stopEscapeWhileSuggestionsOpen
  }
}
