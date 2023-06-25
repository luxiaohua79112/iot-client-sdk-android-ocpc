package io.agora.iotlink.rtmsdk;


import android.content.Context;
import android.os.Message;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.agora.iotlink.ErrCode;
import io.agora.iotlink.IDeviceSessionMgr;
import io.agora.iotlink.base.AtomicBoolean;
import io.agora.iotlink.base.AtomicInteger;
import io.agora.iotlink.base.BaseThreadComp;
import io.agora.iotlink.callkit.SessionCtx;
import io.agora.iotlink.logger.ALog;
import io.agora.iotlink.sdkimpl.DeviceSessionMgr;
import io.agora.iotlink.utils.JsonUtils;
import io.agora.rtm.ErrorInfo;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmClient;
import io.agora.rtm.RtmClientListener;
import io.agora.rtm.RtmMessage;
import io.agora.rtm.RtmStatusCode;
import io.agora.rtm.SendMessageOptions;


/**
 * @brief RTM消息管理组件，有独立的运行线程
 */
public class RtmMgrComp extends BaseThreadComp {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/RtmMgrComp";
    private static final long COMMAND_TIMEOUT = 120000;  // 30000;   ///< 命令响应超时30秒
    private static final long TIMER_INTERVAL = 4000;                ///< 定时器间隔 4秒

    //
    // RTM的状态机
    //
    private static final int RTM_STATE_IDLE = 0x0000;               ///< 还未登录状态
    private static final int RTM_STATE_LOGINING = 0x0001;           ///< 正在登录中
    private static final int RTM_STATE_RENEWING = 0x0002;           ///< 正在RenewToken中
    private static final int RTM_STATE_LOGOUTING = 0x0003;          ///< 正在登出中
    private static final int RTM_STATE_RUNNING = 0x0004;            ///< 正常运行状态

    //
    // The message Id
    //
    private static final int MSGID_RTM_BASE = 0x2000;
    private static final int MSGID_RTM_SEND_PKT = 0x2001;           ///< 处理数据包接收
    private static final int MSGID_RTM_RECV_PKT = 0x2002;           ///< 处理数据包接收
    private static final int MSGID_RTM_CONNECT_DEV = 0x2003;        ///< 连接到设备
    private static final int MSGID_RTM_LOGIN_DONE = 0x2004;         ///< 登录完成消息
    private static final int MSGID_RTM_LOGOUT_DONE = 0x2005;        ///< 登出完成消息（暂时用不到）
    private static final int MSGID_RTM_RENEWTOKEN_DONE = 0x2006;    ///< token刷新完成消息
    private static final int MSGID_RTM_TIMER = 0x2009;              ///< 定时广播消息，防止无消息退出

    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final Object mDataLock = new Object();    ///< 同步访问锁,类中所有变量需要进行加锁处理
    private DeviceSessionMgr mSessionMgr;
    private RtmClient mRtmClient;                               ///< RTM客户端实例
    private AtomicInteger mState = new AtomicInteger();         ///< RTM状态机
    private SendMessageOptions mSendMsgOptions;                 ///< RTM消息配置

    private RtmPktQueue mRecvPktQueue = new RtmPktQueue();  ///< 接收数据包队列
    private RtmPktQueue mSendPktQueue = new RtmPktQueue();  ///< 发送数据包队列

    private RtmCmdMgr mReqCmdMgr = new RtmCmdMgr();        ///< 请求命令管理器


    ///////////////////////////////////////////////////////////////////////
    /////////////////////////// Public Methods ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    /**
     * @brief 初始化 RTM组件
     */
    public int initialize(final DeviceSessionMgr sessionMgr) {
        mSessionMgr = sessionMgr;

        mRecvPktQueue.clear();
        mSendPktQueue.clear();

        int ret = rtmEngCreate();
        if (ret != ErrCode.XOK) {
            return ret;
        }
        mState.setValue(RTM_STATE_IDLE);  // 未登录状态


        // 启动组件线程
        runStart(TAG);

        // 启动定时器消息
        sendSingleMessage(MSGID_RTM_TIMER, 0, 0, null, TIMER_INTERVAL);

        ALog.getInstance().d(TAG, "<initialize> done");
        return ErrCode.XOK;
    }

