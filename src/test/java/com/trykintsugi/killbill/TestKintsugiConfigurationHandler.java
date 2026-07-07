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

import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.tenant.api.Tenant;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Properties;
import java.util.UUID;

public class TestKintsugiConfigurationHandler {

    @Test(groups = "fast")
    public void testCreateConfigurableTrimsProperties() {
        final KintsugiConfigurationHandler handler = new KintsugiConfigurationHandler(
                KintsugiActivator.PLUGIN_NAME, Mockito.mock(OSGIKillbillAPI.class));

        final Properties properties = new Properties();
        properties.setProperty("kintsugiUrl", " https://api.example.com ");
        properties.setProperty("hmacSecret", " shared-secret ");

        final KintsugiTenantConfig config = handler.createConfigurable(properties);

        Assert.assertEquals(config.getKintsugiUrl(), "https://api.example.com");
        Assert.assertEquals(config.getHmacSecret(), "shared-secret");
    }

    @Test(groups = "fast")
    public void testGetConfigurableReturnsTenantConfig() {
        final KintsugiConfigurationHandler handler = new KintsugiConfigurationHandler(
                KintsugiActivator.PLUGIN_NAME, Mockito.mock(OSGIKillbillAPI.class));

        final Properties properties = new Properties();
        properties.setProperty("kintsugiUrl", "https://api.example.com");
        properties.setProperty("hmacSecret", "shared-secret");
        handler.setDefaultConfigurable(handler.createConfigurable(properties));

        final UUID tenantId = UUID.randomUUID();
        final KintsugiTenantConfig config = handler.getConfigurable(tenantId);

        Assert.assertEquals(config.getKintsugiUrl(), "https://api.example.com");
        Assert.assertEquals(config.getHmacSecret(), "shared-secret");
    }

    @Test(groups = "fast")
    public void testAviateConfigIsOptional() {
        final KintsugiConfigurationHandler handler = new KintsugiConfigurationHandler(
                KintsugiActivator.PLUGIN_NAME, Mockito.mock(OSGIKillbillAPI.class));

        final Properties properties = new Properties();
        properties.setProperty("kintsugiUrl", "https://api.example.com");
        properties.setProperty("hmacSecret", "shared-secret");
        properties.setProperty("killbillUrl", "http://kb.example.com:8080");
        properties.setProperty("aviateIdToken", "jwt-token");

        final KintsugiTenantConfig config = handler.createConfigurable(properties);

        Assert.assertEquals(config.getKillbillUrl(), "http://kb.example.com:8080");
        Assert.assertEquals(config.getAviateIdToken(), "jwt-token");
        Assert.assertTrue(config.hasAviateIntegration());
    }

    @Test(groups = "fast")
    public void testDefaultKillbillUrlWhenBlank() {
        final KintsugiConfigurationHandler handler = new KintsugiConfigurationHandler(
                KintsugiActivator.PLUGIN_NAME, Mockito.mock(OSGIKillbillAPI.class));

        final Properties properties = new Properties();
        properties.setProperty("kintsugiUrl", "https://api.example.com");
        properties.setProperty("hmacSecret", "shared-secret");

        final KintsugiTenantConfig config = handler.createConfigurable(properties);

        Assert.assertEquals(config.getKillbillUrl(), "http://127.0.0.1:8080");
        Assert.assertFalse(config.hasAviateIntegration());
    }
}
