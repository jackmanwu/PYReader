package com.neuroandroid.pyreader.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.AppBarLayout;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.gson.Gson;
import com.neuroandroid.pyreader.R;
import com.neuroandroid.pyreader.adapter.BookReadAdapter;
import com.neuroandroid.pyreader.base.BaseActivity;
import com.neuroandroid.pyreader.base.BaseFragment;
import com.neuroandroid.pyreader.bean.BookReadThemeBean;
import com.neuroandroid.pyreader.config.Constant;
import com.neuroandroid.pyreader.event.BaseEvent;
import com.neuroandroid.pyreader.event.BookReadSettingEvent;
import com.neuroandroid.pyreader.event.JumpToTargetChapterEvent;
import com.neuroandroid.pyreader.manager.CacheManager;
import com.neuroandroid.pyreader.model.response.BookMixAToc;
import com.neuroandroid.pyreader.model.response.ChapterRead;
import com.neuroandroid.pyreader.model.response.Recommend;
import com.neuroandroid.pyreader.mvp.contract.IBookReadContract;
import com.neuroandroid.pyreader.mvp.presenter.BookReadPresenter;
import com.neuroandroid.pyreader.provider.PYReaderStore;
import com.neuroandroid.pyreader.ui.fragment.BookDetailCommunityFragment;
import com.neuroandroid.pyreader.ui.fragment.ChapterListFragment;
import com.neuroandroid.pyreader.utils.BookReadSettingUtils;
import com.neuroandroid.pyreader.utils.FragmentUtils;
import com.neuroandroid.pyreader.utils.L;
import com.neuroandroid.pyreader.utils.NavigationUtils;
import com.neuroandroid.pyreader.utils.ShowUtils;
import com.neuroandroid.pyreader.utils.ThemeUtils;
import com.neuroandroid.pyreader.utils.TimeUtils;
import com.neuroandroid.pyreader.utils.UIUtils;
import com.neuroandroid.pyreader.widget.NoPaddingTextView;
import com.neuroandroid.pyreader.widget.dialog.BookReadLoadingDialog;
import com.neuroandroid.pyreader.widget.dialog.BookReadSettingDialog;
import com.neuroandroid.pyreader.widget.dialog.ColorPickerDialog;
import com.neuroandroid.pyreader.widget.reader.BookReadFactory;
import com.neuroandroid.pyreader.widget.reader.BookReadView;
import com.neuroandroid.pyreader.widget.recyclerviewpager.RecyclerViewPager;
import com.xw.repo.BubbleSeekBar;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.polaric.colorful.Colorful;

import java.lang.ref.WeakReference;
import java.util.List;

import butterknife.BindView;

/**
 * Created by NeuroAndroid on 2017/7/6.
 * 小说阅读界面
 * 加载逻辑：
 * 加载当前章节
 * 加载当前章节的前一章
 * 加载当前章节的后n章
 * 如果当前章节已经缓存则直接加载缓存中的章节
 * <p>
 * 总共加载(1 + 1 + n)章内容
 * 保存的阅读位置：[3, 11]
 * 表示阅读到了第3章的第11页
 */
