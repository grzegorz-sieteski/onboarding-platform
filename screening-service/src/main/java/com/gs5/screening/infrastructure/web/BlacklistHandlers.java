package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.BlacklistManagement;
import com.gs5.screening.domain.BlacklistEntry;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static com.gs5.screening.infrastructure.web.HttpResults.read;
import static com.gs5.screening.infrastructure.web.HttpResults.respond;

/** HTTP adapter of blacklist maintenance. */
final class BlacklistHandlers {

    private final BlacklistManagement blacklist;

    BlacklistHandlers(BlacklistManagement blacklist) {
        this.blacklist = blacklist;
    }

    ServerResponse add(ServerRequest request) {
        return respond(read(request, AddBlacklistEntryRequest.class)
                        .flatMap(body -> blacklist.add(body.lastName(), body.reason())),
                entry -> ServerResponse.status(HttpStatus.CREATED).body(BlacklistEntryResponse.of(entry)));
    }

    ServerResponse entries(ServerRequest request) {
        return ServerResponse.ok().body(blacklist.entries().stream().map(BlacklistEntryResponse::of).toList());
    }

    ServerResponse remove(ServerRequest request) {
        return respond(blacklist.remove(request.pathVariable("lastName")), _ -> ServerResponse.noContent().build());
    }

    /** Request body; deliberately not validated here - the domain validates it. */
    record AddBlacklistEntryRequest(String lastName, String reason) {
    }

    record BlacklistEntryResponse(String lastName, String reason) {

        static BlacklistEntryResponse of(BlacklistEntry entry) {
            return new BlacklistEntryResponse(entry.surname().value(), entry.reason());
        }
    }
}
