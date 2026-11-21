package tn.oliveconnect.common;

import java.time.Instant;

/** One manual correction made by staff (FR-6.5), kept in the history of the corrected document. */
public class Correction {

    private Instant at;
    private String byUserId;
    private String comment;

    public Correction() {
    }

    public Correction(Instant at, String byUserId, String comment) {
        this.at = at;
        this.byUserId = byUserId;
        this.comment = comment;
    }

    public Instant getAt() { return at; }
    public String getByUserId() { return byUserId; }
    public String getComment() { return comment; }
}
