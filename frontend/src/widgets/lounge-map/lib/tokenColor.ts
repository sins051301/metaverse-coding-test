const cache = new Map<string, string>()

export function tokenColor(variable: string): string {
  const cached = cache.get(variable)
  if (cached !== undefined) {
    return cached
  }
  const name = /^var\((--[\w-]+)\)$/.exec(variable)?.[1] ?? variable
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim()
  cache.set(variable, value)
  return value
}
