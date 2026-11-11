/**
 * 공용 라운지 지도. 단위는 도트(px). 화면에는 이 값을 정수배(카메라 zoom)로 키워 보여 준다.
 *
 * 지도를 Tiled(타일맵 편집기) 대신 코드 데이터로 둔 이유: 방 하나, 책상 15개뿐이라 이 파일 하나로 다 보인다.
 * 방이 늘거나 장식이 많아지면 Tiled JSON 으로 옮긴다 (README 10장 "라운지").
 */
export const WORLD_WIDTH = 320
export const WORLD_HEIGHT = 180

// 위쪽 벽. 창문·전광판이 걸려 있고 걸어 들어갈 수 없다
export const WALL_HEIGHT = 30

export const FLOOR_CELL = 10

export type Rect = { x: number; y: number; width: number; height: number }

export type Desk = {
  id: number
  // 책상 윗면 왼쪽 위
  x: number
  y: number
}

export const DESK_WIDTH = 26
// 윗면 6 + 앞판 4. 다리(3)는 바닥에 닿는 그림일 뿐 부딪히지 않는다
export const DESK_BODY_HEIGHT = 10
// 노트북이 책상 위로 5 튀어나오고, 다리가 아래로 3 내려온다
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

// 책상 앞에서 [F]를 누를 수 있는 자리 (고양이 발 위치 기준). 일어나면 이 자리에 선다
export function deskFrontSpot(desk: Desk) {
  return { x: desk.x + DESK_WIDTH / 2, y: desk.y + DESK_BODY_HEIGHT + 10 }
}

// 발이 이 거리 안에 들어오면 [F] 책상에 앉기 툴팁을 띄운다
export const DESK_REACH = 10

export const WINDOWS: Rect[] = [14, 54, 236, 276].map((x) => ({
  x,
  y: 6,
  width: 30,
  height: 16,
}))

// 다른 사람이 문제를 풀면 자막이 흐를 전광판 (2단계 이후)
export const BOARD: Rect = { x: 108, y: 5, width: 104, height: 18 }

export const RUG: Rect = { x: 120, y: 148, width: 80, height: 28 }

/**
 * Kenney Roguelike Indoors(CC0) 시트에서 가져온 장식. frame = 시트의 타일 번호(왼쪽 위부터 가로로 0, 1, 2 ...)
 * x, y 는 타일(16x16)의 왼쪽 위. 아래쪽 일부만 부딪히게 해서 화분 잎 뒤로 고양이 머리가 지나갈 수 있다
 */
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

// 러그 왼쪽 아래. 어느 책상 앞 자리와도 떨어져 있어 들어오자마자 [F] 툴팁이 뜨지 않는다
export const DEFAULT_SPAWN = { x: 130, y: 172 }

// 걸을 수 있는 바닥 범위 (발 위치 기준). 저장된 위치가 이 밖이면 기본 위치에서 시작한다
export const WALKABLE: Rect = {
  x: 4,
  y: WALL_HEIGHT + 4,
  width: WORLD_WIDTH - 8,
  height: WORLD_HEIGHT - WALL_HEIGHT - 6,
}
