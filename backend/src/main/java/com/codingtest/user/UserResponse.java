package com.codingtest.user;

/**
 * @param totalSolved 푼 문제 수 (user_solved_problems 행 개수)
 */
public record UserResponse(long id, String nickname, String email, long totalSolved) {
}