    /**
     * @brief 销毁 RTM组件
     */
    public void release() {
        // 停止组件线程
        runStop();

        rtmEngDestroy();
        mRecvPktQueue.clear();
        mSendPktQueue.clear();
        mState.setValue(RTM_STATE_IDLE);  // 未登录状态

        ALog.getInstance().d(TAG, "<release> done");
    }

    /**
     * @brief 连接到某个设备
     */
    public int connectToDevice(final String deviceId, final String rtmToken) {
        // 发送消息处理
        Object[] params = { deviceId, rtmToken};
        sendSingleMessage(MSGID_RTM_CONNECT_DEV, 0, 0, params, 0);

        ALog.getInstance().d(TAG, "<connectToDevice> deviceId=" + deviceId
                + ", rtmToken=" + rtmToken);
        return ErrCode.XOK;
    }


    /**
     * @brief 发送命令到设备
     */
    public int sendCommandToDev(final IRtmCmd command) {

        // 添加到命令处理器中
        mReqCmdMgr.addCommand(command);

        // 发送消息处理
        RtmPacket packet = new RtmPacket();
        packet.mPeerId = command.getDeviceId();
        packet.mPktData = command.getReqCmdData();
        mSendPktQueue.inqueue(packet);
        sendSingleMessage(MSGID_RTM_SEND_PKT, 0, 0, null, 0);

        ALog.getInstance().d(TAG, "<sendMessageToDev> command=" + command);
        return ErrCode.XOK;
    }


    ///////////////////////////////////////////////////////////////////////////
    //////////////// Methods for Override BaseThreadComp //////////////////////
    //////////////////////////////////////////////////////////////////////////
    @Override
    public void processWorkMessage(Message msg) {
        switch (msg.what) {
            case MSGID_RTM_SEND_PKT:
                onMessageSendPkt(msg);
                break;

            case MSGID_RTM_RECV_PKT:
                onMessageRecvPkt(msg);
                break;

            case MSGID_RTM_CONNECT_DEV:
                onMessageConnectToDev(msg);
                break;

            case MSGID_RTM_LOGIN_DONE:
                onMessageLoginDone(msg);
                break;

            case MSGID_RTM_RENEWTOKEN_DONE:
                onMessageRenewTokenDone(msg);
                break;

            case MSGID_RTM_TIMER:
                onMessageTimer(msg);
                break;
        }
    }

    @Override
    protected void removeAllMessages() {
        synchronized (mMsgQueueLock) {
            mWorkHandler.removeMessages(MSGID_RTM_SEND_PKT);
            mWorkHandler.removeMessages(MSGID_RTM_RECV_PKT);
            mWorkHandler.removeMessages(MSGID_RTM_CONNECT_DEV);
            mWorkHandler.removeMessages(MSGID_RTM_LOGIN_DONE);
            mWorkHandler.removeMessages(MSGID_RTM_RENEWTOKEN_DONE);
            mWorkHandler.removeMessages(MSGID_RTM_TIMER);
        }
        ALog.getInstance().d(TAG, "<removeAllMessages> done");
    }

    @Override
    protected void processTaskFinsh() {
        ALog.getInstance().d(TAG, "<processTaskFinsh> done");
    }


    /**
     * @brief 工作线程中运行，连接到设备
     */
    void onMessageConnectToDev(Message msg) {
        Object[] params = (Object[])msg.obj;
        String deviceId = (String)params[0];
        String rtmToken = (String)params[1];
        IDeviceSessionMgr.InitParam sessionMgrInitParam = mSessionMgr.getInitParam();

        int state = mState.getValue();
        if (state == RTM_STATE_IDLE) {  // RTM还没有进行登录
            mState.setValue(RTM_STATE_LOGINING);  // 切换到正在登录状态
            rtmEngLogin(rtmToken, sessionMgrInitParam.mUserId);
            ALog.getInstance().d(TAG, "<onMessageConnectToDev> done, login with token");

        } else {  // 已经登录，进行Token更新操作
            if (!TextUtils.isEmpty(rtmToken)) {
                mState.setValue(RTM_STATE_RENEWING);  // 切换到正在RenewToking状态
                rtmEngRenewToken(rtmToken);
                ALog.getInstance().d(TAG, "<onMessageConnectToDev> done, renew token");
            } else {
                ALog.getInstance().d(TAG, "<onMessageConnectToDev> done, need NOT renew token");
            }
        }
    }

