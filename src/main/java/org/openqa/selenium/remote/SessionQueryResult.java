package org.openqa.selenium.remote;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.remote.SessionId;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SessionQueryResult {

  public final long status ;

  public final ImmutableList< SessionDescriptor > sessionDescriptors ;

  public SessionQueryResult(
      final long status,
      final ImmutableList< SessionDescriptor > sessionDescriptors
  ) {
    this.status = status ;
    this.sessionDescriptors = checkNotNull( sessionDescriptors ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{status=" + status + ";"  + sessionDescriptors + "}" ;
  }

  public static class SessionDescriptor {
    public final SessionId id ;
    public final ImmutableCapabilities capabilities ;

    public SessionDescriptor(
        final SessionId id,
        final ImmutableCapabilities capabilities
    ) {
      this.id = checkNotNull( id ) ;
      this.capabilities = checkNotNull( capabilities ) ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{id=" + id + ";capabilities=[" +
          Joiner.on( ';' ).withKeyValueSeparator( "=" ).join( capabilities.asMap() ) +
          "]}"
      ;
    }

    /**
     * Returns a JSON-friendly {@code Map} with the format expected by
     * {@link org.openqa.selenium.remote.ProtocolHandshake}.
     * This is the same format as a {@code session} when requesting
     * {@code http://localhost:4444/wd/hub/sessions}, only the {@code sessionId} key has
     * a different name.
     */
    public Map< String, ? > asHandshakeMap() {
      throw new UnsupportedOperationException( "TODO" ) ;
    }
  }
}
