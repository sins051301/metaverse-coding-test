class Solution {
    public double solution(int[] scores) {
        long total = 0;
        for (int score : scores) total += score;
        int count = 0;
        for (int score : scores) {
            if ((long) score * scores.length >= total) count++;
        }
        return (double) count / scores.length;
    }
}
