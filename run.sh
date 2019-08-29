#!/bin/sh

SEMPREDIR=`dirname $0`
SEMPREDIR=`realpath $SEMPREDIR`
EXTRA_ARGS="$@"
JAVA=${JAVA:-java}
LANGUAGES=${LANGUAGES:-en zh-hans zh-hant}
PORT=${PORT:-8888}

exec ${JAVA} -Xmx7G -ea ${JAVA_ARGS} -cp ${SEMPREDIR}/libsempre/*:${SEMPREDIR}/lib/* edu.stanford.nlp.sempre.TokenizerServer --port ${PORT} ${LANGUAGES}
