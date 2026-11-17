package com.gs5.screening.domain;

import java.util.Optional;

public interface ScreeningCaseRepository {

    Optional<ScreeningCase> findById(CustomerId customerId);

    boolean exists(CustomerId customerId);

    void save(ScreeningCase screeningCase);
}
