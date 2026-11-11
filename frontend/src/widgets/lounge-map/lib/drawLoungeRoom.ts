import {
  BOARD,
  DESK_BODY_HEIGHT,
  DESK_SPRITE_HEIGHT,
  DESK_SPRITE_TOP,
  DESK_WIDTH,
  FLOOR_CELL,
  RUG,
  WALL_HEIGHT,
  WINDOWS,
  WORLD_HEIGHT,
  WORLD_WIDTH,
} from '@/widgets/lounge-map/model/loungeMap'

type ColorOf = (token: string) => string

// 목업의 도트 지도를 그대로 옮긴 방 배경: 체크무늬 나무 바닥, 벽, 창문, 전광판, 러그. 책상은 따로 그린다(앞뒤 순서 때문에)
export function drawRoom(context: CanvasRenderingContext2D, colorOf: ColorOf) {
  function rect(
    token: string,
    x: number,
    y: number,
    width: number,
    height: number,
  ) {
    context.fillStyle = colorOf(token)
    context.fillRect(x, y, width, height)
  }

  for (
    let row = WALL_HEIGHT / FLOOR_CELL;
    row < WORLD_HEIGHT / FLOOR_CELL;
    row++
  ) {
    for (let column = 0; column < WORLD_WIDTH / FLOOR_CELL; column++) {
      rect(
        (row + column) % 2 ? '--map-floor-a' : '--map-floor-b',
        column * FLOOR_CELL,
        row * FLOOR_CELL,
        FLOOR_CELL,
        FLOOR_CELL,
      )
    }
  }
  rect('--map-rug-edge', RUG.x, RUG.y, RUG.width, RUG.height)
  rect('--map-rug', RUG.x + 2, RUG.y + 2, RUG.width - 4, RUG.height - 4)
  rect('--map-wall', 0, 0, WORLD_WIDTH, WALL_HEIGHT)
  rect('--map-wall-edge', 0, WALL_HEIGHT - 2, WORLD_WIDTH, 2)
  for (const window of WINDOWS) {
    rect('--map-frame', window.x, window.y, window.width, window.height)
    rect(
      '--map-sky',
      window.x + 2,
      window.y + 2,
      window.width - 4,
      window.height - 4,
    )
    rect('--map-sky-light', window.x + 2, window.y + 2, 12, 5)
  }
  rect('--map-frame', BOARD.x, BOARD.y, BOARD.width, BOARD.height)
}

// 책상 하나 (노트북 포함). lit = 누군가 앉아 노트북 화면이 켜졌는지
export function drawDesk(
  context: CanvasRenderingContext2D,
  colorOf: ColorOf,
  lit: boolean,
) {
  function rect(
    token: string,
    x: number,
    y: number,
    width: number,
    height: number,
  ) {
    context.fillStyle = colorOf(token)
    context.fillRect(x, y - DESK_SPRITE_TOP, width, height)
  }
  context.clearRect(0, 0, DESK_WIDTH, DESK_SPRITE_HEIGHT)
  rect('--map-desk-top', 0, 0, DESK_WIDTH, 6)
  rect('--map-desk-front', 0, 6, DESK_WIDTH, DESK_BODY_HEIGHT - 6)
  rect('--map-desk-front', 2, DESK_BODY_HEIGHT, 2, 3)
  rect('--map-desk-front', DESK_WIDTH - 4, DESK_BODY_HEIGHT, 2, 3)
  rect('--map-laptop', 16, -5, 8, 6)
  if (lit) {
    rect('--map-screen', 17, -4, 6, 3)
  }
}
