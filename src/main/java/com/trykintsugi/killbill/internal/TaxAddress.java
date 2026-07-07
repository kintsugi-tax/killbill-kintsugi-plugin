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

package com.trykintsugi.killbill.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.killbill.billing.account.api.Account;

/** Normalized postal address for ship-to / bill-to on tax requests. */
public final class TaxAddress {

    private final String line1;
    private final String line2;
    private final String city;
    private final String state;
    private final String country;
    private final String postalCode;

    public TaxAddress(
            final String line1,
            final String line2,
            final String city,
            final String state,
            final String country,
            final String postalCode) {
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.postalCode = postalCode;
    }

    public static TaxAddress fromAccount(final Account account) {
        if (account == null) {
            return null;
        }
        return new TaxAddress(
                account.getAddress1(),
                account.getAddress2(),
                account.getCity(),
                account.getStateOrProvince(),
                account.getCountry(),
                account.getPostalCode());
    }

    public boolean hasData() {
        return !isBlank(line1)
                || !isBlank(line2)
                || !isBlank(city)
                || !isBlank(state)
                || !isBlank(country)
                || !isBlank(postalCode);
    }

    public ObjectNode toJson(final ObjectMapper mapper) {
        final ObjectNode address = mapper.createObjectNode();
        if (!isBlank(country)) {
            address.put("country", country);
        }
        if (!isBlank(postalCode)) {
            address.put("postal_code", postalCode);
        }
        if (!isBlank(state)) {
            address.put("state", state);
        }
        if (!isBlank(city)) {
            address.put("city", city);
        }
        if (!isBlank(line1)) {
            address.put("line1", line1);
        }
        if (!isBlank(line2)) {
            address.put("line2", line2);
        }
        return address;
    }

    public String getCountry() {
        return country;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
