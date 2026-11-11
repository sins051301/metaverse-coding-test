const cache = new Map<string, string>()

/**
 * CSS 색 토큰(--map-floor-a)의 실제 값을 읽는다. 캔버스 fillStyle 은 oklch(...) 문자열을 그대로 받으므로 변환하지 않는다.
 * 지도와 IDE 가 같은 tokens.css 에서 색을 가져와 한 공간처럼 보이게 하려는 것이다.
 */
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
