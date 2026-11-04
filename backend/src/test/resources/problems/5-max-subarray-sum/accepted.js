function solution(numbers) {
    let best = numbers[0];
    let current = 0;
    for (const number of numbers) {
        current = Math.max(number, current + number);
        best = Math.max(best, current);
    }
    return best;
}
