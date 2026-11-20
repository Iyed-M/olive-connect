package tn.oliveconnect.common;

import java.time.Instant;

/** A single entry in the history of a stateful domain object. */
public class StatusChange {

    private String from;
    private String to;
    private Instant at;
    private String byUserId;
    private String comment;

    public StatusChange() {
    }

    public StatusChange(String from, String to, Instant at, String byUserId, String comment) {
        this.from = from;
        this.to = to;
        this.at = at;
        this.byUserId = byUserId;
        this.comment = comment;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public Instant getAt() {
        return at;
    }

    public String getByUserId() {
        return byUserId;
    }

    public String getComment() {
        return comment;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public void setByUserId(String byUserId) {
        this.byUserId = byUserId;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
