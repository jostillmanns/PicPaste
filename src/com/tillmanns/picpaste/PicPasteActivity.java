package com.tillmanns.picpaste;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONObject;

import com.tillmanns.picpaste.R.layout;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.net.Uri;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.view.View.OnClickListener;
import android.view.View;

import java.util.UUID;

public class PicPasteActivity extends Activity {

    private final static int TIMEOUT = 120;
    private final static String TAG = "PicPaste";

    private final static String REQUEST_PATH = "/picpaste";

    private final static String PREFS_NAME = "picpaste";
    private final static String PREFS_KEY_IMG = "image";

    private final static int CAPTURE_IMAE_ACTIVITY_REQUEST_CODE = 101;

    private final static String SETTINGS = "settings.json";
    private final static String SERVER_SECRET_KEY = "server_secret";
    private final static String KEYSTORE_SECRET_KEY = "keystore_secret";
    private final static String HOST_KEY = "host";

    private String serverSecret = "";
    private String keyStoreSecret = "";
    private String host = "";
    private String settings = "";

    public void onCreate(Bundle bundle) {
	super.onCreate(bundle);
	setContentView(R.layout.picpaste_layout);

	initSettings();

	Button button = (Button) findViewById(R.id.start_camera_button);
	button.setOnClickListener(new OnClickListener() {
		public void onClick(View v) {
		    startCamera();
		}
	    });
    }

    private void initSettings() {
	InputStream inputStream = null;

	try {
	    AssetManager assetManager = this.getAssets();
	    inputStream = assetManager.open(SETTINGS);
	    int length = inputStream.available();
	    byte[] bytes = new byte[length];
	    inputStream.read(bytes);
	    inputStream.close();
	    settings = new String(bytes);
	} catch (Exception e) {
	    Log.e(TAG, "unable to open settings file", e);
	} finally {
	    close(inputStream);
	}


	try {
	    JSONObject jObject = new JSONObject(settings);
	    serverSecret = jObject.getString(SERVER_SECRET_KEY);
	    keyStoreSecret = jObject.getString(KEYSTORE_SECRET_KEY);
	    host = jObject.getString(HOST_KEY);
	} catch (Exception e) {
	    Log.e(TAG, "settings key not found", e);
	}
    }

    public void startCamera() {
	try {
	    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	    Uri fileUri = getOutputMediaFileUri(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE);
	    intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

	    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
	    SharedPreferences.Editor editor = settings.edit();

	    // save last created image to shared preferences
	    editor.putString(PREFS_KEY_IMG, fileUri.getPath());
	    editor.commit();

	    startActivityForResult(intent, CAPTURE_IMAE_ACTIVITY_REQUEST_CODE);
	} catch (PicPasteException e) {
	    Log.e(TAG, "unable to create image from camera", e);
	}
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == CAPTURE_IMAE_ACTIVITY_REQUEST_CODE) {

    	    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    	    String imagePath = settings.getString(PREFS_KEY_IMG, "");

    	    if (imagePath == "") {
    		Log.e(TAG, "file uri for image processing is missing", new PicPasteException("missing file uri"));
    	    }

	    // resize image to save traffic

	    FileOutputStream ostream = null;
	    try {
		Bitmap bm = BitmapFactory.decodeFile(imagePath);
		Integer height = bm.getHeight();
		Integer width = bm.getWidth();
		Integer newWidth;
		Integer newHeight;

		if (height > width) {
		    Double scale = 600. / height;

		    newWidth = (int) Math.round(scale * width);
		    newHeight = 600;
		} else {
		    Double scale = 600. / width;

		    newHeight = (int) Math.round(scale * height);
		    newWidth = 600;
		}
		bm = Bitmap.createScaledBitmap(bm, newWidth, newHeight, false);

		File file = new File(imagePath);
		file.createNewFile();

		ostream = new FileOutputStream(file);
		bm.compress(Bitmap.CompressFormat.JPEG, 100, ostream);
		ostream.close();
		uploadImage(imagePath);

	    } catch (Exception e) {
		Log.e(TAG, "unable to resize file, and therefore unable to upload it", e);
	    } finally {
		close(ostream);
	    }

    	}
    }

    private void close(Closeable c) {
	if (c == null) return;
	try {
	    c.close();
	} catch (IOException e) {
	    Log.e(TAG, "unable to close stream", e);
	}
    }

    private Uri getOutputMediaFileUri(int type) throws PicPasteException {
	return Uri.fromFile(getOutputMediaFile(type));
    }

    private File getOutputMediaFile(int type) throws PicPasteException {
	String mediaStorageError = "unable to create media storage directory";

	File mediaStorageDir
	    = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PicPaste");
	if (!mediaStorageDir.exists()) {
	    if (! mediaStorageDir.mkdirs()) {
		throw new PicPasteException(mediaStorageError);
	    }
	}

	UUID uuid = UUID.randomUUID();

	File mediaFile = new File(mediaStorageDir.getPath() +
				  File.separator + "IMG_" + uuid.toString() + ".jpg");
	return mediaFile;
    }

    private void deployUrl(String url) {
	TextView textView = (TextView) findViewById(R.id.image_url_textview);
	textView.setText(url);

	if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
	    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    clipboard.setText(url);
	} else {
	    android.content.ClipboardManager clipboard =
		(android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    android.content.ClipData clip = android.content.ClipData.newPlainText("PicPaste Url", url);
	    clipboard.setPrimaryClip(clip);
	}
    }

    private void uploadImage(final String filename) {

	new AsyncTask<Void, Void, String>() {
	    protected String doInBackground(Void... params) {
		String response = "";
		try {
		    HttpClient httpClient = getHttpClient();
		    HttpPost postRequest = new HttpPost(host + REQUEST_PATH);

		    MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
		    entity.addPart("password", new StringBody(serverSecret));

		    File image = new File(filename);
		    FileBody fileBody = new FileBody(image, "application/octet-stream");
		    entity.addPart("image", fileBody);

		    postRequest.setEntity(entity);
		    HttpResponse httpResponse = httpClient.execute(postRequest);
		    response = readStream(httpResponse.getEntity().getContent());
		} catch (Exception e) {
		    Log.e(TAG, "unable to upload image", e);
		}

		return response;
	    }

	    protected void onPostExecute(String response) {
		deployUrl(response);
	    }
	}.execute();
    }

    private String readStream(final InputStream in) throws IOException {
	String result = "";
	BufferedReader reader = null;
	try {
	    reader = new BufferedReader(new InputStreamReader(in));
	    String line = "";
	    while ((line = reader.readLine()) != null) {
		result += line;
	    }
	} catch (IOException e) {
	    throw e;
	} finally {
	    reader.close();
	}

	return result;

    }

    public HttpClient getHttpClient() {
	return new CustomHttpClient(this, keyStoreSecret);
    }

    private class PicPasteException extends Exception {
	private static final long serialVersionUID = 1L;

	public PicPasteException (String message) {
	    super (message);
	}
    }
}
