package io.github.caillette.plussave;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class GoogleTest {
  @Test
  public void name() throws Exception {
    final WebDriver driver = new FirefoxDriver() ;

    // And now use this to visit Google
    driver.get( "http://www.google.com" ) ;
    // Alternatively the same thing can be done like this:
    // driver.navigate().to("http://www.google.com") ;

    // Find the text input element by its name.
    WebElement element = driver.findElement( By.name( "q" ) ) ;

    // Enter something to search for:
    element.sendKeys( "Cheese!" ) ;

    // Now submit the form. WebDriver will find the form for us from the element.
    element.submit() ;

    // Check the title of the page.
    System.out.println( "Page title is: " + driver.getTitle() ) ;

    // Google's search is rendered dynamically with JavaScript.
    // Wait for the page to load, timeout after 10 seconds.
    ( new WebDriverWait( driver, 10 ) ).until( d ->
        d.getTitle().toLowerCase().startsWith( "cheese!" ) ) ;

    // Should see: "cheese! - Google Search".
    System.out.println( "Page title is: " + driver.getTitle() ) ;

    // Close the browser.
    driver.quit() ;
  }

  static {
    // brew install geckodriver
    System.setProperty( "webdriver.gecko.driver", "/usr/local/bin/geckodriver" ) ;
  }
}