    /**
     * @brief 工作线程中运行，登录完成
     */
    void onMessageLoginDone(Message msg) {
        int errCode = msg.arg1;

        if (errCode != ErrCode.XOK) {
            mState.setValue(RTM_STATE_IDLE);  // 登录失败，切换到 未登录状态

        } else {
            mState.setValue(RTM_STATE_RUNNING);  // 登录成功，切换到 运行状态
        }

        ALog.getInstance().d(TAG, "<onMessageLoginDone> done, errCode=" + errCode);
    }

    /**
     * @brief 工作线程中运行，RenewToken完成
     */
    void onMessageRenewTokenDone(Message msg) {
        int errCode = msg.arg1;

        if (errCode != ErrCode.XOK) {
            mState.setValue(RTM_STATE_RUNNING);  // Renew失败，切换到 运行状态

        } else {
            mState.setValue(RTM_STATE_RUNNING);  // Renew成功，切换到 运行状态
        }

        ALog.getInstance().d(TAG, "<onMessageRenewTokenDone> done, errCode=" + errCode);
    }

    /**
     * @brief 工作线程中运行，处理发送RTM数据包
     */
    void onMessageSendPkt(Message msg) {
        RtmPacket sendPkt = mSendPktQueue.dequeue();
        if (sendPkt == null) {  // 发送队列为空，没有必要处理发送消息了
            return;
        }

        // 发送数据包
        rtmEngSendData(sendPkt.mPeerId, sendPkt.mPktData);

        // 队列中还有数据包，放到下次发送消息中处理
        if (mSendPktQueue.size() > 0) {
            sendSingleMessage(MSGID_RTM_SEND_PKT, 0, 0, null, 0);
        }
    }

    /**
     * @brief 工作线程中运行，处理接收RTM数据包
     */
    void onMessageRecvPkt(Message msg) {
        for (;;) {
            RtmPacket recvedPkt = mRecvPktQueue.dequeue();
            if (recvedPkt == null) {  // 接收队列为空，没有要接收到的数据包要分发了
                return;
            }

            // 解析回应命令数据包,生成回应命令
            IRtmCmd responseCmd = parseRspCmdData(recvedPkt.mPeerId, recvedPkt.mPktData);
            if (responseCmd == null) {   // 回应命令解析失败
                ALog.getInstance().e(TAG, "<onMessageRecvPkt> fail to parse recved packet, mPeerId=" + recvedPkt.mPeerId);
                continue;
            }

            // 提取相同 sequenceId的请求命令
            long sequenceId = responseCmd.getSequenceId();
            int commandId = responseCmd.getCommandId();
            int errCode = responseCmd.getRespErrCode();
            IRtmCmd requestCmd = mReqCmdMgr.removeCommand(sequenceId);
            if (requestCmd == null) {   // 没有找到对应sequenceId的请求命令
                ALog.getInstance().e(TAG, "<onMessageRecvPkt> fail to distill request command, sequenceId=" + sequenceId);
                continue;
            }
            if (requestCmd.getCommandId() != commandId) {   // 校验命令Id
                ALog.getInstance().e(TAG, "<onMessageRecvPkt> error request commandId"
                        + ", respCmdId=" + commandId + ", reqCmdId=" + requestCmd.getCommandId());
                continue;
            }

            //
            // 回调上层，请求--响应结果
            //
            IRtmCmd.OnRtmCmdRespListener cmdRespListener = requestCmd.getRespListener();
            if (cmdRespListener != null) {
                cmdRespListener.onRtmCmdResponsed(commandId, errCode, requestCmd, responseCmd );
            }

        }
    }

