import { SessionReadinessView } from '../../core/foundation.api';

export type ReadinessIssue = NonNullable<SessionReadinessView['sections']>[number]['issues'][number];

export function readinessIssues(readiness: SessionReadinessView, severity: 'BLOCKER' | 'WARNING'): ReadinessIssue[] {
  return (readiness.sections ?? []).flatMap((section) => section.issues ?? []).filter((issue) => issue.severity === severity);
}

export function readinessIssueLabel(issue: ReadinessIssue, french: boolean): string {
  const labels: Record<string, [string, string]> = {
    CURRICULUM_MISSING: ['Curriculum de classe à configurer', 'Class curriculum needs configuration'],
    CURRICULUM_ASSIGNMENT_MISSING: ['Affectation enseignant à compléter', 'Teacher assignment needs repair'],
    TERM_ACCESS_INVALID: ['Limite d’accès par trimestre invalide', 'Trimester access limit is invalid'],
    TERM_MAPPING_MISSING: ['Jalon non lié à un trimestre', 'Milestone is not linked to a trimester'],
    REPORTING_STRUCTURE_MISSING: ['Structure des résultats manquante', 'Reporting structure is missing'],
    PERIOD_MISSING: ['Jalon de résultat manquant', 'Reporting milestone is missing'],
  };
  const [frLabel, enLabel] = labels[issue.code] ?? [issue.label, issue.label];
  return french ? frLabel : enLabel;
}

export function readinessIssueMessage(issue: ReadinessIssue, french: boolean): string {
  return french ? (issue.messageFr || issue.detail) : (issue.messageEn || issue.detail);
}

export function readinessNextAction(readiness: SessionReadinessView, french: boolean): string {
  if (readiness.phase === 'BLOCKED') return french ? 'Corrigez les blocages affichés avant d’utiliser cette session.' : 'Fix the listed blockers before using this session.';
  if (readiness.sessionStatus === 'DRAFT') return french ? 'Ouvrez la session après validation de sa structure et de ses règles d’accès.' : 'Open the session after validating its structure and access rules.';
  if ((readiness.warnings ?? []).length > 0) return french ? 'La session est prête ; vérifiez les avertissements de configuration des classes avant les soumissions.' : 'The session is ready; review the class configuration warnings before submissions.';
  if (readiness.sessionStatus === 'OPEN' && readiness.ready) {
    return french
      ? 'Les opérations restent soumises à vos droits, à l’état de la session et aux prérequis du dossier.'
      : 'Operations remain subject to your permissions, the session state, and the dossier prerequisites.';
  }
  return readiness.nextAction;
}

export function readinessRepairUrl(readiness: SessionReadinessView, issue: ReadinessIssue): string | null {
  if (issue.repairTarget === 'class-subjects' && issue.classId) {
    const subject = issue.subjectCode ? `&subjectCode=${encodeURIComponent(issue.subjectCode)}` : '';
    return `/settings?tab=academic&subtab=class-subjects&sessionId=${encodeURIComponent(readiness.academicSessionId)}&classId=${encodeURIComponent(issue.classId)}${subject}`;
  }
  if (issue.repairTarget === 'term-management-windows' || issue.repairTarget === 'academic-configuration-wizard') {
    return '/settings?tab=sessions';
  }
  return issue.repairTarget ? '/settings?tab=sessions' : null;
}
