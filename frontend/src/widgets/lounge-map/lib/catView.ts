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
import type { Activity } from '@/entities/lounge/api/loungeMessages'
import {
  BUBBLE_HEIGHT,
  BUBBLE_WIDTH,
  drawBubble,
  drawSolved,
  drawStar,
  drawZ,
  SOLVED_HEIGHT,
  SOLVED_WIDTH,
  STAR_HEIGHT,
  STAR_WIDTH,
  Z_SIZE,
} from '@/widgets/lounge-map/lib/drawEffects'
import { tokenColor } from '@/widgets/lounge-map/lib/tokenColor'
import type { Desk } from '@/widgets/lounge-map/model/loungeMap'
import { vars } from '@/shared/styles/theme.css'

const WALK_FRAME_RATE = 4
const NAME_TAG_GAP = 1
const DIRECTIONS = ['down', 'up', 'left', 'right'] as const

export function catTextureKey(coat: Coat) {
  return `cat-${coat}`
}

export function walkAnimationKey(coat: Coat, direction: string) {
  return `${catTextureKey(coat)}-${direction}`
}

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
  bubble: Phaser.GameObjects.Image
  z: Phaser.GameObjects.Image
  star: Phaser.GameObjects.Image
  solved: Phaser.GameObjects.Image
}

const EFFECT = {
  bubble: (dotIndex: number) => `fx-bubble-${dotIndex}`,
  failure: 'fx-failure',
  z: 'fx-z',
  star: 'fx-star',
  solved: 'fx-solved',
}

function ensureEffectTextures(scene: Phaser.Scene) {
  if (scene.textures.exists(EFFECT.z)) {
    return
  }
  function canvas(
    key: string,
    width: number,
    height: number,
    draw: (context: CanvasRenderingContext2D) => void,
  ) {
    const texture = scene.textures.createCanvas(key, width, height)!
    draw(texture.getContext())
    texture.refresh()
  }
  for (const dotIndex of [0, 1, 2]) {
    canvas(EFFECT.bubble(dotIndex), BUBBLE_WIDTH, BUBBLE_HEIGHT, (context) =>
      drawBubble(context, tokenColor, dotIndex, false),
    )
  }
  canvas(EFFECT.failure, BUBBLE_WIDTH, BUBBLE_HEIGHT, (context) =>
    drawBubble(context, tokenColor, null, true),
  )
  canvas(EFFECT.z, Z_SIZE, Z_SIZE, (context) => drawZ(context, tokenColor))
  canvas(EFFECT.star, STAR_WIDTH, STAR_HEIGHT, (context) =>
    drawStar(context, tokenColor),
  )
  canvas(EFFECT.solved, SOLVED_WIDTH, SOLVED_HEIGHT, (context) =>
    drawSolved(context, tokenColor),
  )
}

const HEAD_TOP = -(FRAME_SIZE - 4)
const NAME_TAG_Y = -FRAME_SIZE + 2 - NAME_TAG_GAP
const BUBBLE_POSITION = { x: -16, y: HEAD_TOP }
const Z_POSITION = { x: 5, y: HEAD_TOP + 1 }
const STAR_BOTTOM = NAME_TAG_Y - 10

export function createCatView(
  scene: Phaser.Scene,
  coat: Coat,
  name: string,
  mine: boolean,
  x: number,
  y: number,
): CatView {
  ensureEffectTextures(scene)
  const sprite = scene.add
    .sprite(0, 0, ensureCatTexture(scene, coat), frameName('down', 0))
    .setOrigin(0.5, 1)
  const nameTag = scene.add
    .text(0, NAME_TAG_Y, name, {
      fontFamily: 'Jua, sans-serif',
      fontSize: '7px',
      color: tokenColor(mine ? vars.color.accentInk : vars.color.tagInk),
      backgroundColor: tokenColor(mine ? vars.color.accent : vars.color.tag),
      padding: { x: 2, y: 0 },
    })
    .setOrigin(0.5, 1)
  const bubble = scene.add
    .image(BUBBLE_POSITION.x, BUBBLE_POSITION.y, EFFECT.bubble(0))
    .setOrigin(0, 0)
    .setVisible(false)
  const z = scene.add
    .image(Z_POSITION.x, Z_POSITION.y, EFFECT.z)
    .setOrigin(0, 0)
    .setVisible(false)
  const star = scene.add
    .image(0, STAR_BOTTOM, EFFECT.star)
    .setOrigin(0.5, 1)
    .setVisible(false)
  const solved = scene.add
    .image(0, NAME_TAG_Y + 2, EFFECT.solved)
    .setOrigin(0.5, 1)
    .setVisible(false)
  const container = scene.add
    .container(x, y, [sprite, nameTag, bubble, z, star, solved])
    .setDepth(y)
  return { container, sprite, nameTag, bubble, z, star, solved }
}

const EFFECT_STEP_MS = 450
export const SOLVED_SHOW_MS = 10_000
export const FAILURE_SHOW_MS = 3_000

export type CatLook = {
  seated: boolean
  activity: Activity
  mine: boolean
  solved: boolean
  failed: boolean
}

export function applyCatLook(view: CatView, look: CatLook, time: number) {
  const step = Math.floor(time / EFFECT_STEP_MS)
  const typing = look.seated && look.activity === 'typing'
  const napping = look.seated && look.activity === 'napping'
  if (look.seated) {
    view.sprite.anims.stop()
    view.sprite.setFrame(
      napping
        ? frameName('nap', 0)
        : frameName('sit', typing ? 1 + (step % 2) : 0),
    )
  }
  const showFailure = look.mine && look.failed
  view.bubble
    .setVisible(typing || showFailure)
    .setTexture(showFailure ? EFFECT.failure : EFFECT.bubble(step % 3))
  view.z.setVisible(napping).setY(Z_POSITION.y - (step % 2))
  view.solved.setVisible(look.mine && look.solved)
  view.nameTag.setVisible(!(look.mine && look.solved))
  view.star.setVisible(!look.mine && look.solved).setY(STAR_BOTTOM - (step % 2))
}

export function seatPosition(desk: Desk) {
  return {
    x: desk.x + 4 + BODY_CENTER_OFFSET,
    y: desk.y - 9 + SEATED_TEMPLATE_TOP_FROM_ORIGIN,
  }
}
