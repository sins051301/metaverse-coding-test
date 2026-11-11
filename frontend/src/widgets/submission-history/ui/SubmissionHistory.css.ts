import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const panel = style({
  height: '100%',
  overflowY: 'auto',
  background: vars.color.paper2,
  borderRadius: vars.radius.panel,
  color: vars.color.ink,
})

export const notice = style({
  display: 'grid',
  justifyItems: 'start',
  gap: vars.space.xs,
  padding: `${vars.space.md} ${vars.space.lg}`,
  color: vars.color.muted,
})

export const history = style({
  paddingBottom: vars.space.md,
})

export const summaryBar = style({
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  padding: `${vars.space.sm} ${vars.space.md} ${vars.space.xs}`,
})

export const headerRow = style({
  display: 'grid',
  gridTemplateColumns: '28px 1.3fr 0.9fr 1.3fr',
  alignItems: 'center',
  textAlign: 'center',
  padding: `${vars.space.sm} 0`,
  borderBlock: `${vars.rule.hair} solid ${vars.color.rule}`,
  color: vars.color.muted,
  fontWeight: '700',
})

export const row = style({
  display: 'grid',
  gridTemplateColumns: '28px 1.3fr 0.9fr 1.3fr',
  alignItems: 'center',
  textAlign: 'center',
  width: '100%',
  padding: `${vars.space.md} 0`,
  border: '0',
  borderBottom: `${vars.rule.hair} solid ${vars.color.rule}`,
  background: 'transparent',
  color: vars.color.ink2,
  cursor: 'pointer',
  transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
  selectors: {
    '&:hover': {
      background: vars.color.raised,
    },
    "&[aria-expanded='true']": {
      background: vars.color.raised,
    },
  },
})

export const rows = style({
  margin: '0',
  padding: '0',
  listStyle: 'none',
})

export const caret = style({
  color: vars.color.muted,
})

export const strong = style({
  color: vars.color.ink,
  fontWeight: '700',
})

export const grade = style({
  display: 'inline-flex',
  justifyContent: 'center',
  alignItems: 'center',
  gap: vars.space.sm,
})

export const ok = style({
  display: 'inline-flex',
  alignItems: 'center',
  gap: vars.space.xxs,
  fontWeight: '700',
  color: vars.color.ok,
})

export const bad = style({
  display: 'inline-flex',
  alignItems: 'center',
  gap: vars.space.xxs,
  fontWeight: '700',
  color: vars.color.bad,
})

export const verdictIcon = style({
  width: '16px',
  height: '16px',
})

export const muted = style({
  color: vars.color.muted,
})

export const codePanel = style({
  padding: `${vars.space.sm} ${vars.space.md}`,
  borderBottom: `${vars.rule.hair} solid ${vars.color.rule}`,
  background: vars.color.paper3,
})

export const copyBar = style({
  display: 'flex',
  justifyContent: 'flex-end',
  marginBottom: vars.space.sm,
})

export const code = style({
  overflow: 'hidden',
  borderRadius: vars.radius.panel,
  background: vars.color.card,
})

export const more = style({
  display: 'flex',
  justifyContent: 'center',
  paddingTop: vars.space.md,
})
