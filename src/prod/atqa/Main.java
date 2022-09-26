package atqa;

import atqa.logging.ILogger;
import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;
import atqa.web.StartLine;
import atqa.web.Web;
import atqa.web.WebFramework;
import static atqa.web.WebFramework.StatusCode._200_OK;


import java.io.IOException;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;

/**
  * first stabs at a homemade Java program
  */
public class Main {
  public static void main(String[] args) throws IOException {
    ExecutorService es = ExtendedExecutor.makeExecutorService();
    ILogger logger = new Logger(es);
    Web web = new Web(logger);
    ZonedDateTime zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));
    WebFramework wf = new WebFramework(logger, zdt);
    wf.registerPath(StartLine.Verb.GET, "add_two_numbers", Main::addTwoNumbers);
    wf.registerPath(StartLine.Verb.GET, "", Main::getIndex);
    web.startServer(es, wf.makeHandler());
  }

  static WebFramework.Response addTwoNumbers(WebFramework.Request r) {
      int aValue = Integer.parseInt(r.sl().pathDetails().queryString().get("a"));
      int bValue = Integer.parseInt(r.sl().pathDetails().queryString().get("b"));
      int sum = aValue + bValue;
      String sumString = String.valueOf(sum);
      return new WebFramework.Response(_200_OK, sumString);
  }

  static WebFramework.Response getIndex(WebFramework.Request r) {
    return new WebFramework.Response(_200_OK, "");
  }

}
