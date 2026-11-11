import type Phaser from 'phaser'
import { frameName } from '@/entities/avatar/lib/catSprite'
import type { AvatarState } from '@/entities/lounge/api/loungeMessages'
import { rosterStore } from '@/entities/lounge/model/rosterStore'
import {
  type CatView,
  createCatView,
  seatPosition,
  walkAnimationKey,
} from '@/widgets/lounge-map/lib/catView'
import { DESK_BODY_HEIGHT, DESKS } from '@/widgets/lounge-map/model/loungeMap'

// 서버가 보내는 간격(100ms)과 같은 시간에 걸쳐 새 위치로 따라간다 (docs/tuning.md "위치 전송 간격")
const FOLLOW_DURATION_MS = 100
// 이보다 멀리 떨어지면 따라가지 않고 바로 옮긴다 (다시 연결해 한참 밀린 경우)
const SNAP_DISTANCE = 40

type RemoteCat = CatView & {
  avatar: AvatarState
}

/**
 * 다른 사람 고양이들. 좌표는 rosterStore 에서 매 프레임 직접 읽는다 (React 를 거치지 않는다).
 *
 * 보간(interpolation): 위치는 100ms 마다 한 번 온다. 받은 자리로 바로 옮기면 1초에 10번 뚝뚝 순간이동한다.
 * 그래서 매 프레임 "남은 거리의 (지난 시간 / 100ms) 만큼"씩 다가가서, 다음 위치가 올 때쯤 도착하게 한다.
 */
export class RemoteCats {
  private readonly cats = new Map<string, RemoteCat>()
  private readonly scene: Phaser.Scene

  constructor(scene: Phaser.Scene) {
    this.scene = scene
  }

  update(deltaMs: number, zoom: number) {
    const { avatars, myAvatarId } = rosterStore.getState()
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
    }
  }

  // 누군가 앉아 있어 노트북을 켜 둘 책상들
  seatedDeskIds(): number[] {
    return [...this.cats.values()]
      .map((cat) => cat.avatar.deskId)
      .filter((deskId): deskId is number => deskId !== null)
  }

  setNameResolution(resolution: number) {
    for (const cat of this.cats.values()) {
      cat.nameTag.setResolution(resolution)
    }
  }

  private add(avatar: AvatarState, zoom: number): RemoteCat {
    const view = createCatView(
      this.scene,
      avatar.coat,
      avatar.nickname,
      false,
      avatar.x,
      avatar.y,
    )
    view.nameTag.setResolution(zoom * window.devicePixelRatio)
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
      sprite.anims.stop()
      sprite.setFrame(frameName('sit', 0))
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
    // 멈췄다는 소식이 와도 아직 따라가는 중이면 걷는 모습을 유지한다
    if (avatar.moving || distance > 0.5) {
      sprite.anims.play(walkAnimationKey(avatar.coat, avatar.direction), true)
    } else {
      sprite.anims.stop()
      sprite.setFrame(frameName(avatar.direction, 0))
    }
  }
}
