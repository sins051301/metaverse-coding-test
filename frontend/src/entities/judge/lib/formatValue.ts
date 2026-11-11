// 프로그래머스처럼 배열은 [1, 2, 3], 문자열은 "abc"로 보여 준다
export function formatValue(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(formatValue).join(', ')}]`
  if (typeof value === 'string') return `"${value}"`
  return String(value)
}
