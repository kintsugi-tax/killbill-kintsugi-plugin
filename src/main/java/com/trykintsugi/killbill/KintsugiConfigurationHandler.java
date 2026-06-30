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
