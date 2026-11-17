import { execFileSync, spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { pipelineDirectory, SUPPORTED_TYPES, type RawProblem, type TransformedProblem } from './workspace.ts';

const TRANSFORM_TIMEOUT_MS = 10 * 60 * 1000;

const typeSchema = { type: 'string', enum: SUPPORTED_TYPES };

const outputSchema = {
  type: 'object',
  additionalProperties: false,
  required: ['title', 'level', 'description', 'returnType', 'parameters', 'timeLimitMs', 'referenceSolution', 'examples', 'generator', 'changeSummary'],
  properties: {
    title: { type: 'string', maxLength: 100 },
    level: { type: 'integer', minimum: 0, maximum: 5 },
    description: { type: 'string' },
    returnType: typeSchema,
    parameters: {
      type: 'array',
      minItems: 1,
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['name', 'type'],
        properties: { name: { type: 'string', pattern: '^[a-z][A-Za-z0-9]*$', maxLength: 50 }, type: typeSchema },
      },
    },
    timeLimitMs: { type: 'integer', minimum: 500, maximum: 10000 },
    referenceSolution: { type: 'string' },
    examples: {
      type: 'array',
      minItems: 2,
      maxItems: 5,
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['input', 'expected'],
        properties: { input: { type: 'array' }, expected: {} },
      },
    },
    generator: { type: 'string' },
    changeSummary: { type: 'string' },
  },
};

function buildPrompt(instructions: string, raw: RawProblem, previous: TransformedProblem | null, rejectReason: string | null) {
  const sections = [
    instructions,
    '# 원본 문제',
    `제목: ${raw.title}`,
    `분류: ${raw.category ?? '알 수 없음'} / 레벨: ${raw.level ?? '알 수 없음'}`,
  ];
  if (raw.javaStarter) {
    sections.push('Java 시그니처:', '```java', raw.javaStarter, '```');
  } else {
    sections.push(
      '원본은 표준 입력으로 값을 읽고 표준 출력으로 답을 쓰는 형식이다.',
      '입력 형식의 값들을 solution 함수의 파라미터로, 출력을 return 값으로 바꿔 설계한다. 지문에도 "입력/출력" 섹션 대신 위에서 정한 네 섹션을 쓰고, 파라미터로 이미 알 수 있는 값(배열 길이 N 등)은 따로 받지 않는다.',
      '원본의 수식은 $...$ 로 감싼 TeX 표기다. 변형본 지문에서는 TeX 대신 일반 텍스트(예: 1 ≤ N ≤ 100)로 쓴다.',
    );
  }
  sections.push('지문:', raw.descriptionText);
  if (previous && rejectReason) {
    sections.push(
      '# 이전 변형본 (검수에서 반려됨)',
      '```json',
      JSON.stringify(previous, null, 2),
      '```',
      `반려 사유: ${rejectReason}`,
      '반려 사유를 반영해서 다시 만든다. 사유와 관계없는 부분은 이전 변형본을 유지해도 된다.',
    );
  }
  return sections.join('\n');
}

function resolveClaudeExecutable() {
  if (process.env.PIPELINE_CLAUDE) {
    return process.env.PIPELINE_CLAUDE;
  }
  if (process.platform !== 'win32') {
    return 'claude';
  }
  const located = execFileSync('where', ['claude'], { encoding: 'utf8' }).split(/\r?\n/).filter(Boolean);
  const executable = located.find((candidate) => candidate.toLowerCase().endsWith('.exe'));
  if (executable) {
    return executable;
  }
  const wrapper = located.find((candidate) => candidate.toLowerCase().endsWith('.cmd'));
  if (!wrapper) {
    throw new Error('claude 실행 파일을 찾을 수 없습니다. PIPELINE_CLAUDE 환경 변수로 경로를 지정하세요');
  }
  const wrapped = path.join(path.dirname(wrapper), 'node_modules', '@anthropic-ai', 'claude-code', 'bin', 'claude.exe');
  if (!existsSync(wrapped)) {
    throw new Error(`${wrapper} 가 가리키는 claude.exe 를 찾을 수 없습니다. PIPELINE_CLAUDE 환경 변수로 경로를 지정하세요`);
  }
  return wrapped;
}

function runClaude(prompt: string, schema: object, systemPrompt: string) {
  const commandArguments = [
    '-p',
    '--output-format', 'json',
    '--tools', '',
    '--no-session-persistence',
    '--strict-mcp-config',
    '--disable-slash-commands',
    '--setting-sources', 'project',
    '--system-prompt', systemPrompt,
    '--json-schema', JSON.stringify(schema),
  ];
  if (process.env.PIPELINE_MODEL) {
    commandArguments.push('--model', process.env.PIPELINE_MODEL);
  }
  return new Promise<string>((resolve, reject) => {
    const child = spawn(resolveClaudeExecutable(), commandArguments, { cwd: pipelineDirectory });
    let stdout = '';
    let stderr = '';
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error(`claude -p 가 ${TRANSFORM_TIMEOUT_MS / 1000}초 안에 끝나지 않았습니다`));
    }, TRANSFORM_TIMEOUT_MS);
    child.stdout.on('data', (chunk) => (stdout += chunk));
    child.stderr.on('data', (chunk) => (stderr += chunk));
    child.on('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
    child.on('close', (code) => {
      clearTimeout(timer);
      if (code === 0) {
        resolve(stdout);
      } else {
        reject(new Error(`claude -p 종료 코드 ${code}: ${(stderr || stdout).slice(0, 2000)}`));
      }
    });
    child.stdin.end(prompt);
  });
}

export function readPrompt(fileName: string) {
  return readFile(path.join(pipelineDirectory, 'src', 'prompts', fileName), 'utf8');
}

export async function askClaude<T>(prompt: string, schema: object, systemPrompt: string) {
  const output = JSON.parse(await runClaude(prompt, schema, systemPrompt));
  if (output.is_error || !output.structured_output) {
    throw new Error(`claude -p 가 구조화된 결과를 돌려주지 않았습니다: ${String(output.result).slice(0, 2000)}`);
  }
  return {
    result: output.structured_output as T,
    usage: `입력 ${output.usage.input_tokens + output.usage.cache_creation_input_tokens + output.usage.cache_read_input_tokens} / 출력 ${output.usage.output_tokens} 토큰`,
  };
}

export async function transformProblem(raw: RawProblem, previous: TransformedProblem | null, rejectReason: string | null) {
  const { result, usage } = await askClaude<TransformedProblem>(
    buildPrompt(await readPrompt('transform.md'), raw, previous, rejectReason),
    outputSchema,
    '너는 코딩테스트 문제 출제자다. JSON 스키마가 요구하는 결과만 만든다.',
  );
  return { problem: result, usage };
}
