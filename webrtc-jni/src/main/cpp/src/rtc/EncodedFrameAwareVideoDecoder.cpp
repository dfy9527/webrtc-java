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

#include "rtc/EncodedFrameAwareVideoDecoder.h"

namespace jni
{
	EncodedFrameAwareVideoDecoder::EncodedFrameAwareVideoDecoder(
			std::unique_ptr<webrtc::VideoDecoder> decoder,
			std::shared_ptr<EncodedFrameSinkSlot> sinkSlot,
			webrtc::VideoCodecType codec) :
		decoder(std::move(decoder)),
		sinkSlot(std::move(sinkSlot)),
		codec(codec)
	{
	}

	bool EncodedFrameAwareVideoDecoder::Configure(const Settings & settings)
	{
		return decoder->Configure(settings);
	}

	int32_t EncodedFrameAwareVideoDecoder::Decode(const webrtc::EncodedImage & input_image, int64_t render_time_ms)
	{
		if (sinkSlot->HasSink()) {
			sinkSlot->DispatchFrame(input_image, codec);
		}

		return decoder->Decode(input_image, render_time_ms);
	}

	int32_t EncodedFrameAwareVideoDecoder::Decode(const webrtc::EncodedImage & input_image, bool missing_frames, int64_t render_time_ms)
	{
		if (sinkSlot->HasSink()) {
			sinkSlot->DispatchFrame(input_image, codec);
		}

		return decoder->Decode(input_image, missing_frames, render_time_ms);
	}

	int32_t EncodedFrameAwareVideoDecoder::RegisterDecodeCompleteCallback(webrtc::DecodedImageCallback * callback)
	{
		return decoder->RegisterDecodeCompleteCallback(callback);
	}

	int32_t EncodedFrameAwareVideoDecoder::Release()
	{
		return decoder->Release();
	}

	EncodedFrameAwareVideoDecoder::DecoderInfo EncodedFrameAwareVideoDecoder::GetDecoderInfo() const
	{
		return decoder->GetDecoderInfo();
	}

	const char * EncodedFrameAwareVideoDecoder::ImplementationName() const
	{
		return decoder->ImplementationName();
	}
}
