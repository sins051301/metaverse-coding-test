import Phaser from 'phaser'
import type { AvatarAppearance } from '@/entities/avatar/model/appearance'
import { LoungeScene } from '@/widgets/lounge-map/lib/LoungeScene'

/**
 * Phaser 게임 하나를 parent 상자 안에 만든다. 이 파일은 LoungeMap 이 동적 import 로 불러와서,
 * Phaser(약 1MB)가 첫 화면 번들에 섞이지 않고 지도가 처음 뜰 때 따로 내려받아진다.
 */
export function startLoungeGame(
  parent: HTMLElement,
  appearance: AvatarAppearance,
) {
  return new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    // 바탕은 CSS 가 칠한다 (Phaser 는 oklch 색을 모른다)
    transparent: true,
    // 도트를 키울 때 번지지 않게 가장 가까운 픽셀로 확대하고, 좌표를 정수로 반올림한다
    pixelArt: true,
    scale: {
      // 지도 상자의 크기를 그대로 따라간다. 라운지(전체 화면) ↔ 패널(280px)로 바뀌어도 게임을 다시 만들지 않는다
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
