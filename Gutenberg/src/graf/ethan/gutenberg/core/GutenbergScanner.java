package graf.ethan.gutenberg.core;

import graf.ethan.gutenberg.pdf.Page;
import graf.ethan.gutenberg.pdf.PdfDictionary;
import graf.ethan.gutenberg.pdf.PdfDocument;
import graf.ethan.gutenberg.pdf.PdfObjectReference;
import graf.ethan.gutenberg.scanner.FileScanner;
import graf.ethan.gutenberg.scanner.PdfScanner;
import graf.ethan.gutenberg.scanner.StreamScanner;
import graf.ethan.gutenberg.scanner.XObjectScanner;
import graf.ethan.gutenberg.xref.Xref;
import graf.ethan.gutenberg.xref.XrefScanner;
import graf.ethan.gutenberg.xref.XrefSection;

import java.io.File;
import java.util.ArrayList;

/*
 * The main scanner class for Gutenberg, responsible for navigating the PDF's basic structure.
 * Contains instances of FileScanner, PdfScanner, CrossReferenceScanner, and StreamScanner
 */
public class GutenbergScanner {
	//Keywords
	private static final String XREF = "xref";
	private static final String STARTXREF = "startxref";
	private static final String TRAILER = "trailer";
	
	//Scanners
	public FileScanner fileScanner;
	public PdfScanner pdfScanner;
	public Xref crossScanner;
	public StreamScanner streamScanner;
	public XObjectScanner xObjectScanner;
	public LinearScanner linearScanner;
	
	//Drawers
	public GutenbergDrawer gutenbergDrawer;
	
	//Markers
	public long trailerPos;
	public long startXrefPos;
	public ArrayList<XrefSection> xrefs;
	
	public PdfDocument document;
	
	public GutenbergScanner(File f) {
		this.document = new PdfDocument(f);
		this.fileScanner = new FileScanner(f);
		this.pdfScanner = new PdfScanner(this);
		this.xObjectScanner = new XObjectScanner(this);
		this.streamScanner = new StreamScanner(this);
		
		Object first = scanFirst();
		if(!document.linearized) {
			firstPass();
			if(document.getCatalog() == null) {
				scanCatalog();
			}
		}
		else {
			System.out.println("Linearized");
			this.linearScanner = new LinearScanner(this, (PdfDictionary) first);
		}
	}
	
	public void setDrawer(GutenbergDrawer drawer) {
		this.gutenbergDrawer = drawer;
	}
	
	/*
	 *Scan the first object of the file to determine whether it is linearized.
	 *Return true if the trailer does not have to be read. 
	 */
	public Object scanFirst() {
		Object nextObj = pdfScanner.scanNext();
		System.out.println(nextObj);
		if(nextObj.getClass() == PdfDictionary.class) {
			if(((PdfDictionary) nextObj).get("Type") == "Catalog") {
				document.setCatalog((PdfDictionary) nextObj);
				document.setPageTree((PdfDictionary) document.getCatalog().get("Pages"));
				System.out.println("Catalog: " + document.getCatalog());
				System.out.println("Page Tree: " + document.getPageTree());
			}
			else if(((PdfDictionary) nextObj).has("Linearized")) {
				document.linearized = true;
			}
		}
		return nextObj;
	}
	
	/*
	 * Finds and marks the locations of important structures.
	 */
	public void firstPass() {
		String nextLine = fileScanner.nextLine();
		xrefs = new ArrayList<>();
		while(nextLine != null) {
			switch(nextLine) {
				case TRAILER:
					//Find the trailer
					trailerPos = fileScanner.getPosition();
					pdfScanner.skipWhiteSpace();
					document.setTrailer((PdfDictionary) pdfScanner.scanNext());
					System.out.println("Trailer: " + document.getTrailer());
					break;
				case XREF:
					//Find all of the XREF sections
					pdfScanner.skipWhiteSpace();
					int startNum = pdfScanner.scanNumeric().intValue();
					pdfScanner.skipWhiteSpace();
					int length = (int) pdfScanner.scanNumeric().intValue();
					xrefs.add(new XrefSection(startNum, length, fileScanner.getPosition()));
					crossScanner = new XrefScanner(this, xrefs);
					break;
				case STARTXREF:
					//Find the startxref marker at the end of the file.
					startXrefPos = fileScanner.getPosition();
					break;
				}
			nextLine = fileScanner.nextLine();
		}
	}
	
	/*
	 * Scans the file catalog, which contains important information about the PDF.
	 */
	public void scanCatalog() {
		document.setCatalog((PdfDictionary) document.getTrailer().get("Root"));
		document.setPageTree((PdfDictionary) document.getCatalog().get("Pages"));
		System.out.println("Catalog: " + document.getCatalog());
		System.out.println("Page Tree: " + document.getPageTree());
	}
	
	public Object getObject(PdfObjectReference reference) {
		if(document.linearized) {
			return linearScanner.getObject(reference);
		}
		else {
			return crossScanner.getObject(reference);
		}
	}
	
	/*
	 * Gets a page object to be rendered.
	 */
	@SuppressWarnings("unchecked")
	public Page getPage(int num) {
		if(document.linearized) {
			return linearScanner.getPage(num);
		}
		if(num < ((ArrayList<Object>) document.getPageTree().get("Kids")).size()) {
			PdfDictionary pageObject = (PdfDictionary) crossScanner.getObject((PdfObjectReference) ((ArrayList<Object>)document.getPageTree().get("Kids")).get(num));
			System.out.println("Page Object: " + pageObject);
			//The coordinates are temporary.
			return new Page(this, pageObject, 50, 50);
		}
		return null;
	}
}
