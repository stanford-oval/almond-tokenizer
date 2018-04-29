#!/bin/sh

SEMPREDIR=`dirname $0`
SEMPREDIR=`realpath $SEMPREDIR`
EXTRA_ARGS="$@"
JAVA=${JAVA:-java}
LANGUAGES=${LANGUAGES:-en}
PORT=${PORT:-8888}

exec ${JAVA} -ea ${JAVA_ARGS} -Djava.library.path=${SEMPREDIR}/jni -cp ${SEMPREDIR}/libsempre/*:${SEMPREDIR}/lib/* edu.stanford.nlp.sempre.TokenizerServer --port ${PORT} ${LANGUAGES}
