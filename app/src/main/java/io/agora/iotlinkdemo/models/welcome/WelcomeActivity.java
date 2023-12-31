package io.agora.iotlinkdemo.models.welcome;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.agora.baselibrary.base.BaseDialog;
import com.agora.baselibrary.utils.SPUtil;

import io.agora.iotlink.ErrCode;
import io.agora.iotlinkdemo.R;
import io.agora.iotlinkdemo.base.AgoraApplication;
import io.agora.iotlinkdemo.base.BaseViewBindingActivity;
import io.agora.iotlinkdemo.base.PushApplication;
import io.agora.iotlinkdemo.common.Constant;
import io.agora.iotlinkdemo.databinding.ActivityWelcomeBinding;
import io.agora.iotlinkdemo.dialog.CommonDialog;
import io.agora.iotlinkdemo.dialog.UserAgreementDialog;
import io.agora.iotlinkdemo.models.home.MainActivity;
import io.agora.iotlinkdemo.models.login.AccountLoginActivity;
import io.agora.iotlinkdemo.presistentconnect.PresistentLinkComp;
import io.agora.iotlinkdemo.thirdpartyaccount.ThirdAccountMgr;
import io.agora.iotlinkdemo.utils.AppStorageUtil;


public class WelcomeActivity extends BaseViewBindingActivity<ActivityWelcomeBinding> {
    private static final String TAG = "IOTLINK/WelcomeAct";

    //
    // 界面流程状态
    //
    private static final int UI_STATE_IDLE = 0x0000;        ///< 空闲状态
    private static final int UI_STATE_INIT = 0x0001;        ///< 正在初始化
    private static final int UI_STATE_USRAGREE = 0x0002;    ///< 隐私协议
    private static final int UI_STATE_OVERLAYWND = 0x0003;  ///< 检测悬浮窗权限
    private static final int UI_STATE_LOGIN = 0x0004;       ///< 处理登录


    //
    // message Id
    //
    private static final int MSGID_ENGINE_INIT = 0x1001;       ///< 初始化离线推送和整个SDK
    private static final int MSGID_CHECK_USRAGREE = 0x1002;    ///< 检测隐私协议是否同意
    private static final int MSGID_CHECK_OVERLAYWND = 0x1003;  ///< 检测悬浮窗权限
    private static final int MSGID_HANDLE_LOGIN = 0x1004;      ///< 处理登录



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private UserAgreementDialog userAgreementDialog;    ///< 隐私协议对话框

    private Handler mMsgHandler = null;                 ///< 主线程中的消息处理
    private int mUiState = UI_STATE_IDLE;               ///< 当前UI处理状态机
    private boolean mOverlyWndSetted = false;           ///< 是否已经显示过悬浮窗设置
    private boolean mUserAgreePrivacy = true;           ///< 用户同意隐私协议



    ////////////////////////////////////////////////////////////////////////////
    ///////////////// Override BaseViewBindingActivity Methods /////////////////
    ////////////////////////////////////////////////////////////////////////////
     @Override
    protected ActivityWelcomeBinding getViewBinding(@NonNull LayoutInflater inflater) {
        return ActivityWelcomeBinding.inflate(inflater);
    }

