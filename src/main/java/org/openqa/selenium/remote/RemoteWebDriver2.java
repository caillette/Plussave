package org.openqa.selenium.remote;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Beta;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Keyboard;
import org.openqa.selenium.interactions.Mouse;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.logging.LocalLogs;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingHandler;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.logging.NeedsLocalLogs;
import org.openqa.selenium.remote.internal.JsonToWebElementConverter;
import org.openqa.selenium.remote.internal.WebElementToJsonConverter;
import org.openqa.selenium.security.Credentials;
import org.openqa.selenium.security.UserAndPassword;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.openqa.selenium.remote.CapabilityType.LOGGING_PREFS;
import static org.openqa.selenium.remote.CapabilityType.SUPPORTS_JAVASCRIPT;

/**
 * Support session reuse.
 *
 * @author Alter Hu https://sqa.stackexchange.com/a/20223
 * @author those of Selenium 3.4.0.
 *
 */
public class RemoteWebDriver2 extends RemoteWebDriver {


  // TODO(dawagner): This static logger should be unified with the per-instance localLogs
  private static final Logger logger = Logger.getLogger( RemoteWebDriver2.class.getName() );
  private Level level = Level.FINE;

  private ErrorHandler errorHandler = new ErrorHandler();
  private final URL hubUrl ;

  /**
   * HACK.
   */
  private HttpCommandExecutor2 executor ;

  private Capabilities capabilities;
  private SessionId sessionId;
  private SessionQueryResult.SessionDescriptor sessionDescriptor = null ;
  private FileDetector fileDetector = new UselessFileDetector();
  private ExecuteMethod executeMethod;

  private JsonToWebElementConverter converter;

  private RemoteKeyboard keyboard;
  private RemoteMouse mouse;
  private Logs remoteLogs;
  private LocalLogs localLogs;

  // For cglib
  protected RemoteWebDriver2() {
    init( new DesiredCapabilities() ) ;
    hubUrl = null ;
  }

  public RemoteWebDriver2(
      final HttpCommandExecutor2 executor,
      final Capabilities desiredCapabilities
  ) throws IOException {
    this( executor, desiredCapabilities, true ) ;
  }

  public RemoteWebDriver2(
      final HttpCommandExecutor2 executor,
      final Capabilities desiredCapabilities,
      final boolean useSessionId
  ) throws IOException {
    this.executor = executor ;
    hubUrl = executor.getAddressOfRemoteServer() ;

    init( desiredCapabilities ) ;

    if( executor instanceof NeedsLocalLogs ) {
      ( ( NeedsLocalLogs ) executor ).setLocalLogs( localLogs ) ;
    }

    if( useSessionId ) {
      getExistingSessionId( desiredCapabilities ) ;
    }

    try {
      startClient( desiredCapabilities ) ;
    } catch( RuntimeException e ) {
      try {
        stopClient( desiredCapabilities ) ;
      } catch( final Exception ignored ) {
        // Ignore the clean-up exception. We'll propagate the original failure.
      }
      throw e ;
    }

    try {
      startSession( desiredCapabilities );
    } catch( RuntimeException e ) {
      try {
        quit();
      } catch( Exception ignored ) {
        // Ignore the clean-up exception. We'll propagate the original failure.
      }

      throw e ;
    }
  }


  public RemoteWebDriver2(
      final URL remoteAddress,
      final Capabilities desiredCapabilities
  ) throws IOException {
    this( new HttpCommandExecutor2( remoteAddress ), desiredCapabilities ) ;
  }


  /**
   * Query the hub for existing sessions.
   */
  public void getExistingSessionId( final Capabilities desiredCapabilities ) throws IOException {
    if( hubUrl != null ) {
      final URL sessionsUrl ;
      try {
        sessionsUrl = new URL(
            hubUrl.getProtocol(),
            hubUrl.getHost(),
            hubUrl.getPort(),
            hubUrl.getFile() + ( hubUrl.getFile().endsWith( "/" ) ? "" : "/" ) + "sessions"
        ) ;
      } catch( MalformedURLException e ) {
        throw new RuntimeException( e ) ;
      }
      final String json = Resources.asCharSource( sessionsUrl, Charsets.UTF_8 ).read() ;
      final SessionQueryResult sessionQueryResult = decodeSessions( json ) ;
      if( ! sessionQueryResult.sessionDescriptors.isEmpty() ) {
        session : for( final SessionQueryResult.SessionDescriptor sessionDescriptor :
            sessionQueryResult.sessionDescriptors
        ) {
          // We could check for capabilities here but this would duplicate some work done
          // when establishing the session.
          final Map< String, Object > capabilitiesAsmap = new HashMap<> (
              sessionDescriptor.capabilities.asMap() ) ;
          capabilitiesAsmap.put(
              "driver.version", desiredCapabilities.getCapability( "driver.version") ) ;
          this.capabilities = new ImmutableCapabilities( capabilitiesAsmap ) ;
          sessionId = sessionDescriptor.id ;
          this.sessionDescriptor = sessionDescriptor ;
          logger.info( "Reusing existing " + SessionId.class.getSimpleName() + " " + sessionId +
              " from session container, hoping for compatible capabilities." ) ;
        }
      }
    }
  }

