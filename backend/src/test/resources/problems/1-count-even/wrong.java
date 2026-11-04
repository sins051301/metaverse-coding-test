// 음수 홀수의 나머지는 -1 이라 % 2 == 1 로 세면 음수 홀수를 짝수로 잘못 센다.
class Solution {
    public int solution(int[] numbers) {
        int oddCount = 0;
        for (int number : numbers) {
            if (number % 2 == 1) oddCount++;
        }
        return numbers.length - oddCount;
    }
}
