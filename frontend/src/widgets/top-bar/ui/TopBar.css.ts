import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const topBar = style({
  height: '56px',
  flex: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.md,
  paddingInline: vars.space.lg,
  borderBottom: `${vars.rule.bar} solid ${vars.color.rule}`,
})

export const wordmark = style({
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.xl,
  letterSpacing: vars.tracking.display,
})

export const spacer = style({
  flex: '1',
})

export const me = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.xs,
})

export const nickname = style({
  padding: `${vars.space.xxs} ${vars.space.sm}`,
  borderRadius: vars.radius.bubble,
  background: vars.color.paper2,
  fontWeight: '700',
})

export const muted = style({
  color: vars.color.muted,
})

export const figure = style({
  marginRight: vars.space.xxxs,
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.md,
  color: vars.color.ink,
})
