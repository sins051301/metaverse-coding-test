package com.codingtest.problem;

import com.codingtest.judge.Language;

import java.util.List;
import java.util.Map;

public record ProblemDetailResponse(
		long id,
		String title,
		int level,
		String description,
		List<Parameter> parameters,
		String returnType,
		int timeLimitMs,
		int memoryLimitMb,
		Map<Language, String> starterCode) {

	static ProblemDetailResponse from(Problem problem) {
		return new ProblemDetailResponse(
				problem.getId(),
				problem.getTitle(),
				problem.getLevel(),
				problem.getDescription(),
				problem.getParameters().stream().map(parameter -> new Parameter(parameter.getName(), parameter.getType())).toList(),
				problem.getReturnType(),
				problem.getTimeLimitMs(),
				problem.getMemoryLimitMb(),
				StarterCode.of(problem));
	}

	public record Parameter(String name, String type) {
	}

}
