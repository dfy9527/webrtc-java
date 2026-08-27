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

#ifndef JNI_WEBRTC_RTC_ENCODED_FRAME_AWARE_VIDEO_DECODER_H_
#define JNI_WEBRTC_RTC_ENCODED_FRAME_AWARE_VIDEO_DECODER_H_

#include "api/EncodedVideoFrameSink.h"

#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_decoder.h"

#include <memory>

namespace jni
{
	// A VideoDecoder that forwards every call to a wrapped real decoder.
	// Before the encoded image is handed to the real decoder, a deep copy of
	// the payload is dispatched to the shared sink slot (if a sink is set).
	// The decoder does not modify the EncodedImage, performs no format
	// conversion and keeps no frame, track or connection state. When no sink
	// is set, Decode() performs a single atomic load and forwards unchanged.
	class EncodedFrameAwareVideoDecoder : public webrtc::VideoDecoder
	{
		public:
			EncodedFrameAwareVideoDecoder(std::unique_ptr<webrtc::VideoDecoder> decoder,
				std::shared_ptr<EncodedFrameSinkSlot> sinkSlot,
				webrtc::VideoCodecType codec);
			~EncodedFrameAwareVideoDecoder() override = default;

			// webrtc::VideoDecoder implementation. Both Decode overloads are
			// intercepted; each forwards to the matching overload of the
			// wrapped decoder.
			bool Configure(const Settings & settings) override;
			int32_t Decode(const webrtc::EncodedImage & input_image, int64_t render_time_ms) override;
			int32_t Decode(const webrtc::EncodedImage & input_image, bool missing_frames, int64_t render_time_ms) override;
			int32_t RegisterDecodeCompleteCallback(webrtc::DecodedImageCallback * callback) override;
			int32_t Release() override;
			DecoderInfo GetDecoderInfo() const override;
			const char * ImplementationName() const override;

		private:
			std::unique_ptr<webrtc::VideoDecoder> decoder;

			std::shared_ptr<EncodedFrameSinkSlot> sinkSlot;

			webrtc::VideoCodecType codec;
	};
}

#endif
