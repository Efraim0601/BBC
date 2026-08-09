-- Legacy demo assessments used a suffix to identify the subject but were
-- created before subject-scoped assessment definitions existed. Preserve
-- genuinely generic definitions and scope only the unambiguous suffixes.
UPDATE academic_assessment
   SET subject_code = upper(split_part(code, '_', 2))
 WHERE subject_code IS NULL
   AND upper(split_part(code, '_', 2)) IN ('FR', 'EN', 'MATH', 'SVT', 'PC');
