/*
 * Copyright 2025 Alex Andres
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef JNI_WEBRTC_API_ENCODED_VIDEO_FRAME_H_
#define JNI_WEBRTC_API_ENCODED_VIDEO_FRAME_H_

#include "api/EncodedVideoFrameSink.h"
#include "JavaClass.h"
#include "JavaRef.h"

#include <jni.h>

namespace jni
{
	namespace EncodedVideoFrame
	{
		// Creates a Java EncodedVideoFrame from a snapshot. The payload is
		// copied into a direct ByteBuffer allocated (and garbage collected)
		// on the Java side. No native buffer address is ever exposed to
		// Java. Returns a null reference if the Java object could not be
		// created (pending exception is cleared).
		JavaLocalRef<jobject> toJava(JNIEnv * env, const EncodedFrameSnapshot & frame);
	}

	// Stable integer codes passed to the Java side. These codes are
	// explicitly defined here and are independent of the libwebrtc enum
	// values: 0 = UNKNOWN, 1..4 = specific constants (see Java enums).
	int CodecToStableId(webrtc::VideoCodecType codec);
	int FrameTypeToStableId(webrtc::VideoFrameType frameType);

	class JavaEncodedVideoFrameClass : public JavaClass
	{
		public:
			explicit JavaEncodedVideoFrameClass(JNIEnv * env);

			jclass cls;
			jmethodID ctor;
			jmethodID allocateDirect;
	};

	class JavaVideoCodecTypeClass : public JavaClass
	{
		public:
			explicit JavaVideoCodecTypeClass(JNIEnv * env);

			jclass cls;
			jmethodID fromNative;
	};

	class JavaVideoFrameTypeClass : public JavaClass
	{
		public:
			explicit JavaVideoFrameTypeClass(JNIEnv * env);

			jclass cls;
			jmethodID fromNative;
	};
}

#endif