  public static SessionQueryResult decodeSessions( final String json ) {
    final JsonToBeanConverter jsonToBeanConverter = new JsonToBeanConverter() ;
    final HashMap< String, Object > converted = jsonToBeanConverter.convert( HashMap.class, json ) ;
    final long status = ( long ) converted.get( "status" ) ;
    final List< Map< String, Object > > rawSessionList =
        ( List< Map< String, Object > > ) converted.get( "value" ) ;
    final ImmutableList.Builder<SessionQueryResult.SessionDescriptor > builder =
        ImmutableList.builder() ;
    for( final Map< String, Object > sessionEntry : rawSessionList ) {
      final SessionId sessionId = new SessionId( ( String ) sessionEntry.get( "id" ) ) ;
      final Map< String, Object > capabilityMap =
          ( Map< String, Object > ) sessionEntry.get( "capabilities" ) ;
      final ImmutableCapabilities capabilities = new ImmutableCapabilities( capabilityMap ) ;
      final SessionQueryResult.SessionDescriptor sessionDescriptor =
          new SessionQueryResult.SessionDescriptor( sessionId, capabilities ) ;
      builder.add( sessionDescriptor ) ;
    }
    return new SessionQueryResult( status, builder.build() ) ;

  }


  private void init( Capabilities capabilities ) {
    capabilities = capabilities == null ? new DesiredCapabilities() : capabilities ;

    logger.addHandler( LoggingHandler.getInstance() );

    converter = new JsonToWebElementConverter( this );
    executeMethod = new RemoteExecuteMethod( this );
    keyboard = new RemoteKeyboard( executeMethod );
    mouse = new RemoteMouse( executeMethod );

    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>() ;

    boolean isProfilingEnabled = capabilities.is( CapabilityType.ENABLE_PROFILING_CAPABILITY ) ;
    if( isProfilingEnabled ) {
      builder.add( LogType.PROFILER ) ;
    }

    LoggingPreferences mergedLoggingPrefs = new LoggingPreferences() ;
    mergedLoggingPrefs.addPreferences(
        ( LoggingPreferences ) capabilities.getCapability( LOGGING_PREFS ) ) ;

    if( ! mergedLoggingPrefs.getEnabledLogTypes().contains( LogType.CLIENT ) ||
        mergedLoggingPrefs.getLevel( LogType.CLIENT ) != Level.OFF
    ) {
      builder.add( LogType.CLIENT ) ;
    }

    Set< String > logTypesToInclude = builder.build() ;

    LocalLogs performanceLogger = LocalLogs.getStoringLoggerInstance( logTypesToInclude ) ;
    LocalLogs clientLogs = LocalLogs.getHandlerBasedLoggerInstance(
        LoggingHandler.getInstance(), logTypesToInclude ) ;
    localLogs = LocalLogs.getCombinedLogsHolder( clientLogs, performanceLogger ) ;
    remoteLogs = new RemoteLogs( executeMethod, localLogs ) ;
  }

  /**
   * Set the file detector to be used when sending keyboard input.
   * By default, this is set to a file detector that does nothing.
   *
   * @param detector The detector to use. Must not be null.
   * @see FileDetector
   * @see LocalFileDetector
   * @see UselessFileDetector
   */
  public void setFileDetector( FileDetector detector ) {
    if( detector == null ) {
      throw new WebDriverException( "You may not set a file detector that is null" );
    }
    fileDetector = detector ;
  }

  public SessionId getSessionId() {
    return sessionId ;
  }

  protected void setSessionId( String opaqueKey ) {
    sessionId = new SessionId( opaqueKey ) ;
  }

