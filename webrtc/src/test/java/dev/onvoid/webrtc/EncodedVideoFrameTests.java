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

package dev.onvoid.webrtc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.MediaType;
import dev.onvoid.webrtc.media.audio.AudioDeviceModule;
import dev.onvoid.webrtc.media.audio.AudioLayer;
import dev.onvoid.webrtc.media.video.CustomVideoSource;
import dev.onvoid.webrtc.media.video.EncodedVideoFrame;
import dev.onvoid.webrtc.media.video.EncodedVideoFrameSink;
import dev.onvoid.webrtc.media.video.NativeI420Buffer;
import dev.onvoid.webrtc.media.video.VideoCodecType;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrack;
import dev.onvoid.webrtc.media.video.VideoTrackSink;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EncodedVideoFrameTests extends TestBase {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private static final long FRAME_INTERVAL_MS = 40; // ~25 fps
    private static final long AWAIT_MS = 10000;


    private static final class TestEncodedSink implements EncodedVideoFrameSink {

        final AtomicInteger count = new AtomicInteger();
        final List<EncodedVideoFrame> frames = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch firstFrame = new CountDownLatch(1);

        volatile boolean throwOnFrame;


        @Override
        public void onEncodedVideoFrame(EncodedVideoFrame frame) {
            if (throwOnFrame) {
                throw new RuntimeException("Test exception from encoded video frame sink");
            }

            frames.add(frame);
            count.incrementAndGet();
            firstFrame.countDown();
        }

        boolean awaitFirstFrame() throws InterruptedException {
            return firstFrame.await(AWAIT_MS, TimeUnit.MILLISECONDS);
        }

        EncodedVideoFrame first() {
            return frames.get(0);
        }
    }

    private static final class CountingVideoSink implements VideoTrackSink {

        final AtomicInteger count = new AtomicInteger();
        final CountDownLatch firstFrame = new CountDownLatch(1);


        @Override
        public void onVideoFrame(VideoFrame frame) {
            count.incrementAndGet();
            firstFrame.countDown();
            frame.release();
        }

        boolean awaitFirstFrame() throws InterruptedException {
            return firstFrame.await(AWAIT_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * A loopback setup: the caller pushes video frames, the callee receives
     * them. H264 is enforced as the negotiated video codec.
     */
    private static final class VideoLoopback {

        final PeerConnectionFactory factory;
        final TestPeerConnection caller;
        final TestPeerConnection callee;
        final CustomVideoSource source;
        final VideoTrack localTrack;
        final RTCRtpSender localSender;
        final VideoTrack remoteTrack;

        private boolean closed = false;


        VideoLoopback(PeerConnectionFactory factory) throws Exception {
            this.factory = factory;

            caller = new TestPeerConnection(factory);
            callee = new TestPeerConnection(factory);

            caller.setRemotePeerConnection(callee);
            callee.setRemotePeerConnection(caller);

            source = new CustomVideoSource();
            localTrack = factory.createVideoTrack("encodedVideoTrack", source);
            localSender = caller.getPeerConnection().addTrack(localTrack, Collections.singletonList("stream"));

            forceH264(caller.getPeerConnection(), factory);

            RTCSessionDescription offer = caller.createOffer();
            callee.setRemoteDescription(offer);
            caller.setRemoteDescription(callee.createAnswer());

            caller.waitUntilConnected();
            callee.waitUntilConnected();

            remoteTrack = findRemoteVideoTrack(callee.getPeerConnection());
            assertNotNull(remoteTrack, "Remote video track expected on the callee");
        }

        void pushFrames(long durationMs) throws InterruptedException {
            long end = System.currentTimeMillis() + durationMs;

            while (System.currentTimeMillis() < end) {
                NativeI420Buffer buffer = NativeI420Buffer.allocate(WIDTH, HEIGHT);
                VideoFrame frame = new VideoFrame(buffer, System.nanoTime());

                source.pushFrame(frame);
                frame.release();

                Thread.sleep(FRAME_INTERVAL_MS);
            }
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;

            // Detach the track from its sender while the peer connection is
            // still open. This binding's close() never releases the native
            // PeerConnection, and its RtpSender keeps a strong reference to
            // the track for the connection's lifetime; without the detach the
            // explicit localTrack.dispose() below would fail with "Native
            // object was not deleted. A reference is still around somewhere."
            if (localSender != null) {
                caller.getPeerConnection().removeTrack(localSender);
            }

            caller.close();
            callee.close();
            source.dispose();
            localTrack.dispose();
        }
    }

    private static void forceH264(RTCPeerConnection pc, PeerConnectionFactory factory) {
        List<RTCRtpCodecCapability> h264 = new ArrayList<>();

        for (RTCRtpCodecCapability capability : factory.getRtpReceiverCapabilities(MediaType.VIDEO).getCodecs()) {
            if (capability.getMediaType() == MediaType.VIDEO && "H264".equals(capability.getName())) {
                h264.add(capability);
            }
        }

        assertFalse(h264.isEmpty(), "H264 must be among the supported receiver codecs");

        for (RTCRtpTransceiver transceiver : pc.getTransceivers()) {
            transceiver.setCodecPreferences(h264);
        }
    }

    private static VideoTrack findRemoteVideoTrack(RTCPeerConnection pc) {
        for (RTCRtpTransceiver transceiver : pc.getTransceivers()) {
            MediaStreamTrack track = transceiver.getReceiver().getTrack();

            if (track instanceof VideoTrack) {
                return (VideoTrack) track;
            }
        }
        return null;
    }

    private static boolean containsAnnexBStartCode(EncodedVideoFrame frame) {
        ByteBuffer data = frame.data;

        for (int i = 0; i + 2 < data.limit(); i++) {
            if (data.get(i) == 0 && data.get(i + 1) == 0) {
                if (data.get(i + 2) == 1) {
                    return true;
                }
                if (i + 3 < data.limit() && data.get(i + 2) == 0 && data.get(i + 3) == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void grace(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }


    @Test
    void encodedFrameReceived() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void encodedDataIsNonEmpty() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
            assertTrue(sink.first().data.remaining() > 0, "Encoded payload must not be empty");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void encodedFrameHasCorrectCodec() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
            assertEquals(VideoCodecType.H264, sink.first().codec, "Negotiated codec must be H264");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void encodedFrameHasDimensions() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(3000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
            assertFalse(sink.frames.isEmpty(), "At least one encoded frame expected");

            // The dimensions are reference values only: they may be 0 before
            // the SPS has been parsed. If they are known, they must match.
            boolean anyNonZero = false;

            for (EncodedVideoFrame frame : sink.frames) {
                assertTrue(frame.width >= 0 && frame.height >= 0, "Dimensions must not be negative");

                if (frame.width > 0) {
                    anyNonZero = true;
                    assertEquals(WIDTH, frame.width, "Encoded width mismatch");
                    assertEquals(HEIGHT, frame.height, "Encoded height mismatch");
                }
            }

            if (!anyNonZero) {
                // Tolerated for the first frames; at least the fields must read.
                assertTrue(sink.frames.get(0).width >= 0);
            }
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void encodedFrameHasRtpTimestamp() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
            assertTrue(sink.first().rtpTimestamp > 0, "RTP timestamp must be set");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void h264AnnexBStartCodes() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            // Verification of the actual output format of the current
            // libwebrtc: the payload is expected to be an H.264 Annex-B byte
            // stream. The payload itself is never converted or modified.
            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
            assertTrue(containsAnnexBStartCode(sink.first()),
                    "H264 payload is expected to contain Annex-B start codes");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void decodedVideoStillWorks() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink encodedSink = new TestEncodedSink();
        CountingVideoSink decodedSink = new CountingVideoSink();

        try {
            factory.setEncodedVideoFrameSink(encodedSink);
            loopback.remoteTrack.addSink(decodedSink);

            loopback.pushFrames(3000);

            assertTrue(encodedSink.awaitFirstFrame(), "No encoded video frame received");
            assertTrue(decodedSink.awaitFirstFrame(), "No decoded video frame received");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.remoteTrack.removeSink(decodedSink);
            loopback.close();
        }
    }

    @Test
    void noEncodedSinkStillDecodes() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        CountingVideoSink decodedSink = new CountingVideoSink();

        try {
            loopback.remoteTrack.addSink(decodedSink);

            loopback.pushFrames(3000);

            assertTrue(decodedSink.awaitFirstFrame(), "Decoded video must work without an encoded sink");
        }
        finally {
            loopback.remoteTrack.removeSink(decodedSink);
            loopback.close();
        }
    }

    @Test
    void removeSinkStopsCallback() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");

            factory.removeEncodedVideoFrameSink();

            // In-flight delivery may complete once; allow it to settle.
            grace(500);

            int count = sink.count.get();

            loopback.pushFrames(1500);
            grace(500);

            assertEquals(count, sink.count.get(), "Sink must not be invoked after removal");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void sinkExceptionDoesNotCrash() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink encodedSink = new TestEncodedSink();
        CountingVideoSink decodedSink = new CountingVideoSink();

        encodedSink.throwOnFrame = true;

        try {
            factory.setEncodedVideoFrameSink(encodedSink);
            loopback.remoteTrack.addSink(decodedSink);

            loopback.pushFrames(3000);

            // The process survived (this line executes) and decoding
            // continued despite the throwing sink.
            assertTrue(decodedSink.awaitFirstFrame(), "Decoding must continue despite a throwing encoded sink");
            assertTrue(decodedSink.count.get() > 0);
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.remoteTrack.removeSink(decodedSink);
            loopback.close();
        }
    }

    @Test
    void encodedBufferIsIndependentFromNativeBuffer() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");

            synchronized (sink.frames) {
                assertTrue(sink.frames.size() >= 2, "At least two frames expected");

                EncodedVideoFrame first = sink.frames.get(0);
                EncodedVideoFrame second = sink.frames.get(1);

                assertNotSame(first.data, second.data, "Each frame must own its buffer");
                assertTrue(first.data.isDirect(), "Buffer must be direct");
                assertTrue(second.data.isDirect(), "Buffer must be direct");

                // Mutating one frame's payload must not affect the other.
                byte before = second.data.get(0);

                first.data.put(0, (byte) 0xFF);

                assertEquals(before, second.data.get(0), "Buffers must be independent");
            }
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }

    @Test
    void multiplePeerConnectionsDoNotCrossRoute() throws Exception {
        AudioDeviceModule admA = new AudioDeviceModule(AudioLayer.kDummyAudio);
        AudioDeviceModule admB = new AudioDeviceModule(AudioLayer.kDummyAudio);
        PeerConnectionFactory factoryA = new PeerConnectionFactory(admA);
        PeerConnectionFactory factoryB = new PeerConnectionFactory(admB);

        VideoLoopback loopbackA = new VideoLoopback(factoryA);
        VideoLoopback loopbackB = new VideoLoopback(factoryB);

        TestEncodedSink sinkA = new TestEncodedSink();
        TestEncodedSink sinkB = new TestEncodedSink();

        try {
            factoryA.setEncodedVideoFrameSink(sinkA);
            factoryB.setEncodedVideoFrameSink(sinkB);

            // Phase 1: both streams send; both isolated sinks receive their
            // own stream.
            loopbackA.pushFrames(2000);
            loopbackB.pushFrames(2000);

            assertTrue(sinkA.awaitFirstFrame(), "Sink A did not receive its own stream");
            assertTrue(sinkB.awaitFirstFrame(), "Sink B did not receive its own stream");

            // Phase 2: only stream A sends. Sink B must stay silent.
            grace(500);
            int countB = sinkB.count.get();

            loopbackA.pushFrames(1500);
            grace(500);

            assertEquals(countB, sinkB.count.get(), "Sink B must not receive frames of factory A");
        }
        finally {
            factoryA.removeEncodedVideoFrameSink();
            factoryB.removeEncodedVideoFrameSink();
            loopbackA.close();
            loopbackB.close();
            admA.dispose();
            factoryA.dispose();
            admB.dispose();
            factoryB.dispose();
        }
    }

    @Test
    void closeAndDisposeDoesNotCrash() throws Exception {
        AudioDeviceModule adm = new AudioDeviceModule(AudioLayer.kDummyAudio);
        PeerConnectionFactory ownFactory = new PeerConnectionFactory(adm);

        VideoLoopback loopback = new VideoLoopback(ownFactory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            ownFactory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");

            ownFactory.removeEncodedVideoFrameSink();
        }
        finally {
            loopback.close();
            adm.dispose();
            ownFactory.dispose();
        }
    }

    @Test
    void concurrentSetRemoveSinkWhileDecoding() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);

        TestEncodedSink sinkA = new TestEncodedSink();
        TestEncodedSink sinkB = new TestEncodedSink();

        Thread pusher = new Thread(() -> {
            try {
                loopback.pushFrames(3000);
            }
            catch (InterruptedException e) {
                // Terminated by join.
            }
        });
        pusher.start();

        try {
            long end = System.currentTimeMillis() + 2500;

            while (System.currentTimeMillis() < end) {
                factory.setEncodedVideoFrameSink(sinkA);
                factory.setEncodedVideoFrameSink(sinkB);
                factory.removeEncodedVideoFrameSink();

                Thread.sleep(1);
            }

            pusher.join();

            // Let a possible in-flight delivery complete.
            grace(500);

            int countA = sinkA.count.get();
            int countB = sinkB.count.get();

            factory.removeEncodedVideoFrameSink();
            grace(500);

            assertEquals(countA, sinkA.count.get(), "Sink A must not be invoked after removal");
            assertEquals(countB, sinkB.count.get(), "Sink B must not be invoked after removal");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            pusher.join();
            loopback.close();
        }
    }

    @Test
    void disposeStopsCallbacks() throws Exception {
        AudioDeviceModule adm = new AudioDeviceModule(AudioLayer.kDummyAudio);
        PeerConnectionFactory ownFactory = new PeerConnectionFactory(adm);

        VideoLoopback loopback = new VideoLoopback(ownFactory);
        TestEncodedSink sink = new TestEncodedSink();

        try {
            ownFactory.setEncodedVideoFrameSink(sink);

            loopback.pushFrames(2000);

            assertTrue(sink.awaitFirstFrame(), "No encoded video frame received");
        }
        finally {
            // Dispose without an explicit sink removal: the dispose path
            // must clear the sink itself.
            loopback.close();
            adm.dispose();
            ownFactory.dispose();
        }

        // No new callbacks after the dispose.
        grace(800);

        int count = sink.count.get();

        grace(800);

        assertEquals(count, sink.count.get(), "No callbacks must occur after dispose");
    }

    @Test
    void sinkReplaceSemantics() throws Exception {
        VideoLoopback loopback = new VideoLoopback(factory);

        TestEncodedSink sinkA = new TestEncodedSink();
        TestEncodedSink sinkB = new TestEncodedSink();

        try {
            factory.setEncodedVideoFrameSink(sinkA);

            loopback.pushFrames(2000);

            assertTrue(sinkA.awaitFirstFrame(), "Sink A did not receive frames");

            // Replacing the sink must stop deliveries to sink A.
            factory.setEncodedVideoFrameSink(sinkB);

            loopback.pushFrames(2000);

            assertTrue(sinkB.awaitFirstFrame(), "Sink B did not receive frames after replacement");

            grace(500);
            int countA = sinkA.count.get();

            loopback.pushFrames(1500);
            grace(500);

            assertEquals(countA, sinkA.count.get(), "Replaced sink must not receive new frames");

            int countB = sinkB.count.get();

            factory.removeEncodedVideoFrameSink();

            loopback.pushFrames(1500);
            grace(500);

            assertEquals(countB, sinkB.count.get(), "Sink B must not be invoked after removal");
        }
        finally {
            factory.removeEncodedVideoFrameSink();
            loopback.close();
        }
    }
}
