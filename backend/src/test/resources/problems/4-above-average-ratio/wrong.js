// 비율을 소수 둘째 자리에서 반올림해 오차가 1e-6을 넘는다.
function solution(scores) {
    const average = scores.reduce((sum, score) => sum + score, 0) / scores.length;
    return Math.round(scores.filter((score) => score >= average).length / scores.length * 100) / 100;
}
