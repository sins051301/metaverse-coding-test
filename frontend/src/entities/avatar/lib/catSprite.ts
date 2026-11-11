import type { CoatPalette } from '@/entities/avatar/model/appearance'

export const FRAME_SIZE = 16

export const POSES = {
  down: 2,
  up: 2,
  left: 2,
  right: 2,
  sit: 3,
  nap: 1,
} as const
export type Pose = keyof typeof POSES

export function frameName(pose: Pose, index: number) {
  return `${pose}-${index}`
}

const SHEET_COLUMNS = Math.max(...Object.values(POSES))
export const SHEET_WIDTH = SHEET_COLUMNS * FRAME_SIZE
export const SHEET_HEIGHT = Object.keys(POSES).length * FRAME_SIZE

const FRONT = [
  '..f....f..',
  '.fpf..fpf.',
  '.ffdddfff.',
  'ffffffffff',
  'ffeffffeff',
  'ffwwnnwwff',
  '.fwwwwwwf.',
  '..ffffff..',
  '.ffffffff.',
  '.ffffffff.',
]

const BACK = [
  '..f....f..',
  '.fdf..fdf.',
  '.ffdddfff.',
  'ffffffffff',
  'ffffffffff',
  'ffffffffff',
  '.ffffffff.',
  '..ffffff..',
  '.ffffffff.',
  '.ffffffff.',
]

const SIDE_RIGHT = [
  '......f..f..',
  '.....fpffpf.',
  '.....fffffff',
  '.....ffffeff',
  '.....fffffwn',
  '.ffffffffww.',
  'fffffffffff.',
  'fffffffff...',
  '.ffffffff...',
]

const SEATED_ROWS = 9

type Cell = { x: number; y: number; letter: string }

function cellsOf(
  template: string[],
  offsetX: number,
  offsetY: number,
  mirror = false,
): Cell[] {
  const width = template[0].length
  return template.flatMap((row, rowIndex) =>
    [...row].flatMap((letter, columnIndex) =>
      letter === '.'
        ? []
        : [
            {
              x: offsetX + (mirror ? width - 1 - columnIndex : columnIndex),
              y: offsetY + rowIndex,
              letter,
            },
          ],
    ),
  )
}

function block(
  x: number,
  y: number,
  width: number,
  height: number,
  letter: string,
): Cell[] {
  const cells: Cell[] = []
  for (let row = 0; row < height; row++) {
    for (let column = 0; column < width; column++) {
      cells.push({ x: x + column, y: y + row, letter })
    }
  }
  return cells
}

const BODY_LEFT = 3
const BODY_TOP = 4

function standingFrontOrBack(template: string[], frame: number): Cell[] {
  const legsTop = BODY_TOP + template.length
  const leftLegHeight = frame === 0 ? 2 : 1
  const rightLegHeight = frame === 0 ? 1 : 2
  const wag = frame
  return [
    ...cellsOf(template, BODY_LEFT, BODY_TOP),
    ...block(BODY_LEFT + 1, legsTop, 2, leftLegHeight, 'f'),
    ...block(BODY_LEFT + 7, legsTop, 2, rightLegHeight, 'f'),
    ...block(BODY_LEFT + 10, BODY_TOP + 8, 1, 2, 'f'),
    ...block(BODY_LEFT + 11, BODY_TOP + 6 + wag, 1, 3, 'f'),
    ...block(BODY_LEFT + 12, BODY_TOP + 5 + wag, 1, 2, 'd'),
  ]
}

function standingSide(frame: number, mirror: boolean): Cell[] {
  const width = SIDE_RIGHT[0].length
  const left = 2
  const top = 15 - SIDE_RIGHT.length - 1
  const legColumns = frame === 0 ? [1, 7] : [2, 6]
  const tailTop = top + (frame === 0 ? 2 : 1)
  const cells = [
    ...cellsOf(SIDE_RIGHT, 0, 0),
    ...legColumns.flatMap((column) =>
      block(column, SIDE_RIGHT.length, 2, 2, 'f'),
    ),
  ].map((cell) => ({
    ...cell,
    x: left + (mirror ? width - 1 - cell.x : cell.x),
    y: top + cell.y,
  }))
  const tailX = mirror ? left + width : left - 1
  return [
    ...cells,
    ...block(tailX, tailTop + 1, 1, 3, 'f'),
    ...block(tailX, tailTop, 1, 1, 'd'),
  ]
}

function seated(typingPaw: number): Cell[] {
  const pawRow = BODY_TOP + SEATED_ROWS
  return [
    ...cellsOf(FRONT.slice(0, SEATED_ROWS), BODY_LEFT, BODY_TOP),
    ...block(BODY_LEFT + 2, pawRow - (typingPaw === 1 ? 1 : 0), 2, 1, 'w'),
    ...block(BODY_LEFT + 6, pawRow - (typingPaw === 2 ? 1 : 0), 2, 1, 'w'),
  ]
}

function napping(): Cell[] {
  return seated(0).map((cell) =>
    cell.letter === 'e' ? { ...cell, letter: 'c' } : cell,
  )
}

function cellsFor(pose: Pose, frame: number): Cell[] {
  switch (pose) {
    case 'down':
      return standingFrontOrBack(FRONT, frame)
    case 'up':
      return standingFrontOrBack(BACK, frame)
    case 'right':
      return standingSide(frame, false)
    case 'left':
      return standingSide(frame, true)
    case 'sit':
      return seated(frame)
    case 'nap':
      return napping()
  }
}

export function drawCatSheet(
  context: CanvasRenderingContext2D,
  palette: CoatPalette,
  resolveColor: (token: string) => string,
) {
  const colors: Record<string, string> = {
    f: resolveColor(palette.fur),
    d: resolveColor(palette.furDark),
    w: resolveColor(palette.muzzle),
    e: resolveColor(palette.eye),
    c: resolveColor(
      palette.furDark === palette.fur ? '--cat-grey-dark' : palette.furDark,
    ),
    p: resolveColor('--cat-ear'),
    n: resolveColor('--cat-nose'),
  }
  context.clearRect(0, 0, SHEET_WIDTH, SHEET_HEIGHT)
  Object.entries(POSES).forEach(([pose, frameCount], row) => {
    for (let frame = 0; frame < frameCount; frame++) {
      for (const cell of cellsFor(pose as Pose, frame)) {
        context.fillStyle = colors[cell.letter]
        context.fillRect(
          frame * FRAME_SIZE + cell.x,
          row * FRAME_SIZE + cell.y,
          1,
          1,
        )
      }
    }
  })
}

export const SEATED_TEMPLATE_TOP_FROM_ORIGIN = FRAME_SIZE - BODY_TOP
export const BODY_CENTER_OFFSET = FRAME_SIZE / 2 - BODY_LEFT
