package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.MobileCategorySection;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.MobileCategoryActivity;
import com.github.tvbox.osc.ui.activity.MobileHomeActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.adapter.MobileCategorySectionAdapter;
import com.github.tvbox.osc.ui.dialog.MobileStoreSheet;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MobileHomeFragment extends BaseLazyFragment {
    private static final int PREVIEW_LIMIT = 8;

    private RecyclerView rvSections;
    private ProgressBar progressLoading;
    private MobileCategorySectionAdapter sectionAdapter;
    private SourceViewModel sourceViewModel;
    private List<MobileCategorySection> sections = new ArrayList<>();
    private Queue<MobileCategorySection> pendingSections = new LinkedList<>();
    private MobileCategorySection loadingSection;
    private AbsSortXml absSortXml;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_mobile_home;
    }

    @Override
    protected void init() {
        rvSections = findViewById(R.id.rvSections);
        progressLoading = findViewById(R.id.progressLoading);
        sectionAdapter = new MobileCategorySectionAdapter();
        rvSections.setLayoutManager(new LinearLayoutManager(mContext));
        rvSections.setAdapter(sectionAdapter);
        sectionAdapter.setMoreClickListener(section -> openCategory(section.sort));

        findViewById(R.id.searchBar).setOnClickListener(v -> jumpActivity(SearchActivity.class));
        findViewById(R.id.btnStoreManage).setOnClickListener(v -> showStoreSheet());

        sourceViewModel = new ViewModelProvider(requireActivity()).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                progressLoading.setVisibility(View.GONE);
                absSortXml = absXml;
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                if (home == null || home.getKey() == null) {
                    return;
                }
                List<MovieSort.SortData> sortTabs = DefaultConfig.adjustSort(home.getKey(),
                        absXml != null && absXml.classes != null && absXml.classes.sortList != null
                                ? absXml.classes.sortList : new ArrayList<>(), true);
                buildSections(sortTabs);
            }
        });
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (loadingSection == null) {
                    return;
                }
                List<Movie.Video> videos = new ArrayList<>();
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
                    int limit = Math.min(PREVIEW_LIMIT, absXml.movie.videoList.size());
                    for (int i = 0; i < limit; i++) {
                        videos.add(absXml.movie.videoList.get(i));
                    }
                }
                loadingSection.videos = videos;
                loadingSection.loading = false;
                int index = sections.indexOf(loadingSection);
                if (index >= 0) {
                    sectionAdapter.notifyItemChanged(index);
                }
                loadingSection = null;
                loadNextSectionPreview();
            }
        });

        if (mActivity instanceof MobileHomeActivity) {
            MobileHomeActivity host = (MobileHomeActivity) mActivity;
            if (host.isConfigReady()) {
                loadSortData();
            } else {
                progressLoading.setVisibility(View.VISIBLE);
                host.runWhenConfigReady(this::loadSortData);
            }
        }
    }

    private void buildSections(List<MovieSort.SortData> sortTabs) {
        sections.clear();
        pendingSections.clear();
        loadingSection = null;
        for (MovieSort.SortData sort : sortTabs) {
            MobileCategorySection section = new MobileCategorySection(sort);
            sections.add(section);
            if ("my0".equals(sort.id)) {
                loadMySectionPreview(section);
            } else {
                pendingSections.add(section);
            }
        }
        sectionAdapter.setNewData(sections);
        loadNextSectionPreview();
    }

    private void loadNextSectionPreview() {
        if (loadingSection != null) {
            return;
        }
        loadingSection = pendingSections.poll();
        if (loadingSection == null) {
            return;
        }
        sourceViewModel.getList(loadingSection.sort, 1);
    }

    private void loadMySectionPreview(MobileCategorySection section) {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        if (homeRec == 1 && absSortXml != null && absSortXml.videoList != null && !absSortXml.videoList.isEmpty()) {
            applyPreview(section, absSortXml.videoList);
            return;
        }
        if (homeRec == 2) {
            section.loading = false;
            sectionAdapter.notifyDataSetChanged();
            return;
        }
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (today.equals(requestDay)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    applyPreview(section, loadHots(json));
                    return;
                }
            }
            OkGo.<String>get("https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range="
                    + year + "," + year).execute(new AbsCallback<String>() {
                @Override
                public void onSuccess(Response<String> response) {
                    String netJson = response.body();
                    Hawk.put("home_hot_day", today);
                    Hawk.put("home_hot", netJson);
                    mActivity.runOnUiThread(() -> applyPreview(section, loadHots(netJson)));
                }

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    return response.body().string();
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
            section.loading = false;
            sectionAdapter.notifyDataSetChanged();
        }
    }

    private void applyPreview(MobileCategorySection section, List<Movie.Video> source) {
        List<Movie.Video> videos = new ArrayList<>();
        if (source != null) {
            int limit = Math.min(PREVIEW_LIMIT, source.size());
            for (int i = 0; i < limit; i++) {
                videos.add(source.get(i));
            }
        }
        section.videos = videos;
        section.loading = false;
        int index = sections.indexOf(section);
        if (index >= 0) {
            sectionAdapter.notifyItemChanged(index);
        }
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                vod.pic = obj.get("cover").getAsString();
                result.add(vod);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private void openCategory(MovieSort.SortData sort) {
        if (sort == null) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putSerializable(MobileCategoryActivity.EXTRA_SORT, sort);
        jumpActivity(MobileCategoryActivity.class, bundle);
    }

    private void loadSortData() {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home == null || home.getKey() == null) {
            progressLoading.setVisibility(View.GONE);
            return;
        }
        progressLoading.setVisibility(View.VISIBLE);
        sourceViewModel.getSort(home.getKey());
    }

    public void reloadContent() {
        loadSortData();
    }

    private void showStoreSheet() {
        MobileStoreSheet sheet = new MobileStoreSheet(mContext, () -> {
            if (mActivity instanceof MobileHomeActivity) {
                ((MobileHomeActivity) mActivity).reloadConfig(false, true);
            }
        });
        sheet.show();
    }
}
