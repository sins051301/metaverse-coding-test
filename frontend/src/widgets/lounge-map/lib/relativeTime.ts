const formatter = new Intl.RelativeTimeFormat('ko', { numeric: 'auto' })

export function formatRelativeTime(isoTime: string, now: number) {
  const seconds = Math.round((Date.parse(isoTime) - now) / 1000)
  if (seconds > -60) {
    return '방금 전'
  }
  const minutes = Math.round(seconds / 60)
  if (minutes > -60) {
    return formatter.format(minutes, 'minute')
  }
  return formatter.format(Math.round(minutes / 60), 'hour')
}
