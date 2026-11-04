function solution(numbers) {
    let best = -Infinity;
    for (let start = 0; start < numbers.length; start++) {
        let sum = 0;
        for (let end = start; end < numbers.length; end++) {
            sum += numbers[end];
            best = Math.max(best, sum);
        }
    }
    return best;
}
