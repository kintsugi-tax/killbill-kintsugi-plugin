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
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Per-tenant plugin config uploaded via {@code uploadPluginConfig/killbill-kintsugi}.
 *
 * <p>Supports YAML POJO:
 * <pre>
 * !!com.trykintsugi.killbill.KintsugiTenantConfig
 * kintsugiUrl: https://api.trykintsugi.com
 * hmacSecret: &lt;shared-secret-from-kintsugi-import&gt;
 * </pre>
 *
 * <p>Or key=value properties:
 * <pre>
 * kintsugiUrl=https://api.trykintsugi.com
 * hmacSecret=&lt;shared-secret-from-kintsugi-import&gt;
 * </pre>
 */
public final class KintsugiConfigurationHandler
        extends PluginTenantConfigurableConfigurationHandler<KintsugiTenantConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KintsugiConfigurationHandler.class);

    public KintsugiConfigurationHandler(final String pluginName, final OSGIKillbillAPI osgiKillbillAPI) {
        super(pluginName, osgiKillbillAPI);
    }

    @Override
    protected KintsugiTenantConfig createConfigurable(final Properties properties) {
        final KintsugiTenantConfig config = new KintsugiTenantConfig();
        config.setKintsugiUrl(properties.getProperty("kintsugiUrl", "").trim());
        config.setHmacSecret(properties.getProperty("hmacSecret", "").trim());
        LOGGER.info("Loaded Kintsugi tenant config (url={})", config.getKintsugiUrl());
        return config;
    }
}
