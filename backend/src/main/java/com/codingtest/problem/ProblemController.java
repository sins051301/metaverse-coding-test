package com.codingtest.problem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

	// 로그인 없이도 열린 API 다. 토큰이 있으면 token 에 신원이 들어오고, 게스트면 null 이다
	@Operation(summary = "문제 목록", description = "로그인했으면 solved 에 내가 푼 문제인지가 들어간다 ([완료] 뱃지)")
	@GetMapping
	public List<ProblemSummaryResponse> findAll(@AuthenticationPrincipal Jwt token) {
		return problemService.findAll(token == null ? null : Long.parseLong(token.getSubject()));
	}

	@Operation(summary = "문제 상세", description = "지문, 파라미터, 시간·메모리 제한, 언어별 시작 코드")
	@GetMapping("/{id}")
	public ProblemDetailResponse getDetail(@PathVariable long id) {
		return problemService.getDetail(id);
	}

}
