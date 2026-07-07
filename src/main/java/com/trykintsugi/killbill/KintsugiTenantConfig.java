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

/** Per-tenant Kill Bill plugin configuration (YAML upload). */
public final class KintsugiTenantConfig {

    private static final String DEFAULT_KILLBILL_URL = "http://127.0.0.1:8080";

    private String kintsugiUrl;
    private String hmacSecret;
    private String killbillUrl = DEFAULT_KILLBILL_URL;
    /** Aviate JWT; when blank, Aviate billing-account lookup is skipped. */
    private String aviateIdToken;

    public String getKintsugiUrl() {
        return kintsugiUrl;
    }

    public void setKintsugiUrl(final String kintsugiUrl) {
        this.kintsugiUrl = kintsugiUrl;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(final String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String getKillbillUrl() {
        return killbillUrl != null && !killbillUrl.isBlank() ? killbillUrl.trim() : DEFAULT_KILLBILL_URL;
    }

    public void setKillbillUrl(final String killbillUrl) {
        this.killbillUrl = killbillUrl;
    }

    public String getAviateIdToken() {
        return aviateIdToken;
    }

    public void setAviateIdToken(final String aviateIdToken) {
        this.aviateIdToken = aviateIdToken;
    }

    public boolean hasAviateIntegration() {
        return aviateIdToken != null && !aviateIdToken.isBlank();
    }
}
