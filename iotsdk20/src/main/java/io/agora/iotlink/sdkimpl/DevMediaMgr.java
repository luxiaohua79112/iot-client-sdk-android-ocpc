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
import io.agora.iotlink.base.AtomicInteger;
import io.agora.iotlink.base.AtomicLong;
import io.agora.iotlink.base.AtomicUuid;
import io.agora.iotlink.logger.ALog;
import io.agora.iotlink.rtmsdk.DevFileDelErrInfo;
import io.agora.iotlink.rtmsdk.IRtmCmd;
import io.agora.iotlink.rtmsdk.RtmBaseCmd;
import io.agora.iotlink.rtmsdk.RtmCmdSeqId;
import io.agora.iotlink.rtmsdk.RtmCoverReqCmd;
import io.agora.iotlink.rtmsdk.RtmCoverRspCmd;
import io.agora.iotlink.rtmsdk.RtmDeleteReqCmd;
import io.agora.iotlink.rtmsdk.RtmDeleteRspCmd;
import io.agora.iotlink.rtmsdk.RtmDownloadReqCmd;
import io.agora.iotlink.rtmsdk.RtmDownloadRspCmd;
import io.agora.iotlink.rtmsdk.RtmEventTimelineRspCmd;
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
    private UUID mDevSessionId;
    private DeviceSessionMgr mSessionMgr;
    private String mDeviceId;

    private View mDisplayView;      ///< 播放显示控件
    private DevPlayingInfo mPlayingInfo = new DevPlayingInfo();     ///< 当前播放状态信息


    ///////////////////////////////////////////////////////////////////////
    ////////////////////////// Public Methods  ////////////////////////////
    ///////////////////////////////////////////////////////////////////////
    public DevMediaMgr(final UUID sessionId, final DeviceSessionMgr sessionMgr) {
        mDevSessionId = sessionId;
        mSessionMgr = sessionMgr;
        IDeviceSessionMgr.SessionInfo sessionInfo = mSessionMgr.getSessionInfo(sessionId);
        mDeviceId = sessionInfo.mPeerDevId;
        mPlayingInfo.reset();
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

        queryReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        queryReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_QUERY;
        queryReqCmd.mDeviceId = mDeviceId;
        queryReqCmd.mSendTimestamp = System.currentTimeMillis();

        queryReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<queryMediaList.onRtmCmdResponsed> errCode=" + errCode);
                RtmQueryRspCmd queryRspCmd = (RtmQueryRspCmd)rspCmd;
                ArrayList<IDevMediaMgr.DevMediaItem> mediaList = new ArrayList<>();
                if (queryRspCmd != null) {
                    mediaList = queryRspCmd.mMediaList;
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
    public int downloadFileList(final List<String> downloadList, final OnDownloadListener downloadListener) {
        RtmDownloadReqCmd downloadReqCmd = new RtmDownloadReqCmd();
        int downloadingCount = downloadList.size();
        for (int i = 0; i < downloadingCount; i++) {
            String fileId = downloadList.get(i);
            downloadReqCmd.mFileIdList.add(fileId);
        }

        downloadReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        downloadReqCmd.mCmdId = IRtmCmd.CMDID_FILE_DOWNLOAD;
        downloadReqCmd.mDeviceId = mDeviceId;
        downloadReqCmd.mSendTimestamp = System.currentTimeMillis();

        downloadReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<downloadFileList.onRtmCmdResponsed> errCode=" + errCode);

                RtmDownloadRspCmd downloadRspCmd = (RtmDownloadRspCmd)rspCmd;
                ArrayList<DevFileDownloadResult> dnloadRsltList = new ArrayList<>();
                if (downloadRspCmd != null) {
                    dnloadRsltList = downloadRspCmd.mDownloadList;
                }
                downloadListener.onDevFileDownloadDone(errCode, dnloadRsltList);
            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(downloadReqCmd);

        ALog.getInstance().d(TAG, "<downloadFileList> done, ret=" + ret
                + ", downloadReqCmd=" + downloadReqCmd);
        return ret;
    }


    @Override
    public int queryEventTimeline(final OnQueryEventListener queryListener) {
        RtmBaseCmd eventReqCmd = new RtmBaseCmd();

        eventReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        eventReqCmd.mCmdId = IRtmCmd.CMDID_EVENTTIMELINE_QUERY;
        eventReqCmd.mDeviceId = mDeviceId;
        eventReqCmd.mSendTimestamp = System.currentTimeMillis();

        eventReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                ALog.getInstance().d(TAG, "<queryEventTimeline.onRtmCmdResponsed> errCode=" + errCode);

                RtmEventTimelineRspCmd eventRspCmd = (RtmEventTimelineRspCmd)rspCmd;
                ArrayList<Long> videoTimeList = new ArrayList<>();
                if (eventRspCmd != null) {
                    videoTimeList = eventRspCmd.mVideoTimeList;
                }
                queryListener.onDevQueryEventDone(errCode, videoTimeList);
            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(eventReqCmd);

        ALog.getInstance().d(TAG, "<queryEventTimeline> done, ret=" + ret
                + ", eventReqCmd=" + eventReqCmd);
        return ret;
    }

    @Override
    public int setDisplayView(final View displayView) {
        mDisplayView = displayView;
        return ErrCode.XOK;
    }

    @Override
    public int play(long globalStartTime, int playSpeed, final IPlayingCallback playingCallback) {
        int playingState = mPlayingInfo.getPlayingState();
        if (playingState != DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<play> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        // 设置当前播放文件信息
        mPlayingInfo.setPlayFileInfo(FILE_ID_GLOBALT_IMELINE, playingCallback);

        RtmPlayReqCmd playReqCmd = new RtmPlayReqCmd();
        playReqCmd.mGlobalStartTime = globalStartTime;
        playReqCmd.mRate = playSpeed;

        playReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        playReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_PLAY_TIMELINE;
        playReqCmd.mDeviceId = mDeviceId;
        playReqCmd.mSendTimestamp = System.currentTimeMillis();

        // 设置当前播放唯一Id
        UUID playingId = UUID.randomUUID();
        mPlayingInfo.setPlayingId(playingId);
        playReqCmd.mUserData = playingId;

        playReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                RtmPlayRspCmd playRspCmd = (RtmPlayRspCmd)rspCmd;
                UUID rspPlayingId = (UUID)rspCmd.getUserData();
                if (!mPlayingInfo.isCurrPlayingId(rspPlayingId)) {  // 不是当前的播放Id
                    ALog.getInstance().w(TAG, "<play.onRtmCmdResponsed> ignore, rspPlayingId=" + rspPlayingId
                            + ", currPlayingId=" + mPlayingInfo.getPlayingId());
                    return;
                }

                int currState = mPlayingInfo.getPlayingState();
                ALog.getInstance().d(TAG, "<play.onRtmCmdResponsed> errCode=" + errCode
                        + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_OPENING) {    // 不是正在打开状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    mPlayingInfo.reset();   // 复位当前播放信息，当前没有播放
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

                    mPlayingInfo.reset();   // 复位当前播放信息，当前没有播放
                    if (playingCallback != null) {
                        playingCallback.onDevMediaOpenDone(FILE_ID_TIMELINE, ret);
                    }
                    return;
                }

                mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PLAYING); // 状态机: 正在播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaOpenDone(FILE_ID_TIMELINE, ErrCode.XOK);
                }
            }
        };

        mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_OPENING); // 状态机: 正在打开
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(playReqCmd);

        // 播放器时钟不走，固定在启动时刻点
        mPlayingInfo.setStartTimestamp(globalStartTime, playSpeed);

        ALog.getInstance().d(TAG, "<play> done, ret=" + ret
                + ", playReqCmd=" + playReqCmd + ", playingId=" + playingId);
        return ret;
    }

    @Override
    public int play(final String fileId, long startPos, int playSpeed,
                    final IPlayingCallback playingCallback) {
        int playingState = mPlayingInfo.getPlayingState();
        if (playingState != DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<play> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        // 设置当前播放文件信息
        mPlayingInfo.setPlayFileInfo(fileId, playingCallback);

        RtmPlayReqCmd playReqCmd = new RtmPlayReqCmd();
        playReqCmd.mFileId = fileId;
        playReqCmd.mOffset = startPos;
        playReqCmd.mRate = playSpeed;

        playReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        playReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_PLAY_ID;
        playReqCmd.mDeviceId = mDeviceId;
        playReqCmd.mSendTimestamp = System.currentTimeMillis();

        // 设置当前播放唯一Id
        UUID playingId = UUID.randomUUID();
        mPlayingInfo.setPlayingId(playingId);
        playReqCmd.mUserData = playingId;

        playReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                RtmPlayRspCmd playRspCmd = (RtmPlayRspCmd)rspCmd;
                UUID rspPlayingId = (UUID)rspCmd.getUserData();
                if (!mPlayingInfo.isCurrPlayingId(rspPlayingId)) {  // 不是当前的播放Id
                    ALog.getInstance().w(TAG, "<play.onRtmCmdResponsed> ignore, rspPlayingId=" + rspPlayingId
                            + ", currPlayingId=" + mPlayingInfo.getPlayingId());
                    return;
                }

                int currState = mPlayingInfo.getPlayingState();
                ALog.getInstance().d(TAG, "<play.onRtmCmdResponsed> errCode=" + errCode
                                    + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_OPENING) {    // 不是正在打开状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    mPlayingInfo.reset();   // 复位当前播放信息，当前没有播放
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
                    stopReqCmd.mUserData = mPlayingInfo.getPlayingId();  // 当前播放Id
                    mSessionMgr.getRtmMgrComp().sendCommandToDev(stopReqCmd);

                    mPlayingInfo.reset();   // 复位当前播放信息，当前没有播放
                    if (playingCallback != null) {
                        playingCallback.onDevMediaOpenDone(fileId, ret);
                    }
                    return;
                }

                mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PLAYING); // 状态机: 正在播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaOpenDone(fileId, ErrCode.XOK);
                }
            }
        };

        mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_OPENING); // 状态机: 正在打开
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(playReqCmd);

        // 播放器时钟不走，固定在启动时刻点
        mPlayingInfo.setStartTimestamp(startPos, playSpeed);

        ALog.getInstance().d(TAG, "<play> done, ret=" + ret
                + ", playReqCmd=" + playReqCmd + ", playingId=" + playingId);
        return ret;
    }

    @Override
    public int stop() {
        int playingState = mPlayingInfo.getPlayingState();
        if (playingState == DEVPLAYER_STATE_STOPPED) {
            ALog.getInstance().d(TAG, "<stop> bad state, already stopped!");
            mPlayingInfo.reset();
            return ErrCode.XERR_BAD_STATE;
        }

        RtmBaseCmd stopReqCmd = new RtmBaseCmd();
        stopReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        stopReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_STOP;
        stopReqCmd.mDeviceId = mDeviceId;
        stopReqCmd.mSendTimestamp = System.currentTimeMillis();
        stopReqCmd.mRespListener = null;    // 不需要管设备端是否收到
        UUID playingId = mPlayingInfo.getPlayingId();
        stopReqCmd.mUserData = playingId;  // 设置当前播放的唯一Id

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(stopReqCmd);

        // 退出频道
        RtcChnlExit();

        // 复位播放器信息
        mPlayingInfo.reset();

        ALog.getInstance().d(TAG, "<stop> done, ret=" + ret
                + ", stopReqCmd=" + stopReqCmd + ", playingId=" + playingId);
        return ret;
    }

    @Override
    public int resume() {
        int playingState = mPlayingInfo.getPlayingState();
        if (playingState != DEVPLAYER_STATE_PAUSED) {
            ALog.getInstance().d(TAG, "<resume> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        RtmBaseCmd resumeReqCmd = new RtmBaseCmd();
        resumeReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        resumeReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_RESUME;
        resumeReqCmd.mDeviceId = mDeviceId;
        resumeReqCmd.mSendTimestamp = System.currentTimeMillis();
        UUID playingId = mPlayingInfo.getPlayingId();
        resumeReqCmd.mUserData = playingId;  // 设置当前播放Id

        resumeReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                UUID rspPlayingId = (UUID)rspCmd.getUserData();
                if (!mPlayingInfo.isCurrPlayingId(rspPlayingId)) {  // 不是当前的播放Id
                    ALog.getInstance().w(TAG, "<resume.onRtmCmdResponsed> ignore, rspPlayingId=" + rspPlayingId
                            + ", currPlayingId=" + mPlayingInfo.getPlayingId());
                    return;
                }

                int currState = mPlayingInfo.getPlayingState();
                IPlayingCallback playingCallback = mPlayingInfo.getPlayingCallback();
                String fileId = mPlayingInfo.getPlayingFileId();
                ALog.getInstance().d(TAG, "<resume.onRtmCmdResponsed> errCode=" + errCode
                        + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_RESUMING) {    // 不是正在暂停状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    ALog.getInstance().e(TAG, "<resume.onRtmCmdResponsed> fail to resume!");
                    mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PAUSED);   // 状态机: 还原原先的暂停状态
                    if (playingCallback != null) {
                        playingCallback.onDevMediaResumeDone(fileId, errCode);
                    }
                    return;
                }

                mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PLAYING);   // 状态机: 正在播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaResumeDone(fileId, errCode);
                }

                // 播放器时钟 从之前的进度位置恢复运行
                mPlayingInfo.clockStart();
            }
        };

        mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_RESUMING); // 状态机: 正在恢复
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(resumeReqCmd);

        ALog.getInstance().d(TAG, "<resume> done, ret=" + ret
                + ", resumeReqCmd=" + resumeReqCmd + ", playingId=" + playingId);
        return ret;
    }

    @Override
    public int pause() {
        int playingState = mPlayingInfo.getPlayingState();
        if (playingState != DEVPLAYER_STATE_PLAYING) {
            ALog.getInstance().d(TAG, "<pause> bad playing state, state=" + playingState);
            return ErrCode.XERR_BAD_STATE;
        }

        RtmBaseCmd pauseReqCmd = new RtmBaseCmd();
        pauseReqCmd.mSequenceId = RtmCmdSeqId.getSeuenceId();
        pauseReqCmd.mCmdId = IRtmCmd.CMDID_MEDIA_PAUSE;
        pauseReqCmd.mDeviceId = mDeviceId;
        pauseReqCmd.mSendTimestamp = System.currentTimeMillis();
        UUID playingId = mPlayingInfo.getPlayingId();
        pauseReqCmd.mUserData = playingId;  // 设置当前播放Id

        pauseReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                UUID rspPlayingId = (UUID)rspCmd.getUserData();
                if (!mPlayingInfo.isCurrPlayingId(rspPlayingId)) {  // 不是当前的播放Id
                    ALog.getInstance().w(TAG, "<pause.onRtmCmdResponsed> ignore, rspPlayingId=" + rspPlayingId
                            + ", currPlayingId=" + mPlayingInfo.getPlayingId());
                    return;
                }

                int currState = mPlayingInfo.getPlayingState();
                IPlayingCallback playingCallback = mPlayingInfo.getPlayingCallback();
                String fileId = mPlayingInfo.getPlayingFileId();
                ALog.getInstance().d(TAG, "<pause.onRtmCmdResponsed> errCode=" + errCode
                        + ", currState=" + currState);

                if (currState != IDevMediaMgr.DEVPLAYER_STATE_PAUSING) {    // 不是正在恢复状态
                    return;
                }
                if (errCode != ErrCode.XOK) {
                    ALog.getInstance().e(TAG, "<pause.onRtmCmdResponsed> fail to pause!");
                    mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PLAYING);   // 状态机: 还原原先的播放状态
                    if (playingCallback != null) {
                        playingCallback.onDevMediaPauseDone(fileId, errCode);
                    }
                    return;
                }

                mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PAUSED);   // 状态机: 暂停播放
                if (playingCallback != null) {
                    playingCallback.onDevMediaPauseDone(fileId, ErrCode.XOK);
                }

                // 播放器时钟停止运行，保留已经运行的进度
                mPlayingInfo.clockStop();
            }
        };

        mPlayingInfo.setPlayingState(IDevMediaMgr.DEVPLAYER_STATE_PAUSING); // 状态机: 正在暂停
        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(pauseReqCmd);

        ALog.getInstance().d(TAG, "<pause> done, ret=" + ret
                + ", pauseReqCmd=" + pauseReqCmd + ", playingId=" + playingId);
        return ret;
    }

    @Override
    public int setPlayingSpeed(int speed) {
        int playingState = mPlayingInfo.getPlayingState();
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
        UUID playingId = mPlayingInfo.getPlayingId();
        speedReqCmd.mUserData = playingId;  // 设置当前播放Id

        speedReqCmd.mRespListener = new IRtmCmd.OnRtmCmdRespListener() {
            @Override
            public void onRtmCmdResponsed(int commandId, int errCode, IRtmCmd reqCmd, IRtmCmd rspCmd) {
                UUID rspPlayingId = (UUID)rspCmd.getUserData();
                if (!mPlayingInfo.isCurrPlayingId(rspPlayingId)) {  // 不是当前的播放Id
                    ALog.getInstance().w(TAG, "<setPlayingSpeed.onRtmCmdResponsed> ignore, rspPlayingId=" + rspPlayingId
                            + ", currPlayingId=" + mPlayingInfo.getPlayingId());
                    return;
                }

                int currState = mPlayingInfo.getPlayingState();
                IPlayingCallback playingCallback = mPlayingInfo.getPlayingCallback();
                String fileId = mPlayingInfo.getPlayingFileId();
                ALog.getInstance().d(TAG, "<setPlayingSpeed.onRtmCmdResponsed> errCode=" + errCode);

                if (errCode != ErrCode.XOK) {
                    ALog.getInstance().e(TAG, "<setPlayingSpeed.onRtmCmdResponsed> fail to set speed!");
                     if (playingCallback != null) {
                        playingCallback.onDevMediaSetSpeedDone(fileId, errCode, speed);
                    }
                    return;
                }

                if (playingCallback != null) {
                    playingCallback.onDevMediaSetSpeedDone(fileId, ErrCode.XOK, speed);
                }

                // 播放器时钟设置倍速
                mPlayingInfo.clockSetSpeed(speed);
            }
        };

        int ret = mSessionMgr.getRtmMgrComp().sendCommandToDev(speedReqCmd);

        ALog.getInstance().d(TAG, "<setPlayingSpeed> done, ret=" + ret
                + ", speedReqCmd=" + speedReqCmd + ", playingId=" + playingId);
        return ret;
    }

    @Override
    public int setAudioMute(boolean mute) {
        int ret = RtcChnlAudioMute(mute);
        return ret;
    }


    @Override
    public long getPlayingProgress()  {
        long progress = mPlayingInfo.getClockProgress();
        return progress;
    }

    @Override
    public int getPlayingState() {
        int playingState = mPlayingInfo.getPlayingState();
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
        mPlayingInfo.setPlayChnlInfo(mDeviceId, rtcUid, chnlName, rtcToken, devUid, mDisplayView, this);

        DeviceSessionMgr.DevPlayerChnlRslt result = mSessionMgr.devPlayerChnlEnter(mPlayingInfo.getPlayChnlInfo());
        if (result.mErrCode == ErrCode.XOK) {
            mPlayingInfo.setPlaySessionId(result.mSessionId);
        }

        return result.mErrCode;
    }

    /**
     * @brief 退出频道
     */
    int RtcChnlExit() {
        mSessionMgr.devPlayerChnlExit(mPlayingInfo.getPlaySessionId());

        ALog.getInstance().d(TAG, "<RtcChnlExit> done");
        return ErrCode.XOK;
    }

    /**
     * @brief 设置音量
     */
    int RtcChnlAudioMute(boolean mute) {
        int ret = mSessionMgr.devPlayerChnlAudioMute(mPlayingInfo.getPlaySessionId(), mute);
        return ErrCode.XOK;
    }

    /////////////////////////////////////////////////////////////////////////////
    //////////////////// TalkingEngine.ICallback 回调处理 ////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    public void onTalkingJoinDone(final UUID sessionId, final String channel, int uid) {
        ALog.getInstance().d(TAG, "<onTalkingJoinDone> sessionId=" + sessionId
                + ", channel=" + channel + ", uid=" + uid);
        UUID playSessionId = mPlayingInfo.getPlaySessionId();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onTalkingJoinDone> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }

    public void onTalkingLeftDone(final UUID sessionId) {
        ALog.getInstance().d(TAG, "<onTalkingLeftDone> sessionId=" + sessionId);
        UUID playSessionId = mPlayingInfo.getPlaySessionId();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onTalkingLeftDone> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }

    public void onUserOnline(final UUID sessionId, int uid, int elapsed) {
        ALog.getInstance().d(TAG, "<onUserOnline> sessionId=" + sessionId + ", uid=" + uid);
        UUID playSessionId = mPlayingInfo.getPlaySessionId();
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
        UUID playSessionId = mPlayingInfo.getPlaySessionId();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onUserOffline> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }


        // 设备端退出设备播放频道
        IPlayingCallback playingCallback = mPlayingInfo.getPlayingCallback();
        String fileId = mPlayingInfo.getPlayingFileId();
        RtcChnlExit();      // 退出频道
        mPlayingInfo.reset();  // 清除当前播放信息

        if (playingCallback != null) {  // 回调给应用层
            ALog.getInstance().d(TAG, "<onUserOffline> callback onDevMediaPlayingDone!");

            playingCallback.onDevMediaPlayingDone(fileId);
        }
    }

    public void onPeerFirstVideoDecoded(final UUID sessionId, int peerUid, int videoWidth, int videoHeight) {
        ALog.getInstance().d(TAG, "<onPeerFirstVideoDecoded> sessionId=" + sessionId
                + ", peerUid=" + peerUid + ", videoWidth=" + videoWidth + ", videoHeight=" + videoHeight);
        UUID playSessionId = mPlayingInfo.getPlaySessionId();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onPeerFirstVideoDecoded> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }

        // 播放器时钟 从指定时刻点开始运行
        int playingState = mPlayingInfo.getPlayingState();
        if (playingState != DEVPLAYER_STATE_STOPPED) {
            mPlayingInfo.clockStartWithCurr();  // 从当前时刻开始播放
            ALog.getInstance().d(TAG, "<onPeerFirstVideoDecoded> clock start from: "
                    + mPlayingInfo.getStartTimestamp());
        }
    }

    public void onRecordingError(final UUID sessionId, int errCode) {
        ALog.getInstance().d(TAG, "<onRecordingError> sessionId=" + sessionId + ", errCode=" + errCode);
        UUID playSessionId = mPlayingInfo.getPlaySessionId();
        if ((playSessionId == null) || (sessionId.compareTo(playSessionId) != 0)) {
            ALog.getInstance().e(TAG, "<onRecordingError> NOT playing sessionId=" + sessionId
                    + ", playSessionId=" + playSessionId);
            return;
        }
    }



}
