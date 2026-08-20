import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const files = [
  'src/app/features/settings/foundation-settings.ts',
  'src/app/features/settings/session-configuration-copy.ts',
  'src/app/features/settings/term-management-windows.ts'
];
const forbidden = [
  'gradeEntryOpensAt', 'teacherSubmissionOpensAt', 'windowRules', 'windowOverrides',
  'effectiveWindow', 'WorkflowWindowRules', 'GRADE_ENTRY', 'TEACHER_SUBMISSION',
  'EMERGENCY_OVERRIDE', 'WORKFLOW_WINDOW'
];

for (const relative of files) {
  const source = readFileSync(resolve(process.cwd(), relative), 'utf8');
  for (const token of forbidden) {
    if (source.includes(token)) {
      throw new Error(`${relative} still exposes legacy workflow-window token: ${token}`);
    }
  }
}

console.log(`Simplified settings source check passed for ${files.length} files.`);
