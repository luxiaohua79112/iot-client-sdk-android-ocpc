package io.agora.iotlinkdemo.models.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.agora.baselibrary.base.BaseDialog;

import org.greenrobot.eventbus.EventBus;

import io.agora.iotlink.AIotAppSdkFactory;
import io.agora.iotlink.ErrCode;
import io.agora.iotlinkdemo.R;
import io.agora.iotlinkdemo.base.BaseViewBindingActivity;
import io.agora.iotlinkdemo.common.Constant;
import io.agora.iotlinkdemo.databinding.ActivityAccountSecurityBinding;
import io.agora.iotlinkdemo.dialog.CommonDialog;
import io.agora.iotlinkdemo.models.login.AccountLoginActivity;
import io.agora.iotlinkdemo.models.login.AccountRegisterActivity;
import io.agora.iotlinkdemo.presistentconnect.PresistentLinkComp;
import io.agora.iotlinkdemo.utils.AppStorageUtil;


/**
 * 账号安全
 */
public class AccountSecurityActivity extends BaseViewBindingActivity<ActivityAccountSecurityBinding> {


    private AccountSecurityActivity mActivity;

    @Override
    protected ActivityAccountSecurityBinding getViewBinding(@NonNull LayoutInflater inflater) {
        return ActivityAccountSecurityBinding.inflate(inflater);
    }

    @Override
    public void initView(@Nullable Bundle savedInstanceState) {
        mActivity = this;

        String localNodeId = PresistentLinkComp.getInstance().getLocalNodeId();
        getBinding().tvNodeId.setText(localNodeId);
    }

    @Override
    public void initListener() {

        getBinding().btnLogout.setOnClickListener(view -> {
            accountLogout();
        });
        getBinding().tvLogOff.setOnClickListener(view -> {
            accountUnregister();
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    /**
     * @brief 登出账号
     */
    public void accountLogout() {
        if (commonDialog == null) {
            commonDialog = new CommonDialog(this);
            commonDialog.setDialogTitle("确定要登出帐号吗？");
            commonDialog.setDialogBtnText(getString(R.string.cancel), getString(R.string.confirm));
            commonDialog.setOnButtonClickListener(new BaseDialog.OnButtonClickListener() {
                @Override
                public void onLeftButtonClick() {
                }

                @Override
                public void onRightButtonClick() {
                    // 进行登出操作
                    PresistentLinkComp.getInstance().unprepare();
                    AIotAppSdkFactory.getDevSessionMgr().release();
                    popupMessage("User account logout successful!");

                    AppStorageUtil.safePutString(mActivity, Constant.ACCOUNT, "");
                    gotoLoginActivity();
                }
            });
        }
        commonDialog.setCanceledOnTouchOutside(false);
        commonDialog.show();
    }

    /**
     * @brief 注销账号
     */
    public void accountUnregister() {
        popupMessage("当前不支持注销操作!");
    }


    void gotoLoginActivity() {
        Intent intent = new Intent(AccountSecurityActivity.this, AccountLoginActivity.class);
        startActivity(intent);
    }
}
