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

/**
 * The video codec of an {@link EncodedVideoFrame}.
 *
 * @author Alex Andres
 */
public enum VideoCodecType {

	H264,
	VP8,
	VP9,
	AV1,
	UNKNOWN;

	/**
	 * Maps a stable native integer code to the enum constant. The codes are
	 * explicitly defined by the native binding and are independent of the
	 * libwebrtc enum values.
	 *
	 * @param nativeValue The stable code: 1 = H264, 2 = VP8, 3 = VP9,
	 *                    4 = AV1.
	 *
	 * @return The codec constant or {@link #UNKNOWN} for unknown codes.
	 */
	static VideoCodecType fromNative(int nativeValue) {
		switch (nativeValue) {
			case 1:
				return H264;
			case 2:
				return VP8;
			case 3:
				return VP9;
			case 4:
				return AV1;
			default:
				return UNKNOWN;
		}
	}
}
