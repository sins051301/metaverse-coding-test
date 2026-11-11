package com.codingtest.judge;

import tools.jackson.databind.JsonNode;

/**
 * DB 에 저장된 테스트 하나의 결과. 제출 결과(SubmissionTestResult)와 실행 결과(RunTestResult)가 같은 모양이라,
 * 조회 응답(TestResultResponse)을 만드는 코드를 하나로 쓰려고 공통으로 둔다.
 */
public interface StoredTestResult {

	// DB 에는 출력·에러를 앞 1KB(1024자)만 저장한다. 유저가 출력을 수 MB 찍어도 행이 커지지 않게 하려는 것이다.
	int MAX_STORED_TEXT_LENGTH = 1024;

	long getTestCaseId();

	Verdict getVerdict();

	int getTimeMs();

	int getMemoryKb();

	JsonNode getActual();

	String getUserOutput();

	String getErrorMessage();

	/**
	 * 빈 문자열은 NULL 로, 긴 문자열은 앞 1KB 만 남긴다.
	 */
	static String truncate(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		return text.length() <= MAX_STORED_TEXT_LENGTH ? text : text.substring(0, MAX_STORED_TEXT_LENGTH);
	}

	static int toMilliseconds(double seconds) {
		return (int) Math.round(seconds * 1000);
	}

}