    /**
     * @brief 工作线程中运行，定时处理消息
     */
    void onMessageTimer(Message msg) {

        List<IRtmCmd> timeoutCmdList = mReqCmdMgr.queryTimeoutCommandList(COMMAND_TIMEOUT);

        //
        // 处理响应超时的命令
        //
        for (IRtmCmd rtmCmd : timeoutCmdList) {
            mReqCmdMgr.removeCommand(rtmCmd.getSequenceId());   // 从命令管理器中删除改请求命令

            //
            // 回调上层，请求--响应超时
            //
            ALog.getInstance().d(TAG, "<onMessageTimer> callback command timeout, rtmCmd=" + rtmCmd);
            IRtmCmd.OnRtmCmdRespListener cmdRespListener = rtmCmd.getRespListener();
            if (cmdRespListener != null) {
                cmdRespListener.onRtmCmdResponsed(rtmCmd.getCommandId(), ErrCode.XERR_TIMEOUT, rtmCmd, null );
            }
        }

        // 下次定时器处理
        sendSingleMessage(MSGID_RTM_TIMER, 0, 0, null, TIMER_INTERVAL);
    }



    /**
     * @brief 工作线程中运行，解析数据包生成相应的ResponseCommand
     */
    IRtmCmd parseRspCmdData(final String deviceId, final String jsonText) {
        if (TextUtils.isEmpty(jsonText)) {
            ALog.getInstance().e(TAG, "<parseRspCmdData> fail to convert data bytes!");
            return null;
        }
        ALog.getInstance().d(TAG, "<parseRspCmdData> BEGIN, jsonText=" + jsonText);

        JSONObject recvJsonObj = JsonUtils.generateJsonObject(jsonText);
        if (recvJsonObj == null) {
            ALog.getInstance().e(TAG, "<parseRspCmdData> END, fail to convert JSON object!");
            return null;
        }

        // 解析基本的回应命令信息
        long sequenceId = JsonUtils.parseJsonLongValue(recvJsonObj, "sequenceId", -1);
        int commandId = JsonUtils.parseJsonIntValue(recvJsonObj, "commandId", -1);
        int errCode = JsonUtils.parseJsonIntValue(recvJsonObj, "code", 0);
        if (sequenceId < 0 || commandId < 0) {
            ALog.getInstance().e(TAG, "<parseRspCmdData> END, no sequenceId or commandId!");
            return null;
        }

        IRtmCmd responseCmd = null;
        switch (commandId) {
            case IRtmCmd.CMDID_MEDIA_QUERY: {       // 查询响应命令
                RtmQueryRspCmd queryRspCmd = new RtmQueryRspCmd();
                queryRspCmd.mSequenceId = sequenceId;
                queryRspCmd.mCmdId = commandId;
                queryRspCmd.mIsRespCmd = true;
                queryRspCmd.mErrCode = errCode;
                queryRspCmd.mDeviceId = deviceId;

                JSONObject respDataObj = JsonUtils.parseJsonObject(recvJsonObj, "data", null);
                if (respDataObj != null) {
                    JSONArray fileArrayObj = JsonUtils.parseJsonArray(respDataObj, "fileList");
                    if (fileArrayObj != null) {
                        for (int i = 0; i < fileArrayObj.length(); i++) {
                            JSONObject fileInfoObj =  JsonUtils.getJsonObjFromArray(fileArrayObj, i);
                            if (fileInfoObj == null) {
                                continue;
                            }

                            DevFileInfo fileInfo = new DevFileInfo();
                            fileInfo.mFileType = JsonUtils.parseJsonIntValue(fileInfoObj, "type", 0);
                            fileInfo.mFileId = JsonUtils.parseJsonStringValue(fileInfoObj, "fileId", null);
                            fileInfo.mEvent = JsonUtils.parseJsonIntValue(fileInfoObj, "event", 0);
                            fileInfo.mStartTime = JsonUtils.parseJsonLongValue(fileInfoObj, "startTime", -1);
                            fileInfo.mStopTime = JsonUtils.parseJsonLongValue(fileInfoObj, "stopTime", -1);
                            fileInfo.mImgUrl = JsonUtils.parseJsonStringValue(fileInfoObj, "imgUrl", null);
                            fileInfo.mVideoUrl = JsonUtils.parseJsonStringValue(fileInfoObj, "videoUrl", null);
                            queryRspCmd.mFileList.add(fileInfo);
                        }
                    }
                }
                responseCmd = queryRspCmd;
            } break;

            case IRtmCmd.CMDID_MEDIA_COVER: {       // 封面图片响应命令

            } break;

            case IRtmCmd.CMDID_MEDIA_DELETE: {      // 删除响应命令
                RtmDeleteRspCmd deleteRspCmd = new RtmDeleteRspCmd();
                deleteRspCmd.mSequenceId = sequenceId;
                deleteRspCmd.mCmdId = commandId;
                deleteRspCmd.mIsRespCmd = true;
                deleteRspCmd.mErrCode = errCode;
                deleteRspCmd.mDeviceId = deviceId;

                JSONObject respDataObj = JsonUtils.parseJsonObject(recvJsonObj, "data", null);
                if (respDataObj != null) {
                    JSONArray undelArrayObj = JsonUtils.parseJsonArray(respDataObj, "undeleteList");
                    if (undelArrayObj != null) {
                        for (int i = 0; i < undelArrayObj.length(); i++) {
                            JSONObject undelItemObj =  JsonUtils.getJsonObjFromArray(undelArrayObj, i);
                            if (undelItemObj == null) {
                                continue;
                            }

                            DevFileDelErrInfo errInfo = new DevFileDelErrInfo();
                            errInfo.mDelErrCode = JsonUtils.parseJsonIntValue(undelItemObj, "error", 0);
                            errInfo.mFileId = JsonUtils.parseJsonStringValue(undelItemObj, "fileId", null);
                            deleteRspCmd.mErrorList.add(errInfo);
                        }
                    }
                }
                responseCmd = deleteRspCmd;
            } break;

            case IRtmCmd.CMDID_CUSTOMIZE_SEND: {    // 定制化响应命令
                RtmCustomizeRspCmd customizeRspCmd = new RtmCustomizeRspCmd();
                customizeRspCmd.mSequenceId = sequenceId;
                customizeRspCmd.mCmdId = commandId;
                customizeRspCmd.mIsRespCmd = true;
                customizeRspCmd.mErrCode = errCode;
                customizeRspCmd.mDeviceId = deviceId;

                JSONObject respDataObj = JsonUtils.parseJsonObject(recvJsonObj, "data", null);
                if (respDataObj != null) {
                    customizeRspCmd.mRecvData = JsonUtils.parseJsonStringValue(respDataObj, "recvData", null);
                }
                responseCmd = customizeRspCmd;
            } break;

            default: {  // 其他响应命令，都不需要响应数据
                RtmBaseCmd baseCmd = new RtmBaseCmd();
                baseCmd.mSequenceId = sequenceId;
                baseCmd.mCmdId = commandId;
                baseCmd.mIsRespCmd = true;
                baseCmd.mErrCode = errCode;
                baseCmd.mDeviceId = deviceId;
                responseCmd = baseCmd;
            } break;


        }

        ALog.getInstance().d(TAG, "<parseRspCmdData> END, responseCmd=" + responseCmd);
        return responseCmd;
    }


