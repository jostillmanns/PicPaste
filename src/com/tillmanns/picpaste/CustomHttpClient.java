package com.tillmanns.picpaste;

import android.content.Context;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;

import java.io.InputStream;
import java.security.KeyStore;

public class CustomHttpClient extends DefaultHttpClient {

    final Context context;

    private String secret;
    public CustomHttpClient(Context context, String secret) {
	this.context = context;
	this.secret = secret;
    }

    @Override
    protected ClientConnectionManager createClientConnectionManager() {
	SchemeRegistry registry = new SchemeRegistry();
	registry.register(new Scheme("https", newSslSocketFactory(), 9785));
	return new SingleClientConnManager(getParams(), registry);
    }

    private SSLSocketFactory newSslSocketFactory() {
	try {
	    KeyStore trusted = KeyStore.getInstance("BKS");
	    InputStream in = context.getResources().openRawResource(R.raw.mystore);
	    try {
		trusted.load(in, secret.toCharArray());
	    } finally {
		in.close();
	    }
	    return new SSLSocketFactory(trusted);
	} catch (Exception e) {
	    throw new AssertionError(e);
	}
    }
}
