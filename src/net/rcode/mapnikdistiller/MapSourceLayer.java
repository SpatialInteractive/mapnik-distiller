package net.rcode.mapnikdistiller;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import mapnik.FeatureTypeStyle;
import mapnik.Layer;
import mapnik.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.XMLWriter;

/**
 * Wraps a Layer tag in the xml source file
 * @author stella
 *
 */
public class MapSourceLayer {
	private MapSource source;
	private Element element;
	private Map nativeMap;
	private Layer nativeLayer;
	
	public MapSourceLayer(MapSource source, Element element) {
		this.source=source;
		this.element=element;
	}
	
	public MapSource getSource() {
		return source;
	}
	
	public Element getElement() {
		return element;
	}

	public String getName() {
		String ret=element.attributeValue("name");
		if (ret==null) return "";
		return ret;
	}

	public String getDatasourceParam(String name) {
		Element dselt=element.element("Datasource");
		if (dselt==null) return null;
		for (Element pelt: (List<Element>)dselt.elements("Parameter")) {
			if (name.equals(pelt.attributeValue("name"))) {
				return pelt.getText();
			}
		}
		
		return null;
	}

	public Map getNativeMap() {
		if (nativeMap!=null) return nativeMap;
		
		String subset=createSubsetXml();
		nativeMap=NativeMapCache.get(subset);
		if (nativeMap!=null) return nativeMap;

		
		nativeMap=new Map();
		nativeMap.loadMapString(subset, false, source.getSourceFile().toString());
		NativeMapCache.put(subset, nativeMap);
		
		return nativeMap;
	}
	
	public Layer getNativeLayer() {
		if (nativeLayer==null) {
			Map m=getNativeMap();
			nativeLayer=m.getLayer(0);
		}
		return nativeLayer;
	}
	
	/**
	 * True if the layer renders at the given scale denominator range
	 * @return if renders
	 */
	public boolean doesRender(RenderConditions conditions) {
		// Get layer level minzoom/maxzoom which trump all
		double layerMinZoom=getMinZoom(), layerMaxZoom=getMaxZoom();
		if (!Double.isNaN(layerMinZoom)) {
			if (layerMinZoom>conditions.getMinScaleDenominator() &&
					layerMinZoom>conditions.getMaxScaleDenominator()) return false;
		}
		if (!Double.isNaN(layerMaxZoom)) {
			if (layerMaxZoom<conditions.getMinScaleDenominator() &&
					layerMaxZoom<conditions.getMaxScaleDenominator()) return false;
		}
		
		// Now cycle through the styles and see if there is at least one that renders
		for (String styleName: getStyleNames()) {
			Element styleElt=findStyleElement(styleName);
			if (styleElt==null) continue;
			
			if (doesStyleRender(styleElt, conditions)) return true;
		}
		
		// Default is that it does not render
		return false;
	}
	
	/**
	 * A style renders if it has one or more rules that are not excluded per conditions
	 * @param styleElt
	 * @param conditions
	 * @return true if the given style element renders per the conditions
	 */
	public boolean doesStyleRender(Element styleElt,
			RenderConditions conditions) {
		Iterator<Element> iter=styleElt.elementIterator("Rule");
		while (iter.hasNext()) {
			Element ruleElt=iter.next();
			
			if (doesRuleRender(ruleElt, conditions)) return true;
		}
		
		return false;
	}

	/**
	 * Check if a rule renders per the conditions
	 * @param ruleElt
	 * @param conditions
	 * @return true if renders
	 */
	public boolean doesRuleRender(Element ruleElt, RenderConditions conditions) {
		Element denomElt;
		double denom;
		
		// Check for min denominator element
		denomElt=ruleElt.element("MinScaleDenominator");
		if (denomElt!=null) {
			try {
				denom=Double.parseDouble(denomElt.getTextTrim());
				if (denom>conditions.getMaxScaleDenominator() &&
						denom>conditions.getMinScaleDenominator()) return false;
			} catch (NumberFormatException e) {
				// Ignore
			}
		}
		
		// Check for max denominator element
		denomElt=ruleElt.element("MaxScaleDenominator");
		if (denomElt!=null) {
			try {
				denom=Double.parseDouble(denomElt.getTextTrim());
				if (denom<conditions.getMinScaleDenominator() &&
						denom<conditions.getMaxScaleDenominator()) return false;
			} catch (NumberFormatException e) {
				// Ignore
			}
		}
		return true;
	}

