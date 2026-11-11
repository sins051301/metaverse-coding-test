import { globalStyle, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const page = style({
  flex: '1',
  minWidth: '0',
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space.sm,
})

export const banner = style({
  flex: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.sm,
  padding: `${vars.space.xs} ${vars.space.md}`,
  borderRadius: vars.radius.pill,
  background: vars.color.paper3,
})

export const owner = style({
  color: vars.color.accent,
})

export const spacer = style({
  flex: '1',
})

export const body = style({
  flex: '1',
  minHeight: '0',
  display: 'flex',
  gap: vars.space.sm,
})

export const statement = style({
  width: '380px',
  flex: 'none',
  minHeight: '0',
  overflow: 'hidden',
})

export const editorCard = style({
  flex: '1',
  minWidth: '0',
  display: 'flex',
  flexDirection: 'column',
  overflow: 'hidden',
  border: `${vars.rule.hair} solid ${vars.color.cardRule}`,
  borderRadius: vars.radius.panel,
  background: vars.color.card,
  color: vars.color.cardInk,
  boxShadow: vars.shadow.card,
})

export const editorHead = style({
  height: '44px',
  flex: 'none',
  display: 'flex',
  alignItems: 'center',
  paddingInline: `${vars.space.lg} ${vars.space.md}`,
  borderBottom: `${vars.rule.hair} solid ${vars.color.cardRule}`,
  fontWeight: '700',
})

export const editorBody = style({
  flex: '1',
  minHeight: '0',
})

globalStyle('.yRemoteSelection', {
  background: `color-mix(in oklab, ${vars.color.accent} 40%, transparent)`,
})

globalStyle('.yRemoteSelectionHead', {
  position: 'absolute',
  height: '100%',
  borderLeft: `2px solid ${vars.color.accent2}`,
})
