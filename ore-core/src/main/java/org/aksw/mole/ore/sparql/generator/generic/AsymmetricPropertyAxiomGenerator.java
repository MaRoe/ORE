package org.aksw.mole.ore.sparql.generator.generic;

import org.aksw.mole.ore.sparql.generator.AbstractSPARQLBasedGeneralAxiomGenerator;
import org.dllearner.kb.SparqlEndpointKS;

public class AsymmetricPropertyAxiomGenerator extends AbstractSPARQLBasedGeneralAxiomGenerator{
	

	public AsymmetricPropertyAxiomGenerator(SparqlEndpointKS endpoint) {
		super(endpoint);
		
		query = "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
				"CONSTRUCT " +
				"{?p a owl:AsymmetricProperty.} " +
				"WHERE " +
				"{?p a owl:AsymmetricProperty.}";
	}
	
	
}
