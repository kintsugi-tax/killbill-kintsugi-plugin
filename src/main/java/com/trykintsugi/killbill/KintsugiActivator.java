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
