package edu.stanford.nlp.sempre;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;

public interface AbstractQuantifiableEntityNormalizer {
	<E extends CoreLabel> List<E> applySpecializedNER(List<E> l);
}
