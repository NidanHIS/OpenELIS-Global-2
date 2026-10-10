package org.openelisglobal.security;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import org.junit.Test;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Nidan passes OpenELIS secrets as environment variables, not -D flags (Tomcat
 * logs every JVM argument at start, backlog KC-45-01). This pins the env-var
 * names nidan-docker uses to the @Value keys the code reads.
 */
public class NidanSecretEnvBindingTest {

    @Test
    public void environmentVariableNamesResolveTheSecretProperties() {
        Map<String, Object> env = Map.of("ORG_OPENELISGLOBAL_MIDDLEWARE_TEST_SYNC_SECRET", "a",
                "ORG_OPENELISGLOBAL_MIDDLEWARE_RESULT_SYNC_SECRET", "b", "ORG_OPENELISGLOBAL_NIDAN_PAYWALL_ODOO_SECRET",
                "c", "ORG_ITECH_LOGIN_OAUTH_CLIENTSECRET", "d");
        SystemEnvironmentPropertySource source = new SystemEnvironmentPropertySource("env", env);
        assertEquals("a", source.getProperty("org.openelisglobal.middleware.test.sync.secret"));
        assertEquals("b", source.getProperty("org.openelisglobal.middleware.result.sync.secret"));
        assertEquals("c", source.getProperty("org.openelisglobal.nidan.paywall.odoo.secret"));
        assertEquals("d", source.getProperty("org.itech.login.oauth.clientSecret"));
    }
}