  protected void startSession( Capabilities desiredCapabilities ) {
    startSession( desiredCapabilities, null ) ;
  }

  @SuppressWarnings( { "unchecked" } )
  protected void startSession( Capabilities desiredCapabilities,
                               Capabilities requiredCapabilities ) {
    ImmutableMap.Builder<String, Capabilities> paramBuilder =
        new ImmutableMap.Builder<>();
    paramBuilder.put( "desiredCapabilities", desiredCapabilities );
    if( requiredCapabilities != null ) {
      paramBuilder.put( "requiredCapabilities", requiredCapabilities );
    }
    Map<String, ?> parameters = paramBuilder.build();

    // HACK.
    executor.setForcedSessionDescriptor( sessionDescriptor ) ;
    Response response = execute( DriverCommand.NEW_SESSION, parameters );

    Map<String, Object> rawCapabilities = ( Map<String, Object> ) response.getValue();
    DesiredCapabilities returnedCapabilities = new DesiredCapabilities();
    for( Map.Entry<String, Object> entry : rawCapabilities.entrySet() ) {
      // Handle the platform later
      if( CapabilityType.PLATFORM.equals( entry.getKey() ) ) {
        continue;
      }
      returnedCapabilities.setCapability( entry.getKey(), entry.getValue() );
    }
    String platformString = ( String ) rawCapabilities.get( CapabilityType.PLATFORM );
    Platform platform;
    try {
      if( platformString == null || "".equals( platformString ) ) {
        platform = Platform.ANY;
      } else {
        platform = Platform.valueOf( platformString );
      }
    } catch( IllegalArgumentException e ) {
      // The server probably responded with a name matching the os.name
      // system property. Try to recover and parse this.
      platform = Platform.extractFromSysProperty( platformString );
    }
    returnedCapabilities.setPlatform( platform );

    if( rawCapabilities.containsKey( SUPPORTS_JAVASCRIPT ) ) {
      Object raw = rawCapabilities.get( SUPPORTS_JAVASCRIPT );
      if( raw instanceof String ) {
        returnedCapabilities.setCapability( SUPPORTS_JAVASCRIPT, Boolean.parseBoolean( ( String ) raw ) );
      } else if( raw instanceof Boolean ) {
        returnedCapabilities.setCapability( SUPPORTS_JAVASCRIPT, ( ( Boolean ) raw ).booleanValue() );
      }
    } else {
      returnedCapabilities.setCapability( SUPPORTS_JAVASCRIPT, true );
    }

    capabilities = returnedCapabilities;
    sessionId = new SessionId( response.getSessionId() );
  }

  /**
   * Method called before {@link #startSession(Capabilities) starting a new session}. The default
   * implementation is a no-op, but subtypes should override this method to define custom behavior.
   */
  protected void startClient() {
  }

  /**
   * Method called before {@link #startSession(Capabilities) starting a new session}. The default
   * implementation is a no-op, but subtypes should override this method to define custom behavior.
   */
  protected void startClient( Capabilities desiredCapabilities ) {
    startClient();
  }

  /**
   * Method called before {@link #startSession(Capabilities) starting a new session}. The default
   * implementation is a no-op, but subtypes should override this method to define custom behavior.
   *
   * @deprecated Use {@link #startClient(Capabilities)}
   */
  @Deprecated
  protected void startClient( Capabilities desiredCapabilities, Capabilities requiredCapabilities ) {
    startClient( desiredCapabilities.merge( requiredCapabilities ) );
  }

  /**
   * Method called after executing a {@link #quit()} command. The default implementation is a no-op,
   * but subtypes should override this method to define custom behavior.
   */
  protected void stopClient() {
  }

  /**
   * Method called after executing a {@link #quit()} command. The default implementation is a no-op,
   * but subtypes should override this method to define custom behavior.
   */
  protected void stopClient( Capabilities desiredCapbilities ) {
    stopClient();
  }

  /**
   * Method called after executing a {@link #quit()} command. The default implementation is a no-op,
   * but subtypes should override this method to define custom behavior.
   *
   * @deprecated Use {@link #stopClient(Capabilities)}
   */
  @Deprecated
  protected void stopClient( Capabilities desiredCapabilities, Capabilities requiredCapabilities ) {
    stopClient( desiredCapabilities.merge( requiredCapabilities ) );
  }

  public ErrorHandler getErrorHandler() {
    return errorHandler;
  }

