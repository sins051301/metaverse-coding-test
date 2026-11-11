import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const workspace = style({
  flex: '1',
  minWidth: '0',
  display: 'flex',
  flexDirection: 'column',
  gap: vars.space.sm,
})

export const message = style({
  flex: '1',
  padding: vars.space.lg,
  color: vars.color.muted,
})

export const problemBar = style({
  height: '48px',
  flex: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.sm,
  paddingInline: vars.space.xs,
})

export const backLink = style({
  padding: `${vars.space.xxs} ${vars.space.sm}`,
  borderRadius: vars.radius.pill,
  color: vars.color.muted,
  fontFamily: vars.font.button,
  fontWeight: '700',
  textDecoration: 'none',
  transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
  selectors: {
    '&:hover': {
      background: vars.color.paper2,
      color: vars.color.ink,
    },
  },
})

export const problemTitle = style({
  fontSize: vars.text.xl,
})

export const level = style({
  color: vars.color.muted,
  fontWeight: '700',
})

export const spacer = style({
  flex: '1',
})

export const languageSelect = style({
  height: '40px',
  paddingInline: vars.space.sm,
  border: `${vars.rule.bar} solid ${vars.color.rule}`,
  borderRadius: vars.radius.panel,
  background: vars.color.paper3,
  color: vars.color.ink,
  fontFamily: vars.font.button,
  fontWeight: '700',
})

export const main = style({
  flex: '1',
  minHeight: '0',
})

export const columnSeparator = style({
  width: vars.space.sm,
  position: 'relative',
  outline: 'none',
  selectors: {
    '&::after': {
      content: "''",
      position: 'absolute',
      inset: '0',
      margin: 'auto',
      borderRadius: vars.radius.pill,
      background: 'transparent',
      transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
      width: '2px',
    },
    '&:hover::after': {
      background: vars.color.accent,
    },
    '&:focus-visible::after': {
      background: vars.color.accent,
    },
  },
})

export const rowSeparator = style({
  height: vars.space.sm,
  position: 'relative',
  outline: 'none',
  selectors: {
    '&::after': {
      content: "''",
      position: 'absolute',
      inset: '0',
      margin: 'auto',
      borderRadius: vars.radius.pill,
      background: 'transparent',
      transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
      height: '2px',
    },
    '&:hover::after': {
      background: vars.color.accent,
    },
    '&:focus-visible::after': {
      background: vars.color.accent,
    },
  },
})

export const editorCard = style({
  height: '100%',
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

export const editorLoading = style({
  padding: `${vars.space.md} ${vars.space.lg}`,
  color: vars.color.cardMuted,
})

export const actionBar = style({
  height: '52px',
  flex: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.xs,
  paddingInline: vars.space.xs,
})
