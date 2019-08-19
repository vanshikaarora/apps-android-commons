package fr.free.nrw.commons.depictions.SubClass;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import fr.free.nrw.commons.explore.depictions.DepictsClient;
import fr.free.nrw.commons.explore.recentsearches.RecentSearch;
import fr.free.nrw.commons.explore.recentsearches.RecentSearchesDao;
import fr.free.nrw.commons.mwapi.OkHttpJsonApiClient;
import fr.free.nrw.commons.upload.structure.depictions.DepictedItem;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

import static fr.free.nrw.commons.di.CommonsApplicationModule.IO_THREAD;
import static fr.free.nrw.commons.di.CommonsApplicationModule.MAIN_THREAD;

/**
* Presenter for parent classes and child classes of Depicted items in Explore
 */
public class SubDepictionListPresenter implements SubDepictionListContract.UserActionListener {

    private static final SubDepictionListContract.View DUMMY = (SubDepictionListContract.View) Proxy
            .newProxyInstance(
                    SubDepictionListContract.View.class.getClassLoader(),
                    new Class[]{SubDepictionListContract.View.class},
                    (proxy, method, methodArgs) -> null);

    private final Scheduler ioScheduler;
    private final Scheduler mainThreadScheduler;
    private  SubDepictionListContract.View view = DUMMY;
    RecentSearchesDao recentSearchesDao;
    String query;
    protected CompositeDisposable compositeDisposable = new CompositeDisposable();
    DepictsClient depictsClient;
    private static int TIMEOUT_SECONDS = 15;
    private List<DepictedItem> queryList = new ArrayList<>();
    OkHttpJsonApiClient okHttpJsonApiClient;

    @Inject
    public SubDepictionListPresenter(RecentSearchesDao recentSearchesDao, DepictsClient depictsClient, OkHttpJsonApiClient okHttpJsonApiClient,  @Named(IO_THREAD) Scheduler ioScheduler,
                                     @Named(MAIN_THREAD) Scheduler mainThreadScheduler) {
        this.recentSearchesDao = recentSearchesDao;
        this.ioScheduler = ioScheduler;
        this.mainThreadScheduler = mainThreadScheduler;
        this.depictsClient = depictsClient;
        this.okHttpJsonApiClient = okHttpJsonApiClient;
    }
    @Override
    public void onAttachView(SubDepictionListContract.View view) {
        this.view = view;
    }

    @Override
    public void onDetachView() {
        this.view = DUMMY;
    }

    @Override
    public void saveQuery() {
        RecentSearch recentSearch = recentSearchesDao.find(query);

        // Newly searched query...
        if (recentSearch == null) {
            recentSearch = new RecentSearch(null, query, new Date());
        } else {
            recentSearch.setLastSearched(new Date());
        }
        recentSearchesDao.save(recentSearch);
    }

    @Override
    public void fetchThumbnailForEntityId(String entityId, int position) {
        compositeDisposable.add(depictsClient.getP18ForItem(entityId)
                .subscribeOn(ioScheduler)
                .observeOn(mainThreadScheduler)
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .subscribe(response -> {
                    Timber.e("line67" + response);
                    view.onImageUrlFetched(response,position);
                }));
    }

    @Override
    public void initSubDepictionList(String qid, Boolean isParentClass) throws IOException {
        if (isParentClass) {
            compositeDisposable.add(okHttpJsonApiClient.getParentQIDs(qid)
                    .subscribeOn(ioScheduler)
                    .observeOn(mainThreadScheduler)
                    .subscribe(this::handleSuccess, this::handleError));
        } else {
            compositeDisposable.add(okHttpJsonApiClient.getChildQIDs(qid)
                    .subscribeOn(ioScheduler)
                    .observeOn(mainThreadScheduler)
                    .subscribe(this::handleSuccess, this::handleError));
        }

    }

    @Override
    public String getQuery() {
        return query;
    }

    /**
     * Handles the success scenario
     * it initializes the recycler view by adding items to the adapter
     */

    public void handleSuccess(List<DepictedItem> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) {
            if(queryList.isEmpty()){
                view.initErrorView();
            }else{
                view.setIsLastPage(true);
            }
        } else {
            this.queryList.addAll(mediaList);
            view.onSuccess(mediaList);
        }
    }

    /**
     * Logs and handles API error scenario
     */
    private void handleError(Throwable throwable) {
        Timber.e(throwable, "Error occurred while loading queried depictions");
        try {
            view.initErrorView();
            view.showSnackbar();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
