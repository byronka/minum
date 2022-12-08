package atqa.primary;

import atqa.logging.TestLogger;

public class TestAnalysisTests {
    private TestLogger logger;

    public TestAnalysisTests(TestLogger logger) {

        this.logger = logger;
    }


    /*$$$$$$$                    /$$                                         /$$                     /$$
   |__  $$__/                   | $$                                        | $$                    |__/
      | $$  /$$$$$$   /$$$$$$$ /$$$$$$          /$$$$$$  /$$$$$$$   /$$$$$$ | $$ /$$   /$$  /$$$$$$$ /$$  /$$$$$$$
      | $$ /$$__  $$ /$$_____/|_  $$_/         |____  $$| $$__  $$ |____  $$| $$| $$  | $$ /$$_____/| $$ /$$_____/
      | $$| $$$$$$$$|  $$$$$$   | $$            /$$$$$$$| $$  \ $$  /$$$$$$$| $$| $$  | $$|  $$$$$$ | $$|  $$$$$$
      | $$| $$_____/ \____  $$  | $$ /$$       /$$__  $$| $$  | $$ /$$__  $$| $$| $$  | $$ \____  $$| $$ \____  $$
      | $$|  $$$$$$$ /$$$$$$$/  |  $$$$/      |  $$$$$$$| $$  | $$|  $$$$$$$| $$|  $$$$$$$ /$$$$$$$/| $$ /$$$$$$$/
      |__/ \_______/|_______/    \___/         \_______/|__/  |__/ \_______/|__/ \____  $$|_______/ |__/|_______/
                                                                              /$$  | $$
                                                                             |  $$$$$$/
                                                                              \_____*/
    public void tests() {


        // region Test Analysis section

        logger.test("playing around with how we could determine testedness of a function");{
            int score = 0;

            // for a pretend method, add(int a, int b)... let's play
            // e.g. add(2, 3)
            int a = 2;
            int b = 3;
            // make sure we hit less than zero, on zero, greater-than zero, both params
            if (a > 0) score++;if (a < 0) score++;if (a == 0) score++;
            if (b > 0) score++;if (b < 0) score++;if (b == 0) score++;
            // make sure we hit max and min
            if (a == Integer.MAX_VALUE) score++; if (a == Integer.MIN_VALUE) score++;
            if (b == Integer.MAX_VALUE) score++; if (b == Integer.MIN_VALUE) score++;
            // now we've dealt with each individually, let's think how they act as pairs
            if (a < 0 && b < 0) score++; if (a > 0 && b > 0) score++; if (a == 0 && b == 0) score++;
            if (a < 0 && b > 0) score++; if (a > 0 && b < 0) score++; if (a == 0 && b != 0) score++;
            if (a != 0 && b == 0) score++;
            if (a == Integer.MAX_VALUE && b == Integer.MAX_VALUE) score++;
            if (a == Integer.MIN_VALUE && b == Integer.MIN_VALUE) score++;

            int finalScore = score;
            logger.logDebug(() -> "Looks like your testedness score is " + finalScore);
        }

    /*
      If we can basically just try casting to things and making comparisons, then we might
      get a leg up for those situations where we deal with non-typed params
     */
        logger.test("how do we test non-typed code? a single param that turns out to be an int");{
            int score = 0;

            // pretend method: foo(Object a)
            Object a = 42;

            // make sure we hit less than zero, on zero, greater-than zero, both params
            try {
                int stuff = (int) a;
                if (stuff > 0) score++;
                if (stuff < 0) score++;
                if (stuff == 0) score++;
            } catch (ClassCastException ex) {
                logger.logDebug(() -> "this is not an int");
            }
            try {
                long stuff = (long) a;
                if (stuff > 0) score++;
                if (stuff < 0) score++;
                if (stuff == 0) score++;
            } catch (ClassCastException ex) {
                logger.logDebug(() -> "this is not a long");
            }

            int finalScore = score;
            logger.logDebug(() -> "Looks like your testedness score is " + finalScore);

        }

        logger.test("some more exotic type tests");{
            int score = 0;

            // pretend method: foo(String[] a, Foobar b)
            String[] a = {"a", "b", "c"};
            class Foobar {
                public Foobar() {}
                public int bar() {return 42;}
            }
            Foobar f = new Foobar();

            if (a.length == 0) score++;
            if (a.length > 0) score++;
            if (a == null) score++;
            if (a.length == 1) score++;

            if (f == null) score++;

            int finalScore = score;
            logger.logDebug(() -> "Looks like your testedness score is " + finalScore);
        }

    }

}
