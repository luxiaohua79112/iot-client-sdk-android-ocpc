/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class io_agora_iotlink_utils_ImageConvert */

#ifndef _Included_io_agora_iotlink_utils_ImageConvert
#define _Included_io_agora_iotlink_utils_ImageConvert
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     io_agora_iotlink_utils_ImageConvert
 * Method:    ImgCvt_I420ToRgba
 * Signature: ([B[B[BIILjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL Java_io_agora_iotlink_utils_ImageConvert_ImgCvt_1I420ToRgba
        (JNIEnv *, jobject, jbyteArray, jbyteArray, jbyteArray, jint, jint, jobject);


/*
 * Class:     io_agora_iotlink_utils_ImageConvert
 * Method:    ImgCvt_I420ToRgba
 * Signature: ([B[B[BIILjava/lang/[B;)I
 */
JNIEXPORT jint JNICALL Java_io_agora_iotlink_utils_ImageConvert_ImgCvt_1YuvToNv12
        (JNIEnv *, jobject, jbyteArray, jbyteArray, jbyteArray, jint, jint, jbyteArray);


/*
 * Class:     io_agora_iotlink_utils_ImageConvert
 * Method:    ImgCvt_YuvToI420
 * Signature: (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;IIIII[B[B[B)I
 */
JNIEXPORT jint JNICALL Java_io_agora_iotlink_utils_ImageConvert_ImgCvt_1YuvToI420
        (JNIEnv *, jobject, jobject, jobject, jobject, jint, jint, jint, jint, jint, jbyteArray, jbyteArray, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
