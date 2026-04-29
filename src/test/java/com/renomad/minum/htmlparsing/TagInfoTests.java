package com.renomad.minum.htmlparsing;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class TagInfoTests {


    static private Context context;
    static private TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("TagInfoTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    @Test
    public void happyPath() {
        TagInfo tagInfo = new TagInfo(TagName.P, Map.of("class", "foo"));
        assertEquals(tagInfo.toString(), "TagInfo{tagName=P, attributes={class=foo}}");
    }
}
