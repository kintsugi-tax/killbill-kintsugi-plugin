package com.trykintsugi.killbill.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceRequestMapperTest {

    @Test
    void formatAmountUsesTwoDecimalPlaces() {
        assertEquals("100.00", InvoiceRequestMapper.formatAmount(new BigDecimal("100")));
        assertEquals("99.99", InvoiceRequestMapper.formatAmount(new BigDecimal("99.994")));
    }

    @Test
    void externalChargeUsesKintsugiProductLabels() {
        assertEquals("Physical", InvoiceRequestMapper.EXTERNAL_CHARGE_CATEGORY);
        assertEquals("General Physical", InvoiceRequestMapper.EXTERNAL_CHARGE_SUBCATEGORY);
    }
}
