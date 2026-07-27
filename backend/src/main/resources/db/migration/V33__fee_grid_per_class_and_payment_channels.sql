-- Paiement progressif de la scolarité : grille de frais par classe, tranches
-- nommées avec échéance, et catalogue des moyens de paiement (OM, MOMO, MPGS…).

-- ---------------------------------------------------------------- 1. grille
-- class_id NULL  = grille par défaut du niveau (comportement actuel)
-- class_id posé  = surcharge appliquée aux élèves de cette classe
ALTER TABLE fee_config ADD COLUMN class_id uuid REFERENCES school_class(id) ON DELETE CASCADE;

-- L'unicité (school, level, subsystem) ne vaut plus que pour les grilles de niveau :
-- sans cela, une surcharge de classe entrerait en conflit avec son propre niveau.
ALTER TABLE fee_config DROP CONSTRAINT IF EXISTS fee_config_school_id_level_subsystem_key;

CREATE UNIQUE INDEX fee_config_level_uniq
    ON fee_config (school_id, level, coalesce(subsystem, '*'))
    WHERE class_id IS NULL;

CREATE UNIQUE INDEX fee_config_class_uniq
    ON fee_config (school_id, class_id)
    WHERE class_id IS NOT NULL;

-- ------------------------------------------------------------- 2. tranches
-- [40000, 30000] devient
-- [{"label":"T1","amount":40000,"dueOn":null}, {"label":"T2","amount":30000,"dueOn":null}]
UPDATE fee_config
   SET tranches = coalesce((
        SELECT jsonb_agg(
                   jsonb_build_object('label', 'T' || ord, 'amount', val, 'dueOn', NULL)
                   ORDER BY ord)
          FROM jsonb_array_elements(tranches) WITH ORDINALITY AS t(val, ord)
       ), '[]'::jsonb)
 WHERE jsonb_typeof(tranches) = 'array'
   AND jsonb_typeof(tranches -> 0) = 'number';

-- ------------------------------------------------- 3. paiements : canal + référence
ALTER TABLE payment ADD COLUMN reference varchar(64);

COMMENT ON COLUMN payment.method IS
    'Code du canal de paiement (payment_channel.code) : CASH, OM, MOMO, MPGS, TRANSFER…';
COMMENT ON COLUMN payment.reference IS
    'Référence de la transaction chez l''opérateur (ID Orange Money / MoMo, n° d''autorisation MPGS).';

-- Les libellés libres saisis jusqu'ici deviennent des codes de canal.
UPDATE payment SET method = CASE
        WHEN lower(method) IN ('espèces', 'especes', 'cash', 'liquide') THEN 'CASH'
        WHEN lower(method) IN ('mobile money', 'momo', 'mtn', 'mtn momo') THEN 'MOMO'
        WHEN lower(method) IN ('orange money', 'om', 'orange') THEN 'OM'
        WHEN lower(method) IN ('virement', 'transfer', 'banque', 'bank') THEN 'TRANSFER'
        WHEN lower(method) IN ('carte', 'card', 'mpgs') THEN 'MPGS'
        ELSE upper(left(method, 20))
    END;

-- ------------------------------------------------------ 4. canaux de paiement
CREATE TABLE payment_channel (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id          uuid NOT NULL REFERENCES school(id),
    code               varchar(20) NOT NULL,
    label_fr           varchar(80) NOT NULL,
    label_en           varchar(80) NOT NULL,
    -- Coordonnées communiquées aux parents : numéro Orange Money / MoMo,
    -- identifiant marchand MPGS, RIB pour un virement.
    account_ref        varchar(120),
    account_name       varchar(120),
    instructions_fr    text,
    instructions_en    text,
    requires_reference boolean NOT NULL DEFAULT false,
    enabled            boolean NOT NULL DEFAULT true,
    visible_to_parents boolean NOT NULL DEFAULT true,
    sort_order         integer NOT NULL DEFAULT 0,
    UNIQUE (school_id, code)
);

-- Catalogue initial pour chaque établissement déjà présent.
INSERT INTO payment_channel (school_id, code, label_fr, label_en, requires_reference,
                             visible_to_parents, sort_order, instructions_fr, instructions_en)
SELECT s.id, c.code, c.label_fr, c.label_en, c.requires_reference,
       c.visible_to_parents, c.sort_order, c.instructions_fr, c.instructions_en
  FROM school s
 CROSS JOIN (VALUES
    ('CASH', 'Espèces', 'Cash', false, false, 1,
     'Versement au guichet de l''économat, contre reçu.',
     'Payment at the bursary desk, against a receipt.'),
    ('OM', 'Orange Money', 'Orange Money', true, true, 2,
     'Composez #150*1# puis suivez « Transfert d''argent » vers le numéro de l''école. Conservez l''ID de transaction et communiquez-le à l''économat.',
     'Dial #150*1#, choose “Money transfer” to the school number. Keep the transaction ID and give it to the bursary.'),
    ('MOMO', 'MTN Mobile Money', 'MTN Mobile Money', true, true, 3,
     'Composez *126# puis « Transfert » vers le numéro de l''école. Conservez l''ID de transaction et communiquez-le à l''économat.',
     'Dial *126#, choose “Transfer” to the school number. Keep the transaction ID and give it to the bursary.'),
    ('MPGS', 'Carte bancaire (MPGS)', 'Bank card (MPGS)', true, true, 4,
     'Paiement par carte auprès de la banque partenaire. Présentez le numéro d''autorisation à l''économat.',
     'Card payment through the partner bank. Show the authorisation number to the bursary.'),
    ('TRANSFER', 'Virement bancaire', 'Bank transfer', true, true, 5,
     'Virement sur le compte de l''établissement, en précisant le matricule de l''élève.',
     'Transfer to the school account, quoting the student ID.')
 ) AS c(code, label_fr, label_en, requires_reference, visible_to_parents, sort_order,
        instructions_fr, instructions_en)
ON CONFLICT (school_id, code) DO NOTHING;
