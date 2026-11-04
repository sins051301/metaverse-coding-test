package com.codingtest.problem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "문제")
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

	private final ProblemService problemService;

	public ProblemController(ProblemService problemService) {
		this.problemService = problemService;
	}

	@Operation(summary = "문제 목록")
	@GetMapping
	public List<ProblemSummaryResponse> findAll() {
		return problemService.findAll();
	}

	@Operation(summary = "문제 상세", description = "지문, 파라미터, 시간·메모리 제한, 언어별 시작 코드")
	@GetMapping("/{id}")
	public ProblemDetailResponse getDetail(@PathVariable long id) {
		return problemService.getDetail(id);
	}

}
