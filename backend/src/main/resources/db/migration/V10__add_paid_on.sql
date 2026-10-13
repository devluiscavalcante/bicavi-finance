-- Data em que a despesa foi paga. NULL = ainda não paga.
--
-- Uma data em vez de um boolean: além de "paga ou não", diz QUANDO foi paga
-- ("paga em 03/11"), e NULL já representa o "não".
ALTER TABLE transactions ADD COLUMN paid_on DATE;

-- Só despesa é paga. Receita (salário) não tem esse conceito.
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_paid_only_expense
    CHECK (paid_on IS NULL OR type = 'EXPENSE');

-- Despesas lançadas antes de outubro/2026 (quando esta funcionalidade surgiu)
-- já foram pagas na vida real. Sem a data verdadeira, usamos a data da própria
-- despesa. De outubro em diante ficam como não pagas e são marcadas no app.
--
-- Data fixa (e não CURRENT_DATE) de propósito: o resultado não depende do dia
-- em que a migration roda em cada banco.
UPDATE transactions
SET paid_on = occurred_on
WHERE type = 'EXPENSE'
  AND occurred_on < DATE '2026-10-01';
