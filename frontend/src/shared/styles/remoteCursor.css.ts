import { globalStyle } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

globalStyle('.yRemoteSelection', {
  background: `color-mix(in oklab, ${vars.color.accent} 40%, transparent)`,
})

globalStyle('.yRemoteSelectionHead', {
  position: 'absolute',
  height: '100%',
  borderLeft: `2px solid ${vars.color.accent2}`,
})

globalStyle('.yRemoteSelectionHead::after', {
  position: 'absolute',
  left: '-2px',
  bottom: '100%',
  padding: `0 ${vars.space.xs}`,
  borderRadius: `8px 8px 8px 0`,
  background: vars.color.accent2,
  color: vars.color.accentInk,
  fontFamily: vars.font.button,
  fontSize: vars.text.xs,
  fontWeight: '700',
  lineHeight: '1.5',
  whiteSpace: 'nowrap',
  pointerEvents: 'none',
})
