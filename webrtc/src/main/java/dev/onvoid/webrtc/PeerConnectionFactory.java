/*
 * Copyright 2019 Alex Andres
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

package dev.onvoid.webrtc;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.onvoid.webrtc.internal.DisposableNativeObject;
import dev.onvoid.webrtc.internal.NativeLoader;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.MediaType;
import dev.onvoid.webrtc.media.audio.AudioDeviceModuleBase;
import dev.onvoid.webrtc.media.audio.AudioOptions;
import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioTrackSource;
import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.video.EncodedVideoFrameSink;
import dev.onvoid.webrtc.media.video.VideoTrackSource;
import dev.onvoid.webrtc.media.video.VideoTrack;

/**
 * The PeerConnectionFactory is the main entry point for a WebRTC application.
 * It provides factory methods for {@link RTCPeerConnection} and audio/video
 * {@link MediaStreamTrack}s.
 *
 * @author Alex Andres
 */
public class PeerConnectionFactory extends DisposableNativeObject {

	static {
		try {
			NativeLoader.loadLibrary("webrtc-java");
		}
		catch (Exception e) {
			throw new RuntimeException("Load library 'webrtc-java' failed", e);
		}
	}


	@SuppressWarnings("unused")
	private long networkThreadHandle;

	@SuppressWarnings("unused")
	private long signalingThreadHandle;

	@SuppressWarnings("unused")
	private long workerThreadHandle;

	@SuppressWarnings("unused")
	private long encodedDecoderFactoryHandle;

	private EncodedVideoFrameSink encodedVideoFrameSink;

	/**
	 * Serializes {@link #setEncodedVideoFrameSink(EncodedVideoFrameSink)},
	 * {@link #removeEncodedVideoFrameSink()} and {@link #dispose()}. Without
	 * this monitor, a {@code set} running concurrently with a {@code remove}
	 * could make the remove read a stale {@code null} field and silently skip
	 * the native sink removal, and a {@code set}/{@code remove} racing with
	 * {@code dispose()} could dereference the native wrapper factory after it
	 * has been released.
	 */
	private final Object sinkLock = new Object();


	/**
	 * Creates an instance of PeerConnectionFactory.
	 */
	public PeerConnectionFactory() {
		this(null, null);
	}

	/**
	 * Creates an instance of PeerConnectionFactory with the provided audio
	 * processing module.
	 *
	 * @param audioProcessing The custom audio processing module.
	 */
	public PeerConnectionFactory(AudioProcessing audioProcessing) {
		initialize(null, audioProcessing);
	}

	/**
	 * Creates an instance of PeerConnectionFactory with the provided audio
	 * device module.
	 *
	 * @param audioModule The custom audio device module.
	 */
	public PeerConnectionFactory(AudioDeviceModuleBase audioModule) {
		initialize(audioModule, null);
	}

	/**
	 * Creates an instance of PeerConnectionFactory with provided modules for
	 * audio devices and audio processing.
	 *
	 * @param audioModule     The custom audio device module.
	 * @param audioProcessing The custom audio processing module.
	 */
	public PeerConnectionFactory(AudioDeviceModuleBase audioModule,
			AudioProcessing audioProcessing) {
		initialize(audioModule, audioProcessing);
	}

	/**
	 * Creates an {@link AudioTrackSource}. The audio source may be used by one
	 * or more {@link AudioTrack}s.
	 *
	 * @param options Audio options to control the audio processing.
	 *
	 * @return The created audio source.
	 */
	public native AudioTrackSource createAudioSource(AudioOptions options);

	/**
	 * Creates an new {@link AudioTrack}. The audio track can be added to the
	 * {@link RTCPeerConnection} using the {@link RTCPeerConnection#addTrack
	 * addTrack} or {@link RTCPeerConnection#addTransceiver addTransceiver}
	 * methods.
	 *
	 * @param label  The identifier string of the audio track.
	 * @param source The audio source that provides audio data.
	 *
	 * @return The created audio track.
	 */
	public native AudioTrack createAudioTrack(String label, AudioTrackSource source);

