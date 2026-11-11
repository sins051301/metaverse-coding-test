import { style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const page = style({
  height: '100%',
  display: 'flex',
  flexDirection: 'column',
})

export const body = style({
  flex: '1',
  minHeight: '0',
  display: 'flex',
  gap: vars.space.sm,
  padding: vars.space.sm,
})
