// int 로 더해서 큰 값에서 오버플로가 난다.
class Solution {
    public long solution(int[][] grid) {
        int sum = 0;
        for (int row = 0; row < grid.length; row++) {
            for (int column = 0; column < grid[row].length; column++) {
                if (row == 0 || row == grid.length - 1 || column == 0 || column == grid[row].length - 1) sum += grid[row][column];
            }
        }
        return sum;
    }
}
