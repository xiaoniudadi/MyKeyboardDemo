package com.paulniu.panllibrary.imchat;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.paulniu.panllibrary.R;
import com.paulniu.panllibrary.imchat.callbacks.IMChatEmojiClickCallback;
import com.paulniu.panllibrary.imchat.callbacks.IMChatSendMessageCallback;
import com.paulniu.panllibrary.imchat.callbacks.IMRecordingStateChangeCallback;
import com.paulniu.panllibrary.imchat.callbacks.IMShowRecordViewCallback;
import com.paulniu.panllibrary.imchat.fragments.IMChatKeyboardExpandFragment;
import com.paulniu.panllibrary.imchat.fragments.IMChatKeyboardRecorderFragment;
import com.paulniu.panllibrary.imchat.fragments.IMChatKeyboardSmilyFragment;
import com.paulniu.panllibrary.utils.EditSharedPreferences;
import com.paulniu.panllibrary.utils.ScreenUtil;
import com.paulniu.panllibrary.utils.UtilityExpression;

import org.greenrobot.eventbus.EventBus;


/**
 * 自定义view--键盘
 */
public class IMChatPanelView extends LinearLayout implements
        View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        View.OnTouchListener,
        IMChatEmojiClickCallback,
        TextWatcher,
        IMShowRecordViewCallback {

    private static final String TAG = IMChatPanelView.class.getSimpleName();

    private static final int INPUTMANAGER_FLAG = 0x00;
    private static final long SHOW_KEYBOARD_DELAY = 200L;
    private static final long HIDE_KEYBOARD_DELAY = 50L;
    private static final int SHOW_KEYBOARD_WEIGHT = 1;
    private static final int MAX_INPUT_TEXT_LENGHT = 1024;

    // 软键盘显示和隐藏接口
    public interface OnChatPanelStateChangeCallback {
        // 软键盘显示和隐藏
        void softKeyboardState(boolean isShow);
    }

    private ViewTreeObserver.OnGlobalLayoutListener inputLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            if (mActivity == null) return;
            try {
                Rect r = new Rect();
                //获取当前界面可视部分
                mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
                //获取屏幕的高度
                int screenHeight = mActivity.getWindow().getDecorView().getRootView().getHeight();
                //此处就是用来获取键盘的高度的， 在键盘没有弹出的时候 此高度为0 键盘弹出的时候为一个正数
                int heightDifference = screenHeight - r.bottom;
                if (heightDifference > SHOW_KEYBOARD_DELAY) {
                    if (mChatPanelStateChangeCallback != null && !isKeyboardShow) {
                        mChatPanelStateChangeCallback.softKeyboardState(true);
                        isKeyboardShow = true;
                    }
                    if (EditSharedPreferences.getKeyboardHeight(mActivity) <= 0) {
                        getSoftKeyboardHeight();
                        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) getFragmentContainer().getLayoutParams();
                        layoutParams.height = getSoftKeyboardHeightLocal();
                        getFragmentContainer().requestLayout();
                    }
                } else {
                    // 当fragmentContainer显示的时候，不要执行执行该接口
                    if (mChatPanelStateChangeCallback != null && isKeyboardShow && !isFragmentContainerVisiable()) {
                        mChatPanelStateChangeCallback.softKeyboardState(false);
                        isKeyboardShow = false;
                    }
                }
            } catch (Exception ex) {
            }
        }
    };

    private Context mContext;
    private View mRoot;
    private AppCompatActivity mActivity;
    private Fragment mCurrentFragment;
    private Handler mHandler;
    private InputMethodManager mInputMethodManager;
    private IMChatSendMessageCallback mSendMsgCallback;
    private OnChatPanelStateChangeCallback mChatPanelStateChangeCallback;
    private View mPreView;// 上一个兄弟元素
    private IMRecordingStateChangeCallback mRecordingStateChangeCallback;

    private RelativeLayout rlVckExpandContainer;
    // 常用语
    private TextView tvVckbPhrase;
    // 输入框
    private EditText etVckbInput;
    // 表情按钮
    private CheckBox cbVckbSmily;
    // 扩展按钮
    private CheckBox cbVckbMore;
    // 发送按钮
    private Button btnVckbSendBtn;
    // 表情fragment
    private Fragment mSmilyFragment;
    // 更多fragment
    private Fragment mExpandFragment;
    // 语音fragment
    private Fragment mRecorderFragment;

    private boolean isNeedShowKeyBoard = true;
    private String selfUid;
    private String otherUid;
    private boolean isKeyboardShow = false;

    public IMChatPanelView(Context context) {
        super(context);
        initView(context);
    }

    public IMChatPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public IMChatPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    /**
     * ///////////////////////////////////////////////////////////初始化方法  结束///////////////////////////////////////////////////
     */
    private void initView(Context context) {
        try {
            this.mContext = context;
            if (context instanceof AppCompatActivity) {
                this.mActivity = (AppCompatActivity) context;
            }
            mRoot = LayoutInflater.from(mContext).inflate(R.layout.view_chat_panel, this);
            initEventBus();
            initLayoutByViewId(mRoot);
            initViewForListener();
            initData();
        } catch (Exception ex) {
        }
    }

    private void initEventBus() {
        try {
            if (!EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().register(this);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * 初始化页面控件
     */
    private void initLayoutByViewId(View root) {
        if (root == null) return;
        try {
            rlVckExpandContainer = root.findViewById(R.id.rlExpandContainer);
            tvVckbPhrase = root.findViewById(R.id.tvPhrase);
            etVckbInput = root.findViewById(R.id.etInputFeild);
            cbVckbSmily = root.findViewById(R.id.checkboxSmily);
            cbVckbMore = root.findViewById(R.id.checkboxMore);
            btnVckbSendBtn = root.findViewById(R.id.btnSendMsg);
        } catch (Exception ex) {
        }
    }

    /**
     * 初始化监听事件
     */
    private void initViewForListener() {
        tvVckbPhrase.setOnClickListener(this);
        btnVckbSendBtn.setOnClickListener(this);
        cbVckbSmily.setOnCheckedChangeListener(this);
        cbVckbMore.setOnCheckedChangeListener(this);
        etVckbInput.setOnTouchListener(this);
        etVckbInput.addTextChangedListener(this);
        if (etVckbInput == null) return;
        try {
            etVckbInput.getViewTreeObserver().addOnGlobalLayoutListener(inputLayoutListener);
        } catch (Exception ex) {
        }
    }

    /**
     * 初始化数据
     */
    private void initData() {

    }

    private void initInputEditTextData() {
        if (getEtVckbInput() == null) return;
    }
    /**
     * ///////////////////////////////////////////////////////////初始化方法  结束///////////////////////////////////////////////////
     */

    /**
     * /////////////////////////////////////////////////////////// get/set 方法  开始 ///////////////////////////////////////////////////
     */
    // 返回输入框
    private EditText getEtVckbInput() {
        return etVckbInput;
    }

    // 返回fragment容器
    private RelativeLayout getFragmentContainer() {
        return rlVckExpandContainer;
    }

    // 获取当前正在显示的fragment
    private Fragment getCurrentFragment() {
        return mCurrentFragment;
    }

    // 赋值当前正在显示的fragment
    private void setCurrentFragment(Fragment fragment) {
        if (fragment == null) return;
        mCurrentFragment = fragment;
    }

    // 获取当前表情按钮
    private CheckBox getSmilyCkeckBox() {
        return cbVckbSmily;
    }

    // 获取当前更多按钮
    private CheckBox getMoreCheckBox() {
        return cbVckbMore;
    }

    // 获取录音fragment
    private Fragment getRecorderFragment() {
        return mRecorderFragment;
    }

    // 设置录音fragment
    private void setRecoderFrgment(Fragment frgment) {
        mRecorderFragment = frgment;
    }

    // 获取表情Fragment
    private Fragment getSmilyFragment() {
        return mSmilyFragment;
    }

    // 设置表情fragment
    private void setSmilyFragment(Fragment fragment) {
        mSmilyFragment = fragment;
    }

    // 获取更多Fragment
    private Fragment getExpandFragment() {
        return mExpandFragment;
    }

    // 设置更多fragment
    private void setExpandFragment(Fragment fragment) {
        mExpandFragment = fragment;
    }

    // 获取发送按钮
    private Button getSendBtn() {
        return btnVckbSendBtn;
    }

    /**
     * /////////////////////////////////////////////////////////// get/set 方法  结束 ///////////////////////////////////////////////////
     */

    // 显示系统软键盘  根据输入框唤起系统软键盘
    private void showSoftKeyboard() {
        if (getEtVckbInput() == null || mInputMethodManager == null) return;
        try {
            getEtVckbInput().requestFocus();
            mInputMethodManager.showSoftInput(getEtVckbInput(), INPUTMANAGER_FLAG);
        } catch (Exception ex) {
        }
    }

    // 设置系统软键盘隐藏
    private void hideSoftKeyboard() {
        if (mInputMethodManager == null || getEtVckbInput() == null) return;
        try {
            mInputMethodManager.hideSoftInputFromWindow(getEtVckbInput().getWindowToken(), INPUTMANAGER_FLAG);
        } catch (Exception ex) {
        }
    }

    // 获取系统软键盘的高度，如果系统软键盘被唤起，则返回相应高度，如果没有被唤起，返回0
    private int getSoftKeyboardHeight() {
        if (mActivity == null) return 0;
        try {
            Rect rect = new Rect();
            mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
            int displayHeight = rect.bottom - rect.top;
            int screenHegith = ScreenUtil.getScreenHeight(mActivity);
            int statusHeight = ScreenUtil.getStatusBarHeight(mActivity);
            int softkeyboaryHeight = screenHegith - displayHeight - statusHeight;
            if (softkeyboaryHeight <= 0) return 0;
            if (EditSharedPreferences.getKeyboardHeight(mActivity) <= 0) {
                EditSharedPreferences.setKeyboardHeight(mActivity, softkeyboaryHeight);
            }
            return softkeyboaryHeight;
        } catch (Exception ex) {
        }
        return 0;
    }

    // 获取存储键盘的高度  如果有则直接返回，如果没有通过方法计算一下
    private int getSoftKeyboardHeightLocal() {
        return
                EditSharedPreferences.getKeyboardHeight(mActivity) <= 0 ?
                        ScreenUtil.getKeyboardInitHeight(mActivity) : EditSharedPreferences.getKeyboardHeight(mActivity);
    }

    // 计算当前系统软键盘是否已经显示
    private boolean isSoftKeyboardShown() {
        return getSoftKeyboardHeight() > 0;
    }


    // 判断fragment容器是否已经显示
    private boolean isFragmentContainerVisiable() {
        if (getFragmentContainer() == null) return false;
        return getFragmentContainer().getVisibility() == View.VISIBLE;
    }

    // 锁定上一个兄弟元素防止跳闪
    private void lockContentViewHeight() {
        if (mPreView == null) return;
        try {
            LinearLayout.LayoutParams layoutParams = (LayoutParams) mPreView.getLayoutParams();
            layoutParams.height = mPreView.getHeight();
            layoutParams.weight = 0;
            mPreView.requestLayout();
        } catch (Exception ex) {
        }
    }

    // 释放锁定的上一个兄弟元素
    private void unlockContentViewHeight() {
        if (mHandler == null || mPreView == null) return;
        try {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ((LayoutParams) mPreView.getLayoutParams()).weight = SHOW_KEYBOARD_WEIGHT;
                    mPreView.requestLayout();
                }
            }, SHOW_KEYBOARD_DELAY);
        } catch (Exception ex) {
        }
    }

    // 显示fragment 注意此处需要动态设置fragment容器的高度
    private void showFragment(Fragment fragment) {
        if (fragment == null || mActivity == null) return;
        try {
            FragmentManager fm = mActivity.getSupportFragmentManager();
            if (fm == null || fm.isDestroyed()) return;
            FragmentTransaction ft = fm.beginTransaction();
            if (ft == null) return;
            // 设置fragment容器的高度
            if (getFragmentContainer() == null) return;
            LinearLayout.LayoutParams layoutParams = (LayoutParams) getFragmentContainer().getLayoutParams();
            layoutParams.height = getSoftKeyboardHeightLocal();// 根据存储的系统软键盘高度设置fragment容器的高度
            getFragmentContainer().requestLayout();
            setFragmentContainerVisiable(true, false);
            if (getCurrentFragment() != null) {
                ft.hide(getCurrentFragment());
            }
            if (fragment.isAdded()) {
                ft.show(fragment).commitAllowingStateLoss();
            } else {
                ft.replace(R.id.rlExpandContainer, fragment, TAG).commitAllowingStateLoss();
            }
            setCurrentFragment(fragment);
            if (mChatPanelStateChangeCallback != null || !isKeyboardShow) {
                mChatPanelStateChangeCallback.softKeyboardState(true);
                isKeyboardShow = true;
            }
        } catch (Exception ex) {
        }
    }

    // 设置fragment容器是否可见
    private void setFragmentContainerVisiable(boolean isVisiable, boolean isChangeCheckBox) {
        if (getFragmentContainer() == null) return;
        getFragmentContainer().setVisibility(isVisiable ? VISIBLE : GONE);
        getFragmentContainer().requestLayout();
        try {
            if (!isChangeCheckBox) return;
            getSmilyCkeckBox().setChecked(false);
            getMoreCheckBox().setChecked(false);
        } catch (Exception ex) {
        }
    }

    // 初始化Fragment
    private void initFragments() {
        try {
            // 初始化SmilyFragment
            setSmilyFragment(IMChatKeyboardSmilyFragment.getInstance());
            if (getSmilyFragment() != null && getSmilyFragment() instanceof IMChatKeyboardSmilyFragment) {
                ((IMChatKeyboardSmilyFragment) getSmilyFragment()).setOnEmojiClickListener(this);
            }
            // 初始化ExpandFragment
            setExpandFragment(IMChatKeyboardExpandFragment.getInstance());
            if (getExpandFragment() != null && getExpandFragment() instanceof IMChatKeyboardExpandFragment) {
                ((IMChatKeyboardExpandFragment) getExpandFragment()).setShowRecorderUIListener(this, mActivity, mSendMsgCallback);
            }
            // 初始化RecorderFragment
            setRecoderFrgment(IMChatKeyboardRecorderFragment.getInstance());
            if (getRecorderFragment() != null && getRecorderFragment() instanceof IMChatKeyboardRecorderFragment) {
                ((IMChatKeyboardRecorderFragment) getRecorderFragment()).setData(mSendMsgCallback, mRecordingStateChangeCallback);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * ///////////////////////////////////////////////////////////// 按钮点击事件  开始 //////////////////////////////////////////////////
     */

    // 点击发送按钮
    private void clickSendMsg() {
        if (mSendMsgCallback == null || getEtVckbInput() == null) return;
        try {
            mSendMsgCallback.sendTextMessage(getEtVckbInput().getText().toString());
            // 发送之后将输入框内容清空
            getEtVckbInput().setText("");
        } catch (Exception ex) {
        }
    }

    /**
     * ///////////////////////////////////////////////////////////// 按钮点击事件  结束 //////////////////////////////////////////////////
     */

    /**
     * ///////////////////////////////////////////////////////////// 表情和更多按钮选中事件  开始 //////////////////////////////////////////////////
     */

    // 表情按钮 checked
    private void smilyCheckBoxChecked() {
        try {
            if (getSmilyFragment() != null) {
                // 如果键盘已经弹起 需要先固定keyboard的位置，之后，让软键盘消失，显示fragment
                if (isSoftKeyboardShown()) {
                    lockContentViewHeight();
                    hideSoftKeyboard();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showFragment(getSmilyFragment());
                            unlockContentViewHeight();
                        }
                    }, HIDE_KEYBOARD_DELAY);
                } else {
                    // 键盘没有弹起，直接显示fragment
                    showFragment(getSmilyFragment());
                }
                // 将更多 复选按钮设置为false
                if (getMoreCheckBox() != null && getMoreCheckBox().isChecked()) {
                    getMoreCheckBox().setChecked(false);
                }
            }
        } catch (Exception ex) {
        }
    }

    // 表情按钮 unchecked
    private void smilyCheckBoxUnchecked() {
        if (mHandler == null || getMoreCheckBox() == null || getMoreCheckBox().isChecked()) return;
        try {
            // 如果之前我们是点击了上面的区域而隐藏，则不需要设置键盘显示，其他情况显示键盘
            if (isNeedShowKeyBoard) {
                lockContentViewHeight();
                setFragmentContainerVisiable(false, false);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showSoftKeyboard();
                        unlockContentViewHeight();
                    }
                }, HIDE_KEYBOARD_DELAY);
            } else {
                setFragmentContainerVisiable(false, false);
            }
        } catch (Exception ex) {
        }
    }

    // 更多按钮 checked
    private void expandCheckBoxChecked() {
        if (getExpandFragment() == null || mHandler == null) return;
        try {
            if (isSoftKeyboardShown()) {
                // 需要先将自定义键盘的位置固定，然后设置系统软键盘消失，在设置fragment显示
                lockContentViewHeight();
                hideSoftKeyboard();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showFragment(getExpandFragment());
                        unlockContentViewHeight();
                    }
                }, HIDE_KEYBOARD_DELAY);
            } else {
                showFragment(getExpandFragment());
            }
            if (getSmilyCkeckBox() != null && getSmilyCkeckBox().isChecked()) {
                getSmilyCkeckBox().setChecked(false);
            }
        } catch (Exception ex) {
        }
    }

    // 更多按钮 unchecked
    private void expandCheckBoxUnchecked() {
        if (getSmilyCkeckBox() == null || mHandler == null || getSmilyCkeckBox().isChecked())
            return;
        try {
            if (isNeedShowKeyBoard) {
                lockContentViewHeight();
                setFragmentContainerVisiable(false, false);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showSoftKeyboard();
                        unlockContentViewHeight();
                    }
                }, HIDE_KEYBOARD_DELAY);
            } else {
                setFragmentContainerVisiable(false, false);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * ///////////////////////////////////////////////////////////// 表情和更多复选框选中事件  结束 //////////////////////////////////////////////////
     */


    /**
     * ///////////////////////////////////////////////////////接口需要实现的方法  开始////////////////////////////////////////////////////////
     */
    // 表情和更多按钮 切换check状态
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        int i = compoundButton.getId();
        if (i == R.id.checkboxSmily) {
            if (isChecked) smilyCheckBoxChecked();
            else smilyCheckBoxUnchecked();
            isNeedShowKeyBoard = true;
        } else if (i == R.id.checkboxMore) {
            if (isChecked) expandCheckBoxChecked();
            else expandCheckBoxUnchecked();
            isNeedShowKeyBoard = true;
        }
    }

    // 表情点击事件
    @Override
    public void onEmojiClick(int emojiType, String emojiName) {
        if (getEtVckbInput() == null) return;
        try {
            int curPosition = getEtVckbInput().getSelectionStart();
            StringBuilder sb = new StringBuilder(getEtVckbInput().getText().toString());
            if (TextUtils.equals(emojiName, UtilityExpression.EMOJI_TYPE_DELETE)) {
                // 删除图标，模拟键盘删除操作
                int keyCode = KeyEvent.KEYCODE_DEL;
                KeyEvent keyEventDown = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                KeyEvent keyEventUp = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
                getEtVckbInput().onKeyDown(keyCode, keyEventDown);
                getEtVckbInput().onKeyUp(keyCode, keyEventUp);
            } else {
                sb.insert(curPosition, emojiName);
                getEtVckbInput().setText(UtilityExpression.getEmojiContent(mContext, getEtVckbInput(), emojiType, sb.toString()));
                getEtVckbInput().setSelection(curPosition + emojiName.length());
            }
        } catch (Exception ex) {
        }
    }

    // 设置录音Fragment可见
    @Override
    public void showRecorderView() {
        if (getRecorderFragment() == null) return;
        showFragment(getRecorderFragment());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    // 监听输入框内容变化
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (getSendBtn() == null || getMoreCheckBox() == null || mActivity == null) return;
        // 如果内容发生改变，隐藏更多按钮，显示发送按钮
        if (TextUtils.isEmpty(s)) return;
        if (s.length() > MAX_INPUT_TEXT_LENGHT) {
            return;
        }
        try {
            if (!TextUtils.isEmpty(s)) {
                getSendBtn().setVisibility(VISIBLE);
                getMoreCheckBox().setVisibility(GONE);
            } else {
                getSendBtn().setVisibility(GONE);
                getMoreCheckBox().setVisibility(VISIBLE);
            }
        } catch (Exception ex) {
        }
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    // 触摸输入框 显示软键盘
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (getSmilyCkeckBox() == null || getMoreCheckBox() == null) return false;
        int i = v.getId();
        if (i == R.id.etInputFeild) {
            if (event.getAction() == MotionEvent.ACTION_UP && getFragmentContainer() != null && isFragmentContainerVisiable()) {
                setFragmentContainerVisiable(false, false);
                getSmilyCkeckBox().setChecked(false);
                getMoreCheckBox().setChecked(false);
            }

        }
        return false;
    }

    /**
     * ///////////////////////////////////////////////////////接口需要实现的方法  结束////////////////////////////////////////////////////////
     */

    /**
     * ////////////////////////////////////////////////////////// 向外部暴露的方法  开始 /////////////////////////////////////////////////////////////////////////////
     */
    /**
     * 外部使用-关闭软键盘
     * <p>
     * 使用场景，点击软键盘其他区域，隐藏软键盘
     * 滑动消息历史隐藏软键盘
     * 发送消息之后隐藏软键盘
     * 点击返回按钮隐藏软键盘
     * </p>
     */
    public void outterSetKeyboardHide() {
        try {
            isNeedShowKeyBoard = false;
            cbVckbMore.setChecked(false);
            cbVckbSmily.setChecked(false);
            setFragmentContainerVisiable(false, false);
            hideSoftKeyboard();
        } catch (Exception ex) {
        }
    }

    /**
     * 是否可以执行返回按钮。
     * <p>
     * 使用场景：点击返回按钮，判断当前软键盘是否已经收起，已经收起返回true，否则返回fasle
     * </p>
     *
     * @return true 可以直接执行返回操作，false 不能执行返回操作
     */
    public boolean outterKeyboardCanBackpress() {
        if (isSoftKeyboardShown() || isFragmentContainerVisiable()) {
            outterSetKeyboardHide();
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置Activity对象，为了获取当前的FragmentManager
     * <p>
     * 使用场景：设置IMChatKeyboardView的主要数据
     * </p>
     *
     * @param activity        当前Activity对象，需要使用该对象获取FragmentManager
     * @param contentView     需要传递的自定义键盘上一个兄弟元素
     * @param selfUserId      自己的id
     * @param otherSideUserID 对方的id
     * @param listener        自定义键盘点击回调接口
     */
    public void outterKeyboardSetData(AppCompatActivity activity, IMChatSendMessageCallback listener, IMRecordingStateChangeCallback stateChangeCallback, OnChatPanelStateChangeCallback keyboardStateChangeCallback, View contentView, String selfUserId, String otherSideUserID) {
        try {
            this.mActivity = activity;
            this.mSendMsgCallback = listener;
            this.mPreView = contentView;
            this.otherUid = otherSideUserID;
            this.selfUid = selfUserId;
            this.mRecordingStateChangeCallback = stateChangeCallback;
            this.mChatPanelStateChangeCallback = keyboardStateChangeCallback;
            this.mHandler = new Handler();
            this.mInputMethodManager = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
            this.mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            initFragments();
            // 初始化草稿
            initInputEditTextData();
        } catch (Exception ex) {
        }
    }

    /**
     * 保存输入框内容到数据库
     * <p>
     * 使用场景：当前聊天详情页面不可见时需要保存输入框内容到数据库
     * </p>
     */
    public void outterKeyboardSaveInputContentToSqlite() {
        try {
            EditText etInput = getEtVckbInput();
            if (etInput == null) return;
            String lastMsg = etInput.getText().toString();
        } catch (Exception ex) {
        }
    }

    /**
     * 获取expandFragment
     * <p>
     * 使用场景：获取语音权限之后显示语音录制页面
     * </p>
     */
    public Fragment getmExpandFragment() {
        return this.mExpandFragment;
    }

    /**
     * ////////////////////////////////////////////////////////// 向外部暴露的方法  结束 /////////////////////////////////////////////////////////////////////////////
     */


    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.tvPhrase) {
//            gotoPhrase();

        } else if (i == R.id.btnSendMsg) {
            clickSendMsg();

        }
    }


}
