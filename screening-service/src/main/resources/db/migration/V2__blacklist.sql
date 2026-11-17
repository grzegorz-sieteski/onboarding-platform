-- Reference data (not event sourced). normalized_surname is computed by the domain (Surname.normalized()).
CREATE TABLE blacklist_entry (
    normalized_surname VARCHAR(200)             NOT NULL PRIMARY KEY,
    surname            VARCHAR(200)             NOT NULL,
    reason             VARCHAR(500)             NOT NULL,
    added_at           TIMESTAMP WITH TIME ZONE NOT NULL
);
