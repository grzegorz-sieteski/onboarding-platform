package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.BlacklistManagement;
import com.gs5.screening.domain.BlacklistEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gs5.screening.infrastructure.web.ScreeningProblems.respond;

@RestController
@RequestMapping("/api/blacklist")
class BlacklistController {

    private final BlacklistManagement blacklist;

    BlacklistController(BlacklistManagement blacklist) {
        this.blacklist = blacklist;
    }

    @PostMapping
    ResponseEntity<?> add(@Valid @RequestBody AddBlacklistEntryRequest request) {
        return respond(blacklist.add(request.lastName(), request.reason()),
                entry -> ResponseEntity.status(HttpStatus.CREATED).body(BlacklistEntryResponse.of(entry)));
    }

    @GetMapping
    List<BlacklistEntryResponse> entries() {
        return blacklist.entries().stream().map(BlacklistEntryResponse::of).toList();
    }

    @DeleteMapping("/{lastName}")
    ResponseEntity<?> remove(@PathVariable String lastName) {
        return respond(blacklist.remove(lastName), _ -> ResponseEntity.noContent().build());
    }

    record AddBlacklistEntryRequest(@NotBlank String lastName, @NotBlank String reason) {
    }

    record BlacklistEntryResponse(String lastName, String reason) {
        static BlacklistEntryResponse of(BlacklistEntry entry) {
            return new BlacklistEntryResponse(entry.surname().value(), entry.reason());
        }
    }
}
