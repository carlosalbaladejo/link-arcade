package app.linkarcade;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

final class Imports {
    private static final int PAGE_LIMIT = 2 * 1024 * 1024;
    private static final int MANIFEST_LIMIT = 64 * 1024;

    private static final class Response {
        final URI url;
        final byte[] body;

        Response(URI url, byte[] body) {
            this.url = url;
            this.body = body;
        }
    }

    static Game inspect(URI uri) throws Exception {
        return inspect(uri, null);
    }

    static Game inspect(URI uri, String requiredOrigin) throws Exception {
        Response page = fetch(uri, requiredOrigin, PAGE_LIMIT);
        Document html = Jsoup.parse(new ByteArrayInputStream(page.body), null, page.url.toString());
        Element link = null;
        for (Element element : html.select("link[rel]")) {
            for (String token : element.attr("rel").split("\\s+")) {
                if (!token.equalsIgnoreCase("tv-game-manifest")) continue;
                if (link != null) throw new IOException("La página declara más de un manifiesto.");
                link = element;
            }
        }
        if (link == null) {
            String title = html.title();
            if (title.isEmpty()) title = page.url.getHost();
            if (title.codePointCount(0, title.length()) > 80) title = title.substring(0, title.offsetByCodePoints(0, 80));
            return new Game(page.url.toString(), title, "", Collections.emptyList());
        }
        URI manifestUri = resolve(page.url, link.attr("href"));
        Response response = fetch(manifestUri, origin(page.url), MANIFEST_LIMIT);
        JSONObject manifest = manifest(response.body);
        String cover = "";
        if (manifest.has("cover")) {
            Object field = manifest.opt("cover");
            if (!(field instanceof String)) throw new IOException("La portada debe ser una URL.");
            URI coverUri = resolve(response.url, (String) field);
            if (!origin(coverUri).equals(origin(page.url))) throw new IOException("La portada debe estar en el mismo sitio.");
            cover = coverUri.toString();
        }
        return Game.fromManifest(page.url.toString(), cover, manifest);
    }

    static Bitmap cover(Game game) throws Exception {
        byte[] data = fetch(webUri(game.cover), origin(webUri(game.url)), PAGE_LIMIT).body;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth < 1 || options.outHeight < 1) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    static JSONObject manifest(byte[] body) throws Exception {
        try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8))) {
            Object data = value(reader, 0);
            if (!(data instanceof JSONObject) || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("El manifiesto debe ser un objeto JSON.");
            }
            return (JSONObject) data;
        }
    }

    private static Object value(JsonReader reader, int depth) throws Exception {
        if (depth > 12) throw new IOException("Manifiesto demasiado profundo.");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JSONObject object = new JSONObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    if (object.has(field)) throw new IOException("Campo duplicado: " + field);
                    object.put(field, value(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JSONArray list = new JSONArray();
                reader.beginArray();
                while (reader.hasNext()) list.put(value(reader, depth + 1));
                reader.endArray();
                return list;
            case STRING: return reader.nextString();
            case NUMBER: return reader.nextDouble();
            case BOOLEAN: return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return JSONObject.NULL;
            default: throw new IOException("JSON inválido.");
        }
    }

    static URI webUri(String input) throws IOException {
        try {
            URI uri = new URI(input).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) throw new IllegalArgumentException();
            return new URI(origin(uri) + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment()));
        } catch (Exception error) {
            throw new IOException("Introduce una URL HTTP o HTTPS válida.");
        }
    }

    static String origin(URI url) {
        String scheme = url.getScheme().toLowerCase(Locale.ROOT);
        int defaultPort = scheme.equals("https") ? 443 : 80;
        int port = url.getPort();
        return scheme + "://" + url.getHost().toLowerCase(Locale.ROOT)
                + (port == -1 || port == defaultPort ? "" : ":" + port);
    }

    private static URI resolve(URI base, String href) throws IOException {
        if (href.isEmpty()) throw new IOException("Falta la URL del archivo.");
        try {
            return webUri(base.resolve(new URI(href)).toString());
        } catch (Exception error) {
            throw new IOException("Un enlace no contiene una URL HTTP o HTTPS válida.");
        }
    }

    private static Response fetch(URI url, String requiredOrigin, int limit) throws IOException {
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (Game.ASSET_HOST.equals(url.getHost()) || (requiredOrigin != null && !requiredOrigin.equals(origin(url)))) {
                throw new IOException("El archivo debe estar en el mismo sitio que el juego.");
            }
            HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
            try {
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("User-Agent", "LinkArcade/1.0 (Android)");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String target = connection.getHeaderField("Location");
                    if (target == null) throw new IOException("Redirección sin destino.");
                    URI previous = url;
                    url = resolve(url, target);
                    if ("https".equals(previous.getScheme()) && "http".equals(url.getScheme())) {
                        throw new IOException("El sitio intentó redirigir de HTTPS a HTTP.");
                    }
                    if (url.getRawFragment() == null && previous.getRawFragment() != null) {
                        url = webUri(url + "#" + previous.getRawFragment());
                    }
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("El sitio devolvió HTTP " + status + ".");
                if (connection.getContentLengthLong() > limit) throw new IOException("El archivo es demasiado grande.");
                return new Response(url, read(connection.getInputStream(), limit));
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Demasiadas redirecciones.");
    }

    static byte[] read(InputStream source, int limit) throws IOException {
        try (InputStream stream = source; ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (result.size() + count > limit) throw new IOException("El archivo es demasiado grande.");
                result.write(buffer, 0, count);
            }
            return result.toByteArray();
        }
    }
}
