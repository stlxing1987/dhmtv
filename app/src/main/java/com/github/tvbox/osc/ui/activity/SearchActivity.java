package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PinyinSearchHelper;
import com.github.tvbox.osc.util.SearchSuggestHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    SourceViewModel sourceViewModel;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private TextView tvAddress;
    private ImageView ivQRCode;
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private String searchTitle = "";
    private final ArrayList<String> searchCorpus = new ArrayList<>();
    private List<String> searchKeywords = new ArrayList<>();

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (pauseRunnable != null && pauseRunnable.size() > 0) {
            searchExecutorService = Executors.newFixedThreadPool(getSearchThreadCount());
            allRunCount.set(pauseRunnable.size());
            for (Runnable runnable : pauseRunnable) {
                searchExecutorService.execute(runnable);
            }
            pauseRunnable.clear();
            pauseRunnable = null;
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvClear = findViewById(R.id.tvClear);
        tvAddress = findViewById(R.id.tvAddress);
        ivQRCode = findViewById(R.id.ivQRCode);
        mGridView = findViewById(R.id.mGridView);
        keyboard = findViewById(R.id.keyBoardRoot);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        mGridViewWord.setHasFixedSize(true);
        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        wordAdapter = new PinyinAdapter();
        mGridViewWord.setAdapter(wordAdapter);
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                search(wordAdapter.getItem(position));
            }
        });
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    try {
                        if (searchExecutorService != null) {
                            pauseRunnable = searchExecutorService.shutdownNow();
                            searchExecutorService = null;
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    search(wd);
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
            }
        });
        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                if (pos > 1) {
                    String text = etSearch.getText().toString().trim();
                    text += key;
                    etSearch.setText(text);
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 1) {
                    String text = etSearch.getText().toString().trim();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                    }
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 0) {
                    RemoteDialog remoteDialog = new RemoteDialog(mContext);
                    remoteDialog.show();
                }
            }
        });
        setLoadSir(llLayout);
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
    }

    /**
     * 拼音联想：本地简拼匹配 + 腾讯接口补充
     */
    private void loadRec(String key) {
        if (TextUtils.isEmpty(key)) {
            return;
        }
        if (PinyinSearchHelper.isLatinInput(key)) {
            List<String> local = PinyinSearchHelper.matchLatin(key, searchCorpus);
            if (!local.isEmpty()) {
                wordAdapter.setNewData(local);
            }
        }
        SearchSuggestHelper.fetch(key, new SearchSuggestHelper.Callback() {
            @Override
            public void onResult(List<String> titles) {
                LinkedHashSet<String> merged = new LinkedHashSet<>();
                if (PinyinSearchHelper.isLatinInput(key)) {
                    merged.addAll(PinyinSearchHelper.matchLatin(key, searchCorpus));
                }
                merged.addAll(titles);
                ArrayList<String> list = new ArrayList<>(merged);
                addToCorpus(list);
                if (!list.isEmpty()) {
                    wordAdapter.setNewData(list);
                }
            }

            @Override
            public void onError() {
                fallbackLocalRec(key);
            }
        });
    }

    private void fallbackLocalRec(String key) {
        if (PinyinSearchHelper.isLatinInput(key)) {
            List<String> local = PinyinSearchHelper.matchLatin(key, searchCorpus);
            if (!local.isEmpty()) {
                wordAdapter.setNewData(local);
            }
        }
    }

    private void addToCorpus(List<String> words) {
        if (words == null) {
            return;
        }
        for (String word : words) {
            if (!TextUtils.isEmpty(word) && !searchCorpus.contains(word)) {
                searchCorpus.add(word);
            }
        }
    }

    private void initData() {
        refreshQRCode();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            showLoading();
            search(title);
        }
        // 加载热词
        ArrayList<String> fallbackHots = new ArrayList<>();
        fallbackHots.add("完美世界");
        fallbackHots.add("镖人");
        fallbackHots.add("爱情有烟火");
        fallbackHots.add("庆余年");
        fallbackHots.add("斗罗大陆");
        addToCorpus(fallbackHots);
        wordAdapter.setNewData(fallbackHots);
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_mobilesearch")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("itemList").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("title").getAsString().trim().replaceAll("<|>|《|》|-", "").split(" ")[0]);
                            }
                            addToCorpus(hots);
                            wordAdapter.setNewData(hots);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("远程搜索使用手机/电脑扫描下面二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void search(String title) {
        cancel();
        showLoading();
        this.searchTitle = title;
        mGridView.setVisibility(View.INVISIBLE);
        searchAdapter.setNewData(new ArrayList<>());

        if (PinyinSearchHelper.isLatinInput(title)) {
            List<String> keywords = PinyinSearchHelper.resolveSearchKeywords(title, searchCorpus);
            if (keywords.isEmpty()) {
                resolveLatinFromRemote(title);
                return;
            }
            this.searchKeywords = keywords;
            searchResult();
            return;
        }
        this.searchKeywords = new ArrayList<>();
        this.searchKeywords.add(title);
        searchResult();
    }

    private void resolveLatinFromRemote(final String latin) {
        SearchSuggestHelper.fetch(latin, new SearchSuggestHelper.Callback() {
            @Override
            public void onResult(List<String> titles) {
                addToCorpus(titles);
                wordAdapter.setNewData(titles);
                searchKeywords = titles;
                searchResult();
            }

            @Override
            public void onError() {
                onLatinResolveFailed(latin);
            }
        });
    }

    private void onLatinResolveFailed(String latin) {
        List<String> local = PinyinSearchHelper.matchLatin(latin, searchCorpus);
        if (!local.isEmpty()) {
            searchKeywords = local;
            searchResult();
            return;
        }
        showEmpty();
        Toast.makeText(mContext, "未识别简拼「" + latin + "」，请从左侧选择片名", Toast.LENGTH_LONG).show();
    }

    private ExecutorService searchExecutorService = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
        }
        searchExecutorService = Executors.newFixedThreadPool(getSearchThreadCount());
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        if (searchKeywords == null || searchKeywords.isEmpty()) {
            searchKeywords = new ArrayList<>();
            searchKeywords.add(searchTitle);
        }
        allRunCount.set(siteKey.size() * searchKeywords.size());
        if (allRunCount.get() <= 0) {
            showEmpty();
            Toast.makeText(mContext, "没有可搜索的源", Toast.LENGTH_SHORT).show();
            return;
        }
        for (String keyword : searchKeywords) {
            for (String key : siteKey) {
                searchExecutorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        sourceViewModel.getSearch(key, keyword);
                    }
                });
            }
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            Set<String> exists = new HashSet<>();
            for (Movie.Video old : searchAdapter.getData()) {
                exists.add(old.sourceKey + "|" + old.id + "|" + old.name);
            }
            for (Movie.Video video : absXml.movie.videoList) {
                if (video != null && video.name != null && !video.name.isEmpty()
                        && video.id != null && !video.id.isEmpty()) {
                    String dedupeKey = video.sourceKey + "|" + video.id + "|" + video.name;
                    if (!exists.contains(dedupeKey)) {
                        exists.add(dedupeKey);
                        data.add(video);
                    }
                }
            }
            if (searchAdapter.getData().size() > 0) {
                searchAdapter.addData(data);
            } else {
                showSuccess();
                mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data);
            }
        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            if (searchAdapter.getData().size() <= 0) {
                showEmpty();
                if (searchTitle != null && PinyinSearchHelper.isLatinInput(searchTitle)) {
                    Toast.makeText(mContext, "简拼「" + searchTitle + "」未搜到结果，可尝试左侧推荐片名", Toast.LENGTH_LONG).show();
                }
            }
            cancel();
        }
    }

    private void cancel() {
        OkGo.getInstance().cancelTag("search");
        OkGo.getInstance().cancelTag("search_suggest");
    }

    private int getSearchThreadCount() {
        int count = Hawk.get(HawkConfig.SEARCH_THREAD, 16);
        return count < 1 ? 1 : count;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }
}