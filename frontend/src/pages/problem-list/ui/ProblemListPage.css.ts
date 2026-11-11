import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const laptop = style({
  flex: '1',
  minWidth: '0',
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space.md,
  padding: `${vars.space.lg} ${vars.space.xl}`,
  overflowY: 'auto',
  border: `${vars.rule.hair} solid ${vars.color.cardRule}`,
  borderRadius: vars.radius.panel,
  background: vars.color.card,
  boxShadow: vars.shadow.card,
})

export const head = style({
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: vars.space.md,
})

export const title = style({
  fontSize: vars.text.xl,
})

export const chips = style({
  display: 'flex',
  gap: vars.space.xxs,
})

export const chip = style({
  padding: `${vars.space.xxs} ${vars.space.sm}`,
  border: '0',
  borderRadius: vars.radius.pill,
  background: vars.color.paper2,
  color: vars.color.ink,
  fontFamily: vars.font.button,
  fontSize: vars.text.sm,
  fontWeight: '700',
  cursor: 'pointer',
  transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
  selectors: {
    '&:hover': {
      background: vars.color.rule,
    },
    "&[aria-pressed='true']": {
      background: vars.color.paper3,
      boxShadow: `inset 0 0 0 3px ${vars.color.accent}`,
    },
  },
})

export const message = style({
  color: vars.color.cardMuted,
})

export const list = style({
  margin: '0',
  padding: '0',
  listStyle: 'none',
})

export const row = style({
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.md,
  padding: `${vars.space.sm} ${vars.space.md}`,
  borderBottom: `${vars.rule.hair} solid ${vars.color.cardRule}`,
  color: vars.color.cardInk,
  textDecoration: 'none',
  transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
  selectors: {
    '&:hover': {
      background: vars.color.paper3,
    },
  },
})

export const number = style({
  width: '32px',
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.md,
  fontVariantNumeric: 'tabular-nums',
  color: vars.color.cardMuted,
})

export const problemTitle = style({
  flex: '1',
  fontSize: vars.text.md,
  fontWeight: '700',
})

export const level = style({
  color: vars.color.cardMuted,
  fontWeight: '700',
})
