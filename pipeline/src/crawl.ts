import { chromium, type Browser } from 'playwright';
import { draftIdOf, writeRaw, type RawProblem } from './workspace.ts';

const PROGRAMMERS_LESSON_URL = 'https://school.programmers.co.kr/learn/courses/30/lessons/';
const DELAY_BETWEEN_REQUESTS_MS = 2500;

let browser: Browser | null = null;
let lastRequestAt = 0;

async function politePage() {
  browser ??= await chromium.launch({ channel: 'chrome' });
  const waitMs = lastRequestAt + DELAY_BETWEEN_REQUESTS_MS - Date.now();
  if (waitMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, waitMs));
  }
  lastRequestAt = Date.now();
  return browser.newPage();
}

export function parseProgrammersLessonId(input: string) {
  const match = input.trim().match(/(?:lessons\/)?(\d+)/);
  if (!match) {
    throw new Error(`프로그래머스 문제 번호를 찾을 수 없습니다: ${input}`);
  }
  return match[1];
}

export async function crawlProgrammers(lessonId: string) {
  const url = `${PROGRAMMERS_LESSON_URL}${lessonId}`;
  const page = await politePage();
  try {
    await page.goto(`${url}?language=java`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.guide-section-description .markdown', { timeout: 15000 });
    const scraped = await page.evaluate(() => {
      const markdown = document.querySelector('.guide-section-description .markdown') as HTMLElement;
      const breadcrumb = [...document.querySelectorAll('ol li')].map((element) => element.textContent?.trim() ?? '');
      const levelMatch = document.body.innerText.match(/Lv\.\s*(\d)/);
      return {
        title: breadcrumb.at(-1) ?? document.title,
        category: breadcrumb.length >= 3 ? breadcrumb[1] : null,
        level: levelMatch ? Number(levelMatch[1]) : null,
        descriptionHtml: markdown.innerHTML,
        descriptionText: markdown.innerText,
        javaStarter: (document.querySelector('textarea#code') as HTMLTextAreaElement | null)?.value ?? '',
      };
    });
    const raw: RawProblem = {
      source: 'programmers',
      sourceId: lessonId,
      url,
      ...scraped,
      crawledAt: new Date().toISOString(),
    };
    await writeRaw(raw);
    return { draftId: draftIdOf(raw.source, raw.sourceId), raw };
  } finally {
    await page.close();
  }
}

export function parseCodetreeUrl(input: string) {
  const match = input.trim().match(/^https:\/\/www\.codetree\.ai\/[a-z]{2}\/frequent-problems\/([\w-]+)\/problems\/([\w-]+)/);
  return match ? { company: match[1], slug: match[2] } : null;
}

export async function crawlCodetree(company: string, slug: string) {
  const url = `https://www.codetree.ai/ko/frequent-problems/${company}/problems/${slug}/description`;
  const page = await politePage();
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => document.body.innerText.includes('Time Limit'), undefined, { timeout: 20000 });
    const scraped = await page.evaluate(() => {
      const containers = [...document.querySelectorAll('div')].filter((element) => {
        const text = element.innerText ?? '';
        return text.includes('총 제출') && text.includes('Time Limit') && !text.includes('코드 초기화');
      });
      const description = containers.sort((first, second) => first.innerText.length - second.innerText.length)[0].cloneNode(true) as HTMLElement;
      description.querySelectorAll('.katex').forEach((katex) => {
        const tex = katex.querySelector('annotation')?.textContent;
        if (tex) {
          katex.replaceWith(`$${tex}$`);
        }
      });
      const lines = document.body.innerText.split('\n').map((line) => line.trim()).filter(Boolean);
      const breadcrumbIndex = lines.findIndex((line) => line.startsWith('기출 문제 /'));
      const host = document.createElement('div');
      host.style.position = 'absolute';
      host.style.left = '-99999px';
      host.append(description);
      document.body.append(host);
      const fullText = description.innerText;
      host.remove();
      const bodyStart = fullText.search(/총 제출[^\n]*\n/);
      const afterHeader = bodyStart >= 0 ? fullText.slice(bodyStart).replace(/^총 제출[^\n]*\n/, '') : fullText;
      const descriptionText = afterHeader.split('이 콘텐츠가 도움이 되었나요?')[0].replace(/\n접기\n/g, '\n').trim();
      return {
        title: lines[breadcrumbIndex + 1] ?? document.title,
        category: breadcrumbIndex >= 0 ? lines[breadcrumbIndex].replace('기출 문제 /', '').trim() : null,
        descriptionHtml: description.innerHTML,
        descriptionText,
      };
    });
    const raw: RawProblem = {
      source: 'codetree',
      sourceId: slug,
      url,
      title: scraped.title,
      category: scraped.category,
      level: null,
      descriptionHtml: scraped.descriptionHtml,
      descriptionText: scraped.descriptionText,
      javaStarter: '',
      crawledAt: new Date().toISOString(),
    };
    await writeRaw(raw);
    return { draftId: draftIdOf(raw.source, raw.sourceId), raw };
  } finally {
    await page.close();
  }
}

export async function closeBrowser() {
  await browser?.close();
  browser = null;
}
