package net.rcode.mapnikdistiller;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * References a loaded Mapnik xml file.  We do a lot of our work on the
 * XML file itself because we're going to be changing it.
 * 
 * @author stella
 *
 */
public class MapSource {
	private File sourceFile;
	private Document document;
	
	public MapSource(File sourceFile) throws Exception {
		this.sourceFile=sourceFile;
		load(sourceFile);
	}

	private MapSource(MapSource other) {
		this.sourceFile=other.sourceFile;
		this.document=(Document) other.document.clone();
	}
	
	public MapSource copy() {
		return new MapSource(this);
	}

	public Document getDocument() {
		return document;
	}
	
	public File getSourceFile() {
		return sourceFile;
	}
	
	private void load(File f) throws Exception {
		SAXReader r=new SAXReader();
		document=r.read(f);
	}
	
	public String saveToString() throws Exception {
		StringWriter out=new StringWriter();
		XMLWriter w=new XMLWriter(out);
		w.write(document);
		w.flush();
		return out.toString();
	}
	
	public List<MapSourceLayer> getLayers() {
		List<MapSourceLayer> ret=new ArrayList<MapSourceLayer>();
		List<Element> elements=document.getRootElement().elements("Layer");
		for (Element element: elements) {
			ret.add(new MapSourceLayer(this, element));
		}
		
		return ret;
	}

}
