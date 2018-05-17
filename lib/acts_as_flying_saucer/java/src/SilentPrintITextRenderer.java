

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.extend.NamespaceHandler;
import org.xhtmlrenderer.extend.UserInterface;
import org.xhtmlrenderer.layout.BoxBuilder;
import org.xhtmlrenderer.layout.Layer;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextFontContext;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextReplacedElementFactory;
import org.xhtmlrenderer.pdf.ITextTextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.pdf.PDFEncryption;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.PageBox;
import org.xhtmlrenderer.render.RenderingContext;
import org.xhtmlrenderer.render.ViewportBox;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.util.Configuration;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfWriter;

public class SilentPrintITextRenderer extends ITextRenderer {
	// These two defaults combine to produce an effective resolution of 96
	// px to the inch
	private static final float DEFAULT_DOTS_PER_POINT = 20f * 4f / 3f;
	private static final int DEFAULT_DOTS_PER_PIXEL = 20;

	private SharedContext _sharedContext;
	private ITextOutputDevice _outputDevice;

	private Document _doc;
	private Box _root;

	private float _dotsPerPoint;

	private com.lowagie.text.Document _pdfDoc;
	private PdfWriter _writer;

	private PDFEncryption _pdfEncryption;

	public SilentPrintITextRenderer() {
		this(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL);
	}

	public SilentPrintITextRenderer(float dotsPerPoint, int dotsPerPixel) {
		_dotsPerPoint = dotsPerPoint;

		_outputDevice = new ITextOutputDevice(_dotsPerPoint);

		ITextUserAgent userAgent = new ITextUserAgent(_outputDevice) {
			@Override
			protected InputStream resolveAndOpenStream(String url) {
				InputStream stream = super.resolveAndOpenStream(url);
				if (stream == null)
					throw new RuntimeException("Resource not found: " + url);
				return stream;
			}
		};
		_sharedContext = new SharedContext(userAgent);
		userAgent.setSharedContext(_sharedContext);
		_outputDevice.setSharedContext(_sharedContext);

		ITextFontResolver fontResolver = new ITextFontResolver(_sharedContext);
		_sharedContext.setFontResolver(fontResolver);

		ITextReplacedElementFactory replacedElementFactory = new ITextReplacedElementFactory(_outputDevice);
		_sharedContext.setReplacedElementFactory(replacedElementFactory);

		_sharedContext.setTextRenderer(new ITextTextRenderer());
		_sharedContext.setDPI(72 * _dotsPerPoint);
		_sharedContext.setDotsPerPixel(dotsPerPixel);
		_sharedContext.setPrint(true);
		_sharedContext.setInteractive(false);
	}

	public ITextFontResolver getFontResolver() {
		return (ITextFontResolver) _sharedContext.getFontResolver();
	}

	private Document loadDocument(final String uri) {
		return _sharedContext.getUac().getXMLResource(uri).getDocument();
	}

	public void setDocument(String uri) {
		setDocument(loadDocument(uri), uri);
	}

	public void setDocument(Document doc, String url) {
		setDocument(doc, url, new XhtmlNamespaceHandler());
	}

	public void setDocument(File file) throws IOException {

		File parent = file.getParentFile();
		setDocument(loadDocument(file.toURI().toURL().toExternalForm()), (parent == null ? "" : parent.toURI()
				.toURL().toExternalForm()));
	}

	public void setDocument(Document doc, String url, NamespaceHandler nsh) {
		_doc = doc;

		getFontResolver().flushFontFaceFonts();

		_sharedContext.reset();
		if (Configuration.isTrue("xr.cache.stylesheets", true)) {
			_sharedContext.getCss().flushStyleSheets();
		} else {
			_sharedContext.getCss().flushAllStyleSheets();
		}
		_sharedContext.setBaseURL(url);
		_sharedContext.setNamespaceHandler(nsh);
		_sharedContext.getCss().setDocumentContext(_sharedContext, _sharedContext.getNamespaceHandler(), doc,
				new NullUserInterface());
		getFontResolver().importFontFaces(_sharedContext.getCss().getFontFaceRules());
	}

	public PDFEncryption getPDFEncryption() {
		return _pdfEncryption;
	}

	public void setPDFEncryption(PDFEncryption pdfEncryption) {
		_pdfEncryption = pdfEncryption;
	}

	public void layout() {
		LayoutContext c = newLayoutContext();
		BlockBox root = BoxBuilder.createRootBox(c, _doc);
		root.setContainingBlock(new ViewportBox(getInitialExtents(c)));
		root.layout(c);
		Dimension dim = root.getLayer().getPaintingDimension(c);
		root.getLayer().trimEmptyPages(c, dim.height);
		root.getLayer().layoutPages(c);
		_root = root;
	}

	private Rectangle getInitialExtents(LayoutContext c) {
		PageBox first = Layer.createPageBox(c, "first");

		return new Rectangle(0, 0, first.getContentWidth(c), first.getContentHeight(c));
	}

	private RenderingContext newRenderingContext() {
		RenderingContext result = _sharedContext.newRenderingContextInstance();
		result.setFontContext(new ITextFontContext());

		result.setOutputDevice(_outputDevice);

		_sharedContext.getTextRenderer().setup(result.getFontContext());

		result.setRootLayer(_root.getLayer());

		return result;
	}

	private LayoutContext newLayoutContext() {
		LayoutContext result = _sharedContext.newLayoutContextInstance();
		result.setFontContext(new ITextFontContext());

		_sharedContext.getTextRenderer().setup(result.getFontContext());

		return result;
	}

