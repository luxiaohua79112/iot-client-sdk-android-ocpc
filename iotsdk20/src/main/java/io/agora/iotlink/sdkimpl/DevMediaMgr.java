/**
 * @file AccountMgr.java
 * @brief This file implement the call kit and RTC management
 *
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2023-05-19
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotlink.sdkimpl;



import android.util.Base64;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.agora.iotlink.ErrCode;
import io.agora.iotlink.IDevMediaMgr;
import io.agora.iotlink.IDeviceSessionMgr;
import io.agora.iotlink.IVodPlayer;
import io.agora.iotlink.base.AtomicInteger;
import io.agora.iotlink.base.AtomicUuid;
import io.agora.iotlink.base.BaseThreadComp;
import io.agora.iotlink.callkit.SessionCtx;
import io.agora.iotlink.logger.ALog;
import io.agora.iotlink.rtmsdk.DevFileDelErrInfo;
import io.agora.iotlink.rtmsdk.DevFileInfo;
import io.agora.iotlink.rtmsdk.IRtmCmd;
import io.agora.iotlink.rtmsdk.RtmBaseCmd;
import io.agora.iotlink.rtmsdk.RtmCmdSeqId;
import io.agora.iotlink.rtmsdk.RtmCoverReqCmd;
import io.agora.iotlink.rtmsdk.RtmCoverRspCmd;
import io.agora.iotlink.rtmsdk.RtmDeleteReqCmd;
import io.agora.iotlink.rtmsdk.RtmDeleteRspCmd;
import io.agora.iotlink.rtmsdk.RtmPlayReqCmd;
import io.agora.iotlink.rtmsdk.RtmPlayRspCmd;
import io.agora.iotlink.rtmsdk.RtmQueryReqCmd;
import io.agora.iotlink.rtmsdk.RtmQueryRspCmd;
import io.agora.iotlink.rtmsdk.RtmSpeedReqCmd;


/*
 * @brief 设备上媒体文件管理器
 */
public class DevMediaMgr implements IDevMediaMgr {


    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private static final String TAG = "IOTSDK/DevMediaMgr";


    //
    // The mesage Id
    //



    ////////////////////////////////////////////////////////////////////////
    //////////////////////// Variable Definition ///////////////////////////
    ////////////////////////////////////////////////////////////////////////
    private final Object mDataLock = new Object();    ///< 同步访问锁

    private UUID mDevSessionId;
    private DeviceSessionMgr mSessionMgr;
    private String mUserId;
    private String mDeviceId;

    private View mDisplayView;
    private DevPlayerChnlInfo mPlayChnlInfo = new DevPlayerChnlInfo();    ///< 设备播放器频道信息
    private AtomicUuid mPlaySessionId = new AtomicUuid();                 ///< 设备播放器的会话Id
    private AtomicInteger mPlayingState = new AtomicInteger();

