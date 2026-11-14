package app.streamloom.player;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StalkerPortalClient {
    private static final String USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG250 stbapp ver: 4 rev: 2721 Safari/533.3";
    private static final String X_USER_AGENT = "Model: MAG254; Link: Ethernet";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);

    private StalkerPortalClient() {}

    static JSONObject connect(String portalInput, String macInput) throws Exception {
        String mac = normalizeMac(macInput);
        String portal = normalizePortal(portalInput);
        String cookie = baseCookie(mac);
        String endpoint = null;
        String token = null;
        Exception lastError = null;

        for (String candidate : endpointCandidates(portal)) {
            try {
                JSONObject response = request(candidate, params("type", "stb", "action", "handshake", "token", "", "JsHttpRequest", "1-xml"), mac, "", cookie);
                token = findString(response, "token");
                if (token == null || token.trim().isEmpty()) throw new Exception("The portal replied, but its handshake did not include a token.");
                endpoint = candidate;
                cookie = mergeCookie(cookie, response.optString("_setCookie", ""));
                break;
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        if (endpoint == null) {
            throw new Exception(lastError == null ? "Could not find the portal API endpoint." : lastError.getMessage());
        }

        JSONObject profile = request(endpoint, params("type", "stb", "action", "get_profile", "JsHttpRequest", "1-xml"), mac, token, cookie);
        cookie = mergeCookie(cookie, profile.optString("_setCookie", ""));
        JSONObject categoryResponse = request(endpoint, params("type", "itv", "action", "get_genres", "JsHttpRequest", "1-xml"), mac, token, cookie);
        cookie = mergeCookie(cookie, categoryResponse.optString("_setCookie", ""));
        JSONObject channelResponse = request(endpoint, channelParams(0, ""), mac, token, cookie);
        cookie = mergeCookie(cookie, channelResponse.optString("_setCookie", ""));

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("portal", portal);
        out.put("endpoint", endpoint);
        out.put("mac", mac);
        out.put("token", token);
        out.put("cookie", cookie);
        out.put("profile", unwrap(profile));
        out.put("categories", extractData(categoryResponse));
        out.put("channels", extractData(channelResponse));
        out.put("total", extractTotal(channelResponse));
        return out;
    }

    static JSONObject loadChannels(JSONObject session, int page, String categoryId) throws Exception {
        String mac = normalizeMac(session.optString("mac", ""));
        String endpoint = session.getString("endpoint");
        String token = session.getString("token");
        String cookie = session.optString("cookie", baseCookie(mac));
        JSONObject response = request(endpoint, channelParams(page, categoryId), mac, token, cookie);
        JSONObject result = new JSONObject();
        result.put("channels", extractData(response));
        result.put("total", extractTotal(response));
        return result;
    }

    static String resolveChannel(JSONObject session, String command) throws Exception {
        String mac = normalizeMac(session.optString("mac", ""));
        String endpoint = session.getString("endpoint");
        String token = session.getString("token");
        String cookie = session.optString("cookie", baseCookie(mac));
        JSONObject response = request(endpoint, params("type", "itv", "action", "create_link", "cmd", command,
                "forced_storage", "", "disable_ad", "0", "download", "0", "JsHttpRequest", "1-xml"), mac, token, cookie);
        String resolved = findString(response, "cmd");
        if (resolved == null) resolved = findString(response, "url");
        if (resolved == null) resolved = findString(response, "link");
        if (resolved == null || resolved.trim().isEmpty()) throw new Exception("The portal did not return a playable link for this channel.");
        Matcher matcher = URL_PATTERN.matcher(resolved);
        if (matcher.find()) resolved = matcher.group();
        resolved = resolved.replaceAll("[\\\"'\\s]+$", "");
        if (!resolved.startsWith("http://") && !resolved.startsWith("https://")) {
            throw new Exception("The portal returned an unsupported stream address.");
        }
        return resolved;
    }

    private static JSONObject request(String endpoint, LinkedHashMap<String, String> params, String mac, String token, String cookie) throws Exception {
        StringBuilder url = new StringBuilder(endpoint).append(endpoint.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) url.append('&');
            first = false;
            url.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url.toString()).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(18000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("X-User-Agent", X_USER_AGENT);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Cookie", cookie == null || cookie.isEmpty() ? baseCookie(mac) : cookie);
            if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);

            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readAll(input);
            if (status < 200 || status >= 300) throw new Exception("Portal returned HTTP " + status + (body.isEmpty() ? "." : ": " + shortText(body)));
            JSONObject json = parseJson(body);
            String error = findString(json, "error");
            if (error != null && !error.trim().isEmpty() && !"0".equals(error)) throw new Exception("Portal rejected the request: " + shortText(error));
            json.put("_setCookie", readCookies(connection));
            return json;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static LinkedHashMap<String, String> channelParams(int page, String categoryId) {
        LinkedHashMap<String, String> p = params("type", "itv", "action", "get_ordered_list", "sortby", "number",
                "hd", "0", "not_active", "0", "fav", "0", "p", String.valueOf(Math.max(0, page)), "JsHttpRequest", "1-xml");
        if (categoryId != null && !categoryId.isEmpty() && !"all".equals(categoryId)) p.put("genre", categoryId);
        return p;
    }

    private static JSONArray extractData(JSONObject response) throws Exception {
        Object js = response.opt("js");
        if (js instanceof JSONObject) {
            JSONObject obj = (JSONObject) js;
            Object data = obj.opt("data");
            if (data instanceof JSONArray) return (JSONArray) data;
            if (data instanceof JSONObject) return new JSONArray().put(data);
            if (jsError(obj) != null) throw new Exception("Portal error: " + shortText(jsError(obj)));
        }
        if (js instanceof JSONArray) return (JSONArray) js;
        Object data = response.opt("data");
        if (data instanceof JSONArray) return (JSONArray) data;
        return new JSONArray();
    }

    private static Object unwrap(JSONObject response) {
        Object js = response.opt("js");
        if (js instanceof JSONObject) return js;
        return response;
    }

    private static int extractTotal(JSONObject response) {
        Object js = response.opt("js");
        if (js instanceof JSONObject) {
            JSONObject obj = (JSONObject) js;
            return obj.optInt("total_items", obj.optInt("total", 0));
        }
        return response.optInt("total_items", response.optInt("total", 0));
    }

    private static String jsError(JSONObject obj) {
        String error = obj.optString("error", "");
        if (error.isEmpty()) error = obj.optString("message", "");
        return error.isEmpty() ? null : error;
    }

    private static String findString(JSONObject response, String key) {
        Object js = response.opt("js");
        if (js instanceof JSONObject) {
            String value = ((JSONObject) js).optString(key, "");
            if (!value.isEmpty() && !"null".equals(value)) return value;
        }
        String value = response.optString(key, "");
        if (!value.isEmpty() && !"null".equals(value)) return value;
        if (js instanceof String && "cmd".equals(key)) return (String) js;
        return null;
    }

    private static String normalizeMac(String value) throws Exception {
        String mac = value == null ? "" : value.trim().toUpperCase();
        if (!mac.matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")) throw new Exception("Enter the provider-issued MAC address in the format 00:1A:79:12:34:56.");
        return mac;
    }

    private static String normalizePortal(String input) throws Exception {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new Exception("Enter the portal address supplied by your provider.");
        if (!value.matches("(?i)^https?://.*")) value = "http://" + value;
        URI uri;
        try { uri = URI.create(value); } catch (Exception ex) { throw new Exception("That portal address is not valid."); }
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new Exception("Use a portal address starting with http:// or https://.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+?$", "");
        return uri.getScheme() + "://" + uri.getRawAuthority() + path;
    }

    private static List<String> endpointCandidates(String portal) {
        List<String> values = new ArrayList<>();
        if (portal.endsWith("/server/load.php") || portal.endsWith("/portal.php")) {
            values.add(portal);
            return values;
        }
        String base = portal;
        String withoutC = base.replaceFirst("(?i)/c$", "");
        add(values, withoutC + "/server/load.php");
        if (!base.equals(withoutC)) add(values, base + "/server/load.php");
        add(values, withoutC + "/portal.php");
        return values;
    }

    private static void add(List<String> values, String value) { if (!values.contains(value)) values.add(value); }
    private static String baseCookie(String mac) { return "mac=" + mac + "; stb_lang=en; timezone=Europe/Paris"; }

    private static String mergeCookie(String current, String setCookie) {
        if (setCookie == null || setCookie.isEmpty()) return current;
        Map<String, String> cookies = new LinkedHashMap<>();
        addCookieParts(cookies, current);
        addCookieParts(cookies, setCookie);
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(e.getKey()).append('=').append(e.getValue());
        }
        return out.toString();
    }

    private static void addCookieParts(Map<String, String> map, String raw) {
        if (raw == null) return;
        for (String part : raw.split(";")) {
            String p = part.trim();
            int ix = p.indexOf('=');
            if (ix <= 0) continue;
            String name = p.substring(0, ix).trim();
            String value = p.substring(ix + 1).trim();
            if (!name.equalsIgnoreCase("path") && !name.equalsIgnoreCase("expires") && !name.equalsIgnoreCase("max-age") && !name.equalsIgnoreCase("samesite")) map.put(name, value);
        }
    }

    private static String readCookies(HttpURLConnection connection) {
        List<String> values = connection.getHeaderFields().get("Set-Cookie");
        if (values == null) return "";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            int semicolon = value.indexOf(';');
            String pair = semicolon >= 0 ? value.substring(0, semicolon) : value;
            if (out.length() > 0) out.append(';');
            out.append(pair);
        }
        return out.toString();
    }

    private static JSONObject parseJson(String body) throws Exception {
        String value = body == null ? "" : body.trim();
        if (value.isEmpty()) throw new Exception("The portal returned an empty response.");
        int start = value.indexOf('{');
        if (start > 0) value = value.substring(start);
        try { return new JSONObject(value); }
        catch (JSONException ex) { throw new Exception("The portal did not return a Stalker/MAG response. Check the portal address."); }
    }

    private static LinkedHashMap<String, String> params(String... kv) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) out.put(kv[i], kv[i + 1]);
        return out;
    }

    private static String encode(String value) throws Exception { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
    private static String shortText(String value) { String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim(); return clean.length() > 160 ? clean.substring(0, 160) + "…" : clean; }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
