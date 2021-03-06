package org.aksw.ore.rendering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import org.aksw.mole.ore.rendering.KeywordColorMap;
import org.aksw.mole.ore.util.PrefixedShortFromProvider;
import org.dllearner.core.owl.Axiom;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.utilities.owl.OWLAPIConverter;
import org.dllearner.utilities.owl.OWLAPIDescriptionConvertVisitor;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLObjectRenderer;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.IRIShortFormProvider;
import org.semanticweb.owlapi.util.QNameShortFormProvider;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleIRIShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.manchester.cs.owl.owlapi.mansyntaxrenderer.ManchesterOWLSyntaxOWLObjectRendererImpl;
import uk.ac.manchester.cs.owlapi.dlsyntax.DLSyntaxObjectRenderer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class Renderer {
	
	private static final Logger logger = LoggerFactory.getLogger(Renderer.class);
	
	private List<RenderingListener> renderingListeners = new ArrayList<RenderingListener>();
	
	private EntityRenderingStyle entityRenderingStyle = EntityRenderingStyle.SHORT_FORM;
	private Syntax axiomRenderingStyle = Syntax.MANCHESTER;

	//the short form providers for each entity rendering style
	Map<EntityRenderingStyle, ShortFormProvider> shortFormProviders = new HashMap<EntityRenderingStyle, ShortFormProvider>();
	
	
	IRIShortFormProvider sfp = new SimpleIRIShortFormProvider();
	
	
	
	private LoadingCache<OWLObject, String> manchesterCache = CacheBuilder.newBuilder()
		       .maximumSize(1000)
		       .expireAfterWrite(10, TimeUnit.MINUTES)
		       .build(
		           new CacheLoader<OWLObject, String>() {
		             public String load(OWLObject object){
		               return renderManchesterSyntax(object);
		             }
		           });
	
	private LoadingCache<OWLObject, String> dlCache = CacheBuilder.newBuilder()
		       .maximumSize(1000)
		       .expireAfterWrite(10, TimeUnit.MINUTES)
		       .build(
		           new CacheLoader<OWLObject, String>() {
		             public String load(OWLObject object){
		               return renderDLSyntax(object);
		             }
		           });
	
	
	private OWLObjectRenderer manchesterSyntaxRenderer = new ManchesterOWLSyntaxOWLObjectRendererImpl();
	private OWLObjectRenderer manchesterSyntaxRendererLongForm = new ManchesterOWLSyntaxOWLObjectRendererImpl();
	private OWLObjectRenderer manchesterSyntaxRendererPrefixed = new UnsortedManchesterSyntaxRendererImpl();
	private OWLObjectRenderer dlSyntaxRenderer = new DLSyntaxObjectRenderer(); 
	
	private KeywordColorMap colorMap = new KeywordColorMap();
	
	public Renderer() {
		shortFormProviders.put(EntityRenderingStyle.SHORT_FORM, new SimpleShortFormProvider());
		shortFormProviders.put(EntityRenderingStyle.URI, new NoShortFormProvider());
		shortFormProviders.put(EntityRenderingStyle.LABEL, new SimpleShortFormProvider());
		
		//default is IRI short form rendering
		setEntityRenderingStyle(EntityRenderingStyle.SHORT_FORM);
	}
	
	/**
	 * @param renderingListeners the renderingListeners to set
	 */
	public void addRenderingListener(RenderingListener renderingListener) {
		this.renderingListeners.add(renderingListener);
	}
	
	/**
	 * @param renderingListeners the renderingListeners to set
	 */
	public void removeRenderingListener(RenderingListener renderingListener) {
		this.renderingListeners.remove(renderingListener);
	}
	
	private void fireRenderingChanged(){
		logger.info("Rendering changed.");
		for (RenderingListener l : renderingListeners) {
			l.renderingChanged();
		}
	}
	
	/**
	 * @param entityRenderingStyle the entityRenderingStyle to set
	 */
	public void setRenderingStyle(Syntax axiomRenderingStyle, EntityRenderingStyle entityRenderingStyle) {
		boolean changed = axiomRenderingStyle != this.axiomRenderingStyle || this.entityRenderingStyle != entityRenderingStyle;
		if(changed){
			this.axiomRenderingStyle = axiomRenderingStyle;
			this.entityRenderingStyle = entityRenderingStyle;
			ShortFormProvider sfp = shortFormProviders.get(entityRenderingStyle);
			manchesterSyntaxRenderer.setShortFormProvider(sfp);
			dlSyntaxRenderer.setShortFormProvider(sfp);
			fireRenderingChanged();
		}
	}
	
	/**
	 * @param entityRenderingStyle the entityRenderingStyle to set
	 */
	public void setEntityRenderingStyle(EntityRenderingStyle entityRenderingStyle) {
		this.entityRenderingStyle = entityRenderingStyle;
		ShortFormProvider sfp = shortFormProviders.get(entityRenderingStyle);
		manchesterSyntaxRenderer.setShortFormProvider(sfp);
		dlSyntaxRenderer.setShortFormProvider(sfp);
		fireRenderingChanged();
	}
	
	public String render(IRI iri) {
		return sfp.getShortForm(iri);
	}
	
	public String render(String iri){
		return render(IRI.create(iri));
	}
	
	public String render(Individual ind){
		return render(IRI.create(ind.getName()));
	}
	
	public String render(Description desc){
		OWLClassExpression ce = OWLAPIDescriptionConvertVisitor.getOWLClassExpression(desc);
		return render(ce);
	}
	
	public String renderHTML(Description desc){
		OWLClassExpression ce = OWLAPIDescriptionConvertVisitor.getOWLClassExpression(desc);
		return renderHTML(ce);
	}
	
	public String renderHTML(Axiom axiom){
		OWLAxiom ax = OWLAPIConverter.getOWLAPIAxiom(axiom);
		return renderHTML(ax);
	}
	
	public String render(Axiom axiom){
		OWLAxiom ax = OWLAPIConverter.getOWLAPIAxiom(axiom);
		return render(ax);
	}
	
	public String render(OWLObject owlObject) {
		switch (axiomRenderingStyle) {
		case MANCHESTER:
			return renderManchesterSyntax(owlObject);
		case DL:
			return renderDLSyntax(owlObject);
		default:
			return renderManchesterSyntax(owlObject);
		}
	}
	
	public String renderHTML(OWLObject owlObject) {
		switch (axiomRenderingStyle) {
		case MANCHESTER:
			return renderManchesterSyntaxHTML(owlObject);
		case DL:
			return renderDLSyntax(owlObject);
		default:
			return renderManchesterSyntaxHTML(owlObject);
		}
	}
	
	private String renderManchesterSyntax(OWLObject object){
		return manchesterSyntaxRenderer.render(object);
	}
	
	private String renderDLSyntax(OWLObject object){
		return dlSyntaxRenderer.render(object);
	}
	
	private String renderManchesterSyntaxHTML(OWLObject object){
		String renderedString = renderManchesterSyntax(object);
		StringTokenizer st = new StringTokenizer(renderedString);
		StringBuffer bf = new StringBuffer();
		
		bf.append("<html>");
		
		String token;
		while(st.hasMoreTokens()){
			token = st.nextToken();
			String color = "black";
			
			boolean isReserved = false;
			String c = colorMap.get(token);
			if(c != null){
				isReserved = true;
				color = c;
			}
			
			if(isReserved){
				color = "#000000";
				bf.append("<b><font color=" + color + ">" + token + " </font></b>");
			} else if(token.equals("(not")){//if token equals (not as workaround
				bf.append("(<b><font color=" + color + ">not</font></b>");
			} else {
				bf.append(" " + token + " ");
			}
		}
		bf.append("</html>");
		renderedString = bf.toString();

		return renderedString;
	}
	
	public static void main(String[] args) throws Exception {
		
		PrefixManager pm = new DefaultPrefixManager("http://dbpedia.org/ontology/");
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLSubClassOfAxiom ax = df.getOWLSubClassOfAxiom(
				df.getOWLClass("A", pm),
				df.getOWLObjectSomeValuesFrom(df.getOWLObjectProperty("p", pm), df.getOWLClass("B", pm)));
		
		OWLObjectRenderer renderer = new ManchesterOWLSyntaxOWLObjectRendererImpl();
		System.out.println(renderer.render(ax));
		
		renderer.setShortFormProvider(new ShortFormProvider() {
			
			@Override
			public String getShortForm(OWLEntity entity) {
				return entity.toStringID();
			}
			
			@Override
			public void dispose() {
			}
		});
		System.out.println(renderer.render(ax));
		
		renderer.setShortFormProvider(new QNameShortFormProvider());
		System.out.println(renderer.render(ax));
		
		renderer.setShortFormProvider(new PrefixedShortFromProvider());
		System.out.println(renderer.render(ax));
		
		renderer = new UnsortedManchesterSyntaxRendererImpl();
		System.out.println(renderer.render(ax));
		
		
	}

}