    ///////////////////////////////////////////////////////////////////////////
    ////////////////////////////// Methods for Rtm SDK ////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @brief 初始化 RtmSdk
     */
    private int rtmEngCreate() {
        mSendMsgOptions = new SendMessageOptions();

        RtmClientListener rtmListener = new RtmClientListener() {
            @Override
            public void onConnectionStateChanged(int state, int reason) {   //连接状态改变
                ALog.getInstance().d(TAG, "<rtmEngCreate.onConnectionStateChanged> state=" + state
                        + ", reason=" + reason);
            }

            @Override
            public void onMessageReceived(RtmMessage rtmMessage, String peerId) {   // 收到RTM消息
                ALog.getInstance().d(TAG, "<rtmEngCreate.onMessageReceived> rtmMessage=" + rtmMessage.getText()
                        + ", peerId=" + peerId);

                RtmPacket packet = new RtmPacket();
                packet.mPeerId = peerId;
                packet.mPktData = rtmMessage.getText();
                mRecvPktQueue.inqueue(packet);
                sendSingleMessage(MSGID_RTM_RECV_PKT, 0, 0, null, 0);
            }

            @Override
            public void onTokenExpired() {
                ALog.getInstance().d(TAG, "<rtmEngCreate.onTokenExpired>");
            }

            @Override
            public void onTokenPrivilegeWillExpire() {
                ALog.getInstance().d(TAG, "<rtmEngCreate.onTokenPrivilegeWillExpire>");
            }

            @Override
            public void onPeersOnlineStatusChanged(Map<String, Integer> peersStatus) {
                ALog.getInstance().d(TAG, "<rtmEngCreate.onPeersOnlineStatusChanged> peersStatus=" + peersStatus);
            }
        };

        try {
            IDeviceSessionMgr.InitParam initParam = mSessionMgr.getInitParam();
            String appId = initParam.mAppId;
            mRtmClient = RtmClient.createInstance(initParam.mContext, appId, rtmListener);
        } catch (Exception exp) {
            exp.printStackTrace();
            ALog.getInstance().e(TAG, "<rtmEngCreate> [EXCEPTION] create rtmp, exp=" + exp.toString());
            return ErrCode.XERR_UNSUPPORTED;
        }

        ALog.getInstance().d(TAG, "<rtmEngCreate> done");
        return ErrCode.XOK;
    }

