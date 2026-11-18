import type Phaser from 'phaser'
import { frameName } from '@/entities/avatar/lib/catSprite'
import type { AvatarState } from '@/entities/lounge/api/loungeMessages'
import { rosterStore } from '@/entities/lounge/model/rosterStore'
import {
  applyCatLook,
  type CatView,
  createCatView,
  recentSpeech,
  SOLVED_SHOW_MS,
  seatPosition,
  walkAnimationKey,
} from '@/widgets/lounge-map/lib/catView'
import { DESK_BODY_HEIGHT, DESKS } from '@/widgets/lounge-map/model/loungeMap'

const FOLLOW_DURATION_MS = 100
const SNAP_DISTANCE = 40

type RemoteCat = CatView & {
  avatar: AvatarState
}

export class RemoteCats {
  private readonly cats = new Map<string, RemoteCat>()
  private readonly scene: Phaser.Scene

  constructor(scene: Phaser.Scene) {
    this.scene = scene
  }

  update(time: number, deltaMs: number, zoom: number) {
    const { avatars, myAvatarId, solvedAt, lastChatAt } = rosterStore.getState()
    const now = Date.now()
    for (const [id, cat] of this.cats) {
      if (!avatars.has(id) || id === myAvatarId) {
        cat.container.destroy()
        this.cats.delete(id)
      }
    }
    for (const avatar of avatars.values()) {
      if (avatar.id === myAvatarId) {
        continue
      }
      const cat = this.cats.get(avatar.id) ?? this.add(avatar, zoom)
      cat.avatar = avatar
      this.follow(cat, deltaMs)
      const solvedTime = solvedAt.get(avatar.id)
      applyCatLook(
        cat,
        {
          seated: avatar.deskId !== null,
          activity: avatar.activity,
          mine: false,
          solved: solvedTime !== undefined && now - solvedTime < SOLVED_SHOW_MS,
          failed: false,
          speech: recentSpeech(lastChatAt.get(avatar.id), now),
        },
        time,
      )
    }
  }

  litDeskIds(): number[] {
    return [...this.cats.values()]
      .filter((cat) => cat.avatar.activity !== 'napping')
      .map((cat) => cat.avatar.deskId)
      .filter((deskId): deskId is number => deskId !== null)
  }

  setNameResolution(resolution: number) {
    for (const cat of this.cats.values()) {
      cat.nameTag.setResolution(resolution)
      cat.speechText.setResolution(resolution)
    }
  }

  private add(avatar: AvatarState, zoom: number): RemoteCat {
    const view = createCatView(
      this.scene,
      avatar.coat,
      avatar.nickname,
      avatar.x,
      avatar.y,
    )
    view.nameTag.setResolution(zoom * window.devicePixelRatio)
    view.speechText.setResolution(zoom * window.devicePixelRatio)
    const cat = { ...view, avatar }
    this.cats.set(avatar.id, cat)
    return cat
  }

  private follow(cat: RemoteCat, deltaMs: number) {
    const { avatar, container, sprite } = cat
    if (avatar.deskId !== null) {
      const seat = seatPosition(DESKS[avatar.deskId])
      container.setPosition(seat.x, seat.y)
      container.setDepth(DESKS[avatar.deskId].y + DESK_BODY_HEIGHT + 0.5)
      return
    }
    const distance = Math.hypot(avatar.x - container.x, avatar.y - container.y)
    if (distance > SNAP_DISTANCE) {
      container.setPosition(avatar.x, avatar.y)
    } else {
      const progress = Math.min(1, deltaMs / FOLLOW_DURATION_MS)
      container.setPosition(
        container.x + (avatar.x - container.x) * progress,
        container.y + (avatar.y - container.y) * progress,
      )
    }
    container.setDepth(container.y)
    if (avatar.moving || distance > 0.5) {
      sprite.anims.play(walkAnimationKey(avatar.coat, avatar.direction), true)
    } else {
      sprite.anims.stop()
      sprite.setFrame(frameName(avatar.direction, 0))
    }
  }
}
