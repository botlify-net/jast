package io.botlify.jast.objects;

import com.sun.net.httpserver.HttpExchange;
import io.botlify.jast.config.RouteConfig;
import io.botlify.jast.enums.HttpMethod;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Request {

    @NotNull
    private final HttpExchange exchange;

    @NotNull
    private final RouteConfig routeConfig;

    @Getter
    private final byte[] rawBody;

    @NotNull
    private final Map<String, Object> attributes;

    public Request(@NotNull final RouteConfig routeConfig,
                   @NotNull final HttpExchange exchange) throws IOException {
        this.attributes = new HashMap<>();
        this.routeConfig = routeConfig;
        this.exchange = exchange;
        if (getMethod().hasRequestBody()) {
            this.rawBody = exchange.getRequestBody().readAllBytes();
            return;
        }
        this.rawBody = null;
    }

    /**
     * Get the method of the request.
     * @return The method of the request.
     */
    public @NotNull HttpMethod getMethod() {
        return (HttpMethod.valueOf(exchange.getRequestMethod()));
    }

    public @NotNull String getPath() {
        return (exchange.getRequestURI().getPath());
    }

    public @NotNull String getHost() {
        return (exchange.getRequestURI().getHost());
    }

    /**
     * Get the body as a string of the request.
     * @return The body as a string of the request.
     * @throws IllegalStateException If the request method does not have a body.
     */
    public @NotNull String getBody() {
        return (new String(getRawBody()));
    }

    public @NotNull JSONObject getBodyAsJson() {
        return (new JSONObject(getBody()));
    }

    public @Nullable ContentType getContentType() {
        final Header header = getFirstHeader("Content-Type");
        if (header == null)
            return (null);
        return (new ContentType(header.getValue()));
    }

    /**
     * Get the list of all the headers of the request.
     * @return The list of all the headers of the request.
     */
    public @NotNull List<Header> getHeaders() {
        final List<Header> headers = new ArrayList<>();
        for (final String key : exchange.getRequestHeaders().keySet()) {
            final List<String> values = exchange.getRequestHeaders().get(key);
            for (final String value : values) {
                headers.add(new Header(key, value));
            }
        }
        return (headers);
    }

    /**
     * Get all the headers with the specified name.
     * @param name The name of the headers to get.
     * @return A list of all the headers with the specified name.
     */
    public @NotNull List<Header> getHeaders(@NotNull final String name) {
        final List<Header> headers = getHeaders();
        return (headers.stream()
                .filter(header -> header.getName().equals(name))
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    /**
     * Get the first header with the specified name.
     * @param name The name of the header to get.
     * @return The first header with the specified name.
     */
    public @Nullable Header getFirstHeader(@NotNull final String name) {
        return (getHeaders(name).stream().findFirst().orElse(null));
    }

    /**
     * Get the IP address of the client that sent the request.
     * @return The IP address of the client that sent the request.
     */
    public @Nullable String getIp() {
        if (exchange.getRemoteAddress() == null)
            return (null);
        if (exchange.getRemoteAddress().getAddress() == null)
            return (null);
        return (exchange.getRemoteAddress().getAddress().getHostAddress());
    }

    // Query parameters

    public @NotNull List<QueryParam> getQueryParams() {
        final List<QueryParam> result = new ArrayList<>();
        final String query = exchange.getRequestURI().getQuery();
        if (query == null)
            return (result);
        final String[] params = query.split("&");
        for (final String param : params) {
            final String[] keyValue = param.split("=");
            if (keyValue.length != 2) continue;
            final String key = keyValue[0];
            final String value = keyValue[1];
            final QueryParam queryParam = new QueryParam(key, value);
            result.add(queryParam);
        }
        return (result);
    }

    public @NotNull List<QueryParam> getQueryParams(@NotNull final String key) {
        final List<QueryParam> result = new ArrayList<>();
        for (final QueryParam queryParam : getQueryParams()) {
            if (queryParam.getKey().equals(key))
                result.add(queryParam);
        }
        return (result);
    }

    public @Nullable QueryParam getFirstQueryParam(@NotNull final String key) {
        final List<QueryParam> result = getQueryParams(key);
        if (result.isEmpty())
            return (null);
        return (result.get(0));
    }

    public @Nullable String getRequestParam(@NotNull final String param) {
        final Map<String, Integer> requestParams = routeConfig.getRequestParam();
        if (!requestParams.containsKey(param))
            return (null);
        final int index = requestParams.get(param);
        final String[] pathSplit = exchange.getRequestURI().getPath().split("/");
        if (pathSplit.length <= index)
            return (null);
        return (pathSplit[index]);
    }

    // Cookies

    /**
     * This method will parse all the Cookie header and
     * return a list of all the cookies.
     * @return A list of all the cookies.
     */
    public @NotNull List<Cookie> getCookies() {
        final List<Header> cookiesHeader = getHeaders("Cookie");
        final List<Cookie> result = new ArrayList<>();
        for (Header header : cookiesHeader) {
            List<Cookie> cookies = Cookie.parseCookieHeader(header.getValue());
            result.addAll(cookies);
        }
        return (result);
    }

    // Attributes

    /**
     * Set an attribute to the request.
     * @param name The name of the attribute.
     * @param value The value of the attribute.
     * @throws NullPointerException If the name or the value given in parameter is null.
     */
    public void setAttribute(@NotNull final String name,
                             @NotNull final Object value) {
        this.attributes.put(name, value);
    }

    /**
     * Check if the request has an attribute with the specified name.
     * @param name The name of the attribute.
     * @return True if the request has an attribute with the specified name, false otherwise.
     * @throws NullPointerException If the name given in parameter is null.
     */
    public boolean hasAttribute(@NotNull final String name) {
        return (this.attributes.containsKey(name));
    }

    /**
     * Get the attribute with the specified name.
     * @param name The name of the attribute.
     * @return The attribute with the specified name.
     * @throws NullPointerException If the name given in parameter is null.
     */
    public @Nullable Object getAttribute(@NotNull final String name) {
        if (!hasAttribute(name))
            return (null);
        return (this.attributes.get(name));
    }

    /**
     * Remove the attribute with the specified name.
     * @param name The name of the attribute.
     * @return True if the attribute has been removed, false otherwise.
     * @throws NullPointerException If the name given in parameter is null.
     */
    public boolean removeAttribute(@NotNull final String name) {
        if (!hasAttribute(name))
            return (false);
        this.attributes.remove(name);
        return (true);
    }

}
