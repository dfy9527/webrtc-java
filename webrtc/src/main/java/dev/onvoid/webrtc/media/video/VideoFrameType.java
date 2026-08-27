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
 * The type of an {@link EncodedVideoFrame}.
 *
 * @author Alex Andres
 */
public enum VideoFrameType {

	KEY_FRAME,
	DELTA_FRAME,
	EMPTY_FRAME,
	UNKNOWN;

	/**
	 * Maps a stable native integer code to the enum constant. The codes are
	 * explicitly defined by the native binding and are independent of the
	 * libwebrtc enum values.
	 *
	 * @param nativeValue The stable code: 1 = KEY_FRAME, 2 = DELTA_FRAME,
	 *                    3 = EMPTY_FRAME.
	 *
	 * @return The frame type constant or {@link #UNKNOWN} for unknown codes.
	 */
	static VideoFrameType fromNative(int nativeValue) {
		switch (nativeValue) {
			case 1:
				return KEY_FRAME;
			case 2:
				return DELTA_FRAME;
			case 3:
				return EMPTY_FRAME;
			default:
				return UNKNOWN;
		}
	}
}
