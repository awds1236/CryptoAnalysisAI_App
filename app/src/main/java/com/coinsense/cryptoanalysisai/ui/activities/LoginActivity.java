package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.coinsense.cryptoanalysisai.MainActivity;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.databinding.ActivityLoginBinding;
import com.coinsense.cryptoanalysisai.services.BillingManager;
import com.coinsense.cryptoanalysisai.services.FirebaseSubscriptionManager;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;
import com.coinsense.cryptoanalysisai.utils.TermsDialogUtils;
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
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class LoginActivity extends BaseActivity {

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

        // 이용약관 및 개인정보처리방침 링크 설정
        setupTermsLinks();
    }

    /**
     * 이용약관 및 개인정보처리방침 링크 설정
     */
    private void setupTermsLinks() {
        String termsText = getString(R.string.terms_agreement_link);
        SpannableString spannableString = new SpannableString(termsText);

        // 현재 언어 확인
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String language = prefs.getString("pref_language", "ko");

        // 언어에 따른 키워드 설정
        String termsKeyword, privacyKeyword;
        if ("en".equals(language)) {
            termsKeyword = "Terms of Service";
            privacyKeyword = "Privacy Policy";
        } else {
            termsKeyword = "이용약관";
            privacyKeyword = "개인정보처리방침";
        }

        // 이용약관 링크 설정
        int termsStart = termsText.indexOf(termsKeyword);
        if (termsStart != -1) {
            int termsEnd = termsStart + termsKeyword.length();

            ClickableSpan termsClickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    TermsDialogUtils.showTermsOfServiceDialog(LoginActivity.this);
                }
            };

            spannableString.setSpan(termsClickableSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new UnderlineSpan(), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 개인정보처리방침 링크 설정
        int privacyStart = termsText.indexOf(privacyKeyword);
        if (privacyStart != -1) {
            int privacyEnd = privacyStart + privacyKeyword.length();

            ClickableSpan privacyClickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    TermsDialogUtils.showPrivacyPolicyDialog(LoginActivity.this);
                }
            };

            spannableString.setSpan(privacyClickableSpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new UnderlineSpan(), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // TextView에 설정
        binding.tvTerms.setText(spannableString);
        binding.tvTerms.setMovementMethod(LinkMovementMethod.getInstance());
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

                            // 구독 정보 초기화
                            SubscriptionManager.getInstance(this).clearLocalSubscriptionData();

                            // 사용자 로그인 시 구독 관리자에 사용자 설정
                            SubscriptionManager.getInstance(this).updateUser(user);

                            // Google Play에서 구독 정보 가져오기
                            BillingManager.getInstance(this).queryPurchases();

                            // 초기 구독 데이터 생성 (없을 경우만)
                            createInitialSubscriptionData(user);
                        }
                        proceedToMainActivity();
                    } else {
                        Log.w(TAG, "Firebase 인증 실패", task.getException());
                        handleSignInFailure("인증에 실패했습니다.");
                    }
                });
    }

    /**
     * 신규 사용자의 초기 구독 데이터 생성
     */
    private void createInitialSubscriptionData(FirebaseUser user) {
        // Firebase 구독 데이터베이스 참조
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference subscriptionRef = database.getReference("subscriptions")
                .child(user.getUid());

        // 이미 데이터가 있는지 확인
        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // 데이터가 없는 경우만 초기 데이터 생성
                if (!snapshot.exists()) {
                    FirebaseSubscriptionManager.SubscriptionData initialData =
                            new FirebaseSubscriptionManager.SubscriptionData();
                    initialData.setSubscriptionType(Constants.SUBSCRIPTION_NONE);
                    initialData.setExpiryTimestamp(0);
                    initialData.setStartTimestamp(System.currentTimeMillis());
                    initialData.setAutoRenewing(false);
                    initialData.setMonthlyPrice("월 ₩15,000");
                    initialData.setYearlyPrice("연 ₩125,000 (월 ₩10,400)");

                    subscriptionRef.setValue(initialData)
                            .addOnSuccessListener(aVoid -> Log.d("LoginActivity", "초기 구독 데이터 생성 완료"))
                            .addOnFailureListener(e -> Log.e("LoginActivity", "초기 구독 데이터 생성 실패: " + e.getMessage()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LoginActivity", "초기 구독 데이터 확인 실패: " + error.getMessage());
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
        // 1. Firebase Auth 상태를 먼저 확인
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Firebase에 로그인되어 있다면 SharedPreferences도 업데이트
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Constants.PREF_IS_LOGGED_IN, true);
            editor.putString(Constants.PREF_USER_EMAIL, currentUser.getEmail());
            editor.putString(Constants.PREF_USER_DISPLAY_NAME, currentUser.getDisplayName());
            editor.putString(Constants.PREF_USER_ID, currentUser.getUid());
            editor.apply();

            return true;
        }

        // 2. Firebase에 없다면 SharedPreferences 확인
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(Constants.PREF_IS_LOGGED_IN, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

}