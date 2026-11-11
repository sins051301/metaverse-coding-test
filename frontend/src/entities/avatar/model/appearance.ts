import { readStorage, writeStorage } from '@/shared/lib/storage'
import { vars } from '@/shared/styles/theme.css'

export const COATS = ['ginger', 'grey', 'black', 'white', 'siamese'] as const
export type Coat = (typeof COATS)[number]

export type AvatarAppearance = {
  coat: Coat
}

export type CoatPalette = {
  fur: string
  furDark: string
  muzzle: string
  eye: string
}

export const COAT_PALETTES: Record<Coat, CoatPalette> = {
  ginger: {
    fur: vars.cat.ginger,
    furDark: vars.cat.gingerDark,
    muzzle: vars.cat.cream,
    eye: vars.cat.eye,
  },
  grey: {
    fur: vars.cat.grey,
    furDark: vars.cat.greyDark,
    muzzle: vars.cat.white,
    eye: vars.cat.eye,
  },
  black: {
    fur: vars.cat.black,
    furDark: vars.cat.black,
    muzzle: vars.cat.blackMuzzle,
    eye: vars.cat.eyeGold,
  },
  white: {
    fur: vars.cat.white,
    furDark: vars.cat.cream,
    muzzle: vars.cat.white,
    eye: vars.cat.eye,
  },
  siamese: {
    fur: vars.cat.cream,
    furDark: vars.cat.brown,
    muzzle: vars.cat.brown,
    eye: vars.cat.eye,
  },
}

const APPEARANCE_KEY = 'codingtest:v1:avatarAppearance'

export function readSavedAppearance(): AvatarAppearance {
  const saved = readStorage(localStorage, APPEARANCE_KEY)
  const coat = COATS.find((candidate) => candidate === saved)
  if (coat) {
    return { coat }
  }
  const picked = COATS[Math.floor(Math.random() * COATS.length)]
  writeStorage(localStorage, APPEARANCE_KEY, picked)
  return { coat: picked }
}

const GUEST_NICKNAME_KEY = 'codingtest:v1:guestNickname'
const GUEST_NICKNAME_PATTERN = /^익명_[0-9A-F]{4}$/

export function readGuestNickname(): string {
  const saved = readStorage(localStorage, GUEST_NICKNAME_KEY)
  if (saved !== null && GUEST_NICKNAME_PATTERN.test(saved)) {
    return saved
  }
  const suffix = Math.floor(Math.random() * 0x10000)
    .toString(16)
    .toUpperCase()
    .padStart(4, '0')
  const nickname = `익명_${suffix}`
  writeStorage(localStorage, GUEST_NICKNAME_KEY, nickname)
  return nickname
}
