package io.github.caillette.plussave;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver2;

import javax.swing.JOptionPane;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    for( int i = 0 ; i < 300 ; i ++ ) {
      driver.executeScript( "window.scrollTo( 0, document.body.scrollHeight )") ;
    }

    final List< WebElement > elements = driver.findElements( By.xpath( "//div[ " +
        "contains( @jslog, 'track:impression,click' ) and " +
        "contains( @tabindex, '-1' ) " +
        "]"
    ) ) ;
    for( final WebElement webElement : elements ) {
      processArticleElement( webElement ) ;
    }

    // Do not close the browser so we can reuse sessions.
    // driver.quit() ;
  }

  private static void processArticleElement( final WebElement articleRootElement ) {

    final List< WebElement > articleTextElement = articleRootElement.findElements( By.xpath(
        ".//div[ contains( @id, 'body:' ) ]//div[ " +
        POST_TEXT_ELEMENT_LEAF_XPATH_CONDITION + " ]"
    ) ) ;
    final String articleTextHtml ;
    if( articleTextElement.isEmpty() ) {
      articleTextHtml = MAGIC_EMPTY ;
    } else {
      articleTextHtml = articleTextElement.get( 0 ).getAttribute( "innerHTML" ) ;
    }

    final List< WebElement > links = articleRootElement.findElements(
        By.xpath( ".//a[ contains( @rel, 'nofollow' ) ]" ) ) ;

    final Element linkElement ;
    if( links.isEmpty() ) {
      linkElement = null ;
    } else {
      // The content of the 'a' element is a lot of div we don't want here.
      linkElement = new Element( "a" ) ;
      final String href = links.get( 0 ).getAttribute( "href" ) ;
      linkElement.attributes().put( new Attribute( "href", href ) ) ;
    }
    System.out.println(
        WebElement.class.getSimpleName() + ": " +
        articleTextHtml.replaceAll( "\n", "" ) + " " +
        anchorAsText( linkElement )
    ) ;

    final List< WebElement > commentWebElements = articleRootElement.findElements( By.xpath(
        ".//div[ contains( @aria-label, 'comments' ) ]"
    ) ) ;
    for( final WebElement commentWebElement : commentWebElements ) {
      processCommentRootElement( commentWebElement ) ;
    }

  }

  private static void processCommentRootElement( final WebElement commentRootWebElement ) {
    final List< WebElement > commentWebElements = commentRootWebElement.findElements( By.xpath(
        "./div/div/div/div/span/span"
    ) ) ;
    for( final WebElement commentWebElement : commentWebElements ) {
      final String innerHTML = commentWebElement.getAttribute( "innerHTML" ) ;
      final Element commentBody = Jsoup.parseBodyFragment( innerHTML ).body() ;
      // jsoup uses JQuery-like selectors.
      final Element link = commentBody.select( "a[href]" ).first() ;

      if( link != null ) {
        cleanAnchor( link ) ;
      }

      System.out.println( "  Comment: " + commentBody.text() + " " + anchorAsText( link ) ) ;
    }
  }

  /**
   * Keep only {@code href} attribute and inner text.
   */
  private static void cleanAnchor( final Element anchorElement ) {
    Preconditions.checkArgument( "a".equals( anchorElement.nodeName() ), anchorElement.html() ) ;
    final Set< String > attributesToRemove = new LinkedHashSet<>() ;
    for( final Attribute attribute : anchorElement.attributes() ) {
      if( ! "href".equals( attribute.getKey() ) ) {
        attributesToRemove.add( attribute.getKey() ) ;
      }
    }
    for( final String attributeToRemove : attributesToRemove ) {
      anchorElement.removeAttr( attributeToRemove ) ;
    }
  }

  private static String anchorAsText( final Element anchorElement ) {
    if( anchorElement == null ) {
      return "" ;
    } else {
      final StringBuilder stringBuilder = new StringBuilder() ;
      stringBuilder
          .append( "<a href='" )
          .append( anchorElement.attr( "href" ) )
          .append( "' >" )
          .append( anchorElement.text() )
          .append( "</a>" )
      ;
      return stringBuilder.toString() ;
    }
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

  /**
   * RTL untested.
   */
  private static final String POST_TEXT_ELEMENT_LEAF_XPATH_CONDITION = "@dir='ltr' or @dir='rtl'" ;

  private static final String MAGIC_EMPTY = "[empty]" ;


}

