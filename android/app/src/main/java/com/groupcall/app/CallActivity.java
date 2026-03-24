package com.groupcall.app;

import android.opengl.EGLContext;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.*;
import java.util.*;
import java.nio.ByteBuffer;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private static final String SERVER_URL = "http://YOUR_SERVER_IP:3000"; // Change this

    private Socket socket;
    private PeerConnection peerConnection;
    private EglBase eglBase;
    private SurfaceViewRenderer localVideo, remoteVideo;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioTrack audioTrack;
    private MediaStream localStream;

    private String roomId;
    private String userId;
    private Map<String, PeerConnection> remotePeerConnections = new HashMap<>();
    private Map<String, SurfaceViewRenderer> remoteVideoViews = new HashMap<>();
    private GridLayout remoteVideosGrid;
    private FrameLayout localVideoContainer;

    private boolean isCreator;
    private Button muteBtn, cameraBtn, endBtn;
    private boolean isMuted = false;
    private boolean isCameraOff = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        roomId = getIntent().getStringExtra("ROOM_ID");
        isCreator = getIntent().getBooleanExtra("IS_CREATOR", false);
        userId = "user_" + System.currentTimeMillis();

        eglBase = EglBase.create();
        initViews();
        initWebRTC();
        connectSocket();
        setupButtons();
    }

    private void initViews() {
        localVideo = findViewById(R.id.localVideo);
        remoteVideosGrid = findViewById(R.id.remoteVideosGrid);
        muteBtn = findViewById(R.id.muteBtn);
        cameraBtn = findViewById(R.id.cameraBtn);
        endBtn = findViewById(R.id.endBtn);
        TextView roomTitle = findViewById(R.id.roomTitle);
        roomTitle.setText("Room: " + roomId);

        localVideo.init(eglBase.getEglBaseContext(), null);
        localVideo.setMirror(true);
        localVideo.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
    }

    private void initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        );

        PeerConnectionFactory factory = PeerConnectionFactory.builder()
            .setOptions(new PeerConnectionFactory.Options())
            .createPeerConnectionFactory();

        // Video source
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
            "video_thread", eglBase.getEglBaseContext()
        );
        videoSource = factory.createVideoSource(false);
        VideoCapturer capturer = createCapturer();
        capturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        capturer.startCapture(1280, 720, 30);

        localVideoTrack = factory.createVideoTrack("video_" + userId, videoSource);
        localVideoTrack.addSink(localVideo);

        // Audio source
        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("audio_" + userId, audioSource);

        // Local stream
        localStream = factory.createLocalMediaStream("local_" + userId);
        localStream.addTrack(localVideoTrack);
        localStream.addTrack(audioTrack);
    }

    private VideoCapturer createCapturer() {
        return new Camera2Enumerator(this)
            .getDeviceNames()
            .stream()
            .filter(name -> isFrontCamera(name))
            .findFirst()
            .map(name -> new Camera2Capturer(this, name, new Camera2Capturer.CameraEventsHandler() {
                public void onCameraOpening() {}
                public void onCameraOpen(String name) {}
                public void onCameraClosing() {}
                public void onCameraError(String name) {}
                public void onFirstFrameAvailable() {}
                public void onCameraDisconnected() {}
            }))
            .orElse(null);
    }

    private boolean isFrontCamera(String name) {
        return new Camera2Enumerator(this).isFrontFacing(name);
    }

    private void connectSocket() {
        try {
            IO.Options options = new IO.Options();
            options.forceNew = true;
            socket = IO.socket(SERVER_URL, options);
            socket.connect();

            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "Socket connected");
                runOnUiThread(() -> Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show());
                joinRoom();
            });

            socket.on("room-users", this::handleRoomUsers);
            socket.on("user-joined", this::handleUserJoined);
            socket.on("user-left", this::handleUserLeft);
            socket.on("offer", this::handleOffer);
            socket.on("answer", this::handleAnswer);
            socket.on("ice-candidate", this::handleIceCandidate);

        } catch (Exception e) {
            Log.e(TAG, "Socket error: " + e.getMessage());
        }
    }

    private void joinRoom() {
        try {
            JSONObject data = new JSONObject();
            data.put("roomId", roomId);
            data.put("userId", userId);
            socket.emit("join-room", data);
        } catch (Exception e) {
            Log.e(TAG, "Join room error: " + e.getMessage());
        }
    }

    private void handleRoomUsers(Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            JSONArray users = data.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                String remoteUserId = users.getString(i);
                createPeerConnection(remoteUserId, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle room users error: " + e.getMessage());
        }
    }

    private void handleUserJoined(Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String remoteUserId = data.getString("userId");
            createPeerConnection(remoteUserId, true);
        } catch (Exception e) {
            Log.e(TAG, "User joined error: " + e.getMessage());
        }
    }

    private void handleUserLeft(Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String remoteUserId = data.getString("userId");
            removeRemoteUser(remoteUserId);
        } catch (Exception e) {
            Log.e(TAG, "User left error: " + e.getMessage());
        }
    }

    private void handleOffer(Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String from = data.getString("from");
            JSONObject offer = data.getJSONObject("offer");
            
            PeerConnection pc = getOrCreatePeerConnection(from);
            pc.setRemoteDescription(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription desc) {}
                @Override
                public void onSetSuccess() {
                    pc.createAnswer(new MySdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription desc) {
                            try {
                                pc.setLocalDescription(new MySdpObserver() {
                                    @Override public void onCreateSuccess(SessionDescription d) {}
                                    @Override public void onSetSuccess() {
                                        JSONObject response = new JSONObject();
                                        response.put("to", from);
                                        response.put("from", userId);
                                        response.put("answer", desc);
                                        socket.emit("answer", response);
                                    }
                                }, desc);
                            } catch (Exception e) {
                                Log.e(TAG, "Answer error: " + e.getMessage());
                            }
                        }
                    }, new MediaConstraints());
                }
            }, new SessionDescription(offer));
        } catch (Exception e) {
            Log.e(TAG, "Offer error: " + e.getMessage());
        }
    }

    private void handleAnswer(Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String from = data.getString("from");
            JSONObject answer = data.getJSONObject("answer");
            
            PeerConnection pc = remotePeerConnections.get(from);
            if (pc != null) {
                pc.setRemoteDescription(new MySdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription desc) {}
                    @Override public void onSetSuccess() {
                        Log.d(TAG, "Answer set successfully");
                    }
                }, new SessionDescription(answer));
            }
        } catch (Exception e) {
            Log.e(TAG, "Answer error: " + e.getMessage());
        }
    }

    private void handleIceCandidate(Object... args) {
        try {
            JSONObject data = (JSONObject) args[0];
            String from = data.getString("from");
            JSONObject candidate = data.getJSONObject("candidate");
            
            PeerConnection pc = remotePeerConnections.get(from);
            if (pc != null) {
                IceCandidate iceCandidate = new IceCandidate(
                    candidate.optString("sdpMid", ""),
                    candidate.optInt("sdpMLineIndex", 0),
                    candidate.optString("candidate", "")
                );
                pc.addIceCandidate(iceCandidate);
            }
        } catch (Exception e) {
            Log.e(TAG, "ICE candidate error: " + e.getMessage());
        }
    }

    private PeerConnection getOrCreatePeerConnection(String userId) {
        if (!remotePeerConnections.containsKey(userId)) {
            createPeerConnection(userId, false);
        }
        return remotePeerConnections.get(userId);
    }

    private void createPeerConnection(String remoteUserId, boolean createOffer) {
        try {
            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").build());
            iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").build());

            MediaConstraints constraints = new MediaConstraints();
            constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

            PeerConnection pc = PeerConnectionFactory.builder()
                .createPeerConnectionFactory()
                .createPeerConnection(iceServers, constraints, new MyPeerObserver());

            pc.addStream(localStream);

            remotePeerConnections.put(remoteUserId, pc);

            if (createOffer) {
                pc.createOffer(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription desc) {
                        pc.setLocalDescription(new MySdpObserver() {
                            @Override public void onCreateSuccess(SessionDescription d) {}
                            @Override public void onSetSuccess() {
                                try {
                                    JSONObject data = new JSONObject();
                                    data.put("to", remoteUserId);
                                    data.put("from", userId);
                                    data.put("offer", desc);
                                    socket.emit("offer", data);
                                } catch (Exception e) {
                                    Log.e(TAG, "Send offer error: " + e.getMessage());
                                }
                            }
                        }, desc);
                    }
                }, new MediaConstraints());
            }

            addRemoteVideoView(remoteUserId, pc);

        } catch (Exception e) {
            Log.e(TAG, "Create PC error: " + e.getMessage());
        }
    }

    private void addRemoteVideoView(String remoteUserId, PeerConnection pc) {
        runOnUiThread(() -> {
            SurfaceViewRenderer remoteView = new SurfaceViewRenderer(this);
            remoteView.init(eglBase.getEglBaseContext(), null);
            remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            
            remoteVideosGrid.addView(remoteView, params);
            remoteVideoViews.put(remoteUserId, remoteView);

            // Find video track
            for (MediaStream stream : pc.getRemoteStreams()) {
                for (VideoTrack track : stream.videoTracks) {
                    track.addSink(remoteView);
                }
            }
        });
    }

    private void removeRemoteUser(String remoteUserId) {
        runOnUiThread(() -> {
            SurfaceViewRenderer view = remoteVideoViews.remove(remoteUserId);
            if (view != null) {
                remoteVideosGrid.removeView(view);
                view.release();
            }
        });
        PeerConnection pc = remotePeerConnections.remove(remoteUserId);
        if (pc != null) {
            pc.close();
        }
    }

    private void setupButtons() {
        muteBtn.setOnClickListener(v -> {
            isMuted = !isMuted;
            audioTrack.setEnabled(!isMuted);
            muteBtn.setText(isMuted ? "🎤 Unmute" : "🎤 Mute");
        });

        cameraBtn.setOnClickListener(v -> {
            isCameraOff = !isCameraOff;
            localVideoTrack.setEnabled(!isCameraOff);
            cameraBtn.setText(isCameraOff ? "📷 Camera On" : "📷 Camera Off");
        });

        endBtn.setOnClickListener(v -> endCall());
    }

    private void endCall() {
        try {
            JSONObject data = new JSONObject();
            data.put("roomId", roomId);
            data.put("userId", userId);
            socket.emit("leave-room", data);
        } catch (Exception e) {
            Log.e(TAG, "Leave room error: " + e.getMessage());
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        localVideoTrack.dispose();
        audioTrack.dispose();
        localStream.dispose();
        videoSource.dispose();
        
        for (PeerConnection pc : remotePeerConnections.values()) {
            pc.close();
        }
        remotePeerConnections.clear();
        
        for (SurfaceViewRenderer view : remoteVideoViews.values()) {
            view.release();
        }
        remoteVideoViews.clear();
        
        if (localVideo != null) {
            localVideo.release();
        }
        
        if (eglBase != null) {
            eglBase.release();
        }
        
        if (socket != null && socket.connected()) {
            socket.disconnect();
        }
    }

    // Helper classes
    private abstract class MySdpObserver implements SdpObserver {
        @Override public void onCreateFailure(String error) { Log.e(TAG, "Create failure: " + error); }
        @Override public void onSetFailure(String error) { Log.e(TAG, "Set failure: " + error); }
    }

    private class MyPeerObserver implements PeerConnection.Observer {
        @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
        @Override public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
        @Override public void onIceCandidate(IceCandidate candidate) {
            // Send ICE candidate to remote peer
        }
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onAddStream(MediaStream stream) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onDataChannel(DataChannel dc) {}
        @Override public void onRenegotiationNeeded() {}
        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
    }
}
