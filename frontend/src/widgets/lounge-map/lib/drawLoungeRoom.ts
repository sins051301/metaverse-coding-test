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
import { vars } from '@/shared/styles/theme.css'

type ColorOf = (token: string) => string

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
        (row + column) % 2 ? vars.map.floorA : vars.map.floorB,
        column * FLOOR_CELL,
        row * FLOOR_CELL,
        FLOOR_CELL,
        FLOOR_CELL,
      )
    }
  }
  rect(vars.map.rugEdge, RUG.x, RUG.y, RUG.width, RUG.height)
  rect(vars.map.rug, RUG.x + 2, RUG.y + 2, RUG.width - 4, RUG.height - 4)
  rect(vars.map.wall, 0, 0, WORLD_WIDTH, WALL_HEIGHT)
  rect(vars.map.wallEdge, 0, WALL_HEIGHT - 2, WORLD_WIDTH, 2)
  for (const window of WINDOWS) {
    rect(vars.map.frame, window.x, window.y, window.width, window.height)
    rect(
      vars.map.sky,
      window.x + 2,
      window.y + 2,
      window.width - 4,
      window.height - 4,
    )
    rect(vars.map.skyLight, window.x + 2, window.y + 2, 12, 5)
  }
  rect(vars.map.frame, BOARD.x, BOARD.y, BOARD.width, BOARD.height)
}

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
  rect(vars.map.deskTop, 0, 0, DESK_WIDTH, 6)
  rect(vars.map.deskFront, 0, 6, DESK_WIDTH, DESK_BODY_HEIGHT - 6)
  rect(vars.map.deskFront, 2, DESK_BODY_HEIGHT, 2, 3)
  rect(vars.map.deskFront, DESK_WIDTH - 4, DESK_BODY_HEIGHT, 2, 3)
  rect(vars.map.laptop, 16, -5, 8, 6)
  if (lit) {
    rect(vars.map.screen, 17, -4, 6, 3)
  }
}
