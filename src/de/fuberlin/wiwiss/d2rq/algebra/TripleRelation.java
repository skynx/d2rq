package de.fuberlin.wiwiss.d2rq.algebra;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;

/**
 * A respresentation of a d2rq:PropertyBridge, describing how
 * a set of virtual triples are to be obtained
 * from a database. The virtual subjects, predicates and objects
 * are generated by {@link NodeMaker}s.
 *
 * @author Chris Bizer chris@bizer.de
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @version $Id: TripleRelation.java,v 1.2 2007/10/22 10:21:16 cyganiak Exp $
 */
public class TripleRelation implements RDFRelation {
	private final static Set S_P_O = new HashSet(Arrays.asList(new String[] {"S", "P", "O"}));
	
	private NodeMaker subjectMaker;
	private NodeMaker predicateMaker;
	private NodeMaker objectMaker; 
	private Relation baseRelation;
	private Set projectionColumns = new HashSet();
	private boolean isUnique;
	
	public TripleRelation(Relation baseRelation, NodeMaker subjectMaker, NodeMaker predicateMaker, NodeMaker objectMaker) {
		this.subjectMaker = subjectMaker;
		this.predicateMaker = predicateMaker;
		this.objectMaker = objectMaker;
		this.baseRelation = baseRelation;
		this.projectionColumns.addAll(this.subjectMaker.projectionColumns());
		this.projectionColumns.addAll(this.predicateMaker.projectionColumns());
		this.projectionColumns.addAll(this.objectMaker.projectionColumns());
		this.isUnique = determineIsUnique();
	}
	
	public Relation baseRelation() {
		return this.baseRelation;
	}
	
	public boolean isUnique() {
		return this.isUnique;
	}
	
	public Set projectionColumns() {
		return this.projectionColumns;
	}

	public NodeMaker nodeMaker(int index) {
		switch (index) {
			case 0: return this.subjectMaker; 
			case 1: return this.predicateMaker;
			case 2: return this.objectMaker;
			default: throw new IllegalArgumentException(Integer.toString(index));
		}
	}
    
	public String toString() {
		return "PropertyBridge(\n" +
				"    " + this.subjectMaker + "\n" +
				"    " + this.predicateMaker + "\n" +
				"    " + this.objectMaker + "\n" +
				")";
	}
	
	// TODO: Some duplication with UnionOverSameBase.tables()
	public RDFRelation withPrefix(int index) {
		Set tables = new HashSet();
		Iterator it = this.projectionColumns.iterator();
		while (it.hasNext()) {
			Attribute column = (Attribute) it.next();
			tables.add(column.relationName());
		}
		it = this.baseRelation.joinConditions().iterator();
		while (it.hasNext()) {
			Join join = (Join) it.next();
			tables.add(join.table1());
			tables.add(join.table2());
		}
		it = this.baseRelation.condition().columns().iterator();
		while (it.hasNext()) {
			Attribute column = (Attribute) it.next();
			tables.add(column.relationName());
		}
		Collection newAliases = new ArrayList();
		it = tables.iterator();
		// TODO Move code to RelationName.withPrefix
		while (it.hasNext()) {
			RelationName tableName = (RelationName) it.next();
			newAliases.add(new Alias(tableName, new RelationName(null,
							"T" + index + "_" + tableName.qualifiedName().replace('.', '_'))));
		}
		return renameColumns(new AliasMap(newAliases));
	}
	
	public Collection makeTriples(ResultRow row) {
		Node s = this.subjectMaker.makeNode(row);
		Node p = this.predicateMaker.makeNode(row);
		Node o = this.objectMaker.makeNode(row);
		if (s == null || p == null || o == null) {
			return Collections.EMPTY_LIST;
		}
		return Collections.singleton(new Triple(s, p, o));
	}
	
	public RDFRelation selectTriple(Triple t) {
		MutableRelation newBase = new MutableRelation(this.baseRelation);
		NodeMaker s = this.subjectMaker.selectNode(t.getSubject(), newBase);
		if (s.equals(NodeMaker.EMPTY)) return RDFRelation.EMPTY;
		NodeMaker p = this.predicateMaker.selectNode(t.getPredicate(), newBase);
		if (p.equals(NodeMaker.EMPTY)) return RDFRelation.EMPTY;
		NodeMaker o = this.objectMaker.selectNode(t.getObject(), newBase);
		if (o.equals(NodeMaker.EMPTY)) return RDFRelation.EMPTY;
		return new TripleRelation(newBase.immutableSnapshot(), s, p, o);
	}
	
	public RDFRelation renameColumns(ColumnRenamer renamer) {
		NodeMaker s = this.subjectMaker.renameColumns(renamer, MutableRelation.DUMMY);
		NodeMaker p = this.predicateMaker.renameColumns(renamer, MutableRelation.DUMMY);
		NodeMaker o = this.objectMaker.renameColumns(renamer, MutableRelation.DUMMY);
		return new TripleRelation(this.baseRelation.renameColumns(renamer), s, p, o);
	}

	public Collection names() {
		return S_P_O;
	}
	
	public NodeMaker namedNodeMaker(String name) {
		if ("S".equals(name)) {
			return this.subjectMaker;
		}
		if ("P".equals(name)) {
			return this.predicateMaker;
		}
		if ("O".equals(name)) {
			return this.objectMaker;
		}
		return null;
	}

	private boolean determineIsUnique() {
		if (this.baseRelation.joinConditions().isEmpty()) {
			return this.subjectMaker.isUnique() || this.predicateMaker.isUnique() || this.objectMaker.isUnique();
		}
		// TODO Make smarter
		return false;
	}
}