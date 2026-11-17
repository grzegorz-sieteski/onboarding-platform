package com.example.screening.infrastructure.web;

import com.example.screening.application.BlacklistManagement;
import com.example.screening.domain.BlacklistEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/blacklist")
class BlacklistController {

    private final BlacklistManagement blacklist;

    BlacklistController(BlacklistManagement blacklist) {
        this.blacklist = blacklist;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BlacklistEntryResponse add(@Valid @RequestBody AddBlacklistEntryRequest request) {
        return BlacklistEntryResponse.of(blacklist.add(request.lastName(), request.reason()));
    }

    @GetMapping
    List<BlacklistEntryResponse> entries() {
        return blacklist.entries().stream().map(BlacklistEntryResponse::of).toList();
    }

    @DeleteMapping("/{lastName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable String lastName) {
        blacklist.remove(lastName);
    }

    record AddBlacklistEntryRequest(@NotBlank String lastName, @NotBlank String reason) {
    }

    record BlacklistEntryResponse(String lastName, String reason) {

        static BlacklistEntryResponse of(BlacklistEntry entry) {
            return new BlacklistEntryResponse(entry.surname().value(), entry.reason());
        }
    }
}
