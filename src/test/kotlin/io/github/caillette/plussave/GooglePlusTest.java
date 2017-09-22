package io.github.caillette.plussave;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.github.caillette.plussave.selenium.RemoteWebDriver2;
import io.github.caillette.plussave.selenium.SessionQueryResult;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import javax.swing.JOptionPane;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

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
 * java -jar ~/Downloads/selenium-server-standalone-3.5.3.jar -role sandalone
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

  @Test
  public void name() throws Exception {
    final DesiredCapabilities capabilities = DesiredCapabilities.firefox() ;
    capabilities.setCapability( "driver.version", "3.5.3" ) ;
    final WebDriver driver = new RemoteWebDriver(
        HUB_URL,
        capabilities
    ) ;

    driver.get( "https://plus.google.com/+LaurentCaillette" ) ;
    JOptionPane.showMessageDialog( null, "Sign on and press OK." ) ;


    // Do not close the browser so we can reuse sessions.
    // driver.quit() ;
  }

  private static void graveyard0( WebDriver driver ) {
    final WebElement signInElement = driver.findElement(
        By.xpath( "//div/a[ contains( ., 'Sign in' ) ]" ) );
    signInElement.click();

    final WebElement identifierIdElement = driver.findElement( By.id( "identifierId" ) );
    identifierIdElement.sendKeys( "laurent.caillette@gmail.com" );
    identifierIdElement.submit();

    final WebElement nameNext = driver.findElement(
        By.xpath( "//content/span[ contains( ., 'Next' ) ]" ) );
    nameNext.click();

    // Clicking enables password area.
    final WebElement passwordEnabler = driver.findElement( By.xpath(
        "//div[ contains( ., 'Enter your password' ) ]" ) );
    passwordEnabler.click();

    final WebElement passwordFieldElement = driver.findElement(
        By.xpath( "//div/input[ type='password' ]" ) );
    passwordFieldElement.sendKeys( "bad" );
  }


// =======
// Fixture
// =======

  private static final URL HUB_URL ;

  static {
    try {
      HUB_URL = new URL( "http://localhost:4444/wd/hub/" ) ;
    } catch( MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }

    System.setProperty( "webdriver.gecko.driver", "/usr/local/bin/geckodriver" ) ;
  }

}

