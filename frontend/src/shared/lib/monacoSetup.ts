import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'
import TypeScriptWorker from 'monaco-editor/language/typescript/ts.worker?worker'

// @monaco-editor/react는 기본으로 CDN에서 Monaco를 받는다. 설치한 패키지를 쓰도록 바꿔 버전을 고정하고 외부 요청을 없앤다.
// 이 모듈은 React.lazy로 불러오는 에디터·코드 보기 컴포넌트만 import하므로, 아래 초기화는 Monaco가 처음 필요할 때 한 번만 돈다.
self.MonacoEnvironment = {
  getWorker: (_workerId, label) =>
    label === 'typescript' || label === 'javascript'
      ? new TypeScriptWorker()
      : new EditorWorker(),
}
loader.config({ monaco })

export const MONACO_THEME = 'pixel-lounge'

// Monaco 테마는 hex만 받는다. tokens.css의 oklch 값을 sRGB로 바꾼 값이다
monaco.editor.defineTheme(MONACO_THEME, {
  base: 'vs-dark',
  inherit: true,
  rules: [
    { token: 'keyword', foreground: 'ee97c9' },
    { token: 'type', foreground: '76d4ce' },
    { token: 'type.identifier', foreground: '76d4ce' },
    { token: 'number', foreground: 'fcb67c' },
    { token: 'string', foreground: 'e3d48f' },
    { token: 'comment', foreground: '887e74' },
    { token: 'identifier', foreground: 'f2eee6' },
  ],
  colors: {
    'editor.background': '#130e0b',
    'editor.foreground': '#f2eee6',
    'editorGutter.background': '#130e0b',
    'editorLineNumber.foreground': '#887e74',
    'editorLineNumber.activeForeground': '#f2eee6',
    'editor.lineHighlightBackground': '#1f1915',
    'editor.lineHighlightBorder': '#1f1915',
    'editor.selectionBackground': '#ffb37c55',
    'editorCursor.foreground': '#ffb37c',
    'editorWidget.background': '#312a24',
  },
})

// 글꼴이 늦게 도착하면 Monaco가 기본 글꼴 폭으로 커서 위치를 계산해 둔다. 도착한 뒤 다시 잰다
void document.fonts.ready.then(() => monaco.editor.remeasureFonts())

export const MONACO_LINE_HEIGHT = 24
export const MONACO_VERTICAL_PADDING = 12

export const BASE_EDITOR_OPTIONS: monaco.editor.IStandaloneEditorConstructionOptions =
  {
    fontFamily: "'JetBrains Mono', 'Gowun Dodum', monospace",
    fontSize: 14,
    lineHeight: MONACO_LINE_HEIGHT,
    tabSize: 4,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    padding: { top: MONACO_VERTICAL_PADDING, bottom: MONACO_VERTICAL_PADDING },
    renderLineHighlight: 'line',
    overviewRulerLanes: 0,
  }
