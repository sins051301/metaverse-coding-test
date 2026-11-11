import Phaser from 'phaser'
import { FRAME_SIZE, frameName } from '@/entities/avatar/lib/catSprite'
import type { AvatarAppearance } from '@/entities/avatar/model/appearance'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import { activityTracker } from '@/entities/lounge/model/activityTracker'
import type { Direction } from '@/entities/lounge/api/loungeMessages'
import {
  loungeStore,
  type LoungeState,
} from '@/entities/lounge/model/loungeStore'
import {
  occupiedDeskIds,
  rosterStore,
} from '@/entities/lounge/model/rosterStore'
import {
  applyCatLook,
  type CatView,
  createCatView,
  FAILURE_SHOW_MS,
  SOLVED_SHOW_MS,
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
  BOARD,
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

const WALK_SPEED = 45
const PANEL_ZOOM = 2
const BOARD_FONT_SIZE = 9
const BOARD_MINIMUM_FONT_SIZE = 6
const BOARD_PADDING = 3

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

export class LoungeScene extends Phaser.Scene {
  private cat!: Phaser.GameObjects.Container
  private catBody!: Phaser.Physics.Arcade.Body
  private catSprite!: Phaser.GameObjects.Sprite
  private nameTag!: Phaser.GameObjects.Text
  private myView!: CatView
  private boardText!: Phaser.GameObjects.Text
  private boardMessage = ''
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
    obstacles.add(
      this.add.zone(WORLD_WIDTH / 2, WALL_HEIGHT / 2, WORLD_WIDTH, WALL_HEIGHT),
    )

    for (const desk of DESKS) {
      const image = this.add
        .image(desk.x, desk.y + DESK_SPRITE_TOP, TEXTURE.deskOff)
        .setOrigin(0, 0)
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
    this.createBoard()
    this.physics.world.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT)
    this.physics.add.collider(this.cat, obstacles)

    const keyboard = this.input.keyboard!
    this.keys = keyboard.addKeys('W,A,S,D,UP,DOWN,LEFT,RIGHT,F', false) as Keys

    this.scale.on(Phaser.Scale.Events.RESIZE, () => this.fitCamera())
    this.unsubscribe = loungeStore.subscribe(() => this.onStoreChange())
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.unsubscribe()
      window.removeEventListener('beforeunload', this.saveCurrentPosition)
    })
    window.addEventListener('beforeunload', this.saveCurrentPosition)

    const { seatedDeskId } = loungeStore.getState()
    if (seatedDeskId !== null) {
      this.seatAt(seatedDeskId)
    }
    this.reportPosition(false)
    this.fitCamera()
  }

  update(time: number, deltaMs: number) {
    this.remoteCats.update(time, deltaMs, this.cameras.main.zoom)
    this.lightDesks()
    this.applyMyLook(time)
    this.updateBoard()

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
    this.myView = view
    this.cat = view.container
    this.catSprite = view.sprite
    this.nameTag = view.nameTag
    this.physics.add.existing(this.cat)
    this.catBody = this.cat.body as Phaser.Physics.Arcade.Body
    this.catBody.setSize(8, 4).setOffset(-4, -4).setCollideWorldBounds(true)
  }

  private move(): boolean {
    const left = this.keys.A.isDown || this.keys.LEFT.isDown
    const right = this.keys.D.isDown || this.keys.RIGHT.isDown
    const up = this.keys.W.isDown || this.keys.UP.isDown
    const down = this.keys.S.isDown || this.keys.DOWN.isDown
    const velocityX = (right ? 1 : 0) - (left ? 1 : 0)
    const velocityY = (down ? 1 : 0) - (up ? 1 : 0)
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
      loungeConnection.requestSit(nearest.desk.id)
      return
    }
    const camera = this.cameras.main
    loungeStore.setNearbyDesk({
      deskId: nearest.desk.id,
      screenX: Math.round(
        (nearest.desk.x + DESK_WIDTH / 2 - camera.worldView.x) * camera.zoom,
      ),
      screenY: Math.round(
        (nearest.desk.y + DESK_SPRITE_TOP - camera.worldView.y) * camera.zoom,
      ),
    })
  }

  private lightDesks() {
    const lit = new Set(this.remoteCats.litDeskIds())
    const { seatedDeskId } = loungeStore.getState()
    if (seatedDeskId !== null && activityTracker.get() !== 'napping') {
      lit.add(seatedDeskId)
    }
    this.desks.forEach((desk, deskId) => {
      const texture = lit.has(deskId) ? TEXTURE.deskOn : TEXTURE.deskOff
      if (desk.texture.key !== texture) {
        desk.setTexture(texture)
      }
    })
  }

  private applyMyLook(time: number) {
    const { seatedDeskId, failedAt } = loungeStore.getState()
    const { myAvatarId, solvedAt } = rosterStore.getState()
    const solvedTime =
      myAvatarId === null ? undefined : solvedAt.get(myAvatarId)
    const now = Date.now()
    applyCatLook(
      this.myView,
      {
        seated: seatedDeskId !== null,
        activity: activityTracker.get(),
        mine: true,
        solved: solvedTime !== undefined && now - solvedTime < SOLVED_SHOW_MS,
        failed:
          failedAt !== null && performance.now() - failedAt < FAILURE_SHOW_MS,
      },
      time,
    )
  }

  private createBoard() {
    this.boardText = this.add
      .text(BOARD.x + BOARD.width / 2, BOARD.y + BOARD.height / 2, '', {
        fontFamily: 'Jua, sans-serif',
        fontSize: `${BOARD_FONT_SIZE}px`,
        color: tokenColor('--color-tag-ink'),
      })
      .setOrigin(0.5, 0.5)
      .setDepth(0)
  }

  private updateBoard() {
    const latest = rosterStore.getState().recentSolves[0]
    const message = latest ? `${latest.nickname}님이 풀었어요` : ''
    if (message === this.boardMessage) {
      return
    }
    this.boardMessage = message
    this.boardText.setText(message)
    for (
      let fontSize = BOARD_FONT_SIZE;
      fontSize > BOARD_MINIMUM_FONT_SIZE &&
      this.boardText.width > BOARD.width - BOARD_PADDING * 2;
      fontSize--
    ) {
      this.boardText.setFontSize(fontSize - 1)
    }
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
    const resolution = camera.zoom * window.devicePixelRatio
    this.nameTag.setResolution(resolution)
    this.boardText.setResolution(resolution)
    this.remoteCats.setNameResolution(resolution)
  }

  private centerCameraOnCat() {
    if (loungeStore.getState().view === 'panel') {
      this.cameras.main.centerOn(this.cat.x, this.cat.y - FRAME_SIZE / 2)
    }
  }
}

function isTyping() {
  const element = document.activeElement
  return (
    element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement ||
    (element instanceof HTMLElement && element.isContentEditable)
  )
}
