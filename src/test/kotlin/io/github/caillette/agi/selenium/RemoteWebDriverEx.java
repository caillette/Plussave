package io.github.caillette.agi.selenium;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Beta;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Keyboard;
import org.openqa.selenium.interactions.Mouse;
import org.openqa.selenium.logging.LocalLogs;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingHandler;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.logging.NeedsLocalLogs;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.ErrorHandler;
import org.openqa.selenium.remote.ExecuteMethod;
import org.openqa.selenium.remote.FileDetector;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.JsonToBeanConverter;
import org.openqa.selenium.remote.RemoteExecuteMethod;
import org.openqa.selenium.remote.RemoteKeyboard;
import org.openqa.selenium.remote.RemoteLogs;
import org.openqa.selenium.remote.RemoteMouse;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.remote.UselessFileDetector;
import org.openqa.selenium.remote.internal.JsonToWebElementConverter;
import org.openqa.selenium.remote.internal.WebElementToJsonConverter;
import org.openqa.selenium.security.Credentials;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Alter Hu https://sqa.stackexchange.com/a/20223
 */
@SuppressWarnings( "deprecation" )
public class RemoteWebDriverEx extends RemoteWebDriver {

  // TODO(dawagner): This static logger should be unified with the per-instance localLogs
  /*
   *
   * 2016/03/07 Alter: Add the use exising session for testing
   */
  private static final Logger logger = Logger.getLogger( RemoteWebDriverEx.class.getName() ) ;
  private Level level = Level.FINE ;

  private ErrorHandler errorHandler = new ErrorHandler() ;
  private CommandExecutor executor ;
  private Capabilities capabilities ;
  private SessionId sessionId ;
  private FileDetector fileDetector = new UselessFileDetector() ;
  private ExecuteMethod executeMethod ;

  private JsonToWebElementConverter converter ;

  private RemoteKeyboard keyboard ;
  private RemoteMouse mouse ;
  private Logs remoteLogs ;
  private LocalLogs localLogs ;

  private int w3cComplianceLevel = 0 ;

  private boolean useSession = false;

  public void useSeleniumSession( boolean useSession ) {
    this.useSession = useSession;
  }

  // For default testing to switch to firefox
  protected RemoteWebDriverEx() {
    this( ( URL ) null, DesiredCapabilities.firefox() );
  }


  protected RemoteWebDriverEx( boolean useSessionId ) {
    this( ( URL ) null, DesiredCapabilities.firefox(), useSessionId ) ;
  }

  public RemoteWebDriverEx(
      final CommandExecutor executor,
      final Capabilities desiredCapabilities,
      final Capabilities requiredCapabilities,
      final boolean useSessionId
  ) {
    this.executor = checkNotNull( executor ) ;

    //recover scenario script
    Thread.currentThread() ;
//    Thread.setDefaultUncaughtExceptionHandler(new RecoveryScenario());

    init( desiredCapabilities, requiredCapabilities ) ;

    if( executor instanceof NeedsLocalLogs ) {
      ( ( NeedsLocalLogs ) executor ).setLocalLogs( localLogs );
    }
    if( useSessionId ) {
      getExistingSessionId() ;
    }
    if( this.sessionId == null ) {
      try {
        startClient() ;
      } catch( RuntimeException e ) {
        try {
          stopClient() ;
        } catch( final Exception ignore ) {
          // Ignore the clean-up exception. We'll propagate the original failure.
        }
        throw e;
      }

      try {
        startSession( desiredCapabilities, requiredCapabilities ) ;
      } catch( final RuntimeException e ) {
        try {
          quit() ;
        } catch( Exception ignored ) {
          // Ignore the clean-up exception. We'll propagate the original failure.
        }
        throw e ;
      }
    }

  }

  public RemoteWebDriverEx( final CommandExecutor executor, final Capabilities desiredCapabilities ) {
    this( executor, desiredCapabilities, null, false ) ;
  }

  public RemoteWebDriverEx( final Capabilities desiredCapabilities ) {
    this( ( URL ) null, desiredCapabilities );
  }

  public RemoteWebDriverEx( final Capabilities desiredCapabilities, final boolean useSessionId ) {
    this( ( URL ) null, desiredCapabilities, useSessionId );
  }

