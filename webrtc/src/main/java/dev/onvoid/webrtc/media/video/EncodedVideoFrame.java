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

package dev.onvoid.webrtc.media.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An encoded (compressed) video frame captured before it is passed to the
 * video decoder. The payload is a deep copy of the native encoded image and
 * is stored in a direct ByteBuffer owned by the Java garbage collector. The
 * buffer is independent from any native buffer lifetime.
 *
 * @author Alex Andres
 */
public final class EncodedVideoFrame {

	/** The encoded frame payload (codec bitstream, e.g. H.264 Annex-B). */
	public final ByteBuffer data;

	/** The codec of the payload. */
	public final VideoCodecType codec;

	/** The frame type (key frame, delta frame, ...). */
	public final VideoFrameType frameType;

	/** Reference width as provided by the encoder. May be {@code 0} if unknown yet. */
	public final int width;

	/** Reference height as provided by the encoder. May be {@code 0} if unknown yet. */
	public final int height;

	/** The RTP timestamp in 90 kHz clock units. */
	public final long rtpTimestamp;


	EncodedVideoFrame(ByteBuffer data, VideoCodecType codec, VideoFrameType frameType,
			int width, int height, long rtpTimestamp) {
		this.data = data;
		this.codec = codec;
		this.frameType = frameType;
		this.width = width;
		this.height = height;
		this.rtpTimestamp = rtpTimestamp;
	}

	/**
	 * Allocates the direct ByteBuffer the native code copies the encoded
	 * payload into. Called from native code only.
	 *
	 * @param capacity The number of bytes to allocate.
	 *
	 * @return A newly allocated direct ByteBuffer in native byte order.
	 */
	static ByteBuffer allocateDirect(int capacity) {
		return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
	}

	@Override
	public String toString() {
		return String.format("EncodedVideoFrame@%x [codec=%s, frameType=%s, %dx%d, rtpTimestamp=%d, bytes=%d]",
				hashCode(), codec, frameType, width, height, rtpTimestamp,
				(data != null) ? data.remaining() : 0);
	}
}
