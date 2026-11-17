import { marked } from '/vendor/marked.js';

const POLL_INTERVAL_MS = 2000;
const OVERLAP_WORD_COUNT = 5;
const VALUE_PREVIEW_LENGTH = 160;

const STATUS_LABELS = {
  crawled: '수집됨',
  transformed: '변형됨',
  verified: '검증 통과',
  failed: '검증 실패',
  approved: '승인됨',
  published: 'SQL 생성됨',
};

const state = {
  drafts: [],
  selectedId: null,
  detail: null,
  descriptionMode: 'edit',
  dirty: false,
};

const elements = {
  crawlForm: document.getElementById('crawl-form'),
  crawlInput: document.getElementById('crawl-input'),
  publishButton: document.getElementById('publish-button'),
  draftList: document.getElementById('draft-list'),
  emptyList: document.getElementById('empty-list'),
  detail: document.getElementById('detail'),
  toast: document.getElementById('toast'),
};

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
}

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.hidden = false;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => (elements.toast.hidden = true), 4000);
}

async function request(method, url, body) {
  const response = await fetch(url, {
    method,
    headers: body ? { 'content-type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message ?? `요청 실패 (${response.status})`);
  }
  return data;
}

function tokenize(text) {
  return text
    .toLowerCase()
    .replace(/[`*_#|>\[\](){}:;,.!?"'“”‘’·-]/g, ' ')
    .split(/\s+/)
    .filter(Boolean);
}

function findOverlaps(originalText, draftText) {
  const originalTokens = tokenize(originalText);
  const shingles = new Set();
  for (let index = 0; index + OVERLAP_WORD_COUNT <= originalTokens.length; index++) {
    shingles.add(originalTokens.slice(index, index + OVERLAP_WORD_COUNT).join(' '));
  }
  const draftTokens = tokenize(draftText);
  const covered = new Array(draftTokens.length).fill(false);
  for (let index = 0; index + OVERLAP_WORD_COUNT <= draftTokens.length; index++) {
    if (shingles.has(draftTokens.slice(index, index + OVERLAP_WORD_COUNT).join(' '))) {
      covered.fill(true, index, index + OVERLAP_WORD_COUNT);
    }
  }
  const phrases = [];
  let start = -1;
  covered.forEach((isCovered, index) => {
    if (isCovered && start < 0) {
      start = index;
    }
    if ((!isCovered || index === covered.length - 1) && start >= 0) {
      const end = isCovered ? index + 1 : index;
      phrases.push(draftTokens.slice(start, end).join(' '));
      start = -1;
    }
  });
  return phrases;
}

function highlightPhrases(container, phrases) {
  const wordSets = phrases.map((phrase) => phrase.split(' '));
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  const textNodes = [];
  while (walker.nextNode()) {
    textNodes.push(walker.currentNode);
  }
  textNodes.forEach((node) => {
    const words = wordSets.find((set) => set.length >= OVERLAP_WORD_COUNT && tokenize(node.textContent).join(' ').includes(set.slice(0, OVERLAP_WORD_COUNT).join(' ')));
    if (words) {
      const mark = document.createElement('mark');
      node.replaceWith(mark);
      mark.append(node);
    }
  });
}

function shorten(value) {
  const text = JSON.stringify(value);
  return text.length > VALUE_PREVIEW_LENGTH ? `${text.slice(0, VALUE_PREVIEW_LENGTH)}… (${Math.round(text.length / 1024)}KB)` : text;
}

function renderList() {
  elements.emptyList.hidden = state.drafts.length > 0;
  elements.draftList.innerHTML = state.drafts
    .map((draft) => {
      const badge = draft.working
        ? `<span class="badge working">${escapeHtml(draft.working)}</span>`
        : `<span class="badge ${draft.status}">${STATUS_LABELS[draft.status] ?? draft.status}</span>`;
      return `<li><button type="button" data-id="${escapeHtml(draft.id)}" aria-current="${draft.id === state.selectedId}">
        <span class="draft-title">${escapeHtml(draft.title ?? draft.originalTitle)}</span>
        <span class="draft-meta">${escapeHtml(draft.id)} ${badge}</span>
      </button></li>`;
    })
    .join('');
  const approvedCount = state.drafts.filter((draft) => draft.status === 'approved').length;
  elements.publishButton.disabled = approvedCount === 0;
  elements.publishButton.textContent = approvedCount > 0 ? `SQL 생성 (${approvedCount}개)` : 'SQL 생성';
}

function originalDocument(raw) {
  const dark = matchMedia('(prefers-color-scheme: dark)').matches;
  return `<!doctype html><meta charset="utf-8"><style>
    body{margin:0;padding:16px;font:14px/1.6 system-ui,'Malgun Gothic',sans-serif;background:${dark ? '#1f1e1c' : '#efede8'};color:${dark ? '#ebe8e2' : '#24221f'}}
    table{border-collapse:collapse}td,th{border:1px solid #8884;padding:4px 10px}code{background:#8882;padding:1px 4px;border-radius:4px}
  </style>${raw.descriptionHtml}`;
}

function renderVerification(verification) {
  if (!verification) {
    return '<p class="muted">아직 검증하지 않았습니다.</p>';
  }
  const banner = verification.passed
    ? `<div class="result-banner passed">검증 통과 — 테스트 ${verification.tests.length}개 (예제 ${verification.tests.filter((test) => !test.hidden).length}, 히든 ${verification.tests.filter((test) => test.hidden).length})</div>`
    : `<div class="result-banner failed">검증 실패 — 오류 ${verification.errors.length}개</div>`;
  const errors = verification.errors.length ? `<ul class="message-list errors">${verification.errors.map((message) => `<li>${escapeHtml(message)}</li>`).join('')}</ul>` : '';
  const warnings = verification.warnings.length ? `<ul class="message-list warnings">${verification.warnings.map((message) => `<li>${escapeHtml(message)}</li>`).join('')}</ul>` : '';
  const checks = verification.checks?.length ? `<ul class="message-list checks">${verification.checks.map((message) => `<li>${escapeHtml(message)}</li>`).join('')}</ul>` : '';
  const rows = verification.tests
    .map((test, index) => `<tr><td>${index + 1}</td><td>${test.hidden ? (test.reason ? '경계' : '히든') : '예제'}</td><td class="value">${escapeHtml(shorten(test.input))}${test.reason ? `<div class="muted">${escapeHtml(test.reason)}</div>` : ''}</td><td class="value">${escapeHtml(shorten(test.expected))}</td><td>${test.timeSeconds}s</td></tr>`)
    .join('');
  const table = verification.tests.length
    ? `<table class="tests"><thead><tr><th>#</th><th>구분</th><th>입력</th><th>기대 출력 (정답 코드 실행 결과)</th><th>시간</th></tr></thead><tbody>${rows}</tbody></table>`
    : '';
  return banner + checks + errors + warnings + table;
}

function renderDetail() {
  const { draft, raw, working } = state.detail;
  const problem = draft.problem;
  const busy = Boolean(working);
  const status = draft.status;
  const canEdit = problem && !busy && ['verified', 'failed', 'approved'].includes(status);
  const overlaps = problem ? findOverlaps(raw.descriptionText, problem.description) : [];

  elements.detail.innerHTML = `
    <div class="detail-header">
      <h2>${escapeHtml(problem?.title ?? raw.title)}</h2>
      <span class="badge ${busy ? 'working' : status}">${escapeHtml(working ?? STATUS_LABELS[status] ?? status)}</span>
      <a href="${escapeHtml(raw.url)}" target="_blank" rel="noreferrer">원본 열기</a>
      <div class="actions">
        <button type="button" data-action="verify" ${!busy && ['transformed', 'verified', 'failed'].includes(status) ? '' : 'disabled'}>다시 검증</button>
        <button type="button" data-action="retransform" ${!busy && ['crawled', 'transformed', 'failed', 'verified'].includes(status) ? '' : 'disabled'}>처음부터 다시 변형</button>
        <button type="button" class="primary" data-action="approve" ${!busy && status === 'verified' ? '' : 'disabled'}>승인</button>
      </div>
    </div>

    ${problem ? `
    <div class="reject-box">
      <textarea id="reject-reason" rows="1" placeholder="반려 사유 (예: 원본과 배경이 너무 비슷함, 예제가 단순함) — 사유를 붙여 다시 변형합니다" ${busy || status === 'published' ? 'disabled' : ''}></textarea>
      <button type="button" class="danger" data-action="reject" ${busy || status === 'published' ? 'disabled' : ''}>반려 후 재변형</button>
    </div>` : ''}

    <div class="columns">
      <section class="panel">
        <div class="panel-header"><h3>원본 — ${escapeHtml(raw.title)}</h3></div>
        <iframe class="original-frame" sandbox="" title="원본 지문"></iframe>
      </section>
      <section class="panel">
        <div class="panel-header">
          <h3>변형본</h3>
          ${problem ? `<div class="tabs">
            <button type="button" data-mode="edit" aria-pressed="${state.descriptionMode === 'edit'}">편집</button>
            <button type="button" data-mode="preview" aria-pressed="${state.descriptionMode === 'preview'}">미리보기</button>
          </div>` : ''}
        </div>
        ${problem ? `
          <div class="field-row">
            <input id="title-input" value="${escapeHtml(problem.title)}" maxlength="100" ${canEdit ? '' : 'disabled'} />
            <select id="level-input" ${canEdit ? '' : 'disabled'}>${[0, 1, 2, 3, 4, 5].map((level) => `<option value="${level}" ${level === problem.level ? 'selected' : ''}>Lv.${level}</option>`).join('')}</select>
            <button type="button" data-action="save" ${canEdit ? '' : 'disabled'}>저장</button>
          </div>
          ${state.descriptionMode === 'edit'
            ? `<textarea id="description-input" class="description-editor" ${canEdit ? '' : 'disabled'}>${escapeHtml(problem.description)}</textarea>`
            : `<div id="markdown-preview" class="markdown-preview">${marked.parse(problem.description)}</div>`}
          <p class="muted">지문을 고쳐도 테스트케이스는 바뀌지 않습니다. 입출력 예를 바꾸려면 반려 후 재변형하세요.</p>
        ` : `<p class="muted">${busy ? '변형이 끝나면 여기에 표시됩니다.' : '아직 변형본이 없습니다.'}</p>`}
      </section>
    </div>

    ${problem ? `
    <section class="panel">
      <h3>원본과 겹치는 표현 (${OVERLAP_WORD_COUNT}단어 이상 연속)</h3>
      ${overlaps.length
        ? `<ul class="message-list warnings">${overlaps.map((phrase) => `<li>${escapeHtml(phrase)}</li>`).join('')}</ul><p class="muted">"return 하도록 solution 함수를" 같은 관용 문구는 겹쳐도 괜찮습니다. 배경 설명 문장이 겹치면 반려하세요.</p>`
        : '<p class="muted">겹치는 구간이 없습니다.</p>'}
    </section>

    <section class="panel">
      <h3>검증 결과</h3>
      ${renderVerification(draft.verification)}
    </section>

    <section class="panel">
      <h3>문제 정보</h3>
      <dl class="facts">
        <dt>함수</dt><dd><code>${escapeHtml(problem.returnType)} solution(${problem.parameters.map((parameter) => `${parameter.type} ${parameter.name}`).join(', ')})</code></dd>
        <dt>시간 제한</dt><dd>${problem.timeLimitMs}ms</dd>
        <dt>변경 요약</dt><dd>${escapeHtml(problem.changeSummary)}</dd>
        ${draft.rejectReasons.length ? `<dt>반려 이력</dt><dd>${draft.rejectReasons.map(escapeHtml).join('<br>')}</dd>` : ''}
        ${draft.publishedFile ? `<dt>생성 파일</dt><dd>${escapeHtml(draft.publishedFile)} (문제 ${draft.publishedProblemId}번)</dd>` : ''}
      </dl>
      <details><summary>정답 코드 (JavaScript)</summary><pre>${escapeHtml(problem.referenceSolution)}</pre></details>
      <details><summary>테스트 입력 생성기</summary><pre>${escapeHtml(problem.generator)}</pre></details>
      ${draft.crossCheck ? `
      <details><summary>대조용 완전탐색 풀이 (지문만 보고 작성)</summary><pre>${escapeHtml(draft.crossCheck.bruteSolution)}</pre></details>
      <details><summary>대조용 Java 풀이</summary><pre>${escapeHtml(draft.crossCheck.javaSolution)}</pre></details>
      <details><summary>제한사항 검사기</summary><pre>${escapeHtml(draft.crossCheck.validator)}</pre></details>
      <details><summary>작은 입력 생성기</summary><pre>${escapeHtml(draft.crossCheck.smallGenerator)}</pre></details>` : ''}
    </section>` : ''}

    <section class="panel">
      <h3>기록</h3>
      <ul class="log">${[...draft.log].reverse().map((entry) => `<li>${new Date(entry.at).toLocaleString('ko-KR')} — ${escapeHtml(entry.message)}</li>`).join('')}</ul>
    </section>
  `;

  elements.detail.querySelector('.original-frame').srcdoc = originalDocument(raw);
  const preview = elements.detail.querySelector('#markdown-preview');
  if (preview && overlaps.length) {
    highlightPhrases(preview, overlaps);
  }
  elements.detail.querySelectorAll('#title-input, #level-input, #description-input').forEach((input) => {
    input.addEventListener('input', () => (state.dirty = true));
  });
}

async function loadList() {
  state.drafts = await request('GET', '/api/drafts');
  renderList();
}

async function loadDetail() {
  if (!state.selectedId) {
    return;
  }
  const previousSignature = state.detail ? JSON.stringify([state.detail.draft, state.detail.working]) : null;
  state.detail = await request('GET', `/api/drafts/${state.selectedId}`).catch(() => null);
  if (!state.detail) {
    elements.detail.innerHTML = '<p class="muted">수집이 끝나면 여기에 표시됩니다.</p>';
    return;
  }
  if (previousSignature !== JSON.stringify([state.detail.draft, state.detail.working]) && !state.dirty) {
    renderDetail();
  }
}

function captureEdits() {
  const description = elements.detail.querySelector('#description-input');
  if (description && state.detail?.draft.problem) {
    state.detail.draft.problem.description = description.value;
  }
}

elements.crawlForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const lessons = elements.crawlInput.value.trim();
  if (!lessons) {
    return;
  }
  try {
    const { queued } = await request('POST', '/api/crawl', { lessons });
    elements.crawlInput.value = '';
    showToast(`${queued.length}개 문제를 대기열에 넣었습니다`);
    await loadList();
  } catch (error) {
    showToast(error.message);
  }
});

elements.publishButton.addEventListener('click', async () => {
  try {
    const result = await request('POST', '/api/publish');
    showToast(`${result.file} 생성 — 문제 ${result.count}개 (${result.firstProblemId}번부터). 커밋은 직접 하세요.`);
    await loadList();
    await loadDetail();
  } catch (error) {
    showToast(error.message);
  }
});

elements.draftList.addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-id]');
  if (!button) {
    return;
  }
  if (state.dirty && !confirm('저장하지 않은 수정이 있습니다. 버리고 이동할까요?')) {
    return;
  }
  state.selectedId = button.dataset.id;
  state.detail = null;
  state.dirty = false;
  renderList();
  await loadDetail();
});