    /**
     * @brief 释放 RtmSDK
     */
    private void rtmEngDestroy()
    {
        if (mRtmClient != null) {
            mRtmClient.release();
            mRtmClient = null;
            ALog.getInstance().d(TAG, "<rtmEngDestroy> done");
        }
    }


    /**
     * @brief 登录用户账号
     */
    private int rtmEngLogin(final String token, final String userId)
    {
        if (mRtmClient == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        mRtmClient.login(token, userId, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void responseInfo) {
                ALog.getInstance().d(TAG, "<rtmEngLogin.onSuccess> success");
                sendSingleMessage(MSGID_RTM_LOGIN_DONE, ErrCode.XOK, 0, null, 0);
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                ALog.getInstance().i(TAG, "<rtmEngLogin.onFailure> failure"
                        + ", errInfo=" + errorInfo.getErrorCode()
                        + ", errDesc=" + errorInfo.getErrorDescription());
                int errCode = mapErrCode(errorInfo.getErrorCode());
                sendSingleMessage(MSGID_RTM_LOGIN_DONE, errCode, 0, null, 0);
            }
        });

        ALog.getInstance().d(TAG, "<rtmEngLogin> done");
        return ErrCode.XOK;
    }


    /**
     * @brief 登出用户账号
     */
    private int rtmEngLogout()
    {
        if (mRtmClient == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        mRtmClient.logout( new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void responseInfo) {
                ALog.getInstance().d(TAG, "<rtmEngLogout.onSuccess> success");
                sendSingleMessage(MSGID_RTM_LOGOUT_DONE, ErrCode.XOK, 0, null, 0);
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                ALog.getInstance().i(TAG, "<rtmEngLogout.onFailure> failure"
                        + ", errInfo=" + errorInfo.getErrorCode()
                        + ", errDesc=" + errorInfo.getErrorDescription());
                int errCode = mapErrCode(errorInfo.getErrorCode());
                sendSingleMessage(MSGID_RTM_LOGOUT_DONE, errCode, 0, null, 0);
            }
        });

        ALog.getInstance().d(TAG, "<rtmEngLogout> done");
        return ErrCode.XOK;
    }


    /**
     * @brief 更新token
     */
    private int rtmEngRenewToken(final String token)
    {
        if (mRtmClient == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        mRtmClient.renewToken(token, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void responseInfo) {
                ALog.getInstance().d(TAG, "<rtmEngRenewToken.onSuccess> success");
                sendSingleMessage(MSGID_RTM_RENEWTOKEN_DONE, ErrCode.XOK, 0, null, 0);
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                ALog.getInstance().i(TAG, "<rtmEngRenewToken.onFailure> failure"
                        + ", errInfo=" + errorInfo.getErrorCode()
                        + ", errDesc=" + errorInfo.getErrorDescription());
                int errCode = mapErrCode(errorInfo.getErrorCode());
                sendSingleMessage(MSGID_RTM_RENEWTOKEN_DONE, errCode, 0, null, 0);
            }
        });

        ALog.getInstance().d(TAG, "<rtmEngRenewToken> done");
        return ErrCode.XOK;
    }


    /**
     * @brief 发送消息到对端
     */
    private int rtmEngSendData(final String peerId, final String messageData) {
        if (mRtmClient == null) {
            return ErrCode.XERR_BAD_STATE;
        }

        RtmMessage rtmMsg = mRtmClient.createMessage(messageData);
        mRtmClient.sendMessageToPeer(peerId, rtmMsg, mSendMsgOptions, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                ALog.getInstance().d(TAG, "<rtmEngSendData.onSuccess>");

            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                ALog.getInstance().i(TAG, "<rtmEngSendData.onFailure> failure"
                        + ", errInfo=" + errorInfo.getErrorCode()
                        + ", errDesc=" + errorInfo.getErrorDescription());
                int errCode = mapErrCode(errorInfo.getErrorCode());

            }
        });

        ALog.getInstance().d(TAG, "<sendMessage> done, messageData=" + messageData);
        return ErrCode.XOK;
    }




    ///////////////////////////////////////////////////////////////////////////
    ////////////////////// Methods for Mapping Error Code /////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @brief 映射RTM的错误码到全局统一的错误码
     */
    private int mapErrCode(int rtmErrCode) {
        switch (rtmErrCode) {
            case RtmStatusCode.LoginError.LOGIN_ERR_UNKNOWN:
                return ErrCode.XERR_UNKNOWN;

            case RtmStatusCode.LoginError.LOGIN_ERR_REJECTED:
                return ErrCode.XERR_RTMMGR_REJECT;

            case RtmStatusCode.LoginError.LOGIN_ERR_INVALID_ARGUMENT:
                return ErrCode.XERR_INVALID_PARAM;

            case RtmStatusCode.LoginError.LOGIN_ERR_INVALID_APP_ID:
                return ErrCode.XERR_APPID_INVALID;

            case RtmStatusCode.LoginError.LOGIN_ERR_INVALID_TOKEN:
                return ErrCode.XERR_TOKEN_INVALID;

            case RtmStatusCode.LoginError.LOGIN_ERR_TOKEN_EXPIRED:
                return ErrCode.XERR_TOKEN_INVALID;

            case RtmStatusCode.LoginError.LOGIN_ERR_NOT_AUTHORIZED:
                return ErrCode.XERR_NOT_AUTHORIZED;

            case RtmStatusCode.LoginError.LOGIN_ERR_ALREADY_LOGIN:
                return ErrCode.XERR_RTMMGR_ALREADY_CONNECTED;

            case RtmStatusCode.LoginError.LOGIN_ERR_TIMEOUT:
                return ErrCode.XERR_TIMEOUT;

            case RtmStatusCode.LoginError.LOGIN_ERR_TOO_OFTEN:
                return ErrCode.XERR_INVOKE_TOO_OFTEN;

            case RtmStatusCode.LoginError.LOGIN_ERR_NOT_INITIALIZED:
                return ErrCode.XERR_BAD_STATE;
        }

        return ErrCode.XOK;
    }



    /**
     * @brief 映射RTM的消息错误码到全局统一的错误码
     */
    private int mapRtmMsgErrCode(int msgErrCode) {
        switch (msgErrCode) {
            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_FAILURE:
                return ErrCode.XERR_RTMMGR_MSG_FAILURE;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_TIMEOUT:
                return ErrCode.XERR_RTMMGR_MSG_TIMEOUT;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_PEER_UNREACHABLE:
                return ErrCode.XERR_RTMMGR_MSG_UNREACHABLE;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_CACHED_BY_SERVER:
                return ErrCode.XERR_RTMMGR_MSG_CACHED_BY_SERVER;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_TOO_OFTEN:
                return ErrCode.XERR_RTMMGR_MSG_TOO_OFTEN;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_INVALID_USERID:
                return ErrCode.XERR_RTMMGR_USERID_INVALID;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_INVALID_MESSAGE:
                return ErrCode.XERR_RTMMGR_MSG_INVALID;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_IMCOMPATIBLE_MESSAGE:
                return ErrCode.XERR_RTMMGR_MSG_IMCOMPATIBLE;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_NOT_INITIALIZED:
                return ErrCode.XERR_BAD_STATE;

            case RtmStatusCode.PeerMessageError.PEER_MESSAGE_ERR_USER_NOT_LOGGED_IN:
                return ErrCode.XERR_BAD_STATE;
        }

        return ErrCode.XOK;
    }

}
