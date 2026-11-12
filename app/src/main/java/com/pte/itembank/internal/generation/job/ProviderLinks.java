package com.pte.itembank.internal.generation.job;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Where a provider is shown on the AI overview: its host, and a link to its web page. Both are built from the
 * configured URL with any user info, query and fragment dropped, so a key placed in a URL never reaches the page.
 */
final class ProviderLinks {

    /** The speech service serves its web page under the API path. */
    static final String SPEECH_WEB_PATH = "/web/";

    private ProviderLinks() {
    }

    /** The host of a URL, or an empty string when it is not set or not a URL. */
    static String hostOf(String url) {
        URI uri = parse(url);
        return uri == null || uri.getHost() == null ? "" : uri.getHost();
    }

    /** {@code webUrl} when set, otherwise the root of the base URL's host. */
    static String gatewayPage(String webUrl, String baseUrl) {
        return isSet(webUrl) ? clean(webUrl, null) : clean(baseUrl, "/");
    }

    /** {@code webUrl} when set, otherwise the base URL followed by {@code /web/}. */
    static String speechPage(String webUrl, String baseUrl) {
        if (isSet(webUrl)) {
            return clean(webUrl, null);
        }
        URI uri = parse(baseUrl);
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
        return clean(baseUrl, path + SPEECH_WEB_PATH);
    }

    /** Scheme, host, port and path only; {@code path} replaces the URL's own path when given. Empty if unusable. */
    private static String clean(String url, String path) {
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return "";
        }
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    path == null ? uri.getPath() : path, null, null).toString();
        } catch (URISyntaxException invalid) {
            return "";
        }
    }

    private static URI parse(String url) {
        if (!isSet(url)) {
            return null;
        }
        try {
            return URI.create(url.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
