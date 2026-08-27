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
 * Receives encoded video frames before they are passed to the video decoder.
 * The frames are deep copies of the native encoded images; the payload is
 * never re-encoded or modified.
 *
 * @author Alex Andres
 */
public interface EncodedVideoFrameSink {

	/**
	 * Invoked for each received encoded video frame.
	 * <p>
	 * The method is called synchronously on an internal WebRTC decode thread.
	 * A slow implementation delays the decoding of the video stream. An
	 * exception thrown by the implementation is caught and logged by the
	 * native layer and does not affect the decoding.
	 *
	 * @param frame The received encoded video frame.
	 */
	void onEncodedVideoFrame(EncodedVideoFrame frame);
}
