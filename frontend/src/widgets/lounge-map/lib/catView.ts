import type Phaser from 'phaser'
import {
  BODY_CENTER_OFFSET,
  drawCatSheet,
  FRAME_SIZE,
  frameName,
  POSES,
  type Pose,
  SEATED_TEMPLATE_TOP_FROM_ORIGIN,
  SHEET_HEIGHT,
  SHEET_WIDTH,
} from '@/entities/avatar/lib/catSprite'
import { COAT_PALETTES, type Coat } from '@/entities/avatar/model/appearance'
import { tokenColor } from '@/widgets/lounge-map/lib/tokenColor'
import type { Desk } from '@/widgets/lounge-map/model/loungeMap'

// 걷기는 초당 4칸 (docs/tuning.md)
const WALK_FRAME_RATE = 4
const NAME_TAG_GAP = 1
const DIRECTIONS = ['down', 'up', 'left', 'right'] as const

export function catTextureKey(coat: Coat) {
  return `cat-${coat}`
}

export function walkAnimationKey(coat: Coat, direction: string) {
  return `${catTextureKey(coat)}-${direction}`
}

/**
 * 털색 시트를 처음 필요할 때 한 번만 만든다. 같은 도트 템플릿에 팔레트만 바꿔 그린다.
 * 다른 사람이 처음 보는 털색으로 들어와도 그때 만들면 된다.
 */
export function ensureCatTexture(scene: Phaser.Scene, coat: Coat) {
  const key = catTextureKey(coat)
  if (scene.textures.exists(key)) {
    return key
  }
  const sheet = scene.textures.createCanvas(key, SHEET_WIDTH, SHEET_HEIGHT)!
  drawCatSheet(sheet.getContext(), COAT_PALETTES[coat], tokenColor)
  Object.entries(POSES).forEach(([pose, frameCount], row) => {
    for (let frame = 0; frame < frameCount; frame++) {
      sheet.add(
        frameName(pose as Pose, frame),
        0,
        frame * FRAME_SIZE,
        row * FRAME_SIZE,
        FRAME_SIZE,
        FRAME_SIZE,
      )
    }
  })
  sheet.refresh()
  for (const direction of DIRECTIONS) {
    scene.anims.create({
      key: walkAnimationKey(coat, direction),
      frames: [0, 1].map((frame) => ({
        key,
        frame: frameName(direction, frame),
      })),
      frameRate: WALK_FRAME_RATE,
      repeat: -1,
    })
  }
  return key
}

export type CatView = {
  container: Phaser.GameObjects.Container
  sprite: Phaser.GameObjects.Sprite
  nameTag: Phaser.GameObjects.Text
}

/**
 * 고양이 = 레이어를 담는 Container. 지금은 몸통 한 장 + 이름표지만, 꾸미기 아이템이 생기면 같은 칸 격자로 그린 스프라이트를 더한다.
 * Container 의 위치 = 발 위치. 그래서 y-정렬과 책상 앞 자리 계산이 모두 "발" 기준이다.
 *
 * @param mine 내 이름표는 복숭아색, 남은 크림색 (docs/ui-design.md 3장)
 */
export function createCatView(
  scene: Phaser.Scene,
  coat: Coat,
  name: string,
  mine: boolean,
  x: number,
  y: number,
): CatView {
  const sprite = scene.add
    .sprite(0, 0, ensureCatTexture(scene, coat), frameName('down', 0))
    .setOrigin(0.5, 1)
  const nameTag = scene.add
    .text(0, -FRAME_SIZE + 2 - NAME_TAG_GAP, name, {
      fontFamily: 'Jua, sans-serif',
      fontSize: '7px',
      color: tokenColor(mine ? '--color-accent-ink' : '--color-tag-ink'),
      backgroundColor: tokenColor(mine ? '--color-accent' : '--color-tag'),
      padding: { x: 2, y: 0 },
    })
    .setOrigin(0.5, 1)
  const container = scene.add.container(x, y, [sprite, nameTag]).setDepth(y)
  return { container, sprite, nameTag }
}

// 앉은 고양이의 발 위치. 템플릿 꼭대기가 책상 윗면보다 9 위에 오게 하면(목업 배치) 앞발 줄이 책상 윗면에 얹힌다
export function seatPosition(desk: Desk) {
  return {
    x: desk.x + 4 + BODY_CENTER_OFFSET,
    y: desk.y - 9 + SEATED_TEMPLATE_TOP_FROM_ORIGIN,
  }
}