  public void setErrorHandler( ErrorHandler handler ) {
    this.errorHandler = handler;
  }

  public CommandExecutor getCommandExecutor() {
    return executor;
  }

  protected void setCommandExecutor( HttpCommandExecutor2 executor ) {
    this.executor = executor;
  }

  public Capabilities getCapabilities() {
    return capabilities;
  }

  public void get( String url ) {
    execute( DriverCommand.GET, ImmutableMap.of( "url", url ) );
  }

  public String getTitle() {
    Response response = execute( DriverCommand.GET_TITLE );
    Object value = response.getValue();
    return value == null ? "" : value.toString();
  }

  public String getCurrentUrl() {
    Response response = execute( DriverCommand.GET_CURRENT_URL );
    if( response == null || response.getValue() == null ) {
      throw new WebDriverException( "Remote browser did not respond to getCurrentUrl" );
    }
    return response.getValue().toString();
  }

  public <X> X getScreenshotAs( OutputType<X> outputType ) throws WebDriverException {
    Response response = execute( DriverCommand.SCREENSHOT );
    Object result = response.getValue();
    if( result instanceof String ) {
      String base64EncodedPng = ( String ) result;
      return outputType.convertFromBase64Png( base64EncodedPng );
    } else if( result instanceof byte[] ) {
      String base64EncodedPng = new String( ( byte[] ) result );
      return outputType.convertFromBase64Png( base64EncodedPng );
    } else {
      throw new RuntimeException( String.format( "Unexpected result for %s command: %s",
          DriverCommand.SCREENSHOT,
          result == null ? "null" : result.getClass().getName() + " instance" ) );
    }
  }

  public List<WebElement> findElements( By by ) {
    return by.findElements( this );
  }

  public WebElement findElement( By by ) {
    return by.findElement( this );
  }

  protected WebElement findElement( String by, String using ) {
    if( using == null ) {
      throw new IllegalArgumentException( "Cannot find elements when the selector is null." );
    }

    Response response = execute( DriverCommand.FIND_ELEMENT,
        ImmutableMap.of( "using", by, "value", using ) );
    Object value = response.getValue();
    WebElement element;
    try {
      element = ( WebElement ) value;
    } catch( ClassCastException ex ) {
      throw new WebDriverException( "Returned value cannot be converted to WebElement: " + value, ex );
    }
    setFoundBy( this, element, by, using );
    return element;
  }

  @SuppressWarnings( "unchecked" )
  protected List<WebElement> findElements( String by, String using ) {
    if( using == null ) {
      throw new IllegalArgumentException( "Cannot find elements when the selector is null." );
    }

    Response response = execute( DriverCommand.FIND_ELEMENTS,
        ImmutableMap.of( "using", by, "value", using ) );
    Object value = response.getValue();
    List<WebElement> allElements;
    try {
      allElements = ( List<WebElement> ) value;
    } catch( ClassCastException ex ) {
      throw new WebDriverException( "Returned value cannot be converted to List<WebElement>: " + value, ex );
    }
    for( WebElement element : allElements ) {
      setFoundBy( this, element, by, using );
    }
    return allElements;
  }

  public WebElement findElementById( String using ) {
    return findElement( "id", using );
  }

  public List<WebElement> findElementsById( String using ) {
    return findElements( "id", using );
  }

  public WebElement findElementByLinkText( String using ) {
    return findElement( "link text", using );
  }

  public List<WebElement> findElementsByLinkText( String using ) {
    return findElements( "link text", using );
  }

  public WebElement findElementByPartialLinkText( String using ) {
    return findElement( "partial link text", using );
  }

  public List<WebElement> findElementsByPartialLinkText( String using ) {
    return findElements( "partial link text", using );
  }

  public WebElement findElementByTagName( String using ) {
    return findElement( "tag name", using );
  }

  public List<WebElement> findElementsByTagName( String using ) {
    return findElements( "tag name", using );
  }

  public WebElement findElementByName( String using ) {
    return findElement( "name", using );
  }

  public List<WebElement> findElementsByName( String using ) {
    return findElements( "name", using );
  }

  public WebElement findElementByClassName( String using ) {
    return findElement( "class name", using );
  }

  public List<WebElement> findElementsByClassName( String using ) {
    return findElements( "class name", using );
  }

  public WebElement findElementByCssSelector( String using ) {
    return findElement( "css selector", using );
  }

