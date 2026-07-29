-- ---------------------------------------------------------------------------
-- Ajout du moyen de paiement SARA au catalogue de chaque établissement.
--
-- Canal déclaratif, comme les cinq autres : le parent règle depuis son compte
-- SARA, conserve l'identifiant de transaction, et l'économat saisit
-- l'encaissement avec cette référence. L'application n'initie aucun débit.
--
-- Le numéro à créditer et les instructions définitives se saisissent ensuite
-- dans Finance → Moyens de paiement → Coordonnées (PUT /finance/channels/SARA).
-- ---------------------------------------------------------------------------
INSERT INTO payment_channel (school_id, code, label_fr, label_en, requires_reference,
                             visible_to_parents, sort_order, instructions_fr, instructions_en)
SELECT s.id, 'SARA', 'SARA', 'SARA', true, true, 6,
       'Depuis votre compte SARA, effectuez le transfert vers le numéro de l''école. Conservez l''ID de transaction et communiquez-le à l''économat.',
       'From your SARA account, transfer to the school number. Keep the transaction ID and give it to the bursary.'
  FROM school s
ON CONFLICT (school_id, code) DO NOTHING;
