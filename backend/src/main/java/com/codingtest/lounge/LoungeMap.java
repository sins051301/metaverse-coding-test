package com.codingtest.lounge;

/**
 * 서버가 검사하는 라운지 지도 값. 프론트의 frontend/src/widgets/lounge-map/model/loungeMap.ts 와 같아야 한다.
 * 서버는 "지도 밖으로 순간이동"과 "없는 책상에 앉기"만 막고, 벽·책상 충돌은 클라이언트에 맡긴다 (README 10장 "2단계").
 */
final class LoungeMap {

	static final int WORLD_WIDTH = 320;
	static final int WORLD_HEIGHT = 180;
	static final int WALL_HEIGHT = 30;
	static final int DESK_COUNT = 30;

	private LoungeMap() {
	}

	static int clampX(int x) {
		return Math.clamp(x, 0, WORLD_WIDTH);
	}

	// 발 위치는 벽 아래 바닥에만 있을 수 있다
	static int clampY(int y) {
		return Math.clamp(y, WALL_HEIGHT, WORLD_HEIGHT);
	}

	static boolean isDesk(int deskId) {
		return deskId >= 0 && deskId < DESK_COUNT;
	}

}
