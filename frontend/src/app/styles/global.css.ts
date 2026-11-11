import { globalStyle } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

globalStyle('*, *::before, *::after', {
  boxSizing: 'border-box',
})

globalStyle('html', {
  colorScheme: 'dark',
  scrollbarColor: `${vars.color.rule} ${vars.color.paper3}`,
})

globalStyle('body', {
  margin: '0',
  fontFamily: vars.font.body,
  fontSize: vars.text.sm,
  fontWeight: '500',
  lineHeight: '1.55',
  color: vars.color.ink,
  background: vars.color.paper,
  WebkitFontSmoothing: 'antialiased',
})

globalStyle('h1, h2, h3', {
  margin: '0',
  fontFamily: vars.font.display,
  fontWeight: '800',
  letterSpacing: vars.tracking.display,
  lineHeight: '1.1',
})

globalStyle('p', {
  margin: '0',
})

globalStyle('button, select', {
  font: 'inherit',
  color: 'inherit',
})

globalStyle(':focus-visible', {
  outline: `3px solid ${vars.color.focus}`,
  outlineOffset: '2px',
})

globalStyle('kbd', {
  fontFamily: vars.font.button,
  fontSize: vars.text.xs,
  fontWeight: '700',
  background: vars.color.paper3,
  color: vars.color.ink2,
  borderRadius: vars.radius.pill,
  padding: `0 ${vars.space.xs}`,
})

globalStyle('*, *::before, *::after', {
  '@media': {
    '(prefers-reduced-motion: reduce)': {
      animationDuration: '0.01ms !important',
      animationIterationCount: '1 !important',
      transitionDuration: '0.01ms !important',
    },
  },
})
