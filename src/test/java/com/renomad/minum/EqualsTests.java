package com.renomad.minum;

import com.renomad.minum.security.Inmate;
import com.renomad.minum.web.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.buildTestingContext;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;

public class EqualsTests {

    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("EqualsVerifier tests");
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void equalsTest() {
        EqualsVerifier.forClass(Constants.class).verify();

        EqualsVerifier.forClass(Response.class).verify();

        EqualsVerifier.forClass(Body.class)
                .withPrefabValues(Headers.class,
                        new Headers(List.of("a"), context),
                        new Headers(List.of("b"), context)
                ).verify();

        EqualsVerifier.forClass(RequestLine.class)
                .withPrefabValues(Context.class,
                        new Context(),
                        new Context()
                        )
                .verify();

        EqualsVerifier.simple().forClass(Inmate.class).verify();

        EqualsVerifier.forClass(VaryHeader.class).verify();
    }


}
