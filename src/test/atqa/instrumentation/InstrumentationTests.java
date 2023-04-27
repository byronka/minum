package atqa.instrumentation;

import atqa.logging.TestLogger;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class InstrumentationTests {

    private final TestLogger logger;

    public InstrumentationTests(TestLogger logger) {
        this.logger = logger;
    }

    private boolean markPredicateExpressionCovered(String fileName, boolean booleanExpression, int lineNumber, int expressionOrder) {
        logger.logDebug(() -> "Called by " +  Arrays.stream(Thread.currentThread().getStackTrace()).skip(6).map(StackTraceElement::toString).collect(Collectors.joining(";")));
        logger.logDebug(() -> "predicate: filename is " + fileName + " expression is " + booleanExpression + " lineNumber is " + lineNumber + " expressionOrder is " + expressionOrder);
        return booleanExpression;
    }

    private void markCovered(String fileName, int lineNumber) {
        logger.logDebug(() -> "Called by " + Arrays.stream(Thread.currentThread().getStackTrace()).skip(6).map(StackTraceElement::toString).collect(Collectors.joining(";")));
        logger.logDebug(() ->"filename is " + fileName + " lineNumber is " + lineNumber);
    }

    public void tests(ExecutorService es) {
        logger.test("playing with explicit coverage probes");{
            var a = true;          markCovered("InstrumentationTests.java", 31);
            var b = false;         markCovered("InstrumentationTests.java", 32);
            var c = true;          markCovered("InstrumentationTests.java", 33);
            if (markPredicateExpressionCovered("InstrumentationTests.java",markPredicateExpressionCovered("InstrumentationTests.java",a && b, 34, 1) || c, 34, 2)) {
                a = false;         markCovered("InstrumentationTests.java", 35);
            } else {
                b = true;          markCovered("InstrumentationTests.java", 37);
            }
        }

    }
}
