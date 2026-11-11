// 서버는 UTC(ISO 8601)로 보낸다. 브라우저 시간대로 바꿔 2026-07-12 17:18:08 형식으로 보여 준다
export function formatDateTime(isoText: string) {
  const date = new Date(isoText)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