	public void createPDF(OutputStream os) throws DocumentException {
		createPDF(os, true);
	}

	public void writeNextDocument() throws DocumentException {
		writeNextDocument(0);
	}

	public void writeNextDocument(int initialPageNo) throws DocumentException {
		List pages = _root.getLayer().getPages();

		RenderingContext c = newRenderingContext();
		c.setInitialPageNo(initialPageNo);
		PageBox firstPage = (PageBox) pages.get(0);
		com.lowagie.text.Rectangle firstPageSize = new com.lowagie.text.Rectangle(0, 0, firstPage.getWidth(c)
				/ _dotsPerPoint, firstPage.getHeight(c) / _dotsPerPoint);

		_outputDevice.setStartPageNo(_writer.getPageNumber());

		_pdfDoc.setPageSize(firstPageSize);
		_pdfDoc.newPage();

		writePDF(pages, c, firstPageSize, _pdfDoc, _writer);
	}

	public void finishPDF() {
		if (_pdfDoc != null) {
			_pdfDoc.close();
		}
	}

	/**
	 * <B>NOTE:</B> Caller is responsible for cleaning up the OutputStream
	 * if something goes wrong.
	 */
	public void createPDF(OutputStream os, boolean finish) throws DocumentException {
		List pages = _root.getLayer().getPages();

		RenderingContext c = newRenderingContext();
		PageBox firstPage = (PageBox) pages.get(0);
		com.lowagie.text.Rectangle firstPageSize = new com.lowagie.text.Rectangle(0, 0, firstPage.getWidth(c)
				/ _dotsPerPoint, firstPage.getHeight(c) / _dotsPerPoint);

		com.lowagie.text.Document doc = new com.lowagie.text.Document(firstPageSize, 0, 0, 0, 0);
		PdfWriter writer = PdfWriter.getInstance(doc, os);
//		if (_pdfEncryption != null) {
//			writer.setEncryption(true, _pdfEncryption.getUserPassword(), _pdfEncryption.getOwnerPassword(),
//					_pdfEncryption.getAllowedPrivileges());
//		}
		doc.open();
		silentPrint(writer);

		if (!finish) {
			_pdfDoc = doc;
			_writer = writer;
		}

		writePDF(pages, c, firstPageSize, doc, writer);

		if (finish) {
			doc.close();
		}
	}

	private void silentPrint(PdfWriter writer) {
		writer
				.addJavaScript(
						"if (typeof JSSilentPrint == 'undefined') this.print({bUI: true, bSilent: true, bShrinkToFit: false}); else JSSilentPrint(this);",
						false);
	}

	private void writePDF(List pages, RenderingContext c, com.lowagie.text.Rectangle firstPageSize,
			com.lowagie.text.Document doc, PdfWriter writer) throws DocumentException {
		_outputDevice.setRoot(_root);

		_outputDevice.start(_doc);
		_outputDevice.setWriter(writer);
		_outputDevice.initializePage(writer.getDirectContent(), firstPageSize.height());

		_root.getLayer().assignPagePaintingPositions(c, Layer.PAGED_MODE_PRINT);

		int pageCount = _root.getLayer().getPages().size();
		c.setPageCount(pageCount);
		for (int i = 0; i < pageCount; i++) {
			PageBox currentPage = (PageBox) pages.get(i);
			c.setPage(i, currentPage);
			paintPage(c, currentPage);
			_outputDevice.finishPage();
			if (i != pageCount - 1) {
				PageBox nextPage = (PageBox) pages.get(i + 1);
				com.lowagie.text.Rectangle nextPageSize = new com.lowagie.text.Rectangle(0, 0, nextPage.getWidth(c)
						/ _dotsPerPoint, nextPage.getHeight(c) / _dotsPerPoint);
				doc.setPageSize(nextPageSize);
				doc.newPage();
				_outputDevice.initializePage(writer.getDirectContent(), nextPageSize.height());
			}
		}

		_outputDevice.finish(c, _root);
	}

	private void paintPage(RenderingContext c, PageBox page) {
		page.paintBackground(c, 0, Layer.PAGED_MODE_PRINT);
		page.paintMarginAreas(c, 0, Layer.PAGED_MODE_PRINT);
		page.paintBorder(c, 0, Layer.PAGED_MODE_PRINT);

		Shape working = _outputDevice.getClip();

		Rectangle content = page.getPrintClippingBounds(c);
		_outputDevice.clip(content);

		int top = -page.getPaintingTop() + page.getMarginBorderPadding(c, CalculatedStyle.TOP);

		int left = page.getMarginBorderPadding(c, CalculatedStyle.LEFT);

		_outputDevice.translate(left, top);
		_root.getLayer().paint(c);
		_outputDevice.translate(-left, -top);

		_outputDevice.setClip(working);
	}

	public ITextOutputDevice getOutputDevice() {
		return _outputDevice;
	}

	public SharedContext getSharedContext() {
		return _sharedContext;
	}

	public void exportText(Writer writer) throws IOException {
		RenderingContext c = newRenderingContext();
		c.setPageCount(_root.getLayer().getPages().size());
		_root.exportText(c, writer);
	}

	private static final class NullUserInterface implements UserInterface {
		public boolean isHover(Element e) {
			return false;
		}

		public boolean isActive(Element e) {
			return false;
		}

		public boolean isFocus(Element e) {
			return false;
		}
	}

}
