// 첫 행·마지막 행·첫 열·마지막 열을 따로 더해서 모서리를 두 번 센다.
function solution(grid) {
    const last = grid.length - 1;
    let sum = 0;
    for (const value of grid[0]) sum += value;
    for (const value of grid[last]) sum += value;
    for (const row of grid) sum += row[0] + row[row.length - 1];
    return sum;
}
