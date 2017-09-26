package io.github.caillette.agi;

import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

final class Blacklist< ITEM > {

  private final Set< ITEM > set = new HashSet<>() ;

  public boolean addIfMissing( final ITEM item ) {
    return set.add( checkNotNull( item ) ) ;
  }

}
