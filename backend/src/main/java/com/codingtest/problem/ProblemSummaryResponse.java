package com.codingtest.problem;

public record ProblemSummaryResponse(long id, String title, int level) {

	static ProblemSummaryResponse from(Problem problem) {
		return new ProblemSummaryResponse(problem.getId(), problem.getTitle(), problem.getLevel());
	}

}
