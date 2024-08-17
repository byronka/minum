package com.renomad.minum;

import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.ParseNodeType;
import com.renomad.minum.htmlparsing.TagInfo;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.security.Inmate;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.web.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

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

        EqualsVerifier.forClass(Response.class)
                .withIgnoredFields("outputGenerator")
                .verify();

        EqualsVerifier.forClass(Body.class)
                .withPrefabValues(Headers.class,
                        new Headers(List.of("a")),
                        new Headers(List.of("b"))
                ).verify();

        EqualsVerifier.forClass(RequestLine.class)
                .withPrefabValues(Context.class,
                        new Context(null, new Constants()),
                        new Context(null, new Constants())
                        )
                .verify();

        EqualsVerifier.simple().forClass(Inmate.class).verify();

        EqualsVerifier.forClass(HtmlParseNode.class)
                .withPrefabValues(HtmlParseNode.class,
                        new HtmlParseNode(
                                ParseNodeType.ELEMENT,
                                new TagInfo(TagName.BR, Map.of("foo", "bar")),
                                List.of(),
                                ""),
                        new HtmlParseNode(
                                ParseNodeType.ELEMENT,
                                new TagInfo(TagName.A, Map.of("class", "biz")),
                                List.of(),
                                ""))
                .verify();

        EqualsVerifier.forClass(TagInfo.class).verify();

        EqualsVerifier.forClass(PathDetails.class).verify();

        EqualsVerifier.forClass(ContentDisposition.class).verify();

        EqualsVerifier.forClass(Partition.class).verify();

        EqualsVerifier.forClass(UrlEncodedKeyValue.class).verify();

        EqualsVerifier.forClass(Headers.class).verify();


    }


}
