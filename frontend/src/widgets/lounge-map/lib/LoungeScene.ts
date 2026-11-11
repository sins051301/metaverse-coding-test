import Phaser from 'phaser'
import { FRAME_SIZE, frameName } from '@/entities/avatar/lib/catSprite'
import type { AvatarAppearance } from '@/entities/avatar/model/appearance'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import type { Direction } from '@/entities/lounge/api/loungeMessages'
import {
  loungeStore,
  type LoungeState,
} from '@/entities/lounge/model/loungeStore'
import { occupiedDeskIds } from '@/entities/lounge/model/rosterStore'
import {
  createCatView,
  seatPosition,
  walkAnimationKey,
} from '@/widgets/lounge-map/lib/catView'
import { drawDesk, drawRoom } from '@/widgets/lounge-map/lib/drawLoungeRoom'
import { RemoteCats } from '@/widgets/lounge-map/lib/RemoteCats'
import {
  readSavedPosition,
  savePosition,
} from '@/widgets/lounge-map/lib/savedPosition'
import { tokenColor } from '@/widgets/lounge-map/lib/tokenColor'
import {
  DECORATION_BODY_HEIGHT,
  DECORATIONS,
  DEFAULT_SPAWN,
  DESK_BODY_HEIGHT,
  DESK_REACH,
  DESK_SPRITE_TOP,
  DESK_SPRITE_HEIGHT,
  DESK_WIDTH,
  DESKS,
  deskFrontSpot,
  KENNEY_SPACING,
  KENNEY_TILE,
  WALL_HEIGHT,
  WORLD_HEIGHT,
  WORLD_WIDTH,
} from '@/widgets/lounge-map/model/loungeMap'

// 도트/초. 라운지 전체 화면(zoom 4)에서 화면상 초당 약 180px
const WALK_SPEED = 45
const PANEL_ZOOM = 2

const TEXTURE = {
  room: 'room',
  deskOff: 'desk-off',
  deskOn: 'desk-on',
  kenney: 'kenney-indoors',
}

type Keys = Record<
  'W' | 'A' | 'S' | 'D' | 'UP' | 'DOWN' | 'LEFT' | 'RIGHT' | 'F',
  Phaser.Input.Keyboard.Key
>

/**
 * 공용 라운지 장면. Phaser 장면의 세 단계:
 *  - preload: 파일(Kenney 장식 시트)을 불러온다
 *  - create: 방·책상·고양이를 한 번 배치한다
 *  - update: 매 프레임(초당 약 60번) 키 입력을 읽어 고양이를 움직이고, 근처 책상을 찾고, 다른 사람 고양이를 따라 옮긴다
 *
 * React 와는 loungeStore 로, 서버와는 loungeConnection 으로만 이야기한다.
 */
export class LoungeScene extends Phaser.Scene {
  private cat!: Phaser.GameObjects.Container
  private catBody!: Phaser.Physics.Arcade.Body
  private catSprite!: Phaser.GameObjects.Sprite
  private nameTag!: Phaser.GameObjects.Text
  private remoteCats!: RemoteCats
  private desks: Phaser.GameObjects.Image[] = []
  private keys!: Keys
  private direction: Direction = 'down'
  private unsubscribe = () => {}
  private previousState: LoungeState = loungeStore.getState()
  private readonly appearance: AvatarAppearance

  constructor(appearance: AvatarAppearance) {
    super('lounge')
    this.appearance = appearance
  }

  preload() {
    this.load.spritesheet(
      TEXTURE.kenney,
      '/assets/kenney/roguelike-indoors.png',
      {
        frameWidth: KENNEY_TILE,
        frameHeight: KENNEY_TILE,
        spacing: KENNEY_SPACING,
      },
    )
  }

