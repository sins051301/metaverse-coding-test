import confetti from 'canvas-confetti'

const COLORS = [
  '#f4b483',
  '#e8925a',
  '#f3d36b',
  '#8fd48a',
  '#efe3c8',
  '#e8a3a3',
]
const DURATION_MS = 1500

const pixelPiece: confetti.Options = {
  shapes: ['square'],
  flat: true,
  scalar: 1.2,
  colors: COLORS,
  zIndex: 1000,
  disableForReducedMotion: true,
}

export function celebrate() {
  void confetti({
    ...pixelPiece,
    particleCount: 140,
    spread: 100,
    startVelocity: 45,
    origin: { x: 0.5, y: 0.6 },
  })

  const end = Date.now() + DURATION_MS
  const fireFromSides = () => {
    void confetti({
      ...pixelPiece,
      particleCount: 3,
      angle: 60,
      spread: 60,
      origin: { x: 0, y: 0.8 },
    })
    void confetti({
      ...pixelPiece,
      particleCount: 3,
      angle: 120,
      spread: 60,
      origin: { x: 1, y: 0.8 },
    })
    if (Date.now() < end) requestAnimationFrame(fireFromSides)
  }
  fireFromSides()
}
