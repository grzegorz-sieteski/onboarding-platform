package com.gs5.screening.infrastructure.config;

import com.gs5.screening.application.BlacklistManagement;
import com.gs5.screening.application.ScreeningCommandHandler;
import com.gs5.screening.application.ScreeningQueries;
import com.gs5.screening.application.UnitOfWork;
import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.ScreeningCaseRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wiring of the framework-free core: the application's use cases. */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ScreeningCommandHandler screeningCommandHandler(ScreeningCaseRepository screenings, Blacklist blacklist,
                                                    UnitOfWork unitOfWork, Clock clock) {
        return new ScreeningCommandHandler(screenings, blacklist, unitOfWork, clock);
    }

    @Bean
    ScreeningQueries screeningQueries(ScreeningCaseRepository screenings, UnitOfWork unitOfWork) {
        return new ScreeningQueries(screenings, unitOfWork);
    }

    @Bean
    BlacklistManagement blacklistManagement(Blacklist blacklist, UnitOfWork unitOfWork) {
        return new BlacklistManagement(blacklist, unitOfWork);
    }
}