  public List<WebElement> findElementsByCssSelector( String using ) {
    return findElements( "css selector", using );
  }

  public WebElement findElementByXPath( String using ) {
    return findElement( "xpath", using );
  }

  public List<WebElement> findElementsByXPath( String using ) {
    return findElements( "xpath", using );
  }

  // Misc

  public String getPageSource() {
    return ( String ) execute( DriverCommand.GET_PAGE_SOURCE ).getValue();
  }

  public void close() {
    execute( DriverCommand.CLOSE );
  }

  public void quit() {
    // no-op if session id is null. We're only going to make ourselves unhappy
    if( sessionId == null ) {
      return;
    }

    try {
      execute( DriverCommand.QUIT );
    } finally {
      sessionId = null;
      stopClient();
    }
  }

  @SuppressWarnings( { "unchecked" } )
  public Set<String> getWindowHandles() {
    Response response = execute( DriverCommand.GET_WINDOW_HANDLES );
    Object value = response.getValue();
    try {
      List<String> returnedValues = ( List<String> ) value;
      return new LinkedHashSet<>( returnedValues );
    } catch( ClassCastException ex ) {
      throw new WebDriverException(
          "Returned value cannot be converted to List<String>: " + value, ex );
    }
  }

  public String getWindowHandle() {
    return String.valueOf( execute( DriverCommand.GET_CURRENT_WINDOW_HANDLE ).getValue() );
  }

  public Object executeScript( String script, Object... args ) {
    if( !capabilities.is( SUPPORTS_JAVASCRIPT ) ) {
      throw new UnsupportedOperationException(
          "You must be using an underlying instance of WebDriver that supports executing javascript" );
    }

    // Escape the quote marks
    script = script.replaceAll( "\"", "\\\"" );

    Iterable<Object> convertedArgs = Iterables.transform(
        Lists.newArrayList( args ), new WebElementToJsonConverter() );

    Map<String, ?> params = ImmutableMap.of(
        "script", script,
        "args", Lists.newArrayList( convertedArgs ) );

    return execute( DriverCommand.EXECUTE_SCRIPT, params ).getValue();
  }

  public Object executeAsyncScript( String script, Object... args ) {
    if( !isJavascriptEnabled() ) {
      throw new UnsupportedOperationException( "You must be using an underlying instance of " +
          "WebDriver that supports executing javascript" );
    }

    // Escape the quote marks
    script = script.replaceAll( "\"", "\\\"" );

    Iterable<Object> convertedArgs = Iterables.transform(
        Lists.newArrayList( args ), new WebElementToJsonConverter() );

    Map<String, ?> params = ImmutableMap.of(
        "script", script, "args", Lists.newArrayList( convertedArgs ) );

    return execute( DriverCommand.EXECUTE_ASYNC_SCRIPT, params ).getValue();
  }

  private boolean isJavascriptEnabled() {
    return capabilities.is( SUPPORTS_JAVASCRIPT );
  }

  public TargetLocator switchTo() {
    return new RemoteWebDriver2.RemoteTargetLocator();
  }

  public Navigation navigate() {
    return new RemoteWebDriver2.RemoteNavigation();
  }

  public Options manage() {
    return new RemoteWebDriver2.RemoteWebDriverOptions();
  }

  protected void setElementConverter( JsonToWebElementConverter converter ) {
    this.converter = converter;
  }

  protected JsonToWebElementConverter getElementConverter() {
    return converter;
  }

  /**
   * Sets the RemoteWebDriver's client log level.
   *
   * @param level The log level to use.
   */
  public void setLogLevel( Level level ) {
    this.level = level ;
  }

