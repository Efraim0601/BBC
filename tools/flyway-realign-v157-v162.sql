-- ============================================================================
--  Réalignement de l'historique Flyway — V41…V46 → V157…V162
--
--  Contexte : les six migrations de la branche « promotion / clôture / admins
--  de section / import / ressources » ont été écrites quand la branche a
--  divergé de main, le 29/07, alors que le compteur était à V40. main est
--  depuis passé à V156, et les numéros V41 à V46 y désignent tout autre chose.
--  Elles ont donc été renumérotées dans le commit de fusion.
--
--  Ce script dit à Flyway sous quels noms ces migrations sont désormais
--  connues, dans une base qui les a déjà reçues sous les anciens numéros. Le
--  schéma en base est inchangé : rien n'est rejoué, seuls l'étiquette et la
--  somme de contrôle bougent.
--
--  Sommes de contrôle calculées avec l'algorithme de Flyway (CRC32 ligne à
--  ligne, sans les fins de ligne), et validées contre les valeurs déjà
--  enregistrées pour V41 et V42 — qui figurent dans le script précédent,
--  tools/flyway-realign-v37-v38.sql, et que ce calcul reproduit exactement.
--
--  ATTENTION — CE SCRIPT NE SUFFIT PAS À LUI SEUL.
--  Il libère les numéros V41 à V46, mais il ne règle pas le fait que cette
--  base n'a jamais vu les 116 migrations V41 à V156 de main. Une fois les
--  numéros libérés, Flyway les verra « en attente » avec des versions
--  INFÉRIEURES à la plus haute appliquée (162) : hors migration désordonnée
--  (`spring.flyway.out-of-order=true`), le démarrage échouera. Et rien ne dit
--  que ces 116 migrations s'appliquent proprement sur un schéma où la branche
--  a déjà créé ses propres tables. Voir la note en fin de fichier.
--
--  SAUVEGARDER AVANT :
--    docker exec bbc-prod-db-1 pg_dump -U bbc bbc_sms | gzip \
--      > backups/bbc-prod-avant-realign-v157-$(date +%Y%m%d-%H%M%S).sql.gz
-- ============================================================================

BEGIN;

-- On apparie sur le NOM du script, pas sur le numéro de version : c'est le
-- seul identifiant qui n'a pas bougé, et il reste juste que le script
-- précédent (V37/V38 → V41/V42) ait été passé ou non sur cette base.

UPDATE flyway_schema_history
   SET version = '157', script = 'V157__promotion.sql', checksum = 497795539
 WHERE script = 'V41__promotion.sql';

UPDATE flyway_schema_history
   SET version = '158', script = 'V158__year_closure.sql', checksum = 307214111
 WHERE script = 'V42__year_closure.sql';

UPDATE flyway_schema_history
   SET version = '159', script = 'V159__section_admin.sql', checksum = 1923780264
 WHERE script = 'V43__section_admin.sql';

UPDATE flyway_schema_history
   SET version = '160', script = 'V160__student_import_lookup.sql', checksum = -895031960
 WHERE script = 'V44__student_import_lookup.sql';

UPDATE flyway_schema_history
   SET version = '161', script = 'V161__drop_redundant_student_index.sql', checksum = -1958033515
 WHERE script = 'V45__drop_redundant_student_index.sql';

UPDATE flyway_schema_history
   SET version = '162', script = 'V162__resource_library.sql', checksum = 1131084985
 WHERE script = 'V46__resource_library.sql';

-- Contrôle : plus aucune ligne ne doit porter les anciens noms.
DO $$
DECLARE restants int;
BEGIN
    SELECT count(*) INTO restants
      FROM flyway_schema_history
     WHERE script IN ('V41__promotion.sql', 'V42__year_closure.sql',
                      'V43__section_admin.sql', 'V44__student_import_lookup.sql',
                      'V45__drop_redundant_student_index.sql', 'V46__resource_library.sql');
    IF restants > 0 THEN
        RAISE EXCEPTION 'Réalignement incomplet : % ligne(s) portent encore un ancien nom', restants;
    END IF;
END $$;

COMMIT;

-- ---------------------------------------------------------------------------
--  CE QUI RESTE À DÉCIDER, ET QUI NE SE RÈGLE PAS EN SQL
--
--  Cette base suit la lignée de la branche : V1…V40 communes, puis les six
--  ci-dessus. Elle n'a jamais reçu V41…V156 de main. Trois voies, à trancher
--  après avoir regardé le schéma réel :
--
--   1. Migration désordonnée. Poser `spring.flyway.out-of-order=true` le temps
--      d'un démarrage, laisser Flyway appliquer V41…V156, puis retirer le
--      réglage. Suppose que ces 116 migrations s'appliquent sans heurter les
--      objets déjà créés par la branche — à vérifier d'abord sur une copie.
--
--   2. Rejouer sur copie. Restaurer la sauvegarde dans une base jetable,
--      lancer le démarrage complet, relever ce qui casse, et n'écrire les
--      correctifs qu'ensuite. C'est la voie la plus lente et la plus sûre.
--
--   3. Repartir de la lignée de main. Monter une base neuve sur V1…V162 et y
--      réimporter les données métier. À envisager si (1) et (2) montrent trop
--      de collisions.
--
--  Inventaire préalable, à lire avant de choisir :
--    docker exec bbc-prod-db-1 psql -U bbc -d bbc_sms -c \
--      "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank;"
-- ---------------------------------------------------------------------------
