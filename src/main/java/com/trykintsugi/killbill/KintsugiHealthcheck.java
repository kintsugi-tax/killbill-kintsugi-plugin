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

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.tenant.api.Tenant;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/** Reports whether the plugin bundle is loaded and tenant config is present. */
public final class KintsugiHealthcheck implements Healthcheck {

    private final KintsugiTenantConfigSource configSource;

    public KintsugiHealthcheck(final KintsugiConfigurationHandler configurationHandler) {
        this(configurationHandler::getConfigurable);
    }

    KintsugiHealthcheck(final KintsugiTenantConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    public HealthStatus getHealthStatus(@Nullable final Tenant tenant, @Nullable final Map properties) {
        if (tenant == null) {
            return HealthStatus.healthy("Kintsugi plugin loaded");
        }

        final KintsugiTenantConfig config = configSource.getConfigurable(tenant.getId());
        if (config == null || isBlank(config.getKintsugiUrl()) || isBlank(config.getHmacSecret())) {
            return HealthStatus.unHealthy("Kintsugi plugin is not configured for tenant");
        }

        return HealthStatus.healthy("Kintsugi plugin configured");
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
