package edu.stanford.nlp.sempre.thingtalk;

import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.Values;
import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a rule to be setup remotely.
 * @author Rakesh Ramesh
 */
public final class SetupValue extends Value implements Cloneable{
    public final TypedStringValue person; // cannot be null

    // Only one of the following will be non-null
    public final RuleValue rule;
    public final TriggerValue trigger;
    public final QueryValue query;
    public final ActionValue action;

    public SetupValue(TypedStringValue person, RuleValue rule, TriggerValue trigger, QueryValue query, ActionValue action) {
        this.person = person;

        this.trigger = trigger;
        this.query = query;
        this.action = action;
        this.rule = rule;

        if(this.person == null ||
                (this.trigger == null && this.query == null && this.action == null && this.rule == null)) {
            LogInfo.warning("Got invalid SetupValue");
        }
    }

    public SetupValue(LispTree tree) {
        this.person = (TypedStringValue) Values.fromLispTree(tree.child(1));

        this.rule = (RuleValue) Values.fromLispTree(tree.child(2));
        this.trigger = (TriggerValue) Values.fromLispTree(tree.child(2));
        this.query = (QueryValue) Values.fromLispTree(tree.child(2));
        this.action = (ActionValue) Values.fromLispTree(tree.child(2));

        if(this.person == null ||
                (this.trigger == null && this.query == null && this.action == null && this.rule == null)) {
            LogInfo.warning("Got invalid SetupValue");
        }
    }

    private void addToLispTree(LispTree tree, Value val) {
            tree.addChild(val.toLispTree());
    }

    @Override
    public LispTree toLispTree() {
        LispTree tree = LispTree.proto.newList();
        tree.addChild("setup");
        tree.addChild(this.person.toLispTree());
        if(this.rule != null) tree.addChild(this.rule.toLispTree());
        if(this.trigger != null) tree.addChild(this.trigger.toLispTree());
        if(this.query != null) tree.addChild(this.query.toLispTree());
        if(this.action != null) tree.addChild(this.action.toLispTree());
        return tree;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new HashMap<>();
        json.put("person", this.person.value);
        if(this.rule != null)
            json.put("rule", this.rule.toJson());
        if(this.trigger != null)
            json.put("trigger", this.trigger.toJson());
        if(this.query != null)
            json.put("query", this.query.toJson());
        if(this.action != null)
            json.put("action", this.action.toJson());
        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SetupValue that = (SetupValue) o;

        if (!person.equals(that.person)) return false;
        if (rule != null ? !rule.equals(that.rule) : that.rule != null) return false;
        if (trigger != null ? !trigger.equals(that.trigger) : that.trigger != null) return false;
        if (query != null ? !query.equals(that.query) : that.query != null) return false;
        return action != null ? action.equals(that.action) : that.action == null;
    }

    @Override
    public int hashCode() {
        int result = person.hashCode();
        result = 31 * result + (rule != null ? rule.hashCode() : 0);
        result = 31 * result + (trigger != null ? trigger.hashCode() : 0);
        result = 31 * result + (query != null ? query.hashCode() : 0);
        result = 31 * result + (action != null ? action.hashCode() : 0);
        return result;
    }

    @Override
    public SetupValue clone() {
        return new SetupValue(new TypedStringValue("Username", this.person.value),
                (this.rule == null) ? null : rule.clone(),
                (this.trigger == null) ? null : (TriggerValue) trigger.clone(),
                (this.query == null) ? null : (QueryValue) query.clone(),
                (this.action == null) ? null : (ActionValue) action.clone());
    }
}
