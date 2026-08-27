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

#include "api/EncodedVideoFrame.h"
#include "JavaClasses.h"
#include "JavaUtils.h"
#include "JNI_WebRTC.h"

#include <cstring>

namespace jni
{
	namespace EncodedVideoFrame
	{
		JavaLocalRef<jobject> toJava(JNIEnv * env, const EncodedFrameSnapshot & frame)
		{
			const auto frameClass = JavaClasses::get<JavaEncodedVideoFrameClass>(env);
			const auto codecClass = JavaClasses::get<JavaVideoCodecTypeClass>(env);
			const auto typeClass = JavaClasses::get<JavaVideoFrameTypeClass>(env);

			const jsize size = static_cast<jsize>(frame.size);

			// Allocate the GC-owned direct buffer on the Java side and copy
			// the payload into it. The buffer is the only deep copy: the
			// borrowed snapshot stays valid only for this synchronous call.
			jobject jBuffer = env->CallStaticObjectMethod(frameClass->cls, frameClass->allocateDirect, size);

			if (env->ExceptionCheck() || jBuffer == nullptr) {
				env->ExceptionDescribe();
				env->ExceptionClear();

				if (jBuffer != nullptr) {
					env->DeleteLocalRef(jBuffer);
				}

				return JavaLocalRef<jobject>(env, nullptr);
			}

			if (size > 0) {
				void * address = env->GetDirectBufferAddress(jBuffer);

				// Copy not possible: drop the frame instead of delivering a
				// payload that silently reads as empty or garbage.
				if (address == nullptr || frame.data == nullptr) {
					env->DeleteLocalRef(jBuffer);

					return JavaLocalRef<jobject>(env, nullptr);
				}

				std::memcpy(address, frame.data, frame.size);
			}

			jobject jCodec = env->CallStaticObjectMethod(codecClass->cls, codecClass->fromNative,
				static_cast<jint>(CodecToStableId(frame.codec)));

			if (env->ExceptionCheck() || jCodec == nullptr) {
				env->ExceptionDescribe();
				env->ExceptionClear();

				env->DeleteLocalRef(jBuffer);

				return JavaLocalRef<jobject>(env, nullptr);
			}

			jobject jFrameType = env->CallStaticObjectMethod(typeClass->cls, typeClass->fromNative,
				static_cast<jint>(FrameTypeToStableId(frame.frameType)));

			if (env->ExceptionCheck() || jFrameType == nullptr) {
				env->ExceptionDescribe();
				env->ExceptionClear();

				env->DeleteLocalRef(jBuffer);
				env->DeleteLocalRef(jCodec);

				return JavaLocalRef<jobject>(env, nullptr);
			}

			jobject jFrame = env->NewObject(frameClass->cls, frameClass->ctor,
				jBuffer, jCodec, jFrameType,
				static_cast<jint>(frame.width), static_cast<jint>(frame.height),
				static_cast<jlong>(frame.rtpTimestamp));

			env->DeleteLocalRef(jBuffer);
			env->DeleteLocalRef(jCodec);
			env->DeleteLocalRef(jFrameType);

			if (env->ExceptionCheck() || jFrame == nullptr) {
				env->ExceptionDescribe();
				env->ExceptionClear();

				if (jFrame != nullptr) {
					env->DeleteLocalRef(jFrame);
				}

				return JavaLocalRef<jobject>(env, nullptr);
			}

			return JavaLocalRef<jobject>(env, jFrame);
		}
	}

	int CodecToStableId(webrtc::VideoCodecType codec)
	{
		switch (codec) {
			case webrtc::kVideoCodecH264:
				return 1;
			case webrtc::kVideoCodecVP8:
				return 2;
			case webrtc::kVideoCodecVP9:
				return 3;
			case webrtc::kVideoCodecAV1:
				return 4;
			default:
				return 0;
		}
	}

	int FrameTypeToStableId(webrtc::VideoFrameType frameType)
	{
		switch (frameType) {
			case webrtc::VideoFrameType::kVideoFrameKey:
				return 1;
			case webrtc::VideoFrameType::kVideoFrameDelta:
				return 2;
			case webrtc::VideoFrameType::kEmptyFrame:
				return 3;
			default:
				return 0;
		}
	}

	JavaEncodedVideoFrameClass::JavaEncodedVideoFrameClass(JNIEnv * env)
	{
		cls = FindClass(env, PKG_VIDEO"EncodedVideoFrame");

		ctor = GetMethod(env, cls, "<init>",
			"(" BYTE_BUFFER_SIG "L" PKG_VIDEO "VideoCodecType;L" PKG_VIDEO "VideoFrameType;IIJ)V");

		allocateDirect = GetStaticMethod(env, cls, "allocateDirect", "(I)" BYTE_BUFFER_SIG);
	}

	JavaVideoCodecTypeClass::JavaVideoCodecTypeClass(JNIEnv * env)
	{
		cls = FindClass(env, PKG_VIDEO"VideoCodecType");

		fromNative = GetStaticMethod(env, cls, "fromNative", "(I)L" PKG_VIDEO "VideoCodecType;");
	}

	JavaVideoFrameTypeClass::JavaVideoFrameTypeClass(JNIEnv * env)
	{
		cls = FindClass(env, PKG_VIDEO"VideoFrameType");

		fromNative = GetStaticMethod(env, cls, "fromNative", "(I)L" PKG_VIDEO "VideoFrameType;");
	}
}