  create() {
    this.createRoomTextures()
    this.add.image(0, 0, TEXTURE.room).setOrigin(0, 0).setDepth(-1)

    const obstacles = this.physics.add.staticGroup()
    // 벽: 위쪽 전체. 바닥 경계는 physics.world 의 경계(setCollideWorldBounds)가 막는다
    obstacles.add(
      this.add.zone(WORLD_WIDTH / 2, WALL_HEIGHT / 2, WORLD_WIDTH, WALL_HEIGHT),
    )

    for (const desk of DESKS) {
      const image = this.add
        .image(desk.x, desk.y + DESK_SPRITE_TOP, TEXTURE.deskOff)
        .setOrigin(0, 0)
        // 앞뒤 순서(y-정렬): 발 y 가 클수록 앞에 그린다. 책상은 앞판 아래쪽 y 를 기준으로
        .setDepth(desk.y + DESK_BODY_HEIGHT)
      this.desks.push(image)
      obstacles.add(
        this.add.zone(
          desk.x + DESK_WIDTH / 2,
          desk.y + DESK_BODY_HEIGHT / 2,
          DESK_WIDTH,
          DESK_BODY_HEIGHT,
        ),
      )
    }

    for (const decoration of DECORATIONS) {
      const bottom = decoration.y + KENNEY_TILE
      this.add
        .image(decoration.x, decoration.y, TEXTURE.kenney, decoration.frame)
        .setOrigin(0, 0)
        .setDepth(bottom)
      obstacles.add(
        this.add.zone(
          decoration.x + KENNEY_TILE / 2,
          bottom - DECORATION_BODY_HEIGHT / 2,
          KENNEY_TILE - 4,
          DECORATION_BODY_HEIGHT,
        ),
      )
    }

    this.createMyCat()
    this.remoteCats = new RemoteCats(this)
    this.physics.world.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT)
    this.physics.add.collider(this.cat, obstacles)

    const keyboard = this.input.keyboard!
    // enableCapture = false: 키를 Phaser 가 가로채지 않게 한다. 그래야 에디터·입력칸에서 W, A, S, D, F 를 그대로 칠 수 있다
    this.keys = keyboard.addKeys('W,A,S,D,UP,DOWN,LEFT,RIGHT,F', false) as Keys

