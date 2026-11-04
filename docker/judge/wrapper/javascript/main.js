// 채점용 진입점. stdin의 인자 JSON 배열로 solution.js 의 solution 함수를 호출하고,
// {"returnValue":...,"userOutput":"..."} 한 줄을 stdout에 쓴다. 유저의 console 출력은 userOutput으로 모은다.
const fs = require('fs');
const vm = require('vm');

const USER_OUTPUT_LIMIT_CHARACTERS = 65536;

const solutionArguments = JSON.parse(fs.readFileSync(0, 'utf8'));
// 함수 본문으로 컴파일하면 function/const/let 어느 방식으로 선언해도 마지막 줄에서 solution을 꺼낼 수 있고, 줄 번호도 유저 파일 그대로 유지된다.
const loadSolution = vm.compileFunction(
  fs.readFileSync('solution.js', 'utf8') + '\nreturn typeof solution === "function" ? solution : undefined;',
  ['require'],
  { filename: 'solution.js' });

let userOutput = '';
process.stdout.write = (chunk) => {
  if (userOutput.length < USER_OUTPUT_LIMIT_CHARACTERS) {
    userOutput = (userOutput + String(chunk)).slice(0, USER_OUTPUT_LIMIT_CHARACTERS);
  }
  return true;
};

const solution = loadSolution(require);
if (solution === undefined) {
  throw new Error('solution 함수가 없습니다.');
}
const returnValue = solution(...solutionArguments);

fs.writeSync(1, JSON.stringify({ returnValue: returnValue === undefined ? null : returnValue, userOutput }));
