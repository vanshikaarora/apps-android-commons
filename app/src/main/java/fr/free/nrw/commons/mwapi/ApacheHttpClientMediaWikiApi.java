package fr.free.nrw.commons.mwapi;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.wikipedia.util.DateUtil;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import fr.free.nrw.commons.BuildConfig;
import fr.free.nrw.commons.CommonsApplication;
import fr.free.nrw.commons.kvstore.JsonKvStore;
import io.reactivex.Observable;
import io.reactivex.Single;
import timber.log.Timber;

/**
 * @author Addshore
 */
public class ApacheHttpClientMediaWikiApi implements MediaWikiApi {
    private AbstractHttpClient httpClient;
    private CustomMwApi api;
    private JsonKvStore defaultKvStore;

    public ApacheHttpClientMediaWikiApi(String apiURL) {
        BasicHttpParams params = new BasicHttpParams();
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
        schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
        ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        params.setParameter(CoreProtocolPNames.USER_AGENT, CommonsApplication.getInstance().getUserAgent());
        httpClient = new DefaultHttpClient(cm, params);
        if (BuildConfig.DEBUG) {
            httpClient.addRequestInterceptor(NetworkInterceptors.getHttpRequestInterceptor());
        }
        api = new CustomMwApi(apiURL, httpClient);
        this.defaultKvStore = defaultKvStore;
    }

    /**
     * @param username String
     * @param password String
     * @return String as returned by this.getErrorCodeToReturn()
     * @throws IOException On api request IO issue
     */
    public String login(String username, String password) throws IOException {
        String loginToken = getLoginToken();
        Timber.d("Login token is %s", loginToken);
        return getErrorCodeToReturn(api.action("clientlogin")
                .param("rememberMe", "1")
                .param("username", username)
                .param("password", password)
                .param("logintoken", loginToken)
                .param("loginreturnurl", "https://commons.wikimedia.org")
                .post());
    }

    /**
     * @param username      String
     * @param password      String
     * @param twoFactorCode String
     * @return String as returned by this.getErrorCodeToReturn()
     * @throws IOException On api request IO issue
     */
    public String login(String username, String password, String twoFactorCode) throws IOException {
        String loginToken = getLoginToken();
        Timber.d("Login token is %s", loginToken);
        return getErrorCodeToReturn(api.action("clientlogin")
                .param("rememberMe", "true")
                .param("username", username)
                .param("password", password)
                .param("logintoken", loginToken)
                .param("logincontinue", "true")
                .param("OATHToken", twoFactorCode)
                .post());
    }

    private String getLoginToken() throws IOException {
        return api.action("query")
                .param("action", "query")
                .param("meta", "tokens")
                .param("type", "login")
                .post()
                .getString("/api/query/tokens/@logintoken");
    }

    /**
     * @param loginCustomApiResult CustomApiResult Any clientlogin api result
     * @return String On success: "PASS"
     * continue: "2FA" (More information required for 2FA)
     * failure: A failure message code (defined by mediawiki)
     * misc:    genericerror-UI, genericerror-REDIRECT, genericerror-RESTART
     */
    private String getErrorCodeToReturn(CustomApiResult loginCustomApiResult) {
        String status = loginCustomApiResult.getString("/api/clientlogin/@status");
        if (status.equals("PASS")) {
            api.isLoggedIn = true;
            setAuthCookieOnLogin(true);
            return status;
        } else if (status.equals("FAIL")) {
            setAuthCookieOnLogin(false);
            return loginCustomApiResult.getString("/api/clientlogin/@messagecode");
        } else if (
                status.equals("UI")
                        && loginCustomApiResult.getString("/api/clientlogin/requests/_v/@id").equals("TOTPAuthenticationRequest")
                        && loginCustomApiResult.getString("/api/clientlogin/requests/_v/@provider").equals("Two-factor authentication (OATH).")
        ) {
            setAuthCookieOnLogin(false);
            return "2FA";
        }

        // UI, REDIRECT, RESTART
        return "genericerror-" + status;
    }

