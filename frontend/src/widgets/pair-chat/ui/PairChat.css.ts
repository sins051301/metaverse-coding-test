import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const chat = style({
  width: '260px',
  flex: 'none',
  display: 'flex',
  flexDirection: 'column',
  minHeight: '0',
  overflow: 'hidden',
  borderRadius: vars.radius.panel,
  background: vars.color.paper2,
})

export const title = style({
  flex: 'none',
  padding: `${vars.space.md} ${vars.space.md} ${vars.space.xs}`,
  fontSize: vars.text.md,
})

export const log = style({
  flex: '1',
  minHeight: '0',
  overflowY: 'auto',
  display: 'grid',
  alignContent: 'start',
  gap: vars.space.sm,
  padding: `${vars.space.xs} ${vars.space.sm}`,
})

export const empty = style({
  color: vars.color.muted,
  fontSize: vars.text.xs,
})

const bubble = {
  padding: `${vars.space.xs} ${vars.space.sm}`,
  overflowWrap: 'anywhere',
} as const

export const message = style({
  ...bubble,
  borderRadius: `${vars.radius.bubble} ${vars.radius.bubble} ${vars.radius.bubble} 4px`,
  background: vars.color.paper3,
})

export const mine = style({
  ...bubble,
  borderRadius: `${vars.radius.bubble} ${vars.radius.bubble} 4px ${vars.radius.bubble}`,
  background: vars.color.paper,
})

export const who = style({
  color: vars.color.muted,
  fontSize: vars.text.xs,
  fontWeight: '700',
})

export const text = style({
  color: vars.color.ink,
  whiteSpace: 'pre-wrap',
})

export const form = style({
  flex: 'none',
  padding: vars.space.sm,
})

export const input = style({
  width: '100%',
  padding: `${vars.space.xs} ${vars.space.md}`,
  border: 0,
  borderRadius: vars.radius.pill,
  background: vars.color.paper3,
  color: vars.color.ink,
})
