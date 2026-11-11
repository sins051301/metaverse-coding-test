package com.codingtest.submission;

import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 제출 내역 한 페이지. Spring Data 의 Slice 를 그대로 JSON 으로 내보내지 않고 필요한 두 값만 옮긴다.
 * Slice 를 그대로 직렬화하면 pageable·sort 같은 내부 구조가 응답에 섞이고, Spring Data 버전이 바뀌면 모양도 바뀐다.
 *
 * @param totalCount 이 문제에 낸 내 제출 전체 개수 (화면 머리줄 "N개의 제출"). 첫 페이지(page=0)에서만 세고 다음 페이지는 null 이다.
 *                   프론트는 첫 페이지 값을 계속 쓰므로, [더 보기]마다 COUNT 를 다시 돌릴 필요가 없다
 * @param hasNext 다음 페이지가 있는지. 프론트는 이 값이 true 일 때만 [더 보기]를 보여 준다
 */
public record SubmissionHistoryResponse(List<SubmissionSummary> submissions, Long totalCount, boolean hasNext) {

	static SubmissionHistoryResponse of(Slice<SubmissionSummary> slice, Long totalCount) {
		return new SubmissionHistoryResponse(slice.getContent(), totalCount, slice.hasNext());
	}

}
