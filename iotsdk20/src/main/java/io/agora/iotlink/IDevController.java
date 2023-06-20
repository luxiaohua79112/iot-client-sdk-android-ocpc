/**
 * @file IRtmMgr.java
 * @brief This file define the interface of RTM management
 * @author xiaohua.lu
 * @email luxiaohua@agora.io
 * @version 1.0.0.1
 * @date 2022-08-11
 * @license Copyright (C) 2021 AgoraIO Inc. All rights reserved.
 */
package io.agora.iotlink;


import android.view.View;

import java.util.List;
import java.util.UUID;

/**
 * @brief 设备控制器，可以给设备发控制命令
 */
public interface IDevController  {

    /**
     * @brief 命令发送回调监听器
     */
    public static interface OnDevCmdListener {

        /**
         * @brief 命令执行完成回调
         * @param errCode: 命令执行结果错误码
         * @param result1: 不同的命令对应不同的返回值1
         * @param result2: 不同的命令对应不同的返回值2
         */
        default void onDeviceCmdDone(int errCode, long result1, long result2) {}
    }


    ////////////////////////////////////////////////////////////////////////
    //////////////////////////// Public Methods ///////////////////////////
    ////////////////////////////////////////////////////////////////////////

    /**
     * @brief 发送云台控制命令
     * @param action: 动作命令：0-开始，1-停止
     * @param direction: 方向：0-上、1-下、2-左、3-右、4-镜头拉近、5-镜头拉远
     * @param speed: 速度：0-慢，1-适中（默认），2-快
     * @param cmdListener: 命令完成回调
     * @return 返回错误码
     */
    int sendCmdPtzCtrl(int action, int direction, int speed, final OnDevCmdListener cmdListener);

    /**
     * @brief 发送云台校准命令
     * @param cmdListener: 命令完成回调
     * @return 返回错误码
     */
    int sendCmdPtzReset(final OnDevCmdListener cmdListener);


    /**
     * @brief 发送存储卡格式化命令
     * @param cmdListener: 命令完成回调
     * @return 返回错误码
     */
    int storageCardFormat(final OnDevCmdListener cmdListener);

}
