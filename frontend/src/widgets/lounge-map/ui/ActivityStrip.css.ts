import { globalStyle, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const strip = style({
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: vars.space.md,
})

export const iconButton = style({
  width: '32px',
  height: '32px',
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

export const figure = style({
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  lineHeight: '1.1',
})

export const number = style({
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.xl,
  fontVariantNumeric: 'tabular-nums',
})

export const label = style({
  color: vars.color.muted,
  fontSize: vars.text.xs,
})

export const star = style({
  width: '36px',
  height: '36px',
  display: 'grid',
  placeItems: 'center',
  borderRadius: vars.radius.pill,
  background: vars.color.paper3,
  color: vars.map.star,
})

globalStyle(`${iconButton} svg`, {
  width: '14px',
  height: '14px',
})

globalStyle(`${star} svg`, {
  width: '18px',
  height: '18px',
})
