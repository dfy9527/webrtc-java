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

#ifndef JNI_WEBRTC_API_ENCODED_VIDEO_FRAME_SINK_H_
#define JNI_WEBRTC_API_ENCODED_VIDEO_FRAME_SINK_H_

#include "JavaClass.h"
#include "JavaRef.h"

#include "api/video/encoded_image.h"
#include "api/video/video_frame_type.h"
#include "api/video_codecs/video_codec.h"

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <jni.h>
#include <memory>
#include <mutex>

namespace jni
{
	// A borrowed view into a webrtc::EncodedImage. The payload is not copied:
	// the snapshot is consumed synchronously by the Java delivery within the
	// DispatchFrame call, so the underlying image memory is guaranteed to be
	// alive for the entire snapshot lifetime. The webrtc::EncodedImage itself
	// is only valid during the Decode() call and must never be retained
	// beyond it. The Java side receives a deep copy into a GC-owned direct
	// buffer, so it remains independent of the native buffer lifetime.
	struct EncodedFrameSnapshot
	{
		const uint8_t * data = nullptr;
		size_t size = 0;

		uint32_t rtpTimestamp = 0;

		webrtc::VideoFrameType frameType = webrtc::VideoFrameType::kVideoFrameDelta;

		// Reference values provided by the encoder. May be 0 if unknown yet.
		uint32_t width = 0;
		uint32_t height = 0;

		webrtc::VideoCodecType codec = webrtc::kVideoCodecGeneric;
	};

	// Captures the metadata and a borrowed view of the encoded image payload.
	// The payload is passed through unmodified (no format conversion, no
	// re-encoding) and is not copied; see EncodedFrameSnapshot.
	EncodedFrameSnapshot MakeSnapshot(const webrtc::EncodedImage & image, webrtc::VideoCodecType codec);


	class EncodedVideoFrameSink
	{
		public:
			EncodedVideoFrameSink(JNIEnv * env, const JavaGlobalRef<jobject> & sink);
			~EncodedVideoFrameSink() = default;

			// Delivers the snapshot to the Java sink. Exceptions thrown by
			// the Java side are caught, described and cleared: they must
			// never propagate to the WebRTC decode thread.
			void OnEncodedVideoFrame(const EncodedFrameSnapshot & frame);

		private:
			class JavaEncodedVideoFrameSinkClass : public JavaClass
			{
				public:
					JavaEncodedVideoFrameSinkClass(JNIEnv * env);

					jmethodID onEncodedVideoFrame;
			};

			JavaGlobalRef<jobject> sink;

			const std::shared_ptr<JavaEncodedVideoFrameSinkClass> javaClass;
	};


	// Minimal shared sink slot, co-owned by the decoder factory wrapper and
	// every proxy decoder. The shared ownership guarantees that the slot
	// outlives whichever of its owners dies last: no pointer between the
	// factory and the decoders can ever dangle, independent of the native
	// destruction order. Responsibilities: set/remove sink, HasSink and
	// DispatchFrame. Nothing else.
	class EncodedFrameSinkSlot
	{
		public:
			// Replace semantics: at most one sink at a time. The previously
			// set sink is released while holding the mutex; an in-flight
			// dispatch holding a shared_ptr copy completes safely.
			void SetSink(JNIEnv * env, jobject sink);

			// Removes the sink. Once this returns, no new dispatch invokes
			// the sink. An already in-flight invocation may complete.
			void RemoveSink();

			// Fast check for the decode hot path (single atomic load).
			bool HasSink() const noexcept;

			// Fixed order: HasSink check, capture the (borrowed) frame
			// snapshot, lock + snapshot the sink, unlock, then invoke the
			// sink. Never calls into Java while holding the mutex.
			void DispatchFrame(const webrtc::EncodedImage & image, webrtc::VideoCodecType codec);

		private:
			std::mutex mutex_;

			std::shared_ptr<EncodedVideoFrameSink> sink_;

			std::atomic<bool> hasSink_{ false };
	};
}

#endif