  public RemoteWebDriverEx(
      final URL remoteAddress,
      final Capabilities desiredCapabilities,
      final Capabilities requiredCapabilities
  ) {
    this(
        new HttpCommandExecutor( remoteAddress ),
        desiredCapabilities,
        requiredCapabilities,
        false
    ) ;
  }

  public RemoteWebDriverEx(
      final URL remoteAddress,
      final Capabilities desiredCapabilities,
      final Capabilities requiredCapabilities,
      final boolean useSessionId
  ) {
    this( new HttpCommandExecutor( remoteAddress ), desiredCapabilities,
        requiredCapabilities, useSessionId ) ;
  }

  public RemoteWebDriverEx( final URL remoteAddress, final Capabilities desiredCapabilities ) {
    this( new HttpCommandExecutor( remoteAddress ), desiredCapabilities, null, true ) ;
  }

  public RemoteWebDriverEx(
      final URL remoteAddress,
      final Capabilities desiredCapabilities,
      final boolean useSessionId
  ) {
    this( new HttpCommandExecutor( remoteAddress ), desiredCapabilities, null, useSessionId ) ;
  }

  public int getW3CStandardComplianceLevel() {
    return w3cComplianceLevel ;
  }

  private void init(
      final Capabilities desiredCapabilities,
      final Capabilities requiredCapabilities
  ) {
    logger.addHandler(LoggingHandler.getInstance() ) ;

    converter = new JsonToWebElementConverter( this ) ;
    executeMethod = new RemoteExecuteMethod( this ) ;
    keyboard = new RemoteKeyboard( executeMethod ) ;
    mouse = new RemoteMouse( executeMethod ) ;

    final ImmutableSet.Builder< String > builder = new ImmutableSet.Builder<String>() ;

    boolean isProfilingEnabled = desiredCapabilities != null
        && desiredCapabilities.is( CapabilityType.ENABLE_PROFILING_CAPABILITY ) ;
    if( requiredCapabilities != null &&
        requiredCapabilities.getCapability( CapabilityType.ENABLE_PROFILING_CAPABILITY ) != null
    ) {
      isProfilingEnabled = requiredCapabilities.is( CapabilityType.ENABLE_PROFILING_CAPABILITY ) ;
    }
    if( isProfilingEnabled ) {
      builder.add( LogType.PROFILER ) ;
    }

    LoggingPreferences mergedLoggingPrefs = new LoggingPreferences() ;
    if( desiredCapabilities != null ) {
      mergedLoggingPrefs.addPreferences( ( LoggingPreferences )
          desiredCapabilities.getCapability( CapabilityType.LOGGING_PREFS ) ) ;
    }
    if( requiredCapabilities != null ) {
      mergedLoggingPrefs.addPreferences( ( LoggingPreferences )
          requiredCapabilities.getCapability( CapabilityType.LOGGING_PREFS ) ) ;
    }
    if( ( mergedLoggingPrefs.getEnabledLogTypes().contains( LogType.CLIENT )
        && mergedLoggingPrefs.getLevel( LogType.CLIENT ) != Level.OFF )
        || !mergedLoggingPrefs.getEnabledLogTypes().contains( LogType.CLIENT )
    ) {
      builder.add( LogType.CLIENT ) ;
    }

    Set<String> logTypesToInclude = builder.build() ;

    LocalLogs performanceLogger = LocalLogs.getStoringLoggerInstance( logTypesToInclude ) ;
    LocalLogs clientLogs = LocalLogs.getHandlerBasedLoggerInstance(
        LoggingHandler.getInstance(), logTypesToInclude ) ;
    localLogs = LocalLogs.getCombinedLogsHolder( clientLogs, performanceLogger ) ;
    remoteLogs = new RemoteLogs( executeMethod, localLogs ) ;
  }

  /**
   * Set the file detector to be used when sending keyboard input. By default,
   * this is set to a file detector that does nothing.
   *
   * @param detector The detector to use. Must not be null.
   * @see FileDetector
   * @see UselessFileDetector
   */
  public void setFileDetector( final FileDetector detector ) {
    if( detector == null ) {
      throw new WebDriverException( "You may not set a file detector that is null" );
    }
    fileDetector = detector ;
  }

