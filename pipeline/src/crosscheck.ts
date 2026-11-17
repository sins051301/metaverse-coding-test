import { askClaude, readPrompt } from './transform.ts';
import type { CrossCheck, TransformedProblem } from './workspace.ts';

const MAX_BOUNDARY_CASES = 15;

const outputSchema = {
  type: 'object',
  additionalProperties: false,
  required: ['bruteSolution', 'javaSolution', 'smallGenerator', 'boundaryCases', 'validator'],
  properties: {
    bruteSolution: { type: 'string' },
    javaSolution: { type: 'string' },
    smallGenerator: { type: 'string' },
    boundaryCases: {
      type: 'array',
      minItems: 5,
      maxItems: MAX_BOUNDARY_CASES,
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['input', 'reason'],
        properties: { input: { type: 'array' }, reason: { type: 'string' } },
      },
    },
    validator: { type: 'string' },
  },
};

function buildPrompt(instructions: string, problem: TransformedProblem) {
  const signature = `${problem.returnType} solution(${problem.parameters.map((parameter) => `${parameter.type} ${parameter.name}`).join(', ')})`;
  return [instructions, '# 검수할 문제', `함수: ${signature}`, `시간 제한: ${problem.timeLimitMs}ms`, '지문:', problem.description].join('\n');
}

export async function writeCrossCheck(problem: TransformedProblem) {
  const { result, usage } = await askClaude<CrossCheck>(
    buildPrompt(await readPrompt('crosscheck.md'), problem),
    outputSchema,
    '너는 코딩테스트 문제 검수자다. JSON 스키마가 요구하는 결과만 만든다.',
  );
  return { crossCheck: result, usage };
}
