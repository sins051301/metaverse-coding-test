// 모든 구간을 확인하는 O(n^2) 풀이. n = 200,000 에서 시간 초과가 나야 한다.
class Solution {
    public long solution(int[] numbers) {
        long best = Long.MIN_VALUE;
        for (int start = 0; start < numbers.length; start++) {
            long sum = 0;
            for (int end = start; end < numbers.length; end++) {
                sum += numbers[end];
                best = Math.max(best, sum);
            }
        }
        return best;
    }
}
