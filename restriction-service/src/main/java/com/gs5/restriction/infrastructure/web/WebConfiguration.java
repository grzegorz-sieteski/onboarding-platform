package com.gs5.restriction.infrastructure.web;

import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.application.RestrictionQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/** The whole HTTP API of the service in one place: every route and the handler that serves it. */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    @Bean
    RouterFunction<ServerResponse> routes(RestrictionCommandHandler commands, RestrictionQueries queries) {
        var restrictions = new RestrictionHandlers(commands, queries);
        return RouterFunctions.route()
                .GET("/api/restrictions/{customerId}", restrictions::get)
                .POST("/api/restrictions/{customerId}/ban", restrictions::ban)
                .build();
    }
}
