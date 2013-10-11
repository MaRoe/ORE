package org.aksw.ore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener;
import org.aksw.ore.model.Knowledgebase;
import org.aksw.ore.model.OWLOntologyKnowledgebase;
import org.aksw.ore.util.HelpManager;
import org.aksw.ore.view.ConstraintValidationView;
import org.aksw.ore.view.DebuggingView;
import org.aksw.ore.view.EnrichmentView;
import org.aksw.ore.view.KnowledgebaseView;
import org.aksw.ore.view.LearningView;
import org.aksw.ore.view.NamingView;
import org.aksw.ore.view.SPARQLDebuggingView;
import org.semanticweb.owlapi.io.ToStringRenderer;

import uk.ac.manchester.cs.owl.owlapi.mansyntaxrenderer.ManchesterOWLSyntaxOWLObjectRendererImpl;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Title;
import com.vaadin.navigator.Navigator;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener;
import com.vaadin.server.Page;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.NativeButton;
import com.vaadin.ui.Notification;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

@Theme("dashboard")
@Title("ORE")
@Push
@SuppressWarnings("serial")
public class OREUI extends UI implements KnowledgebaseLoadingListener
{
	Navigator navigator;
    
    HorizontalLayout root = new HorizontalLayout();
    CssLayout content = new CssLayout();
    CssLayout menu = new CssLayout();
    
    private List<String> views = Arrays.asList(new String[]{"knowledgebase", "enrichment", "logical", "naming", "constraints"});
    
    Map<String, Class<? extends View>> routes = new HashMap<String, Class<? extends View>>() {
        {
//        	put("/knowledgebase", KnowledgebaseView.class);
        	put("/enrichment", EnrichmentView.class);
            put("/logical", DebuggingView.class);
            put("/logical", SPARQLDebuggingView.class);
            put("/constraints", ConstraintValidationView.class);
            put("/naming", NamingView.class);
        }
    };
    
    Map<String, Button> viewNameToMenuButton = new HashMap<String, Button>();
    
    private HelpManager helpManager;

	public OREUI() {
		ToStringRenderer.getInstance().setRenderer(new ManchesterOWLSyntaxOWLObjectRendererImpl());
	}
    
    @Override
    protected void init(VaadinRequest request) {
    	getPage().setTitle("ORE");
    	root.addStyleName("root");
        root.setSizeFull();
        setContent(root);
        
        //create and add sidebar to the left
        Component sidebar = createSidebar();
        root.addComponent(sidebar);
        
        
//        root.setExpandRatio(l, 1);
        
        //create the main content panel
        content.setSizeFull();
        content.setStyleName("view-content");
        root.addComponent(content);
        root.setExpandRatio(content, 1f);
        
        ORESession.init();
        
        
        // Create a navigator to control the views
        navigator = new Navigator(this, content);
        
        // Create and register the views
        navigator.addView("/knowledgebase", new KnowledgebaseView());
        for (String route : routes.keySet()) {
        	navigator.addView(route, routes.get(route));
        }
        
        updateAvailableViews();
        
        String f = Page.getCurrent().getUriFragment();
        if (f != null && f.startsWith("!")) {
            f = f.substring(1);
        }
        if (f == null || f.equals("") || f.equals("/")) {
        	f = "/knowledgebase";
        }
        navigator.navigateTo(f);
    	viewNameToMenuButton.get(f).addStyleName("selected");
    	
    	helpManager = new HelpManager(this);
    	
    	navigator.addViewChangeListener(new ViewChangeListener() {

            @Override
            public boolean beforeViewChange(ViewChangeEvent event) {
            	//avoid the navigation to views that are currently not available
            	if(event.getNewView() instanceof DebuggingView && !ORESession.getKnowledgebaseManager().getKnowledgebase().canDebug()){
            		return false;
            	} else if(event.getNewView() instanceof LearningView && !ORESession.getKnowledgebaseManager().getKnowledgebase().canLearn()){
            		return false;
            	}
                helpManager.closeAll();
                return true;
            }

            @Override
            public void afterViewChange(ViewChangeEvent event) {
                View newView = event.getNewView();
                helpManager.showHelpFor(newView);
            }
        });
    	
    	
    	ORESession.getKnowledgebaseManager().addListener(this);
    }
    
    private void updateAvailableViews(){
    	Knowledgebase knowledgebase = ORESession.getKnowledgebaseManager().getKnowledgebase();
    	if(knowledgebase instanceof OWLOntologyKnowledgebase){
			navigator.addView("/enrichment", new LearningView());
			navigator.addView("/logical", new DebuggingView());
			viewNameToMenuButton.get("/naming").setEnabled(true);
		} else {
			navigator.addView("/enrichment", new EnrichmentView());
			navigator.addView("/logical", new SPARQLDebuggingView());
			viewNameToMenuButton.get("/naming").setEnabled(false);
		}
    	viewNameToMenuButton.get("/enrichment").setEnabled(knowledgebase.canLearn());
    	viewNameToMenuButton.get("/logical").setEnabled(knowledgebase.canDebug());
    }
    
