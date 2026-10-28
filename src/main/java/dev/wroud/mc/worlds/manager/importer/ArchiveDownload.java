package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.util.Util;

public final class ArchiveDownload {
  private static final int MAX_REDIRECTS = 5;
  private static final int CONNECT_TIMEOUT_MS = 15_000;
  private static final int READ_TIMEOUT_MS = 30_000;
  private static final long MEGABYTE = 1024 * 1024;

  private ArchiveDownload() {
  }

  public static boolean isUrl(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  public static String label(String text) {
    if (!isUrl(text)) {
      return text;
    }
    int query = text.length();
    for (char end : new char[] { '?', '#' }) {
      int index = text.indexOf(end);
      if (index >= 0 && index < query) {
        query = index;
      }
    }
    String base = text.substring(0, query);
    int authority = base.indexOf("://") + 3;
    int slash = base.indexOf('/', authority);
    int at = base.lastIndexOf('@', slash < 0 ? base.length() : slash);
    return at < authority ? base : base.substring(0, authority) + base.substring(at + 1);
  }

  static void download(ImportJob job, String text, Path target) throws ImportException {
    URI uri = parse(text);
    if (uri == null) {
      throw new ImportException("exception.invalid_url", job.folder());
    }
    job.phase().accept("downloading");
    try {
      for (int redirects = 0;; redirects++) {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(job.server().getProxy());
        try {
          connection.setInstanceFollowRedirects(false);
          connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
          connection.setReadTimeout(READ_TIMEOUT_MS);
          connection.setRequestProperty("User-Agent", McWorldMod.MOD_ID);
          connection.setRequestProperty("Accept-Encoding", "identity");
          int status = connection.getResponseCode();
          String location = connection.getHeaderField("Location");
          if (status / 100 == 3 && location != null) {
            if (redirects >= MAX_REDIRECTS) {
              throw new IOException("too many redirects");
            }
            uri = redirect(uri, location);
            if (uri == null) {
              throw new ImportException("exception.invalid_url", job.folder());
            }
            continue;
          }
          if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + status + " " + Objects.toString(connection.getResponseMessage(), ""));
          }
          String encoding = connection.getContentEncoding();
          if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
            throw new IOException("unsupported Content-Encoding " + encoding);
          }
          String type = connection.getContentType();
          if (type != null && type.toLowerCase(Locale.ROOT).startsWith("text/html")) {
            throw new ImportException("exception.share_page", job.folder());
          }
          copy(job, connection, target);
          return;
        } finally {
          connection.disconnect();
        }
      }
    } catch (IOException e) {
      throw job.failure(e);
    }
  }

  private static void copy(ImportJob job, HttpURLConnection connection, Path target)
      throws IOException, ImportException {
    long length = connection.getContentLengthLong();
    long budget = job.diskBudget();
    if (length > budget) {
      throw new ImportException("exception.disk_space", job.folder());
    }
    String total = length < 0 ? "?" : Long.toString(length / MEGABYTE);
    long copied;
    try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
      copied = job.copy(in, out, budget, () -> new ImportException("exception.disk_space", job.folder()),
          done -> job.phase().accept("downloading " + done / MEGABYTE + "/" + total + " MB"));
    }
    if (length >= 0 && copied != length) {
      throw new IOException("received " + copied + " of " + length + " bytes");
    }
  }

  private static @Nullable URI redirect(URI from, String location) {
    try {
      return parse(from.resolve(location).toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static @Nullable URI parse(String text) {
    try {
      URI uri = Util.parseAndValidateUntrustedUri(text);
      return uri.getRawAuthority() == null ? null : uri;
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
