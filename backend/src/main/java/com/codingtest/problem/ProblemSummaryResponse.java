package com.codingtest.problem;

/**
 * @param solved 로그인한 유저가 이 문제를 AC 받은 적이 있는지. 게스트는 늘 false
 */
public record ProblemSummaryResponse(long id, String title, int level, boolean solved) {

	static ProblemSummaryResponse of(Problem problem, boolean solved) {
		return new ProblemSummaryResponse(problem.getId(), problem.getTitle(), problem.getLevel(), solved);
	}

}
