-- Forma de pagamento da transação. Só faz sentido para despesas:
-- receitas (salário etc.) não têm forma de pagamento.
-- O banco de dev foi resetado antes desta migration, então não há linhas
-- antigas para preencher (em produção, seria preciso um UPDATE de backfill
-- antes do segundo CHECK).
ALTER TABLE transactions ADD COLUMN payment_method VARCHAR(10);

-- Só valores conhecidos. NULL passa pelo CHECK (é o caso das receitas).
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_payment_method
    CHECK (payment_method IN ('PIX', 'DINHEIRO', 'DEBITO', 'CREDITO', 'BOLETO'));

-- Despesa TEM forma de pagamento; receita NÃO tem.
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_payment_method_by_type
    CHECK ((type = 'EXPENSE' AND payment_method IS NOT NULL)
        OR (type = 'INCOME' AND payment_method IS NULL));
