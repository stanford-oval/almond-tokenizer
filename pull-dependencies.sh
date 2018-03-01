#!/bin/sh

set -e
SEMPREDIR=`dirname $0`
SEMPREDIR=`realpath $SEMPREDIR`

pullsempre() {
  wget -c 'https://nlp.stanford.edu/software/sempre/dependencies-2.0/'$1 -O lib/`basename $1`
}

pullparmesan() {
  wget -c 'https://parmesan.stanford.edu/corenlp/'$1 -O lib/$1
}

cd $SEMPREDIR
if test -d fig ; then
  (cd fig ; git pull )
else
  git clone 'https://github.com/percyliang/fig' fig
fi

make -C fig
mkdir -p lib
ln -sf ../fig/fig.jar lib/fig.jar

pullsempre '/u/nlp/data/semparse/resources/guava-14.0.1.jar'
# TestNG -- testing framework
pullsempre '/u/nlp/data/semparse/resources/testng-6.8.5.jar'
pullsempre '/u/nlp/data/semparse/resources/jcommander-1.30.jar'

# JSON
pullsempre '/u/nlp/data/semparse/resources/jackson-core-2.2.0.jar'
pullsempre '/u/nlp/data/semparse/resources/jackson-annotations-2.2.0.jar'
pullsempre '/u/nlp/data/semparse/resources/jackson-databind-2.2.0.jar'

pullparmesan 'stanford-corenlp-3.8.0.jar'
pullparmesan 'joda-time.jar'
pullparmesan 'jollyday.jar'
pullparmesan 'ejml-0.23.jar'
pullparmesan 'javax.json.jar'
pullparmesan 'protobuf.jar'
pullparmesan 'slf4j-api.jar'
pullparmesan 'slf4j-simple.jar'
pullparmesan 'xom.jar'
pullparmesan 'stanford-english-corenlp-2017-06-09-models.jar'
pullparmesan 'trove4j-3.0.3.jar'
pullparmesan 'trove4j-3.0.3-javadoc.jar'
