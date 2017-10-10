package edu.stanford.nlp.sempre.thingtalk;

import java.util.Iterator;

import com.google.common.base.Joiner;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;

public class AddPredicateFn extends SemanticFn {
    private boolean isAction;
    private String logicOp;
    private String ifToken;
    private String opToken;
    private String operator;

    @Override
    public void init(LispTree tree) {
        super.init(tree);
        isAction = tree.child(1).value.equals("action");
        logicOp = tree.child(2).value;
        ifToken = tree.child(3).value;

        if (tree.child(4).isLeaf())
            opToken = tree.child(4).value;
        else
            opToken = Joiner.on(' ').join(tree.child(4).children);
        operator = tree.child(5).value;
    }

    @Override
    public DerivationStream call(final Example ex, final Callable c) {
        return new AddPredicateStream(ex, c);
    }

    private static boolean operatorOk(Type paramType, String operator) {
        assert (!operator.equals("is")) : "Bad use of AddPredicateValueFn";
        switch (operator) {
            case "contains":
            case "starts":
            case "ends":
                return paramType == Type.String;
            case "has":
                return paramType instanceof Type.Array;
            case ">":
            case "<":
                return paramType == Type.Number || paramType instanceof Type.Measure;
            case "=":
                return !(paramType instanceof Type.Array);
            default:
                return true;
        }
    }

    private class AddPredicateStream extends MultipleDerivationStream {
        private final Example ex;
        private final Callable callable;
        private final ParametricValue invocation;
        private final Iterator<String> argnameIter;
        private String currentArgname;

        public AddPredicateStream(Example ex, Callable callable) {
            this.ex = ex;
            this.callable = callable;

            Derivation left = callable.child(0);
            if (left.value == null || !(left.value instanceof ParametricValue))
                throw new IllegalArgumentException("AddPredicateValueFn used incorrectly");

            invocation = (ParametricValue) left.value;
            argnameIter = invocation.name.argtypes.keySet().iterator();
        }

        private Derivation findLastAnchoredArg(Derivation deriv) {
            Derivation lastArg = null;
            if (deriv.children != null && deriv.children.size() == 2)
                lastArg = deriv.child(1);
            else
                return null;
            if (lastArg.getCat().equals("$PersonValue"))
                return null;
            if (lastArg.spanStart == -1 || lastArg.spanEnd == -1)
                return findLastAnchoredArg(deriv.child(0));
            else
                return lastArg;
        }

        @Override
        public Derivation createDerivation() {
            Derivation lastArg = findLastAnchoredArg(callable.child(0));
            if (lastArg != null && !lastArg.isLeftOf(callable.child(1)))
                return null;

            while (true) {
                if (!argnameIter.hasNext())
                    return null;

                currentArgname = argnameIter.next();
                if (currentArgname.startsWith("__")) // compat argument
                    continue;

                ParamNameValue param = new ParamNameValue(currentArgname, invocation.name.getArgType(currentArgname));

                if (!operatorOk(param.type, operator))
                    continue;

                if(logicOp.equals("or") && invocation.isEmptyPredicate())
                    continue;

                Derivation left = callable.child(0);
                Derivation right = callable.child(1);
                Value toAdd = right.value;
                String sempreType = ThingTalk.typeFromValue(toAdd);
                Type haveType = Type.fromString(sempreType);

                if (!ArgFilterHelpers.typeOk(haveType, param.type, toAdd) &&
                        !ArgFilterHelpers.typeOkArray(haveType, param.type, toAdd))
                    continue;

                ParamValue pv = new ParamValue(param, sempreType, operator, toAdd);

                ParametricValue newInvocation = invocation.clone();
                newInvocation.addPredicate(pv, logicOp.equals("and"));

                String logicOpToken = logicOp + " ";
                if(invocation.isEmptyPredicate())
                    logicOpToken = "";

                String canonical = left.canonicalUtterance + " " + logicOpToken +
                        ifToken + " " + invocation.name.getArgCanonical(currentArgname) + " " +
                        opToken + " " + right.canonicalUtterance;
                String nerCanonical = left.nerUtterance + " " + logicOpToken +
                        ifToken + " " + invocation.name.getArgCanonical(currentArgname) + " " +
                        opToken + " " + right.nerUtterance;

                Derivation.Builder bld = new Derivation.Builder()
                        .withCallable(callable)
                        .formula(new ValueFormula<>(newInvocation))
                        .type(SemType.entityType)
                        .canonicalUtterance(canonical)
                        .nerUtterance(nerCanonical);
                Derivation deriv = bld.createDerivation();

                // FIXME : Need to add new features for detecting the operator
                int spanMin = callable.child(1).spanStart;
                if (spanMin > 0 && ThingTalkFeatureComputer.opts.featureDomains.contains("thingtalk_params_leftword"))
                    deriv.addFeature("thingtalk_params_leftword",
                            ex.token(spanMin - 1) + "---" + pv.name.argname + "," + pv.operator);

                return deriv;
            }
        }
    }
}
