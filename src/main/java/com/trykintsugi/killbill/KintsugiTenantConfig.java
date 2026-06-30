package com.trykintsugi.killbill;

/** Per-tenant Kill Bill plugin configuration (YAML upload). */
public final class KintsugiTenantConfig {

    private String kintsugiUrl;
    private String hmacSecret;

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
}
