package com.masterroll.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private AdView adView;
    private WebAppInterface webAppInterface;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // Layout with Ads
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        setContentView(mainLayout);

        // WebView Area
        webView = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mainLayout.addView(webView, webParams);

        // Initialize Ads
        MobileAds.initialize(this, initializationStatus -> {});

        // AdView at bottom
        adView = new AdView(this);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId("ca-app-pub-3940256099942544/6300978111"); // Replace with REAL ID
        LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        adParams.gravity = Gravity.CENTER_HORIZONTAL;
        mainLayout.addView(adView, adParams);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webAppInterface = new WebAppInterface(this, webView);
        webView.addJavascriptInterface(webAppInterface, "AndroidStore");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/attendance-app.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    public static class WebAppInterface {
        Context mContext;
        SharedPreferences prefs;
        WebView mWebView;
        
        private FirebaseAuth mAuth;
        private DatabaseReference mDatabase;
        private String mVerificationId;

        WebAppInterface(Context c, WebView webView) { 
            mContext = c; 
            mWebView = webView;
            prefs = c.getSharedPreferences("MasterRollData", Context.MODE_PRIVATE);
            mAuth = FirebaseAuth.getInstance();
            
            // Note: If cloud sync fails, ensure your Realtime Database URL is correct in google-services.json
            // or use: FirebaseDatabase.getInstance("https://your-db-url.firebaseio.com/").getReference();
            try {
                mDatabase = FirebaseDatabase.getInstance().getReference();
            } catch (Exception e) {
                // Fallback for missing database URL in JSON
                mDatabase = null;
            }
        }

        @JavascriptInterface
        public void saveData(String key, String data) {
            prefs.edit().putString(key, data).apply();
            if (mAuth.getCurrentUser() != null) {
                String uid = mAuth.getCurrentUser().getUid();
                mDatabase.child("users").child(uid).child(key).setValue(data);
            }
        }

        @JavascriptInterface
        public String loadData(String key) {
            return prefs.getString(key, null);
        }

        @JavascriptInterface
        public void sendOtp(String phoneNumber) {
            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber("+91" + phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity((MainActivity) mContext)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                            String code = credential.getSmsCode();
                            if (code != null) {
                                mWebView.post(() -> mWebView.loadUrl("javascript:onOtpAutoFilled('" + code + "')"));
                            }
                        }

                        @Override
                        public void onVerificationFailed(@NonNull FirebaseException e) {
                            mWebView.post(() -> mWebView.loadUrl("javascript:alert('OTP Failed: " + e.getMessage() + "')"));
                        }

                        @Override
                        public void onCodeSent(@NonNull String verificationId,
                                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
                            mVerificationId = verificationId;
                            mWebView.post(() -> mWebView.loadUrl("javascript:showAuthStep('otp')"));
                        }
                    })
                    .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        }

        @JavascriptInterface
        public void verifyOtp(String code) {
            if (mVerificationId == null) return;
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener((MainActivity) mContext, task -> {
                        if (task.isSuccessful()) {
                            mWebView.post(() -> mWebView.loadUrl("javascript:enterApp()"));
                            syncDataFromCloud();
                        } else {
                            mWebView.post(() -> mWebView.loadUrl("javascript:alert('Invalid OTP')"));
                        }
                    });
        }

        private void syncDataFromCloud() {
            if (mAuth.getCurrentUser() == null) return;
            String uid = mAuth.getCurrentUser().getUid();
            mDatabase.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    SharedPreferences.Editor editor = prefs.edit();
                    boolean hasData = false;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        editor.putString(child.getKey(), child.getValue().toString());
                        hasData = true;
                    }
                    editor.apply();
                    if (hasData) {
                        mWebView.post(() -> mWebView.loadUrl("javascript:location.reload()"));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }

        @JavascriptInterface
        public void printPdf() {
            ((MainActivity) mContext).runOnUiThread(() -> {
                PrintManager printManager = (PrintManager) mContext.getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter printAdapter = mWebView.createPrintDocumentAdapter("MasterRoll_Payslip");
                String jobName = "MasterRoll_Document";
                printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
            });
        }

        @JavascriptInterface
        public void shareText(String message) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, message);
            Intent chooser = Intent.createChooser(intent, "Share via");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(chooser);
        }

        @JavascriptInterface
        public void openSupportWhatsApp(String message) {
            try {
                String url = "https://api.whatsapp.com/send?phone=919905445671&text=" + Uri.encode(message);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception e) {
                mWebView.post(() -> mWebView.loadUrl("javascript:alert('WhatsApp not installed.')"));
            }
        }

        @JavascriptInterface
        public String getAppVersion() {
            try {
                return mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.3.0";
            }
        }

        @JavascriptInterface
        public void shareApp() {
            try {
                String appPath = mContext.getApplicationInfo().sourceDir;
                File originalApk = new File(appPath);
                File shareFolder = new File(mContext.getExternalFilesDir(null), "Download");
                if (!shareFolder.exists()) shareFolder.mkdirs();
                File sharedFile = new File(shareFolder, "MasterRoll_v1.3.0.apk");
                
                InputStream in = new FileInputStream(originalApk);
                OutputStream out = new FileOutputStream(sharedFile);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
                out.close();
                in.close();

                Uri contentUri = FileProvider.getUriForFile(mContext, 
                    "com.masterroll.app.fileprovider", sharedFile);

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/vnd.android.package-archive");
                intent.putExtra(Intent.EXTRA_STREAM, contentUri);
                intent.putExtra(Intent.EXTRA_SUBJECT, "Master Roll App");
                intent.putExtra(Intent.EXTRA_TEXT, "Download Master Roll v1.3.0 - Professional Attendance Tracker.");
                
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("", contentUri));
                
                Intent chooser = Intent.createChooser(intent, "Share Master Roll APK");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(chooser);

            } catch (Exception e) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, "Check out MASTER ROLL Attendance Tracker!");
                Intent chooser = Intent.createChooser(intent, "Share App");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(chooser);
            }
        }

        @JavascriptInterface
        public void sendEmail(String subject, String body) {
            try {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:hariom2003mgr@gmail.com"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"hariom2003mgr@gmail.com", "hariom10@amityonline.com"});
                intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                intent.putExtra(Intent.EXTRA_TEXT, body);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(Intent.createChooser(intent, "Send Report"));
            } catch (Exception e) {
            }
        }
    }
}
