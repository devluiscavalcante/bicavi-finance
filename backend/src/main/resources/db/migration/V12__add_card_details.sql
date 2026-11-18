-- Dados que aparecem no desenho do cartão (aba Cartões): bandeira, titular,
-- últimos 4 dígitos, validade e cor.
--
-- O que NÃO entra, de propósito: o número completo e o código de segurança (CVV).
-- O app não faz pagamento nenhum, só organiza os gastos. Os 4 últimos dígitos já
-- aparecem em qualquer comprovante de compra: servem para reconhecer o cartão,
-- não para comprar com ele. Guardar o número completo traria a obrigação de
-- protegê-lo (norma PCI DSS) sem nenhum ganho para o app.
--
-- Todas as colunas aceitam NULL: os cartões cadastrados antes desta migration só
-- têm o nome, e o banco não tem como inventar o resto. Quem exige os dados nos
-- cartões NOVOS e nos EDITADOS é a API (CardRequest), a mesma ideia da V8
-- ("crédito exige cartão" fica no service). O app pede para completar os antigos.
ALTER TABLE cards ADD COLUMN brand        VARCHAR(20);
ALTER TABLE cards ADD COLUMN holder_name  VARCHAR(40);
ALTER TABLE cards ADD COLUMN last_four    VARCHAR(4);
ALTER TABLE cards ADD COLUMN expiry_month INTEGER;
ALTER TABLE cards ADD COLUMN expiry_year  INTEGER;
ALTER TABLE cards ADD COLUMN color        VARCHAR(20);

-- Só valores conhecidos: os mesmos dos enums CardBrand e CardColor.
-- NULL passa pelo CHECK (um CHECK só recusa quando o resultado é FALSO).
ALTER TABLE cards ADD CONSTRAINT ck_cards_brand
    CHECK (brand IN ('VISA', 'MASTERCARD', 'ELO', 'AMEX', 'HIPERCARD', 'OUTRA'));

ALTER TABLE cards ADD CONSTRAINT ck_cards_color
    CHECK (color IN ('AURORA', 'ROXO', 'PRETO'));

-- Exatamente 4 dígitos. Texto e não número: "0042" viraria 42 e perderia os zeros.
-- "~" compara com uma expressão regular: ^ início, [0-9]{4} quatro dígitos, $ fim.
ALTER TABLE cards ADD CONSTRAINT ck_cards_last_four
    CHECK (last_four ~ '^[0-9]{4}$');

-- Validade como está impressa no cartão (MM/AA), em duas colunas.
-- O ano fica entre 2000 e 2099 porque o cartão só mostra dois dígitos:
-- "29" é sempre 2029.
ALTER TABLE cards ADD CONSTRAINT ck_cards_expiry_month
    CHECK (expiry_month BETWEEN 1 AND 12);

ALTER TABLE cards ADD CONSTRAINT ck_cards_expiry_year
    CHECK (expiry_year BETWEEN 2000 AND 2099);

-- Os dados andam juntos: ou o cartão é antigo (nenhum preenchido) ou está
-- completo (todos). num_nulls conta quantos dos argumentos são NULL: 6 = nenhum
-- preenchido, 0 = todos. Um cartão "pela metade" (2, 3...) é recusado.
ALTER TABLE cards ADD CONSTRAINT ck_cards_details_all_or_none
    CHECK (num_nulls(brand, holder_name, last_four, expiry_month, expiry_year, color) IN (0, 6));
