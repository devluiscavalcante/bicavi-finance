-- Categorias de despesa padrão para as contas que JÁ existem.
-- Contas novas recebem as mesmas no cadastro (DefaultCategories, em Java).
--
-- A lista aparece nos dois lugares de propósito: esta migration é um retrato
-- do momento em que rodou (o Flyway proíbe alterá-la depois), e a lista em
-- Java pode evoluir sem mexer aqui.
--
-- CROSS JOIN: combina cada usuário com cada nome da lista (usuários x 12).
-- NOT EXISTS: pula a que o usuário já tem, sem diferenciar maiúsculas
-- ("alimentação" já cadastrada não vira uma segunda "Alimentação"), a mesma
-- regra do índice único da V6. Uma categoria de RECEITA com o mesmo nome
-- também é pulada: o índice não permitiria as duas.
INSERT INTO categories (user_id, name, type)
SELECT u.id, d.name, 'EXPENSE'
FROM users u
CROSS JOIN (VALUES ('Casa'), ('Transporte'), ('Saúde'), ('Educação'), ('Alimentação'),
                   ('Comunicação'), ('Dívidas'), ('Roupas'), ('Cuidados pessoais'),
                   ('Lazer'), ('Compras'), ('Investimentos')) AS d(name)
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = u.id AND LOWER(c.name) = LOWER(d.name)
);
