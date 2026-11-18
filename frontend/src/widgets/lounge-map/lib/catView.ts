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
  drawSpeechBox,
  drawSpeechTail,
  SOLVED_HEIGHT,
  SOLVED_WIDTH,
  SPEECH_BOX_SIZE,
  SPEECH_BOX_SLICE,
  SPEECH_TAIL_HEIGHT,
  SPEECH_TAIL_WIDTH,
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
  speech: Phaser.GameObjects.Container
  speechBox: Phaser.GameObjects.NineSlice
  speechText: Phaser.GameObjects.Text
}

const EFFECT = {
  speechBox: 'fx-speech-box',
  speechTail: 'fx-speech-tail',
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
  canvas(EFFECT.speechBox, SPEECH_BOX_SIZE, SPEECH_BOX_SIZE, (context) =>
    drawSpeechBox(context, tokenColor),
  )
  canvas(EFFECT.speechTail, SPEECH_TAIL_WIDTH, SPEECH_TAIL_HEIGHT, (context) =>
    drawSpeechTail(context, tokenColor),
  )
}

const HEAD_TOP = -(FRAME_SIZE - 4)
const NAME_TAG_Y = -FRAME_SIZE + 2 - NAME_TAG_GAP
const BUBBLE_POSITION = { x: -16, y: HEAD_TOP }
const Z_POSITION = { x: 5, y: HEAD_TOP + 1 }
const STAR_BOTTOM = NAME_TAG_Y - 10
const SPEECH_TIP_Y = NAME_TAG_Y - 7
const SPEECH_WRAP_WIDTH = 52
const SPEECH_PADDING = { x: 3, y: 2 }
const SPEECH_POP_MS = 180
const SPEECH_FADE_MS = 350
const SPEECH_POP_START_SCALE = 0.4
export const SPEECH_SHOW_MS = 4_000

export function createCatView(
  scene: Phaser.Scene,
  coat: Coat,
  name: string,
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
      fontSize: '5px',
      color: tokenColor(vars.color.tag),
    })
    .setShadow(0, 1, tokenColor(vars.color.tagInk), 0)
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
  const speechBox = scene.add
    .nineslice(
      0,
      0,
      EFFECT.speechBox,
      undefined,
      SPEECH_BOX_SIZE,
      SPEECH_BOX_SIZE,
      SPEECH_BOX_SLICE,
      SPEECH_BOX_SLICE,
      SPEECH_BOX_SLICE,
      SPEECH_BOX_SLICE,
    )
    .setOrigin(0, 1)
  const speechTail = scene.add
    .image(-Math.floor(SPEECH_TAIL_WIDTH / 2), 0, EFFECT.speechTail)
    .setOrigin(0, 1)
  const speechText = scene.add
    .text(0.5, 0, '', {
      fontFamily: 'Jua, sans-serif',
      fontSize: '5px',
      color: tokenColor(vars.color.tagInk),
      wordWrap: { width: SPEECH_WRAP_WIDTH, useAdvancedWrap: true },
      align: 'center',
    })
    .setOrigin(0.5, 0)
  const speech = scene.add
    .container(0, SPEECH_TIP_Y, [speechBox, speechTail, speechText])
    .setVisible(false)
  const container = scene.add
    .container(x, y, [sprite, nameTag, bubble, z, star, solved, speech])
    .setDepth(y)
  return {
    container,
    sprite,
    nameTag,
    bubble,
    z,
    star,
    solved,
    speech,
    speechBox,
    speechText,
  }
}

function fitSpeechBox(view: CatView, text: string) {
  view.speechText.setText(text)
  const oddWidth =
    Math.ceil(view.speechText.width / 2) * 2 + 1 + SPEECH_PADDING.x * 2
  const height = Math.ceil(view.speechText.height) + SPEECH_PADDING.y * 2
  const boxBottom = -(SPEECH_TAIL_HEIGHT - 1)
  view.speechBox
    .setSize(oddWidth, height)
    .setPosition(-(oddWidth - 1) / 2, boxBottom)
  view.speechText.setY(boxBottom - height + SPEECH_PADDING.y)
}

function easeOutBack(progress: number) {
  const overshoot = 1.70158
  const shifted = progress - 1
  return 1 + (overshoot + 1) * shifted ** 3 + overshoot * shifted ** 2
}

function applySpeech(view: CatView, speech: Speech | null) {
  view.speech.setVisible(speech !== null)
  if (speech === null) return
  if (view.speechText.text !== speech.text) {
    fitSpeechBox(view, speech.text)
  }
  const popScale =
    speech.ageMs < SPEECH_POP_MS
      ? SPEECH_POP_START_SCALE +
        (1 - SPEECH_POP_START_SCALE) * easeOutBack(speech.ageMs / SPEECH_POP_MS)
      : 1
  const remainingMs = SPEECH_SHOW_MS - speech.ageMs
  view.speech
    .setScale(popScale)
    .setAlpha(Math.min(1, remainingMs / SPEECH_FADE_MS))
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
  speech: Speech | null
}

type Speech = { text: string; ageMs: number }

export function recentSpeech(
  lastChat: { text: string; at: number } | undefined,
  now: number,
): Speech | null {
  return lastChat !== undefined && now - lastChat.at < SPEECH_SHOW_MS
    ? { text: lastChat.text, ageMs: now - lastChat.at }
    : null
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
  applySpeech(view, look.speech)
  const starBottom =
    look.speech === null
      ? STAR_BOTTOM
      : SPEECH_TIP_Y - (SPEECH_TAIL_HEIGHT - 1) - view.speechBox.height - 1
  view.star.setVisible(!look.mine && look.solved).setY(starBottom - (step % 2))
}

export function seatPosition(desk: Desk) {
  return {
    x: desk.x + 4 + BODY_CENTER_OFFSET,
    y: desk.y - 9 + SEATED_TEMPLATE_TOP_FROM_ORIGIN,
  }
}
