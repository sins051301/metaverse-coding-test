import { readStorage, writeStorage } from '@/shared/lib/storage'

export const COATS = ['ginger', 'grey', 'black', 'white', 'siamese'] as const
export type Coat = (typeof COATS)[number]

/**
 * 아바타 외형. 지금은 털색만 있지만, 꾸미기(모자·안경 등)가 생기면 여기에 칸을 늘린다.
 * 그림은 이 값만 보고 레이어를 겹쳐 그리므로(catSprite), 외형을 바꿔도 게임 코드는 그대로다.
 */
export type AvatarAppearance = {
  coat: Coat
}

// 도트 템플릿의 글자가 어떤 색 토큰으로 칠해지는지. 같은 템플릿에 팔레트만 바꿔 끼워 털색 5종을 만든다
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

// 게스트는 처음 들어올 때 털색을 하나 뽑아 브라우저에 기억해 둔다. 회원 외형은 꾸미기 시스템이 생기면 서버에서 가져온다
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

// 게스트 이름표 (FR-01-1). 브라우저에 기억해 두어 새로고침해도 같은 이름으로 들어온다. 서버는 이 모양일 때만 받아 준다
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