  protected Response execute( final String driverCommand, Map< String, ? > parameters ) {
    Command command = new Command( sessionId, driverCommand, parameters ) ;
    Response response ;

    long start = System.currentTimeMillis() ;
    String currentName = Thread.currentThread().getName() ;
    Thread.currentThread().setName(
        String.format( "Forwarding %s on session %s to remote", driverCommand, sessionId ) ) ;
    try {
      log( sessionId, command.getName(), command, RemoteWebDriver2.When.BEFORE ) ;
      response = executor.execute( command ) ;
      log( sessionId, command.getName(), command, RemoteWebDriver2.When.AFTER ) ;

      if( response == null ) {
        return null;
      }

      // Unwrap the response value by converting any JSON objects of the form
      // {"ELEMENT": id} to RemoteWebElements.
      Object value = converter.apply( response.getValue() ) ;
      response.setValue( value ) ;
    } catch( WebDriverException e ) {
      throw e ;
    } catch( Exception e ) {
      log( sessionId, command.getName(), command, RemoteWebDriver2.When.EXCEPTION ) ;
      String errorMessage = "Error communicating with the remote browser. " +
          "It may have died.";
      if( driverCommand.equals( DriverCommand.NEW_SESSION ) ) {
        errorMessage = "Could not start a new session. Possible causes are " +
            "invalid address of the remote server or browser start-up failure.";
      }
      UnreachableBrowserException ube = new UnreachableBrowserException( errorMessage, e );
      if( getSessionId() != null ) {
        ube.addInfo( WebDriverException.SESSION_ID, getSessionId().toString() );
      }
      if( getCapabilities() != null ) {
        ube.addInfo( "Capabilities", getCapabilities().toString() );
      }
      throw ube;
    } finally {
      Thread.currentThread().setName( currentName );
    }

    try {
      errorHandler.throwIfResponseFailed( response, System.currentTimeMillis() - start );
    } catch( WebDriverException ex ) {
      if( parameters != null && parameters.containsKey( "using" ) && parameters.containsKey( "value" ) ) {
        ex.addInfo(
            "*** Element info",
            String.format(
                "{Using=%s, value=%s}",
                parameters.get( "using" ),
                parameters.get( "value" ) ) );
      }
      ex.addInfo( WebDriverException.DRIVER_INFO, this.getClass().getName() );
      if( getSessionId() != null ) {
        ex.addInfo( WebDriverException.SESSION_ID, getSessionId().toString() );
      }
      if( getCapabilities() != null ) {
        ex.addInfo( "Capabilities", getCapabilities().toString() );
      }
      throw ex;
    }
    return response;
  }

  protected Response execute( String command ) {
    return execute( command, ImmutableMap.of() );
  }

  protected ExecuteMethod getExecuteMethod() {
    return executeMethod;
  }

  @Override
  public void perform( Collection<Sequence> actions ) {
    execute( DriverCommand.ACTIONS, ImmutableMap.of( "actions", actions ) );
  }

  @Override
  public void resetInputState() {
    execute( DriverCommand.CLEAR_ACTIONS_STATE );
  }

  public Keyboard getKeyboard() {
    return keyboard;
  }

  public Mouse getMouse() {
    return mouse;
  }

  /**
   * Override this to be notified at key points in the execution of a command.
   *
   * @param sessionId   the session id.
   * @param commandName the command that is being executed.
   * @param toLog       any data that might be interesting.
   * @param when        verb tense of "Execute" to prefix message
   */
  protected void log( SessionId sessionId, String commandName, Object toLog, RemoteWebDriver2.When when ) {
    String text = "" + toLog;
    if( commandName.equals( DriverCommand.EXECUTE_SCRIPT )
        || commandName.equals( DriverCommand.EXECUTE_ASYNC_SCRIPT ) ) {
      if( text.length() > 100 && Boolean.getBoolean( "webdriver.remote.shorten_log_messages" ) ) {
        text = text.substring( 0, 100 ) + "...";
      }
    }
    switch( when ) {
      case BEFORE:
        logger.log( level, "Executing: " + commandName + " " + text );
        break;
      case AFTER:
        logger.log( level, "Executed: " + text );
        break;
      case EXCEPTION:
        logger.log( level, "Exception: " + text );
        break;
      default:
        logger.log( level, text );
        break;
    }
  }

  public FileDetector getFileDetector() {
    return fileDetector;
  }

  protected class RemoteWebDriverOptions implements Options {

    @Beta
    public Logs logs() {
      return remoteLogs;
    }

    public void addCookie( Cookie cookie ) {
      cookie.validate();
      execute( DriverCommand.ADD_COOKIE, ImmutableMap.of( "cookie", cookie ) );
    }

    public void deleteCookieNamed( String name ) {
      execute( DriverCommand.DELETE_COOKIE, ImmutableMap.of( "name", name ) );
    }

    public void deleteCookie( Cookie cookie ) {
      deleteCookieNamed( cookie.getName() );
    }

    public void deleteAllCookies() {
      execute( DriverCommand.DELETE_ALL_COOKIES );
    }

