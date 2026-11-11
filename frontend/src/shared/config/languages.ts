export type Language = 'JAVA' | 'JAVASCRIPT'

export const LANGUAGES: Record<
  Language,
  { label: string; monacoLanguage: string; fileName: string }
> = {
  JAVA: { label: 'Java', monacoLanguage: 'java', fileName: 'Solution.java' },
  JAVASCRIPT: {
    label: 'JavaScript',
    monacoLanguage: 'javascript',
    fileName: 'solution.js',
  },
}

export const LANGUAGE_ORDER: Language[] = ['JAVA', 'JAVASCRIPT']