  public SessionId getSessionId() {
    return sessionId ;
  }

  public Capabilities getCapabilities() {
    return capabilities ;
  }

  /**
   * Doesn't work with Selenium 3.4.0, need a session to get all existing sessions.
   */
  @SuppressWarnings( { "rawtypes", "unchecked" } )
  public void getExistingSessionId() {

    final Response response = execute( DriverCommand.GET_ALL_SESSIONS ) ;
    final ArrayList sessionsList = ( ArrayList ) response.getValue() ;
    int size = sessionsList.size() ;
    if( size > 0 ) {
      // Here it will get the first sesion container ,if you have multiply
      // sesssion ,it always get the top sessions in the session
      // containers
      final Map< String, Object > rawCapabilities = ( Map< String, Object > )
          sessionsList.get( sessionsList.size() - 1 ) ;

      DesiredCapabilities returnedCapabilities = new DesiredCapabilities() ;
      for( Map.Entry<String, Object> entry : rawCapabilities.entrySet() ) {
        // Handle the platform later
        if( CapabilityType.PLATFORM.equals( entry.getKey() ) ) {
          continue;
        }
        returnedCapabilities.setCapability( entry.getKey(), entry.getValue() ) ;
      }
      String platformString = ( String ) rawCapabilities.get( CapabilityType.PLATFORM ) ;
      Platform platform ;
      try {
        if( platformString == null || "".equals( platformString ) ) {
          platform = Platform.ANY ;
        } else {
          platform = Platform.valueOf( platformString ) ;
        }
      } catch( IllegalArgumentException e ) {
        // The server probably responded with a name matching the os.name system property.
        // Try to recover and parse this.
        platform = Platform.extractFromSysProperty( platformString ) ;
      }
      returnedCapabilities.setPlatform( platform ) ;

      capabilities = returnedCapabilities ;
      String oldsessionid = ( String ) rawCapabilities.get( "id" ) ;
      sessionId = new SessionId( oldsessionid ) ;
      logger.info( "Found Existing sessionId: " + oldsessionid
          + " from session container,and emulate all the operations in this session." ) ;
      if( response.getStatus() == null ) {
        w3cComplianceLevel = 1 ;
      }
    }

  }

  protected void setSessionId( final String opaqueKey ) {
    sessionId = new SessionId( opaqueKey ) ;
  }

  protected void startSession( final Capabilities desiredCapabilities ) {
    startSession( desiredCapabilities, null ) ;
  }

  @SuppressWarnings( { "unchecked" } )
  protected void startSession(
      final Capabilities desiredCapabilities,
      final Capabilities requiredCapabilities
  ) {

    final ImmutableMap.Builder< String, Capabilities > paramBuilder = new ImmutableMap.Builder<String, Capabilities>() ;
    paramBuilder.put( "desiredCapabilities", desiredCapabilities ) ;
    if( requiredCapabilities != null ) {
      paramBuilder.put( "requiredCapabilities", requiredCapabilities ) ;
    }
    final Map< String, ? > parameters = paramBuilder.build() ;

    final Response response = execute( DriverCommand.NEW_SESSION, parameters ) ;

    Map< String, Object > rawCapabilities = ( Map< String, Object > ) response.getValue() ;
    final DesiredCapabilities returnedCapabilities = new DesiredCapabilities() ;
    for( Map.Entry<String, Object> entry : rawCapabilities.entrySet() ) {
      // Handle the platform later
      if( CapabilityType.PLATFORM.equals( entry.getKey() ) ) {
        continue ;
      }
      returnedCapabilities.setCapability( entry.getKey(), entry.getValue() ) ;
    }
    String platformString = ( String ) rawCapabilities.get( CapabilityType.PLATFORM ) ;
    Platform platform ;
    try {
      if( platformString == null || "".equals( platformString ) ) {
        platform = Platform.ANY ;
      } else {
        platform = Platform.valueOf( platformString ) ;
      }
    } catch( IllegalArgumentException e ) {
      // The server probably responded with a name matching the os.name system property.
      // Try to recover and parse this.
      platform = Platform.extractFromSysProperty( platformString ) ;
    }
    returnedCapabilities.setPlatform( platform ) ;

    capabilities = returnedCapabilities ;
    sessionId = new SessionId( response.getSessionId() ) ;
    if( response.getStatus() == null ) {
      w3cComplianceLevel = 1 ;
    }
  }

