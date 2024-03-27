package com.renomad.minum.web;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class PreparedResponseTests {

    @Test
    public void testEquals() {
        EqualsVerifier.forClass(PreparedResponse.class).verify();
    }
}
