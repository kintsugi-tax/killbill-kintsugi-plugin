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
    void externalChargeUsesDefaultProductLabels() {
        assertEquals("Physical", InvoiceRequestMapper.EXTERNAL_CHARGE_CATEGORY);
        assertEquals("General Physical", InvoiceRequestMapper.EXTERNAL_CHARGE_SUBCATEGORY);
    }
}
