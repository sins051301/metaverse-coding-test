package com.codingtest.judge;

/**
 * 제출·실행을 접수하면 바로 돌려주는 응답(HTTP 202 Accepted). 채점은 아직 시작 전이다.
 * 클라이언트는 이 id 로 조회 API 를 1초쯤 간격으로 불러 status 가 DONE 이 되면 결과를 보여 준다 (폴링).
 */
public record JudgeAcceptedResponse(long id, JudgeStatus status) {
}
