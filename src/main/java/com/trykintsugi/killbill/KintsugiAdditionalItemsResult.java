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

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.AdditionalItemsResult;
import org.killbill.billing.payment.api.PluginProperty;

import java.util.Collections;
import java.util.List;

/** {@link AdditionalItemsResult} for Kintsugi tax lines. */
public final class KintsugiAdditionalItemsResult implements AdditionalItemsResult {

    private final List<InvoiceItem> additionalItems;
    private final Iterable<PluginProperty> adjustedPluginProperties;

    public KintsugiAdditionalItemsResult(final List<InvoiceItem> additionalItems) {
        this(additionalItems, null);
    }

    private KintsugiAdditionalItemsResult(
            final List<InvoiceItem> additionalItems,
            final Iterable<PluginProperty> adjustedPluginProperties) {
        this.additionalItems = additionalItems != null
                ? List.copyOf(additionalItems)
                : Collections.emptyList();
        this.adjustedPluginProperties = adjustedPluginProperties;
    }

    public static KintsugiAdditionalItemsResult empty() {
        return new KintsugiAdditionalItemsResult(Collections.emptyList());
    }

    @Override
    public List<InvoiceItem> getAdditionalItems() {
        return additionalItems;
    }

    @Override
    public Iterable<PluginProperty> getAdjustedPluginProperties() {
        return adjustedPluginProperties;
    }
}