  public Object executeScript( String script, final Object... args ) {
    if( ! capabilities.isJavascriptEnabled() ) {
      throw new UnsupportedOperationException(
          "You must be using an underlying instance of WebDriver that supports executing javascript"
      ) ;
    }

    // Escape the quote marks
    script = script.replaceAll( "\"", "\\\"" );

    Iterable< Object > convertedArgs = Iterables.transform(
        Lists.newArrayList( args ), new WebElementToJsonConverter() ) ;

    Map< String, ? > params = ImmutableMap.of(
        "script", script,
        "args", Lists.newArrayList( convertedArgs )
    ) ;

    return execute( DriverCommand.EXECUTE_SCRIPT, params ).getValue() ;
  }

  public Object executeAsyncScript( String script, final Object... args ) {
    if( ! isJavascriptEnabled() ) {
      throw new UnsupportedOperationException(
          "You must be using an underlying instance of " +
              "WebDriver that supports executing javascript"
      ) ;
    }

    // Escape the quote marks
    script = script.replaceAll( "\"", "\\\"" ) ;

    Iterable< Object > convertedArgs = Iterables.transform( Lists.newArrayList( args ),
        new WebElementToJsonConverter() ) ;

    Map<String, ?> params = ImmutableMap.of(
        "script", script,
        "args", Lists.newArrayList( convertedArgs )
    ) ;

    return execute( DriverCommand.EXECUTE_ASYNC_SCRIPT, params ).getValue() ;
  }

  private boolean isJavascriptEnabled() {
    return capabilities.isJavascriptEnabled() ;
  }

  public Options manage() {
    return new RemoteWebDriverOptions() ;
  }

  protected void setElementConverter( final JsonToWebElementConverter converter ) {
    this.converter = converter ;
  }

  protected JsonToWebElementConverter getElementConverter() {
    return converter ;
  }

  protected Response execute( final String driverCommand, final Map< String, ? > parameters ) {
    final Command command = new Command( sessionId, driverCommand, parameters ) ;
    Response response ;

    long start = System.currentTimeMillis() ;
    String currentName = Thread.currentThread().getName() ;
    Thread.currentThread()
        .setName( String.format( "Forwarding %s on session %s to remote", driverCommand, sessionId )
    ) ;
    try {
      log( sessionId, command.getName(), command, When.BEFORE ) ;
      response = executor.execute( command ) ;
      log( sessionId, command.getName(), command, When.AFTER ) ;

      if( response == null ) {
        return null ;
      }

      // Unwrap the response value by converting any JSON objects of the form
      // {"ELEMENT": id} to RemoteWebElements.
      Object value = converter.apply( response.getValue() ) ;
      response.setValue( value ) ;
    } catch ( WebDriverException e ) {
      throw e ;
    } catch( Exception e ) {
      log( sessionId, command.getName(), command, When.EXCEPTION ) ;
      String errorMessage = "Error communicating with the remote browser. " + "It may have died." ;
      if( driverCommand.equals( DriverCommand.NEW_SESSION ) ) {
        errorMessage = "Could not start a new session. Possible causes are "
            + "invalid address of the remote server or browser start-up failure." ;
      }
      UnreachableBrowserException ube = new UnreachableBrowserException( errorMessage, e ) ;
      if( getSessionId() != null ) {
        ube.addInfo( WebDriverException.SESSION_ID, getSessionId().toString() ) ;
      }
      if( getCapabilities() != null ) {
        ube.addInfo( "Capabilities", getCapabilities().toString() ) ;
      }
      throw ube ;
    } finally {
      Thread.currentThread().setName( currentName ) ;
    }

    try {
      errorHandler.throwIfResponseFailed( response, System.currentTimeMillis() - start ) ;
    } catch( WebDriverException ex ) {
      if( parameters != null && parameters.containsKey( "using" ) &&
          parameters.containsKey( "value" )
      ) {
        ex.addInfo(
            "*** Element info",
            String.format(
                "{Using=%s, value=%s}",
                parameters.get( "using" ),
                parameters.get( "value" )
            )
        ) ;
      }
      ex.addInfo( WebDriverException.DRIVER_INFO, this.getClass().getName() ) ;
      if( getSessionId() != null ) {
        ex.addInfo( WebDriverException.SESSION_ID, getSessionId().toString() ) ;
      }
      if( getCapabilities() != null ) {
        ex.addInfo( "Capabilities", getCapabilities().toString() ) ;
      }
      Throwables.propagate( ex ) ;
    }
    return response ;
  }

