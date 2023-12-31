package io.agora.iotlink.rtmsdk;


import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.agora.iotlink.ErrCode;
import io.agora.iotlink.logger.ALog;
import io.agora.iotlink.utils.JsonUtils;


/**
 * @brief 设备端文件删除失败信息
 *
 */
public class DevFileDelErrInfo  {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/DevFileDelErrInfo";


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    public String mFileId;          ///< 文件Id，是文件的唯一标识
    public int mDelErrCode;         ///< 删除失败错误码
    public String mFileName;        ///< 删除的文件名

    ///////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods  ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    @Override
    public String toString() {
        String infoText = "{ mFileId=" + mFileId
                + ", mDelErrCode=" + mDelErrCode + ", mFileName=" + mFileName + " }";
        return infoText;
    }

}