    private void setAuthCookieOnLogin(boolean isLoggedIn) {
        if (isLoggedIn) {
            defaultKvStore.putBoolean("isUserLoggedIn", true);
            defaultKvStore.putString("getAuthCookie", api.getAuthCookie());
        } else {
            defaultKvStore.putBoolean("isUserLoggedIn", false);
            defaultKvStore.remove("getAuthCookie");
        }
    }

    @Override
    public Single<String> parseWikicode(String source) {
        return Single.fromCallable(() -> api.action("flow-parsoid-utils")
                .param("from", "wikitext")
                .param("to", "html")
                .param("content", source)
                .param("title", "Main_page")
                .get()
                .getString("/api/flow-parsoid-utils/@content"));
    }

    @Override
    @NonNull
    public Single<MediaResult> fetchMediaByFilename(String filename) {
        return Single.fromCallable(() -> {
            CustomApiResult apiResult = api.action("query")
                    .param("prop", "revisions")
                    .param("titles", filename)
                    .param("rvprop", "content")
                    .param("rvlimit", 1)
                    .param("rvgeneratexml", 1)
                    .get();

            return new MediaResult(
                    apiResult.getString("/api/query/pages/page/revisions/rev"),
                    apiResult.getString("/api/query/pages/page/revisions/rev/@parsetree"));
        });
    }

    @Override
    @NonNull
    public LogEventResult logEvents(String user, String lastModified, String queryContinue, int limit) throws IOException {
        CustomMwApi.RequestBuilder builder = api.action("query")
                .param("list", "logevents")
                .param("letype", "upload")
                .param("leprop", "title|timestamp|ids")
                .param("leuser", user)
                .param("lelimit", limit);
        if (!TextUtils.isEmpty(lastModified)) {
            builder.param("leend", lastModified);
        }
        if (!TextUtils.isEmpty(queryContinue)) {
            builder.param("lestart", queryContinue);
        }
        CustomApiResult result = builder.get();

        return new LogEventResult(
                getLogEventsFromResult(result),
                result.getString("/api/query-continue/logevents/@lestart"));
    }

    @NonNull
    private ArrayList<LogEventResult.LogEvent> getLogEventsFromResult(CustomApiResult result) {
        ArrayList<CustomApiResult> uploads = result.getNodes("/api/query/logevents/item");
        Timber.d("%d results!", uploads.size());
        ArrayList<LogEventResult.LogEvent> logEvents = new ArrayList<>();
        for (CustomApiResult image : uploads) {
            logEvents.add(new LogEventResult.LogEvent(
                    image.getString("@pageid"),
                    image.getString("@title"),
                    parseMWDate(image.getString("@timestamp")))
            );
        }
        return logEvents;
    }

    @Override
    @Nullable
    public String revisionsByFilename(String filename) throws IOException {
        return api.action("query")
                .param("prop", "revisions")
                .param("rvprop", "timestamp|content")
                .param("titles", filename)
                .get()
                .getString("/api/query/pages/page/revisions/rev");
    }

    /**
     * Checks to see if a user is currently blocked from Commons
     *
     * @return whether or not the user is blocked from Commons
     */
    @Override
    public boolean isUserBlockedFromCommons() {
        boolean userBlocked = false;
        try {
            CustomApiResult result = api.action("query")
                    .param("action", "query")
                    .param("format", "xml")
                    .param("meta", "userinfo")
                    .param("uiprop", "blockinfo")
                    .get();
            if (result != null) {
                String blockEnd = result.getString("/api/query/userinfo/@blockexpiry");
                if (blockEnd.equals("infinite")) {
                    userBlocked = true;
                } else if (!blockEnd.isEmpty()) {
                    Date endDate = parseMWDate(blockEnd);
                    Date current = new Date();
                    userBlocked = endDate.after(current);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return userBlocked;
    }

    private Date parseMWDate(String mwDate) {
        try {
            return DateUtil.iso8601DateParse(mwDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calls media wiki's logout API
     */
    public void logout() {
        try {
            api.logout();
        } catch (IOException e) {
            Timber.e(e, "Error occurred while logging out");
        }
    }

}
