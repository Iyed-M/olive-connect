import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { CardTitle } from './card'

describe('CardTitle', () => {
  it('renders a semantic heading for accessible page and card titles', () => {
    const markup = renderToStaticMarkup(<CardTitle>Request sent</CardTitle>)

    expect(markup).toMatch(/<h3[^>]*data-slot="card-title"[^>]*>Request sent<\/h3>/)
  })
})
