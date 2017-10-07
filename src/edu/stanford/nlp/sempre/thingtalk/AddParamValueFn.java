package edu.stanford.nlp.sempre.thingtalk;

import java.util.Iterator;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;

public class AddParamValueFn extends SemanticFn {
  private String withToken;

  @Override
  public void init(LispTree tree) {
    super.init(tree);
    withToken = tree.child(1).value;
  }

  @Override
  public DerivationStream call(final Example ex, final Callable c) {
    return new AddParamValueStream(ex, c);
  }

  private class AddParamValueStream extends MultipleDerivationStream {
    private final Example ex;
    private final Callable callable;
    private final ParametricValue invocation;
    private final Iterator<String> argnameIter;
    private String currentArgname;

    public AddParamValueStream(Example ex, Callable callable) {
      this.ex = ex;
      this.callable = callable;

      Derivation left = callable.child(0);
      if (left.value == null || !(left.value instanceof ParametricValue))
        throw new IllegalArgumentException("AddParamValueFn used incorrectly");

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
        if (currentArgname.startsWith("__") || !invocation.name.isInputArg.get(currentArgname) || invocation.hasParamName(currentArgname))
          continue;

        ParamNameValue param = new ParamNameValue(currentArgname, invocation.name.getArgType(currentArgname));

        Derivation left = callable.child(0);
        Derivation right = callable.child(1);
        Value valToAdd = right.value;
        String sempreType = ThingTalk.typeFromValue(valToAdd);
        Type haveType = Type.fromString(sempreType);

        if (!ArgFilterHelpers.typeOk(haveType, param.type, valToAdd) &&
            !ArgFilterHelpers.typeOkArray(haveType, param.type, valToAdd))
          continue;

        ParamValue pv = new ParamValue(param, sempreType, "is", valToAdd);

        ParametricValue newInvocation = invocation.clone();
        newInvocation.addParam(pv);

        String opPart = " ";

        String canonical = left.canonicalUtterance + " " + withToken + " " +
            invocation.name.getArgCanonical(currentArgname) + opPart
            + right.canonicalUtterance;
        String nerCanonical = left.nerUtterance + " " + withToken + " "
            + invocation.name.getArgCanonical(currentArgname) + opPart
            + right.nerUtterance;

        Derivation.Builder bld = new Derivation.Builder()
            .withCallable(callable)
            .formula(new ValueFormula<>(newInvocation))
            .type(SemType.entityType)
            .canonicalUtterance(canonical)
            .nerUtterance(nerCanonical);
        Derivation deriv = bld.createDerivation();

        int spanMin = callable.child(1).spanStart;
        if (spanMin > 0 && ThingTalkFeatureComputer.opts.featureDomains.contains("thingtalk_params_leftword"))
          deriv.addFeature("thingtalk_params_leftword",
              ex.token(spanMin - 1) + "---" + pv.name.argname + "," + pv.operator);

        return deriv;
      }
    }
  }
}