  protected Response execute( String command ) {
    return execute( command, ImmutableMap.of() ) ;
  }

  protected ExecuteMethod getExecuteMethod() {
    return executeMethod ;
  }

  public Keyboard getKeyboard() {
    return keyboard ;
  }

  public Mouse getMouse() {
    return mouse ;
  }

  /**
   * Override this to be notified at key points in the execution of a command.
   *
   * @param sessionId   the session id.
   * @param commandName the command that is being executed.
   * @param toLog       any data that might be interesting.
   * @param when        verb tense of "Execute" to prefix message
   */
  protected void log(
      final SessionId sessionId,
      final String commandName,
      final Object toLog,
      final When when
  ) {
    String text = "" + toLog ;
    if( commandName.equals( DriverCommand.EXECUTE_SCRIPT )
        || commandName.equals( DriverCommand.EXECUTE_ASYNC_SCRIPT ) ) {
      if( text.length() > 100 && Boolean.getBoolean( "webdriver.remote.shorten_log_messages" ) ) {
        text = text.substring( 0, 100 ) + "..." ;
      }
    }
    switch( when ) {
      case BEFORE :
        logger.info( "Executing: " + commandName + " " + text ) ;
        break ;
      case AFTER :
        logger.info( "Executed: " + text ) ;
        break ;
      case EXCEPTION :
        logger.info( "Exception: " + text ) ;
        break ;
      default :
        logger.info( text ) ;
        break ;
    }
  }

  public FileDetector getFileDetector() {
    return fileDetector ;
  }

  protected class RemoteWebDriverOptions implements Options {

    @Beta
    public Logs logs() {
      return remoteLogs;
    }

    public void addCookie( Cookie cookie ) {
      cookie.validate() ;
      execute( DriverCommand.ADD_COOKIE, ImmutableMap.of( "cookie", cookie ) ) ;
    }

    public void deleteCookieNamed( String name ) {
      execute( DriverCommand.DELETE_COOKIE, ImmutableMap.of( "name", name ) ) ;
    }

    public void deleteCookie( final Cookie cookie ) {
      deleteCookieNamed( cookie.getName() ) ;
    }

    public void deleteAllCookies() {
      final Object response = execute( DriverCommand.DELETE_ALL_COOKIES ) ;
      System.out.println( response ) ;
    }

    @SuppressWarnings( { "unchecked" } )
    public Set< Cookie > getCookies() {
      Object returned = execute( DriverCommand.GET_ALL_COOKIES ).getValue() ;

      Set< Cookie > toReturn = new HashSet<>() ;

      List< Map< String, Object > > cookies = new JsonToBeanConverter().convert(
          List.class, returned ) ;
      if( cookies == null ) {
        return toReturn ;
      }

      for( Map< String, Object > rawCookie : cookies ) {
        String name = ( String ) rawCookie.get( "name" ) ;
        String value = ( String ) rawCookie.get( "value" ) ;
        String path = ( String ) rawCookie.get( "path" ) ;
        String domain = ( String ) rawCookie.get( "domain" ) ;
        boolean secure = rawCookie.containsKey( "secure" ) &&
            ( Boolean ) rawCookie.get( "secure" ) ;

        final Number expiryNum = ( Number ) rawCookie.get( "expiry" ) ;
        Date expiry = expiryNum == null ? null :
            new Date( TimeUnit.SECONDS.toMillis( expiryNum.longValue() ) ) ;

        toReturn.add( new Cookie.Builder( name, value ).path( path ).domain( domain )
            .isSecure( secure ).expiresOn( expiry ).build() ) ;
      }

      return toReturn ;
    }

