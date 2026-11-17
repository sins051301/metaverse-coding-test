import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { appendLog, listDrafts, migrationDirectory, repositoryDirectory, writeDraft } from './workspace.ts';

function sqlString(value: string) {
  return `'${value.replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

async function readMigrations() {
  const fileNames = (await readdir(migrationDirectory)).filter((fileName) => /^V\d+__.+\.sql$/.test(fileName));
  const contents = await Promise.all(fileNames.map((fileName) => readFile(path.join(migrationDirectory, fileName), 'utf8')));
  const latestVersion = Math.max(...fileNames.map((fileName) => Number(fileName.match(/^V(\d+)__/)![1])));
  const problemIds = contents.flatMap((content) =>
    [...content.matchAll(/INSERT INTO problems \([^)]*\) VALUES \((\d+),/g)].map((match) => Number(match[1])),
  );
  return { latestVersion, latestProblemId: Math.max(0, ...problemIds) };
}

export async function publishApproved() {
  const approved = (await listDrafts()).filter((draft) => draft.status === 'approved' && draft.problem && draft.verification?.passed);
  if (approved.length === 0) {
    throw new Error('승인된 문제가 없습니다');
  }
  const { latestVersion, latestProblemId } = await readMigrations();
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const fileName = `V${latestVersion + 1}__add_problems_${today}.sql`;
  const statements = [`-- pipeline 으로 생성한 문제 ${approved.length}개 (변형·검증·사람 검수 완료)`, ''];

  approved.forEach((draft, index) => {
    const problemId = latestProblemId + index + 1;
    const problem = draft.problem!;
    statements.push(
      `INSERT INTO problems (id, title, level, description, return_type, time_limit_ms, memory_limit_mb) VALUES (${problemId}, ${sqlString(problem.title)}, ${problem.level}, ${sqlString(problem.description)}, ${sqlString(problem.returnType)}, ${problem.timeLimitMs}, DEFAULT);`,
    );
    problem.parameters.forEach((parameter, position) => {
      statements.push(
        `INSERT INTO problem_parameters (problem_id, position, name, type) VALUES (${problemId}, ${position + 1}, ${sqlString(parameter.name)}, ${sqlString(parameter.type)});`,
      );
    });
    draft.verification!.tests.forEach((test, sequence) => {
      statements.push(
        `INSERT INTO test_cases (problem_id, sequence, hidden, input, expected) VALUES (${problemId}, ${sequence + 1}, ${test.hidden ? 'TRUE' : 'FALSE'}, ${sqlString(JSON.stringify(test.input))}, ${sqlString(JSON.stringify(test.expected))});`,
      );
    });
    statements.push('');
  });

  const filePath = path.join(migrationDirectory, fileName);
  await writeFile(filePath, statements.join('\n'));
  const relativePath = path.relative(repositoryDirectory, filePath).replace(/\\/g, '/');
  await Promise.all(
    approved.map((draft, index) => {
      draft.status = 'published';
      draft.publishedProblemId = latestProblemId + index + 1;
      draft.publishedFile = relativePath;
      appendLog(draft, `${relativePath} 에 문제 ${draft.publishedProblemId}번으로 생성`);
      return writeDraft(draft);
    }),
  );
  return { file: relativePath, count: approved.length, firstProblemId: latestProblemId + 1 };
}
