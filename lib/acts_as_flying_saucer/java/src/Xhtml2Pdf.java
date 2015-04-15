import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Xhtml2Pdf {
	public static void main(String[] args) throws IOException {
		String input = args[0];
		String output = args[1];
		try {
			disableCertificateChecking();
			String url = new File(input).toURI().toURL().toString();
			OutputStream os = new FileOutputStream(output);
			SilentPrintITextRenderer renderer = new SilentPrintITextRenderer();
			renderer.setDocument(url);
			renderer.layout();
			renderer.createPDF(os);
			os.close();
		} catch (Exception e) {
			Files.delete(FileSystems.getDefault().getPath(output));
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void disableCertificateChecking()
			throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
			}
		} }, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String urlHostName, SSLSession session) {
				return true;
			}
		});
	}
}
