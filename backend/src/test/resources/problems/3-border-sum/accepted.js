function solution(grid) {
    let sum = 0;
    grid.forEach((row, rowIndex) => row.forEach((value, columnIndex) => {
        if (rowIndex === 0 || rowIndex === grid.length - 1 || columnIndex === 0 || columnIndex === row.length - 1) sum += value;
    }));
    return sum;
}
