import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class Xhtml2Pdf {
	public static void main(String[] args) throws Exception {

		String input = args[0];
		String url = new File(input).toURI().toURL().toString();
		String output = args[1];

		OutputStream os = new FileOutputStream(output);
		SilentPrintITextRenderer renderer = new SilentPrintITextRenderer();
		renderer.setDocument(url);
		renderer.layout();
		renderer.createPDF(os);
		os.close();

	}
}
