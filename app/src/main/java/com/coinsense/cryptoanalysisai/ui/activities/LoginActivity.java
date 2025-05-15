package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.coinsense.cryptoanalysisai.MainActivity;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.databinding.ActivityLoginBinding;
import com.coinsense.cryptoanalysisai.services.BillingManager;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private FirebaseAuth firebaseAuth;
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;

    // ActivityResultLauncher 변경
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    GoogleSignInAccount account = task.getResult(ApiException.class);

                    // 계정 정보로 Firebase 인증
                    if (account != null) {
                        firebaseAuthWithGoogle(account.getIdToken());
                    }
                } catch (ApiException e) {
                    Log.e(TAG, "Google 로그인 실패: " + e.getStatusCode() + ": " + e.getMessage(), e);
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

    // 기존 One Tap 코드 대신 아래 코드 사용
    private void signInWithGoogle() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGoogleSignIn.setEnabled(false);

        // 표준 Google 로그인 구성
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        // 계정 선택 화면을 항상 보여주도록 설정
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
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

                            // 로그인 성공 후 사용자의 구독 상태 동기화
                            syncUserSubscription(user.getUid());
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

    // 사용자 구독 정보 동기화 메소드 추가
    private void syncUserSubscription(String userId) {
        // BillingManager에 현재 사용자 ID 설정
        BillingManager billingManager = BillingManager.getInstance(this);
        billingManager.setCurrentUserId(userId);

        // 구독 상태 동기화
        billingManager.syncSubscriptions();
    }
}