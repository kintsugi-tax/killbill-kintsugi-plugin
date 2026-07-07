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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parsed Aviate billing account used for tax metadata (not Aviate's internal rate engine). */
public final class AviateBillingAccount {

    private final String companyName;
    private final String contactName;
    private final String email;
    private final TaxAddress companyAddress;
    private final List<TaxRegistration> taxRegistrations;

    private AviateBillingAccount(
            final String companyName,
            final String contactName,
            final String email,
            final TaxAddress companyAddress,
            final List<TaxRegistration> taxRegistrations) {
        this.companyName = companyName;
        this.contactName = contactName;
        this.email = email;
        this.companyAddress = companyAddress;
        this.taxRegistrations = List.copyOf(taxRegistrations);
    }

    public static AviateBillingAccount fromJson(final JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        final List<TaxRegistration> registrations = new ArrayList<>();
        final JsonNode registrationNodes = root.path("taxRegistrations");
        if (registrationNodes.isArray()) {
            for (final JsonNode node : registrationNodes) {
                registrations.add(TaxRegistration.fromJson(node));
            }
        }
        return new AviateBillingAccount(
                textOrNull(root, "companyName"),
                textOrNull(root, "contactName"),
                textOrNull(root, "email"),
                TaxAddressParser.fromAviateAddress(root.path("address")),
                registrations);
    }

    public boolean isTaxExempt() {
        for (final TaxRegistration registration : taxRegistrations) {
            if (registration.exempt()) {
                return true;
            }
        }
        return false;
    }

    public TaxAddress resolveShipToAddress(final String preferredCountry) {
        final Optional<TaxRegistration> registration = registrationForCountry(preferredCountry);
        if (registration.isPresent()) {
            final TaxAddress registrationAddress = registration.get().address();
            if (registrationAddress != null && registrationAddress.hasData()) {
                return registrationAddress;
            }
        }
        if (companyAddress != null && companyAddress.hasData()) {
            return companyAddress;
        }
        return null;
    }

    public String resolveTaxRegistrationNumber(final String preferredCountry) {
        return registrationForCountry(preferredCountry)
                .map(TaxRegistration::trn)
                .orElse(null);
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    private Optional<TaxRegistration> registrationForCountry(final String preferredCountry) {
        if (taxRegistrations.isEmpty()) {
            return Optional.empty();
        }
        if (preferredCountry != null && !preferredCountry.isBlank()) {
            for (final TaxRegistration registration : taxRegistrations) {
                final TaxAddress address = registration.address();
                if (address != null
                        && preferredCountry.equalsIgnoreCase(address.getCountry())) {
                    return Optional.of(registration);
                }
            }
        }
        return Optional.of(taxRegistrations.get(0));
    }

    private static String textOrNull(final JsonNode node, final String field) {
        final JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        final String text = value.asText(null);
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    static final class TaxRegistration {
        private final boolean exempt;
        private final String trn;
        private final TaxAddress address;

        private TaxRegistration(final boolean exempt, final String trn, final TaxAddress address) {
            this.exempt = exempt;
            this.trn = trn;
            this.address = address;
        }

        static TaxRegistration fromJson(final JsonNode node) {
            return new TaxRegistration(
                    node.path("exempt").asBoolean(false),
                    textOrNull(node, "trn"),
                    TaxAddressParser.fromAviateAddress(node.path("address")));
        }

        boolean exempt() {
            return exempt;
        }

        String trn() {
            return trn;
        }

        TaxAddress address() {
            return address;
        }
    }
}
