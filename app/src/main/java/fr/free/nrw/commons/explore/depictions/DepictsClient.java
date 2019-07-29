package fr.free.nrw.commons.explore.depictions;

import androidx.annotation.Nullable;

import org.wikipedia.dataclient.mwapi.MwQueryResponse;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import fr.free.nrw.commons.Media;
import fr.free.nrw.commons.depictions.models.Search;
import fr.free.nrw.commons.media.MediaInterface;
import fr.free.nrw.commons.upload.depicts.DepictsInterface;
import fr.free.nrw.commons.upload.structure.depicts.DepictedItem;
import fr.free.nrw.commons.utils.CommonsDateUtil;
import io.reactivex.Observable;
import io.reactivex.Single;

@Singleton
public class DepictsClient {

    private final DepictsInterface depictsInterface;
    private final MediaInterface mediaInterface;
    private Map<String, Map<String, String>> continuationStore;

    @Inject
    public DepictsClient(DepictsInterface depictsInterface, MediaInterface mediaInterface) {
        this.depictsInterface = depictsInterface;
        this.mediaInterface = mediaInterface;
        this.continuationStore = new HashMap<>();
    }

    /**
     * Search for depictions using the search item
     * @return list of depicted items
     */

    public Observable<DepictedItem> searchForDepictions(String query, int limit) {

        return depictsInterface.searchForDepicts(query, String.valueOf(limit))
                .flatMap(depictSearchResponse -> Observable.fromIterable(depictSearchResponse.getSearch()))
                .map(depictSearchItem -> new DepictedItem(depictSearchItem.getLabel(), depictSearchItem.getDescription(), null, false, depictSearchItem.getId()));
    }

    /**
     * @return list of images for a particular depict entity
     */

    public Observable<List<Media>> fetchImagesForDepictedItem(String query, int limit) {
        return mediaInterface.fetchImagesForDepictedItem("haswbstatement:P180="+query)
                .map(mwQueryResponse -> {
                    List<Media> mediaList =  new ArrayList<>();
                    for (Search s: mwQueryResponse.getQuery().getSearch()) {
                        Media media = new Media(null,
                                getUrl(s.getTitle()),
                                s.getTitle(),
                                new HashMap<>(),
                                "",
                                0,
                                safeParseDate(s.getTimestamp()),
                                safeParseDate(s.getTimestamp()),
                                ""
                        );
                        mediaList.add(media);
                    }
                    return mediaList;
                });

    }

    /**
     * Get url for the image from media of depictions
     */

    private String getUrl(String title) {
        String baseUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/";
        title = title.substring(title.indexOf(':')+1);
        title = title.replace(" ", "_");
        String MD5Hash = getMd5(title);
        return baseUrl + MD5Hash.charAt(0) + '/' + MD5Hash.charAt(0) + MD5Hash.charAt(1) + '/' + title + "/640px-" + title;
    }

    /**
     * Generates MD5 hash for the filename
     */

    public String getMd5(String input)
    {
        try {

            // Static getInstance method is called with hashing MD5
            MessageDigest md = MessageDigest.getInstance("MD5");

            // digest() method is called to calculate message digest
            //  of an input digest() return array of byte
            byte[] messageDigest = md.digest(input.getBytes());

            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Single<List<Media>> responseToMediaList(Observable<MwQueryResponse> response, String key) {
        return response.flatMap(mwQueryResponse -> {
            if (null == mwQueryResponse
                    || null == mwQueryResponse.query()
                    || null == mwQueryResponse.query().pages()) {
                return Observable.empty();
            }
            continuationStore.put(key, mwQueryResponse.continuation());
            return Observable.fromIterable(mwQueryResponse.query().pages());
        })
                .map(Media::from)
                .collect(ArrayList<Media>::new, List::add);
    }

    @Nullable
    private static Date safeParseDate(String dateStr) {
        try {
            return CommonsDateUtil.getIso8601DateFormatShort().parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }
}
