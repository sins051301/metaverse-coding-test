// 최댓값을 0에서 시작해서 모든 수가 음수일 때 0을 돌려준다.
class Solution {
    public long solution(int[] numbers) {
        long best = 0;
        long current = 0;
        for (int number : numbers) {
            current = Math.max(0, current + number);
            best = Math.max(best, current);
        }
        return best;
    }
}
