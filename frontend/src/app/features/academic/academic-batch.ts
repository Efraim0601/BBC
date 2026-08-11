import { BulletinBatchItemView, BulletinBatchJobView, BulletinBatchPreviewRow, BulletinBatchRepairTarget } from './academic.api';

const arg = (row: { messageArgs?: Record<string, unknown>; headlineArgs?: Record<string, unknown> } | null | undefined, key: string, fallback: string): string => {
  const value = row?.messageArgs?.[key] ?? row?.headlineArgs?.[key];
  return value == null || value === '' ? fallback : String(value);
};

export function batchReasonText(code: string | null | undefined, fr: boolean, periodCode = '', studentName = '', args?: Record<string, unknown>): string {
  const period = args?.['periodCode'] == null ? periodCode || (fr ? 'la période sélectionnée' : 'the selected period') : String(args['periodCode']);
  const student = args?.['studentName'] == null ? studentName || (fr ? "l'élève" : 'the student') : String(args['studentName']);
  switch (code) {
    case 'REPORT_NOT_CREATED':
    case 'REPORT_NOT_PUBLISHED_LEGACY':
      return fr ? `Aucun bulletin ${period} n\u2019a encore été créé ou publié pour ${student}.` : `No ${period} report card has been created or published for ${student}.`;
    case 'REPORT_DRAFT':
      return fr ? `Le bulletin ${period} de ${student} est encore en brouillon. Finalisez-le puis validez-le.` : `The ${period} report card for ${student} is still a draft. Complete and validate it.`;
    case 'REPORT_RETURNED':
      return fr ? `Le bulletin ${period} de ${student} a été retourné pour correction. Corrigez-le, validez-le puis publiez-le.` : `The ${period} report card for ${student} was returned for correction. Correct, validate, and publish it.`;
    case 'REPORT_VALIDATED_NOT_PUBLISHED':
      return fr ? `Le bulletin ${period} de ${student} est validé mais pas encore publié. Publiez-le avant l\u2019export officiel.` : `The ${period} report card for ${student} is validated but not published. Publish it before official export.`;
    case 'REPORT_SUPERSEDED_ONLY':
      return fr ? `Seules des versions supersédées du bulletin ${period} de ${student} existent. Créez ou actualisez la version active.` : `Only superseded versions of the ${period} report card for ${student} exist. Create or refresh the active version.`;
    case 'REPORT_STALE':
      return fr ? `Le bulletin ${period} de ${student} doit être actualisé avant publication.` : `The ${period} report card for ${student} must be refreshed before publication.`;
    case 'REPORT_PUBLICATION_REVOKED':
      return fr ? `La publication du bulletin ${period} de ${student} n\u2019est plus éligible. Résolvez l\u2019état de correction.` : `Publication of the ${period} report card for ${student} is no longer eligible. Resolve its correction state.`;
    case 'REPORT_PUBLICATION_CHANGED':
      return fr ? `La publication du bulletin ${period} de ${student} a changé depuis la préparation. Revérifiez l\u2019éligibilité.` : `The ${period} publication for ${student} changed after preparation. Recheck eligibility.`;
    case 'ENROLLMENT_MISSING':
      return fr ? `${student} n\u2019est plus inscrit activement dans la classe sélectionnée.` : `${student} is no longer actively enrolled in the selected class.`;
    case 'SNAPSHOT_UNREADABLE':
      return fr ? `Le bulletin ${period} de ${student} est illisible. Consultez la référence technique.` : `The ${period} report-card snapshot for ${student} is unreadable. Consult the technical reference.`;
    case 'PDF_RENDER_FAILED':
      return fr ? `Le PDF de ${student} n\u2019a pas pu être créé. Utilisez la relance technique.` : `The PDF for ${student} could not be created. Use technical retry.`;
    case 'DOCUMENT_REGISTRATION_FAILED':
      return fr ? `L\u2019enregistrement officiel du PDF de ${student} a échoué. Une relance idempotente est possible.` : `Official registration for ${student} failed. An idempotent retry is available.`;
    case 'STORAGE_FAILED':
      return fr ? `Le stockage du PDF de ${student} a échoué. Une relance technique est possible.` : `PDF storage for ${student} failed. A technical retry is available.`;
    case 'UNEXPECTED_GENERATION_ERROR':
      return fr ? `Une erreur technique a interrompu la génération de ${student}. Consultez la référence avant de relancer.` : `A technical error interrupted generation for ${student}. Consult the reference before retrying.`;
    case 'PUBLISHED':
      return fr ? `Le bulletin ${period} de ${student} a été généré.` : `${period} report card for ${student} was generated.`;
    default:
      return fr ? `Le résultat de ${student} nécessite une vérification.` : `The result for ${student} needs review.`;
  }
}