    public Cookie getCookieNamed( String name ) {
      Set< Cookie > allCookies = getCookies() ;
      for( Cookie cookie : allCookies ) {
        if( cookie.getName().equals( name ) ) {
          return cookie ;
        }
      }
      return null ;
    }

    public Timeouts timeouts() {
      return new RemoteWebDriverOptions.RemoteTimeouts() ;
    }

    public ImeHandler ime() {
      return new RemoteWebDriverOptions.RemoteInputMethodManager() ;
    }

    @Beta
    public Window window() {
      return new RemoteWebDriverOptions.RemoteWindow() ;
    }

    protected class RemoteInputMethodManager implements ImeHandler {

      @SuppressWarnings( "unchecked" )
      public List< String > getAvailableEngines() {
        Response response = execute( DriverCommand.IME_GET_AVAILABLE_ENGINES ) ;
        return ( List<String> ) response.getValue();
      }

      public String getActiveEngine() {
        Response response = execute( DriverCommand.IME_GET_ACTIVE_ENGINE );
        return ( String ) response.getValue();
      }

      public boolean isActivated() {
        Response response = execute( DriverCommand.IME_IS_ACTIVATED );
        return ( Boolean ) response.getValue();
      }

      public void deactivate() {
        execute( DriverCommand.IME_DEACTIVATE );
      }

      public void activateEngine( String engine ) {
        execute( DriverCommand.IME_ACTIVATE_ENGINE, ImmutableMap.of( "engine", engine ) );
      }
    } // RemoteInputMethodManager class

    protected class RemoteTimeouts implements Timeouts {

      public Timeouts implicitlyWait( long time, TimeUnit unit ) {
        execute( DriverCommand.SET_TIMEOUT,
            ImmutableMap.of( "type", "implicit", "ms", TimeUnit.MILLISECONDS.convert( time, unit ) ) );
        return this;
      }

      public Timeouts setScriptTimeout( long time, TimeUnit unit ) {
        execute( DriverCommand.SET_TIMEOUT,
            ImmutableMap.of( "type", "script", "ms", TimeUnit.MILLISECONDS.convert( time, unit ) ) );
        return this;
      }

      public Timeouts pageLoadTimeout( long time, TimeUnit unit ) {
        execute( DriverCommand.SET_TIMEOUT,
            ImmutableMap.of( "type", "page load", "ms", TimeUnit.MILLISECONDS.convert( time, unit ) ) );
        return this;
      }
    } // timeouts class.

    @Beta
    protected class RemoteWindow implements Window {

      public void setSize( Dimension targetSize ) {
        execute( DriverCommand.SET_CURRENT_WINDOW_SIZE,
            ImmutableMap.of( "width", targetSize.width, "height", targetSize.height ) );
      }

      public void setPosition( Point targetPosition ) {
        executeScript( "window.screenX = arguments[0]; window.screenY = arguments[1]", targetPosition.x,
            targetPosition.y );
      }

      @SuppressWarnings( { "unchecked" } )
      public Dimension getSize() {
        Response response = execute( DriverCommand.GET_CURRENT_WINDOW_SIZE );

        Map<String, Object> rawSize = ( Map<String, Object> ) response.getValue();

        int width = ( ( Number ) rawSize.get( "width" ) ).intValue();
        int height = ( ( Number ) rawSize.get( "height" ) ).intValue();

        return new Dimension( width, height );
      }

      Map<String, Object> rawPoint;

      @SuppressWarnings( "unchecked" )
      public Point getPosition() {
        if( getW3CStandardComplianceLevel() == 0 ) {
          Response response = execute( DriverCommand.GET_CURRENT_WINDOW_POSITION,
              ImmutableMap.of( "windowHandle", "current" ) );
          rawPoint = ( Map<String, Object> ) response.getValue();
        } else {
          rawPoint = ( Map<String, Object> ) executeScript( "return {x: window.screenX, y: window.screenY}" );
        }

        int x = ( ( Number ) rawPoint.get( "x" ) ).intValue();
        int y = ( ( Number ) rawPoint.get( "y" ) ).intValue();

        return new Point( x, y );
      }

