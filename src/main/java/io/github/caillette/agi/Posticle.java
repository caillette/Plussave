package io.github.caillette.agi;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.net.URL;

/**
 * A {@link Posticle} is a small piece of post, as an article is a small piece of art.
 */
public class Posticle {

  final Note primary = null ;
  final ImmutableList< Note > comments = null ;


  public class Note {
    final String html = null ;
    final Hyperlink hyperlink = null ;
  }

  public class Hyperlink {
    final String title = null ;

    /**
     * May appear under the title with smaller characters.
     */
    final String summary = null ;

    /**
     * Appears as an overlay if there is an image link.
     */
    final String webOrigin = null ;
    final URL targetUrl = null ;

    /**
     * Links to Google's preprocessed image, {@code null} after download completes.
     */
    final URL rawImageUrl = null ;

    /**
     * The downloaded image, {@code null} until download completes.
     */
    final File downloadedImageFile = null ;

  }
}
