package com.paulniu.panllibrary.imchat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.paulniu.panllibrary.R;
import com.paulniu.panllibrary.imchat.callbacks.IMRecordingStateChangeCallback;
import com.paulniu.panllibrary.imchat.events.OnSendVoiceEvent;
import com.paulniu.panllibrary.utils.UtilitySecurity;


/**
 * 发送语音UI的页面动画
 */
public class IMRecordingStateView extends RelativeLayout implements IMRecordingStateChangeCallback {

    private Context mContext;
    private View root;
    private ImageView ivVckrsCancel;
    private ImageView ivVckrsSending;
    private LinearLayout llVckrsCount;
    private TextView tvVckrsCount;

    public static int LIMIT_TIME = 60;
    public static int BEGIN_TIME = 51;

    public IMRecordingStateView(Context context) {
        super(context);
        initView(context);
    }

    public IMRecordingStateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }


    public IMRecordingStateView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        try {
            this.mContext = context;
            root = LayoutInflater.from(mContext).inflate(R.layout.view_chat_panel_record_state, this);
            initViewByFindId(root);
        } catch (Exception ex) {
        }
    }

    private void initViewByFindId(View view) {
        try {
            ivVckrsCancel = view.findViewById(R.id.ivCancel);
            ivVckrsSending = view.findViewById(R.id.ivSending);
            llVckrsCount = view.findViewById(R.id.llCount);
            tvVckrsCount = view.findViewById(R.id.tvCount);
        } catch (Exception ex) {
        }
    }

    /**
     * 根据不同的音量显示不同的图片
     *
     * @param event
     */
    private void showChangeVoiceImage(OnSendVoiceEvent event) {
        if (event == null || ivVckrsSending == null) {
            return;
        }
        switch (event.showImageType) {
            case OnSendVoiceEvent.SHOW_IMAGE_TYPE_1:
                UtilitySecurity.setImageResource(ivVckrsSending, R.drawable.icon_im_recorder_anim1);
                break;
            case OnSendVoiceEvent.SHOW_IMAGE_TYPE_2:
                UtilitySecurity.setImageResource(ivVckrsSending, R.drawable.icon_im_recorder_anim2);
                break;
            case OnSendVoiceEvent.SHOW_IMAGE_TYPE_3:
                UtilitySecurity.setImageResource(ivVckrsSending, R.drawable.icon_im_recorder_anim3);
                break;
            case OnSendVoiceEvent.SHOW_IMAGE_TYPE_4:
                UtilitySecurity.setImageResource(ivVckrsSending, R.drawable.icon_im_recorder_anim4);
                break;
            case OnSendVoiceEvent.SHOW_IMAGE_TYPE_5:
                UtilitySecurity.setImageResource(ivVckrsSending, R.drawable.icon_im_recorder_anim5);
                break;
        }
    }

    // 正在录制语音
    private void recording() {
        ivVckrsSending.setVisibility(VISIBLE);
        ivVckrsCancel.setVisibility(INVISIBLE);
        llVckrsCount.setVisibility(INVISIBLE);
    }

    // 录音倒计时
    private void recordingCount(OnSendVoiceEvent event) {
        try {
            if (!event.isMoreLimit) {
                // 如果距离小于200 显示内容
                ivVckrsSending.setVisibility(INVISIBLE);
                ivVckrsCancel.setVisibility(INVISIBLE);
                llVckrsCount.setVisibility(VISIBLE);
            }
            if (event.count + BEGIN_TIME == LIMIT_TIME && !event.isMoreLimit) {
                ivVckrsSending.setVisibility(INVISIBLE);
                ivVckrsCancel.setVisibility(INVISIBLE);
                llVckrsCount.setVisibility(VISIBLE);
            }
        } catch (Exception ex) {
        }
        // 根据传递过来的数字决定展示的内容
        tvVckrsCount.setText(String.valueOf(event.count));
    }

    // 取消录音
    private void recordCancel() {
        ivVckrsCancel.setVisibility(VISIBLE);
        ivVckrsSending.setVisibility(INVISIBLE);
        llVckrsCount.setVisibility(INVISIBLE);
    }

    // 录音结束
    private void recordStop() {
        ivVckrsCancel.setVisibility(INVISIBLE);
        ivVckrsSending.setVisibility(INVISIBLE);
        llVckrsCount.setVisibility(INVISIBLE);
    }

    // 语音回到倒计时状态 (正在倒计时，手指向上滑动，显示取消录音的样式)
    private void recordRebackCounting() {
        ivVckrsCancel.setVisibility(INVISIBLE);
        ivVckrsSending.setVisibility(INVISIBLE);
        llVckrsCount.setVisibility(VISIBLE);
    }

    @Override
    public void changeRecorderState(OnSendVoiceEvent event) {
        if (event == null) return;
        int type = event.status;
        // 根据不同的状态显示不同的样式
        switch (type) {
            case OnSendVoiceEvent.TYPE_SENDING:
                recording();
                break;
            case OnSendVoiceEvent.TYPE_COUNT:
                // 50s到60s之间显示倒计时消息
                // 确保第一次的时候展示，之后不需要再次强制展示
                recordingCount(event);
                break;
            case OnSendVoiceEvent.TYPE_CANCEL:
                // 取消发送，全部隐藏
                recordCancel();
                break;
            case OnSendVoiceEvent.TYPE_STOP:
                // 语音发送成功
                recordStop();
                break;
            case OnSendVoiceEvent.TYPE_REBACK_COUNTING:
                // 倒计时中，向上滑动之后又返回到原来的位置
                recordRebackCounting();
                break;
            case OnSendVoiceEvent.TYPE_CHANGE_VOICE:
                // 正在发送语音，根据音量显示不同的图片
                showChangeVoiceImage(event);
                break;
        }
    }
}
