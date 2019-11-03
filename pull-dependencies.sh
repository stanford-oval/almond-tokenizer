#!/bin/sh

set -e
set -x
srcdir=`dirname $0`
srcdir=`realpath $srcdir`

pullsempre() {
  wget -c 'https://nlp.stanford.edu/software/sempre/dependencies-2.0/'$1 -O lib/`basename $1`
}

pullparmesan() {
  wget -c 'https://parmesan.stanford.edu/corenlp/'$1 -O lib/$1
}

pullopencc() {
  wget -c 'https://github.com/yichen0831/OpenCC-Java/releases/download/'$1 -O lib/`basename $1`
}

cd $srcdir
mkdir -p lib
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
pullparmesan 'stanford-chinese-corenlp-2017-06-09-models.jar'
pullparmesan 'trove4j-3.0.3.jar'
pullparmesan 'trove4j-3.0.3-javadoc.jar'

# OpenCC
pullopencc 'v0.1/OpenCC-Java-all-0.1.jar'

# Italian support - requires a fork of CoreNLP, but luckily it is mostly
# compatible
wget -c 'http://www.airpedia.org/tint/0.2/tint-runner-0.2-bin.tar.gz' -O lib/tint-runner-0.2-bin.tar.gz
tmpdir=`mktemp -d`
cd $tmpdir
tar xvf $srcdir/lib/tint-runner-0.2-bin.tar.gz
for f in `cat $srcdir/italian-deps.txt` ; do
    cp tint/lib/$f $srcdir/lib
done
cd $srcdir
rm -fr $tmpdir
rm -fr lib/tint-runner-0.2-bin.tar.gz
