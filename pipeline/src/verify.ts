import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { sandboxDirectory, type CrossCheck, type TestCase, type TransformedProblem, type ValueType, type Verification } from './workspace.ts';

const LANGUAGES = {
  javascript: { image: 'coding-test/judge-javascript', sourceFile: 'solution.js' },
  java: { image: 'coding-test/judge-java', sourceFile: 'Solution.java' },
};
type Language = keyof typeof LANGUAGES;

const GENERATOR_TIME_LIMIT_SECONDS = 10;
const HELPER_TIME_LIMIT_SECONDS = 5;
const HELPER_MEMORY_LIMIT_MB = 512;
const DEFAULT_MEMORY_LIMIT_MB = 265;
const CONTAINER_OVERHEAD_SECONDS = 30;
const MINIMUM_GENERATED_TESTS = 5;
const MINIMUM_SMALL_TESTS = 100;
const MAX_CASE_BYTES = 200 * 1024;
const MAX_TOTAL_BYTES = 4 * 1024 * 1024;
const HTML_TAG = /<\/?[a-zA-Z][a-zA-Z0-9]*(\s[^<>]*)?\/?>/g;
const MARKDOWN_CODE = /```[\s\S]*?```|`[^`\n]*`/g;
const MAX_MESSAGES_PER_KIND = 8;
const DOUBLE_TOLERANCE = 1e-6;
const INT_MIN = -(2 ** 31);
const INT_MAX = 2 ** 31 - 1;

type SandboxTestResult = {
  exitCode: number;
  timeSeconds: number;
  oomKilled: boolean;
  output: { returnValue: unknown } | null;
  stderr: string;
};

type SandboxResult = { compileError: string | null; tests: SandboxTestResult[] };

type CaseKind = 'example' | 'generated' | 'boundary' | 'small';
type Case = { kind: CaseKind; label: string; input: unknown[]; reason?: string };

function parseTar(archive: Buffer) {
  const files = new Map<string, Buffer>();
  let offset = 0;
  while (offset + 512 <= archive.length) {
    const header = archive.subarray(offset, offset + 512);
    if (header.every((byte) => byte === 0)) {
      break;
    }
    const readText = (start: number, length: number) => header.subarray(start, start + length).toString('utf8').replace(/\0.*$/s, '');
    const name = (readText(345, 155) ? `${readText(345, 155)}/` : '') + readText(0, 100);
    const size = parseInt(readText(124, 12).trim() || '0', 8);
    const typeFlag = readText(156, 1);
    offset += 512;
    if (typeFlag === '0' || typeFlag === '') {
      files.set(name.replace(/^\.\//, ''), archive.subarray(offset, offset + size));
    }
    offset += Math.ceil(size / 512) * 512;
  }
  return files;
}

function runContainer(language: Language, inputDirectory: string, runTimeoutSeconds: number, memoryLimitMb: number, testCount: number) {
  const containerName = `pipeline-${randomUUID()}`;
  const commandArguments = [
    'run', '--rm', '--name', containerName,
    '--network', 'none',
    '--read-only',
    '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=32m,mode=1777',
    '--memory', `${memoryLimitMb}m`, '--memory-swap', `${memoryLimitMb}m`,
    '--cpus', '1',
    '--pids-limit', '128',
    '--ulimit', 'core=0',
    '--cap-drop', 'ALL', '--cap-add', 'SETUID', '--cap-add', 'SETGID', '--cap-add', 'KILL',
    '--security-opt', 'no-new-privileges',
    '--mount', `type=bind,source=${inputDirectory},target=/judge/in,readonly`,
    '-e', 'COMPILE_TIMEOUT=10',
    '-e', `RUN_TIMEOUT=${runTimeoutSeconds}`,
    '-e', `MEMORY_LIMIT_MB=${memoryLimitMb}`,
    LANGUAGES[language].image,
  ];
  const containerTimeoutMs = (10 + (runTimeoutSeconds + 1) * testCount + CONTAINER_OVERHEAD_SECONDS) * 1000;
  return new Promise<Buffer>((resolve, reject) => {
    const child = spawn('docker', commandArguments);
    const chunks: Buffer[] = [];
    let stderr = '';
    const timer = setTimeout(() => {
      spawn('docker', ['kill', containerName]);
      reject(new Error(`채점 컨테이너가 ${containerTimeoutMs / 1000}초 안에 끝나지 않았습니다`));
    }, containerTimeoutMs);
    child.stdout.on('data', (chunk: Buffer) => chunks.push(chunk));
    child.stderr.on('data', (chunk) => (stderr += chunk));
    child.on('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
    child.on('close', (code) => {
      clearTimeout(timer);
      if (code === 0) {
        resolve(Buffer.concat(chunks));
      } else {
        reject(new Error(`docker run 종료 코드 ${code}: ${stderr.slice(0, 2000)}`));
      }
    });
  });
}

async function runInSandbox(language: Language, sourceCode: string, inputs: unknown[][], runTimeoutSeconds: number, memoryLimitMb: number): Promise<SandboxResult> {
  const inputDirectory = path.join(sandboxDirectory, randomUUID());
  await mkdir(path.join(inputDirectory, 'tests'), { recursive: true });
  try {
    await writeFile(path.join(inputDirectory, LANGUAGES[language].sourceFile), sourceCode);
    await Promise.all(inputs.map((input, index) => writeFile(path.join(inputDirectory, 'tests', `${index + 1}.in`), JSON.stringify(input))));
    const files = parseTar(await runContainer(language, inputDirectory, runTimeoutSeconds, memoryLimitMb, inputs.length));
    const readFileText = (name: string) => files.get(name)?.toString('utf8') ?? '';
    if (readFileText('compile.exit').trim() !== '0') {
      return { compileError: readFileText('compile.log') || '컴파일 단계 실패', tests: [] };
    }
    const tests = inputs.map((_, index) => {
      const testName = String(index + 1);
      const meta = Object.fromEntries(
        readFileText(`${testName}.meta`).split('\n').filter(Boolean).map((line) => line.split('=') as [string, string]),
      );
      const outputText = readFileText(`${testName}.out`);
      return {
        exitCode: Number(meta.exit ?? -1),
        timeSeconds: Number(meta.time_seconds ?? 0),
        oomKilled: meta.oom_killed === 'true',
        output: outputText ? (JSON.parse(outputText) as { returnValue: unknown }) : null,
        stderr: readFileText(`${testName}.err`),
      };
    });
    return { compileError: null, tests };
  } finally {
    await rm(inputDirectory, { recursive: true, force: true });
  }
}

function isInteger(value: unknown, minimum: number, maximum: number) {
  return typeof value === 'number' && Number.isInteger(value) && value >= minimum && value <= maximum;
}

export function matchesType(value: unknown, type: ValueType): boolean {
  switch (type) {
    case 'int':
      return isInteger(value, INT_MIN, INT_MAX);
    case 'long':
      return isInteger(value, Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER);
    case 'double':
      return typeof value === 'number' && Number.isFinite(value);
    case 'boolean':
      return typeof value === 'boolean';
    case 'String':
      return typeof value === 'string';
    case 'int[]':
      return Array.isArray(value) && value.every((element) => matchesType(element, 'int'));
    case 'long[]':
      return Array.isArray(value) && value.every((element) => matchesType(element, 'long'));
    case 'String[]':
      return Array.isArray(value) && value.every((element) => matchesType(element, 'String'));
    case 'int[][]':
      return Array.isArray(value) && value.every((element) => matchesType(element, 'int[]'));
  }
}

function sameValue(actual: unknown, expected: unknown): boolean {
  if (typeof actual === 'number' && typeof expected === 'number') {
    return Math.abs(actual - expected) <= DOUBLE_TOLERANCE;
  }
  if (Array.isArray(actual) && Array.isArray(expected)) {
    return actual.length === expected.length && actual.every((element, index) => sameValue(element, expected[index]));
  }
  return actual === expected;
}

function inputErrors(input: unknown, problem: TransformedProblem) {
  if (!Array.isArray(input) || input.length !== problem.parameters.length) {
    return [`인자 개수가 파라미터 ${problem.parameters.length}개와 다릅니다`];
  }
  return problem.parameters
    .filter((parameter, index) => !matchesType(input[index], parameter.type))
    .map((parameter) => `${parameter.name} 가 ${parameter.type} 타입이 아닙니다`);
}

function isTimeout(result: SandboxTestResult, timeLimitSeconds: number) {
  return result.exitCode === 137 || result.timeSeconds >= timeLimitSeconds;
}

function describeFailure(result: SandboxTestResult, timeLimitSeconds: number) {
  if (result.oomKilled) {
    return '메모리 초과';
  }
  if (isTimeout(result, timeLimitSeconds)) {
    return '시간 초과';
  }
  return `런타임 에러: ${result.stderr.slice(0, 500)}`;
}

function preview(value: unknown) {
  const text = JSON.stringify(value);
  return text.length > 300 ? `${text.slice(0, 300)}…` : text;
}

function succeeded(result: SandboxTestResult) {
  return result.exitCode === 0 && result.output !== null;
}

// 같은 종류의 오류가 수백 개 나와도 화면이 넘치지 않게 몇 개만 남기고 나머지는 개수로 알린다
class MessageList {
  readonly messages: string[] = [];
  private readonly counts = new Map<string, number>();

  add(kind: string, message: string) {
    const count = (this.counts.get(kind) ?? 0) + 1;
    this.counts.set(kind, count);
    if (count <= MAX_MESSAGES_PER_KIND) {
      this.messages.push(message);
    }
  }

  summarizeOverflow() {
    for (const [kind, count] of this.counts) {
      if (count > MAX_MESSAGES_PER_KIND) {
        this.messages.push(`${kind}: 위 ${MAX_MESSAGES_PER_KIND}개 외 ${count - MAX_MESSAGES_PER_KIND}개 더`);
      }
    }
    return this.messages;
  }

  get length() {
    return this.counts.size;
  }
}

async function runInputGenerator(source: string, name: string, errors: MessageList) {
  const run = await runInSandbox('javascript', source, [[]], GENERATOR_TIME_LIMIT_SECONDS, HELPER_MEMORY_LIMIT_MB);
  if (run.compileError) {
    errors.add(name, `${name} 문법 오류: ${run.compileError.slice(0, 500)}`);
    return null;
  }
  const result = run.tests[0];
  if (!succeeded(result)) {
    errors.add(name, `${name} 실행 실패: ${describeFailure(result, GENERATOR_TIME_LIMIT_SECONDS)}`);
    return null;
  }
  if (!Array.isArray(result.output!.returnValue)) {
    errors.add(name, `${name}가 배열을 return 하지 않았습니다`);
    return null;
  }
  return result.output!.returnValue as unknown[][];
}

function distinctCases(cases: Case[]) {
  const seen = new Set<string>();
  return cases.filter((testCase) => {
    const key = JSON.stringify(testCase.input);
    if (testCase.kind !== 'example' && seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

export async function verifyProblem(problem: TransformedProblem, crossCheck: CrossCheck): Promise<Verification> {
  const errors = new MessageList();
  const warnings = new MessageList();
  const checks: string[] = [];
  const finish = (tests: TestCase[]): Verification => ({
    passed: errors.length === 0,
    checkedAt: new Date().toISOString(),
    errors: errors.summarizeOverflow(),
    warnings: warnings.summarizeOverflow(),
    checks,
    tests,
  });

  const htmlTags = [...new Set(problem.description.replace(MARKDOWN_CODE, '').match(HTML_TAG) ?? [])];
  if (htmlTags.length > 0) {
    errors.add('지문 HTML', `지문에 HTML 태그 ${htmlTags.join(', ')} 가 있습니다. 화면에 글자 그대로 보이므로 마크다운·유니코드(10⁻⁶)로 바꿔야 합니다`);
  } else {
    checks.push('지문 형식: HTML 태그 없음');
  }

  const generated = await runInputGenerator(problem.generator, '테스트 생성기', errors);
  const small = await runInputGenerator(crossCheck.smallGenerator, '작은 입력 생성기', errors);
  if (!generated || !small) {
    return finish([]);
  }
  if (generated.length < MINIMUM_GENERATED_TESTS) {
    errors.add('생성 개수', `테스트 생성기가 ${generated.length}개만 만들었습니다 (최소 ${MINIMUM_GENERATED_TESTS}개)`);
  }
  if (small.length < MINIMUM_SMALL_TESTS) {
    errors.add('작은 입력 개수', `작은 입력 생성기가 ${small.length}개만 만들었습니다 (최소 ${MINIMUM_SMALL_TESTS}개)`);
  }

  let storedCases = distinctCases([
    ...problem.examples.map((example, index): Case => ({ kind: 'example', label: `예제 ${index + 1}`, input: example.input })),
    ...generated.map((input, index): Case => ({ kind: 'generated', label: `생성 ${index + 1}`, input })),
    ...crossCheck.boundaryCases.map((boundary, index): Case => ({ kind: 'boundary', label: `경계 ${index + 1}`, input: boundary.input, reason: boundary.reason })),
  ]);
  const storedKeys = new Set(storedCases.map((testCase) => JSON.stringify(testCase.input)));
  const smallCases = distinctCases(small.map((input, index): Case => ({ kind: 'small', label: `작은 입력 ${index + 1}`, input })))
    .filter((testCase) => !storedKeys.has(JSON.stringify(testCase.input)));
  let allCases = [...storedCases, ...smallCases];

  allCases.forEach((testCase) => {
    inputErrors(testCase.input, problem).forEach((message) => errors.add(`${testCase.kind} 타입`, `${testCase.label}: ${message}`));
  });
  if (errors.length > 0) {
    return finish([]);
  }

  const validatorRun = await runInSandbox('javascript', crossCheck.validator, allCases.map((testCase) => testCase.input), HELPER_TIME_LIMIT_SECONDS, HELPER_MEMORY_LIMIT_MB);
  if (validatorRun.compileError) {
    errors.add('검사기', `제한사항 검사기 문법 오류: ${validatorRun.compileError.slice(0, 500)}`);
    return finish([]);
  }
  const droppedCases = new Set<Case>();
  validatorRun.tests.forEach((result, index) => {
    const testCase = allCases[index];
    if (!succeeded(result)) {
      errors.add('검사기 실행', `${testCase.label}: 제한사항 검사기 ${describeFailure(result, HELPER_TIME_LIMIT_SECONDS)}`);
      return;
    }
    const violations = result.output!.returnValue;
    if (!Array.isArray(violations) || violations.length === 0) {
      return;
    }
    const message = `${testCase.label}: 제한사항 위반 — ${violations.join(', ')} / 입력 ${preview(testCase.input)}`;
    // 경계·작은 입력은 검수자 호출이 만든 것이라, 틀려도 문제 자체의 결함이 아니다. 빼고 진행한다
    if (testCase.kind === 'boundary' || testCase.kind === 'small') {
      droppedCases.add(testCase);
      warnings.add(`${testCase.kind} 제외`, `${message} → 대조용 입력이라 제외`);
    } else {
      errors.add(`${testCase.kind} 제한사항`, message);
    }
  });
  checks.push(`제한사항 검사: 입력 ${allCases.length}개 검사${droppedCases.size ? `, 대조용 입력 ${droppedCases.size}개는 제한사항 위반이라 제외` : ''}`);
  storedCases = storedCases.filter((testCase) => !droppedCases.has(testCase));
  allCases = allCases.filter((testCase) => !droppedCases.has(testCase));

  const timeLimitSeconds = problem.timeLimitMs / 1000;
  const referenceRun = await runInSandbox('javascript', problem.referenceSolution, allCases.map((testCase) => testCase.input), timeLimitSeconds, DEFAULT_MEMORY_LIMIT_MB);
  if (referenceRun.compileError) {
    errors.add('정답 코드', `정답 코드 문법 오류: ${referenceRun.compileError.slice(0, 500)}`);
    return finish([]);
  }
  const expectedValues = referenceRun.tests.map((result, index) => {
    const testCase = allCases[index];
    if (!succeeded(result)) {
      errors.add('정답 코드 실행', `${testCase.label}: 정답 코드 ${describeFailure(result, timeLimitSeconds)}`);
      return undefined;
    }
    const expected = result.output!.returnValue;
    if (!matchesType(expected, problem.returnType)) {
      errors.add('반환 타입', `${testCase.label}: 반환값이 ${problem.returnType} 타입이 아닙니다 (${preview(expected)})`);
    }
    if (testCase.kind === 'example') {
      const written = problem.examples[index].expected;
      if (!sameValue(expected, written)) {
        errors.add('예제 기대값', `${testCase.label}: 지문의 기대값 ${preview(written)} 과 정답 코드 결과 ${preview(expected)} 가 다릅니다`);
      }
    }
    if (testCase.kind !== 'small' && result.timeSeconds > timeLimitSeconds / 2) {
      warnings.add('정답 코드 시간', `${testCase.label}: 정답 코드가 ${result.timeSeconds}초 걸렸습니다 (시간 제한의 절반 초과)`);
    }
    return expected;
  });
  if (errors.length > 0) {
    return finish([]);
  }

  const bruteIndexes = allCases.flatMap((testCase, index) => (testCase.kind === 'generated' ? [] : [index]));
  const bruteRun = await runInSandbox('javascript', crossCheck.bruteSolution, bruteIndexes.map((index) => allCases[index].input), HELPER_TIME_LIMIT_SECONDS, HELPER_MEMORY_LIMIT_MB);
  if (bruteRun.compileError) {
    errors.add('완전탐색', `완전탐색 풀이 문법 오류: ${bruteRun.compileError.slice(0, 500)}`);
    return finish([]);
  }
  let bruteMatched = 0;
  let bruteSkipped = 0;
  bruteRun.tests.forEach((result, position) => {
    const index = bruteIndexes[position];
    const testCase = allCases[index];
    if (!succeeded(result)) {
      if (testCase.kind === 'small') {
        errors.add('완전탐색 실행', `${testCase.label}: 완전탐색 풀이 ${describeFailure(result, HELPER_TIME_LIMIT_SECONDS)} / 입력 ${preview(testCase.input)}`);
      } else {
        bruteSkipped += 1;
      }
      return;
    }
    const bruteValue = result.output!.returnValue;
    if (sameValue(bruteValue, expectedValues[index])) {
      bruteMatched += 1;
    } else {
      errors.add('완전탐색 불일치', `${testCase.label}: 정답 코드 ${preview(expectedValues[index])} ≠ 완전탐색 ${preview(bruteValue)} / 입력 ${preview(testCase.input)}`);
    }
  });
  checks.push(`완전탐색 대조: ${bruteMatched}개 일치${bruteSkipped ? `, ${bruteSkipped}개 생략 (입력이 커서 완전탐색이 ${HELPER_TIME_LIMIT_SECONDS}초 안에 못 끝남)` : ''}`);

  const javaRun = await runInSandbox('java', crossCheck.javaSolution, storedCases.map((testCase) => testCase.input), timeLimitSeconds, DEFAULT_MEMORY_LIMIT_MB);
  if (javaRun.compileError) {
    errors.add('Java', `Java 풀이 컴파일 오류: ${javaRun.compileError.slice(0, 500)}`);
  } else {
    let javaMatched = 0;
    javaRun.tests.forEach((result, index) => {
      const testCase = storedCases[index];
      if (!succeeded(result)) {
        if (isTimeout(result, timeLimitSeconds)) {
          warnings.add('Java 시간', `${testCase.label}: Java 풀이 시간 초과 (Java 풀이가 느리거나, 시간 제한이 Java 에 빡빡할 수 있음)`);
        } else {
          errors.add('Java 실행', `${testCase.label}: Java 풀이 ${describeFailure(result, timeLimitSeconds)}`);
        }
        return;
      }
      const javaValue = result.output!.returnValue;
      if (sameValue(javaValue, expectedValues[index])) {
        javaMatched += 1;
      } else {
        errors.add('Java 불일치', `${testCase.label}: 정답 코드 ${preview(expectedValues[index])} ≠ Java ${preview(javaValue)} / 입력 ${preview(testCase.input)}`);
      }
    });
    checks.push(`Java 대조: 테스트 ${storedCases.length}개 중 ${javaMatched}개 일치`);
  }

  const tests: TestCase[] = [];
  let totalBytes = 0;
  storedCases.forEach((testCase, index) => {
    const expected = expectedValues[index];
    const caseBytes = Buffer.byteLength(JSON.stringify(testCase.input)) + Buffer.byteLength(JSON.stringify(expected));
    if (caseBytes > MAX_CASE_BYTES) {
      errors.add('케이스 크기', `${testCase.label}: 테스트 크기 ${Math.round(caseBytes / 1024)}KB 가 상한 ${MAX_CASE_BYTES / 1024}KB 를 넘습니다`);
    }
    totalBytes += caseBytes;
    tests.push({
      hidden: testCase.kind !== 'example',
      input: testCase.input,
      expected,
      timeSeconds: referenceRun.tests[index].timeSeconds,
      ...(testCase.reason ? { reason: testCase.reason } : {}),
    });
  });
  if (totalBytes > MAX_TOTAL_BYTES) {
    errors.add('전체 크기', `테스트 전체 크기 ${Math.round(totalBytes / 1024)}KB 가 상한 ${MAX_TOTAL_BYTES / 1024}KB 를 넘습니다`);
  }
  checks.push(`저장할 테스트: 예제 ${problem.examples.length}개, 생성 ${storedCases.filter((testCase) => testCase.kind === 'generated').length}개, 경계 ${storedCases.filter((testCase) => testCase.kind === 'boundary').length}개 (중복 입력 제외)`);
  return finish(tests);
}
