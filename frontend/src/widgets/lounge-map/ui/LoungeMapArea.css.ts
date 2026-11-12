import { globalStyle, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const lounge = style({
  position: 'relative',
  flex: '1',
  minWidth: '0',
  display: 'flex',
  flexDirection: 'column',
  overflow: 'hidden',
  borderRadius: vars.radius.panel,
})

export const panel = style({
  width: '280px',
  flex: 'none',
  display: 'flex',
  flexDirection: 'column',
  overflow: 'hidden',
  background: vars.color.paper2,
  borderRadius: vars.radius.panel,
})

export const collapsed = style({
  width: '48px',
  alignItems: 'center',
  paddingTop: vars.space.xs,
})

export const head = style({
  height: '48px',
  flex: 'none',
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.xxs,
  paddingInline: `${vars.space.md} ${vars.space.xs}`,
})

export const name = style({
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.md,
})

export const spacer = style({
  flex: '1',
})

export const mapBox = style({
  flex: '1',
  minHeight: '0',
  selectors: {
    '&[hidden]': {
      display: 'none',
    },
  },
})

export const iconButton = style({
  width: '32px',
  height: '32px',
  flex: 'none',
  display: 'grid',
  placeItems: 'center',
  padding: '0',
  border: '0',
  borderRadius: vars.radius.pill,
  background: 'transparent',
  color: vars.color.ink,
  cursor: 'pointer',
  transition: `background-color ${vars.dur.micro} ${vars.ease.out}`,
  selectors: {
    '&:hover': {
      background: vars.color.rule,
    },
  },
})

export const key = style({
  padding: `0 ${vars.space.xs}`,
  borderRadius: vars.radius.pill,
  background: vars.color.paper3,
  color: vars.color.ink,
  fontFamily: vars.font.button,
  fontSize: vars.text.xs,
  fontWeight: '700',
})

export const keys = style({
  position: 'absolute',
  left: vars.space.lg,
  bottom: vars.space.lg,
  display: 'flex',
  gap: vars.space.md,
  padding: `${vars.space.xs} ${vars.space.md} ${vars.space.xs} ${vars.space.xs}`,
  borderRadius: vars.radius.panel,
  background: vars.color.paper3,
  color: vars.color.ink,
})

globalStyle(`${panel} ${mapBox}`, {
  flex: 'none',
  height: '320px',
})

globalStyle(`${iconButton} svg`, {
  width: '14px',
  height: '14px',
})

globalStyle(`${keys} span`, {
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.xs,
})

export const side = style({
  flex: '1',
  minHeight: '0',
  display: 'flex',
  flexDirection: 'column',
})

export const tabs = style({
  flex: 'none',
  display: 'flex',
  gap: vars.space.xxs,
  padding: `${vars.space.xs} ${vars.space.xs} ${vars.space.xxs}`,
})

export const tab = style({
  padding: `${vars.space.xxs} ${vars.space.sm}`,
  border: 0,
  borderRadius: vars.radius.pill,
  background: 'transparent',
  color: vars.color.muted,
  fontFamily: vars.font.button,
  fontSize: vars.text.xs,
  fontWeight: '700',
  cursor: 'pointer',
  selectors: {
    '&[aria-selected="true"]': {
      background: vars.color.paper3,
      color: vars.color.ink,
    },
  },
})
