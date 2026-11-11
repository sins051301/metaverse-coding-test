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

const DESK_COLUMNS = [30, 90, 150, 210, 270]
const DESK_ROWS = [60, 100, 140]

export const DESKS: Desk[] = DESK_ROWS.flatMap((y, rowIndex) =>
  DESK_COLUMNS.map((x, columnIndex) => ({
    id: rowIndex * DESK_COLUMNS.length + columnIndex,
    x,
    y,
  })),
)

export function deskFrontSpot(desk: Desk) {
  return { x: desk.x + DESK_WIDTH / 2, y: desk.y + DESK_BODY_HEIGHT + 10 }
}

export const DESK_REACH = 10

export const WINDOWS: Rect[] = [14, 54, 236, 276].map((x) => ({
  x,
  y: 6,
  width: 30,
  height: 16,
}))

export const BOARD: Rect = { x: 108, y: 5, width: 104, height: 18 }

export const RUG: Rect = { x: 120, y: 148, width: 80, height: 28 }

export type Decoration = { frame: number; x: number; y: number }

export const KENNEY_TILE = 16
export const KENNEY_SPACING = 1

export const DECORATIONS: Decoration[] = [
  { frame: 16, x: 2, y: 32 },
  { frame: 17, x: 302, y: 32 },
  { frame: 17, x: 2, y: 162 },
  { frame: 16, x: 302, y: 162 },
  { frame: 478, x: 92, y: 28 },
  { frame: 480, x: 212, y: 28 },
]

export const DECORATION_BODY_HEIGHT = 6

export const DEFAULT_SPAWN = { x: 130, y: 172 }

export const WALKABLE: Rect = {
  x: 4,
  y: WALL_HEIGHT + 4,
  width: WORLD_WIDTH - 8,
  height: WORLD_HEIGHT - WALL_HEIGHT - 6,
}
