import { Editor } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { BASE_EDITOR_OPTIONS, MONACO_THEME } from '@/shared/lib/monacoSetup'

type CodeEditorProps = {
  path: string
  language: string
  defaultValue: string
  readOnly?: boolean
  onChange?: (code: string) => void
  onMount: (codeEditor: editor.IStandaloneCodeEditor) => void
}

const READ_ONLY_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
  ...BASE_EDITOR_OPTIONS,
  readOnly: true,
  domReadOnly: true,
}

export function CodeEditor({
  path,
  language,
  defaultValue,
  readOnly = false,
  onChange,
  onMount,
}: CodeEditorProps) {
  return (
    <Editor
      path={path}
      language={language}
      defaultValue={defaultValue}
      theme={MONACO_THEME}
      options={readOnly ? READ_ONLY_OPTIONS : BASE_EDITOR_OPTIONS}
      onChange={(value) => onChange?.(value ?? '')}
      onMount={onMount}
    />
  )
}
