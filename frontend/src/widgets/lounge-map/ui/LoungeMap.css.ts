import { globalStyle, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const map = style({
  position: 'relative',
  width: '100%',
  height: '100%',
  overflow: 'hidden',
  background: vars.color.paper3,
})

export const canvasHost = style({
  width: '100%',
  height: '100%',
})

export const tooltip = style({
  position: 'absolute',
  transform: `translate(-50%, calc(-100% - ${vars.space.xs}))`,
  display: 'flex',
  alignItems: 'center',
  gap: vars.space.xs,
  padding: `${vars.space.xxs} ${vars.space.sm} ${vars.space.xxs} ${vars.space.xxs}`,
  borderRadius: vars.radius.bubble,
  background: vars.color.tag,
  color: vars.color.tagInk,
  fontWeight: '700',
  whiteSpace: 'nowrap',
  boxShadow: vars.shadow.card,
  pointerEvents: 'none',
})

export const key = style({
  padding: `0 ${vars.space.xs}`,
  borderRadius: vars.radius.pill,
  background: vars.color.accent,
  color: vars.color.accentInk,
  fontFamily: vars.font.button,
  fontSize: vars.text.xs,
  fontWeight: '700',
})

globalStyle(`${canvasHost} canvas`, {
  display: 'block',
  imageRendering: 'pixelated',
})
