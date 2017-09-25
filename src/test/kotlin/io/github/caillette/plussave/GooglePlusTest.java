package io.github.caillette.plussave;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import org.openqa.selenium.remote.RemoteWebDriver2;
import org.openqa.selenium.remote.SessionQueryResult;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.swing.JOptionPane;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.caillette.plussave.GooglePlusSave.HUB_URL;

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

