package com.jacoco.twitterwebview;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private WebView webView;
    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private static final String TAG = MainActivity.class.getSimpleName();
    private WebSettings webSettings;
    private ValueCallback<Uri[]> mUploadMessage;
    private String mCameraPhotoPath = null;
    private long size = 0;
    private AudioManager audioManager;

    private Timer reload;
    private boolean runningTask = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        webView = findViewById(R.id.webView);
        reload = new Timer();

        verifyPermissions();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        } else if(keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        } else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
        } else if(keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_PLAY_SOUND);
        }
        else {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setupView() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if(!runningTask && !isInternetAvailable() && !isNetworkConnected()) {
                    runningTask = true;
                    reload.schedule(new TimerTask() {
                        public void run() {
                            runOnUiThread(() -> webView.reload());
                            runningTask = false;
                        }
                    }, 15000);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if(runningTask && isInternetAvailable() && isNetworkConnected()) {
                    reload.cancel();
                    reload.purge();
                    reload = new Timer();
                    runningTask = false;
                }
            }
        });
        webSettings = webView.getSettings();
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(webSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setAllowFileAccess(true);
        webView.setWebChromeClient(new WebChromeClient() {
            private View mCustomView;
            private WebChromeClient.CustomViewCallback mCustomViewCallback;
            private int mOriginalOrientation;
            private int mOriginalSystemUiVisibility;

            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePath, WebChromeClient.FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePath;
                Log.e("FileCooserParams => ", filePath.toString());

                Intent takePictureIntent = new Intent(MediaStore.AUTHORITY);

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                contentSelectionIntent.setType("image/*,video/*");

                Intent[] intentArray = new Intent[]{takePictureIntent};

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                startActivityForResult(Intent.createChooser(chooserIntent, "Select images"), 1);

                return true;

            }

            public Bitmap getDefaultVideoPoster()
            {
                if (mCustomView == null) {
                    return null;
                }
                return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
            }

            public void onHideCustomView()
            {
                ((FrameLayout)getWindow().getDecorView()).removeView(this.mCustomView);
                this.mCustomView = null;
                getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
                setRequestedOrientation(this.mOriginalOrientation);
                this.mCustomViewCallback.onCustomViewHidden();
                this.mCustomViewCallback = null;
            }

            public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback)
            {
                if (this.mCustomView != null)
                {
                    onHideCustomView();
                    return;
                }
                this.mCustomView = paramView;
                this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
                this.mOriginalOrientation = getRequestedOrientation();
                this.mCustomViewCallback = paramCustomViewCallback;
                ((FrameLayout)getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

                View decorView = getWindow().getDecorView();

                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }
        });
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.loadUrl("https://twitter.com/");
    }

    private void verifyPermissions() {
        String[] permissions =
                {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
        if(ContextCompat.checkSelfPermission(this.getApplicationContext(), permissions[0]) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this.getApplicationContext(), permissions[1]) == PackageManager.PERMISSION_GRANTED) {
            setupView();
        } else {
            ActivityCompat.requestPermissions(this, permissions, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        verifyPermissions();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != INPUT_FILE_REQUEST_CODE || mUploadMessage == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        try {
            String file_path = mCameraPhotoPath.replace("file:","");
            File file = new File(file_path);
            size = file.length();

        }catch (Exception e){
            Log.e("Error!", "Error while opening image file" + e.getLocalizedMessage());
        }

        if (data != null || mCameraPhotoPath != null) {
            Integer count = 0; //fix fby https://github.com/nnian
            ClipData images = null;
            try {
                images = data.getClipData();
            }catch (Exception e) {
                Log.e("Error!", e.getLocalizedMessage());
            }

            if (images == null && data != null && data.getDataString() != null) {
                count = data.getDataString().length();
            } else if (images != null) {
                count = images.getItemCount();
            }
            Uri[] results = new Uri[count];
            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                if (size != 0) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                } else if (data.getClipData() == null) {
                    results = new Uri[]{Uri.parse(data.getDataString())};
                } else {

                    for (int i = 0; i < images.getItemCount(); i++) {
                        results[i] = images.getItemAt(i).getUri();
                    }
                }
            }

            mUploadMessage.onReceiveValue(results);
            mUploadMessage = null;
        }
    }

    public boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("google.com");
            return !ipAddr.equals("");

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}