    this.scale.on(Phaser.Scale.Events.RESIZE, () => this.fitCamera())
    this.unsubscribe = loungeStore.subscribe(() => this.onStoreChange())
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.unsubscribe()
      window.removeEventListener('beforeunload', this.saveCurrentPosition)
    })
    // 로그인하러 떠날 때도 위치를 남겨 두었다가, 돌아와 다시 연결하면 같은 자리에 선다 (README 10장 "라운지")
    window.addEventListener('beforeunload', this.saveCurrentPosition)

    const { seatedDeskId } = loungeStore.getState()
    if (seatedDeskId !== null) {
      this.seatAt(seatedDeskId)
    }
    // 서버 입장(join)에 쓸 첫 위치. 앉은 채로 시작해도 입장할 수 있게 여기서 한 번 알린다
    this.reportPosition(false)
    this.fitCamera()
  }

  update(_time: number, deltaMs: number) {
    this.remoteCats.update(deltaMs, this.cameras.main.zoom)
    this.lightDesks()

    const state = loungeStore.getState()
    const walking =
      state.view === 'lounge' && state.seatedDeskId === null && !isTyping()
    if (!walking) {
      this.catBody.setVelocity(0, 0)
      return
    }
    const moving = this.move()
    this.cat.setDepth(this.cat.y)
    this.reportPosition(moving)
    this.updateNearbyDesk()
  }

  private saveCurrentPosition = () => {
    if (loungeStore.getState().seatedDeskId === null) {
      savePosition({ x: Math.round(this.cat.x), y: Math.round(this.cat.y) })
    }
  }

  private reportPosition(moving: boolean) {
    loungeConnection.reportPosition(
      {
        x: Math.round(this.cat.x),
        y: Math.round(this.cat.y),
        direction: this.direction,
      },
      moving,
    )
  }

  private createRoomTextures() {
    const room = this.textures.createCanvas(
      TEXTURE.room,
      WORLD_WIDTH,
      WORLD_HEIGHT,
    )!
    drawRoom(room.getContext(), tokenColor)
    room.refresh()

    for (const [key, lit] of [
      [TEXTURE.deskOff, false],
      [TEXTURE.deskOn, true],
    ] as const) {
      const desk = this.textures.createCanvas(
        key,
        DESK_WIDTH,
        DESK_SPRITE_HEIGHT,
      )!
      drawDesk(desk.getContext(), tokenColor, lit)
      desk.refresh()
    }
  }

  private createMyCat() {
    const spawn = readSavedPosition() ?? DEFAULT_SPAWN
    const view = createCatView(
      this,
      this.appearance.coat,
      '나',
      true,
      spawn.x,
      spawn.y,
    )
    this.cat = view.container
    this.catSprite = view.sprite
    this.nameTag = view.nameTag
    this.physics.add.existing(this.cat)
    this.catBody = this.cat.body as Phaser.Physics.Arcade.Body
    // 부딪히는 몸통은 발밑 8x4 만. 머리는 책상·화분 뒤로 지나갈 수 있어 탑다운에서 자연스럽다
    this.catBody.setSize(8, 4).setOffset(-4, -4).setCollideWorldBounds(true)
  }

  // @return 이번 프레임에 움직였는지
  private move(): boolean {
    const left = this.keys.A.isDown || this.keys.LEFT.isDown
    const right = this.keys.D.isDown || this.keys.RIGHT.isDown
    const up = this.keys.W.isDown || this.keys.UP.isDown
    const down = this.keys.S.isDown || this.keys.DOWN.isDown
    const velocityX = (right ? 1 : 0) - (left ? 1 : 0)
    const velocityY = (down ? 1 : 0) - (up ? 1 : 0)
    // 대각선도 같은 속도로 걷도록 길이를 1로 맞춘다
    const length = Math.hypot(velocityX, velocityY) || 1
    this.catBody.setVelocity(
      (velocityX / length) * WALK_SPEED,
      (velocityY / length) * WALK_SPEED,
    )

    if (velocityX === 0 && velocityY === 0) {
      this.catSprite.anims.stop()
      this.catSprite.setFrame(frameName(this.direction, 0))
      return false
    }
    // 옆으로 걷는 중이면 옆모습, 아니면 위·아래
    this.direction =
      velocityX !== 0
        ? velocityX > 0
          ? 'right'
          : 'left'
        : velocityY > 0
          ? 'down'
          : 'up'
    this.catSprite.anims.play(
      walkAnimationKey(this.appearance.coat, this.direction),
      true,
    )
    return true
  }

  private updateNearbyDesk() {
    // 남이 앉아 있는 책상에는 [F]를 띄우지 않는다
    const occupied = occupiedDeskIds()
    const nearest = DESKS.filter((desk) => !occupied.has(desk.id))
      .map((desk) => {
        const spot = deskFrontSpot(desk)
        return {
          desk,
          distance: Math.hypot(spot.x - this.cat.x, spot.y - this.cat.y),
        }
      })
      .toSorted((left, right) => left.distance - right.distance)[0]

    if (nearest === undefined || nearest.distance > DESK_REACH) {
      loungeStore.setNearbyDesk(null)
      return
    }
    if (Phaser.Input.Keyboard.JustDown(this.keys.F)) {
      // 같은 순간 두 사람이 F 를 누를 수 있어서, 누가 앉을지는 서버가 정한다
      loungeConnection.requestSit(nearest.desk.id)
      return
    }
    const camera = this.cameras.main
    loungeStore.setNearbyDesk({
      deskId: nearest.desk.id,
      // 툴팁은 책상 노트북 위에 띄운다. 월드 좌표 → 지도 상자 안의 화면 좌표
      screenX: Math.round(
        (nearest.desk.x + DESK_WIDTH / 2 - camera.worldView.x) * camera.zoom,
      ),
      screenY: Math.round(
        (nearest.desk.y + DESK_SPRITE_TOP - camera.worldView.y) * camera.zoom,
      ),
    })
  }

  // 누군가 앉은 책상은 노트북 화면이 켜진다 (나 + 다른 사람)
  private lightDesks() {
    const lit = new Set(this.remoteCats.seatedDeskIds())
    const { seatedDeskId } = loungeStore.getState()
    if (seatedDeskId !== null) {
      lit.add(seatedDeskId)
    }
    this.desks.forEach((desk, deskId) => {
      const texture = lit.has(deskId) ? TEXTURE.deskOn : TEXTURE.deskOff
      if (desk.texture.key !== texture) {
        desk.setTexture(texture)
      }
    })
  }

  private onStoreChange() {
    const state = loungeStore.getState()
    const previous = this.previousState
    this.previousState = state
    if (state.seatedDeskId !== previous.seatedDeskId) {
      if (previous.seatedDeskId !== null) {
        this.standUpFrom(previous.seatedDeskId)
      }
      if (state.seatedDeskId !== null) {
        this.seatAt(state.seatedDeskId)
      }
    }
    if (state.view !== previous.view) {
      this.fitCamera()
    }
  }

  private seatAt(deskId: number) {
    const desk = DESKS[deskId]
    const seat = seatPosition(desk)
    this.catSprite.anims.stop()
    this.catSprite.setFrame(frameName('sit', 0))
    this.catBody.setVelocity(0, 0)
    this.catBody.enable = false
    this.cat.setPosition(seat.x, seat.y)
    // 앉은 고양이는 책상보다 앞에 그려야 앞발이 책상 위에 보인다. 아래 몸은 시트에 없어서 책상에 가려진 것처럼 보인다
    this.cat.setDepth(desk.y + DESK_BODY_HEIGHT + 0.5)
    this.centerCameraOnCat()
  }

  private standUpFrom(deskId: number) {
    const spot = deskFrontSpot(DESKS[deskId])
    this.catBody.enable = true
    this.catBody.reset(spot.x, spot.y)
    this.cat.setPosition(spot.x, spot.y).setDepth(spot.y)
    this.direction = 'down'
    this.catSprite.setFrame(frameName('down', 0))
    this.reportPosition(false)
  }

  /**
   * 화면 모드에 맞춰 카메라를 맞춘다.
   *  - lounge: 방 전체가 보이는 가장 큰 정수배로 키우고 가운데 둔다. 정수배여야 도트 하나가 화면 픽셀 n x n 으로 고르게 커진다
   *  - panel: 2배로 키우고 내 고양이를 가운데 둔다 (왼쪽 280px 패널)
   */
  private fitCamera() {
    const camera = this.cameras.main
    const { width, height } = this.scale
    if (width === 0 || height === 0) {
      return
    }
    if (loungeStore.getState().view === 'lounge') {
      const zoom = Math.max(
        1,
        Math.floor(Math.min(width / WORLD_WIDTH, height / WORLD_HEIGHT)),
      )
      camera.setZoom(zoom)
      camera.removeBounds()
      camera.centerOn(WORLD_WIDTH / 2, WORLD_HEIGHT / 2)
    } else {
      camera.setZoom(PANEL_ZOOM)
      camera.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT)
      this.centerCameraOnCat()
    }
    // 이름표 글자를 확대 배율만큼 촘촘하게 그려 두어야 카메라가 키워도 흐려지지 않는다
    const resolution = camera.zoom * window.devicePixelRatio
    this.nameTag.setResolution(resolution)
    this.remoteCats.setNameResolution(resolution)
  }

  private centerCameraOnCat() {
    if (loungeStore.getState().view === 'panel') {
      this.cameras.main.centerOn(this.cat.x, this.cat.y - FRAME_SIZE / 2)
    }
  }
}

// 입력칸·에디터에 글자를 치는 중에는 고양이가 움직이지 않게 한다
function isTyping() {
  const element = document.activeElement
  return (
    element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement ||
    (element instanceof HTMLElement && element.isContentEditable)
  )
}
