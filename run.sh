#!/bin/sh

SEMPREDIR=`dirname $0`
SEMPREDIR=`realpath $SEMPREDIR`
EXTRA_ARGS="$@"
JAVA=${JAVA:-java}
LANGUAGES=${LANGUAGES:-en-us zh-cn zh-tw}
PORT=${PORT:-8888}

exec ${JAVA} -Xmx7G -ea ${JAVA_ARGS} -Djava.library.path=${SEMPREDIR}/jni -cp ${SEMPREDIR}/libsempre/*:${SEMPREDIR}/lib/* edu.stanford.nlp.sempre.TokenizerServer --port ${PORT} ${LANGUAGES}