    ///////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods  ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    public DevMediaMgr(final UUID sessionId, final DeviceSessionMgr sessionMgr) {
        mDevSessionId = sessionId;
        mSessionMgr = sessionMgr;
        IDeviceSessionMgr.SessionInfo sessionInfo = mSessionMgr.getSessionInfo(sessionId);
        mUserId = sessionInfo.mUserId;
        mDeviceId = sessionInfo.mPeerDevId;
        mPlayingState.setValue(DEVPLAYER_STATE_STOPPED);  // 停止播放状态
    }


    ///////////////////////////////////////////////////////////////////////
    ///////////////// Methods of Override IDevMediaMgr  ///////////////////
    ///////////////////////////////////////////////////////////////////////

    @Override
    public int queryMediaList(final QueryParam queryParam, final OnQueryListener queryListener) {
        RtmQueryReqCmd queryReqCmd = new RtmQueryReqCmd();
        queryReqCmd.mQueryParam.mFileId = queryParam.mFileId;
        queryReqCmd.mQueryParam.mBeginTime = queryParam.mBeginTimestamp;
        queryReqCmd.mQueryParam.mEndTime = queryParam.mEndTimestamp;
        queryReqCmd.mQueryParam.mPageIndex = queryParam.mPageIndex;
        queryReqCmd.mQueryParam.mPageSize = queryParam.mPageSize;

        queryReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        queryReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_QUERY;
        queryReqCmd.mDeviceId = mDeviceId;
        queryReqCmd.mSendTimestamp = System.currentTimeMillis();

        queryReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<queryMediaList.onRtmCmdResponsed> errCode=" + errCode);
                RtmQueryRspCmd queryRspCmd = (RtmQueryRspCmd)rspCmd;
                ArrayList<DevMediaItem> mediaList = new ArrayList<>();
                if (queryRspCmd != null) {
                    int count = queryRspCmd.mFileList.size();
                    for (int i = 0; i < count; i++) {
                        DevFileInfo fileInfo = queryRspCmd.mFileList.get(i);

                        DevMediaItem mediaItem = new DevMediaItem();
                        mediaItem.mFileId = fileInfo.mFileId;
                        mediaItem.mStartTimestamp = fileInfo.mStartTime;
                        mediaItem.mStopTimestamp = fileInfo.mStopTime;
                        mediaItem.mType = fileInfo.mFileType;
                        mediaItem.mEvent = fileInfo.mEvent;
                        mediaItem.mImgUrl = fileInfo.mImgUrl;
                        mediaItem.mVideoUrl = fileInfo.mVideoUrl;
                        mediaList.add(mediaItem);
                    }
                }

                queryListener.onDevMediaQueryDone(errCode, mediaList);
            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(queryReqCmd);

        ALog.getInstance().d(TAG, "<queryMediaList> done, ret=" + ret
                + ", queryReqCmd=" + queryReqCmd);
        return ret;
    }

    @Override
    public int deleteMediaList(final List<String> deletingList, final OnDeleteListener deleteListener) {
        RtmDeleteReqCmd deleteReqCmd = new RtmDeleteReqCmd();
        int deletingCount = deletingList.size();
        for (int i = 0; i < deletingCount; i++) {
            String fileId = deletingList.get(i);
            deleteReqCmd.mFileIdList.add(fileId);
        }

        deleteReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        deleteReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_DELETE;
        deleteReqCmd.mDeviceId = mDeviceId;
        deleteReqCmd.mSendTimestamp = System.currentTimeMillis();

        deleteReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<deleteMediaList.onRtmCmdResponsed> errCode=" + errCode);
                RtmDeleteRspCmd deleteRspCmd = (RtmDeleteRspCmd)rspCmd;
                ArrayList<DevMediaDelResult> delRsltList = new ArrayList<>();
                if (deleteRspCmd != null) {
                    int count = deleteRspCmd.mErrorList.size();
                    for (int i = 0; i < count; i++) {
                        DevFileDelErrInfo delErrInfo = deleteRspCmd.mErrorList.get(i);

                        DevMediaDelResult delResult = new DevMediaDelResult();
                        delResult.mFileId = delErrInfo.mFileId;
                        delResult.mErrCode = delErrInfo.mDelErrCode;
                        delRsltList.add(delResult);
                    }
                }

                deleteListener.onDevMediaDeleteDone(errCode, delRsltList);
            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(deleteReqCmd);

        ALog.getInstance().d(TAG, "<deleteMediaList> done, ret=" + ret
                + ", deleteReqCmd=" + deleteReqCmd);
        return ret;
    }


    @Override
    public int getMediaCoverData(final String imgUrl, final OnCoverDataListener coverDataListener) {
        RtmCoverReqCmd coverReqCmd = new RtmCoverReqCmd();
        coverReqCmd.mImgUrl = imgUrl;

        coverReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        coverReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_COVER;
        coverReqCmd.mDeviceId = mDeviceId;
        coverReqCmd.mSendTimestamp = System.currentTimeMillis();

        coverReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<getMediaCoverData.onRtmCmdResponsed> errCode=" + errCode);
                RtmCoverRspCmd coverRspCmd = (RtmCoverRspCmd)rspCmd;
                byte[] imgData = null;
                if ((coverRspCmd != null) && (coverRspCmd.mContentBase64 != null)) {
                    imgData = Base64.decode(coverRspCmd.mContentBase64, Base64.NO_WRAP);
                }
                coverDataListener.onDevMediaCoverDataDone(errCode, imgUrl, imgData);
            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(coverReqCmd);

        ALog.getInstance().d(TAG, "<getMediaCoverData> done, ret=" + ret
                + ", coverReqCmd=" + coverReqCmd);
        return ret;
    }




    @Override
    public int setDisplayView(final View displayView) {
        mDisplayView = displayView;
        return ErrCode.XOK;
    }

    @Override
    public int play(long globalStartTime, int playSpeed, final IPlayingCallback playingCallback) {
        int playingState = mPlayingState.getValue();
        if (playingState != DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<play> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        // 设置当前播放信息
        mPlayChnlInfo.setPlayingInfo(FILE_ID_GLOBALT_IMELINE, playingCallback);

        RtmPlayReqCmd playReqCmd = new RtmPlayReqCmd();
        playReqCmd.mGlobalStartTime = globalStartTime;
        playReqCmd.mRate = playSpeed;

        playReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        playReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_PLAY_TIMELINE;
        playReqCmd.mDeviceId = mDeviceId;
        playReqCmd.mSendTimestamp = System.currentTimeMillis();

        playReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                RtmPlayRspCmd playRspCmd = (RtmPlayRspCmd)rspCmd;
                int currState = mPlayingState.getValue();
                ALog.getInstance().d(TAG, "<play.onRtmCmdResponsed> errCode=" + errCode
                        + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_OPENING) {    // 不是正在打开状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_STOPPED);   // 状态机: 停止播放
                    if (playingCallback != null) {
                        playingCallback.onDevMediaOpenDone(null, errCode);
                    }
                    return;
                }

                // 进入RTC频道拉流
                int ret = RtcChnlEnter(playRspCmd.mRtcUid, playRspCmd.mChnlName, playRspCmd.mRtcToken, playRspCmd.mDevRtcUid);
                if (ret != ErrCode.XOK) {
                    // 加入频道失败时，返回媒体文件打开失败，同时发送停止播放命令给设备端
                    ALog.getInstance().e(TAG, "<play.onRtmCmdResponsed> fail to enter channel, ret=" + ret);

                    RtmBaseCmd stopReqCmd = new RtmBaseCmd();
                    stopReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
                    stopReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_STOP;
                    stopReqCmd.mDeviceId = mDeviceId;
                    stopReqCmd.mSendTimestamp = System.currentTimeMillis();
                    stopReqCmd.mRespListener = null;    // 不需要管设备端是否收到
                    mSessionMgr.getRtmMgrComp().sendCommandToDev(stopReqCmd);

                    mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_STOPPED);   // 状态机: 停止播放
                    if (playingCallback != null) {
                        playingCallback.onDevMediaOpenDone(FILE_ID_TIMELINE, ret);
                    }
                    return;
                }

                mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PLAYING); // 状态机: 正在播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaOpenDone(FILE_ID_TIMELINE, ErrCode.XOK);
                }
            }
        };

        mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_OPENING); // 状态机: 正在打开
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(playReqCmd);

        ALog.getInstance().d(TAG, "<play> done, ret=" + ret
                + ", playReqCmd=" + playReqCmd);
        return ret;
    }

    @Override
    public int play(final String fileId, long startPos, int playSpeed,
                    final IPlayingCallback playingCallback) {
        int playingState = mPlayingState.getValue();
        if (playingState != DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<play> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        // 设置当前播放信息
        mPlayChnlInfo.setPlayingInfo(fileId, playingCallback);

        RtmPlayReqCmd playReqCmd = new RtmPlayReqCmd();
        playReqCmd.mFileId = fileId;
        playReqCmd.mOffset = startPos;
        playReqCmd.mRate = playSpeed;

        playReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        playReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_PLAY_ID;
        playReqCmd.mDeviceId = mDeviceId;
        playReqCmd.mSendTimestamp = System.currentTimeMillis();

        playReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                RtmPlayRspCmd playRspCmd = (RtmPlayRspCmd)rspCmd;
                int currState = mPlayingState.getValue();
                ALog.getInstance().d(TAG, "<play.onRtmCmdResponsed> errCode=" + errCode
                                    + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_OPENING) {    // 不是正在打开状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_STOPPED);   // 状态机: 停止播放
                    if (playingCallback != null) {
                        playingCallback.onDevMediaOpenDone(fileId, errCode);
                    }
                    return;
                }

                // 进入RTC频道拉流
                int ret = RtcChnlEnter(playRspCmd.mRtcUid, playRspCmd.mChnlName, playRspCmd.mRtcToken, playRspCmd.mDevRtcUid);
                if (ret != ErrCode.XOK) {
                    // 加入频道失败时，返回媒体文件打开失败，同时发送停止播放命令给设备端
                    ALog.getInstance().e(TAG, "<play.onRtmCmdResponsed> fail to enter channel, ret=" + ret);

                    RtmBaseCmd stopReqCmd = new RtmBaseCmd();
                    stopReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
                    stopReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_STOP;
                    stopReqCmd.mDeviceId = mDeviceId;
                    stopReqCmd.mSendTimestamp = System.currentTimeMillis();
                    stopReqCmd.mRespListener = null;    // 不需要管设备端是否收到
                    mSessionMgr.getRtmMgrComp().sendCommandToDev(stopReqCmd);

                    mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_STOPPED);   // 状态机: 停止播放
                    if (playingCallback != null) {
                        playingCallback.onDevMediaOpenDone(fileId, ret);
                    }
                    return;
                }

                mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PLAYING); // 状态机: 正在播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaOpenDone(fileId, ErrCode.XOK);
                }
            }
        };

        mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_OPENING); // 状态机: 正在打开
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(playReqCmd);

        ALog.getInstance().d(TAG, "<play> done, ret=" + ret
                + ", playReqCmd=" + playReqCmd);
        return ret;
    }

    @Override
    public int stop() {
        int playingState = mPlayingState.getValue();
        if (playingState == DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<stop> bad state, already stopped!");
            return ErrCode.XERR_BAD_STATE;
        }

        RtmBaseCmd stopReqCmd = new RtmBaseCmd();
        stopReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        stopReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_STOP;
        stopReqCmd.mDeviceId = mDeviceId;
        stopReqCmd.mSendTimestamp = System.currentTimeMillis();
        stopReqCmd.mRespListener = null;    // 不需要管设备端是否收到
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(stopReqCmd);

        // 退出频道
        RtcChnlExit();

        mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_STOPPED); // 状态机: 停止播放

        ALog.getInstance().d(TAG, "<stop> done, ret=" + ret
                + ", stopReqCmd=" + stopReqCmd);
        return ret;
    }

    @Override
    public int resume() {
        int playingState = mPlayingState.getValue();
        if (playingState != DEVPLAYER_STATE_PAUSED) {
            ALog.getInstance().d(TAG, "<resume> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        RtmBaseCmd resumeReqCmd = new RtmBaseCmd();
        resumeReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        resumeReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_RESUME;
        resumeReqCmd.mDeviceId = mDeviceId;
        resumeReqCmd.mSendTimestamp = System.currentTimeMillis();

        resumeReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                int currState = mPlayingState.getValue();
                IPlayingCallback playingCallback = mPlayChnlInfo.getPlayingCallback();
                String fileId = mPlayChnlInfo.getPlayingFileId();
                ALog.getInstance().d(TAG, "<resume.onRtmCmdResponsed> errCode=" + errCode
                        + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_RESUMING) {    // 不是正在暂停状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    ALog.getInstance().e(TAG, "<resume.onRtmCmdResponsed> fail to resume!");
                    mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PAUSED);   // 状态机: 还原原先的暂停状态
                    if (playingCallback != null) {
                        playingCallback.onDevMediaResumeDone(fileId, errCode);
                    }
                    return;
                }

                mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PLAYING);   // 状态机: 正在播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaResumeDone(fileId, errCode);
                }
            }
        };

        mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_RESUMING); // 状态机: 正在恢复
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(resumeReqCmd);

        ALog.getInstance().d(TAG, "<resume> done, ret=" + ret
                + ", resumeReqCmd=" + resumeReqCmd);
        return ret;
    }

    @Override
    public int pause() {
        int playingState = mPlayingState.getValue();
        if (playingState != DEVPLAYER_STATE_PLAYING) {
            ALog.getInstance().d(TAG, "<pause> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        RtmBaseCmd pauseReqCmd = new RtmBaseCmd();
        pauseReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        pauseReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_PAUSE;
        pauseReqCmd.mDeviceId = mDeviceId;
        pauseReqCmd.mSendTimestamp = System.currentTimeMillis();

        pauseReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                int currState = mPlayingState.getValue();
                IPlayingCallback playingCallback = mPlayChnlInfo.getPlayingCallback();
                String fileId = mPlayChnlInfo.getPlayingFileId();
                ALog.getInstance().d(TAG, "<pause.onRtmCmdResponsed> errCode=" + errCode
                        + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_PAUSING) {    // 不是正在恢复状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    ALog.getInstance().e(TAG, "<pause.onRtmCmdResponsed> fail to pause!");
                    mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PLAYING);   // 状态机: 还原原先的播放状态
                    if (playingCallback != null) {
                        playingCallback.onDevMediaPauseDone(fileId, errCode);
                    }
                    return;
                }

                mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PAUSED);   // 状态机: 暂停播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaPauseDone(fileId, ErrCode.XOK);
                }
            }
        };

        mPlayingState.setValue(IDevMediaMgr.DEVPLAYER_STATE_PAUSING); // 状态机: 正在暂停
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(pauseReqCmd);

        ALog.getInstance().d(TAG, "<pause> done, ret=" + ret
                + ", pauseReqCmd=" + pauseReqCmd);
        return ret;
    }

    @Override
    public int seek(long seekPos) {
        return ErrCode.XERR_UNSUPPORTED;
    }

    @Override
    public int setPlayingSpeed(int speed) {
        int playingState = mPlayingState.getValue();
        if (playingState == DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<setPlayingSpeed> bad state, playing stopped!");
            return ErrCode.XERR_BAD_STATE;
        }

        RtmSpeedReqCmd speedReqCmd = new RtmSpeedReqCmd();
        speedReqCmd.mRate = speed;

        speedReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        speedReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_RATE;
        speedReqCmd.mDeviceId = mDeviceId;
        speedReqCmd.mSendTimestamp = System.currentTimeMillis();

        speedReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<setPlayingSpeed.onRtmCmdResponsed> errCode=" + errCode);

            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(speedReqCmd);

        ALog.getInstance().d(TAG, "<setPlayingSpeed> done, ret=" + ret
                + ", speedReqCmd=" + speedReqCmd);
        return ret;
    }


    @Override
    public long getPlayingProgress()  {
        return 0;
    }

    @Override
    public int getPlayingState() {
        int playingState = mPlayingState.getValue();
        return playingState;
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////// Methods of RtcEngine ///////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @brief 进行频道进行音视频拉流
     */
    int RtcChnlEnter(int rtcUid, final String chnlName, final String rtcToken, int devUid) {
        // 进入播放器频道
        mPlayChnlInfo.clear();
        mPlayChnlInfo.setInfo(mDeviceId, rtcUid, chnlName, rtcToken, devUid, mDisplayView, this);

        DeviceSessionMgr.DevPlayerChnlRslt result = mSessionMgr.devPlayerChnlEnter(mPlayChnlInfo);
        if (result.mErrCode == ErrCode.XOK) {
            mPlaySessionId.setValue(result.mSessionId);
        }

        return result.mErrCode;
    }

    /**
     * @brief 退出频道
     */
    int RtcChnlExit() {
        mSessionMgr.devPlayerChnlExit(mPlaySessionId.getValue());
        mPlaySessionId.setValue(null);
        mPlayChnlInfo.clear();

        ALog.getInstance().d(TAG, "<RtcChnlExit> done");
        return ErrCode.XOK;
    }

    /////////////////////////////////////////////////////////////////////////////
    //////////////////// TalkingEngine.ICallback 回调处理 ////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    public void onTalkingJoinDone(final UUID sessionId, final String channel, int uid) {
        ALog.getInstance().d(TAG, "<onTalkingJoinDone> sessionId=" + sessionId
                + ", channel=" + channel + ", uid=" + uid);
        UUID playSessionId = mPlaySessionId.getValue();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onTalkingJoinDone> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }

    public void onTalkingLeftDone(final UUID sessionId) {
        ALog.getInstance().d(TAG, "<onTalkingLeftDone> sessionId=" + sessionId);
        UUID playSessionId = mPlaySessionId.getValue();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onTalkingLeftDone> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }

    public void onUserOnline(final UUID sessionId, int uid, int elapsed) {
        ALog.getInstance().d(TAG, "<onUserOnline> sessionId=" + sessionId + ", uid=" + uid);
        UUID playSessionId = mPlaySessionId.getValue();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onUserOnline> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }

        // 设备端进入设备播放频道

    }

    public void onUserOffline(final UUID sessionId, int uid, int reason) {
        ALog.getInstance().d(TAG, "<onUserOffline> sessionId=" + sessionId
                + ", uid=" + uid + ", reason=" + reason);
        UUID playSessionId = mPlaySessionId.getValue();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onUserOffline> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }

        // 设备端退出设备播放频道
        IPlayingCallback playingCallback = mPlayChnlInfo.getPlayingCallback();
        String fileId = mPlayChnlInfo.getPlayingFileId();
        mPlayingState.setValue(DEVPLAYER_STATE_STOPPED);
        if (playingCallback != null) {
            playingCallback.onDevMediaPlayingDone(fileId);
        }
    }

    public void onPeerFirstVideoDecoded(final UUID sessionId, int peerUid, int videoWidth, int videoHeight) {
        ALog.getInstance().d(TAG, "<onPeerFirstVideoDecoded> sessionId=" + sessionId
                + ", peerUid=" + peerUid + ", videoWidth=" + videoWidth + ", videoHeight=" + videoHeight);
        UUID playSessionId = mPlaySessionId.getValue();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onPeerFirstVideoDecoded> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }

    public void onRecordingError(final UUID sessionId, int errCode) {
        ALog.getInstance().d(TAG, "<onRecordingError> sessionId=" + sessionId + ", errCode=" + errCode);
        UUID playSessionId = mPlaySessionId.getValue();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onRecordingError> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }



}
