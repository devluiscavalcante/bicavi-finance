-- Liga as parcelas de uma mesma compra, para editar/excluir "esta e as próximas".
--
--   installment_group   identificador da compra (o mesmo em todas as parcelas)
--   installment_number  qual parcela é esta (1, 2, 3...)
--   installment_count   total de parcelas da compra
--
-- Transação comum (à vista, receita): as três colunas ficam NULL.
-- Parcelas criadas antes desta migration também ficam NULL: o banco não tem
-- como saber quais estavam ligadas (a descrição "(1/3)" era só texto).
ALTER TABLE transactions ADD COLUMN installment_group UUID;
ALTER TABLE transactions ADD COLUMN installment_number INTEGER;
ALTER TABLE transactions ADD COLUMN installment_count INTEGER;

-- As três andam juntas: ou é parcela (todas preenchidas e coerentes) ou não é.
-- Os IS NOT NULL são necessários: "NULL BETWEEN 2 AND 24" não é falso, é
-- desconhecido (NULL), e um CHECK só recusa quando o resultado é FALSO.
-- Sem eles, uma linha só com o grupo preenchido passaria.
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_installment CHECK (
    (installment_group IS NULL AND installment_number IS NULL AND installment_count IS NULL)
    OR (installment_group IS NOT NULL
        AND installment_number IS NOT NULL
        AND installment_count IS NOT NULL
        AND installment_count BETWEEN 2 AND 24
        AND installment_number BETWEEN 1 AND installment_count)
);

-- "Esta e as próximas" busca as parcelas pelo grupo.
CREATE INDEX idx_transactions_installment_group ON transactions (installment_group);