      public void maximize() {
        if( getW3CStandardComplianceLevel() == 0 ) {
          execute( DriverCommand.MAXIMIZE_CURRENT_WINDOW, ImmutableMap.of( "windowHandle", "current" ) );
        } else {
          execute( DriverCommand.MAXIMIZE_CURRENT_WINDOW );
        }
      }

      public void fullscreen() {
        execute( DriverCommand.FULLSCREEN_CURRENT_WINDOW );
      }
    }
  }


  protected class RemoteTargetLocator implements TargetLocator {

    public WebDriver frame( int frameIndex ) {
      execute( DriverCommand.SWITCH_TO_FRAME, ImmutableMap.of( "id", frameIndex ) );
      return RemoteWebDriverEx.this;
    }

    public WebDriver frame( String frameName ) {
      String name = frameName.replaceAll( "(['\"\\\\#.:;,!?+<>=~*^$|%&@`{}\\-/\\[\\]\\(\\)])", "\\\\$1" );
      List<WebElement> frameElements = RemoteWebDriverEx.this
          .findElements( By.cssSelector( "frame[name='" + name + "'],iframe[name='" + name + "']" ) );
      if( frameElements.size() == 0 ) {
        frameElements = RemoteWebDriverEx.this.findElements( By.cssSelector( "frame#" + name + ",iframe#" + name ) );
      }
      if( frameElements.size() == 0 ) {
        throw new NoSuchFrameException( "No frame element found by name or id " + frameName );
      }
      return frame( frameElements.get( 0 ) );
    }

    public WebDriver frame( WebElement frameElement ) {
      Object elementAsJson = new WebElementToJsonConverter().apply( frameElement );
      execute( DriverCommand.SWITCH_TO_FRAME, ImmutableMap.of( "id", elementAsJson ) );
      return RemoteWebDriverEx.this;
    }

    public WebDriver parentFrame() {
      execute( DriverCommand.SWITCH_TO_PARENT_FRAME );
      return RemoteWebDriverEx.this;
    }

    public WebDriver window( String windowHandleOrName ) {
      if( getW3CStandardComplianceLevel() == 0 ) {
        execute( DriverCommand.SWITCH_TO_WINDOW, ImmutableMap.of( "name", windowHandleOrName ) );
        return RemoteWebDriverEx.this;
      } else {
        try {
          execute( DriverCommand.SWITCH_TO_WINDOW, ImmutableMap.of( "handle", windowHandleOrName ) );
          return RemoteWebDriverEx.this;
        } catch( NoSuchWindowException nsw ) {
          // simulate search by name
          String original = getWindowHandle();
          for( String handle : getWindowHandles() ) {
            switchTo().window( handle );
            if( windowHandleOrName.equals( executeScript( "return window.name" ) ) ) {
              return RemoteWebDriverEx.this; // found by name
            }
          }
          switchTo().window( original );
          throw nsw;
        }
      }
    }

    public WebDriver defaultContent() {
      Map<String, Object> frameId = Maps.newHashMap();
      frameId.put( "id", null );
      execute( DriverCommand.SWITCH_TO_FRAME, frameId );
      return RemoteWebDriverEx.this;
    }

    public WebElement activeElement() {
      Response response = execute( DriverCommand.GET_ACTIVE_ELEMENT );
      return ( WebElement ) response.getValue();
    }

    public Alert alert() {
      execute( DriverCommand.GET_ALERT_TEXT ) ;
      return new RemoteAlert() ;
    }
  }

  private class RemoteAlert implements Alert {

    public void dismiss() {
      if( getW3CStandardComplianceLevel() > 0 ) {
        execute( DriverCommand.DISMISS_ALERT ) ;
      } else {
        execute( DriverCommand.DISMISS_ALERT ) ;
      }
    }


// Do we need those?


    @Override
    public void accept() {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public String getText() {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void sendKeys( String keysToSend ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void setCredentials( Credentials credentials ) {
      throw new UnsupportedOperationException( "TODO" );
    }

    @Override
    public void authenticateUsing( Credentials credentials ) {
      throw new UnsupportedOperationException( "TODO" );
    }
  }
}
