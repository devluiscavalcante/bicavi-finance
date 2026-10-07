-- Nome de categoria único POR USUÁRIO, agora sem diferenciar maiúsculas de
-- minúsculas: "Cartão de Crédito BB" e "Cartão de crédito BB" são a mesma.
--
-- Uma constraint UNIQUE só aceita colunas, não expressões. Por isso trocamos
-- por um índice único sobre (user_id, LOWER(name)): o banco compara os nomes
-- já convertidos para minúsculas.
--
-- O banco de dev teve as duplicatas removidas à mão antes desta migration.
-- Em produção, seria preciso tratar as duplicatas existentes ANTES do
-- CREATE UNIQUE INDEX (ex.: renomear com ROW_NUMBER() OVER (PARTITION BY
-- user_id, LOWER(name))), senão a criação do índice falha.
ALTER TABLE categories DROP CONSTRAINT uk_categories_user_name;

-- user_id continua sendo a PRIMEIRA coluna, então o índice ainda serve para
-- as buscas só por user_id (listar as categorias do usuário).
CREATE UNIQUE INDEX uk_categories_user_lower_name ON categories (user_id, LOWER(name));
