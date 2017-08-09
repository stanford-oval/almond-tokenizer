#!/bin/bash

#
# Extracts and preprocesses all datasets for Seq2Seq training and evaluation
#
# Produces the following files:
# - base-author.tsv
# - paraphrasing-train+dev.tsv
# - generated.tsv
# - train.tsv
# - dev.tsv
# - paraphrasing-test-prim.tsv
# - paraphrasing-test-compound.tsv
# - scenario-prim.tsv
# - scenario-compound.tsv
# - cheatsheet-prim.tsv
# - cheatsheet-compound.tsv
#
#
# Usage:
#
# WORKDIR=... ./scripts/extract-seq2seq-all.sh ${DATABASE_PW}

DATABASE_PW=${DATABASE_PW:-$1}
shift
set -x
set -e

SEMPREDIR=`dirname $0`/..
SEMPREDIR=`realpath ${SEMPREDIR}`
WORKDIR=${WORKDIR:-.}
GLOVE=${GLOVE:-$WORKDIR/glove.txt}
EXTRA_ARGS="-ThingpediaDatabase.dbPw ${DATABASE_PW} $@"

BASEAUTHOR_TRAIN="thingpedia online"
PARA_TRAINDEV="turking1 turking2 turking3"
GENERATED="generated-highvariance"
PARA_TEST="test1 test3"

SCENARIO="test-scenarios test-scenarios2"
CHEATSHEET="test-cheatsheet test-cheatsheet2"

do_one() {
${SEMPREDIR}/scripts/run-extract-seq2seq.sh -ExtractSeq2Seq.types $1 -ExtractSeq2Seq.output ${WORKDIR}/$2.tsv ${EXTRA_ARGS}
}

# extract the datasets
do_one "$BASEAUTHOR_TRAIN" base-author
do_one "$PARA_TRAINDEV" paraphrasing-train+dev
do_one "$PARA_TEST" paraphrasing-test
do_one "$SCENARIO" scenario
do_one "$CHEATSHEET" cheatsheet
do_one "$GENERATED" generated
do_one "generated-cheatsheet" generated-cheatsheet
