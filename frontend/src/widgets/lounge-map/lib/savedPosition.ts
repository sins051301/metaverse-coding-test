import { readStorage, writeStorage } from '@/shared/lib/storage'
import { WALKABLE } from '@/widgets/lounge-map/model/loungeMap'

const POSITION_KEY = 'codingtest:v1:loungePosition'

type Position = { x: number; y: number }

export function readSavedPosition(): Position | null {
  const saved = readStorage(localStorage, POSITION_KEY)
  if (saved === null) {
    return null
  }
  try {
    const { x, y } = JSON.parse(saved) as Position
    const inside =
      x >= WALKABLE.x &&
      x <= WALKABLE.x + WALKABLE.width &&
      y >= WALKABLE.y &&
      y <= WALKABLE.y + WALKABLE.height
    return inside ? { x, y } : null
  } catch {
    return null
  }
}

export function savePosition(position: Position) {
  writeStorage(localStorage, POSITION_KEY, JSON.stringify(position))
}
