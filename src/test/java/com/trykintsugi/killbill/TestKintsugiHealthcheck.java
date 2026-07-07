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

import org.killbill.billing.tenant.api.Tenant;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;

public class TestKintsugiHealthcheck {

    @Test(groups = "fast")
    public void testReportsHealthyWhenTenantIsAbsent() {
        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(tenantId -> null);

        Assert.assertTrue(healthcheck.getHealthStatus(null, null).isHealthy());
    }

    @Test(groups = "fast")
    public void testReportsUnhealthyWhenTenantConfigMissing() {
        final UUID tenantId = UUID.randomUUID();
        final Tenant tenant = Mockito.mock(Tenant.class);
        Mockito.when(tenant.getId()).thenReturn(tenantId);

        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(id -> null);

        Assert.assertFalse(healthcheck.getHealthStatus(tenant, null).isHealthy());
    }

    @Test(groups = "fast")
    public void testReportsUnhealthyWhenUrlBlank() {
        final UUID tenantId = UUID.randomUUID();
        final Tenant tenant = Mockito.mock(Tenant.class);
        Mockito.when(tenant.getId()).thenReturn(tenantId);

        final KintsugiTenantConfig config = new KintsugiTenantConfig();
        config.setKintsugiUrl("");
        config.setHmacSecret("secret");

        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(id -> config);

        Assert.assertFalse(healthcheck.getHealthStatus(tenant, null).isHealthy());
    }

    @Test(groups = "fast")
    public void testReportsUnhealthyWhenUrlOrSecretBlank() {
        final UUID tenantId = UUID.randomUUID();
        final Tenant tenant = Mockito.mock(Tenant.class);
        Mockito.when(tenant.getId()).thenReturn(tenantId);

        final KintsugiTenantConfig config = new KintsugiTenantConfig();
        config.setKintsugiUrl("https://api.example.com");
        config.setHmacSecret("");

        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(id -> config);

        Assert.assertFalse(healthcheck.getHealthStatus(tenant, null).isHealthy());
    }

    @Test(groups = "fast")
    public void testReportsHealthyWhenTenantConfigPresent() {
        final UUID tenantId = UUID.randomUUID();
        final Tenant tenant = Mockito.mock(Tenant.class);
        final KintsugiTenantConfig config = new KintsugiTenantConfig();
        config.setKintsugiUrl("https://api.example.com");
        config.setHmacSecret("secret");

        Mockito.when(tenant.getId()).thenReturn(tenantId);

        final KintsugiHealthcheck healthcheck = new KintsugiHealthcheck(id -> config);

        Assert.assertTrue(healthcheck.getHealthStatus(tenant, null).isHealthy());
    }
}