    private Component createSidebar(){
    	VerticalLayout l = new VerticalLayout();
        l.addStyleName("sidebar");
        l.setWidth(null);
        l.setHeight("100%");
        l.setSpacing(true);
        
        //add logo left
        l.addComponent(new CssLayout() {
            {
                addStyleName("branding");
                Label logo = new Label(
                        "<span>QuickTickets</span> Dashboard",
                        ContentMode.HTML);
                logo.setSizeUndefined();
//                addComponent(logo);
                Image img = new Image(null, new ThemeResource("img/ore-logo.png"));
                img.setWidth("95%");
                img.setHeight("95%");
                addComponent(img);
                setHeight("100px");
            }
        });
        
        //create menu bar left
        createMenu();
        l.addComponent(menu);
        
    	l.setExpandRatio(menu, 1);

        // User menu
        l.addComponent(new VerticalLayout() {
            {
                setSizeUndefined();
                addStyleName("user");
                Image profilePic = new Image(
                        null,
                        new ThemeResource("img/profile-pic.png"));
                profilePic.setWidth("34px");
                addComponent(profilePic);
                Label userName = new Label("Guest");
                userName.setSizeUndefined();
                addComponent(userName);

                Command cmd = new Command() {
                    @Override
                    public void menuSelected(
                            MenuItem selectedItem) {
                        Notification
                                .show("Not implemented in this demo");
                    }
                };
                MenuBar settings = new MenuBar();
                MenuItem settingsMenu = settings.addItem("",
                        null);
                settingsMenu.setStyleName("icon-cog");
                settingsMenu.addItem("Settings", cmd);
                settingsMenu.addItem("Preferences", cmd);
                settingsMenu.addSeparator();
                settingsMenu.addItem("My Account", cmd);
                addComponent(settings);

                Button exit = new NativeButton("Exit");
                exit.addStyleName("icon-cancel");
                exit.setDescription("Sign Out");
                addComponent(exit);
                exit.addClickListener(new ClickListener() {
                    @Override
                    public void buttonClick(ClickEvent event) {
//                        buildLoginView(true);
                    }
                });
            }
        });
        return l;
    }
    
    private void createMenu(){
    	//enrichment and knowledge base button
    	for (final String view : new String[]{"knowledgebase", "enrichment"}) {
    		String caption = view.equals("knowledgebase") ? "knowledge base" : view;
            Button b = new NativeButton(caption);
            b.addStyleName("icon-" + view);
            b.addClickListener(new ClickListener() {
                @Override
                public void buttonClick(ClickEvent event) {
                    clearMenuSelection();
                    event.getButton().addStyleName("selected");
                    if (!navigator.getState().equals("/" + view))
                    	navigator.navigateTo("/" + view);
                }
            });
            menu.addComponent(b);
            viewNameToMenuButton.put("/" + view, b);
    	}
    	//the debugging buttons in extra layout
    	HorizontalLayout debugging = new HorizontalLayout();
    	debugging.setWidth("100px");
    	debugging.setHeight("100px");
    	Label debuggingLabel = new Label("Debugging");
    	debuggingLabel.addStyleName("debugging-label");
    	menu.addComponent(debuggingLabel);
    	VerticalLayout l = new VerticalLayout();
    	for (final String view : new String[]{"logical", "naming", "constraints"}) {
            Button b = new NativeButton(view);
            b.addStyleName("icon-" + view);
            b.addClickListener(new ClickListener() {
                @Override
                public void buttonClick(ClickEvent event) {
                    clearMenuSelection();
                    event.getButton().addStyleName("selected");
                    if (!navigator.getState().equals("/" + view))
                    	navigator.navigateTo("/" + view);
                }
            });
            menu.addComponent(b);
            viewNameToMenuButton.put("/" + view, b);
    	}
    	debugging.addComponent(l);
    	debugging.setExpandRatio(l, 1f);
//    	menu.addComponent(debugging);
    	
    	menu.addStyleName("menu");
        menu.setHeight("100%");
    }
    
    private void clearMenuSelection() {
    	for (Button b : viewNameToMenuButton.values()) {
    		b.removeStyleName("selected");
		}
    }
    
	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseChanged(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseChanged(Knowledgebase knowledgebase) {
		
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseAnalyzed(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseAnalyzed(Knowledgebase knowledgebase) {
		updateAvailableViews();
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#knowledgebaseStatusChanged(org.aksw.ore.model.Knowledgebase)
	 */
	@Override
	public void knowledgebaseStatusChanged(Knowledgebase knowledgebase) {
	}

	/* (non-Javadoc)
	 * @see org.aksw.ore.manager.KnowledgebaseManager.KnowledgebaseLoadingListener#message(java.lang.String)
	 */
	@Override
	public void message(String message) {
	}
    
}