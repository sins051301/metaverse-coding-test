function solution(numbers) {
    return numbers.length - numbers.filter((number) => number % 2 === 1).length;
}
