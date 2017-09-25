package io.github.caillette.plussave;

import com.google.common.base.Joiner;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver2;

import javax.swing.JOptionPane;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

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
public class GooglePlusSave {

  public static void main( final String... arguments ) throws Exception {
    final String profileName = "LaurentCaillette" ;

    final DesiredCapabilities capabilities = DesiredCapabilities.firefox() ;
    capabilities.setCapability( "driver.version", "3.5.3" ) ;
    final RemoteWebDriver2 driver = new RemoteWebDriver2(
        HUB_URL,
        capabilities
    ) ;

    driver.get( "https://plus.google.com/+" + profileName ) ;

    if( ! driver.reusingSession() ) {
      JOptionPane.showMessageDialog( null, "Once signed in, press OK." ) ;
    }

    for( int i = 0 ; i < 10 ; i ++ ) {
      driver.executeScript( "window.scrollTo( 0, document.body.scrollHeight )") ;
    }

    final List< WebElement > elements = driver.findElements( By.xpath( "//div[ " +
        "contains( @jslog, 'track:impression,click' ) and " +
        "contains( @tabindex, '-1' ) " +
        "]"
    ) ) ;
    for( final WebElement webElement : elements ) {
      process( webElement ) ;
    }

    // Do not close the browser so we can reuse sessions.
    // driver.quit() ;
  }

  private static void process( final WebElement webElement ) {
    final List< WebElement > links = webElement.findElements( By.xpath( ".//a" ) ) ;

    final String linksAsSingleLine = Joiner.on( ", " )
        .join( links.stream()
        .map( e -> e.getAttribute( "href" ) ).collect( Collectors.toList() ) )
    ;
    System.out.println(
        WebElement.class.getSimpleName() + ": " +
        webElement.getText().replaceAll( "\n", "" ) +
        linksAsSingleLine
    ) ;
  }



  /**
   * Do something to save images.
   *
   * <h1>Solution 1: automatic download</h1>
   * https://sqa.stackexchange.com/a/6317
   *
   * <h1>Solution 2: reuse headers and cookies</h1>
   * https://blog.codecentric.de/en/2010/07/file-downloads-with-selenium-mission-impossible/
   * http://ardesco.lazerycode.com/testing/webdriver/2012/07/25/how-to-download-files-with-selenium-and-why-you-shouldnt.html
   */
  private static void saveFile() {

  }

  public static final URL HUB_URL ;

  static {
    try {
      HUB_URL = new URL( "http://localhost:4444/wd/hub/" ) ;
    } catch( MalformedURLException e ) {
      throw new RuntimeException( e ) ;
    }

    System.setProperty( "webdriver.gecko.driver", "/usr/local/bin/geckodriver" ) ;
  }

}

