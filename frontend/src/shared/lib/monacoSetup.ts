import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'
import TypeScriptWorker from 'monaco-editor/language/typescript/ts.worker?worker'

self.MonacoEnvironment = {
  getWorker: (_workerId, label) =>
    label === 'typescript' || label === 'javascript'
      ? new TypeScriptWorker()
      : new EditorWorker(),
}
loader.config({ monaco })

export const MONACO_THEME = 'pixel-lounge'

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
