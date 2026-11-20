package tn.oliveconnect.imports;

import org.springframework.stereotype.Service;

/**
 * Raises automatic field alerts after a dynamic import (rain, maturity). Hook only for now:
 * the rules are added with the alert module (docs/07 §4).
 */
@Service
public class AutoAlertService {

    public void evaluate() {
        // rules come later
    }
}
