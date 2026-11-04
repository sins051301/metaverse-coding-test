function solution(s) {
    return s.replace(/(.)\1*/g, (run) => run[0] + run.length);
}
