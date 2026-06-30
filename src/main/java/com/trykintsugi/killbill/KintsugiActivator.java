package com.trykintsugi.killbill;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.osgi.framework.BundleContext;

import java.util.Hashtable;

import org.killbill.billing.osgi.api.OSGIPluginProperties;

/** OSGi bundle activator — registers the Kintsugi invoice plugin. */
public final class KintsugiActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-kintsugi";

    private KintsugiConfigurationHandler configurationHandler;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        configurationHandler = new KintsugiConfigurationHandler(PLUGIN_NAME, killbillAPI);
        configurationHandler.setDefaultConfigurable(
                configurationHandler.createConfigurable(new java.util.Properties()));

        final InvoicePluginApi invoicePluginApi = new KintsugiInvoicePluginApi(
                killbillAPI, configProperties, null, configurationHandler);
        registerInvoicePluginApi(context, invoicePluginApi);

        final PluginConfigurationEventHandler configHandler =
                new PluginConfigurationEventHandler(configurationHandler);
        dispatcher.registerEventHandlers(configHandler);
    }

    private void registerInvoicePluginApi(final BundleContext context, final InvoicePluginApi api) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, InvoicePluginApi.class, api, props);
    }
}
