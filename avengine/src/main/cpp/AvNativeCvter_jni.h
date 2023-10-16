/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class io_agora_avmodule_AvNativeCvter */

#ifndef _Included_io_agora_avmodule_AvNativeCvter
#define _Included_io_agora_avmodule_AvNativeCvter
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     io_agora_avmodule_AvNativeCvter
 * Method:    native_cvterOpen
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_io_agora_avmodule_AvNativeCvter_native_1cvterOpen
  (JNIEnv *, jobject, jstring, jstring);

/*
 * Class:     io_agora_avmodule_AvNativeCvter
 * Method:    native_cvterClose
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_io_agora_avmodule_AvNativeCvter_native_1cvterClose
  (JNIEnv *, jobject, jlong);

/*
 * Class:     io_agora_avmodule_AvNativeCvter
 * Method:    native_cvterGetMediaInfo
 * Signature: (JLio/agora/avmodule/AvMediaInfo;)I
 */
JNIEXPORT jint JNICALL Java_io_agora_avmodule_AvNativeCvter_native_1cvterGetMediaInfo
  (JNIEnv *, jobject, jlong, jobject);

/*
 * Class:     io_agora_avmodule_AvNativeCvter
 * Method:    native_cvterDoStep
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_io_agora_avmodule_AvNativeCvter_native_1cvterDoStep
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif