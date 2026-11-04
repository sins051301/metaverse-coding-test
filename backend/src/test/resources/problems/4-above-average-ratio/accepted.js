function solution(scores) {
    const average = scores.reduce((sum, score) => sum + score, 0) / scores.length;
    return scores.filter((score) => score >= average).length / scores.length;
}
