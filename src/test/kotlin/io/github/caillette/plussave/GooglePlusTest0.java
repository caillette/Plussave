package io.github.caillette.plussave;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;

/**
 *
 * <h1>Prerequisites</h1>
 * Run a Selenium Server (same as a Hub and a single Node):
 * <pre>
 * java -jar ~/Downloads/selenium-server-standalone-3.5.3.jar -role sandalone
 * </pre>
 * Default port for Hub is 4444.
 */
public class GooglePlusTest0 {
  @Test
  public void name() throws Exception {
    final DesiredCapabilities capabilities = DesiredCapabilities.firefox();
    final WebDriver driver = new RemoteWebDriver(
        new URL( "http://localhost:4444/wd/hub" ),
        capabilities
    );

    driver.get( "https://plus.google.com/+LaurentCaillette" );

    final WebElement signInElement = driver.findElement(
        By.xpath( "//div/a[ contains( ., 'Sign in' ) ]" ) ) ;
    signInElement.click() ;

    final WebElement identifierIdElement = driver.findElement( By.id( "identifierId" ) ) ;
    identifierIdElement.sendKeys( "laurent.caillette@gmail.com" ) ;
    identifierIdElement.submit() ;

    final WebElement nameNext = driver.findElement(
        By.xpath( "//content/span[ contains( ., 'Next' ) ]" ) ) ;
    nameNext.click() ;

    // Clicking enables password area.
    final WebElement passwordEnabler = driver.findElement( By.xpath(
        "//div[ contains( ., 'Enter your password' ) ]" ) ) ;
    passwordEnabler.click() ;

    final WebElement passwordFieldElement = driver.findElement(
        By.xpath( "//div/input[ type='password' ]" ) ) ;
    passwordFieldElement.sendKeys( "bad" ) ;

     driver.quit() ;
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

  static {
    // brew install geckodriver
    System.setProperty( "webdriver.gecko.driver", "/usr/local/bin/geckodriver" );
  }

}

