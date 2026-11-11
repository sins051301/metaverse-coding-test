import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const dialog = style({
  width: '440px',
  padding: vars.space.lg,
  border: '0',
  borderRadius: vars.radius.panel,
  background: vars.color.raised,
  color: vars.color.ink,
  boxShadow: vars.shadow.card,
  selectors: {
    '&::backdrop': {
      background: 'oklch(10% 0.01 60 / 0.65)',
    },
  },
})

export const title = style({
  fontSize: vars.text.xl,
})

export const description = style({
  marginTop: vars.space.sm,
  color: vars.color.ink2,
  fontSize: vars.text.md,
  lineHeight: '1.6',
})

export const actions = style({
  display: 'flex',
  justifyContent: 'flex-end',
  gap: vars.space.xs,
  marginTop: vars.space.lg,
})
