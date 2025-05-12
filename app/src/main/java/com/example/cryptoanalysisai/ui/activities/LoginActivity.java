package com.example.cryptoanalysisai.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cryptoanalysisai.MainActivity;
import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.databinding.ActivityLoginBinding;
import com.example.cryptoanalysisai.utils.Constants;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.BeginSignInResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private FirebaseAuth firebaseAuth;
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;

    private final ActivityResultLauncher<IntentSenderRequest> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                try {
                    SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(result.getData());
                    String idToken = credential.getGoogleIdToken();
                    if (idToken != null) {
                        // Got an ID token from Google. Use it to authenticate with Firebase.
                        firebaseAuthWithGoogle(idToken);
                    }
                } catch (ApiException e) {
                    Log.e(TAG, "Google 로그인 실패: " + e.getMessage(), e);
                    handleSignInFailure("Google 로그인에 실패했습니다.");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 이미 로그인되어 있는지 확인
        if (isUserSignedIn()) {
            proceedToMainActivity();
            return;
        }

        // Firebase 인증 초기화
        firebaseAuth = FirebaseAuth.getInstance();

        // Google One Tap 설정
        oneTapClient = Identity.getSignInClient(this);
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .build();

        // 구글 로그인 버튼 클릭 리스너
        binding.btnGoogleSignIn.setOnClickListener(v -> {
            signInWithGoogle();
        });
    }

    private void signInWithGoogle() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGoogleSignIn.setEnabled(false);

        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, new OnSuccessListener<BeginSignInResult>() {
                    @Override
                    public void onSuccess(BeginSignInResult result) {
                        try {
                            IntentSenderRequest intentSenderRequest =
                                    new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build();
                            signInLauncher.launch(intentSenderRequest);
                        } catch (Exception e) {
                            Log.e(TAG, "Google 로그인 실행 오류: " + e.getMessage(), e);
                            handleSignInFailure("Google 로그인 시작에 실패했습니다.");
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Google One Tap 로그인 실패: " + e.getMessage(), e);
                        handleSignInFailure("Google 로그인 요청이 실패했습니다.");
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            saveUserData(user);
                        }
                        proceedToMainActivity();
                    } else {
                        Log.w(TAG, "Firebase 인증 실패", task.getException());
                        handleSignInFailure("인증에 실패했습니다.");
                    }
                });
    }

    private void saveUserData(FirebaseUser user) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.PREF_IS_LOGGED_IN, true);
        editor.putString(Constants.PREF_USER_EMAIL, user.getEmail());
        editor.putString(Constants.PREF_USER_DISPLAY_NAME, user.getDisplayName());
        editor.putString(Constants.PREF_USER_ID, user.getUid());
        editor.apply();
    }

    private void handleSignInFailure(String errorMessage) {
        runOnUiThread(() -> {
            binding.btnGoogleSignIn.setEnabled(true);
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        });
    }

    private void proceedToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean isUserSignedIn() {
        // SharedPreferences에서 로그인 상태 확인
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(Constants.PREF_IS_LOGGED_IN, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}