-- Todo dado passa a ter dono. NOT NULL: não existe categoria/transação "de ninguém".
-- (Esta migration exige tabelas vazias; o banco de desenvolvimento foi recriado.)

-- ===== categories =====
ALTER TABLE categories ADD COLUMN user_id BIGINT NOT NULL;

-- ON DELETE CASCADE: se um usuário for excluído, os dados dele vão junto.
ALTER TABLE categories ADD CONSTRAINT fk_categories_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- Nome único POR USUÁRIO: dois usuários podem ter, cada um, sua "Mercado".
ALTER TABLE categories DROP CONSTRAINT uk_categories_name;
ALTER TABLE categories ADD CONSTRAINT uk_categories_user_name UNIQUE (user_id, name);
-- A constraint UNIQUE cria um índice em (user_id, name). Ele também serve para
-- buscas só por user_id, porque user_id é a PRIMEIRA coluna do índice.

-- ===== transactions =====
ALTER TABLE transactions ADD COLUMN user_id BIGINT NOT NULL;

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- A consulta mais comum agora é "transações DESTE usuário NESTE mês".
-- Um índice composto (user_id, occurred_on) atende exatamente isso e
-- substitui o índice antigo, só por data.
DROP INDEX idx_transactions_occurred_on;
CREATE INDEX idx_transactions_user_occurred_on ON transactions (user_id, occurred_on);
