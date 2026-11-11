import { readStorage, writeStorage } from '@/shared/lib/storage'

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
    fur: '--cat-ginger',
    furDark: '--cat-ginger-dark',
    muzzle: '--cat-cream',
    eye: '--cat-eye',
  },
  grey: {
    fur: '--cat-grey',
    furDark: '--cat-grey-dark',
    muzzle: '--cat-white',
    eye: '--cat-eye',
  },
  black: {
    fur: '--cat-black',
    furDark: '--cat-black',
    muzzle: '--cat-black-muzzle',
    eye: '--cat-eye-gold',
  },
  white: {
    fur: '--cat-white',
    furDark: '--cat-cream',
    muzzle: '--cat-white',
    eye: '--cat-eye',
  },
  siamese: {
    fur: '--cat-cream',
    furDark: '--cat-brown',
    muzzle: '--cat-brown',
    eye: '--cat-eye',
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
