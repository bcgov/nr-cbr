package ca.bc.gov.nrs.cbr.util;

import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

/**
 * A document's media type, from its file name and then its extension code.
 *
 * <p>Shared by the download, the upload and the BLOB copy, because the object store signs the
 * Content-Type: it has to be a real media type. Legacy's {@code FILE_MIME_TYPE_CODE} is a
 * ten-character code, and its descriptions ("JPG Graphic File") got nr-frep 403s from the gateway.
 */
public final class DocumentMediaTypes {

  private DocumentMediaTypes() {
  }

  /**
   * The type for the name, else for the extension code, else {@code application/octet-stream};
   * without parameters such as a charset.
   */
  public static MediaType of(String filename, String extensionCode) {
    return Optional.ofNullable(filename)
        .flatMap(MediaTypeFactory::getMediaType)
        .or(() -> extensionCode == null
            ? Optional.empty()
            : MediaTypeFactory.getMediaType("file." + extensionCode))
        .map(type -> new MediaType(type.getType(), type.getSubtype()))
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
  }
}
