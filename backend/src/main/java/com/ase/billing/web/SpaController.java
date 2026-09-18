package com.ase.billing.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards deep links to the single-page app.
 *
 * React Router owns paths like /invoices/12. Without this, a refresh on that URL
 * reaches Spring, matches no controller, and returns the Whitelabel 404 page
 * instead of the app. /api and /assets are excluded so real endpoints and the
 * built bundle still resolve normally.
 *
 * WC & QC addition: "/wcqc" and "/wcqc/{path:[^.]*}" cover the module's own
 * client-side routes (/wcqc/generate, /wcqc/generate-wc, /wcqc/generate-qc,
 * /wcqc/history — and generate-wc/generate-qc's own "?id=" query string,
 * which this pattern is untouched by since it only matches the path).
 * Without this, refreshing or deep-linking into any WC & QC page 404s here
 * instead of reaching React Router — which is exactly what was happening:
 * the server saw "GET /wcqc/generate-wc", found no controller or static
 * file for it, and logged it as an unhandled exception even though nothing
 * was actually broken in the module itself.
 */
@Controller
public class SpaController {

    @GetMapping({
        "/",
        "/invoices", "/invoices/{path:[^.]*}",
        "/shipments/{path:[^.]*}",
        "/soa",
        "/quotation",
        "/wcqc", "/wcqc/{path:[^.]*}"
    })
    public String forwardToApp() {
        return "forward:/index.html";
    }
}
