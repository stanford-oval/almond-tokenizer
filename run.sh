#!/bin/sh

SEMPREDIR=`dirname $0`
SEMPREDIR=`realpath $SEMPREDIR`
MODULE=${MODULE:-almond}
EXTRA_ARGS="$@"
JAVA=${JAVA:-java}

exec ${JAVA} -ea ${JAVA_ARGS} -Djava.library.path=${SEMPREDIR}/jni -cp ${SEMPREDIR}/libsempre/*:${SEMPREDIR}/lib/* edu.stanford.nlp.sempre.TokenizerServer ++${MODULE}/${MODULE}.tokenizer.conf -SpellCheckerAnnotator.dictionaryDirectory ${SEMPREDIR}/myspell ${EXTRA_ARGS}
