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

#include "rtc/EncodedFrameAwareVideoDecoderFactory.h"
#include "rtc/EncodedFrameAwareVideoDecoder.h"

#include "api/video_codecs/video_codec.h"

namespace jni
{
	EncodedFrameAwareVideoDecoderFactory::EncodedFrameAwareVideoDecoderFactory(
			std::unique_ptr<webrtc::VideoDecoderFactory> factory) :
		sinkSlot(std::make_shared<EncodedFrameSinkSlot>()),
		factory(std::move(factory))
	{
	}

	void EncodedFrameAwareVideoDecoderFactory::SetSink(JNIEnv * env, jobject sink)
	{
		sinkSlot->SetSink(env, sink);
	}

	void EncodedFrameAwareVideoDecoderFactory::RemoveSink()
	{
		sinkSlot->RemoveSink();
	}

	bool EncodedFrameAwareVideoDecoderFactory::HasSink() const noexcept
	{
		return sinkSlot->HasSink();
	}

	void EncodedFrameAwareVideoDecoderFactory::DispatchFrame(const webrtc::EncodedImage & image, webrtc::VideoCodecType codec)
	{
		sinkSlot->DispatchFrame(image, codec);
	}

	std::unique_ptr<webrtc::VideoDecoder> EncodedFrameAwareVideoDecoderFactory::Create(
			const webrtc::Environment & env, const webrtc::SdpVideoFormat & format)
	{
		std::unique_ptr<webrtc::VideoDecoder> decoder = factory->Create(env, format);

		if (decoder == nullptr) {
			return nullptr;
		}

		return std::make_unique<EncodedFrameAwareVideoDecoder>(std::move(decoder), sinkSlot, CodecOf(format));
	}

	std::vector<webrtc::SdpVideoFormat> EncodedFrameAwareVideoDecoderFactory::GetSupportedFormats() const
	{
		return factory->GetSupportedFormats();
	}

	EncodedFrameAwareVideoDecoderFactory::CodecSupport EncodedFrameAwareVideoDecoderFactory::QueryCodecSupport(
			const webrtc::SdpVideoFormat & format, bool reference_scaling, std::optional<webrtc::Resolution> resolution) const
	{
		return factory->QueryCodecSupport(format, reference_scaling, resolution);
	}

	webrtc::VideoCodecType EncodedFrameAwareVideoDecoderFactory::CodecOf(const webrtc::SdpVideoFormat & format)
	{
		return webrtc::PayloadStringToCodecType(format.name);
	}
}
