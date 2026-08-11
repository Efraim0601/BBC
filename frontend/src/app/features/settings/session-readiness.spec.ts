import { describe, expect, it } from 'vitest';
import { SessionReadinessView } from '../../core/foundation.api';
import { ReadinessIssue, readinessIssueLabel, readinessIssueMessage, readinessIssues, readinessNextAction, readinessRepairUrl } from './session-readiness';

const assignmentIssue: ReadinessIssue = {
  code: 'CURRICULUM_ASSIGNMENT_MISSING', severity: 'WARNING', label: 'Teacher assignment needs repair',
  detail: 'The class subject has no active authoritative teacher assignment.', repairTarget: 'class-subjects', count: 1,
  scope: '5eme A · FRANC', classId: 'class-5eme', subjectId: 'subject-franc', subjectCode: 'FRANC',
  messageFr: "Aucun enseignant responsable actif n'est affecté à Français pour la classe 5eme A. Cette classe n'a actuellement aucun élève actif : cela n'empêche pas la préparation de la session, mais l'affectation devra être configurée avant son utilisation.",
  messageEn: 'No active responsible teacher is assigned to Français for 5eme A. This class has no active enrollments, so it does not block session readiness; configure the assignment before the class is used.',
};

const readiness = (issue: ReadinessIssue): SessionReadinessView => ({
  academicSessionId: 'session-2026-2027', sessionStatus: 'OPEN', phase: 'READY', ready: true,
  nextAction: 'The session is ready.', blockers: [], warnings: ['CURRICULUM_ASSIGNMENT_MISSING'], actions: [],
  sections: [{ key: 'CURRICULUM', label: 'Curriculum and assignments', status: 'WARNING', ready: true, issues: [issue] }],
});

describe('session readiness presentation contract', () => {
  it('keeps an unused class assignment out of blockers and provides an actionable repair link', () => {
    const view = readiness(assignmentIssue);

    expect(readinessIssues(view, 'BLOCKER')).toEqual([]);
    expect(readinessIssues(view, 'WARNING')).toHaveLength(1);
    expect(readinessIssueMessage(assignmentIssue, true)).toContain('aucun élève actif');
    expect(readinessIssueMessage(assignmentIssue, false)).toContain('does not block session readiness');
    expect(readinessIssueLabel(assignmentIssue, true)).toBe('Affectation enseignant à compléter');
    expect(readinessIssueLabel(assignmentIssue, false)).toBe('Teacher assignment needs repair');
    expect(readinessRepairUrl(view, assignmentIssue)).toBe('/settings?tab=academic&subtab=class-subjects&sessionId=session-2026-2027&classId=class-5eme&subjectCode=FRANC');
    expect(readinessNextAction(view, true)).toContain('session est prête');
    expect(readinessNextAction(view, false)).toContain('session is ready');
  });

  it('preserves a real active-class curriculum blocker as a blocker', () => {
    const issue = {
      ...assignmentIssue,
      code: 'CURRICULUM_MISSING', severity: 'BLOCKER', scope: '4eme A', classId: 'class-4eme',
      subjectId: null, subjectCode: null,
      messageFr: 'Aucune matière n’est configurée pour la classe 4eme A qui compte des élèves actifs.',
      messageEn: 'No class-subject curriculum is configured for 4eme A, which has active enrollments.',
    };
    const view = { ...readiness(issue), warnings: [], blockers: ['CURRICULUM_MISSING'], phase: 'BLOCKED', ready: false,
      sections: [{ key: 'CURRICULUM', label: 'Curriculum and assignments', status: 'BLOCKED', ready: false, issues: [issue] }] };

    expect(readinessIssues(view, 'BLOCKER')).toHaveLength(1);
    expect(readinessIssues(view, 'WARNING')).toEqual([]);
    expect(readinessIssueMessage(issue, true)).toContain('élèves actifs');
    expect(readinessIssueMessage(issue, false)).toContain('active enrollments');
    expect(readinessRepairUrl(view, issue)).toContain('classId=class-4eme');
  });
});
