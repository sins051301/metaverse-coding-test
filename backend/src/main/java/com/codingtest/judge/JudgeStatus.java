package com.codingtest.judge;

/**
 * 제출·실행 한 건의 채점 진행 상태. 흐름: PENDING → JUDGING → DONE 또는 FAILED.
 *
 * <ul>
 *   <li>PENDING: 접수만 됨. 작업 스레드가 아직 집어 가지 않음</li>
 *   <li>JUDGING: 작업 스레드가 채점 중 (Docker 컨테이너 실행 중)</li>
 *   <li>DONE: 채점 끝. 판정(AC/WA/.../CE)이 있다. 오답이나 컴파일 에러도 "채점은 정상으로 끝났다"는 뜻이라 DONE 이다</li>
 *   <li>FAILED: 우리 시스템 문제(Docker 가 응답하지 않음 등)로 채점 자체를 못 함. 판정이 없다</li>
 * </ul>
 *
 * 서버가 채점 도중 꺼지면 DB 에 PENDING·JUDGING 이 남는다. 서버가 다시 켜질 때 이 둘을 찾아 다시 채점한다 (JudgeRecovery).
 */
public enum JudgeStatus {
	PENDING, JUDGING, DONE, FAILED
}
