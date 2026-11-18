package com.gs5.restriction.infrastructure.config;

import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.application.RestrictionQueries;
import com.gs5.restriction.application.UnitOfWork;
import com.gs5.restriction.domain.ClientRestrictionRepository;
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
    RestrictionCommandHandler restrictionCommandHandler(ClientRestrictionRepository restrictions,
                                                        UnitOfWork unitOfWork, Clock clock) {
        return new RestrictionCommandHandler(restrictions, unitOfWork, clock);
    }

    @Bean
    RestrictionQueries restrictionQueries(ClientRestrictionRepository restrictions, UnitOfWork unitOfWork) {
        return new RestrictionQueries(restrictions, unitOfWork);
    }
}
