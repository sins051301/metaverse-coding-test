import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { crawlCodetree, crawlProgrammers, parseCodetreeUrl, parseProgrammersLessonId } from './crawl.ts';
import { writeCrossCheck } from './crosscheck.ts';
import { publishApproved } from './publish.ts';
import { transformProblem } from './transform.ts';
import { verifyProblem } from './verify.ts';
import {
  appendLog,
  draftIdOf,
  draftsDirectory,
  ensureWorkspace,
  listDrafts,
  pipelineDirectory,
  readDraft,
  readRaw,
  writeDraft,
  type Draft,
} from './workspace.ts';

const PORT = Number(process.env.PIPELINE_PORT ?? 4000);

type CrawlTarget = { source: 'programmers'; lessonId: string } | { source: 'codetree'; company: string; slug: string };
type Job = { draftId: string; kind: 'crawl' | 'transform' | 'crosscheck' | 'verify'; target?: CrawlTarget; rejectReason?: string };

function parseCrawlTarget(input: string): CrawlTarget {
  const codetree = parseCodetreeUrl(input);
  if (codetree) {
    return { source: 'codetree', ...codetree };
  }
  return { source: 'programmers', lessonId: parseProgrammersLessonId(input) };
}

function draftIdOfTarget(target: CrawlTarget) {
  return target.source === 'codetree' ? draftIdOf('codetree', target.slug) : draftIdOf('programmers', target.lessonId);
}

const queue: Job[] = [];
const working = new Map<string, string>();
let processing = false;

const JOB_LABELS = { crawl: '수집 중', transform: '변형 중 (claude -p)', crosscheck: '대조 풀이 작성 중 (claude -p)', verify: '검증 중 (judge 컨테이너)' };

function enqueue(job: Job) {
  queue.push(job);
  working.set(job.draftId, `대기 중: ${JOB_LABELS[job.kind]}`);
  void processQueue();
}

async function runJob(job: Job) {
  if (job.kind === 'crawl') {
    const target = job.target!;
    if (target.source === 'codetree') {
      await crawlCodetree(target.company, target.slug);
    } else {
      await crawlProgrammers(target.lessonId);
    }
    const draft: Draft = existsSync(path.join(draftsDirectory, `${job.draftId}.json`))
      ? await readDraft(job.draftId)
      : { id: job.draftId, status: 'crawled', problem: null, crossCheck: null, verification: null, rejectReasons: [], publishedProblemId: null, publishedFile: null, log: [] };
    appendLog(draft, '원본 수집 완료');
    await writeDraft(draft);
    queue.unshift({ draftId: job.draftId, kind: 'transform' });
    return;
  }

  const draft = await readDraft(job.draftId);
  if (job.kind === 'transform') {
    const raw = await readRaw(job.draftId);
    const { problem, usage } = await transformProblem(raw, draft.problem, job.rejectReason ?? null);
    draft.problem = problem;
    draft.crossCheck = null;
    draft.verification = null;
    draft.status = 'transformed';
    appendLog(draft, `변형 완료 (${usage})${job.rejectReason ? ` — 반려 사유 반영: ${job.rejectReason}` : ''}`);
    await writeDraft(draft);
    queue.unshift({ draftId: job.draftId, kind: 'crosscheck' });
    return;
  }

  if (job.kind === 'crosscheck') {
    const { crossCheck, usage } = await writeCrossCheck(draft.problem!);
    draft.crossCheck = crossCheck;
    appendLog(draft, `대조 자료 작성 완료 (${usage}) — 완전탐색·Java 풀이, 경계값 ${crossCheck.boundaryCases.length}개`);
    await writeDraft(draft);
    queue.unshift({ draftId: job.draftId, kind: 'verify' });
    return;
  }

  if (!draft.crossCheck) {
    queue.unshift({ draftId: job.draftId, kind: 'crosscheck' });
    return;
  }

  const verification = await verifyProblem(draft.problem!, draft.crossCheck);
  draft.verification = verification;
  draft.status = verification.passed ? 'verified' : 'failed';
  appendLog(draft, verification.passed ? `검증 통과 (테스트 ${verification.tests.length}개)` : `검증 실패: 오류 ${verification.errors.length}개`);
  await writeDraft(draft);
}

async function processQueue() {
  if (processing) {
    return;
  }
  processing = true;
  while (queue.length > 0) {
    const job = queue.shift()!;
    working.set(job.draftId, JOB_LABELS[job.kind]);
    try {
      await runJob(job);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error(`[${job.draftId}] ${job.kind} 실패:`, message);
      if (existsSync(path.join(draftsDirectory, `${job.draftId}.json`))) {
        const draft = await readDraft(job.draftId);
        appendLog(draft, `${JOB_LABELS[job.kind]} 단계 실패: ${message}`);
        await writeDraft(draft);
      }
    }
    if (!queue.some((queued) => queued.draftId === job.draftId)) {
      working.delete(job.draftId);
    }
  }
  processing = false;
}

async function readBody(request: IncomingMessage) {
  let body = '';
  for await (const chunk of request) {
    body += chunk;
  }
  return body ? JSON.parse(body) : {};
}

function sendJson(response: ServerResponse, status: number, body: unknown) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

const STATIC_FILES: Record<string, { file: string; type: string }> = {
  '/': { file: path.join(pipelineDirectory, 'public', 'index.html'), type: 'text/html; charset=utf-8' },
  '/app.js': { file: path.join(pipelineDirectory, 'public', 'app.js'), type: 'text/javascript; charset=utf-8' },
  '/style.css': { file: path.join(pipelineDirectory, 'public', 'style.css'), type: 'text/css; charset=utf-8' },
  '/vendor/marked.js': { file: path.join(pipelineDirectory, 'node_modules', 'marked', 'lib', 'marked.esm.js'), type: 'text/javascript; charset=utf-8' },
};

