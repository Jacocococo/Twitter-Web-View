package com.jacoco.twitterwebview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> mUploadMessage;
    private final String mCameraPhotoPath = null;
    private AudioManager audioManager;
    private final Pattern photoUrl = Pattern.compile(".*/photo/\\d$");
    private final Pattern statusUrl = Pattern.compile(".*/status/\\d*$");

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        webView = findViewById(R.id.webView);

        webView.setOnTouchListener(new OnSwipeTouchListener(MainActivity.this) {
            public boolean onSwipeRight() {
                if(photoUrl.matcher(webView.getUrl()).matches())
                    return false;
                webView.evaluateJavascript("if(document.querySelector(\"div[data-testid=\\\"app-bar-back\\\"]\")) document.querySelector(\"div[data-testid=\\\"app-bar-back\\\"]\").click()\n" +
                                            "else if(document.querySelector(\"div[data-testid=\\\"DashButton_ProfileIcon_Link\\\"]\")) document.querySelector(\"div[data-testid=\\\"DashButton_ProfileIcon_Link\\\"]\").click()",
                        null);
                return true;
            }
        });

        Spinner spinner = findViewById(R.id.spinner);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item = parent.getItemAtPosition(position).toString();
                if(item.equals("Copy Current URL")) {
                    String url = webView.getUrl();
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(url, url);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getApplicationContext(), "Copied URL", Toast.LENGTH_SHORT).show();
                } else if(item.equals("Reload")) {
                    webView.reload();
                }
                parent.setSelection(0);
            }

            public void onNothingSelected(AdapterView<?> parent) {}
        });

        List<String> categories = new ArrayList<String>();
        categories.add("Action: ");
        categories.add("Copy Current URL");
        categories.add("Reload");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categories);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_PLAY_SOUND);
            }
        }
        else {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setupView() {
        // Weird workaround needed for the
        // Open App button not being removed
        // the first time after reload
        final boolean[] firstStatus = {true};

        webView.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                final String url = request.getUrl().toString();
                if(url.contains("twitter.com")) {
                    view.loadUrl(url);
                } else {
                    Intent i = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    startActivity(i);
                }
                return true;
            }

            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.evaluateJavascript("function removeAds() { " +
                        "const element = document.querySelector(\"div[data-testid=\\\"placementTracking\\\"]\");" +
                        "if(element) {" +
                            "element.parentElement.remove()" +
                        "}}", null);
                webView.evaluateJavascript("new PerformanceObserver(removeAds).observe({type: 'largest-contentful-paint', buffered: true});", null);
                webView.evaluateJavascript("document.addEventListener('scroll', () => {" +
                        "clearTimeout(removeAds._tId);" +
                        "removeAds._tId= setTimeout(removeAds, 100)})", null);
                firstStatus[0] = true;
            }
        });
        WebSettings webSettings = webView.getSettings();
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setAllowFileAccess(true);
        webView.setWebChromeClient(new WebChromeClient() {
            private View mCustomView;
            private WebChromeClient.CustomViewCallback mCustomViewCallback;
            private int mOriginalOrientation;
            private int mOriginalSystemUiVisibility;

            public void onProgressChanged(WebView view, int newProgress) {
                String url = view.getUrl();
                if(url.endsWith("/compose/tweet") || url.endsWith("/settings/profile")) {
                    findViewById(R.id.spinner).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.spinner).setVisibility(View.VISIBLE);
                    if(statusUrl.matcher(webView.getUrl()).matches()) {
                        Runnable runnable = () -> webView.evaluateJavascript("if(document.querySelector(\"div[aria-label=\\\"Open app\\\"]\")) document.querySelector(\"div[aria-label=\\\"Open app\\\"]\").remove()", null);
                        if(firstStatus[0]) new Handler().postDelayed(runnable, 200);
                        else runnable.run();
                        firstStatus[0] = false;
                    }
                }

                super.onProgressChanged(view, newProgress);
            }

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

            public Bitmap getDefaultVideoPoster() {
                if (mCustomView == null) {
                    return null;
                }
                return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
            }

            public void onHideCustomView() {
                ((FrameLayout)getWindow().getDecorView()).removeView(this.mCustomView);
                this.mCustomView = null;
                getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
                setRequestedOrientation(this.mOriginalOrientation);
                this.mCustomViewCallback.onCustomViewHidden();
                this.mCustomViewCallback = null;
            }

            public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback) {
                if (this.mCustomView != null) {
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

            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebView", consoleMessage.message());
                return true;
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
        long size = 0;
        if (requestCode != 1 || mUploadMessage == null) {
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

        if (data != null) {
            int count = 0;
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
            if (resultCode == Activity.RESULT_OK) {
                if (size != 0) {
                    results = new Uri[]{Uri.parse(mCameraPhotoPath)};
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
}