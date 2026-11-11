import { Editor } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import {
  BASE_EDITOR_OPTIONS,
  MONACO_LINE_HEIGHT,
  MONACO_THEME,
  MONACO_VERTICAL_PADDING,
} from '@/shared/lib/monacoSetup'

const VIEWER_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
  ...BASE_EDITOR_OPTIONS,
  readOnly: true,
  domReadOnly: true,
  renderLineHighlight: 'none',
  contextmenu: false,
}

const MAX_VISIBLE_LINES = 16

// 줄 수만큼 높이를 잡고, 길면 16줄에서 멈춘 뒤 안에서 스크롤한다
export function CodeViewer({
  code,
  language,
}: {
  code: string
  language: string
}) {
  const visibleLines = Math.min(code.split('\n').length, MAX_VISIBLE_LINES)
  const height = visibleLines * MONACO_LINE_HEIGHT + MONACO_VERTICAL_PADDING * 2

  return (
    <Editor
      height={height}
      value={code}
      language={language}
      theme={MONACO_THEME}
      options={VIEWER_OPTIONS}
    />
  )
}
