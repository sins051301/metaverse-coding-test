class Solution {
    public long solution(int[] numbers) {
        long best = numbers[0];
        long current = 0;
        for (int number : numbers) {
            current = Math.max(number, current + number);
            best = Math.max(best, current);
        }
        return best;
    }
}
