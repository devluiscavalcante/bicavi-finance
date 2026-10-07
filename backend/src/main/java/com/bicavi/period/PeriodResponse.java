package com.bicavi.period;

import java.time.YearMonth;

// Limites calculados pelo backend para a data de hoje. O frontend usa isto para
// esconder ações e limitar a navegação, sem repetir a regra em TypeScript.
public record PeriodResponse(YearMonth firstEditableMonth, YearMonth oldestVisibleMonth) {
}
