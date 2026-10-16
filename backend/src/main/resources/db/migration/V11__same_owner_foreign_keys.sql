-- A categoria e o cartão de uma transação precisam ser do MESMO dono da transação.
--
-- Até aqui, quem garantia isso era só o código Java (findByIdAndUserId nos
-- services). As FKs apontavam apenas para o id: o banco aceitaria uma transação
-- da Alice ligada à categoria do Bob, se um bug ou um endpoint novo esquecesse
-- a checagem. Agora a FK aponta para o PAR (dono, id): categoria de outro dono
-- simplesmente não existe para a transação. A regra passa a valer no código E
-- no banco (defesa em profundidade).
--
-- Se algum dado já estiver misturado, esta migration falha e o app não sobe:
-- melhor descobrir assim do que conviver com o dado errado.

-- ===== categories =====

-- Uma FK só pode apontar para colunas com garantia de unicidade. O id sozinho
-- já é único (PRIMARY KEY), então o par também é; mas o PostgreSQL exige uma
-- constraint declarada exatamente sobre (user_id, id).
ALTER TABLE categories ADD CONSTRAINT uk_categories_user_id UNIQUE (user_id, id);

-- Mesmo nome da FK antiga: as mensagens de erro continuam as mesmas.
ALTER TABLE transactions DROP CONSTRAINT fk_transactions_category;
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_category
    FOREIGN KEY (user_id, category_id) REFERENCES categories (user_id, id)
    -- Excluir a categoria deixa a transação sem categoria, como antes.
    -- A lista "(category_id)" é essencial (PostgreSQL 15+): sem ela, o banco
    -- tentaria zerar TODAS as colunas da FK, inclusive user_id, que é NOT NULL.
    ON DELETE SET NULL (category_id);

-- ===== cards =====

ALTER TABLE cards ADD CONSTRAINT uk_cards_user_id UNIQUE (user_id, id);

ALTER TABLE transactions DROP CONSTRAINT fk_transactions_card;
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_card
    FOREIGN KEY (user_id, card_id) REFERENCES cards (user_id, id)
    ON DELETE SET NULL (card_id);

-- Os índices de category_id e card_id (V2 e V8) continuam servindo: ao excluir
-- uma categoria ou um cartão, o banco procura as transações por esse id.
