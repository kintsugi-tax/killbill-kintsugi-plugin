/*
 * Copyright 2026 Kintsugi Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trykintsugi.killbill;

import org.junit.jupiter.api.Test;
import org.killbill.billing.tenant.api.Tenant;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class KintsugiHealthcheckTest {

    @Test
    void reportsHealthyWhenTenantIsAbsent() {
        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(tenantId -> null);

        assertTrue(healthcheck.getHealthStatus(null, null).isHealthy());
    }

    @Test
    void reportsUnhealthyWhenTenantConfigMissing() {
        final UUID tenantId = UUID.randomUUID();
        final Tenant tenant = Mockito.mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);

        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(id -> null);

        assertFalse(healthcheck.getHealthStatus(tenant, null).isHealthy());
    }

    @Test
    void reportsHealthyWhenTenantConfigPresent() {
        final UUID tenantId = UUID.randomUUID();
        final Tenant tenant = Mockito.mock(Tenant.class);
        final KintsugiTenantConfig config = new KintsugiTenantConfig();
        config.setKintsugiUrl("https://api.example.com");
        config.setHmacSecret("secret");

        when(tenant.getId()).thenReturn(tenantId);

        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(id -> config);

        assertTrue(healthcheck.getHealthStatus(tenant, null).isHealthy());
    }
}
