package com.bicavi.period;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Exige token, como todo /api/** (ver SecurityConfig). Hoje a regra é a mesma
// para todos os usuários, mas só quem está logado precisa dela.
@RestController
public class PeriodController {

    private final PeriodPolicy policy;

    public PeriodController(PeriodPolicy policy) {
        this.policy = policy;
    }

    // GET /api/period -> {"firstEditableMonth": "2026-10", "oldestVisibleMonth": "2026-04"}
    @GetMapping("/api/period")
    public PeriodResponse period() {
        return new PeriodResponse(policy.firstEditableMonth(), policy.oldestVisibleMonth());
    }
}
