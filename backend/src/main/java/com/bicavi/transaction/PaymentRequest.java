package com.bicavi.transaction;

import jakarta.validation.constraints.NotNull;

// Boolean (objeto) e não boolean: sem o campo no JSON, o primitivo viraria
// false em silêncio e desmarcaria o pagamento. Com @NotNull, isso vira 400.
public record PaymentRequest(@NotNull(message = "Informe se a despesa está paga") Boolean paid) {
}
