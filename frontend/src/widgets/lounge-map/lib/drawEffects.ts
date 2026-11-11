type ColorOf = (token: string) => string

export const BUBBLE_WIDTH = 10
export const BUBBLE_HEIGHT = 6

export function drawBubble(
  context: CanvasRenderingContext2D,
  colorOf: ColorOf,
  dotIndex: number | null,
  failure: boolean,
) {
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
  const face = failure ? '--cat-grey' : '--map-frame'
  rect(face, 0, 0, 9, 5)
  rect(face, 7, 5, 2, 1)
  rect(face, 9, 4, 1, 1)
  for (let dot = 0; dot < 3; dot++) {
    rect(dot === dotIndex ? '--map-pot' : '--map-laptop', 2 + dot * 2, 2, 1, 1)
  }
}

export const Z_SIZE = 3

export function drawZ(context: CanvasRenderingContext2D, colorOf: ColorOf) {
  context.fillStyle = colorOf('--map-frame')
  context.fillRect(0, 0, 3, 1)
  context.fillRect(1, 1, 1, 1)
  context.fillRect(0, 2, 3, 1)
}

export const STAR_WIDTH = 8
export const STAR_HEIGHT = 4

export function drawStar(context: CanvasRenderingContext2D, colorOf: ColorOf) {
  context.fillStyle = colorOf('--map-star')
  context.fillRect(3, 0, 2, 1)
  context.fillRect(1, 1, 6, 1)
  context.fillRect(2, 2, 4, 1)
  context.fillRect(2, 3, 1, 1)
  context.fillRect(5, 3, 1, 1)
}

const GLYPHS: Record<string, string[]> = {
  S: ['111', '100', '111', '001', '111'],
  O: ['111', '101', '101', '101', '111'],
  L: ['100', '100', '100', '100', '111'],
  V: ['101', '101', '101', '101', '010'],
  E: ['111', '100', '111', '100', '111'],
  D: ['110', '101', '101', '101', '110'],
  '!': ['1', '1', '1', '0', '1'],
}
const SOLVED_WORD = 'SOLVED!'
const SOLVED_GLYPHS = [...SOLVED_WORD].map((letter) => GLYPHS[letter])
const SOLVED_TEXT_WIDTH = SOLVED_GLYPHS.reduce(
  (sum, glyph) => sum + glyph[0].length + 1,
  -1,
)
export const SOLVED_WIDTH = SOLVED_TEXT_WIDTH + 4
export const SOLVED_HEIGHT = 9

export function drawSolved(
  context: CanvasRenderingContext2D,
  colorOf: ColorOf,
) {
  context.fillStyle = colorOf('--map-frame')
  context.fillRect(0, 0, SOLVED_WIDTH, SOLVED_HEIGHT)
  context.fillStyle = colorOf('--map-pot')
  let cursor = 2
  for (const glyph of SOLVED_GLYPHS) {
    glyph.forEach((row, rowIndex) =>
      [...row].forEach((cell, columnIndex) => {
        if (cell === '1') {
          context.fillRect(cursor + columnIndex, 2 + rowIndex, 1, 1)
        }
      }),
    )
    cursor += glyph[0].length + 1
  }
}
