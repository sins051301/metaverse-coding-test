import Phaser from 'phaser'
import type { AvatarAppearance } from '@/entities/avatar/model/appearance'
import { LoungeScene } from '@/widgets/lounge-map/lib/LoungeScene'

export function startLoungeGame(
  parent: HTMLElement,
  appearance: AvatarAppearance,
) {
  return new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    transparent: true,
    pixelArt: true,
    scale: {
      mode: Phaser.Scale.RESIZE,
      width: parent.clientWidth,
      height: parent.clientHeight,
    },
    physics: {
      default: 'arcade',
      arcade: { debug: false },
    },
    scene: new LoungeScene(appearance),
    banner: false,
  })
}
