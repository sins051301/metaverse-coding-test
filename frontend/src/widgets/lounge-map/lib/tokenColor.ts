const cache = new Map<string, string>()

export function tokenColor(token: string): string {
  const cached = cache.get(token)
  if (cached !== undefined) {
    return cached
  }
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(token)
    .trim()
  cache.set(token, value)
  return value
}
