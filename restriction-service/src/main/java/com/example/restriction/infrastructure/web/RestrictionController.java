package com.example.restriction.infrastructure.web;

import com.example.restriction.application.BanClientCommand;
import com.example.restriction.application.RestrictionCommandHandler;
import com.example.restriction.application.RestrictionQueries;
import com.example.restriction.application.RestrictionView;
import com.example.restriction.domain.CustomerId;
import com.example.restriction.domain.RestrictionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restrictions")
class RestrictionController {

    private final RestrictionCommandHandler commands;
    private final RestrictionQueries queries;

    RestrictionController(RestrictionCommandHandler commands, RestrictionQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/{customerId}")
    RestrictionView get(@PathVariable String customerId) {
        return queries.get(CustomerId.of(customerId));
    }

    /** Manual ban by a compliance officer - goes through the same aggregate and the same events. */
    @PostMapping("/{customerId}/ban")
    RestrictionView ban(@PathVariable String customerId, @Valid @RequestBody BanRequest request) {
        CustomerId id = CustomerId.of(customerId);
        commands.handle(new BanClientCommand(id, request.reason(), RestrictionSource.MANUAL));
        return queries.get(id);
    }

    record BanRequest(@NotBlank String reason) {
    }
}
