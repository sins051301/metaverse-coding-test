import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const app = style({
  height: '100vh',
  '@media': {
    '(max-width: 1279px)': {
      display: 'none',
    },
  },
})

export const narrowNotice = style({
  display: 'none',
  height: '100vh',
  placeItems: 'center',
  padding: vars.space.lg,
  fontSize: vars.text.md,
  color: vars.color.muted,
  textAlign: 'center',
  '@media': {
    '(max-width: 1279px)': {
      display: 'grid',
    },
  },
})