elements.detail.addEventListener('click', async (event) => {
  const modeButton = event.target.closest('button[data-mode]');
  if (modeButton) {
    captureEdits();
    state.descriptionMode = modeButton.dataset.mode;
    renderDetail();
    return;
  }
  const actionButton = event.target.closest('button[data-action]');
  if (!actionButton) {
    return;
  }
  const draftId = state.selectedId;
  try {
    if (actionButton.dataset.action === 'save') {
      captureEdits();
      const problem = state.detail.draft.problem;
      await request('PUT', `/api/drafts/${draftId}`, {
        title: elements.detail.querySelector('#title-input').value,
        level: elements.detail.querySelector('#level-input').value,
        description: problem.description,
      });
      state.dirty = false;
      showToast('저장했습니다');
    } else if (actionButton.dataset.action === 'reject') {
      const reason = elements.detail.querySelector('#reject-reason').value.trim();
      if (!reason) {
        showToast('반려 사유를 입력하세요');
        return;
      }
      await request('POST', `/api/drafts/${draftId}/reject`, { reason });
      state.dirty = false;
      showToast('반려 사유를 붙여 다시 변형합니다');
    } else {
      await request('POST', `/api/drafts/${draftId}/${actionButton.dataset.action}`);
      showToast(actionButton.dataset.action === 'approve' ? '승인했습니다' : '대기열에 넣었습니다');
    }
    await loadList();
    await loadDetail();
  } catch (error) {
    showToast(error.message);
  }
});

async function poll() {
  try {
    await loadList();
    await loadDetail();
  } catch (error) {
    showToast(`서버 연결 실패: ${error.message}`);
  }
  setTimeout(poll, POLL_INTERVAL_MS);
}

poll();
