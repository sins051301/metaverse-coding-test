import { Editor } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { BASE_EDITOR_OPTIONS, MONACO_THEME } from '@/shared/lib/monacoSetup'

type CodeEditorProps = {
  path: string
  language: string
  defaultValue: string
  onChange: (code: string) => void
  onMount: (codeEditor: editor.IStandaloneCodeEditor) => void
}

export function CodeEditor({
  path,
  language,
  defaultValue,
  onChange,
  onMount,
}: CodeEditorProps) {
  return (
    <Editor
      path={path}
      language={language}
      defaultValue={defaultValue}
      theme={MONACO_THEME}
      options={BASE_EDITOR_OPTIONS}
      onChange={(value) => onChange(value ?? '')}
      onMount={onMount}
    />
  )
}