export function batchItemText(item: Pick<BulletinBatchItemView, 'resultCode' | 'studentName' | 'messageArgs'>, fr: boolean, periodCode = ''): string {
  return batchReasonText(item.resultCode, fr, periodCode, item.studentName, item.messageArgs);
}

export function batchResultCategory(job: Pick<BulletinBatchJobView, 'resultCategory' | 'status' | 'totalItems' | 'publishedItems' | 'blockedItems' | 'errorItems'>): string {
  if (job.resultCategory) return job.resultCategory;
  if (job.status === 'CANCELLED') return 'CANCELLED';
  if (job.status === 'QUEUED' || job.status === 'RUNNING') return 'RUNNING';
  if (job.publishedItems === job.totalItems && job.blockedItems === 0 && job.errorItems === 0) return 'SUCCESS';
  if (job.publishedItems > 0 && (job.blockedItems > 0 || job.errorItems > 0)) return 'PARTIAL';
  if (job.publishedItems === 0 && job.blockedItems > 0 && job.errorItems === 0) return 'BLOCKED';
  return 'FAILED';
}

export function batchHeadlineText(job: BulletinBatchJobView, fr: boolean): string {
  const category = batchResultCategory(job);
  const period = arg(job, 'periodCode', fr ? 'la période sélectionnée' : 'the selected period');
  switch (category) {
    case 'SUCCESS': return fr ? `Génération terminée — ${job.publishedItems} bulletin${job.publishedItems > 1 ? 's' : ''} ${period} généré${job.publishedItems > 1 ? 's' : ''}.` : `Generation complete — ${job.publishedItems} ${period} report card${job.publishedItems === 1 ? '' : 's'} generated.`;
    case 'PARTIAL': return fr ? `Génération partiellement terminée — ${job.publishedItems} bulletin${job.publishedItems > 1 ? 's' : ''} généré${job.publishedItems > 1 ? 's' : ''}, ${job.blockedItems + job.errorItems} élève${job.blockedItems + job.errorItems > 1 ? 's' : ''} nécessite${job.blockedItems + job.errorItems > 1 ? 'nt' : ''} une action.` : `Generation partially complete — ${job.publishedItems} generated, ${job.blockedItems + job.errorItems} student${job.blockedItems + job.errorItems === 1 ? ' needs' : 's need'} action.`;
    case 'BLOCKED': return fr ? `Aucun bulletin ${period} n\u2019a été généré — ${job.blockedItems} élève${job.blockedItems > 1 ? 's' : ''} nécessite${job.blockedItems > 1 ? 'nt' : ''} une publication.` : `No ${period} report card was generated — ${job.blockedItems} student${job.blockedItems === 1 ? ' needs' : 's need'} publication.`;
    case 'FAILED': return fr ? `Génération échouée — une intervention technique peut être nécessaire.` : `Generation failed — technical intervention may be required.`;
    case 'CANCELLED': return fr ? 'Génération annulée.' : 'Generation cancelled.';
    default: return fr ? 'Génération en cours.' : 'Generation in progress.';
  }
}

export function batchRepairUrl(target: BulletinBatchRepairTarget): string {
  const query = Object.entries(target.query).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&');
  return `${target.route}${query ? `?${query}` : ''}`;
}

export function batchFormatBytes(bytes: number | null | undefined): string {
  if (!bytes || bytes < 1024) return `${bytes ?? 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1).replace(/\.0$/, '')} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '')} MB`;
}

export function batchBlockedRows(rows: BulletinBatchPreviewRow[], showAll: boolean): BulletinBatchPreviewRow[] {
  const blocked = rows.filter((row) => row.eligibility !== 'READY');
  return showAll || blocked.length <= 8 ? blocked : blocked.slice(0, 8);
}
