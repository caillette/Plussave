package io.github.caillette.plussave.selenium;

import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;

import java.io.IOException;
import java.net.URL;

/**
 * @author Yanir https://stackoverflow.com/a/38827934/1923328
 */
public class AttachedWebDriver extends RemoteWebDriver {

  public AttachedWebDriver( final URL url, final String sessionId ) {
    setSessionId( sessionId ) ;
    setCommandExecutor( new HttpCommandExecutor( url ) {
      @Override
      public Response execute( final Command command ) throws IOException {
        if( command.getName() != "newSession" ) {
          return super.execute( command ) ;
        }
        return super.execute( new Command( getSessionId(), "getCapabilities" ) ) ;
      }
    } ) ;
    startSession( new DesiredCapabilities() ) ;
  }
}