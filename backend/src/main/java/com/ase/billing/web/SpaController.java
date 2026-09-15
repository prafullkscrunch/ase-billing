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
 */
@Controller
public class SpaController {

    @GetMapping({
        "/",
        "/invoices", "/invoices/{path:[^.]*}",
        "/shipments/{path:[^.]*}",
        "/soa"
    })
    public String forwardToApp() {
        return "forward:/index.html";
    }
}
