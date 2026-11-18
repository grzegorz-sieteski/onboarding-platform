package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.BlacklistManagement;
import com.gs5.screening.application.ScreeningQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/** The whole HTTP API of the service in one place: every route and the handler that serves it. */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    @Bean
    RouterFunction<ServerResponse> routes(BlacklistManagement blacklistManagement, ScreeningQueries queries) {
        var blacklist = new BlacklistHandlers(blacklistManagement);
        var screenings = new ScreeningHandlers(queries);
        return RouterFunctions.route()
                .POST("/api/blacklist", blacklist::add)
                .GET("/api/blacklist", blacklist::entries)
                .DELETE("/api/blacklist/{lastName}", blacklist::remove)
                .GET("/api/screenings/{customerId}", screenings::get)
                .build();
    }
}
