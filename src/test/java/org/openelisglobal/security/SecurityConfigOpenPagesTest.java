package org.openelisglobal.security;

import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import org.junit.Test;

/**
 * Pins that plugin servlets are not in the unauthenticated page lists:
 * PluginsController runs any plugin servlet bean named in the URL, so listing
 * /pluginServlet/** there exposes every installed plugin without a login.
 */
public class SecurityConfigOpenPagesTest {

    @Test
    public void pluginServletsRequireAuthentication() {
        for (String[] pages : new String[][] { SecurityConfig.OPEN_PAGES, SecurityConfig.LOGIN_PAGES,
                SecurityConfig.RESOURCE_PAGES }) {
            assertFalse(Arrays.toString(pages), Arrays.stream(pages).anyMatch(p -> p.startsWith("/pluginServlet")));
        }
    }
}
