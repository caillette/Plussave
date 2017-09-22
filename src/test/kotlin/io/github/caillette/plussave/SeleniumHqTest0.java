package io.github.caillette.plussave;

import io.ddavison.conductor.Browser;
import io.ddavison.conductor.Conductor;
import io.ddavison.conductor.Config;
import io.ddavison.conductor.Locomotive;
import org.junit.Test;

@Config(
    browser = Browser.CHROME,
    url     = "http://seleniumhq.org"
)
public class SeleniumHqTest0 {
  @Test
  public void testDownloadLinkExists() {
    final Conductor conductor = new Locomotive() ;
    conductor.validatePresent( Homepage.LOC_LNK_DOWNLOADSELENIUM ) ;

  }

  @Test
  public void testTabsExist() {

  }

}
