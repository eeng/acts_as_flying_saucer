import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.lowagie.text.pdf.BaseFont;

public class Xhtml2Pdf {
	private static List<String> FONTS = Arrays.asList("Calibri",
			"Calibri Bold", "Calibri Italic", "Calibri Bold Italic", "Arial",
			"Arial Bold", "Arial Italic", "Arial Bold Italic");

	public static void main(String[] args) throws Exception {
		String input = args[0];
		String output = args[1];
		try {
			disableCertificateChecking();
			String url = new File(input).toURI().toURL().toString();
			OutputStream os = new FileOutputStream(output);
			SilentPrintITextRenderer renderer = new SilentPrintITextRenderer();
			for (String font : FONTS)
				renderer.getFontResolver().addFont("/fonts/" + font + ".ttf",
						BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
			renderer.setDocument(url);
			renderer.layout();
			renderer.createPDF(os);
			os.close();
		} catch (Exception e) {
			new File(output).delete();
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
