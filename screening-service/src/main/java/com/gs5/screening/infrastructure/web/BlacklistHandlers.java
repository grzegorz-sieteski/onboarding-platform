package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.BlacklistManagement;
import com.gs5.screening.domain.BlacklistEntry;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static com.gs5.screening.infrastructure.web.HttpResults.read;

/** HTTP adapter of blacklist maintenance. Every endpoint reads top-down: request, use case, response. */
final class BlacklistHandlers {

    private final BlacklistManagement blacklist;

    BlacklistHandlers(BlacklistManagement blacklist) {
        this.blacklist = blacklist;
    }

    ServerResponse add(ServerRequest request) {
        return read(request, AddBlacklistEntryRequest.class)
                .flatMap(body -> blacklist.add(body.lastName(), body.reason()))
                .map(BlacklistEntryResponse::of)
                .fold(BlacklistHandlers::created, HttpResults::problem);
    }

    ServerResponse entries(ServerRequest request) {
        return HttpResults.ok(blacklist.entries().stream().map(BlacklistEntryResponse::of).toList());
    }

    ServerResponse remove(ServerRequest request) {
        return blacklist.remove(request.pathVariable("lastName"))
                .fold(_ -> ServerResponse.noContent().build(), HttpResults::problem);
    }

    private static ServerResponse created(BlacklistEntryResponse entry) {
        return ServerResponse.status(HttpStatus.CREATED).body(entry);
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
