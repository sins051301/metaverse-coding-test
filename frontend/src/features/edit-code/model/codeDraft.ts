import type { Language } from '@/shared/config/languages'
import { readStorage, writeStorage } from '@/shared/lib/storage'

function draftKey(problemId: number, language: Language) {
  return `codingtest:v1:draft:${problemId}:${language}`
}

export function loadDraft(problemId: number, language: Language) {
  return readStorage(localStorage, draftKey(problemId, language))
}

export function saveDraft(problemId: number, language: Language, code: string) {
  writeStorage(localStorage, draftKey(problemId, language), code)
}