	/**
	 * @return List of referenced style names (StyleName children)
	 */
	public List<String> getStyleNames() {
		ArrayList<String> ret=new ArrayList<String>();
		Iterator<Element> iter=element.elementIterator("StyleName");
		while (iter.hasNext()) {
			Element snelt=iter.next();
			ret.add(snelt.getTextTrim());
		}
		return ret;
	}
	
	/**
	 * @param name
	 * @return The map style element with the given name
	 */
	public Element findStyleElement(String name) {
		Element mapElt=element.getParent();
		Iterator<Element> iter=mapElt.elementIterator("Style");
		while (iter.hasNext()) {
			Element selt=iter.next();
			if (name.equals(selt.attributeValue("name"))) return selt;
		}
		
		return null;
	}
	
	/**
	 * 
	 * @return layer level min zoom or nan
	 */
	public double getMinZoom() {
		String v=element.attributeValue("minzoom");
		if (v==null) return Double.NaN;
		try {
			return Double.parseDouble(v);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/**
	 * 
	 * @return layer level max zoom or nan
	 */
	public double getMaxZoom() {
		String v=element.attributeValue("maxzoom");
		if (v==null) return Double.NaN;
		try {
			return Double.parseDouble(v);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	private String createSubsetXml() {
		Element rootElement=DocumentHelper.createElement("Map");
		Document subsetDoc=DocumentHelper.createDocument(rootElement);
		
		Iterator<Element> iter=source.getDocument().getRootElement().elementIterator();
		while (iter.hasNext()) {
			Element elt=iter.next();
			if (elt==this.element || !elt.getName().equals("Layer")) {
				// Add a copy
				Element copy=(Element)elt.clone();
				if (elt==this.element) {
					customizeSubsetElement(copy);
				}
				
				rootElement.add(copy);
			}
		}
		
		try {
			StringWriter out=new StringWriter();
			XMLWriter w=new XMLWriter(out);
			w.write(subsetDoc);
			w.flush();
			return out.toString();
		} catch (Exception e) {
			throw new RuntimeException("Error serializing subset doc", e);
		}
	}

	private void customizeSubsetElement(Element elementCopy) {
		if ("postgis".equals(getDatasourceParam("type"))) {
			// We need to make sure that there is a fetch size defined
			// Otherwise, postgres will buffer things in memory and kill us
			Element dseltCopy=elementCopy.element("Datasource");
			Iterator<Element> iter=dseltCopy.elements("Parameter").iterator();
			while (iter.hasNext()) {
				Element paramCopy=iter.next();
				if ("cursor_size".equals(paramCopy.attributeValue("name"))) {
					iter.remove();
				}
			}
			
			dseltCopy.add(DocumentHelper.createElement("Parameter")
					.addAttribute("name", "cursor_size")
					.addText("100"));
		}
	}

	public Collection<String> discoverAttributes() {
		Set<String> attributes=new HashSet<String>();
		Map m=getNativeMap();
		
		for (String styleName: getNativeLayer().getStyles()) {
			FeatureTypeStyle style=m.getStyle(styleName);
			if (style==null) {
				throw new IllegalArgumentException("Style " + styleName + " referenced from layer " + getName() + " not found");
			}
			
			attributes.addAll(style.collectAttributes());
		}
		
		return attributes;
	}

	/**
	 * Drop the layer from the map
	 */
	public void drop() {
		element.detach();
	}	
	
	
}
