import { globalStyle, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const recentSolves = style({
  position: 'absolute',
  top: vars.space.sm,
  right: vars.space.sm,
  width: '200px',
  padding: `${vars.space.xs} ${vars.space.sm}`,
  borderRadius: vars.radius.panel,
  background: `color-mix(in oklab, ${vars.color.paper2} 85%, transparent)`,
  fontSize: vars.text.xs,
  pointerEvents: 'none',
})

export const title = style({
  marginBottom: vars.space.xxs,
  fontSize: vars.text.sm,
})

export const list = style({
  margin: '0',
  padding: '0',
  listStyle: 'none',
})

export const solve = style({
  display: 'grid',
  gridTemplateColumns: 'minmax(0, 1fr) auto',
  gap: `0 ${vars.space.xs}`,
  paddingBlock: vars.space.xxs,
  borderTop: `${vars.rule.hair} solid ${vars.color.rule}`,
})

export const who = style({
  minWidth: '0',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
  fontWeight: '700',
})

export const what = style({
  gridColumn: '1',
  minWidth: '0',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
  color: vars.color.muted,
})

export const when = style({
  gridColumn: '2',
  gridRow: '1 / span 2',
  alignSelf: 'center',
  color: vars.color.muted,
})

export const neighbors = style({
  flex: '1',
  minHeight: '0',
  overflowY: 'auto',
  padding: `${vars.space.xs} 0`,
})

export const subtitle = style({
  padding: `${vars.space.xxs} ${vars.space.md}`,
  fontSize: vars.text.sm,
})

export const neighbor = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.xs,
  padding: `${vars.space.xxs} ${vars.space.md}`,
})

export const doing = style({
  flex: 'none',
  color: vars.color.muted,
  fontSize: vars.text.xs,
})

export const empty = style({
  padding: vars.space.md,
  color: vars.color.muted,
  fontSize: vars.text.sm,
})

globalStyle(`${neighbor} ${who}`, {
  flex: '1',
})
