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

    /** A correction made right now by the given user. */
    public static Correction now(String userId, String comment) {
        return new Correction(Instant.now(), userId, comment);
    }

    public Instant getAt() { return at; }
    public String getByUserId() { return byUserId; }
    public String getComment() { return comment; }
}
