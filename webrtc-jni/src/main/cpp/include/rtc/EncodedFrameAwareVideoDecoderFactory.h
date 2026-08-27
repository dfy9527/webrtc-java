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

#ifndef JNI_WEBRTC_RTC_ENCODED_FRAME_AWARE_VIDEO_DECODER_FACTORY_H_
#define JNI_WEBRTC_RTC_ENCODED_FRAME_AWARE_VIDEO_DECODER_FACTORY_H_

#include "api/EncodedVideoFrameSink.h"

#include "api/video/resolution.h"
#include "api/video_codecs/video_decoder_factory.h"

#include <memory>
#include <vector>

namespace jni
{
	// Wraps the platform's real video decoder factory. Every created decoder
	// is wrapped by an EncodedFrameAwareVideoDecoder that dispatches a deep
	// copy of each encoded image to the sink slot before decoding. The sink
	// defines the media ingestion isolation domain: all PeerConnections of
	// the owning native PeerConnectionFactory deliver their encoded frames
	// to it. When no sink is set, decoding behaves exactly as before.
	class EncodedFrameAwareVideoDecoderFactory : public webrtc::VideoDecoderFactory
	{
		public:
			explicit EncodedFrameAwareVideoDecoderFactory(std::unique_ptr<webrtc::VideoDecoderFactory> factory);

			// Sink management (invoked on the Java thread).
			void SetSink(JNIEnv * env, jobject sink);
			void RemoveSink();

			// Invoked by the proxy decoders on the decode thread.
			bool HasSink() const noexcept;
			void DispatchFrame(const webrtc::EncodedImage & image, webrtc::VideoCodecType codec);

			// webrtc::VideoDecoderFactory implementation.
			std::unique_ptr<webrtc::VideoDecoder> Create(const webrtc::Environment & env, const webrtc::SdpVideoFormat & format) override;
			std::vector<webrtc::SdpVideoFormat> GetSupportedFormats() const override;
			CodecSupport QueryCodecSupport(const webrtc::SdpVideoFormat & format, bool reference_scaling, std::optional<webrtc::Resolution> resolution) const override;

		private:
			static webrtc::VideoCodecType CodecOf(const webrtc::SdpVideoFormat & format);

			std::shared_ptr<EncodedFrameSinkSlot> sinkSlot;

			std::unique_ptr<webrtc::VideoDecoderFactory> factory;
	};
}

#endif