public class BookReadActivity extends BaseActivity<IBookReadContract.Presenter>
        implements IBookReadContract.View, ChapterListFragment.OnDrawerPageChangeListener {
    private static final int ANIM_DURATION = 100;
    private static final int MSG_UPDATE_SYSTEM_TIME = 34;

    /**
     * 加载当前章节的后n章
     */
    public static final int ONE_TIME_LOAD_CHAPTER = 3;

    @BindView(R.id.app_bar)
    AppBarLayout mAppBarLayout;
    @BindView(R.id.rv_book_read)
    RecyclerViewPager mRvBookRead;
    @BindView(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.ll_bottom_control)
    LinearLayout mLlBottomControl;
    @BindView(R.id.ll_catalog)
    LinearLayout mLlCatalog;
    @BindView(R.id.sb_progress)
    BubbleSeekBar mSbProgress;
    @BindView(R.id.view_cover)
    View mViewCover;
    @BindView(R.id.ll_setting)
    LinearLayout mLlSetting;
    @BindView(R.id.tv_pre_chapter)
    NoPaddingTextView mTvPreChapter;
    @BindView(R.id.tv_next_chapter)
    NoPaddingTextView mTvNextChapter;
    @BindView(R.id.ll_night_mode)
    LinearLayout mLlNightMode;
    @BindView(R.id.iv_night_mode)
    ImageView mIvNightMode;
    @BindView(R.id.tv_night_mode)
    NoPaddingTextView mTvNightMode;
    @BindView(R.id.iv_catalog)
    ImageView mIvCatalog;
    @BindView(R.id.iv_file_download)
    ImageView mIvFileDownload;
    @BindView(R.id.iv_aa)
    ImageView mIvAa;

    // 书籍是否来自SD卡
    private boolean mFromSD;
    /**
     * 书籍id
     */
    private String mBookId;
    private Recommend.BooksBean mBooksBean;

    /**
     * 章节列表
     */
    private List<BookMixAToc.MixToc.Chapters> mChapterList;

    private BookReadFactory mBookReadFactory;
    private BookReadAdapter mBookReadAdapter;
    private PYReaderStore mPYReaderStore;
    private String mBookTitle;
    private BookReadHandler mBookReadHandler;
    private String mPreUpdateTime;
    private BatteryBroadcastReceiver mBatteryReceiver;
    private ChapterListFragment mChapterListFragment;

    private int mAppBarHeight, mBottomControlHeight;
    /**
     * ToolBar和底部控制台是否显示
     */
    private boolean mShowAppBarAndBottomControl;

    private BaseFragment mCurrentFragment;

    // 当前阅读到第几章
    private int mReadChapter;
    // 当前阅读到第几章的第几页
    private int mReadPage;

    // 跳转到指定章节
    private int mTargetChapter = -1;
    private LinearLayoutManager mLayoutManager;
    private int mSystemScreenBrightness;
    private BookReadSettingDialog mBookReadSettingDialog;

    private BookReadLoadingDialog mBookReadLoadingDialog;

    @Override
    public void onPageSelected(boolean isLast) {
        if (isLast) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        } else if (mDrawerLayout.getDrawerLockMode(GravityCompat.START) == DrawerLayout.LOCK_MODE_UNLOCKED) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
        }
    }

    private static class BookReadHandler extends Handler {
        private WeakReference<BookReadActivity> mActivity;

        public BookReadHandler(BookReadActivity activity) {
            this.mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            final BookReadActivity bookReadActivity = mActivity.get();
            if (bookReadActivity != null) {
                switch (msg.what) {
                    case MSG_UPDATE_SYSTEM_TIME:
                        bookReadActivity.updateSystemTime();
                        break;
                }
            }
        }
    }

    @Override
    protected void initPresenter() {
        mPresenter = new BookReadPresenter(this);
    }

    @Override
    protected int attachLayoutRes() {
        return R.layout.activity_book_read;
    }

    @Override
    protected void initView() {
        setDisplayHomeAsUpEnabled();
        setToolbarTitle("");
        UIUtils.fullScreen(this, true);

        /*new BookReadLoadingDialog(this)
                .setDialogCanceledOnTouchOutside(false)
                .showDialog();*/

        mViewCover.setVisibility(View.GONE);

        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mRvBookRead.setLayoutManager(mLayoutManager);
        mBookReadAdapter = new BookReadAdapter(this, null, null);
        mBookReadAdapter.clearRvAnim(mRvBookRead);
        mBookReadAdapter.setRvBookRead(mRvBookRead);
        mRvBookRead.setAdapter(mBookReadAdapter);

        // mChapterListFragment = (ChapterListFragment) getSupportFragmentManager().findFragmentById(R.id.left_menu);
        mChapterListFragment = new ChapterListFragment();
        FragmentUtils.replaceFragment(getSupportFragmentManager(), mChapterListFragment, R.id.fl_left_menu, false);
        mChapterListFragment.setImmersive(mImmersive);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        setScreenBrightness();

        boolean darkMode = ThemeUtils.isDarkMode();
        changeNightModeIcon(darkMode);

        if (darkMode) {
            mLlBottomControl.setBackgroundColor(UIUtils.getColor(R.color.backgroundColorDark));
            int color = UIUtils.getColor(R.color.white);
            mIvCatalog.setColorFilter(color);
            mIvFileDownload.setColorFilter(color);
            mIvAa.setColorFilter(color);
            mIvNightMode.setColorFilter(color);
        }

        if (ThemeUtils.isDarkMode())
            mAppBarLayout.setBackgroundColor(UIUtils.getColor(R.color.backgroundColorDark));
    }

    /**
     * 设置屏幕亮度
     * 只在当前界面有效
     */
    private void setScreenBrightness() {
        mSystemScreenBrightness = UIUtils.getScreenBrightness(this);
        int screenBrightness = BookReadSettingUtils.getScreenBrightness(this);
        if (screenBrightness != BookReadSettingUtils.FOLLOW_SYSTEM) {
            // 不是跟随系统才去设置屏幕亮度
            UIUtils.setScreenBrightness(this, screenBrightness);
        } else {

        }
    }

    @Override
    protected void initData() {
        mBookReadHandler = new BookReadHandler(this);
        updateSystemTime();
        registerBatteryBroadcastReceiver();
        mBookReadFactory = BookReadFactory.getInstance();
        mBookReadFactory.initBookReadMap();
        mPYReaderStore = PYReaderStore.getInstance(this);
        mFromSD = getIntent().getBooleanExtra(Constant.INTENT_FROM_SD, false);
        mBooksBean = (Recommend.BooksBean) getIntent().getSerializableExtra(Constant.INTENT_BOOK_BEAN);
        mBookTitle = mBooksBean.getTitle();
        mBookId = mBooksBean.getBookId();
        mChapterListFragment.setBookId(mBookId);
        List<BookMixAToc.MixToc.Chapters> chapterList = CacheManager.getChapterList(this, mBookId);
        if (chapterList == null) {
            showBookReaderLoadingDialog();
            mPresenter.getBookMixAToc(mBookId);
        } else {
            L.e("从缓存加载章节列表");
            mChapterListFragment.setChaptersList(chapterList);
            String json = new Gson().toJson(chapterList);
            L.e("章节列表 : " + json);
            mChapterList = chapterList;
            loadReadPositionLogic();
        }
    }

    @Override
    protected void initListener() {
        mBookReadAdapter.setReadStateChangeListener(new BookReadView.OnReadStateChangeListener() {
            @Override
            public void onChapterChanged(int oldChapter, int newChapter) {
                L.e("oldChapter : " + oldChapter + " newChapter : " + newChapter);
                // 当章节发生变化的时候去加载附近5章内容
                if (mTargetChapter != -1) mTargetChapter = -1;
                setNullReadPositionCallBack();
                mReadChapter = newChapter;
                loadChapterLogic(mReadChapter);
            }

            @Override
            public void onPageChanged(int currentChapter, int page) {
                L.e("currentChapter : " + currentChapter + " page : " + page);
                // 页码发生变化的时候保存当前章节和当前页码的值
                saveReadPosition(currentChapter, page);
            }

            @Override
            public void onCenterClick() {
                L.e("点击了中心区域");
                if (!mShowAppBarAndBottomControl) {
                    // 如果没有显示则显示
                    showAppBarAndBottomControl();
                } else {
                    hideAppBarAndBottomControl();
                }
            }

            @Override
            public void onNextPage() {
                L.e("下一页");
                toNextPage();
            }

            @Override
            public void onPrePage() {
                L.e("上一页");
                toPrePage();
            }
        });
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                UIUtils.fullScreen(BookReadActivity.this, false);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                UIUtils.fullScreen(BookReadActivity.this, true);
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                mChapterListFragment.restoreOrder();
            }
        });
        mDrawerLayout.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    closeDrawer();
                    break;
            }
            return false;
        });
        mAppBarLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mAppBarLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mAppBarHeight = mAppBarLayout.getHeight();
                mBottomControlHeight = mLlBottomControl.getHeight();
                mAppBarLayout.setTranslationY(-mAppBarHeight);
                mLlBottomControl.setTranslationY(mBottomControlHeight);
            }
        });
        mLlCatalog.setOnClickListener(view ->
                hideAppBarAndBottomControl(() -> {
                    mChapterListFragment.restoreOrder();
                    mChapterListFragment.setCurrentItem(ChapterListFragment.CATALOG_POSITION);
                    mChapterListFragment.setReadChapter(mReadChapter);
                    mDrawerLayout.openDrawer(GravityCompat.START);
                }));
        mViewCover.setOnClickListener(view -> hideAppBarAndBottomControl());
        mSbProgress.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListenerAdapter() {
            @Override  // 手指抬起时候回调
            public void getProgressOnActionUp(int progress, float progressFloat) {
                super.getProgressOnActionUp(progress, progressFloat);
                int chapter = (int) ((progressFloat / 100) * (mChapterList.size() - 1));
                jumpToTargetChapter(chapter);
            }
        });
        mLlSetting.setOnClickListener(view -> {
            hideAppBarAndBottomControl();
            mBookReadSettingDialog = new BookReadSettingDialog(BookReadActivity.this)
                    .setSystemScreenBrightness(mSystemScreenBrightness)
                    .setFullWidth()
                    .setFromBottom()
                    .setDimAmount(0f)
                    .showDialog();
        });
        mTvPreChapter.setOnClickListener(view -> {
            if (mReadChapter <= 0) {
                ShowUtils.showToast("已经是第一章了");
                return;
            }
            mReadChapter--;
            jumpToTargetChapter(mReadChapter);
        });
        mTvNextChapter.setOnClickListener(view -> {
            if (mReadChapter >= mChapterList.size() - 1) {
                ShowUtils.showToast("已经是最后一章了");
                return;
            }
            mReadChapter++;
            jumpToTargetChapter(mReadChapter);
        });
        mLlNightMode.setOnClickListener(view -> {
            boolean darkMode;
            if (ThemeUtils.isDarkMode()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                darkMode = false;
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                darkMode = true;
            }
            changeNightModeIcon(darkMode);
            Colorful.config(this)
                    .dark(darkMode)
                    .apply();
            UIUtils.getHandler().postDelayed(() -> changeTheme(), 300);
        });
    }

    /**
     * 显示加载dialog
     */
    private void showBookReaderLoadingDialog() {
        if (mBookReadLoadingDialog == null) {
            mBookReadLoadingDialog = new BookReadLoadingDialog(this)
                    .setDialogCanceledOnTouchOutside(false)
                    .showDialog();
        } else {
            mBookReadLoadingDialog.showDialog();
        }
    }

    /**
     * 隐藏加载dialog
     */
    private void dismissBookReadLoadingDialog() {
        if (mBookReadLoadingDialog != null && mBookReadLoadingDialog.isShowing()) {
            mBookReadLoadingDialog.dismissDialog();
        }
    }

    /**
     * 改变夜间模式的图标
     *
     * @param darkMode 是否是夜间模式
     */
    private void changeNightModeIcon(boolean darkMode) {
        if (darkMode) {
            mIvNightMode.setImageResource(R.drawable.ic_sunny_mode);
            mTvNightMode.setText("日间模式");
        } else {
            mIvNightMode.setImageResource(R.drawable.ic_night_mode);
            mTvNightMode.setText("夜间模式");
        }
    }

    /**
     * 保存当前阅读位置
     *
     * @param currentChapter 当前阅读位置
     * @param page           当前章节的第几页
     */
    private void saveReadPosition(int currentChapter, int page) {
        CacheManager.saveReadPosition(BookReadActivity.this, mBookId, currentChapter, page);
    }

    /**
     * 关闭抽屉
     */
    public void closeDrawer() {
        mDrawerLayout.closeDrawers();
    }

    public DrawerLayout getDrawerLayout() {
        return mDrawerLayout;
    }

    @Override
    public void showBookToc(List<BookMixAToc.MixToc.Chapters> list) {
        dismissBookReadLoadingDialog();

        mChapterList = list;
        mChapterListFragment.setChaptersList(list);
        CacheManager.saveChapterList(this, mBookId, mChapterList);
        loadReadPositionLogic();
    }

    /**
     * 加载上次阅读位置的逻辑
     */
    private void loadReadPositionLogic() {
        setReadPositionCallBack();
        int[] readPosition = CacheManager.getReadPosition(this, mBookId);
        mReadChapter = readPosition[0];
        mReadPage = readPosition[1];
        L.e("阅读" + mBookTitle + " 第" + (mReadChapter + 1) + "章 第" + mReadPage + "页");
        float percent = (mReadChapter + 1) * 100f / mChapterList.size();
        mSbProgress.setProgress(percent);
        loadChapterLogic(mReadChapter);
    }

    /**
     * 加载逻辑
     */
    private void loadChapterLogic(int readChapter) {
        for (int i = 0; i < 2 + ONE_TIME_LOAD_CHAPTER; i++) {
            if (readChapter == 0) {
                loadChapterContent(i);
            } else {
                // 共16章  0, 1...11, 12, 13, 14, 15
                int diff = readChapter - (mChapterList.size() - ONE_TIME_LOAD_CHAPTER - 1);
                if (diff > 0) {
                    // 当前阅读到第13章, 加载12, 13, 14, 15章  diff = 1
                    // 当前阅读到第14章, 加载13, 14, 15章  diff = 2
                    // 当前阅读到第15章, 加载14, 15章  diff = 3
                    if (i >= diff) {
                        loadChapterContent(readChapter + (i - (diff + 1)));
                    }
                } else {
                    // 当前阅读到n(1-12)章, 加载n-1, n, 后面3章
                    // 比如当前阅读到第1章, 加载0, 1, 2, 3, 4章
                    loadChapterContent(readChapter + (i - 1));
                }
            }
        }
    }

    /**
     * 如果有缓存则加载缓存中的章节内容
     * 否则直接网络加载章节内容
     *
     * @param chapter 加载第几章节
     */
    private void loadChapterContent(int chapter) {
        L.e("加载第" + chapter + "章, 总章节数: " + mChapterList.size());
        if (mPYReaderStore.findChapterByBookId(chapter, mBookId)) {
            // show dialog
            showBookReaderLoadingDialog();
            mPresenter.getChapterRead(mChapterList.get(chapter).getLink(), chapter);
        } else {
            mBookReadFactory.setChapterContent(mBookReadAdapter, mPYReaderStore.getChapter(chapter, mBookId),
                    chapter, mChapterList.get(chapter).getTitle(), mBookTitle, mReadPositionCallBack, mTargetChapter);
        }
    }

    @Override
    public void showChapterRead(ChapterRead.Chapter data, int chapter) {
        dismissBookReadLoadingDialog();

        // 缓存
        mPYReaderStore.addChapter(chapter, mBookId, data.getBody());
        mBookReadFactory.setChapterContent(mBookReadAdapter, mPYReaderStore.getChapter(chapter, mBookId),
                chapter, mChapterList.get(chapter).getTitle(), mBookTitle, mReadPositionCallBack, mTargetChapter);
    }

    @Override
    protected boolean useEventBus() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BaseEvent baseEvent) {
        switch (baseEvent.getEventFlag()) {
            case BaseEvent.EVENT_JUMP_TO_TARGET_CHAPTER:
                handleChapterJumpLogic(baseEvent);
                break;
            case BaseEvent.EVENT_BOOK_READ_SETTING:
                handleBookReadSetting(baseEvent);
                break;
        }
    }

    /**
     * 处理章节跳转逻辑
     */
    private void handleChapterJumpLogic(BaseEvent baseEvent) {
        JumpToTargetChapterEvent toTargetChapterEvent = (JumpToTargetChapterEvent) baseEvent;
        int page = toTargetChapterEvent.getPage();
        L.e("收到章节跳转的事件啦 -- 将要跳转到第" + page + "页");
        mLayoutManager.scrollToPosition(page);
    }

    /**
     * 处理设置dialog里面的事件
     */
    private void handleBookReadSetting(BaseEvent baseEvent) {
        BookReadSettingEvent readSettingEvent = (BookReadSettingEvent) baseEvent;
        BookReadThemeBean bookReadThemeBean = readSettingEvent.getBookReadThemeBean();
        String bookReadThemeName = bookReadThemeBean.getBookReadThemeName();
        if (!readSettingEvent.isFromColorPickerDialog() && "自定义".equals(bookReadThemeName)) {
            BookReadThemeBean bookReadTheme = BookReadSettingUtils.getBookReadTheme(this);
            if ("自定义".equals(bookReadTheme.getBookReadThemeName())) {
                mBookReadSettingDialog.dismissDialog();
                new ColorPickerDialog(this)
                        .setDimAmount(0f)
                        .showDialog();
                return;
            }
        }
        if (readSettingEvent.isFromColorPickerDialog()) {
            // 保存自定义主题
            BookReadSettingUtils.saveCustomBookReadTheme(this, bookReadThemeBean);
        }
        BookReadSettingUtils.saveBookReadTheme(this, bookReadThemeBean);
        mBookReadAdapter.notifyItemChanged(mRvBookRead.getCurrentPosition());

        if (ThemeUtils.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            Colorful.config(this)
                    .dark(false)
                    .apply();
            UIUtils.getHandler().postDelayed(() -> changeTheme(), 300);
        }
    }

    private InnerBookReadPositionCallBack mReadPositionCallBack;

    /**
     * mReadPositionCallBack赋值
     */
    public void setReadPositionCallBack() {
        mReadPositionCallBack = new InnerBookReadPositionCallBack();
    }

    /**
     * mReadPositionCallBack置为空
     */
    public void setNullReadPositionCallBack() {
        if (mReadPositionCallBack != null) mReadPositionCallBack = null;
    }

    private class InnerBookReadPositionCallBack implements BookReadFactory.ReadPositionCallBack {
        @Override
        public void callBack(int page) {
            toReadPosition(page);
        }
    }

    /**
     * 跳转到保存的阅读位置
     */
    private void toReadPosition(int page) {
        int readPosition;
        if (mReadChapter > 0) {
            readPosition = page + mReadPage;
        } else {
            readPosition = mReadPage;
        }
        mRvBookRead.scrollToPosition(readPosition - 1);
    }

    /**
     * 跳转到下一页
     */
    private void toNextPage() {
        int nextPosition = mRvBookRead.getCurrentPosition() + 1;
        if (nextPosition <= mBookReadAdapter.getItemCount()) {
            mRvBookRead.smoothScrollToPosition(nextPosition);
        } else {
            ShowUtils.showToast("已经是最后一页了");
        }
    }

    /**
     * 跳转到上一页
     */
    private void toPrePage() {
        int prePosition = mRvBookRead.getCurrentPosition() - 1;
        if (prePosition >= 0) {
            mRvBookRead.smoothScrollToPosition(prePosition);
        } else {
            ShowUtils.showToast("已经是第一页了");
        }
    }

    /**
     * 显示ToolBar和底部控制栏
     */
    private void showAppBarAndBottomControl() {
        mViewCover.setVisibility(View.VISIBLE);
        ViewCompat.animate(mAppBarLayout).translationY(0).setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new ViewPropertyAnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(View view) {
                        super.onAnimationStart(view);
                        mShowAppBarAndBottomControl = true;
                        UIUtils.fullScreen(BookReadActivity.this, false);
                    }
                }).start();
        ViewCompat.animate(mLlBottomControl).translationY(0).setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .setUpdateListener(view -> mSbProgress.correctOffsetWhenContainerOnScrolling()).start();
    }

    private void hideAppBarAndBottomControl() {
        hideAppBarAndBottomControl(null);
    }

    private void hideAppBarAndBottomControl(OnAppBarAndBottomControlActivityListener appBarAndBottomControlActivityListener) {
        mViewCover.setVisibility(View.GONE);
        ViewCompat.animate(mAppBarLayout).translationY(-mAppBarHeight).setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new ViewPropertyAnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(View view) {
                        super.onAnimationStart(view);
                        mShowAppBarAndBottomControl = false;
                        UIUtils.fullScreen(BookReadActivity.this, true);
                    }

                    @Override
                    public void onAnimationEnd(View view) {
                        super.onAnimationEnd(view);
                        if (appBarAndBottomControlActivityListener != null) {
                            appBarAndBottomControlActivityListener.onAppBarAndBottomControlClosed();
                        }
                    }
                }).start();
        ViewCompat.animate(mLlBottomControl).translationY(mBottomControlHeight).setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .setUpdateListener(view -> mSbProgress.correctOffsetWhenContainerOnScrolling()).start();
    }

    /**
     * 打开BookDetailCommunityFragment
     * index : (0 : 讨论  1 : 书评)
     */
    public void openBookDetailCommunityFragment(int index) {
        UIUtils.fullScreen(this, false);
        mCurrentFragment = new BookDetailCommunityFragment();
        Bundle bundle = new Bundle();
        bundle.putString(Constant.BOOK_ID, mBookId);
        bundle.putString(Constant.BOOK_TITLE, mBookTitle);
        bundle.putInt(Constant.BOOK_DETAIL_COMMUNITY_INDEX, index);
        mCurrentFragment.setArguments(bundle);
        FragmentUtils.replaceFragment(getSupportFragmentManager(), mCurrentFragment, R.id.fl_container, false);
    }

    /**
     * 跳转到指定章节
     *
     * @param targetChapter 目标章节
     */
    public void jumpToTargetChapter(int targetChapter) {
        this.mReadChapter = targetChapter;
        this.mTargetChapter = targetChapter;
        loadChapterLogic(targetChapter);
        saveReadPosition(targetChapter, 1);
    }

    private void updateSystemTime() {
        String updateTime = TimeUtils.millis2String(System.currentTimeMillis(), "HH:mm");
        if (UIUtils.isEmpty(mPreUpdateTime)) {
            mBookReadAdapter.setUpdateTime(updateTime);
        }
        if (!UIUtils.isEmpty(mPreUpdateTime) && !mPreUpdateTime.equals(updateTime)) {
            L.e("更新UI : " + updateTime);
            mBookReadAdapter.setUpdateTime(updateTime);
        }
        mPreUpdateTime = updateTime;
        mBookReadHandler.sendEmptyMessageDelayed(MSG_UPDATE_SYSTEM_TIME, 1000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book_read, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_download:

                break;
            case R.id.action_bookmark:

                break;
            case R.id.action_community:
                hideAppBarAndBottomControl(() -> openBookDetailCommunityFragment(0));
                break;
            case R.id.action_book_detail:
                hideAppBarAndBottomControl(() -> NavigationUtils.goToBookDetailPage(this, mBookId, true));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 注册更新电量系统广播
     */
    private void registerBatteryBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mBatteryReceiver = new BatteryBroadcastReceiver();
        registerReceiver(mBatteryReceiver, filter);
    }

    private class BatteryBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra("level", 0);
            updateBatteryImage(level);
        }
    }

    /**
     * 更新电池图标
     *
     * @param level 电池电量
     */
    private void updateBatteryImage(int level) {
        L.e("level : " + level);
        if (level < 0) level = 0;
        if (level > 100) level = 100;
        mBookReadAdapter.setBatteryLevel(level);
    }

    @Override
    public void onBackPressed() {
        if (mCurrentFragment != null) {
            UIUtils.fullScreen(this, true);
            FragmentUtils.removeFragment(mCurrentFragment);
            mCurrentFragment = null;
            return;
        }
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBookReadHandler.removeCallbacksAndMessages(null);
        mBookReadHandler = null;
        if (mBatteryReceiver != null) {
            unregisterReceiver(mBatteryReceiver);
            mBatteryReceiver = null;
        }
    }

    public interface OnAppBarAndBottomControlActivityListener {
        void onAppBarAndBottomControlClosed();
    }
}