    @SuppressWarnings( { "unchecked" } )
    public Set<Cookie> getCookies() {
      Object returned = execute( DriverCommand.GET_ALL_COOKIES ).getValue();

      Set<Cookie> toReturn = new HashSet<>();

      List<Map<String, Object>> cookies =
          new JsonToBeanConverter().convert( List.class, returned );
      if( cookies == null ) {
        return toReturn;
      }

      for( Map<String, Object> rawCookie : cookies ) {
        String name = ( String ) rawCookie.get( "name" );
        String value = ( String ) rawCookie.get( "value" );
        String path = ( String ) rawCookie.get( "path" );
        String domain = ( String ) rawCookie.get( "domain" );
        boolean secure = rawCookie.containsKey( "secure" ) && ( Boolean ) rawCookie.get( "secure" );
        boolean httpOnly = rawCookie.containsKey( "httpOnly" ) && ( Boolean ) rawCookie.get( "httpOnly" );

        Number expiryNum = ( Number ) rawCookie.get( "expiry" );
        Date expiry = expiryNum == null ? null : new Date(
            TimeUnit.SECONDS.toMillis( expiryNum.longValue() ) );

        toReturn.add( new Cookie.Builder( name, value )
            .path( path )
            .domain( domain )
            .isSecure( secure )
            .isHttpOnly( httpOnly )
            .expiresOn( expiry )
            .build() );
      }

      return toReturn;
    }

    public Cookie getCookieNamed( String name ) {
      Set<Cookie> allCookies = getCookies();
      for( Cookie cookie : allCookies ) {
        if( cookie.getName().equals( name ) ) {
          return cookie;
        }
      }
      return null;
    }

    public Timeouts timeouts() {
      return new RemoteWebDriver2.RemoteWebDriverOptions.RemoteTimeouts();
    }

    public ImeHandler ime() {
      return new RemoteWebDriver2.RemoteWebDriverOptions.RemoteInputMethodManager();
    }

    @Beta
    public Window window() {
      return new RemoteWebDriver2.RemoteWebDriverOptions.RemoteWindow();
    }

    protected class RemoteInputMethodManager implements WebDriver.ImeHandler {

      @SuppressWarnings( "unchecked" )
      public List<String> getAvailableEngines() {
        Response response = execute( DriverCommand.IME_GET_AVAILABLE_ENGINES );
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
        execute( DriverCommand.SET_TIMEOUT, ImmutableMap.of(
            "implicit", TimeUnit.MILLISECONDS.convert( time, unit ) ) );
        return this;
      }

      public Timeouts setScriptTimeout( long time, TimeUnit unit ) {
        execute( DriverCommand.SET_TIMEOUT, ImmutableMap.of(
            "script", TimeUnit.MILLISECONDS.convert( time, unit ) ) );
        return this;
      }

      public Timeouts pageLoadTimeout( long time, TimeUnit unit ) {
        execute( DriverCommand.SET_TIMEOUT, ImmutableMap.of(
            "page load", TimeUnit.MILLISECONDS.convert( time, unit ) ) );
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
        execute( DriverCommand.SET_CURRENT_WINDOW_POSITION,
            ImmutableMap.of( "x", targetPosition.x, "y", targetPosition.y ) );
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
        Response response = execute( DriverCommand.GET_CURRENT_WINDOW_POSITION,
            ImmutableMap.of( "windowHandle", "current" ) );
        rawPoint = ( Map<String, Object> ) response.getValue();

        int x = ( ( Number ) rawPoint.get( "x" ) ).intValue();
        int y = ( ( Number ) rawPoint.get( "y" ) ).intValue();

        return new Point( x, y );
      }

      public void maximize() {
        execute( DriverCommand.MAXIMIZE_CURRENT_WINDOW );
      }

      public void fullscreen() {
        execute( DriverCommand.FULLSCREEN_CURRENT_WINDOW );
      }
    }
  }

  private class RemoteNavigation implements Navigation {

    public void back() {
      execute( DriverCommand.GO_BACK );
    }

    public void forward() {
      execute( DriverCommand.GO_FORWARD );
    }

    public void to( String url ) {
      get( url );
    }

    public void to( URL url ) {
      get( String.valueOf( url ) );
    }

    public void refresh() {
      execute( DriverCommand.REFRESH );
    }
  }

  protected class RemoteTargetLocator implements TargetLocator {

    public WebDriver frame( int frameIndex ) {
      execute( DriverCommand.SWITCH_TO_FRAME, ImmutableMap.of( "id", frameIndex ) );
      return RemoteWebDriver2.this;
    }

