package com.gs5.restriction.infrastructure.web;

import com.gs5.restriction.application.BanClientCommand;
import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.application.RestrictionQueries;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.gs5.restriction.infrastructure.web.RestrictionProblems.respond;

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
    ResponseEntity<?> get(@PathVariable String customerId) {
        return respond(CustomerId.of(customerId).flatMap(queries::get), view -> ResponseEntity.ok(view));
    }

    /** Manual ban by a compliance officer - goes through the same aggregate and the same events. */
    @PostMapping("/{customerId}/ban")
    ResponseEntity<?> ban(@PathVariable String customerId, @Valid @RequestBody BanRequest request) {
        return respond(CustomerId.of(customerId)
                        .flatMap(id -> commands.handle(new BanClientCommand(id, request.reason(), RestrictionSource.MANUAL)))
                        .flatMap(queries::get),
                view -> ResponseEntity.ok(view));
    }

    record BanRequest(@NotBlank String reason) {
    }
}
