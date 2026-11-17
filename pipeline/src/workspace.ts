import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const pipelineDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const repositoryDirectory = path.resolve(pipelineDirectory, '..');
export const workspaceDirectory = path.join(pipelineDirectory, 'workspace');
export const rawDirectory = path.join(workspaceDirectory, 'raw');
export const draftsDirectory = path.join(workspaceDirectory, 'drafts');
export const sandboxDirectory = path.join(workspaceDirectory, 'sandbox');
export const migrationDirectory = path.join(repositoryDirectory, 'backend', 'src', 'main', 'resources', 'db', 'migration');

export const SUPPORTED_TYPES = ['int', 'long', 'double', 'boolean', 'String', 'int[]', 'long[]', 'String[]', 'int[][]'] as const;
export type ValueType = (typeof SUPPORTED_TYPES)[number];

export type RawProblem = {
  source: 'programmers' | 'codetree';
  sourceId: string;
  url: string;
  title: string;
  category: string | null;
  level: number | null;
  descriptionHtml: string;
  descriptionText: string;
  javaStarter: string;
  crawledAt: string;
};

export type Parameter = { name: string; type: ValueType };
export type Example = { input: unknown[]; expected: unknown };

export type TransformedProblem = {
  title: string;
  level: number;
  description: string;
  returnType: ValueType;
  parameters: Parameter[];
  timeLimitMs: number;
  referenceSolution: string;
  examples: Example[];
  generator: string;
  changeSummary: string;
};

export type BoundaryCase = { input: unknown[]; reason: string };

// 변형본의 정답 코드를 보지 않고 지문만으로 따로 만든 대조 자료 (crosscheck.ts)
export type CrossCheck = {
  bruteSolution: string;
  javaSolution: string;
  validator: string;
  boundaryCases: BoundaryCase[];
  smallGenerator: string;
};

export type TestCase = { hidden: boolean; input: unknown[]; expected: unknown; timeSeconds: number; reason?: string };

export type Verification = {
  passed: boolean;
  checkedAt: string;
  errors: string[];
  warnings: string[];
  checks: string[];
  tests: TestCase[];
};

export type DraftStatus =
  | 'crawled'
  | 'transformed'
  | 'verified'
  | 'failed'
  | 'approved'
  | 'published';

export type Draft = {
  id: string;
  status: DraftStatus;
  problem: TransformedProblem | null;
  crossCheck?: CrossCheck | null;
  verification: Verification | null;
  rejectReasons: string[];
  publishedProblemId: number | null;
  publishedFile: string | null;
  log: { at: string; message: string }[];
};

export async function ensureWorkspace() {
  await Promise.all([rawDirectory, draftsDirectory, sandboxDirectory].map((directory) => mkdir(directory, { recursive: true })));
}

export function draftIdOf(source: string, sourceId: string) {
  return `${source}-${sourceId}`;
}

async function readJson<T>(filePath: string): Promise<T> {
  return JSON.parse(await readFile(filePath, 'utf8')) as T;
}

export function readRaw(draftId: string) {
  return readJson<RawProblem>(path.join(rawDirectory, `${draftId}.json`));
}

export async function writeRaw(raw: RawProblem) {
  await writeFile(path.join(rawDirectory, `${draftIdOf(raw.source, raw.sourceId)}.json`), JSON.stringify(raw, null, 2));
}

export function readDraft(draftId: string) {
  return readJson<Draft>(path.join(draftsDirectory, `${draftId}.json`));
}

export async function writeDraft(draft: Draft) {
  await writeFile(path.join(draftsDirectory, `${draft.id}.json`), JSON.stringify(draft, null, 2));
}

export async function listDrafts() {
  const fileNames = (await readdir(draftsDirectory)).filter((fileName) => fileName.endsWith('.json'));
  return Promise.all(fileNames.map((fileName) => readDraft(fileName.replace(/\.json$/, ''))));
}

export function appendLog(draft: Draft, message: string) {
  draft.log.push({ at: new Date().toISOString(), message });
}
