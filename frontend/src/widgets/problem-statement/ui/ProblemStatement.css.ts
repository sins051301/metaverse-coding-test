import { globalStyle, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

export const statement = style({
  height: '100%',
  overflowY: 'auto',
  padding: vars.space.lg,
  background: vars.color.paper2,
  borderRadius: vars.radius.panel,
  color: vars.color.ink2,
  fontSize: vars.text.md,
  lineHeight: '1.7',
})

export const limits = style({
  display: 'flex',
  gap: vars.space.xs,
  marginBottom: vars.space.md,
})

export const pill = style({
  padding: `${vars.space.xxxs} ${vars.space.sm}`,
  borderRadius: vars.radius.pill,
  background: vars.color.paper3,
  color: vars.color.ink2,
  fontSize: vars.text.xs,
  fontWeight: '700',
})

export const markdown = style({})

globalStyle(`${markdown} h2`, {
  margin: `${vars.space.lg} 0 ${vars.space.xs}`,
  fontSize: vars.text.lg,
  color: vars.color.ink,
})

globalStyle(`${markdown} h2:first-child`, {
  marginTop: '0',
})

globalStyle(`${markdown} p`, {
  margin: `0 0 ${vars.space.xs}`,
})

globalStyle(`${markdown} ul`, {
  margin: '0',
  paddingLeft: vars.space.md,
})

globalStyle(`${markdown} code`, {
  padding: `0 ${vars.space.xxs}`,
  borderRadius: vars.radius.panel,
  background: vars.color.paper3,
  color: vars.color.ink,
  fontFamily: vars.font.code,
  fontSize: '0.88em',
})

globalStyle(`${markdown} table`, {
  width: '100%',
  borderCollapse: 'collapse',
  fontSize: vars.text.sm,
})

globalStyle(`${markdown} th`, {
  padding: vars.space.xs,
  borderBottom: `${vars.rule.bar} solid ${vars.color.rule}`,
  color: vars.color.muted,
  fontWeight: '700',
  textAlign: 'left',
})

globalStyle(`${markdown} td`, {
  padding: vars.space.xs,
  borderBottom: `${vars.rule.hair} solid ${vars.color.rule}`,
  color: vars.color.ink,
  fontFamily: vars.font.code,
})