    public WebDriver frame( String frameName ) {
      String name = frameName.replaceAll( "(['\"\\\\#.:;,!?+<>=~*^$|%&@`{}\\-/\\[\\]\\(\\)])", "\\\\$1" );
      List<WebElement> frameElements = RemoteWebDriver2.this.findElements(
          By.cssSelector( "frame[name='" + name + "'],iframe[name='" + name + "']" ) );
      if( frameElements.size() == 0 ) {
        frameElements = RemoteWebDriver2.this.findElements(
            By.cssSelector( "frame#" + name + ",iframe#" + name ) );
      }
      if( frameElements.size() == 0 ) {
        throw new NoSuchFrameException( "No frame element found by name or id " + frameName );
      }
      return frame( frameElements.get( 0 ) );
    }

    public WebDriver frame( WebElement frameElement ) {
      Object elementAsJson = new WebElementToJsonConverter().apply( frameElement );
      execute( DriverCommand.SWITCH_TO_FRAME, ImmutableMap.of( "id", elementAsJson ) );
      return RemoteWebDriver2.this;
    }

    public WebDriver parentFrame() {
      execute( DriverCommand.SWITCH_TO_PARENT_FRAME );
      return RemoteWebDriver2.this;
    }

    public WebDriver window( String windowHandleOrName ) {
      try {
        execute( DriverCommand.SWITCH_TO_WINDOW, ImmutableMap.of( "handle", windowHandleOrName ) );
        return RemoteWebDriver2.this ;
      } catch( NoSuchWindowException nsw ) {
        // simulate search by name
        String original = getWindowHandle();
        for( String handle : getWindowHandles() ) {
          switchTo().window( handle );
          if( windowHandleOrName.equals( executeScript( "return window.name" ) ) ) {
            return RemoteWebDriver2.this ; // found by name
          }
        }
        switchTo().window( original ) ;
        throw nsw ;
      }
    }

    public WebDriver defaultContent() {
      Map<String, Object> frameId = Maps.newHashMap() ;
      frameId.put( "id", null ) ;
      execute( DriverCommand.SWITCH_TO_FRAME, frameId ) ;
      return RemoteWebDriver2.this ;
    }

    public WebElement activeElement() {
      Response response = execute( DriverCommand.GET_ACTIVE_ELEMENT ) ;
      return ( WebElement ) response.getValue() ;
    }

    public Alert alert() {
      execute( DriverCommand.GET_ALERT_TEXT ) ;
      return new RemoteAlert() ;
    }
  }

  private class RemoteAlert implements Alert {

    public RemoteAlert() {
    }

    public void dismiss() {
      execute( DriverCommand.DISMISS_ALERT );
    }

    public void accept() {
      execute( DriverCommand.ACCEPT_ALERT );
    }

    public String getText() {
      return ( String ) execute( DriverCommand.GET_ALERT_TEXT ).getValue();
    }

    public void sendKeys( String keysToSend ) {
      execute( DriverCommand.SET_ALERT_VALUE, ImmutableMap.of( "text", keysToSend ) );
    }

    @Beta
    public void setCredentials( Credentials credentials ) {
      if( !( credentials instanceof UserAndPassword ) ) {
        throw new RuntimeException( "Unsupported credentials: " + credentials );
      }

      UserAndPassword userAndPassword = ( UserAndPassword ) credentials;
      execute(
          DriverCommand.SET_ALERT_CREDENTIALS,
          ImmutableMap.of(
              "username", userAndPassword.getUsername(),
              "password", userAndPassword.getPassword() ) );
    }

    /**
     * Authenticate an HTTP Basic Auth dialog.
     * Implicitly 'clicks ok'
     * <p>
     * Usage: driver.switchTo().alert().authenticateUsing(new UsernamePasswordCredentials("cheese",
     * "secretGouda"));
     *
     * @param credentials credentials to pass to Auth prompt
     */
    @Beta
    public void authenticateUsing( Credentials credentials ) {
      this.setCredentials( credentials ) ;
      this.accept() ;
    }
  }

  public enum When {
    BEFORE,
    AFTER,
    EXCEPTION
  }

  @Override
  public String toString() {
    Capabilities caps = getCapabilities() ;
    if( caps == null ) {
      return super.toString() ;
    }
    return String.format( "%s: %s on %s (%s)", getClass().getSimpleName(),
        caps.getBrowserName(), caps.getPlatform(), getSessionId() ) ;
  }

}
