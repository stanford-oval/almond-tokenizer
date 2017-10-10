package edu.stanford.nlp.sempre.thingtalk;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.Values;
import fig.basic.LispTree;


/**
 * Represents a parameter for thingtalk
 * @author Rakesh Ramesh
 */
public class ParamValue extends Value {
	public final ParamNameValue name;

    // type: "String", "Date", "List", "Number", "Measure"
    public final String tt_type;
    // operator: "is", "contains", "has"
    public final String operator;
    public final boolean negated;
    public final Value value;

    public ParamValue(LispTree tree) {
		this.name = (ParamNameValue) Values.fromLispTree(tree.child(1));
        this.tt_type = tree.child(2).value;
        this.negated = tree.child(3).value.equals("not");
        this.operator = tree.child(4).value;
        this.value = Values.fromLispTree(tree.child(5));
    }

	public ParamValue(ParamNameValue name, String tt_type, String operator, Value value) {
        this.name = name;
        this.tt_type = tt_type;
        this.operator = operator;
        this.value = value;
        this.negated = false;
    }

    public ParamValue(ParamNameValue name, String tt_type, String operator, Value value, boolean negated) {
        this.name = name;
        this.tt_type = tt_type;
        this.operator = operator;
        this.value = value;
        this.negated = negated;
    }

    @Override
	public LispTree toLispTree() {
        LispTree tree = LispTree.proto.newList();
        tree.addChild("param");
        tree.addChild(name.toLispTree());
        tree.addChild(tt_type);
        tree.addChild(negated ? "not" : "");
        tree.addChild(operator);
        tree.addChild(value.toLispTree());
        return tree;
    }

    @Override
	public Map<String,Object> toJson() {
        Map<String,Object> json = new HashMap<>();
        json.put("name", name.toJson());
        json.put("type", tt_type);
        if(negated) json.put("negated", negated);
        json.put("operator", operator);
        json.put("value", value.toJson());
        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParamValue that = (ParamValue) o;

        if (negated != that.negated) return false;
        if (!name.equals(that.name)) return false;
        if (!tt_type.equals(that.tt_type)) return false;
        if (!operator.equals(that.operator)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + tt_type.hashCode();
        result = 31 * result + operator.hashCode();
        result = 31 * result + (negated ? 1 : 0);
        result = 31 * result + value.hashCode();
        return result;
    }
}