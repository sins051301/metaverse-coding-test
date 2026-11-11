import { globalStyle, keyframes, style } from '@vanilla-extract/css'
import { vars } from '@/shared/styles/theme.css'

const breathe = keyframes({
  to: {
    opacity: '0.4',
  },
})

export const panel = style({
  height: '100%',
  display: 'flex',
  flexDirection: 'column',
  background: vars.color.paper3,
  borderRadius: vars.radius.panel,
  overflow: 'hidden',
})

export const title = style({
  flex: 'none',
  padding: `${vars.space.sm} ${vars.space.lg} 0`,
  fontSize: vars.text.md,
})

export const body = style({
  flex: '1',
  minHeight: '0',
  overflowY: 'auto',
  padding: `${vars.space.xs} ${vars.space.lg} ${vars.space.md}`,
})

export const muted = style({
  color: vars.color.muted,
})

export const ok = style({
  color: vars.color.ok,
  fontWeight: '700',
})

export const bad = style({
  color: vars.color.bad,
  fontWeight: '700',
})

export const notice = style({
  display: 'grid',
  justifyItems: 'start',
  gap: vars.space.xs,
  color: vars.color.ink2,
})

export const skeleton = style({
  display: 'grid',
  gap: vars.space.xs,
  paddingTop: vars.space.xs,
})

export const log = style({
  margin: `${vars.space.xs} 0 0`,
  padding: `${vars.space.sm} ${vars.space.md}`,
  overflowX: 'auto',
  borderRadius: vars.radius.panel,
  fontFamily: vars.font.code,
  fontSize: vars.text.sm,
  whiteSpace: 'pre',
  background: vars.color.card,
  color: vars.color.cardInk,
})

export const runCase = style({
  display: 'grid',
  gridTemplateColumns: '80px minmax(0, 1fr)',
  gap: `${vars.space.xxxs} ${vars.space.sm}`,
  margin: '0',
  paddingBlock: vars.space.xs,
  borderBottom: `${vars.rule.hair} solid ${vars.color.rule}`,
  selectors: {
    '&:last-child': {
      borderBottom: '0',
    },
  },
})

export const caseName = style({
  gridColumn: '1 / -1',
  fontWeight: '700',
})

export const cost = style({
  marginLeft: vars.space.xs,
  fontFamily: vars.font.body,
  fontSize: vars.text.xs,
  color: vars.color.muted,
})

export const output = style({
  whiteSpace: 'pre-wrap',
})

export const scoreRow = style({
  display: 'flex',
  alignItems: 'baseline',
  gap: vars.space.sm,
  paddingBottom: vars.space.xs,
})

export const score = style({
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.xxxxl,
  fontVariantNumeric: 'lining-nums tabular-nums',
  letterSpacing: '-0.04em',
  lineHeight: '1',
})

export const scoreOf = style({
  fontFamily: vars.font.display,
  fontWeight: '800',
  fontSize: vars.text.lg,
  color: vars.color.muted,
})

export const gradeList = style({
  display: 'grid',
  gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
  gap: `${vars.space.xxs} ${vars.space.lg}`,
  margin: '0',
  padding: '0',
  listStyle: 'none',
})

export const testName = style({
  width: '56px',
  flex: 'none',
  color: vars.color.ink2,
})

globalStyle(`${skeleton} span`, {
  height: '14px',
  borderRadius: vars.radius.pill,
  background: vars.color.paper2,
  animation: `${breathe} 1.2s ${vars.ease.out} infinite alternate`,
})

globalStyle(`${skeleton} span:nth-child(2)`, {
  width: '75%',
})

globalStyle(`${skeleton} span:nth-child(3)`, {
  width: '55%',
})

globalStyle(`${runCase} dt`, {
  color: vars.color.muted,
})

globalStyle(`${runCase} dd`, {
  margin: '0',
  fontFamily: vars.font.code,
  overflowWrap: 'anywhere',
})

globalStyle(`${gradeList} li`, {
  display: 'flex',
  alignItems: 'baseline',
  gap: vars.space.sm,
})
