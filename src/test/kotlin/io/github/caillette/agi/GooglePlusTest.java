package io.github.caillette.agi;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.openqa.selenium.remote.RemoteWebDriver2;
import org.openqa.selenium.remote.SessionQueryResult;
import org.junit.Test;

import java.net.URL;

import static io.github.caillette.agi.SilverIodide.HUB_URL;

/**
 *
 * <h1>Prerequisites</h1>
 * <p>
 * Install geckodriver for Firefox.
 * <pre>
 * brew install geckodriver
 * </pre>
 * <p>
 * Run a Selenium Server (same as a Hub and a single Node):
 * <pre>
 * java -jar ~/Downloads/selenium-server-standalone-3.5.3.jar -role standalone
 * </pre>
 * Default port for Hub is 4444.
 */
public class GooglePlusTest {
  @Test
  public void parseSessions() throws Exception {
    final URL sessionsUrl = new URL( HUB_URL.toExternalForm() + "sessions" );
    final String json = Resources.asCharSource(
        sessionsUrl, Charsets.UTF_8 ).read() ;
    System.out.println( "From " + sessionsUrl.toExternalForm() + ":\n" + json ) ;
    final SessionQueryResult decoded = RemoteWebDriver2.decodeSessions( json ) ;
    System.out.println( "Decoded: " + decoded ) ;
  }


// =======
// Fixture
// =======


}

