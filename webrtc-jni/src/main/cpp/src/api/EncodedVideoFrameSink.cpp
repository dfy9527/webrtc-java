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

#include "api/EncodedVideoFrameSink.h"
#include "api/EncodedVideoFrame.h"
#include "JavaClasses.h"
#include "JavaUtils.h"
#include "JNI_WebRTC.h"

namespace jni
{
	EncodedFrameSnapshot MakeSnapshot(const webrtc::EncodedImage & image, webrtc::VideoCodecType codec)
	{
		EncodedFrameSnapshot snapshot;

		// Borrow the payload instead of copying it: the snapshot is consumed
		// synchronously by the Java delivery within this DispatchFrame call,
		// so the image's memory is guaranteed to be alive for its whole
		// lifetime here.
		snapshot.data = image.data();
		snapshot.size = image.size();

		snapshot.rtpTimestamp = image.RtpTimestamp();
		snapshot.frameType = image.FrameType();
		snapshot.width = image._encodedWidth;
		snapshot.height = image._encodedHeight;
		snapshot.codec = codec;

		return snapshot;
	}

	EncodedVideoFrameSink::EncodedVideoFrameSink(JNIEnv * env, const JavaGlobalRef<jobject> & sink) :
		sink(sink),
		javaClass(JavaClasses::get<JavaEncodedVideoFrameSinkClass>(env))
	{
	}

	void EncodedVideoFrameSink::OnEncodedVideoFrame(const EncodedFrameSnapshot & frame)
	{
		JNIEnv * env = AttachCurrentThread();

		try {
			JavaLocalRef<jobject> jFrame = EncodedVideoFrame::toJava(env, frame);

			if (jFrame.get() == nullptr) {
				return;
			}

			env->CallVoidMethod(sink, javaClass->onEncodedVideoFrame, jFrame.get());

			// A throwing sink must not break the decode thread or any other
			// part of the decoding pipeline. Describe and clear the pending
			// exception. Note: the jni::ExceptionCheck(env) helper is not
			// used here on purpose, because it throws a C++ exception.
			if (env->ExceptionCheck()) {
				env->ExceptionDescribe();
				env->ExceptionClear();
			}
		}
		catch (...) {
			// The JNI helpers may throw C++ exceptions (e.g. JavaWrappedException).
			// Clear any pending Java exception and swallow the C++ one.
			if (env->ExceptionCheck()) {
				env->ExceptionDescribe();
				env->ExceptionClear();
			}
		}
	}

	EncodedVideoFrameSink::JavaEncodedVideoFrameSinkClass::JavaEncodedVideoFrameSinkClass(JNIEnv * env)
	{
		jclass cls = FindClass(env, PKG_VIDEO"EncodedVideoFrameSink");

		onEncodedVideoFrame = GetMethod(env, cls, "onEncodedVideoFrame", "(L" PKG_VIDEO "EncodedVideoFrame;)V");
	}

	void EncodedFrameSinkSlot::SetSink(JNIEnv * env, jobject jSink)
	{
		auto sink = std::make_shared<EncodedVideoFrameSink>(env, JavaGlobalRef<jobject>(env, jSink));

		std::lock_guard<std::mutex> lock(mutex_);

		sink_ = std::move(sink);
		hasSink_.store(true, std::memory_order_release);
	}

	void EncodedFrameSinkSlot::RemoveSink()
	{
		std::lock_guard<std::mutex> lock(mutex_);

		sink_.reset();
		hasSink_.store(false, std::memory_order_release);
	}

	bool EncodedFrameSinkSlot::HasSink() const noexcept
	{
		return hasSink_.load(std::memory_order_acquire);
	}

	void EncodedFrameSinkSlot::DispatchFrame(const webrtc::EncodedImage & image, webrtc::VideoCodecType codec)
	{
		// Re-check under race with a concurrent removal to avoid the deep
		// copy when nobody is listening.
		if (!HasSink()) {
			return;
		}

		// Capture the borrowed snapshot first, without holding the lock. The
		// EncodedImage is only valid during the Decode() call; the snapshot
		// must be consumed synchronously below.
		EncodedFrameSnapshot snapshot = MakeSnapshot(image, codec);

		std::shared_ptr<EncodedVideoFrameSink> sink;

		{
			std::lock_guard<std::mutex> lock(mutex_);
			sink = sink_;
		}

		// Invoke outside the mutex: a Java callback that calls back into
		// set/remove cannot deadlock, and a slow sink does not block the
		// teardown of a previous sink.
		if (sink != nullptr) {
			sink->OnEncodedVideoFrame(snapshot);
		}
	}
}
