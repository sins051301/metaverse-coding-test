package com.codingtest.judge;

import com.codingtest.problem.TestCase;
import com.codingtest.problem.TestCaseOrder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조회 API 가 돌려주는 테스트 하나의 결과. 히든 테스트는 input·expected·actual·userOutput·errorMessage 가 모두 null 이다.
 */
public record TestResultResponse(
		int testNumber,
		boolean hidden,
		Verdict verdict,
		int timeMs,
		int memoryKb,
		JsonNode input,
		JsonNode expected,
		JsonNode actual,
		String userOutput,
		String errorMessage) {

	/**
	 * 저장된 결과를 화면 순서(예제 먼저, 그 안에서 sequence 순)로 정렬하고 예제에는 입력·기댓값을 붙인다.
	 *
	 * <p>왜 DB 에서 결과와 테스트 케이스를 JOIN + ORDER BY 로 한 번에 읽지 않나:
	 * test_cases.input 에 1.7MB짜리 행이 있어서, 조인 결과를 정렬하면 MySQL 정렬 버퍼(256KB)를 넘어 "Out of sort memory"로 실패한다 (README 8장).
	 * 그래서 (1) 순서만 담은 가벼운 목록(id, hidden)과 (2) 예제 테스트(입력이 작다)를 따로 읽어 메모리에서 합친다.
	 *
	 * @param results  DB 에 저장된 결과 (순서 상관없음)
	 * @param order    문제의 테스트 케이스 순서 (TestCaseRepository#findOrderByProblemId)
	 * @param examples 문제의 예제 테스트 케이스 (입력·기댓값을 붙이는 데 쓴다)
	 */
	public static List<TestResultResponse> inDisplayOrder(List<? extends StoredTestResult> results, List<TestCaseOrder> order, List<TestCase> examples) {
		Map<Long, StoredTestResult> resultByTestCaseId = results.stream()
				.collect(Collectors.toMap(StoredTestResult::getTestCaseId, Function.identity()));
		Map<Long, TestCase> exampleById = examples.stream()
				.collect(Collectors.toMap(TestCase::getId, Function.identity()));

		List<TestResultResponse> responses = new ArrayList<>();
		for (TestCaseOrder testCase : order) {
			StoredTestResult result = resultByTestCaseId.get(testCase.id());
			if (result == null) {
				// [코드 실행]은 예제만 돌리므로 히든 테스트의 결과가 없다.
				continue;
			}
			TestCase example = exampleById.get(testCase.id());
			responses.add(new TestResultResponse(
					responses.size() + 1,
					testCase.hidden(),
					result.getVerdict(),
					result.getTimeMs(),
					result.getMemoryKb(),
					example == null ? null : example.getInput(),
					example == null ? null : example.getExpected(),
					result.getActual(),
					result.getUserOutput(),
					result.getErrorMessage()));
		}
		return responses;
	}

}