	/**
	 * Creates a new {@link VideoTrack}. The video track can be added to the
	 * {@link RTCPeerConnection} using the {@link RTCPeerConnection#addTrack
	 * addTrack} or {@link RTCPeerConnection#addTransceiver addTransceiver}
	 * methods.
	 *
	 * @param label  The identifier string of the video track.
	 * @param source The video source that provides video data.
	 *
	 * @return The created video track.
	 */
	public native VideoTrack createVideoTrack(String label, VideoTrackSource source);

	/**
	 * Creates a new {@link RTCPeerConnection}.
	 *
	 * @param config   The peer connection configuration.
	 * @param observer The observer that receives peer connection state
	 *                 changes.
	 *
	 * @return The created peer connection.
	 */
	public native RTCPeerConnection createPeerConnection(
			RTCConfiguration config, PeerConnectionObserver observer);

	/**
	 * Sets the sink that receives encoded video frames before they are passed
	 * to the video decoder. At most one sink can be set at a time; setting a
	 * new sink replaces the current one.
	 * <p>
	 * A PeerConnectionFactory defines the media ingestion isolation domain:
	 * all PeerConnections created by this factory deliver their encoded video
	 * frames to the sink. If multiple media sources must be recorded
	 * independently, use a dedicated PeerConnectionFactory per source.
	 * <p>
	 * The sink is invoked synchronously on an internal WebRTC decode thread.
	 * Exceptions thrown by the sink are caught and logged by the native layer
	 * and do not affect the decoding. Once {@link
	 * #removeEncodedVideoFrameSink()} returns, the sink will not be invoked
	 * again; an already in-flight invocation may complete.
	 *
	 * @param sink The sink that receives encoded video frames.
	 */
	public void setEncodedVideoFrameSink(EncodedVideoFrameSink sink) {
		if (isNull(sink)) {
			throw new NullPointerException("EncodedVideoFrameSink must not be null");
		}

		synchronized (sinkLock) {
			setEncodedVideoFrameSinkInternal(sink);

			encodedVideoFrameSink = sink;
		}
	}

	/**
	 * Removes the currently set {@link EncodedVideoFrameSink}. If no sink is
	 * set, this is a no-op.
	 */
	public void removeEncodedVideoFrameSink() {
		synchronized (sinkLock) {
			if (nonNull(encodedVideoFrameSink)) {
				removeEncodedVideoFrameSinkInternal();

				encodedVideoFrameSink = null;
			}
		}
	}

	/**
	 * Returns the capabilities of the system for receiving media of the given
	 * media type.
	 *
	 * @param type The type value must be either {@code AUDIO} or {@code
	 *             VIDEO}.
	 *
	 * @return The supported capabilities for an {@link RTCRtpReceiver}.
	 */
	public native RTCRtpCapabilities getRtpReceiverCapabilities(MediaType type);

	/**
	 * Returns the capabilities of the system for sending media of the given
	 * media type.
	 *
	 * @param type The type value must be either {@code AUDIO} or {@code
	 *             VIDEO}.
	 *
	 * @return The supported capabilities for an {@link RTCRtpSender}.
	 */
	public native RTCRtpCapabilities getRtpSenderCapabilities(MediaType type);

	@Override
	public void dispose() {
		// Holding the sink lock across disposeNative guarantees that no
		// concurrent set/remove can dereference the wrapper factory after it
		// has been released by the media engine. Note: the sink is invoked on
		// an internal decode thread, so onEncodedVideoFrame must not
		// synchronously re-enter set/remove/dispose of this factory while
		// another thread is disposing it.
		synchronized (sinkLock) {
			removeEncodedVideoFrameSink();

			disposeNative();
		}
	}

	private native void setEncodedVideoFrameSinkInternal(EncodedVideoFrameSink sink);

	private native void removeEncodedVideoFrameSinkInternal();

	private native void disposeNative();

	private native void initialize(AudioDeviceModuleBase audioModule,
			AudioProcessing audioProcessing);

}
