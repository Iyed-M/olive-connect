package tn.oliveconnect.resource;

import java.time.Instant;

/** A period when a Worker or an Equipment cannot be used. Embedded in the owning document. */
public class Unavailability {

    private Instant from;
    private Instant to;
    private String reason;

    public Unavailability(Instant from, Instant to, String reason) {
        this.from = from;
        this.to = to;
        this.reason = reason;
    }

    public Instant getFrom() { return from; }
    public Instant getTo() { return to; }
    public String getReason() { return reason; }
}
