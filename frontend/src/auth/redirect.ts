// `?redirect=` comes from the URL, so anyone can put anything in it.
// Only paths inside this app are accepted ("/x", never "//evil.com" or "http://...").
export function safeRedirect(value: unknown): string | null {
  if (typeof value !== 'string') return null
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null
  return value
}
