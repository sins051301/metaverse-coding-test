import { recipe, type RecipeVariants } from '@vanilla-extract/recipes'
import { vars } from '@/shared/styles/theme.css'

export const button = recipe({
  base: {
    height: '40px',
    display: 'inline-flex',
    alignItems: 'center',
    gap: vars.space.xs,
    border: `${vars.rule.bar} solid ${vars.color.ink}`,
    borderRadius: vars.radius.pill,
    paddingInline: vars.space.md,
    background: 'transparent',
    color: vars.color.ink,
    fontFamily: vars.font.button,
    fontWeight: '700',
    fontSize: vars.text.sm,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    transition: `background-color ${vars.dur.micro} ${vars.ease.out}, translate ${vars.dur.micro} ${vars.ease.out}`,
    selectors: {
      '&:hover': {
        background: vars.color.paper2,
      },
      '&:active': {
        translate: '0 1px',
      },
      '&:disabled': {
        opacity: '0.5',
        cursor: 'not-allowed',
      },
    },
  },
  variants: {
    variant: {
      default: {},
      primary: {
        background: vars.color.accent,
        borderColor: vars.color.accent,
        color: vars.color.accentInk,
        selectors: {
          '&:hover': {
            background: vars.color.accent2,
            borderColor: vars.color.accent2,
          },
        },
      },
      quiet: {
        borderColor: 'transparent',
      },
    },
    size: {
      medium: {},
      small: {
        height: '32px',
        paddingInline: vars.space.sm,
        fontSize: vars.text.xs,
      },
    },
  },
  defaultVariants: {
    variant: 'default',
    size: 'medium',
  },
})

export type ButtonVariants = NonNullable<RecipeVariants<typeof button>>
