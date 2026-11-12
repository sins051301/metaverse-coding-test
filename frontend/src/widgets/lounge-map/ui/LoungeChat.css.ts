import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

const base = {
  display: 'flex',
  flexDirection: 'column',
  minHeight: '0',
  fontSize: vars.text.xs,
} as const

export const overlay = style({
  ...base,
  position: 'absolute',
  left: vars.space.lg,
  bottom: '72px',
  width: '300px',
  height: '190px',
  padding: vars.space.xs,
  borderRadius: vars.radius.panel,
  background: `color-mix(in oklab, ${vars.color.paper3} 85%, transparent)`,
})

export const panel = style({
  ...base,
  flex: '1',
  padding: `0 ${vars.space.xs} ${vars.space.xs}`,
})

export const log = style({
  flex: '1',
  minHeight: '0',
  overflowY: 'auto',
  margin: 0,
  padding: `0 ${vars.space.xxs}`,
  listStyle: 'none',
  display: 'grid',
  alignContent: 'end',
  gap: vars.space.xxs,
})

export const empty = style({
  color: vars.color.muted,
})

export const line = style({
  overflowWrap: 'anywhere',
})

export const who = style({
  marginRight: vars.space.xs,
  color: vars.color.muted,
  fontWeight: '700',
})

export const mine = style({
  marginRight: vars.space.xs,
  color: vars.color.accent,
  fontWeight: '700',
})

export const text = style({
  color: vars.color.ink,
})

export const notice = style({
  padding: `${vars.space.xxs} ${vars.space.xs}`,
  color: vars.color.bad,
})

export const form = style({
  flex: 'none',
  marginTop: vars.space.xs,
})

export const input = style({
  width: '100%',
  padding: `${vars.space.xxs} ${vars.space.sm}`,
  border: 0,
  borderRadius: vars.radius.pill,
  background: vars.color.paper2,
  color: vars.color.ink,
})
