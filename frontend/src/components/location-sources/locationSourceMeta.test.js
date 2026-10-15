import { describe, expect, it } from 'vitest'
import { getLocationSourceIdentifier } from './locationSourceMeta'

describe('getLocationSourceIdentifier', () => {
  it('names the API token for Home Assistant sources connected to one', () => {
    expect(getLocationSourceIdentifier({ type: 'HOME_ASSISTANT', apiTokenId: 'id', apiTokenName: 'Home Assistant' }))
      .toBe('API token: Home Assistant')
  })

  it('keeps showing the source token prefix for other Home Assistant sources', () => {
    expect(getLocationSourceIdentifier({ type: 'HOME_ASSISTANT', token: 'abcdefgh12345' }))
      .toBe('Token: abcdefgh...')
  })
})
