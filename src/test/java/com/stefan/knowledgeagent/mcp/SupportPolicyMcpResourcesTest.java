package com.stefan.knowledgeagent.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportPolicyMcpResourcesTest {

    private final SupportPolicyMcpResources resources =
            new SupportPolicyMcpResources(new DefaultResourceLoader());

    @Test
    void readsAllowListedPolicyDocumentsFromClasspath() {
        assertThat(resources.readPolicy("refund-policy"))
                .contains("# Refund Policy")
                .contains("30 calendar days")
                .contains("demonstration policy content");

        assertThat(resources.readPolicy("shipping-policy"))
                .contains("# Shipping Policy");

        assertThat(resources.readPolicy("warranty-policy"))
                .contains("# Warranty Policy");
    }

    @Test
    void resourcePathCannotBeChosenByCaller() {
        assertThatThrownBy(() -> resources.readPolicy("../application.yml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown policy ID");

        assertThatThrownBy(() -> resources.readPolicy("application"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge://policies");
    }

    @Test
    void catalogIdentifiesTheSupportedResourcesAndTheirTrustBoundary() {
        assertThat(resources.policyCatalog())
                .contains("shipping-policy")
                .contains("refund-policy")
                .contains("warranty-policy")
                .contains("not approved operational");
    }
}
