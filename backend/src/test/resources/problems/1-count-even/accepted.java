class Solution {
    public int solution(int[] numbers) {
        int count = 0;
        for (int number : numbers) {
            if (number % 2 == 0) count++;
        }
        return count;
    }
}
