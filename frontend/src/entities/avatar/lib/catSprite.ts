import type { CoatPalette } from '@/entities/avatar/model/appearance'

/**
 * 고양이 도트를 스프라이트 시트 한 장으로 그린다. Phaser 는 이 시트를 프레임 이름(`down-0` 등)으로만 쓴다.
 *
 * 시트 배치 규칙 (이 규칙만 지키면 그림을 아무리 고쳐도 게임 코드는 그대로다)
 *  - 칸 하나는 FRAME_SIZE x FRAME_SIZE. 고양이는 칸 아래 가운데에 발이 닿게 그린다 (스프라이트 원점 = 아래 가운데)
 *  - 줄 = 자세(POSES), 칸 = 그 자세의 프레임 번호
 *  - 칸을 키워(예: 24) 더 자세히 그리려면 FRAME_SIZE 와 템플릿만 바꾸면 된다
 *
 * 템플릿 글자: f 털, d 짙은 털(줄무늬·꼬리 끝), w 주둥이·앞발, e 눈, p 귀 안쪽, n 코, . 빈칸
 * 같은 템플릿에 팔레트만 바꿔 끼워 털색을 만든다. 꾸미기 아이템도 같은 칸 격자에 레이어로 겹쳐 그리면 된다.
 */
export const FRAME_SIZE = 16

export const POSES = {
  down: 2,
  up: 2,
  left: 2,
  right: 2,
  sit: 3,
} as const
export type Pose = keyof typeof POSES

export function frameName(pose: Pose, index: number) {
  return `${pose}-${index}`
}

const SHEET_COLUMNS = Math.max(...Object.values(POSES))
export const SHEET_WIDTH = SHEET_COLUMNS * FRAME_SIZE
export const SHEET_HEIGHT = Object.keys(POSES).length * FRAME_SIZE

// 앞모습 (목업 도트 그대로). 머리가 큰 2등신, 10x10
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

// 오른쪽을 보는 옆모습. 왼쪽은 이것을 좌우로 뒤집어 그린다
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

// 앉은 모습은 앞모습의 어깨까지만 그린다. 아래는 책상에 가려지고, 앞발만 책상 위에 올린다
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

// 앞·뒷모습 공통: 템플릿을 칸 가운데(x 3~12)에 두고 발이 칸 바닥(y 15)에 닿게 한다
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
    // 꼬리: 몸 오른쪽에서 위로 말려 올라가고, 프레임마다 끝이 한 칸씩 흔들린다
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
  // 꼬리는 엉덩이(옆모습의 왼쪽 끝)에서 위로 선다
  const tailX = mirror ? left + width : left - 1
  return [
    ...cells,
    ...block(tailX, tailTop + 1, 1, 3, 'f'),
    ...block(tailX, tailTop, 1, 1, 'd'),
  ]
}

// typingPaw: 0 = 둘 다 내려놓음, 1 = 왼발 듦, 2 = 오른발 듦 (타이핑할 때 번갈아)
function seated(typingPaw: number): Cell[] {
  const pawRow = BODY_TOP + SEATED_ROWS
  return [
    ...cellsOf(FRONT.slice(0, SEATED_ROWS), BODY_LEFT, BODY_TOP),
    ...block(BODY_LEFT + 2, pawRow - (typingPaw === 1 ? 1 : 0), 2, 1, 'w'),
    ...block(BODY_LEFT + 6, pawRow - (typingPaw === 2 ? 1 : 0), 2, 1, 'w'),
  ]
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
  }
}

/**
 * @param resolveColor 색 토큰 이름(--cat-ginger)을 실제 CSS 색으로 바꾼다
 */
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

// 앉은 고양이를 책상에 맞춰 놓을 때 쓴다: 칸 바닥(원점)에서 템플릿 꼭대기까지의 높이
export const SEATED_TEMPLATE_TOP_FROM_ORIGIN = FRAME_SIZE - BODY_TOP
export const BODY_CENTER_OFFSET = FRAME_SIZE / 2 - BODY_LEFT
