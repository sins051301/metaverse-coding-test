export const WORLD_WIDTH = 320
export const WORLD_HEIGHT = 180

export const WALL_HEIGHT = 30

export const FLOOR_CELL = 10

export type Rect = { x: number; y: number; width: number; height: number }

export type Desk = {
  id: number
  x: number
  y: number
}

export const DESK_WIDTH = 26
export const DESK_BODY_HEIGHT = 10
export const DESK_SPRITE_TOP = -5
export const DESK_SPRITE_HEIGHT = 18

const DESK_COLUMNS = [22, 68, 114, 180, 226, 272]
const DESK_ROWS = [44, 72, 100, 128, 156]

export const DESKS: Desk[] = DESK_ROWS.flatMap((y, rowIndex) =>
  DESK_COLUMNS.map((x, columnIndex) => ({
    id: rowIndex * DESK_COLUMNS.length + columnIndex,
    x,
    y,
  })),
)

export function deskChairSpot(desk: Desk) {
  return { x: desk.x + DESK_WIDTH / 2, y: desk.y - 2 }
}

export const DESK_REACH = 10

export const WINDOWS: Rect[] = [14, 54, 236, 276].map((x) => ({
  x,
  y: 6,
  width: 30,
  height: 16,
}))

export const BOARD: Rect = { x: 108, y: 5, width: 104, height: 18 }

export const RUG: Rect = { x: 143, y: 146, width: 34, height: 30 }

export type Decoration = { frame: number; x: number; y: number }

export const KENNEY_TILE = 16
export const KENNEY_SPACING = 1

export const DECORATIONS: Decoration[] = [
  { frame: 16, x: 2, y: 32 },
  { frame: 17, x: 302, y: 32 },
  { frame: 17, x: 2, y: 162 },
  { frame: 16, x: 302, y: 162 },
  { frame: 478, x: 143, y: 28 },
  { frame: 480, x: 161, y: 28 },
]

export const DECORATION_BODY_HEIGHT = 6

export const DEFAULT_SPAWN = { x: 160, y: 172 }

export const WALKABLE: Rect = {
  x: 4,
  y: WALL_HEIGHT + 4,
  width: WORLD_WIDTH - 8,
  height: WORLD_HEIGHT - WALL_HEIGHT - 6,
}
