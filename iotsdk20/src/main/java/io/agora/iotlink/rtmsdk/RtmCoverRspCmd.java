package io.agora.iotlink.rtmsdk;



/**
 * @brief 获取视频封面响应命令
 *
 */
public class RtmCoverRspCmd extends RtmBaseCmd  {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/RtmCoverRspCmd";



    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    public String mContentBase64;            ///< 封面图片内容的base64数据




    ///////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods  ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    @Override
    public String toString() {
        String infoText = "{ mSequenceId=" + mSequenceId
                + ", mDeviceId=" + mDeviceId
                + ", mCmdId=" + mCmdId
                + ", mContentBase64=" + mContentBase64
                + ", mIsRespCmd=" + mIsRespCmd
                + ", mErrCode=" + mErrCode + " }";
        return infoText;
    }



    ///////////////////////////////////////////////////////////////////////
    //////////////////// Override Methods of IRtmCmd //////////////////////
    ///////////////////////////////////////////////////////////////////////






}
