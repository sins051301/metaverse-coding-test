function solution(numbers) {
    let best = 0;
    let current = 0;
    for (const number of numbers) {
        current = Math.max(0, current + number);
        best = Math.max(best, current);
    }
    return best;
}