    @Override
    public boolean isBlackDarkStatus() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "<onCreate> ==>Enter");

        // 创建主线程消息处理
        mMsgHandler = new Handler(getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            @RequiresApi(api = Build.VERSION_CODES.M)
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_ENGINE_INIT:
                        onMsgEngineInitialie();
                        break;

                    case MSGID_CHECK_USRAGREE:
                        onMsgCheckUserAgreement();
                        break;

                    case MSGID_CHECK_OVERLAYWND:
                        onMsgCheckOverlayWnd();
                        break;

                    case MSGID_HANDLE_LOGIN:
                        onMsgHandleLogin();
                        break;
                }
            }
        };

        // 开始初始化引擎
        mUiState = UI_STATE_IDLE;
        mMsgHandler.sendEmptyMessageDelayed(MSGID_ENGINE_INIT, 100);

        Log.d(TAG, "<onCreate> <==Exit");
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();


        if ((mUiState == UI_STATE_OVERLAYWND) && (mOverlyWndSetted)) {
            mMsgHandler.sendEmptyMessage(MSGID_HANDLE_LOGIN);
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();

        if (mMsgHandler != null) {
            mMsgHandler.removeMessages(MSGID_CHECK_USRAGREE);
            mMsgHandler.removeMessages(MSGID_CHECK_OVERLAYWND);
            mMsgHandler.removeMessages(MSGID_HANDLE_LOGIN);
            mMsgHandler = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!mUserAgreePrivacy) {
                Log.d(TAG, "<onKeyDown> Exit application");
                mHealthActivityManager.popAllActivity();
                System.exit(0);  // 直接退出应用
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    ////////////////////////////////////////////////////////////////////
    ////////////////// Methods for Engine Initialize ///////////////////
    ///////////////////////////////////////////////////////////////////
    void onMsgEngineInitialie() {
        Log.d(TAG, "<onMsgEngineInitialie> ==>Enter");
        // 初始化SDK引擎
        AgoraApplication appInstance = (AgoraApplication) getApplication();
        appInstance.initializeEngine();

        // 开始处理隐私协议
        mUiState = UI_STATE_USRAGREE;
        mMsgHandler.sendEmptyMessageDelayed(MSGID_CHECK_USRAGREE, 100);
        Log.d(TAG, "<onMsgEngineInitialie> <==Exit");
    }

    ////////////////////////////////////////////////////////////////////
    ////////////////// Methods for User Agreement /////////////////////
    ///////////////////////////////////////////////////////////////////
    void onMsgCheckUserAgreement() {
        mUiState = UI_STATE_USRAGREE;
        if (!SPUtil.Companion.getInstance(WelcomeActivity.this).getBoolean(Constant.IS_AGREE, false)) {
            showUserAgreementDialog();
        } else {
            mMsgHandler.sendEmptyMessage(MSGID_CHECK_OVERLAYWND);
        }
    }

    /**
     * @brief 显示用户协议 隐私政策对话框
     */
    private void showUserAgreementDialog() {
        if (userAgreementDialog == null) {
            userAgreementDialog = new UserAgreementDialog(this);
            userAgreementDialog.setOnButtonClickListener(new BaseDialog.OnButtonClickListener() {
                @Override
                public void onLeftButtonClick() {   // 不同意
                    userAgreementDialog.dismiss();
                    mUserAgreePrivacy = false;  // 用户不同意隐私协议
                }

                @Override
                public void onRightButtonClick() {  // 同意
                    userAgreementDialog.dismiss();
                    SPUtil.Companion.getInstance(WelcomeActivity.this).putBoolean(Constant.IS_AGREE, true);
                    mMsgHandler.sendEmptyMessage(MSGID_CHECK_OVERLAYWND);
                }
            });
        }
        userAgreementDialog.show();
    }

    //////////////////////////////////////////////////////////////////
    /////////////// Methods for Overlay Wnd Permission ///////////////
    //////////////////////////////////////////////////////////////////
    @RequiresApi(api = Build.VERSION_CODES.M)
    void onMsgCheckOverlayWnd() {
        // TODO: 悬浮窗权限放到登录后的主界面中进行申请，这里不做处理

//        mUiState = UI_STATE_OVERLAYWND;
//        mOverlyWndSetted = false;
//        if (!Settings.canDrawOverlays(this)) {
//            showRequestSuspensionDialog();
//        } else {
//            Log.d(TAG, "<onMsgCheckOverlayWnd> already have overlay permission");
//            mMsgHandler.sendEmptyMessage(MSGID_HANDLE_LOGIN);
//        }

        mMsgHandler.sendEmptyMessage(MSGID_HANDLE_LOGIN);
    }

    /**
     * @brief 获取悬浮窗权限提示
     */
    public void showRequestSuspensionDialog() {
        if (commonDialog == null) {
            commonDialog = new CommonDialog(this);
            commonDialog.setDialogTitle("请给软件设置悬浮窗权限，否则收不到被叫通知！");
            commonDialog.setDialogBtnText(getString(R.string.cancel), getString(R.string.confirm));
            commonDialog.setOnButtonClickListener(new BaseDialog.OnButtonClickListener() {
                @Override
                public void onLeftButtonClick() {
                    Log.d(TAG, "<showRequestSuspensionDialog> onLeftButtonClick");
                    mMsgHandler.sendEmptyMessage(MSGID_HANDLE_LOGIN);
                }

                @RequiresApi(api = Build.VERSION_CODES.M)
                @Override
                public void onRightButtonClick() {
                    Log.d(TAG, "<showRequestSuspensionDialog> onRightButtonClick");
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivity(intent);
                    mOverlyWndSetted = true;
                }
            });
        }
        commonDialog.show();
    }


    //////////////////////////////////////////////////////////////////
    //////////////////////// Methods for Login ///////////////////////
    //////////////////////////////////////////////////////////////////
    void onMsgHandleLogin() {
        mUiState = UI_STATE_LOGIN;

        int linkState = PresistentLinkComp.getInstance().getStateMachine();
        if (linkState == PresistentLinkComp.PRESISTENTLINK_STATE_RUNNING) {  // 当前账号已经登录，直接跳转到主界面
            Log.d(TAG, "<onMsgHandleLogin> account already login, goto main");
            gotoMainActivity();
            return;
        }

        String storedAccount = AppStorageUtil.safeGetString(this, Constant.ACCOUNT, null);
        String storedPassword = AppStorageUtil.safeGetString(this, Constant.PASSWORD, null);
        if (TextUtils.isEmpty(storedAccount) || TextUtils.isEmpty(storedPassword)) { // 没有历史登录信息
            Log.d(TAG, "<onMsgHandleLogin> No login history, goto loginActivity");
            gotoLoginActivity();
            return;
        }

        Bundle metaData = ((PushApplication)getApplication()).getMetaData();
        String appId = metaData.getString("AGORA_APPID", "");
        if (TextUtils.isEmpty(appId)) {
            Log.d(TAG, "<onMsgHandleLogin> fail to get appId, goto loginActivity");
            gotoLoginActivity();
            return;
        }

        // 开始进行登录操作
        PresistentLinkComp.PrepareParam prepareParam = new PresistentLinkComp.PrepareParam();
        prepareParam.mAppId = appId;
        prepareParam.mUserId = storedAccount;
        prepareParam.mClientType = 2;
        prepareParam.mPusherId = "None";  // 设置离线推送Id
        int ret = PresistentLinkComp.getInstance().prepare(prepareParam, new PresistentLinkComp.OnPrepareListener() {
            @Override
            public void onSdkPrepareDone(PresistentLinkComp.PrepareParam prepareParam, int errCode) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onCallbackLoginDone(errCode);
                    }
                });
            }
        });

        if (ret != ErrCode.XOK) {   // 登录失败，切换到登录界面
            Log.e(TAG, "<onMsgHandleLogin> fail to prepare(), ret=" + ret);
            gotoLoginActivity();
        }
    }

    void onCallbackLoginDone(final int errCode) {
        Log.d(TAG, "<onCallbackLoginDone> errCode=" + errCode);
        if (errCode == ErrCode.XOK) {        // 自动登录成功直接跳转到主界面
            gotoMainActivity();

        } else {   // 自动登录失败跳转到登录界面
            gotoLoginActivity();
        }
    }

    void gotoLoginActivity() {
        Intent intent = new Intent(WelcomeActivity.this, AccountLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
        startActivity(intent);
    }

    void gotoMainActivity() {
        Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // 清除Activity堆栈
        startActivity(intent);
    }

}