function requireStatus(draft: Draft, allowed: Draft['status'][]) {
  if (!allowed.includes(draft.status) || working.has(draft.id)) {
    throw Object.assign(new Error(`지금 상태(${draft.status}${working.has(draft.id) ? ', 작업 중' : ''})에서는 할 수 없습니다`), { status: 409 });
  }
}

async function handleApi(request: IncomingMessage, response: ServerResponse, pathname: string) {
  const method = request.method ?? 'GET';

  if (method === 'GET' && pathname === '/api/drafts') {
    const drafts = await listDrafts();
    const summaries = await Promise.all(
      drafts.map(async (draft) => ({
        id: draft.id,
        status: draft.status,
        working: working.get(draft.id) ?? null,
        title: draft.problem?.title ?? null,
        originalTitle: (await readRaw(draft.id)).title,
      })),
    );
    const pending = [...working.entries()]
      .filter(([draftId]) => !drafts.some((draft) => draft.id === draftId))
      .map(([draftId, label]) => ({ id: draftId, status: 'crawled', working: label, title: null, originalTitle: draftId }));
    return sendJson(response, 200, [...summaries, ...pending]);
  }

  if (method === 'POST' && pathname === '/api/crawl') {
    const { lessons } = await readBody(request);
    const targets = String(lessons ?? '').split(/[\s,]+/).filter(Boolean).map(parseCrawlTarget);
    const draftIds = targets.map(draftIdOfTarget);
    targets.forEach((target, index) => enqueue({ draftId: draftIds[index], kind: 'crawl', target }));
    return sendJson(response, 202, { queued: draftIds });
  }

  if (method === 'POST' && pathname === '/api/publish') {
    return sendJson(response, 200, await publishApproved());
  }

  const match = pathname.match(/^\/api\/drafts\/([\w-]+)(?:\/(approve|reject|verify|retransform))?$/);
  if (!match) {
    return sendJson(response, 404, { message: '없는 API 입니다' });
  }
  const [, draftId, action] = match;
  const draft = await readDraft(draftId);

  if (method === 'GET' && !action) {
    return sendJson(response, 200, { draft, raw: await readRaw(draftId), working: working.get(draftId) ?? null });
  }

  if (method === 'PUT' && !action) {
    requireStatus(draft, ['verified', 'failed', 'approved']);
    const { title, level, description } = await readBody(request);
    Object.assign(draft.problem!, { title, level: Number(level), description });
    if (draft.status === 'approved') {
      draft.status = 'verified';
    }
    appendLog(draft, '지문 직접 수정');
    await writeDraft(draft);
    return sendJson(response, 200, draft);
  }

  if (method === 'POST' && action === 'approve') {
    requireStatus(draft, ['verified']);
    draft.status = 'approved';
    appendLog(draft, '승인');
    await writeDraft(draft);
    return sendJson(response, 200, draft);
  }

  if (method === 'POST' && action === 'reject') {
    requireStatus(draft, ['verified', 'failed', 'approved', 'transformed']);
    const { reason } = await readBody(request);
    if (!reason || !String(reason).trim()) {
      return sendJson(response, 400, { message: '반려 사유를 입력하세요' });
    }
    draft.rejectReasons.push(String(reason).trim());
    appendLog(draft, `반려: ${reason}`);
    await writeDraft(draft);
    const verificationErrors = draft.verification?.errors ?? [];
    const reasonWithCounterexamples = verificationErrors.length
      ? `${String(reason).trim()}\n\n직전 검증에서 나온 오류 (반례 포함):\n${verificationErrors.map((message) => `- ${message}`).join('\n')}`
      : String(reason).trim();
    enqueue({ draftId, kind: 'transform', rejectReason: reasonWithCounterexamples });
    return sendJson(response, 202, draft);
  }

  if (method === 'POST' && action === 'retransform') {
    requireStatus(draft, ['crawled', 'transformed', 'failed', 'verified']);
    enqueue({ draftId, kind: 'transform' });
    return sendJson(response, 202, draft);
  }

  if (method === 'POST' && action === 'verify') {
    requireStatus(draft, ['transformed', 'verified', 'failed']);
    enqueue({ draftId, kind: 'verify' });
    return sendJson(response, 202, draft);
  }

  return sendJson(response, 405, { message: '허용하지 않는 요청입니다' });
}

await ensureWorkspace();

createServer(async (request, response) => {
  const pathname = new URL(request.url ?? '/', 'http://localhost').pathname;
  try {
    if (pathname.startsWith('/api/')) {
      await handleApi(request, response, pathname);
      return;
    }
    if (pathname === '/favicon.ico') {
      response.writeHead(204).end();
      return;
    }
    const staticFile = STATIC_FILES[pathname];
    if (!staticFile) {
      response.writeHead(404).end();
      return;
    }
    response.writeHead(200, { 'content-type': staticFile.type, 'cache-control': 'no-store' });
    response.end(await readFile(staticFile.file));
  } catch (error) {
    const status = (error as { status?: number }).status ?? 500;
    sendJson(response, status, { message: error instanceof Error ? error.message : String(error) });
  }
}).listen(PORT, '127.0.0.1', () => {
  console.log(`문제 파이프라인 검수 화면: http://localhost:${PORT}`);
});
