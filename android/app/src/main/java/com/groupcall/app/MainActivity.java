package com.groupcall.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 100;
    private EditText roomIdInput;
    private Button joinButton, createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        roomIdInput = findViewById(R.id.roomIdInput);
        joinButton = findViewById(R.id.joinButton);
        createButton = findViewById(R.id.createButton);

        if (!checkPermissions()) {
            requestPermissions();
        }

        joinButton.setOnClickListener(v -> {
            String roomId = roomIdInput.getText().toString().trim();
            if (!roomId.isEmpty()) {
                startCall(roomId, false);
            } else {
                Toast.makeText(this, "Enter room ID", Toast.LENGTH_SHORT).show();
            }
        });

        createButton.setOnClickListener(v -> {
            String roomId = roomIdInput.getText().toString().trim();
            if (roomId.isEmpty()) {
                roomId = "room_" + System.currentTimeMillis();
            }
            startCall(roomId, true);
        });
    }

    private void startCall(String roomId, boolean isCreator) {
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("ROOM_ID", roomId);
        intent.putExtra("IS_CREATOR", isCreator);
        startActivity(intent);
    }

    private boolean checkPermissions() {
        String[] perms = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        };
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        }, PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show();
            }
        }
    }
}
